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

import org.eclipse.swt.internal.gtk.*;

/**
 * The GType constants, struct sizes, version numbers and function pointer calls that the C macros provided.
 * The fundamental GTypes are compile time constants, everything else asks the library.
 */
public final class FFMTypes {

	/** G_TYPE_MAKE_FUNDAMENTAL shifts the fundamental type number by G_TYPE_FUNDAMENTAL_SHIFT. */
	static long fundamental(int number) {
		return ((long) number) << 2;
	}

	private FFMTypes() {
	}

	/* ---------------------------------------------------------------- fundamental GTypes */

	public static long G_TYPE_INVALID() {
		return fundamental(0);
	}

	public static long G_TYPE_BOOLEAN() {
		return fundamental(5);
	}

	public static long G_TYPE_INT() {
		return fundamental(6);
	}

	public static long G_TYPE_LONG() {
		return fundamental(8);
	}

	public static long G_TYPE_INT64() {
		return fundamental(10);
	}

	public static long G_TYPE_FLOAT() {
		return fundamental(14);
	}

	public static long G_TYPE_DOUBLE() {
		return fundamental(15);
	}

	public static long G_TYPE_STRING() {
		return fundamental(16);
	}

	/* ---------------------------------------------------------------- registered GTypes */

	public static long GDK_TYPE_RGBA() {
		return FFMMacros.type("gdk_rgba_get_type");
	}

	public static long GDK_TYPE_PIXBUF() {
		return FFMMacros.type("gdk_pixbuf_get_type");
	}

	public static long PANGO_TYPE_FONT_DESCRIPTION() {
		return FFMMacros.type("pango_font_description_get_type");
	}

	public static long PANGO_TYPE_FONT_FAMILY() {
		return FFMMacros.type("pango_font_family_get_type");
	}

	public static long PANGO_TYPE_FONT_FACE() {
		return FFMMacros.type("pango_font_face_get_type");
	}

	public static long PANGO_TYPE_LAYOUT() {
		return FFMMacros.type("pango_layout_get_type");
	}

	public static long GTK_TYPE_WIDGET() {
		return FFMMacros.type("gtk_widget_get_type");
	}

	public static long GTK_TYPE_WINDOW() {
		return FFMMacros.type("gtk_window_get_type");
	}

	public static long GTK_TYPE_MENU() {
		return FFMMacros.type("gtk_menu_get_type");
	}

	public static long GTK_TYPE_FILE_FILTER() {
		return FFMMacros.type("gtk_file_filter_get_type");
	}

	public static long GTK_TYPE_IM_MULTICONTEXT() {
		return FFMMacros.type("gtk_im_multicontext_get_type");
	}

	public static long GTK_TYPE_CELL_RENDERER_TEXT() {
		return FFMMacros.type("gtk_cell_renderer_text_get_type");
	}

	public static long GTK_TYPE_CELL_RENDERER_PIXBUF() {
		return FFMMacros.type("gtk_cell_renderer_pixbuf_get_type");
	}

	public static long GTK_TYPE_CELL_RENDERER_TOGGLE() {
		return FFMMacros.type("gtk_cell_renderer_toggle_get_type");
	}

	public static long GTK_TYPE_TEXT_VIEW_ACCESSIBLE() {
		return FFMMacros.type("gtk_text_view_accessible_get_type");
	}

	public static long ATK_TYPE_ACTION() {
		return FFMMacros.type("atk_action_get_type");
	}

	public static long ATK_TYPE_COMPONENT() {
		return FFMMacros.type("atk_component_get_type");
	}

	public static long ATK_TYPE_EDITABLE_TEXT() {
		return FFMMacros.type("atk_editable_text_get_type");
	}

	public static long ATK_TYPE_HYPERTEXT() {
		return FFMMacros.type("atk_hypertext_get_type");
	}

	public static long ATK_TYPE_SELECTION() {
		return FFMMacros.type("atk_selection_get_type");
	}

	public static long ATK_TYPE_TABLE() {
		return FFMMacros.type("atk_table_get_type");
	}

	public static long ATK_TYPE_TEXT() {
		return FFMMacros.type("atk_text_get_type");
	}

	public static long ATK_TYPE_VALUE() {
		return FFMMacros.type("atk_value_get_type");
	}

	/* ---------------------------------------------------------------- sizes of C types */

	public static int GValue_sizeof() {
		return (int) Extra_FFM.GVALUE;
	}

	public static int GPollFD_sizeof() {
		return (int) Extra_FFM.GPOLLFD;
	}

	public static int GtkTextIter_sizeof() {
		return (int) Extra_FFM.GTKTEXTITER;
	}

	public static int GtkTreeIter_sizeof() {
		return (int) Extra_FFM.GTKTREEITER;
	}

	public static int GtkCellRendererText_sizeof() {
		return (int) Extra_FFM.GTKCELLRENDERERTEXT;
	}

	public static int GtkCellRendererTextClass_sizeof() {
		return (int) Extra_FFM.GTKCELLRENDERERTEXTCLASS;
	}

	public static int GtkCellRendererPixbuf_sizeof() {
		return (int) Extra_FFM.GTKCELLRENDERERPIXBUF;
	}

	public static int GtkCellRendererPixbufClass_sizeof() {
		return (int) Extra_FFM.GTKCELLRENDERERPIXBUFCLASS;
	}

	public static int GtkCellRendererToggle_sizeof() {
		return (int) Extra_FFM.GTKCELLRENDERERTOGGLE;
	}

	public static int GtkCellRendererToggleClass_sizeof() {
		return (int) Extra_FFM.GTKCELLRENDERERTOGGLECLASS;
	}

	/* ---------------------------------------------------------------- versions and windowing */

	/** The glib version variables, which are exported symbols rather than functions. */
	static int version(String symbol) {
		return MemorySegment.ofAddress(FFM.address(symbol)).reinterpret(4).get(JAVA_INT, 0);
	}

	public static int glib_major_version() {
		return version("glib_major_version");
	}

	public static int glib_minor_version() {
		return version("glib_minor_version");
	}

	public static int glib_micro_version() {
		return version("glib_micro_version");
	}

	/** GDK_WINDOWING_X11 tells whether GDK was built with X11 support, which its display type reveals. */
	public static boolean GDK_WINDOWING_X11() {
		return FFMMacros.type("gdk_x11_display_get_type") != 0;
	}

	public static boolean GDK_WINDOWING_WAYLAND() {
		return FFMMacros.type("gdk_wayland_display_get_type") != 0;
	}

	/* ---------------------------------------------------------------- calls through a function pointer */

	static MethodHandle pointer(FunctionDescriptor descriptor, long function) {
		return FFM.LINKER.downcallHandle(MemorySegment.ofAddress(function), descriptor);
	}

	static FunctionDescriptor longs(int count) {
		MemoryLayout[] layouts = new MemoryLayout[count];
		java.util.Arrays.fill(layouts, JAVA_LONG);
		return FunctionDescriptor.of(JAVA_LONG, layouts);
	}

	public static long call(long function, long arg0) {
		try {
			return (long) pointer(longs(1), function).invokeExact(arg0);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1) {
		try {
			return (long) pointer(longs(2), function).invokeExact(arg0, arg1);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2) {
		try {
			return (long) pointer(longs(3), function).invokeExact(arg0, arg1, arg2);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3) {
		try {
			return (long) pointer(longs(4), function).invokeExact(arg0, arg1, arg2, arg3);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3, long arg4) {
		try {
			return (long) pointer(longs(5), function).invokeExact(arg0, arg1, arg2, arg3, arg4);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3, long arg4, long arg5) {
		try {
			return (long) pointer(longs(6), function).invokeExact(arg0, arg1, arg2, arg3, arg4, arg5);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6) {
		try {
			return (long) pointer(longs(7), function).invokeExact(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static int Call(long proc, long arg1, long arg2) {
		try {
			return (int) pointer(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG), proc).invokeExact(arg1, arg2);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static int Call(long func, long arg0, int arg1, int arg2) {
		try {
			return (int) pointer(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_INT), func).invokeExact(arg0, arg1, arg2);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}
}
