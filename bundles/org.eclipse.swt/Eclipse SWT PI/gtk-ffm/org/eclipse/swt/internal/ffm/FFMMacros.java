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

/**
 * Java implementations of the GLib, GTK and ATK macros that have no symbol to link against.
 * Each one does what the macro expands to: a type check, a field of a public struct or a bit of arithmetic.
 */
public final class FFMMacros {

	static final MethodHandle TYPE_CHECK_INSTANCE_IS_A = FFM.downcall("g_type_check_instance_is_a", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
	static final MethodHandle TYPE_INTERFACE_PEEK = FFM.downcall("g_type_interface_peek", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle TYPE_CHECK_VALUE = FFM.downcall("g_type_check_value", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
	static final MethodHandle TYPE_NAME = FFM.downcall("g_type_name", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle SIGNAL_CONNECT_DATA = FFM.downcall("g_signal_connect_data",
		FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT));
	static final MethodHandle LOCALECONV = FFM.downcall("localeconv", FunctionDescriptor.of(JAVA_LONG));

	private FFMMacros() {
	}

	static MemorySegment at(long pointer, long size) {
		return MemorySegment.ofAddress(pointer).reinterpret(size);
	}

	static long pointerField(long pointer, long offset) {
		return pointer == 0 ? 0 : at(pointer, offset + 8).get(JAVA_LONG_UNALIGNED, offset);
	}

	static int intField(long pointer, long offset) {
		return pointer == 0 ? 0 : at(pointer, offset + 4).get(JAVA_INT_UNALIGNED, offset);
	}

	/* ---------------------------------------------------------------- GObject and GType */

	/** G_TYPE_CHECK_INSTANCE_TYPE, used by all the GTK_IS_* and GDK_IS_* macros. */
	public static boolean isA(long instance, long type) {
		if (instance == 0 || type == 0) return false;
		try {
			return (int) TYPE_CHECK_INSTANCE_IS_A.invokeExact(instance, type) != 0;
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/** G_TYPE_INSTANCE_GET_INTERFACE, used by the ATK_*_GET_IFACE macros. */
	public static long getInterface(long instance, long type) {
		if (instance == 0) return 0;
		try {
			return (long) TYPE_INTERFACE_PEEK.invokeExact(G_OBJECT_GET_CLASS(instance), type);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/** GTypeInstance.g_class, the first field of every GObject instance. */
	public static long G_OBJECT_GET_CLASS(long object) {
		return pointerField(object, 0);
	}

	public static long GTK_WIDGET_GET_CLASS(long widget) {
		return pointerField(widget, 0);
	}

	/** GTypeClass.g_type of the class of the instance. */
	public static long G_OBJECT_TYPE(long instance) {
		return pointerField(G_OBJECT_GET_CLASS(instance), 0);
	}

	public static long G_OBJECT_TYPE_NAME(long object) {
		return typeName(G_OBJECT_TYPE(object));
	}

	/** GObjectClass.constructor, whose offset comes from the layout probe. */
	public static long G_OBJECT_CLASS_CONSTRUCTOR(long objectClass) {
		return pointerField(objectClass, org.eclipse.swt.internal.gtk.Structs_FFM.GObjectClass_CONSTRUCTOR_OFFSET);
	}

	public static void G_OBJECT_CLASS_SET_CONSTRUCTOR(long objectClass, long constructor) {
		long offset = org.eclipse.swt.internal.gtk.Structs_FFM.GObjectClass_CONSTRUCTOR_OFFSET;
		at(objectClass, offset + 8).set(JAVA_LONG_UNALIGNED, offset, constructor);
	}

	/** GValue.g_type, the first field of a GValue. */
	public static long G_VALUE_TYPE(long value) {
		return pointerField(value, 0);
	}

	public static long G_VALUE_TYPE_NAME(long value) {
		return typeName(G_VALUE_TYPE(value));
	}

	public static boolean G_IS_VALUE(long value) {
		if (value == 0) return false;
		try {
			return (int) TYPE_CHECK_VALUE.invokeExact(value) != 0;
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static long typeName(long type) {
		if (type == 0) return 0;
		try {
			return (long) TYPE_NAME.invokeExact(type);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/* ---------------------------------------------------------------- lists, errors, arithmetic */

	/** GList.data and GSList.data. */
	public static long g_list_data(long list) {
		return pointerField(list, 0);
	}

	public static long g_slist_data(long list) {
		return pointerField(list, 0);
	}

	/** GList.next and GSList.next. */
	public static long g_list_next(long list) {
		return pointerField(list, 8);
	}

	public static long g_slist_next(long list) {
		return pointerField(list, 8);
	}

	/** GList.prev. */
	public static long g_list_previous(long list) {
		return pointerField(list, 16);
	}

	/** GError.message, after the GQuark domain and the gint code. */
	public static long g_error_get_message(long error) {
		return pointerField(error, 8);
	}

	/** PANGO_PIXELS rounds Pango units to device pixels. */
	public static int PANGO_PIXELS(int dimension) {
		return (dimension + 512) >> 10;
	}

	/** CAIRO_VERSION_ENCODE. */
	public static int CAIRO_VERSION_ENCODE(int major, int minor, int micro) {
		return major * 10000 + minor * 100 + micro;
	}

	/** XAnyEvent.type, the first field of every X event. */
	public static int X_EVENT_TYPE(long xevent) {
		return intField(xevent, 0);
	}

	/** XAnyEvent.window, whose offset comes from the layout probe. */
	public static long X_EVENT_WINDOW(long xevent) {
		return pointerField(xevent, org.eclipse.swt.internal.gtk.Structs_FFM.XAnyEvent_WINDOW_OFFSET);
	}

	/** lconv.decimal_point, the first field of the struct localeconv returns. */
	public static long localeconv_decimal_point() {
		try {
			return pointerField((long) LOCALECONV.invokeExact(), 0);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/** g_signal_connect is g_signal_connect_data without a destroy notify and without flags. */
	public static int g_signal_connect(long instance, byte[] detailedSignal, long proc, long data) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment signal = FFM.copyIn(arena, detailedSignal);
			return (int) (long) SIGNAL_CONNECT_DATA.invokeExact(instance, signal.address(), proc, data, 0L, 0);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long GET_FUNCTION_POINTER_gtk_false() {
		return FFM.address("gtk_false");
	}

	public static int PTR_sizeof() {
		return 8;
	}

	/* ---------------------------------------------------------------- type checks */

	static final java.util.Map<String, Long> TYPES = new java.util.concurrent.ConcurrentHashMap<>();

	/** The GType a g_type getter such as gtk_button_get_type returns, or 0 when the library lacks it. */
	static long type(String getter) {
		return TYPES.computeIfAbsent(getter, name -> {
			MethodHandle handle = FFM.downcallOptional(name, FunctionDescriptor.of(JAVA_LONG));
			if (handle == null) return 0L;
			try {
				return (long) handle.invokeExact();
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		});
	}

	public static boolean GTK_IS_ACCEL_LABEL(long obj) {
		return isA(obj, type("gtk_accel_label_get_type"));
	}

	public static boolean GTK_IS_BOX(long obj) {
		return isA(obj, type("gtk_box_get_type"));
	}

	public static boolean GTK_IS_BUTTON(long obj) {
		return isA(obj, type("gtk_button_get_type"));
	}

	public static boolean GTK_IS_CELL_RENDERER_PIXBUF(long obj) {
		return isA(obj, type("gtk_cell_renderer_pixbuf_get_type"));
	}

	public static boolean GTK_IS_CELL_RENDERER_TEXT(long obj) {
		return isA(obj, type("gtk_cell_renderer_text_get_type"));
	}

	public static boolean GTK_IS_CELL_RENDERER_TOGGLE(long obj) {
		return isA(obj, type("gtk_cell_renderer_toggle_get_type"));
	}

	public static boolean GTK_IS_CONTAINER(long obj) {
		return isA(obj, type("gtk_container_get_type"));
	}

	public static boolean GTK_IS_IM_CONTEXT(long obj) {
		return isA(obj, type("gtk_im_context_get_type"));
	}

	public static boolean GTK_IS_LABEL(long obj) {
		return isA(obj, type("gtk_label_get_type"));
	}

	public static boolean GTK_IS_MENU_ITEM(long obj) {
		return isA(obj, type("gtk_menu_item_get_type"));
	}

	public static boolean GTK_IS_PLUG(long obj) {
		return isA(obj, type("gtk_plug_get_type"));
	}

	public static boolean GTK_IS_SCROLLED_WINDOW(long obj) {
		return isA(obj, type("gtk_scrolled_window_get_type"));
	}

	public static boolean GTK_IS_WINDOW(long obj) {
		return isA(obj, type("gtk_window_get_type"));
	}

	public static boolean GDK_IS_X11_DISPLAY(long display) {
		return isA(display, type("gdk_x11_display_get_type"));
	}

	public static boolean GDK_IS_WAYLAND_DISPLAY(long display) {
		return isA(display, type("gdk_wayland_display_get_type"));
	}

	public static long ATK_ACTION_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_action_get_type"));
	}

	public static long ATK_COMPONENT_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_component_get_type"));
	}

	public static long ATK_EDITABLE_TEXT_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_editable_text_get_type"));
	}

	public static long ATK_HYPERTEXT_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_hypertext_get_type"));
	}

	public static long ATK_SELECTION_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_selection_get_type"));
	}

	public static long ATK_TABLE_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_table_get_type"));
	}

	public static long ATK_TEXT_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_text_get_type"));
	}

	public static long ATK_VALUE_GET_IFACE(long obj) {
		return getInterface(obj, type("atk_value_get_type"));
	}
}
