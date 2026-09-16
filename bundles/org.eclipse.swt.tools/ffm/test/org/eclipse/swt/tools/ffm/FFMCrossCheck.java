/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tools.ffm;

import java.lang.foreign.*;
import java.lang.reflect.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cairo.*;
import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.accessibility.gtk.*;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk3.*;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Runs the JNI and the generated FFM implementation of the same natives in one process and compares the results.
 * Run against the "jni" build of build-gtk.sh, where the natives are still JNI.
 */
public class FFMCrossCheck {

	static final String[] NATIVES = {
		"org.eclipse.swt.internal.C", "org.eclipse.swt.internal.gtk.OS", "org.eclipse.swt.internal.gtk.GDK",
		"org.eclipse.swt.internal.gtk.GTK", "org.eclipse.swt.internal.gtk3.GTK3", "org.eclipse.swt.internal.cairo.Cairo",
		"org.eclipse.swt.internal.accessibility.gtk.ATK",
	};
	static final String[] STRUCT_PACKAGES = {
		"org.eclipse.swt.internal", "org.eclipse.swt.internal.gtk", "org.eclipse.swt.internal.gtk3",
		"org.eclipse.swt.internal.cairo", "org.eclipse.swt.internal.accessibility.gtk",
	};

	/** Differences explained by the headers the shipped JNI binaries were compiled against. */
	static final Map<String, String> KNOWN = Map.of(
		"sizeof PangoLayoutRun", "the JNI binary was built against Pango headers without PangoGlyphItem.y_offset, start_x_offset and end_x_offset");

	static int checks, failures, known;

	static void check(String what, Object jni, Object ffm) {
		checks++;
		if (deepEquals(jni, ffm)) return;
		String reason = KNOWN.get(what);
		if (reason != null) {
			known++;
			System.out.println("KNOWN    " + what + " (JNI " + describe(jni) + ", FFM " + describe(ffm) + "): " + reason);
			return;
		}
		failures++;
		System.out.println("MISMATCH " + what + "\n   JNI: " + describe(jni) + "\n   FFM: " + describe(ffm));
	}

	public static void main(String[] args) throws Exception {
		Display display = new Display();
		try {
			checkSizes();
			checkStructs();
			checkFunctions(display);
		} finally {
			display.dispose();
		}
		System.out.println(checks + " checks, " + failures + " mismatches, " + known + " known header differences");
		System.exit(failures == 0 ? 0 : 1);
	}

	/* ------------------------------------------------------------------ sizes */

	static void checkSizes() throws Exception {
		int count = 0;
		for (String pkg : STRUCT_PACKAGES) {
			Class<?> structs;
			try {
				structs = Class.forName(pkg + ".Structs_FFM");
			} catch (ClassNotFoundException e) {
				continue;
			}
			for (Field field : structs.getFields()) {
				if (!field.getName().endsWith("_SIZEOF")) continue;
				String name = field.getName().substring(0, field.getName().length() - "_SIZEOF".length());
				long ffm = field.getLong(null);
				Integer jni = jniSizeof(pkg, name);
				if (jni == null) continue;
				check("sizeof " + name, (long) jni, ffm);
				count++;
			}
		}
		System.out.println("Compared " + count + " struct sizes");
	}

	static Integer jniSizeof(String pkg, String name) throws Exception {
		for (String natives : NATIVES) {
			try {
				Method method = Class.forName(natives).getMethod(name + "_sizeof");
				if (Modifier.isNative(method.getModifiers())) return (Integer) method.invoke(null);
			} catch (NoSuchMethodException e) {
				// declared in another natives class
			}
		}
		try {
			return Class.forName(pkg + "." + name).getField("sizeof").getInt(null);
		} catch (NoSuchFieldException | ClassNotFoundException e) {
			return null;
		}
	}

	/* ------------------------------------------------------------------ struct marshalling */

	static void checkStructs() throws Exception {
		int reads = 0, writes = 0;
		for (String natives : NATIVES) {
			Class<?> jniClass = Class.forName(natives);
			for (Method jni : jniClass.getDeclaredMethods()) {
				if (!jni.getName().equals("memmove") || !Modifier.isNative(jni.getModifiers())) continue;
				Class<?>[] types = jni.getParameterTypes();
				boolean read = !types[0].isPrimitive() && !types[0].isArray() && types[1] == long.class;
				boolean write = types[0] == long.class && !types[1].isPrimitive() && !types[1].isArray();
				if (!read && !write) continue;
				Class<?> struct = read ? types[0] : types[1];
				Class<?> structs = Class.forName(struct.getPackageName() + ".Structs_FFM");
				long ffmSize = structs.getField(struct.getSimpleName() + "_SIZEOF").getLong(null);
				Integer jniSize = jniSizeof(struct.getPackageName(), struct.getSimpleName());
				// JNI copies into a C struct of its own compile-time size, so never pass more
				long size = jniSize == null ? ffmSize : Math.min(ffmSize, jniSize);
				System.out.println("  " + (read ? "read  " : "write ") + struct.getName() + " (" + size + " bytes)");
				Method ffmRead = structs.getMethod(struct.getSimpleName() + "_read", MemorySegment.class, struct);
				Method ffmWrite = structs.getMethod(struct.getSimpleName() + "_write", MemorySegment.class, struct);
				Random random = new Random(struct.getName().hashCode());
				for (int round = 0; round < 20; round++) {
					long buffer = C.malloc(ffmSize);
					try {
						if (read) {
							byte[] bytes = new byte[(int) ffmSize];
							random.nextBytes(bytes);
							C.memmove(buffer, bytes, ffmSize);
							Object viaJni = struct.getConstructor().newInstance();
							invokeJni(jni, viaJni, buffer, size);
							Object viaFfm = struct.getConstructor().newInstance();
							ffmRead.invoke(null, FFM.segment(buffer, size), viaFfm);
							check("read " + struct.getSimpleName() + " round " + round, viaJni, viaFfm);
							reads++;
						} else {
							Object value = randomStruct(struct, random);
							long other = C.malloc(ffmSize);
							try {
								C.memset(buffer, 0, ffmSize);
								C.memset(other, 0, ffmSize);
								invokeJni(jni, buffer, value, size);
								ffmWrite.invoke(null, FFM.segment(other, size), value);
								Object fromJni = struct.getConstructor().newInstance();
								Object fromFfm = struct.getConstructor().newInstance();
								ffmRead.invoke(null, FFM.segment(buffer, size), fromJni);
								ffmRead.invoke(null, FFM.segment(other, size), fromFfm);
								check("write " + struct.getSimpleName() + " round " + round, fromJni, fromFfm);
								writes++;
							} finally {
								C.free(other);
							}
						}
					} finally {
						C.free(buffer);
					}
				}
			}
		}
		System.out.println("Compared " + reads + " struct reads and " + writes + " struct writes");
	}

	static void invokeJni(Method jni, Object a, Object b, long size) throws Exception {
		Class<?>[] types = jni.getParameterTypes();
		if (types.length == 2) jni.invoke(null, a, b);
		else if (types[2] == int.class) jni.invoke(null, a, b, (int) size);
		else jni.invoke(null, a, b, size);
	}

	static Object randomStruct(Class<?> type, Random random) throws Exception {
		Object value = type.getConstructor().newInstance();
		for (Field field : publicFields(type)) {
			Class<?> t = field.getType();
			if (t == int.class) field.setInt(value, random.nextInt());
			else if (t == long.class) field.setLong(value, random.nextLong());
			else if (t == short.class) field.setShort(value, (short) random.nextInt());
			else if (t == byte.class) field.setByte(value, (byte) random.nextInt());
			else if (t == char.class) field.setChar(value, (char) random.nextInt());
			else if (t == boolean.class) field.setBoolean(value, random.nextBoolean());
			else if (t == double.class) field.setDouble(value, random.nextDouble() * 1e6 - 5e5);
			else if (t == float.class) field.setFloat(value, random.nextFloat() * 1000 - 500);
			else if (t.isArray()) {
				Object array = field.get(value);
				if (array == null) continue;
				for (int i = 0; i < Array.getLength(array); i++) {
					if (t == byte[].class) Array.setByte(array, i, (byte) random.nextInt());
					else if (t == int[].class) Array.setInt(array, i, random.nextInt());
					else if (t == long[].class) Array.setLong(array, i, random.nextLong());
					else if (t == short[].class) Array.setShort(array, i, (short) random.nextInt());
					else if (t == char[].class) Array.setChar(array, i, (char) random.nextInt());
					else if (t == double[].class) Array.setDouble(array, i, random.nextDouble());
					else if (t == float[].class) Array.setFloat(array, i, random.nextFloat());
				}
			} else {
				field.set(value, randomStruct(t, random));
			}
		}
		return value;
	}

	static List<Field> publicFields(Class<?> type) {
		List<Field> fields = new ArrayList<>();
		for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				int mods = field.getModifiers();
				if (Modifier.isPublic(mods) && !Modifier.isStatic(mods) && !Modifier.isFinal(mods)) fields.add(field);
			}
		}
		return fields;
	}

	static boolean deepEquals(Object a, Object b) {
		if (a == null || b == null) return a == b;
		if (a instanceof float[] x && b instanceof float[] y) return Arrays.equals(x, y);
		if (a.getClass().isArray()) return Objects.deepEquals(a, b);
		if (a instanceof Number || a instanceof Boolean || a instanceof Character || a instanceof String) return a.equals(b);
		if (a.getClass() != b.getClass()) return false;
		try {
			for (Field field : publicFields(a.getClass())) {
				if (!deepEquals(field.get(a), field.get(b))) return false;
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return true;
	}

	static String describe(Object value) {
		if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) return String.valueOf(value);
		if (value.getClass().isArray()) return Arrays.deepToString(new Object[] {value});
		StringBuilder b = new StringBuilder(value.getClass().getSimpleName()).append(" {");
		try {
			for (Field field : publicFields(value.getClass())) b.append(' ').append(field.getName()).append('=').append(describe(field.get(value)));
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return b.append(" }").toString();
	}

	/* ------------------------------------------------------------------ functions */

	static void checkFunctions(Display display) {
		Shell shell = new Shell(display);
		Label label = new Label(shell, SWT.NONE);
		label.setText("FFM cross check");
		shell.setSize(300, 200);
		shell.open();
		while (display.readAndDispatch()) {
			// flush pending events so allocations are set
		}
		int start = checks;

		check("gtk_get_major_version", GTK.gtk_get_major_version(), GTK_FFM.gtk_get_major_version());
		check("gtk_get_minor_version", GTK.gtk_get_minor_version(), GTK_FFM.gtk_get_minor_version());
		check("addressof_g_free", OS.addressof_g_free(), OS_FFM.addressof_g_free());

		// critical char[] and long[] arrays, pointer result
		char[] text = "Grüße aus FFM €".toCharArray();
		long[] jniWritten = new long[1], ffmWritten = new long[1];
		long jniUtf8 = OS.g_utf16_to_utf8(text, text.length, null, jniWritten, null);
		long ffmUtf8 = OS_FFM.g_utf16_to_utf8(text, text.length, null, ffmWritten, null);
		check("g_utf16_to_utf8 items written", jniWritten[0], ffmWritten[0]);
		check("g_utf16_to_utf8 bytes", utf8(jniUtf8, (int) jniWritten[0]), utf8(ffmUtf8, (int) ffmWritten[0]));
		check("g_utf8_strlen", OS.g_utf8_strlen(jniUtf8, -1), OS_FFM.g_utf8_strlen(ffmUtf8, -1));
		check("strlen", C.strlen(jniUtf8), C_FFM.strlen(ffmUtf8));
		OS.g_free(jniUtf8);
		OS_FFM.g_free(ffmUtf8);

		// booleans and struct out parameters
		long handle = shell.handle;
		check("gtk_widget_get_visible", GTK.gtk_widget_get_visible(handle), GTK_FFM.gtk_widget_get_visible(handle));
		GtkAllocation jniAllocation = new GtkAllocation(), ffmAllocation = new GtkAllocation();
		GTK.gtk_widget_get_allocation(handle, jniAllocation);
		GTK_FFM.gtk_widget_get_allocation(handle, ffmAllocation);
		check("gtk_widget_get_allocation", jniAllocation, ffmAllocation);

		// variadic call with a NULL sentinel
		long settings = GTK.gtk_settings_get_default();
		int[] jniTime = new int[1], ffmTime = new int[1];
		OS.g_object_get(settings, GTK.gtk_double_click_time, jniTime, 0);
		OS_FFM.g_object_get(settings, GTK.gtk_double_click_time, ffmTime, 0);
		check("g_object_get gtk-double-click-time", jniTime[0], ffmTime[0]);

		// Pango: struct pairs and bit-field structs from native arrays
		long context = GDK.gdk_pango_context_get();
		long layout = OS.pango_layout_new(context);
		byte[] utf8 = Converter.wcsToMbcs("Hello FFM world. Second sentence!", true);
		OS_FFM.pango_layout_set_text(layout, utf8, -1);
		for (int index = 0; index < utf8.length - 1; index += 7) {
			PangoRectangle jniPos = new PangoRectangle(), ffmPos = new PangoRectangle();
			OS.pango_layout_index_to_pos(layout, index, jniPos);
			OS_FFM.pango_layout_index_to_pos(layout, index, ffmPos);
			check("pango_layout_index_to_pos " + index, jniPos, ffmPos);
		}
		long[] jniAttrs = new long[1], ffmAttrs = new long[1];
		int[] jniCount = new int[1], ffmCount = new int[1];
		OS.pango_layout_get_log_attrs(layout, jniAttrs, jniCount);
		OS_FFM.pango_layout_get_log_attrs(layout, ffmAttrs, ffmCount);
		check("pango_layout_get_log_attrs count", jniCount[0], ffmCount[0]);
		for (int i = 0; i < jniCount[0]; i++) {
			PangoLogAttr jniAttr = new PangoLogAttr(), ffmAttr = new PangoLogAttr();
			OS.memmove(jniAttr, jniAttrs[0] + (long) i * PangoLogAttr.sizeof, PangoLogAttr.sizeof);
			OS_FFM.memmove(ffmAttr, ffmAttrs[0] + (long) i * PangoLogAttr.sizeof, PangoLogAttr.sizeof);
			check("PangoLogAttr " + i, jniAttr, ffmAttr);
		}
		OS.g_free(jniAttrs[0]);
		OS.g_free(ffmAttrs[0]);
		OS.g_object_unref(layout);
		OS.g_object_unref(context);

		// Cairo: doubles, double[] matrices and unsigned results
		long surface = Cairo.cairo_image_surface_create(Cairo.CAIRO_FORMAT_ARGB32, 64, 32);
		long cairo = Cairo.cairo_create(surface);
		Cairo_FFM.cairo_set_tolerance(cairo, 0.375);
		check("cairo_get_tolerance", Cairo.cairo_get_tolerance(cairo), Cairo_FFM.cairo_get_tolerance(cairo));
		Cairo.cairo_rectangle(cairo, 3.5, 4, 20, 10.25);
		Cairo.cairo_clip(cairo);
		GdkRectangle jniClip = new GdkRectangle(), ffmClip = new GdkRectangle();
		check("gdk_cairo_get_clip_rectangle", GDK.gdk_cairo_get_clip_rectangle(cairo, jniClip), GDK_FFM.gdk_cairo_get_clip_rectangle(cairo, ffmClip));
		check("gdk_cairo_get_clip_rectangle rect", jniClip, ffmClip);
		double[] jniMatrix = new double[6], ffmMatrix = new double[6];
		Cairo.cairo_matrix_init(jniMatrix, 1.5, 0.25, -0.5, 2, 10, -20);
		Cairo_FFM.cairo_matrix_init(ffmMatrix, 1.5, 0.25, -0.5, 2, 10, -20);
		check("cairo_matrix_init", jniMatrix, ffmMatrix);
		double[] jx = {3.5}, jy = {-7.25}, fx = {3.5}, fy = {-7.25};
		Cairo.cairo_matrix_transform_point(jniMatrix, jx, jy);
		Cairo_FFM.cairo_matrix_transform_point(ffmMatrix, fx, fy);
		check("cairo_matrix_transform_point", new double[] {jx[0], jy[0]}, new double[] {fx[0], fy[0]});
		// the same array as result and operand, as Transform.multiply does
		double[] jniAliased = {1, 0, 0, 1, 10, 10}, ffmAliased = {1, 0, 0, 1, 10, 10}, other = {1, 0, 0, 1, 20, 20};
		Cairo.cairo_matrix_multiply(jniAliased, other, jniAliased);
		Cairo_FFM.cairo_matrix_multiply(ffmAliased, other, ffmAliased);
		check("cairo_matrix_multiply aliased", jniAliased, ffmAliased);
		check("cairo_image_surface_get_stride", Cairo.cairo_image_surface_get_stride(surface), Cairo_FFM.cairo_image_surface_get_stride(surface));
		Cairo.cairo_destroy(cairo);
		Cairo.cairo_surface_destroy(surface);

		// UTF-16 offset helpers: C implementation in os_custom.c against the Java port
		String[] samples = {"", "ascii only", "Gr\u00fc\u00dfe", "\u20ac\u20ac\u20ac", "a\ud83d\ude00b\ud83c\udf0e", "\u4e2d\u6587\ud83d\ude00\u00e9x"};
		for (String sample : samples) {
			byte[] bytes = Converter.wcsToMbcs(sample, true);
			long pointer = C.malloc(bytes.length);
			C.memmove(pointer, bytes, bytes.length);
			try {
				for (long max = -1; max < bytes.length + 1; max++) {
					check("g_utf16_strlen(" + sample + "," + max + ")", OS.g_utf16_strlen(pointer, max), FFMUtf16.g_utf16_strlen(pointer, max));
				}
				for (long offset = 0; offset < sample.length() + 2; offset++) {
					check("g_utf16_offset_to_pointer(" + sample + "," + offset + ")",
						OS.g_utf16_offset_to_pointer(pointer, offset) - pointer, FFMUtf16.g_utf16_offset_to_pointer(pointer, offset) - pointer);
					check("g_utf16_offset_to_utf8_offset(" + sample + "," + offset + ")",
						OS.g_utf16_offset_to_utf8_offset(pointer, offset), FFMUtf16.g_utf16_offset_to_utf8_offset(pointer, offset));
					check("g_utf8_offset_to_utf16_offset(" + sample + "," + offset + ")",
						OS.g_utf8_offset_to_utf16_offset(pointer, offset), FFMUtf16.g_utf8_offset_to_utf16_offset(pointer, offset));
				}
				for (long position = 0; position <= bytes.length; position++) {
					check("g_utf16_pointer_to_offset(" + sample + "," + position + ")",
						OS.g_utf16_pointer_to_offset(pointer, pointer + position), FFMUtf16.g_utf16_pointer_to_offset(pointer, pointer + position));
				}
			} finally {
				C.free(pointer);
			}
		}

		// macros without a symbol: type checks, struct fields and arithmetic
		Button button = new Button(shell, SWT.PUSH);
		button.setText("ok");
		long buttonHandle = button.handle, labelHandle = label.handle;
		check("GTK_IS_BUTTON(button)", GTK.GTK_IS_BUTTON(buttonHandle), FFMMacros.GTK_IS_BUTTON(buttonHandle));
		check("GTK_IS_BUTTON(label)", GTK.GTK_IS_BUTTON(labelHandle), FFMMacros.GTK_IS_BUTTON(labelHandle));
		check("GTK_IS_LABEL(label)", GTK.GTK_IS_LABEL(labelHandle), FFMMacros.GTK_IS_LABEL(labelHandle));
		check("GTK_IS_WINDOW(shell)", GTK.GTK_IS_WINDOW(handle), FFMMacros.GTK_IS_WINDOW(handle));
		check("GTK_IS_CONTAINER(shell)", GTK.GTK_IS_CONTAINER(handle), FFMMacros.GTK_IS_CONTAINER(handle));
		check("GTK_IS_PLUG(shell)", GTK.GTK_IS_PLUG(handle), FFMMacros.GTK_IS_PLUG(handle));
		check("GDK_IS_X11_DISPLAY", GDK.GDK_IS_X11_DISPLAY(GDK.gdk_display_get_default()), FFMMacros.GDK_IS_X11_DISPLAY(GDK.gdk_display_get_default()));
		check("GDK_IS_WAYLAND_DISPLAY", GDK.GDK_IS_WAYLAND_DISPLAY(GDK.gdk_display_get_default()), FFMMacros.GDK_IS_WAYLAND_DISPLAY(GDK.gdk_display_get_default()));
		check("G_OBJECT_GET_CLASS", OS.G_OBJECT_GET_CLASS(buttonHandle), FFMMacros.G_OBJECT_GET_CLASS(buttonHandle));
		check("GTK_WIDGET_GET_CLASS", GTK.GTK_WIDGET_GET_CLASS(buttonHandle), FFMMacros.GTK_WIDGET_GET_CLASS(buttonHandle));
		check("G_OBJECT_TYPE", OS.G_OBJECT_TYPE(buttonHandle), FFMMacros.G_OBJECT_TYPE(buttonHandle));
		check("G_OBJECT_TYPE_NAME", OS.G_OBJECT_TYPE_NAME(buttonHandle), FFMMacros.G_OBJECT_TYPE_NAME(buttonHandle));
		check("G_OBJECT_CLASS_CONSTRUCTOR", OS.G_OBJECT_CLASS_CONSTRUCTOR(OS.G_OBJECT_GET_CLASS(buttonHandle)), FFMMacros.G_OBJECT_CLASS_CONSTRUCTOR(OS.G_OBJECT_GET_CLASS(buttonHandle)));
		for (int pixels : new int[] {0, 1, 511, 512, 1024, -1024, 123456}) {
			check("PANGO_PIXELS(" + pixels + ")", OS.PANGO_PIXELS(pixels), FFMMacros.PANGO_PIXELS(pixels));
		}
		check("PTR_sizeof", C.PTR_sizeof(), FFMMacros.PTR_sizeof());
		check("GET_FUNCTION_POINTER_gtk_false", GTK.GET_FUNCTION_POINTER_gtk_false(), FFMMacros.GET_FUNCTION_POINTER_gtk_false());
		check("localeconv_decimal_point", OS.localeconv_decimal_point(), FFMMacros.localeconv_decimal_point());
		check("CAIRO_VERSION_ENCODE", Cairo.CAIRO_VERSION_ENCODE(1, 18, 2), FFMMacros.CAIRO_VERSION_ENCODE(1, 18, 2));

		// the C macros dereference without a null check, so only non-null nodes are compared
		long children = GTK3.gtk_container_get_children(handle);
		if (children != 0) {
			check("g_list_data", OS.g_list_data(children), FFMMacros.g_list_data(children));
			check("g_list_next", OS.g_list_next(children), FFMMacros.g_list_next(children));
			long next = OS.g_list_next(children);
			if (next != 0) check("g_list_previous", OS.g_list_previous(next), FFMMacros.g_list_previous(next));
			OS.g_list_free(children);
		}

		long value = C.malloc(OS.GValue_sizeof());
		C.memset(value, 0, OS.GValue_sizeof());
		OS.g_value_init(value, OS.G_TYPE_INT());
		check("G_VALUE_TYPE", OS.G_VALUE_TYPE(value), FFMMacros.G_VALUE_TYPE(value));
		check("G_VALUE_TYPE_NAME", OS.G_VALUE_TYPE_NAME(value), FFMMacros.G_VALUE_TYPE_NAME(value));
		check("G_IS_VALUE", OS.G_IS_VALUE(value), FFMMacros.G_IS_VALUE(value));
		OS.g_value_unset(value);
		C.free(value);

		long xevent = C.malloc(OS.XEvent_sizeof());
		byte[] noise = new byte[OS.XEvent_sizeof()];
		new Random(7).nextBytes(noise);
		C.memmove(xevent, noise, noise.length);
		check("X_EVENT_TYPE", OS.X_EVENT_TYPE(xevent), FFMMacros.X_EVENT_TYPE(xevent));
		check("X_EVENT_WINDOW", OS.X_EVENT_WINDOW(xevent), FFMMacros.X_EVENT_WINDOW(xevent));
		C.free(xevent);

		long accessible = GTK3.gtk_widget_get_accessible(buttonHandle);
		check("ATK_ACTION_GET_IFACE", ATK.ATK_ACTION_GET_IFACE(accessible), FFMMacros.ATK_ACTION_GET_IFACE(accessible));
		check("ATK_COMPONENT_GET_IFACE", ATK.ATK_COMPONENT_GET_IFACE(accessible), FFMMacros.ATK_COMPONENT_GET_IFACE(accessible));
		check("ATK_TEXT_GET_IFACE", ATK.ATK_TEXT_GET_IFACE(accessible), FFMMacros.ATK_TEXT_GET_IFACE(accessible));
		check("ATK_VALUE_GET_IFACE", ATK.ATK_VALUE_GET_IFACE(accessible), FFMMacros.ATK_VALUE_GET_IFACE(accessible));

		byte[] clicked = Converter.wcsToMbcs("clicked", true);
		long proc = GTK.GET_FUNCTION_POINTER_gtk_false();
		long jniHandler = OS.g_signal_connect(buttonHandle, clicked, proc, 42);
		long ffmHandler = FFMMacros.g_signal_connect(buttonHandle, clicked, proc, 42);
		check("g_signal_connect connects", jniHandler != 0, ffmHandler != 0);
		check("g_signal_connect handler is the next id", jniHandler + 1, ffmHandler);
		OS.g_signal_handler_disconnect(buttonHandle, jniHandler);
		OS.g_signal_handler_disconnect(buttonHandle, ffmHandler);
		button.dispose();

		// GType constants, sizes, versions and calls through a function pointer
		check("G_TYPE_INVALID", OS.G_TYPE_INVALID(), FFMTypes.G_TYPE_INVALID());
		check("G_TYPE_BOOLEAN", OS.G_TYPE_BOOLEAN(), FFMTypes.G_TYPE_BOOLEAN());
		check("G_TYPE_INT", OS.G_TYPE_INT(), FFMTypes.G_TYPE_INT());
		check("G_TYPE_LONG", OS.G_TYPE_LONG(), FFMTypes.G_TYPE_LONG());
		check("G_TYPE_INT64", OS.G_TYPE_INT64(), FFMTypes.G_TYPE_INT64());
		check("G_TYPE_FLOAT", OS.G_TYPE_FLOAT(), FFMTypes.G_TYPE_FLOAT());
		check("G_TYPE_DOUBLE", OS.G_TYPE_DOUBLE(), FFMTypes.G_TYPE_DOUBLE());
		check("G_TYPE_STRING", OS.G_TYPE_STRING(), FFMTypes.G_TYPE_STRING());
		check("GDK_TYPE_RGBA", GDK.GDK_TYPE_RGBA(), FFMTypes.GDK_TYPE_RGBA());
		check("GDK_TYPE_PIXBUF", GDK.GDK_TYPE_PIXBUF(), FFMTypes.GDK_TYPE_PIXBUF());
		check("PANGO_TYPE_LAYOUT", OS.PANGO_TYPE_LAYOUT(), FFMTypes.PANGO_TYPE_LAYOUT());
		check("PANGO_TYPE_FONT_DESCRIPTION", OS.PANGO_TYPE_FONT_DESCRIPTION(), FFMTypes.PANGO_TYPE_FONT_DESCRIPTION());
		check("GTK_TYPE_WIDGET", GTK.GTK_TYPE_WIDGET(), FFMTypes.GTK_TYPE_WIDGET());
		check("GTK_TYPE_WINDOW", GTK.GTK_TYPE_WINDOW(), FFMTypes.GTK_TYPE_WINDOW());
		check("GTK_TYPE_MENU", GTK3.GTK_TYPE_MENU(), FFMTypes.GTK_TYPE_MENU());
		check("GTK_TYPE_CELL_RENDERER_TEXT", GTK.GTK_TYPE_CELL_RENDERER_TEXT(), FFMTypes.GTK_TYPE_CELL_RENDERER_TEXT());
		check("ATK_TYPE_TEXT", ATK.ATK_TYPE_TEXT(), FFMTypes.ATK_TYPE_TEXT());
		check("ATK_TYPE_COMPONENT", ATK.ATK_TYPE_COMPONENT(), FFMTypes.ATK_TYPE_COMPONENT());
		check("GValue_sizeof", OS.GValue_sizeof(), FFMTypes.GValue_sizeof());
		check("GPollFD_sizeof", OS.GPollFD_sizeof(), FFMTypes.GPollFD_sizeof());
		check("GtkTextIter_sizeof", GTK.GtkTextIter_sizeof(), FFMTypes.GtkTextIter_sizeof());
		check("GtkTreeIter_sizeof", GTK.GtkTreeIter_sizeof(), FFMTypes.GtkTreeIter_sizeof());
		check("GtkCellRendererTextClass_sizeof", GTK.GtkCellRendererTextClass_sizeof(), FFMTypes.GtkCellRendererTextClass_sizeof());
		check("glib_major_version", OS.glib_major_version(), FFMTypes.glib_major_version());
		check("glib_minor_version", OS.glib_minor_version(), FFMTypes.glib_minor_version());
		check("glib_micro_version", OS.glib_micro_version(), FFMTypes.glib_micro_version());
		check("GDK_WINDOWING_X11", OS.GDK_WINDOWING_X11(), FFMTypes.GDK_WINDOWING_X11());
		check("GDK_WINDOWING_WAYLAND", OS.GDK_WINDOWING_WAYLAND(), FFMTypes.GDK_WINDOWING_WAYLAND());
		long gtkFalse = GTK.GET_FUNCTION_POINTER_gtk_false();
		check("call through a function pointer", OS.call(gtkFalse, 0, 0, 0, 0), FFMTypes.call(gtkFalse, 0, 0, 0, 0));
		check("ATK call through a function pointer", ATK.call(gtkFalse, 0), FFMTypes.call(gtkFalse, 0));
		check("Call through a function pointer", OS.Call(gtkFalse, 0, 0), FFMTypes.Call(gtkFalse, 0, 0));

		// dynamic function present in GTK 3
		check("gtk_accel_group_new available", GTK.gtk_accel_group_new() != 0, GTK_FFM.gtk_accel_group_new() != 0);

		System.out.println("Compared " + (checks - start) + " function results");
		shell.dispose();
	}

	static String utf8(long pointer, int length) {
		byte[] bytes = new byte[length];
		C.memmove(bytes, pointer, length);
		return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
	}
}
