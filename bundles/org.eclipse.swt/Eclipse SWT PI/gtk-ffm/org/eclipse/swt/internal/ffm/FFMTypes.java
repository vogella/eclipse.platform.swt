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

import org.eclipse.swt.internal.C_FFM;
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

	/** Types that only GTK3 has, and that the JNI library has no natives for under GTK4. */
	static int gtk3Only(long size, String name) {
		if (FFM.GTK4) throw new UnsatisfiedLinkError(name);
		return (int) size;
	}

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
		return gtk3Only(Extra_FFM.GTKCELLRENDERERPIXBUF, "GtkCellRendererPixbuf_sizeof");
	}

	public static int GtkCellRendererPixbufClass_sizeof() {
		return gtk3Only(Extra_FFM.GTKCELLRENDERERPIXBUFCLASS, "GtkCellRendererPixbufClass_sizeof");
	}

	public static int GtkCellRendererToggle_sizeof() {
		return gtk3Only(Extra_FFM.GTKCELLRENDERERTOGGLE, "GtkCellRendererToggle_sizeof");
	}

	public static int GtkCellRendererToggleClass_sizeof() {
		return gtk3Only(Extra_FFM.GTKCELLRENDERERTOGGLECLASS, "GtkCellRendererToggleClass_sizeof");
	}

	/* ---------------------------------------------------------------- boxed types for content providers */

	/** The boxed copy function of the content provider types, which share the pointer. */
	static long contentProvidersCopy(long pointer) {
		return pointer;
	}

	static void contentProvidersFree(long pointer) {
	}

	/** Registers a boxed GType whose values are shared pointers that nobody frees. */
	public static long content_providers_create_gtype(String name) {
		try (Arena arena = Arena.ofConfined()) {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			FunctionDescriptor copy = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG);
			FunctionDescriptor free = FunctionDescriptor.ofVoid(JAVA_LONG);
			long copyStub = FFM.LINKER.upcallStub(lookup.findStatic(FFMTypes.class, "contentProvidersCopy", copy.toMethodType()), copy, Arena.global()).address();
			long freeStub = FFM.LINKER.upcallStub(lookup.findStatic(FFMTypes.class, "contentProvidersFree", free.toMethodType()), free, Arena.global()).address();
			return OS_FFM.g_boxed_type_register_static(name == null ? 0L : arena.allocateFrom(name).address(), copyStub, freeStub);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/** A heap allocated GValue of the type holding <code>value</code>, which the GValue takes over. */
	public static long content_providers_create_gvalue(long gtype, long value) {
		long gvalue = OS_FFM.g_malloc(Extra_FFM.GVALUE);
		C_FFM.memset(gvalue, 0, Extra_FFM.GVALUE);
		OS_FFM.g_value_init(gvalue, gtype);
		OS_FFM.g_value_take_boxed(gvalue, value);
		return gvalue;
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

	/** One handle per shape, taking the function pointer as its first argument; a holder so class init links nothing. */
	static final class Calls {
		static final MethodHandle[] LONGS = new MethodHandle[8];
		static {
			for (int count = 1; count < LONGS.length; count++) {
				LONGS[count] = FFM.LINKER.downcallHandle(longs(count));
			}
		}
		static final MethodHandle INT_LL = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
		static final MethodHandle INT_LII = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_INT));
	}

	static FunctionDescriptor longs(int count) {
		MemoryLayout[] layouts = new MemoryLayout[count];
		java.util.Arrays.fill(layouts, JAVA_LONG);
		return FunctionDescriptor.of(JAVA_LONG, layouts);
	}

	public static long call(long function, long arg0) {
		try {
			return (long) Calls.LONGS[1].invokeExact(MemorySegment.ofAddress(function), arg0);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1) {
		try {
			return (long) Calls.LONGS[2].invokeExact(MemorySegment.ofAddress(function), arg0, arg1);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2) {
		try {
			return (long) Calls.LONGS[3].invokeExact(MemorySegment.ofAddress(function), arg0, arg1, arg2);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3) {
		try {
			return (long) Calls.LONGS[4].invokeExact(MemorySegment.ofAddress(function), arg0, arg1, arg2, arg3);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3, long arg4) {
		try {
			return (long) Calls.LONGS[5].invokeExact(MemorySegment.ofAddress(function), arg0, arg1, arg2, arg3, arg4);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3, long arg4, long arg5) {
		try {
			return (long) Calls.LONGS[6].invokeExact(MemorySegment.ofAddress(function), arg0, arg1, arg2, arg3, arg4, arg5);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static long call(long function, long arg0, long arg1, long arg2, long arg3, long arg4, long arg5, long arg6) {
		try {
			return (long) Calls.LONGS[7].invokeExact(MemorySegment.ofAddress(function), arg0, arg1, arg2, arg3, arg4, arg5, arg6);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static int Call(long proc, long arg1, long arg2) {
		try {
			return (int) Calls.INT_LL.invokeExact(MemorySegment.ofAddress(proc), arg1, arg2);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static int Call(long func, long arg0, int arg1, int arg2) {
		try {
			return (int) Calls.INT_LII.invokeExact(MemorySegment.ofAddress(func), arg0, arg1, arg2);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}
}
