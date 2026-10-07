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

import static org.eclipse.swt.tools.ffm.FFMCrossCheck.check;

import java.lang.reflect.*;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.accessibility.gtk.*;
import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk3.*;
import org.eclipse.swt.internal.gtk4.*;

/**
 * JNI against FFM for the GLib and GTK natives the hand written ports of SwtFixed, the accessible type and the
 * scaled paintable call: type getters, type checks, GValue setters, signals, interfaces and boxed types.
 */
public class FFMCrossCheckTypes {

	static final long G_TYPE_NONE = 1L << 2, G_TYPE_INT = 6L << 2, G_TYPE_OBJECT = 20L << 2, G_SIGNAL_RUN_LAST = 2;

	static final int[][] emitted = new int[2][];

	public void jni(long instance, int a, int b, long data) {
		emitted[0] = new int[] {a, b, (int) data};
	}

	public void ffm(long instance, int a, int b, long data) {
		emitted[1] = new int[] {a, b, (int) data};
	}

	static long cstring(String s) {
		byte[] bytes = Converter.wcsToMbcs(s, true);
		long pointer = C.malloc(bytes.length);
		C.memmove(pointer, bytes, bytes.length);
		return pointer;
	}

	static String string(long pointer) {
		byte[] bytes = new byte[C.strlen(pointer)];
		C.memmove(bytes, pointer, bytes.length);
		return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
	}

	/** The GType of a get_type function the natives do not declare, looked up by the FFM side. */
	static long gtype(String getter) throws Exception {
		Method type = FFMMacros.class.getDeclaredMethod("type", String.class);
		type.setAccessible(true);
		return (long) type.invoke(null, getter);
	}

	/** Natives both GTK versions have, on a live <code>widget</code>. */
	static void checkTypes(long widget) throws Exception {
		long widgetType = GTK.gtk_widget_get_type(), scrollable = GTK.gtk_scrollable_get_type();
		check("gtk_widget_get_type", widgetType, GTK_FFM.gtk_widget_get_type());
		check("gtk_widget_get_type is GTK_TYPE_WIDGET", GTK_FFM.gtk_widget_get_type(), GTK.GTK_TYPE_WIDGET());
		check("gtk_scrollable_get_type", scrollable, GTK_FFM.gtk_scrollable_get_type());
		check("gtk_scrollable_get_type is a type", GTK_FFM.gtk_scrollable_get_type() != 0, true);

		long klass = OS.G_OBJECT_GET_CLASS(widget);
		check("g_type_check_instance_is_a widget", OS.g_type_check_instance_is_a(widget, widgetType), OS_FFM.g_type_check_instance_is_a(widget, widgetType));
		check("g_type_check_instance_is_a widget is true", OS_FFM.g_type_check_instance_is_a(widget, widgetType), true);
		check("g_type_check_instance_is_a scrollable", OS.g_type_check_instance_is_a(widget, scrollable), OS_FFM.g_type_check_instance_is_a(widget, scrollable));
		check("g_type_interface_peek", OS.g_type_interface_peek(klass, scrollable), OS_FFM.g_type_interface_peek(klass, scrollable));

		long jniValue = C.malloc(OS.GValue_sizeof()), ffmValue = C.malloc(OS.GValue_sizeof());
		C.memset(jniValue, 0, OS.GValue_sizeof());
		C.memset(ffmValue, 0, OS.GValue_sizeof());
		check("g_type_check_value empty", OS.g_type_check_value(jniValue), OS_FFM.g_type_check_value(ffmValue));
		long orientation = gtype("gtk_orientation_get_type");
		OS.g_value_init(jniValue, orientation);
		OS.g_value_init(ffmValue, orientation);
		check("g_type_check_value initialized", OS.g_type_check_value(jniValue), OS_FFM.g_type_check_value(ffmValue));
		check("g_type_check_value initialized is true", OS_FFM.g_type_check_value(ffmValue), true);
		OS.g_value_set_enum(jniValue, 1);
		OS_FFM.g_value_set_enum(ffmValue, 1);
		check("g_value_set_enum", OS.g_value_get_enum(jniValue), OS_FFM.g_value_get_enum(ffmValue));
		check("g_value_set_enum value", OS_FFM.g_value_get_enum(ffmValue), 1);
		check("g_value_get_enum across", OS.g_value_get_enum(ffmValue), OS_FFM.g_value_get_enum(jniValue));
		OS.g_value_unset(jniValue);
		OS.g_value_unset(ffmValue);

		long pixbuf = GDK.gdk_pixbuf_new(0, true, 8, 4, 3);
		OS.g_value_init(jniValue, GDK.GDK_TYPE_PIXBUF());
		OS.g_value_init(ffmValue, GDK.GDK_TYPE_PIXBUF());
		OS.g_value_set_object(jniValue, pixbuf);
		OS_FFM.g_value_set_object(ffmValue, pixbuf);
		check("g_value_set_object", OS.g_value_get_object(jniValue), OS_FFM.g_value_get_object(ffmValue));
		check("g_value_set_object value", OS_FFM.g_value_get_object(ffmValue), pixbuf);
		check("g_value_set_object ref count", OS.g_value_get_object(jniValue), OS.g_value_get_object(ffmValue));
		OS.g_value_unset(jniValue);
		OS.g_value_unset(ffmValue);
		OS.g_object_unref(pixbuf);
		C.free(jniValue);
		C.free(ffmValue);

		// a signal with two int parameters, emitted with its parameters through variadic calls
		FFMCrossCheckTypes handler = new FFMCrossCheckTypes();
		Callback jniCallback = new Callback(handler, "jni", void.class, new Type[] {long.class, int.class, int.class, long.class});
		Callback ffmCallback = new Callback(handler, "ffm", void.class, new Type[] {long.class, int.class, int.class, long.class});
		int jniSignal = OS.g_signal_new(cstring("swt-xcheck-jni"), widgetType, (int) G_SIGNAL_RUN_LAST, 0, 0, 0, 0, G_TYPE_NONE, 2, G_TYPE_INT, G_TYPE_INT);
		int ffmSignal = OS_FFM.g_signal_new(cstring("swt-xcheck-ffm"), widgetType, (int) G_SIGNAL_RUN_LAST, 0, 0, 0, 0, G_TYPE_NONE, 2, G_TYPE_INT, G_TYPE_INT);
		check("g_signal_new ids are consecutive", ffmSignal, jniSignal + 1);
		check("g_signal_new id", jniSignal != 0, ffmSignal != 0);
		long jniHandler = OS.g_signal_connect_data(widget, Converter.wcsToMbcs("swt-xcheck-jni", true), jniCallback.getAddress(), 11, 0, 0);
		long ffmHandler = OS.g_signal_connect_data(widget, Converter.wcsToMbcs("swt-xcheck-ffm", true), ffmCallback.getAddress(), 11, 0, 0);
		check("g_signal_connect_data ids are consecutive", ffmHandler, jniHandler + 1);
		long ffmHandler2 = OS_FFM.g_signal_connect_data(widget, Converter.wcsToMbcs("swt-xcheck-ffm", true), ffmCallback.getAddress(), 11, 0, 0);
		check("g_signal_connect_data through FFM", ffmHandler2, ffmHandler + 1);
		OS.g_signal_handler_disconnect(widget, ffmHandler2);
		OS.g_signal_emit(widget, jniSignal, 0, -7, 1 << 20);
		OS_FFM.g_signal_emit(widget, ffmSignal, 0, -7, 1 << 20);
		check("g_signal_emit parameters", emitted[0], emitted[1]);
		check("g_signal_emit parameters value", emitted[1], new int[] {-7, 1 << 20, 11});
		OS.g_signal_handler_disconnect(widget, jniHandler);
		OS.g_signal_handler_disconnect(widget, ffmHandler);
		jniCallback.dispose();
		ffmCallback.dispose();

		// boxed types and an interface on a type registered from Java, the way the ports do it
		long copy = OS.addressof_g_free(), free = OS.addressof_g_free();
		long jniBoxed = OS.g_boxed_type_register_static(cstring("SwtXcheckBoxedJni"), copy, free);
		long ffmBoxed = OS_FFM.g_boxed_type_register_static(cstring("SwtXcheckBoxedFfm"), copy, free);
		check("g_boxed_type_register_static", jniBoxed != 0, ffmBoxed != 0);
		check("g_boxed_type_register_static name", string(OS_FFM.g_type_name(ffmBoxed)), "SwtXcheckBoxedFfm");
		check("g_boxed_type_register_static name JNI", string(OS.g_type_name(jniBoxed)), "SwtXcheckBoxedJni");
		check("g_boxed_type_register_static parent", OS.g_type_parent(jniBoxed), OS_FFM.g_type_parent(ffmBoxed));

		long jniType = registerObjectType("SwtXcheckObjectJni"), ffmType = registerObjectType("SwtXcheckObjectFfm");
		long interfaceInfo = C.malloc(24);
		C.memset(interfaceInfo, 0, 24);
		OS.g_type_add_interface_static(jniType, scrollable, interfaceInfo);
		OS_FFM.g_type_add_interface_static(ffmType, scrollable, interfaceInfo);
		check("g_type_add_interface_static", OS.g_type_is_a(jniType, scrollable), OS.g_type_is_a(ffmType, scrollable));
		check("g_type_add_interface_static is set", OS.g_type_is_a(ffmType, scrollable), true);
		check("g_type_name through FFM", string(OS_FFM.g_type_name(jniType)), "SwtXcheckObjectJni");
		C.free(interfaceInfo);
	}

	static long registerObjectType(String name) {
		GTypeInfo info = new GTypeInfo();
		info.class_size = (short) org.eclipse.swt.internal.gtk.Structs_FFM.GObjectClass_SIZEOF;
		info.instance_size = 24;
		long pointer = C.malloc(GTypeInfo.sizeof);
		C.memset(pointer, 0, GTypeInfo.sizeof);
		OS.memmove(pointer, info, GTypeInfo.sizeof);
		return OS.g_type_register_static(G_TYPE_OBJECT, Converter.wcsToMbcs(name, true), pointer, 0);
	}

	/** Natives only GTK3 has, which SwtFixed and its accessible use. */
	static void checkGtk3(long widget) {
		check("gtk_container_get_type", GTK3.gtk_container_get_type(), GTK3_FFM.gtk_container_get_type());
		check("gtk_container_accessible_get_type", GTK3.gtk_container_accessible_get_type(), GTK3_FFM.gtk_container_accessible_get_type());
		check("gtk_container_get_type is a type", GTK3_FFM.gtk_container_get_type() != 0, true);
		check("gtk_widget_get_visual", GTK3.gtk_widget_get_visual(widget), GTK3_FFM.gtk_widget_get_visual(widget));
		check("gtk_widget_get_visual is set", GTK3_FFM.gtk_widget_get_visual(widget) != 0, true);

		long label = GTK.gtk_label_new(null);
		OS.g_object_ref_sink(label);
		GTK3.gtk_widget_set_mapped(label, true);
		check("gtk_widget_set_mapped true", GTK.gtk_widget_get_mapped(label), true);
		GTK3_FFM.gtk_widget_set_mapped(label, false);
		check("gtk_widget_set_mapped false", GTK.gtk_widget_get_mapped(label), false);
		GTK3_FFM.gtk_widget_set_mapped(label, true);
		check("gtk_widget_set_mapped true through FFM", GTK.gtk_widget_get_mapped(label), true);
		GTK3.gtk_widget_set_mapped(label, false);
		OS.g_object_unref(label);

		// a GtkAccessible of its own, the accessible of a widget is a no-op object without an a11y bridge
		long accessible = OS.g_object_new(GTK3.gtk_container_accessible_get_type(), 0);
		long other = OS.g_object_new(GTK3.gtk_container_accessible_get_type(), 0);
		ATK.atk_object_initialize(accessible, widget);
		ATK_FFM.atk_object_initialize(other, widget);
		check("atk_object_initialize", GTK3.gtk_accessible_get_widget(accessible), GTK3.gtk_accessible_get_widget(other));
		GTK3.gtk_accessible_set_widget(accessible, widget);
		check("gtk_accessible_set_widget", GTK3.gtk_accessible_get_widget(accessible), GTK3_FFM.gtk_accessible_get_widget(accessible));
		check("gtk_accessible_set_widget value", GTK3_FFM.gtk_accessible_get_widget(accessible), widget);
		GTK3.gtk_accessible_set_widget(accessible, 0);
		check("gtk_accessible_set_widget NULL", GTK3.gtk_accessible_get_widget(accessible), GTK3_FFM.gtk_accessible_get_widget(accessible));
		GTK3_FFM.gtk_accessible_set_widget(other, widget);
		check("gtk_accessible_set_widget through FFM", GTK3.gtk_accessible_get_widget(other), widget);
		GTK3_FFM.gtk_accessible_set_widget(other, 0);
		check("gtk_accessible_set_widget NULL through FFM", GTK3.gtk_accessible_get_widget(other), 0L);
		OS.g_object_unref(other);
		OS.g_object_unref(accessible);

		long argc = C.malloc(4), argv = C.malloc(8), program = cstring("ffm-check");
		C.memmove(argc, new int[] {1}, 4);
		C.memmove(argv, new long[] {program}, 8);
		long argvPointer = C.malloc(8);
		C.memmove(argvPointer, new long[] {argv}, 8);
		check("gtk_parse_args", GTK3.gtk_parse_args(argc, argvPointer), GTK3_FFM.gtk_parse_args(argc, argvPointer));
	}

	/** Natives only GTK4 has, for the scaled paintable. */
	static void checkGtk4() {
		check("gdk_paintable_get_type", GTK4.gdk_paintable_get_type(), GTK4_FFM.gdk_paintable_get_type());
		check("gdk_paintable_get_type is a type", GTK4_FFM.gdk_paintable_get_type() != 0, true);
	}
}
