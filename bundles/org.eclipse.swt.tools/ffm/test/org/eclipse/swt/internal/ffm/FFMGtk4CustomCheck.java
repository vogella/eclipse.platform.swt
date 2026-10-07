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
package org.eclipse.swt.internal.ffm;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk4.GTK4;

/**
 * Compares the hand written GTK4 ports (SwtFixed, SwtScaledPaintable, content providers, the macros
 * and constants of FFMMacros and FFMTypes) with the JNI natives of libswt-pi4 in one process.
 * Run with SWT_GTK4=1 under Xvfb. Args: probe output of probe-gtk4.c to validate the layouts,
 * optionally "ffm-only" and a trace file to replay what the JNI run recorded without loading any SWT library.
 */
public class FFMGtk4CustomCheck {

	static int checks, failures;

	static void check(String what, Object jni, Object ffm) {
		checks++;
		boolean ok = Objects.equals(jni, ffm);
		if (!ok) failures++;
		if (!ok || VERBOSE) System.out.println((ok ? "OK   " : "FAIL ") + what + ": jni=" + jni + " ffm=" + ffm);
	}

	static final boolean VERBOSE = System.getProperty("check.verbose") != null;

	/* ---------------------------------------------------------------- GTK handles of the check */

	static MethodHandle h(String name, FunctionDescriptor descriptor, Linker.Option... options) {
		MethodHandle handle = FFM.downcallOptional(name, descriptor, options);
		if (handle == null) throw new UnsatisfiedLinkError(name);
		return handle;
	}

	static final FunctionDescriptor L_L = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG);
	static final FunctionDescriptor I_L = FunctionDescriptor.of(JAVA_INT, JAVA_LONG);
	static final FunctionDescriptor V_L = FunctionDescriptor.ofVoid(JAVA_LONG);
	static final FunctionDescriptor V_LL = FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG);

	static final MethodHandle GTK_INIT_CHECK = h("gtk_init_check", FunctionDescriptor.of(JAVA_INT));
	static final MethodHandle OBJECT_NEW = h("g_object_new", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG), Linker.Option.firstVariadicArg(1));
	static final MethodHandle OBJECT_SET_INT = h("g_object_set", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG), Linker.Option.firstVariadicArg(1));
	static final MethodHandle OBJECT_SET_PTR = h("g_object_set", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG), Linker.Option.firstVariadicArg(1));
	static final MethodHandle OBJECT_GET = h("g_object_get", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG), Linker.Option.firstVariadicArg(1));
	static final MethodHandle OBJECT_REF = h("g_object_ref", L_L);
	static final MethodHandle OBJECT_UNREF = h("g_object_unref", V_L);
	static final MethodHandle WINDOW_NEW = h("gtk_window_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle WINDOW_SET_CHILD = h("gtk_window_set_child", V_LL);
	static final MethodHandle WINDOW_DESTROY = h("gtk_window_destroy", V_L);
	static final MethodHandle WIDGET_ALLOCATE = h("gtk_widget_allocate", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG));
	static final MethodHandle WIDGET_GET_ALLOCATION = h("gtk_widget_get_allocation", V_LL);
	static final MethodHandle WIDGET_MEASURE = h("gtk_widget_measure", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle WIDGET_GET_PARENT = h("gtk_widget_get_parent", L_L);
	static final MethodHandle WIDGET_GET_FIRST_CHILD = h("gtk_widget_get_first_child", L_L);
	static final MethodHandle WIDGET_GET_NEXT_SIBLING = h("gtk_widget_get_next_sibling", L_L);
	static final MethodHandle LABEL_NEW = h("gtk_label_new", L_L);
	static final MethodHandle BUTTON_NEW = h("gtk_button_new_with_label", L_L);
	static final MethodHandle BOX_NEW = h("gtk_box_new", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT));
	static final MethodHandle SIGNAL_CONNECT = h("g_signal_connect_data", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT));
	static final MethodHandle TYPE_NAME = h("g_type_name", L_L);
	static final MethodHandle TYPE_PARENT = h("g_type_parent", L_L);
	static final MethodHandle TYPE_QUERY = h("g_type_query", V_LL);
	static final MethodHandle TYPE_IS_A = h("g_type_is_a", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
	static final MethodHandle TYPE_CLASS_REF = h("g_type_class_ref", L_L);
	static final MethodHandle TYPE_CLASS_UNREF = h("g_type_class_unref", V_L);
	static final MethodHandle SCROLLABLE_GET_TYPE = h("gtk_scrollable_get_type", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle ADJUSTMENT_NEW = h("gtk_adjustment_new", FunctionDescriptor.of(JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));
	static final MethodHandle ADJUSTMENT_GET_VALUE = h("gtk_adjustment_get_value", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG));
	static final MethodHandle ADJUSTMENT_GET_UPPER = h("gtk_adjustment_get_upper", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG));
	static final MethodHandle PAINTABLE_GET_TYPE = h("gdk_paintable_get_type", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle PAINTABLE_WIDTH = h("gdk_paintable_get_intrinsic_width", I_L);
	static final MethodHandle PAINTABLE_HEIGHT = h("gdk_paintable_get_intrinsic_height", I_L);
	static final MethodHandle PAINTABLE_RATIO = h("gdk_paintable_get_intrinsic_aspect_ratio", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG));
	static final MethodHandle PAINTABLE_FLAGS = h("gdk_paintable_get_flags", I_L);
	static final MethodHandle PAINTABLE_CURRENT_IMAGE = h("gdk_paintable_get_current_image", L_L);
	static final MethodHandle BYTES_NEW = h("g_bytes_new", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle BYTES_GET_DATA = h("g_bytes_get_data", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle BYTES_GET_SIZE = h("g_bytes_get_size", L_L);
	static final MethodHandle BYTES_UNREF = h("g_bytes_unref", V_L);
	static final MethodHandle MEMORY_TEXTURE_NEW = h("gdk_memory_texture_new", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG));
	static final MethodHandle SNAPSHOT_NEW = h("gtk_snapshot_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle SNAPSHOT_FREE_TO_NODE = h("gtk_snapshot_free_to_node", L_L);
	static final MethodHandle NODE_SERIALIZE = h("gsk_render_node_serialize", L_L);
	static final MethodHandle NODE_UNREF = h("gsk_render_node_unref", V_L);
	static final MethodHandle CAIRO_RENDERER_NEW = h("gsk_cairo_renderer_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle RENDERER_REALIZE = h("gsk_renderer_realize_for_display", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle RENDERER_RENDER_TEXTURE = h("gsk_renderer_render_texture", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle TEXTURE_DOWNLOAD = h("gdk_texture_download", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle TEXTURE_WIDTH = h("gdk_texture_get_width", I_L);
	static final MethodHandle DISPLAY_GET_DEFAULT = h("gdk_display_get_default", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle VALUE_GET_BOXED = h("g_value_get_boxed", L_L);
	static final MethodHandle VALUE_COPY = h("g_value_copy", V_LL);
	static final MethodHandle VALUE_UNSET = h("g_value_unset", V_L);
	static final MethodHandle VALUE_INIT = h("g_value_init", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle FREE = h("g_free", V_L);
	static final MethodHandle MALLOC0 = h("g_malloc0", L_L);
	static final MethodHandle LIST_APPEND = h("g_list_append", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle LIST_PREPEND = h("g_list_prepend", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle SLIST_APPEND = h("g_slist_append", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle ERROR_NEW_LITERAL = h("g_error_new_literal", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG));
	static final MethodHandle POPOVER_MENU_NEW = h("gtk_popover_menu_new_from_model", L_L);
	static final MethodHandle SCROLLED_WINDOW_NEW = h("gtk_scrolled_window_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle IM_MULTICONTEXT_NEW = h("gtk_im_multicontext_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle CELL_TEXT_NEW = h("gtk_cell_renderer_text_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle CELL_PIXBUF_NEW = h("gtk_cell_renderer_pixbuf_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle CELL_TOGGLE_NEW = h("gtk_cell_renderer_toggle_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle FILE_FILTER_NEW = h("gtk_file_filter_new", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle STRDUP = h("g_strdup", L_L);

	static long l(MethodHandle handle, Object... args) {
		try {
			return (long) handle.invokeWithArguments(args);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static int i(MethodHandle handle, Object... args) {
		try {
			return (int) handle.invokeWithArguments(args);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static double d(MethodHandle handle, Object... args) {
		try {
			return (double) handle.invokeWithArguments(args);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static void v(MethodHandle handle, Object... args) {
		try {
			handle.invokeWithArguments(args);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static long str(String text) {
		return FFMGtk4.literal(text);
	}

	static String cstr(long pointer) {
		return pointer == 0 ? null : FFM.segment(pointer, Integer.MAX_VALUE).getString(0);
	}

	/* ---------------------------------------------------------------- the two implementations */

	/** The natives under test, once through JNI and once through the hand written Java. */
	interface Natives {
		String label();
		long fixedType();
		void add(long fixed, long widget);
		void remove(long fixed, long widget);
		void move(long fixed, long widget, int x, int y);
		void resize(long fixed, long widget, int width, int height);
		void restack(long fixed, long widget, long sibling, boolean above);
		long scaledPaintable(long texture, int width, int height);
		long boxedType(String name);
		long boxedValue(long type, long value);
	}

	static final Natives JNI = new Natives() {
		public String label() { return "jni"; }
		public long fixedType() { return OS.swt_fixed_get_type(); }
		public void add(long fixed, long widget) { OS.swt_fixed_add(fixed, widget); }
		public void remove(long fixed, long widget) { OS.swt_fixed_remove(fixed, widget); }
		public void move(long fixed, long widget, int x, int y) { OS.swt_fixed_move(fixed, widget, x, y); }
		public void resize(long fixed, long widget, int width, int height) { OS.swt_fixed_resize(fixed, widget, width, height); }
		public void restack(long fixed, long widget, long sibling, boolean above) { OS.swt_fixed_restack(fixed, widget, sibling, above); }
		public long scaledPaintable(long texture, int width, int height) { return OS.swt_scaled_paintable_new(texture, width, height); }
		public long boxedType(String name) { return OS.content_providers_create_gtype(name); }
		public long boxedValue(long type, long value) { return OS.content_providers_create_gvalue(type, value); }
	};

	static final Natives FFM_NATIVES = new Natives() {
		public String label() { return "ffm"; }
		public long fixedType() { return FFMSwtFixed.swt_fixed_get_type(); }
		public void add(long fixed, long widget) { FFMSwtFixed.swt_fixed_add(fixed, widget); }
		public void remove(long fixed, long widget) { FFMSwtFixed.swt_fixed_remove(fixed, widget); }
		public void move(long fixed, long widget, int x, int y) { FFMSwtFixed.swt_fixed_move(fixed, widget, x, y); }
		public void resize(long fixed, long widget, int width, int height) { FFMSwtFixed.swt_fixed_resize(fixed, widget, width, height); }
		public void restack(long fixed, long widget, long sibling, boolean above) { FFMSwtFixed.swt_fixed_restack(fixed, widget, sibling, above); }
		public long scaledPaintable(long texture, int width, int height) { return FFMSwtFixed.swt_scaled_paintable_new(texture, width, height); }
		public long boxedType(String name) { return FFMTypes.content_providers_create_gtype(name); }
		public long boxedValue(long type, long value) { return FFMTypes.content_providers_create_gvalue(type, value); }
	};

	/* ---------------------------------------------------------------- main */

	public static void main(String[] args) throws Throwable {
		if (!FFM.GTK4) {
			System.out.println("SWT_GTK4=1 is required");
			System.exit(2);
		}
		boolean ffmOnly = args.length > 1 && args[1].equals("ffm-only");
		String traceFile = args.length > 2 ? args[2] : null;
		if (i(GTK_INIT_CHECK) == 0) {
			System.out.println("gtk_init_check failed, is a display available?");
			System.exit(2);
		}
		if (args.length > 0 && !args[0].isEmpty()) layouts(Path.of(args[0]));
		// the types register under other names than the C ones, so that both can live in one process
		FFMSwtFixed4.typeName = "SwtFixedFfm";
		FFMScaledPaintable.typeName = "SwtScaledPaintableFfm";

		if (ffmOnly) {
			List<String> expected = Files.readAllLines(Path.of(traceFile));
			List<String> actual = new ArrayList<>(fixedScenario(FFM_NATIVES));
			actual.addAll(siblingScenario(FFM_NATIVES));
			actual.addAll(paintableScenario(FFM_NATIVES));
			actual.addAll(boxedScenario(FFM_NATIVES));
			compareTraces("ffm-only replay of " + traceFile, expected, actual);
		} else {
			List<String> jni = new ArrayList<>(fixedScenario(JNI));
			jni.addAll(siblingScenario(JNI));
			jni.addAll(paintableScenario(JNI));
			jni.addAll(boxedScenario(JNI));
			List<String> ffm = new ArrayList<>(fixedScenario(FFM_NATIVES));
			ffm.addAll(siblingScenario(FFM_NATIVES));
			ffm.addAll(paintableScenario(FFM_NATIVES));
			ffm.addAll(boxedScenario(FFM_NATIVES));
			compareTraces("SwtFixed, scaled paintable and content providers", jni, ffm);
			if (traceFile != null) Files.write(Path.of(traceFile), jni);
			if (VERBOSE) jni.forEach(line -> System.out.println("  trace " + line));
			macros();
		}
		System.out.println(checks + " checks, " + failures + " failures");
		System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILURES");
		System.exit(failures == 0 ? 0 : 1);
	}

	static void compareTraces(String what, List<String> jni, List<String> ffm) {
		int n = Math.max(jni.size(), ffm.size());
		int before = failures;
		for (int line = 0; line < n; line++) {
			String a = line < jni.size() ? jni.get(line) : "<missing>";
			String b = line < ffm.size() ? ffm.get(line) : "<missing>";
			check("line " + line, a, b);
		}
		System.out.println(what + ": " + n + " observations, " + (failures - before) + " mismatches");
	}

	/* ---------------------------------------------------------------- layouts */

	/** Validates the generated struct layouts the ports use against the output of probe-gtk4.c. */
	static void layouts(Path probe) throws Exception {
		Map<String, Integer> values = new HashMap<>();
		for (String line : Files.readAllLines(probe)) {
			String[] parts = line.split("=");
			if (parts.length == 2) values.put(parts[0], Integer.parseInt(parts[1].trim()));
		}
		Object[][] table = {
			{"GObject_SIZEOF", Extra_FFM.GOBJECT}, {"GObjectClass_SIZEOF", Structs_FFM.GObjectClass_SIZEOF},
			{"GObjectClass_set_property_OFFSET", Structs_FFM.GObjectClass_SET_PROPERTY_OFFSET}, {"GObjectClass_get_property_OFFSET", Structs_FFM.GObjectClass_GET_PROPERTY_OFFSET},
			{"GObjectClass_dispose_OFFSET", Structs_FFM.GObjectClass_DISPOSE_OFFSET}, {"GObjectClass_finalize_OFFSET", Structs_FFM.GObjectClass_FINALIZE_OFFSET},
			{"GTypeInfo_SIZEOF", Structs_FFM.GTypeInfo_SIZEOF}, {"GInterfaceInfo_SIZEOF", Extra_FFM.GINTERFACEINFO},
			{"GtkWidget_SIZEOF", Extra_FFM.GTKWIDGET}, {"GtkWidgetClass_SIZEOF", Structs_FFM.GtkWidgetClass_SIZEOF},
			{"GtkWidgetClass_size_allocate_OFFSET", Structs_FFM.GtkWidgetClass_SIZE_ALLOCATE_OFFSET}, {"GtkWidgetClass_measure_OFFSET", Extra_FFM.GTKWIDGETCLASS_MEASURE},
			{"GdkPaintableInterface_SIZEOF", Extra_FFM.GDKPAINTABLEINTERFACE},
			{"GdkPaintableInterface_snapshot_OFFSET", Extra_FFM.GDKPAINTABLEINTERFACE_SNAPSHOT},
			{"GdkPaintableInterface_get_flags_OFFSET", Extra_FFM.GDKPAINTABLEINTERFACE_GET_FLAGS},
			{"GdkPaintableInterface_get_intrinsic_width_OFFSET", Extra_FFM.GDKPAINTABLEINTERFACE_GET_INTRINSIC_WIDTH},
			{"GdkPaintableInterface_get_intrinsic_height_OFFSET", Extra_FFM.GDKPAINTABLEINTERFACE_GET_INTRINSIC_HEIGHT},
			{"GdkPaintableInterface_get_intrinsic_aspect_ratio_OFFSET", Extra_FFM.GDKPAINTABLEINTERFACE_GET_INTRINSIC_ASPECT_RATIO},
			{"GDK_PAINTABLE_STATIC_SIZE", FFMGtk4.GDK_PAINTABLE_STATIC_SIZE}, {"GDK_PAINTABLE_STATIC_CONTENTS", FFMGtk4.GDK_PAINTABLE_STATIC_CONTENTS},
			{"GValue_SIZEOF", Extra_FFM.GVALUE}, {"GPollFD_SIZEOF", Extra_FFM.GPOLLFD}, {"GtkTextIter_SIZEOF", Extra_FFM.GTKTEXTITER},
			{"GtkTreeIter_SIZEOF", Extra_FFM.GTKTREEITER}, {"GtkCellRendererText_SIZEOF", Extra_FFM.GTKCELLRENDERERTEXT},
			{"GtkCellRendererTextClass_SIZEOF", Extra_FFM.GTKCELLRENDERERTEXTCLASS},
			{"GObject_SIZEOF", FFMScaledPaintable.TEXTURE},
		};
		int before = failures;
		for (Object[] row : table) {
			check("layout " + row[0], values.get(row[0]), ((Number) row[1]).intValue());
		}
		// the instance of the scaled paintable follows the GObject that the probe measured
		check("layout scaled paintable instance size", values.get("GObject_SIZEOF") + 16, FFMScaledPaintable.INSTANCE_SIZEOF);
		System.out.println("layouts against the C probe: " + table.length + " values, " + (failures - before) + " mismatches");
	}

	/* ---------------------------------------------------------------- SwtFixed */

	static List<String> trace;
	static final Map<Long, String> NAMES = new HashMap<>();

	static String name(long widget) {
		return widget == 0 ? "null" : NAMES.getOrDefault(widget, "?");
	}

	static void onResize(long instance, int width, int height, long data) {
		trace.add("resize signal " + name(instance) + " " + width + "x" + height);
	}

	static void onNotify(long instance, long pspec, long data) {
		trace.add("notify " + cstr(data) + " on " + name(instance));
	}

	static long resizeStub, notifyStub;

	static void connectResize(long instance) throws Throwable {
		if (resizeStub == 0) {
			FunctionDescriptor resize = FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG);
			resizeStub = FFM.LINKER.upcallStub(MethodHandles.lookup().findStatic(FFMGtk4CustomCheck.class, "onResize", resize.toMethodType()), resize, Arena.global()).address();
			FunctionDescriptor notify = FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG);
			notifyStub = FFM.LINKER.upcallStub(MethodHandles.lookup().findStatic(FFMGtk4CustomCheck.class, "onNotify", notify.toMethodType()), notify, Arena.global()).address();
		}
		l(SIGNAL_CONNECT, instance, str("resize"), resizeStub, 0L, 0L, 0);
	}

	static void connectNotify(long instance, String property) throws Throwable {
		connectResize(instance);
		l(SIGNAL_CONNECT, instance, str("notify::" + property), notifyStub, str(property), 0L, 0);
	}

	static long named(String name, long widget) {
		NAMES.put(widget, name);
		return widget;
	}

	static final MethodHandle OBJECT_REF_SINK = h("g_object_ref_sink", L_L);

	/** A widget the scenario owns a reference to, so that unparenting it does not finalize it. */
	static long owned(String name, long widget) {
		return named(name, l(OBJECT_REF_SINK, widget));
	}

	static String allocation(long widget) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment rectangle = arena.allocate(16, 4);
			v(WIDGET_GET_ALLOCATION, widget, rectangle.address());
			return rectangle.get(JAVA_INT, 0) + "," + rectangle.get(JAVA_INT, 4) + " " + rectangle.get(JAVA_INT, 8) + "x" + rectangle.get(JAVA_INT, 12);
		}
	}

	static String measure(long widget, int orientation) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(16, 4);
			v(WIDGET_MEASURE, widget, orientation, -1, out.address(), out.address() + 4, out.address() + 8, out.address() + 12);
			return "min " + out.get(JAVA_INT, 0) + " nat " + out.get(JAVA_INT, 4) + " minb " + out.get(JAVA_INT, 8) + " natb " + out.get(JAVA_INT, 12);
		}
	}

	static void state(String step, long... widgets) {
		for (long widget : widgets) {
			trace.add(step + " " + name(widget) + " parent=" + name(l(WIDGET_GET_PARENT, widget)) + " allocation " + allocation(widget));
		}
	}

	static void children(String step, long parent) {
		StringBuilder b = new StringBuilder(step + " children of " + name(parent) + ":");
		for (long c = l(WIDGET_GET_FIRST_CHILD, parent); c != 0; c = l(WIDGET_GET_NEXT_SIBLING, c)) b.append(' ').append(name(c));
		trace.add(b.toString());
	}

	static int criticals;
	static boolean logCounted;
	static Natives removalNatives;
	static long removalFixed, removalChild;

	static int onLog(int level, long fields, long count, long data) {
		// critical and warning
		if ((level & (8 | 16)) != 0) criticals++;
		return 1;
	}

	static void onResizeRemove(long instance, int width, int height, long data) {
		trace.add("resize signal " + name(instance) + " " + width + "x" + height);
		if (removalChild == 0) return;
		long child = removalChild;
		removalChild = 0;
		removalNatives.remove(removalFixed, child);
	}

	/** A Resize listener that removes a later sibling during size allocation: no GTK critical, the others are still allocated. */
	static List<String> siblingScenario(Natives natives) throws Throwable {
		trace = new ArrayList<>();
		NAMES.clear();
		if (!logCounted) {
			logCounted = true;
			FunctionDescriptor writer = FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG);
			long stub = FFM.LINKER.upcallStub(MethodHandles.lookup().findStatic(FFMGtk4CustomCheck.class, "onLog", writer.toMethodType()), writer, Arena.global()).address();
			v(h("g_log_set_writer_func", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG)), stub, 0L, 0L);
		}
		criticals = 0;
		long type = natives.fixedType();
		long window = named("window", l(WINDOW_NEW));
		long parent = named("P", l(OBJECT_NEW, type, 0L));
		v(WINDOW_SET_CHILD, window, parent);
		long first = owned("X", l(OBJECT_NEW, type, 0L));
		long removed = owned("Y", l(LABEL_NEW, str("y")));
		long last = owned("Z", l(LABEL_NEW, str("zzz")));
		for (long child : new long[] {first, removed, last}) natives.add(parent, child);
		natives.resize(parent, first, 10, 10);
		natives.resize(parent, removed, 20, 20);
		natives.resize(parent, last, 30, 30);
		natives.move(parent, last, 5, 6);
		connectResize(parent);
		FunctionDescriptor resize = FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG);
		long stub = FFM.LINKER.upcallStub(MethodHandles.lookup().findStatic(FFMGtk4CustomCheck.class, "onResizeRemove", resize.toMethodType()), resize, Arena.global()).address();
		l(SIGNAL_CONNECT, first, str("resize"), stub, 0L, 0L, 0);
		removalNatives = natives;
		removalFixed = parent;
		removalChild = removed;
		v(WIDGET_ALLOCATE, parent, 200, 100, -1, 0L);
		children("after sibling removal", parent);
		state("sibling removal", parent, first, removed, last);
		trace.add("criticals " + criticals);
		removalNatives = null;
		v(WINDOW_DESTROY, window);
		for (long keep : new long[] {first, removed, last}) v(OBJECT_UNREF, keep);
		trace.add("sibling scenario done");
		return trace;
	}

	static List<String> fixedScenario(Natives natives) throws Throwable {
		trace = new ArrayList<>();
		NAMES.clear();
		long type = natives.fixedType();
		l(TYPE_CLASS_REF, type);
		trace.add("type name " + cstr(l(TYPE_NAME, type)).replaceAll("Ffm$", ""));
		trace.add("type parent " + cstr(l(TYPE_NAME, l(TYPE_PARENT, type))));
		trace.add("is scrollable " + i(TYPE_IS_A, type, l(SCROLLABLE_GET_TYPE)));
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment query = arena.allocate(24, 8);
			v(TYPE_QUERY, type, query.address());
			trace.add("class size " + query.get(JAVA_INT, 16) + " instance size " + query.get(JAVA_INT, 20));
			long klass = l(TYPE_CLASS_REF, type);
			trace.add("measure slot set " + (FFMGtk4.getLong(klass, Extra_FFM.GTKWIDGETCLASS_MEASURE) != 0)
				+ " size_allocate slot set " + (FFMGtk4.getLong(klass, Structs_FFM.GtkWidgetClass_SIZE_ALLOCATE_OFFSET) != 0)
				+ " set_property slot set " + (FFMGtk4.getLong(klass, Structs_FFM.GObjectClass_SET_PROPERTY_OFFSET) != 0)
				+ " dispose slot set " + (FFMGtk4.getLong(klass, Structs_FFM.GObjectClass_DISPOSE_OFFSET) != 0)
				+ " finalize slot set " + (FFMGtk4.getLong(klass, Structs_FFM.GObjectClass_FINALIZE_OFFSET) != 0));
			v(TYPE_CLASS_UNREF, klass);
		}

		long window = named("window", l(WINDOW_NEW));
		long root = named("root", l(OBJECT_NEW, type, 0L));
		v(WINDOW_SET_CHILD, window, root);
		long a = owned("A", l(OBJECT_NEW, type, 0L));
		long b = owned("B", l(LABEL_NEW, str("Hello world")));
		long c = owned("C", l(BUTTON_NEW, str("OK")));
		long dd = owned("D", l(OBJECT_NEW, type, 0L));
		long d1 = owned("D1", l(LABEL_NEW, str("grandchild with a long text")));
		long box = owned("box", l(BOX_NEW, 0, 0));
		long stray = owned("stray", l(LABEL_NEW, str("stray")));
		for (long fixed : new long[] {root, a, dd}) connectResize(fixed);

		// adding, including the cases the C code rejects with g_return_if_fail
		for (long child : new long[] {a, b, c, dd}) natives.add(root, child);
		natives.add(dd, d1);
		children("after add", root);
		natives.add(root, a);
		natives.add(root, d1);
		natives.add(box, stray);
		natives.add(root, 0);
		natives.add(0, stray);
		children("after rejected adds", root);
		state("parents", a, b, c, dd, d1, stray);

		// geometry and layout
		natives.move(root, a, 10, 20);
		natives.resize(root, a, 50, 30);
		natives.move(root, b, 5, 7);
		natives.resize(root, c, 40, -1);
		natives.move(root, c, 100, 100);
		natives.resize(root, dd, 60, 40);
		natives.move(root, dd, 150, 10);
		natives.move(root, stray, 1, 1);
		natives.resize(root, stray, 2, 2);
		natives.resize(dd, d1, 30, 20);
		v(WIDGET_ALLOCATE, root, 300, 200, -1, 0L);
		state("first allocation", root, a, b, c, dd, d1);
		trace.add("root " + measure(root, 0) + " | " + measure(root, 1));
		trace.add("D " + measure(dd, 0) + " | " + measure(dd, 1));
		trace.add("A " + measure(a, 0) + " | " + measure(a, 1));

		// stacking order shows in the order in which nested containers get their resize signal
		natives.restack(root, a, dd, false);
		natives.resize(root, a, 51, 31);
		natives.resize(root, dd, 61, 41);
		v(WIDGET_ALLOCATE, root, 310, 200, -1, 0L);
		state("after restack above=false", root, a, dd);
		natives.restack(root, a, dd, true);
		natives.resize(root, a, 52, 32);
		natives.resize(root, dd, 62, 42);
		v(WIDGET_ALLOCATE, root, 320, 200, -1, 0L);
		state("after restack above=true", root, a, dd);
		natives.restack(root, dd, 0, true);
		natives.resize(root, a, 53, 33);
		natives.resize(root, dd, 63, 43);
		v(WIDGET_ALLOCATE, root, 330, 200, -1, 0L);
		natives.restack(root, a, 0, false);
		natives.resize(root, a, 54, 34);
		natives.resize(root, dd, 64, 44);
		v(WIDGET_ALLOCATE, root, 340, 200, -1, 0L);
		natives.restack(root, a, stray, true);
		natives.restack(root, stray, a, true);
		children("gtk order", root);

		// removing
		natives.remove(root, b);
		natives.remove(root, b);
		natives.remove(root, d1);
		natives.remove(box, stray);
		natives.remove(root, 0);
		children("after remove", root);
		state("parents after remove", b, d1);
		natives.move(root, b, 3, 3);
		natives.resize(root, b, 3, 3);
		natives.resize(root, c, 41, 11);
		v(WIDGET_ALLOCATE, root, 350, 210, -1, 0L);
		state("after remove allocation", root, c);
		natives.add(root, b);
		natives.resize(root, b, 22, 12);
		natives.resize(root, c, 42, 12);
		v(WIDGET_ALLOCATE, root, 360, 210, -1, 0L);
		state("after re-adding", root, b, c);
		trace.add("root " + measure(root, 0) + " | " + measure(root, 1));

		// the Scrollable properties
		properties(root);
		// a container that never had adjustments, disposed with children
		long bare = owned("bare", l(OBJECT_NEW, type, 0L));
		long bareChild = owned("bareChild", l(LABEL_NEW, str("x")));
		natives.add(bare, bareChild);
		v(OBJECT_UNREF, bare);
		trace.add("bare child after dispose parent=" + name(l(WIDGET_GET_PARENT, bareChild)));

		// disposing the window disposes the fixed, which has to release every child
		v(WINDOW_DESTROY, window);
		state("after destroy", a, b, c, dd);
		for (long keep : new long[] {a, b, c, dd, d1, box, stray, bareChild}) v(OBJECT_UNREF, keep);
		trace.add("scenario done");
		return trace;
	}

	static long getPointerProperty(long object, String property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(8, 8);
			v(OBJECT_GET, object, str(property), out.address(), 0L);
			return out.get(JAVA_LONG, 0);
		}
	}

	static int getIntProperty(long object, String property) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(8, 8);
			v(OBJECT_GET, object, str(property), out.address(), 0L);
			return out.get(JAVA_INT, 0);
		}
	}

	static void properties(long fixed) throws Throwable {
		connectNotify(fixed, "hadjustment");
		l(SIGNAL_CONNECT, fixed, str("notify::vadjustment"), notifyStub, str("vadjustment"), 0L, 0);
		trace.add("hscroll-policy " + getIntProperty(fixed, "hscroll-policy") + " vscroll-policy " + getIntProperty(fixed, "vscroll-policy"));
		v(OBJECT_SET_INT, fixed, str("hscroll-policy"), 1, 0L);
		trace.add("hscroll-policy " + getIntProperty(fixed, "hscroll-policy") + " vscroll-policy " + getIntProperty(fixed, "vscroll-policy"));
		v(OBJECT_SET_INT, fixed, str("vscroll-policy"), 1, 0L);
		trace.add("hscroll-policy " + getIntProperty(fixed, "hscroll-policy") + " vscroll-policy " + getIntProperty(fixed, "vscroll-policy"));
		trace.add("hadjustment before " + (getPointerProperty(fixed, "hadjustment") != 0));
		long adjustment = l(ADJUSTMENT_NEW, 1.0, 0.0, 100.0, 4.0, 5.0, 6.0);
		v(OBJECT_REF, adjustment);
		v(OBJECT_SET_PTR, fixed, str("hadjustment"), adjustment, 0L);
		long got = getPointerProperty(fixed, "hadjustment");
		trace.add("hadjustment is the one set " + (got == adjustment) + " value " + d(ADJUSTMENT_GET_VALUE, got) + " upper " + d(ADJUSTMENT_GET_UPPER, got));
		v(OBJECT_UNREF, got);
		v(OBJECT_SET_PTR, fixed, str("hadjustment"), adjustment, 0L);
		trace.add("same adjustment set again");
		v(OBJECT_SET_PTR, fixed, str("hadjustment"), 0L, 0L);
		got = getPointerProperty(fixed, "hadjustment");
		trace.add("hadjustment after NULL is new " + (got != 0 && got != adjustment) + " upper " + d(ADJUSTMENT_GET_UPPER, got));
		v(OBJECT_UNREF, got);
		v(OBJECT_SET_PTR, fixed, str("vadjustment"), adjustment, 0L);
		got = getPointerProperty(fixed, "vadjustment");
		trace.add("vadjustment is the one set " + (got == adjustment) + " upper " + d(ADJUSTMENT_GET_UPPER, got));
		v(OBJECT_UNREF, got);
		v(OBJECT_UNREF, adjustment);
	}

	/* ---------------------------------------------------------------- SwtScaledPaintable */

	static long texture(int width, int height) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pixels = arena.allocate((long) width * height * 4, 4);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					// premultiplied RGBA with a gradient and an opaque border
					int alpha = (x == 0 || y == 0 || x == width - 1 || y == height - 1) ? 255 : 128 + (x * 127 / width);
					int r = x * alpha / width, g = y * alpha / height, bl = (x + y) % 2 * alpha;
					pixels.set(JAVA_INT, ((long) y * width + x) * 4, r | g << 8 | bl << 16 | alpha << 24);
				}
			}
			long bytes = l(BYTES_NEW, pixels.address(), (long) width * height * 4);
			// GDK_MEMORY_R8G8B8A8_PREMULTIPLIED
			long texture = l(MEMORY_TEXTURE_NEW, width, height, 2, bytes, (long) width * 4);
			v(BYTES_UNREF, bytes);
			return texture;
		}
	}

	static String serialize(long node) {
		long bytes = l(NODE_SERIALIZE, node);
		long data = l(BYTES_GET_DATA, bytes, 0L);
		String text = cstr(data);
		v(BYTES_UNREF, bytes);
		return text;
	}

	static List<String> paintableScenario(Natives natives) throws Throwable {
		List<String> out = new ArrayList<>();
		long texture = texture(40, 20);
		long renderer = l(CAIRO_RENDERER_NEW);
		int realized = 0;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment error = arena.allocate(8, 8);
			realized = i(RENDERER_REALIZE, renderer, l(DISPLAY_GET_DEFAULT), error.address());
		}
		int[][] sizes = {{20, 10}, {40, 20}, {0, 0}, {30, 0}, {7, 3}, {-5, 8}};
		for (long source : new long[] {texture, 0L}) {
			for (int[] size : sizes) {
				long paintable = natives.scaledPaintable(source, size[0], size[1]);
				String label = (source == 0 ? "null texture " : "texture ") + size[0] + "x" + size[1];
				out.add(label + " type " + cstr(l(TYPE_NAME, FFMGtk4.getLong(FFMGtk4.getLong(paintable, 0), 0))).replaceAll("Ffm$", "")
					+ " is paintable " + i(TYPE_IS_A, FFMGtk4.getLong(FFMGtk4.getLong(paintable, 0), 0), l(PAINTABLE_GET_TYPE)));
				out.add(label + " intrinsic " + i(PAINTABLE_WIDTH, paintable) + "x" + i(PAINTABLE_HEIGHT, paintable)
					+ " ratio " + Double.doubleToLongBits(d(PAINTABLE_RATIO, paintable)) + " (" + d(PAINTABLE_RATIO, paintable) + ") flags " + i(PAINTABLE_FLAGS, paintable));
				for (double[] box : new double[][] {{40, 20}, {80, 40}, {20, 10}, {13.5, 6.25}}) {
					long snapshot = l(SNAPSHOT_NEW);
					GTK4.gdk_paintable_snapshot(paintable, snapshot, box[0], box[1]);
					long node = l(SNAPSHOT_FREE_TO_NODE, snapshot);
					out.add(label + " snapshot " + box[0] + "x" + box[1] + ": " + (node == 0 ? "empty" : serialize(node).replaceAll("(?s)data:[^\"]*", "<data>").replaceAll("\\s+", " ")));
					if (node != 0 && realized != 0) {
						out.add(label + " rendered " + box[0] + "x" + box[1] + ": " + render(renderer, node, (int) Math.ceil(box[0]), (int) Math.ceil(box[1])));
					}
					if (node != 0) v(NODE_UNREF, node);
				}
				// the default current image goes through the snapshot of the interface as well
				long image = l(PAINTABLE_CURRENT_IMAGE, paintable);
				out.add(label + " current image " + (image == 0 ? "none" : image == paintable ? "itself" : "other"));
				if (image != 0) v(OBJECT_UNREF, image);
				v(OBJECT_UNREF, paintable);
			}
		}
		out.add("renderer realized " + (realized != 0));
		v(OBJECT_UNREF, texture);
		return out;
	}

	/** The pixels of the node rendered by the Cairo renderer, as a hash, or the reason it did not render. */
	static String render(long renderer, long node, int width, int height) {
		try (Arena arena = Arena.ofConfined()) {
			// a graphene_rect_t: x, y, width, height as floats
			MemorySegment viewport = arena.allocate(16, 4);
			viewport.set(JAVA_FLOAT, 8, width);
			viewport.set(JAVA_FLOAT, 12, height);
			long rendered = l(RENDERER_RENDER_TEXTURE, renderer, node, viewport.address());
			if (rendered == 0) return "no texture";
			int w = i(TEXTURE_WIDTH, rendered);
			MemorySegment data = arena.allocate((long) w * height * 4 + 64, 4);
			v(TEXTURE_DOWNLOAD, rendered, data.address(), (long) w * 4);
			int hash = 1;
			long nonTransparent = 0;
			for (long offset = 0; offset < (long) w * height * 4; offset += 4) {
				int pixel = data.get(JAVA_INT, offset);
				hash = 31 * hash + pixel;
				if ((pixel >>> 24) != 0) nonTransparent++;
			}
			v(OBJECT_UNREF, rendered);
			return w + "x" + height + " hash " + hash + " opaque pixels " + nonTransparent;
		}
	}

	/* ---------------------------------------------------------------- content providers */

	static List<String> boxedScenario(Natives natives) throws Throwable {
		List<String> out = new ArrayList<>();
		long type = natives.boxedType("SwtCheckBoxed" + natives.label());
		out.add("boxed type registered " + (type != 0) + " name " + cstr(l(TYPE_NAME, type)).replaceAll(natives.label() + "$", "")
			+ " parent " + cstr(l(TYPE_NAME, l(TYPE_PARENT, type))) + " is boxed " + i(TYPE_IS_A, type, FFMTypes.fundamental(18)));
		long payload = l(STRDUP, str("payload"));
		long value = natives.boxedValue(type, payload);
		out.add("gvalue type " + (FFMGtk4.getLong(value, 0) == type) + " boxed is payload " + (l(VALUE_GET_BOXED, value) == payload));
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment copy = arena.allocate(24, 8);
			l(VALUE_INIT, copy.address(), type);
			v(VALUE_COPY, value, copy.address());
			out.add("copy shares the pointer " + (l(VALUE_GET_BOXED, copy.address()) == payload));
			v(VALUE_UNSET, copy.address());
		}
		v(VALUE_UNSET, value);
		out.add("payload survives unset " + cstr(payload));
		v(FREE, value);
		v(FREE, payload);
		return out;
	}

	/* ---------------------------------------------------------------- macros and constants */

	/** Every public static method of FFMMacros and FFMTypes, called with samples through both implementations. */
	static void macros() throws Throwable {
		for (String line : new TreeSet<>(macroScenario())) System.out.println(line);
	}

	static final Class<?>[] JNI_CLASSES = {OS.class, GTK.class, GDK.class, org.eclipse.swt.internal.gtk4.GTK4.class, org.eclipse.swt.internal.C.class, org.eclipse.swt.internal.cairo.Cairo.class};

	static Method jniMethod(String name, Class<?>[] parameters) {
		for (Class<?> type : JNI_CLASSES) {
			try {
				Method method = type.getMethod(name, parameters);
				if (Modifier.isNative(method.getModifiers())) return method;
			} catch (NoSuchMethodException e) {
				// next class
			}
		}
		return null;
	}

	static Object invoke(Method method, Object... args) {
		try {
			return method.invoke(null, args);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof UnsatisfiedLinkError) return "UnsatisfiedLinkError";
			return "threw " + e.getCause();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static List<String> macroScenario() throws Throwable {
		List<String> notes = new ArrayList<>();
		long label = l(LABEL_NEW, str("label"));
		long button = l(BUTTON_NEW, str("button"));
		long box = l(BOX_NEW, 0, 0);
		long window = l(WINDOW_NEW);
		long scrolled = l(SCROLLED_WINDOW_NEW);
		long popover = l(POPOVER_MENU_NEW, 0L);
		long im = l(IM_MULTICONTEXT_NEW);
		long text = l(CELL_TEXT_NEW), pixbuf = l(CELL_PIXBUF_NEW), toggle = l(CELL_TOGGLE_NEW);
		long display = l(DISPLAY_GET_DEFAULT);
		long list = l(LIST_APPEND, l(LIST_APPEND, l(LIST_APPEND, 0L, label), button), box);
		long slist = l(SLIST_APPEND, l(SLIST_APPEND, 0L, label), button);
		long error = l(ERROR_NEW_LITERAL, 1, 2, str("an error"));
		long[] instances = {0, label, button, box, window, scrolled, popover, im, text, pixbuf, toggle, display, l(FILE_FILTER_NEW)};
		long gvalue = l(MALLOC0, 24L);
		long uninitialized = l(MALLOC0, 24L);
		l(VALUE_INIT, gvalue, FFMTypes.G_TYPE_INT());
		long xevent = l(MALLOC0, 192L);
		FFM.segment(xevent, 192).set(JAVA_INT, 0, 12);
		FFM.segment(xevent, 192).set(JAVA_LONG, 32, 0xCAFEL);
		long[] classes = {FFMMacros.G_OBJECT_GET_CLASS(label), FFMMacros.G_OBJECT_GET_CLASS(box), FFMMacros.G_OBJECT_GET_CLASS(window)};
		long[] values = {gvalue, uninitialized, 0};
		int[] ints = {0, 1, 1023, 1024, 1536, 512, -1, 123456};

		int compared = 0, unavailable = 0;
		for (Class<?> owner : new Class<?>[] {FFMMacros.class, FFMTypes.class}) {
			for (Method ffm : owner.getDeclaredMethods()) {
				if (!Modifier.isPublic(ffm.getModifiers()) || !Modifier.isStatic(ffm.getModifiers())) continue;
				String name = ffm.getName();
				if (name.equals("isA") || name.equals("getInterface") || name.startsWith("content_providers") || name.equals("call") || name.equals("Call")) continue;
				Method jni = jniMethod(name, ffm.getParameterTypes());
				if (jni == null) {
					notes.add("note: no JNI native " + name + " under GTK4");
					continue;
				}
				List<Object[]> samples = new ArrayList<>();
				Class<?>[] parameters = ffm.getParameterTypes();
				if (parameters.length == 0) {
					samples.add(new Object[0]);
				} else if (parameters.length == 1 && parameters[0] == int.class) {
					for (int sample : ints) samples.add(new Object[] {sample});
				} else if (parameters.length == 3 && parameters[0] == int.class) {
					samples.add(new Object[] {3, 4, 5});
					samples.add(new Object[] {1, 16, 0});
				} else if (parameters.length == 1 && parameters[0] == long.class) {
					long[] pointers = instances;
					if (name.startsWith("G_OBJECT_CLASS_")) pointers = classes;
					else if (name.startsWith("G_VALUE") || name.equals("G_IS_VALUE")) pointers = values;
					else if (name.startsWith("g_list")) pointers = new long[] {0, list};
					else if (name.startsWith("g_slist")) pointers = new long[] {0, slist};
					else if (name.equals("g_error_get_message")) pointers = new long[] {0, error};
					else if (name.startsWith("X_EVENT")) pointers = new long[] {0, xevent};
					else if (name.startsWith("ATK_")) pointers = new long[] {0};
					// the C macros dereference the pointer, only the type checks accept NULL
					boolean nullSafe = name.startsWith("GTK_IS_") || name.startsWith("GDK_IS_") || name.equals("G_IS_VALUE");
					for (long pointer : pointers) if (pointer != 0 || nullSafe) samples.add(new Object[] {pointer});
				} else if (name.equals("G_OBJECT_CLASS_SET_CONSTRUCTOR") || name.equals("g_signal_connect")) {
					continue;
				} else {
					notes.add("note: " + name + " not sampled");
					continue;
				}
				for (Object[] sample : samples) {
					Object jniResult = invoke(jni, sample);
					Object ffmResult = invoke(ffm, sample);
					String what = owner.getSimpleName() + "." + name + Arrays.toString(sample);
					if (jniResult.equals("UnsatisfiedLinkError")) {
						unavailable++;
						notes.add("note: JNI " + name + " is not part of the GTK4 library, ffm=" + ffmResult);
						continue;
					}
					if (name.equals("localeconv_decimal_point") || name.equals("G_OBJECT_TYPE_NAME") || name.equals("G_VALUE_TYPE_NAME")) {
						jniResult = jniResult instanceof Long p ? cstr(p) : jniResult;
						ffmResult = ffmResult instanceof Long p ? cstr(p) : ffmResult;
					}
					compared++;
					check(what, jniResult, ffmResult);
				}
			}
		}
		// the constructor accessors round trip through a class, and g_signal_connect connects the same handler
		long klass = classes[0];
		long constructor = FFMMacros.G_OBJECT_CLASS_CONSTRUCTOR(klass);
		compared++;
		check("G_OBJECT_CLASS_SET_CONSTRUCTOR round trip", OS.G_OBJECT_CLASS_CONSTRUCTOR(klass), constructor);
		FFMMacros.G_OBJECT_CLASS_SET_CONSTRUCTOR(klass, constructor);
		compared++;
		check("G_OBJECT_CLASS_SET_CONSTRUCTOR keeps the constructor", constructor, OS.G_OBJECT_CLASS_CONSTRUCTOR(klass));
		long target = l(BUTTON_NEW, str("target"));
		int jniId = OS.g_signal_connect(target, "clicked\0".getBytes(), resizeStub == 0 ? 1L : notifyStub, 0);
		int ffmId = FFMMacros.g_signal_connect(target, "clicked\0".getBytes(), resizeStub == 0 ? 1L : notifyStub, 0);
		compared++;
		check("g_signal_connect handler ids are consecutive", jniId + 1, ffmId);
		System.out.println("macros and constants compared: " + compared + ", not part of the GTK4 JNI library: " + unavailable);
		return notes;
	}
}
