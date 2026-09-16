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
import java.util.*;
import java.util.concurrent.*;

import org.eclipse.swt.internal.C;
import org.eclipse.swt.internal.Converter;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk3.*;

/**
 * Java port of the SwtFixed container of os_custom.c, the GtkContainer every SWT control lives in.
 * The type is registered from Java and its vtable entries are upcall stubs.
 */
public final class FFMSwtFixed {

	static final int PROP_HADJUSTMENT = 1, PROP_VADJUSTMENT = 2, PROP_HSCROLL_POLICY = 3, PROP_VSCROLL_POLICY = 4;

	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	/* calls SWT does not declare as natives */
	static final MethodHandle WIDGET_MAP = FFM.downcall("gtk_widget_map", FunctionDescriptor.ofVoid(JAVA_LONG));
	static final MethodHandle SET_MAPPED = FFM.downcall("gtk_widget_set_mapped", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT));
	static final MethodHandle SET_REALIZED = FFM.downcall("gtk_widget_set_realized", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT));
	static final MethodHandle GET_VISUAL = FFM.downcall("gtk_widget_get_visual", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle SET_WINDOW = FFM.downcall("gtk_widget_set_window", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
	static final MethodHandle SET_BACKGROUND = FFM.downcallOptional("gtk_style_context_set_background", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
	static final MethodHandle VALUE_SET_OBJECT = FFM.downcall("g_value_set_object", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
	static final MethodHandle VALUE_SET_ENUM = FFM.downcall("g_value_set_enum", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT));
	static final MethodHandle VALUE_GET_ENUM = FFM.downcall("g_value_get_enum", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
	static final MethodHandle OVERRIDE_PROPERTY = FFM.downcall("g_object_class_override_property", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_LONG));
	static final MethodHandle ADD_INTERFACE = FFM.downcall("g_type_add_interface_static", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle CONTAINER_GET_TYPE = FFM.downcall("gtk_container_get_type", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle SCROLLABLE_GET_TYPE = FFM.downcall("gtk_scrollable_get_type", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle ATK_OBJECT_INITIALIZE = FFM.downcall("atk_object_initialize", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
	static final MethodHandle ACCESSIBLE_SET_WIDGET = FFM.downcall("gtk_accessible_set_widget", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));

	/** A child of the container with the geometry SWT assigned to it. */
	static final class Child {
		final long widget;
		int x, y, width = -1, height = -1;

		Child(long widget) {
			this.widget = widget;
		}
	}

	/** What the C implementation keeps in SwtFixedPrivate and in the instance struct. */
	static final class State {
		final List<Child> children = new ArrayList<>();
		long hadjustment, vadjustment, accessible;
		int hscrollPolicy, vscrollPolicy;
	}

	static final Map<Long, State> STATE = new ConcurrentHashMap<>();

	static long type, parentClass;

	private FFMSwtFixed() {
	}

	static State state(long fixed) {
		return STATE.computeIfAbsent(fixed, key -> new State());
	}

	/* ---------------------------------------------------------------- type registration */

	public static synchronized long swt_fixed_get_type() {
		if (type != 0) return type;
		try {
			GTypeInfo info = new GTypeInfo();
			info.class_size = (short) Extra_FFM.GTKCONTAINERCLASS;
			info.class_init = stub("classInit", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			info.instance_size = (short) Extra_FFM.GTKCONTAINER;
			info.instance_init = stub("instanceInit", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			long infoPointer = OS.g_malloc(GTypeInfo.sizeof);
			C.memset(infoPointer, 0, GTypeInfo.sizeof);
			OS.memmove(infoPointer, info, GTypeInfo.sizeof);
			type = OS.g_type_register_static((long) CONTAINER_GET_TYPE.invokeExact(), name("SwtFixed"), infoPointer, 0);
			long interfaceInfo = OS.g_malloc(3 * 8);
			C.memset(interfaceInfo, 0, 3 * 8);
			ADD_INTERFACE.invokeExact(type, (long) SCROLLABLE_GET_TYPE.invokeExact(), interfaceInfo);
			return type;
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static long stub(String name, FunctionDescriptor descriptor) throws ReflectiveOperationException {
		MethodHandle handle = LOOKUP.findStatic(FFMSwtFixed.class, name, descriptor.toMethodType());
		return FFM.LINKER.upcallStub(handle, descriptor, Arena.global()).address();
	}

	static void setSlot(long klass, long offset, String name, FunctionDescriptor descriptor) throws ReflectiveOperationException {
		MemorySegment.ofAddress(klass + offset).reinterpret(8).set(JAVA_LONG_UNALIGNED, 0, stub(name, descriptor));
	}

	static void classInit(long klass, long data) {
		try {
			parentClass = OS.g_type_class_peek_parent(klass);
			setSlot(klass, org.eclipse.swt.internal.gtk.Structs_FFM.GObjectClass_SET_PROPERTY_OFFSET, "setProperty", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
			setSlot(klass, org.eclipse.swt.internal.gtk.Structs_FFM.GObjectClass_GET_PROPERTY_OFFSET, "getProperty", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
			setSlot(klass, org.eclipse.swt.internal.gtk.Structs_FFM.GObjectClass_FINALIZE_OFFSET, "finalizeInstance", FunctionDescriptor.ofVoid(JAVA_LONG));

			OVERRIDE_PROPERTY.invokeExact(klass, PROP_HADJUSTMENT, address("hadjustment"));
			OVERRIDE_PROPERTY.invokeExact(klass, PROP_VADJUSTMENT, address("vadjustment"));
			OVERRIDE_PROPERTY.invokeExact(klass, PROP_HSCROLL_POLICY, address("hscroll-policy"));
			OVERRIDE_PROPERTY.invokeExact(klass, PROP_VSCROLL_POLICY, address("vscroll-policy"));

			setSlot(klass, Extra_FFM.GTKWIDGETCLASS_REALIZE, "realize", FunctionDescriptor.ofVoid(JAVA_LONG));
			setSlot(klass, Extra_FFM.GTKWIDGETCLASS_MAP, "map", FunctionDescriptor.ofVoid(JAVA_LONG));
			setSlot(klass, Extra_FFM.GTKWIDGETCLASS_GET_PREFERRED_WIDTH, "getPreferredSize", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG));
			setSlot(klass, Extra_FFM.GTKWIDGETCLASS_GET_PREFERRED_HEIGHT, "getPreferredSize", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG));
			setSlot(klass, Extra_FFM.GTKWIDGETCLASS_SIZE_ALLOCATE, "sizeAllocate", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			setSlot(klass, Extra_FFM.GTKWIDGETCLASS_GET_ACCESSIBLE, "getAccessible", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));

			setSlot(klass, Extra_FFM.GTKCONTAINERCLASS_ADD, "add", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			setSlot(klass, Extra_FFM.GTKCONTAINERCLASS_REMOVE, "remove", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			setSlot(klass, Extra_FFM.GTKCONTAINERCLASS_FORALL, "forall", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
		} catch (Throwable t) {
			FFM.rethrow(t);
		}
	}

	static final Map<String, MemorySegment> NAMES = new ConcurrentHashMap<>();

	/** A NUL terminated copy of the name, as the C code passes string literals. */
	static byte[] name(String text) {
		return Converter.wcsToMbcs(text, true);
	}

	static long address(String name) {
		return NAMES.computeIfAbsent(name, key -> Arena.global().allocateFrom(key)).address();
	}

	static void instanceInit(long instance, long klass) {
		STATE.put(instance, new State());
	}

	/* ---------------------------------------------------------------- GObject */

	static void finalizeInstance(long object) {
		State state = STATE.remove(object);
		if (state != null) {
			if (state.hadjustment != 0) OS.g_object_unref(state.hadjustment);
			if (state.vadjustment != 0) OS.g_object_unref(state.vadjustment);
			if (state.accessible != 0) OS.g_object_unref(state.accessible);
		}
		try {
			long parentFinalize = MemorySegment.ofAddress(parentClass + org.eclipse.swt.internal.gtk.Structs_FFM.GObjectClass_FINALIZE_OFFSET)
				.reinterpret(8).get(JAVA_LONG_UNALIGNED, 0);
			FFM.LINKER.downcallHandle(MemorySegment.ofAddress(parentFinalize), FunctionDescriptor.ofVoid(JAVA_LONG)).invokeExact(object);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static void getProperty(long object, int property, long value, long pspec) {
		State state = state(object);
		try {
			switch (property) {
				case PROP_HADJUSTMENT -> VALUE_SET_OBJECT.invokeExact(value, state.hadjustment);
				case PROP_VADJUSTMENT -> VALUE_SET_OBJECT.invokeExact(value, state.vadjustment);
				case PROP_HSCROLL_POLICY -> VALUE_SET_ENUM.invokeExact(value, state.hscrollPolicy);
				case PROP_VSCROLL_POLICY -> VALUE_SET_ENUM.invokeExact(value, state.vscrollPolicy);
				default -> { /* GTK warns about the property id, which is not worth reproducing */ }
			}
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static void setProperty(long object, int property, long value, long pspec) {
		State state = state(object);
		try {
			switch (property) {
				case PROP_HADJUSTMENT, PROP_VADJUSTMENT -> {
					boolean horizontal = property == PROP_HADJUSTMENT;
					long current = horizontal ? state.hadjustment : state.vadjustment;
					long adjustment = OS.g_value_get_object(value);
					if (adjustment != 0 && current == adjustment) return;
					if (current != 0) OS.g_object_unref(current);
					if (adjustment == 0) adjustment = GTK.gtk_adjustment_new(0, 0, 0, 0, 0, 0);
					adjustment = OS.g_object_ref_sink(adjustment);
					if (horizontal) state.hadjustment = adjustment; else state.vadjustment = adjustment;
					OS.g_object_notify(object, name(horizontal ? "hadjustment" : "vadjustment"));
				}
				case PROP_HSCROLL_POLICY -> state.hscrollPolicy = (int) VALUE_GET_ENUM.invokeExact(value);
				case PROP_VSCROLL_POLICY -> state.vscrollPolicy = (int) VALUE_GET_ENUM.invokeExact(value);
				default -> { /* as in getProperty */ }
			}
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/* ---------------------------------------------------------------- GtkWidget */

	static void realize(long widget) {
		try {
			if (!GTK3.gtk_widget_get_has_window(widget)) {
				long parentRealize = MemorySegment.ofAddress(parentClass + Extra_FFM.GTKWIDGETCLASS_REALIZE)
					.reinterpret(8).get(JAVA_LONG_UNALIGNED, 0);
				FFM.LINKER.downcallHandle(MemorySegment.ofAddress(parentRealize), FunctionDescriptor.ofVoid(JAVA_LONG)).invokeExact(widget);
				return;
			}
			SET_REALIZED.invokeExact(widget, 1);
			GtkAllocation allocation = new GtkAllocation();
			GTK.gtk_widget_get_allocation(widget, allocation);
			GdkWindowAttr attributes = new GdkWindowAttr();
			attributes.window_type = GDK.GDK_WINDOW_CHILD;
			attributes.x = allocation.x;
			attributes.y = allocation.y;
			attributes.width = allocation.width;
			attributes.height = allocation.height;
			attributes.wclass = 0; // GDK_INPUT_OUTPUT
			attributes.visual = (long) GET_VISUAL.invokeExact(widget);
			attributes.event_mask = GDK.GDK_EXPOSURE_MASK | GDK.GDK_SCROLL_MASK | (1 << 23) | GTK3.gtk_widget_get_events(widget);
			int mask = GDK.GDK_WA_X | GDK.GDK_WA_Y | GDK.GDK_WA_VISUAL;
			long window = GTK3.gdk_window_new(GTK.gtk_widget_get_parent_window(widget), attributes, mask);
			SET_WINDOW.invokeExact(widget, window);
			GDK.gdk_window_set_user_data(window, widget);
			if (SET_BACKGROUND != null && GTK.gtk_check_version(3, 18, 0) != 0) {
				SET_BACKGROUND.invokeExact(GTK.gtk_widget_get_style_context(widget), window);
			}
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static void map(long widget) {
		try {
			SET_MAPPED.invokeExact(widget, 1);
			for (Child child : children(widget)) {
				if (GTK.gtk_widget_get_visible(child.widget) && !GTK.gtk_widget_get_mapped(child.widget)) {
					WIDGET_MAP.invokeExact(child.widget);
				}
			}
			// unlike most GTK containers this one does not raise the window, so overlapping
			// children keep the stacking order SWT gave them
			if (GTK3.gtk_widget_get_has_window(widget)) {
				GDK.gdk_window_show_unraised(GTK3.gtk_widget_get_window(widget));
			}
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/**
	 * Creates the accessible the same way swt_fixed_accessible_new does, without its
	 * SWT_IS_FIXED assertion, which only accepts the type registered by the C code.
	 */
	static long getAccessible(long widget) {
		State state = state(widget);
		if (state.accessible == 0) {
			try {
				long accessible = OS.g_object_new(OS.swt_fixed_accessible_get_type(), 0);
				ATK_OBJECT_INITIALIZE.invokeExact(accessible, widget);
				// not every SwtFixed has a matching Java Accessible, see bug 536974
				ACCESSIBLE_SET_WIDGET.invokeExact(accessible, widget);
				state.accessible = accessible;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}
		return state.accessible;
	}

	static void getPreferredSize(long widget, long minimum, long natural) {
		if (minimum != 0) MemorySegment.ofAddress(minimum).reinterpret(4).set(JAVA_INT_UNALIGNED, 0, 0);
		if (natural != 0) MemorySegment.ofAddress(natural).reinterpret(4).set(JAVA_INT_UNALIGNED, 0, 0);
	}

	static void sizeAllocate(long widget, long allocationPointer) {
		GtkAllocation allocation = new GtkAllocation();
		org.eclipse.swt.internal.gtk.Structs_FFM.GtkAllocation_read(FFM.segment(allocationPointer, org.eclipse.swt.internal.gtk.Structs_FFM.GtkAllocation_SIZEOF), allocation);
		GTK3.gtk_widget_set_allocation(widget, allocation);
		boolean hasWindow = GTK3.gtk_widget_get_has_window(widget);
		if (hasWindow && GTK.gtk_widget_get_realized(widget)) {
			GDK.gdk_window_move_resize(GTK3.gtk_widget_get_window(widget), allocation.x, allocation.y, allocation.width, allocation.height);
		}
		GtkRequisition requisition = new GtkRequisition();
		for (Child child : children(widget)) {
			GtkAllocation childAllocation = new GtkAllocation();
			childAllocation.x = child.x;
			childAllocation.y = child.y;
			if (!hasWindow) {
				childAllocation.x += allocation.x;
				childAllocation.y += allocation.y;
			}
			int w = child.width, h = child.height;
			if (w == -1 || h == -1) {
				GTK.gtk_widget_get_preferred_size(child.widget, requisition, null);
				if (w == -1) w = requisition.width;
				if (h == -1) h = requisition.height;
			}
			// GTK warns unless the preferred size is queried before allocating, see bug 486068
			GTK.gtk_widget_get_preferred_size(child.widget, requisition, null);
			childAllocation.width = w;
			childAllocation.height = h;
			GTK3.gtk_widget_size_allocate(child.widget, childAllocation);
		}
	}

	/* ---------------------------------------------------------------- GtkContainer */

	static List<Child> children(long container) {
		return state(container).children;
	}

	static void add(long container, long widget) {
		children(container).add(new Child(widget));
		GTK.gtk_widget_set_parent(widget, container);
	}

	static void remove(long container, long widget) {
		List<Child> children = children(container);
		for (Iterator<Child> it = children.iterator(); it.hasNext();) {
			if (it.next().widget == widget) {
				GTK.gtk_widget_unparent(widget);
				it.remove();
				return;
			}
		}
	}

	static void forall(long container, int includeInternals, long callback, long data) {
		// a foreach traversal goes front to back so that layouts place children in order, while an
		// internal traversal goes back to front because map() does not raise the windows
		List<Child> children = new ArrayList<>(children(container));
		if (includeInternals != 0) Collections.reverse(children);
		try {
			MethodHandle handle = FFM.LINKER.downcallHandle(MemorySegment.ofAddress(callback), FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			for (Child child : children) {
				handle.invokeExact(child.widget, data);
			}
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/* ---------------------------------------------------------------- called from SWT */

	public static void swt_fixed_move(long fixed, long widget, int x, int y) {
		for (Child child : children(fixed)) {
			if (child.widget == widget) {
				child.x = x;
				child.y = y;
				return;
			}
		}
	}

	public static void swt_fixed_resize(long fixed, long widget, int width, int height) {
		for (Child child : children(fixed)) {
			if (child.widget != widget) continue;
			child.width = width;
			child.height = height;
			/*
			 * Allocate the child directly, because the sizing of nested SwtFixed widgets is
			 * otherwise too late for SWT, see bug 487160.
			 */
			GtkAllocation allocation = new GtkAllocation();
			GTK.gtk_widget_get_allocation(widget, allocation);
			GtkAllocation target = new GtkAllocation();
			target.x = allocation.x;
			target.y = allocation.y;
			target.width = width;
			target.height = height;
			GTK.gtk_widget_get_preferred_size(widget, new GtkRequisition(), null);
			GTK3.gtk_widget_size_allocate(widget, target);
			return;
		}
	}

	public static void swt_fixed_restack(long fixed, long widget, long sibling, boolean above) {
		List<Child> children = children(fixed);
		int index = indexOf(children, widget);
		if (index == -1) return;
		Child child = children.remove(index);
		int position = -1;
		if (sibling != 0) {
			position = indexOf(children, sibling);
			if (position != -1 && !above) position++;
		}
		if (position == -1) position = above ? 0 : children.size();
		children.add(position, child);
	}

	static int indexOf(List<Child> children, long widget) {
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i).widget == widget) return i;
		}
		return -1;
	}
}
