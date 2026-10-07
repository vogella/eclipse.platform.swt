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
import static org.eclipse.swt.internal.ffm.FFMGtk4.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;

import org.eclipse.swt.internal.Converter;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk4.*;

/**
 * Java port of the GTK4 SwtFixed of os_custom.c: a plain GtkWidget that lays its children out at
 * the positions and sizes SWT assigned. The public natives live in {@link FFMSwtFixed}, which
 * dispatches here under GTK4.
 */
final class FFMSwtFixed4 {

	static final int PROP_HADJUSTMENT = 1, PROP_VADJUSTMENT = 2, PROP_HSCROLL_POLICY = 3, PROP_VSCROLL_POLICY = 4;

	static final int G_SIGNAL_RUN_LAST = 2;

	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	/** A void function of one pointer, called through the function pointer of the parent class. */
	static final MethodHandle PARENT_VOID = FFM.LINKER.downcallHandle(FunctionDescriptor.ofVoid(JAVA_LONG));

	/** A child of the container with the geometry SWT assigned to it. */
	static final class Child {
		final long widget;
		int x, y, width = -1, height = -1;

		Child(long widget) {
			this.widget = widget;
		}
	}

	/** What the C implementation keeps in SwtFixedPrivate. */
	static final class State {
		final List<Child> children = new ArrayList<>();
		long hadjustment, vadjustment;
		int hscrollPolicy, vscrollPolicy;
	}

	static final Map<Long, State> STATE = new ConcurrentHashMap<>();

	/** The name the type registers under, changeable so that a check can register a second one next to the C type. */
	static String typeName = "SwtFixed";

	static final byte[] HADJUSTMENT = Converter.wcsToMbcs("hadjustment", true), VADJUSTMENT = Converter.wcsToMbcs("vadjustment", true);

	static long type, parentClass;
	static int resizeSignal;

	private FFMSwtFixed4() {
	}

	static State state(long fixed) {
		return STATE.computeIfAbsent(fixed, _ -> new State());
	}

	static List<Child> children(long fixed) {
		return state(fixed).children;
	}

	/* ---------------------------------------------------------------- type registration */

	static synchronized long getType() {
		if (type != 0) return type;
		long newType = registerType(GTK_FFM.gtk_widget_get_type(), typeName, Structs_FFM.GtkWidgetClass_SIZEOF,
			stub("classInit", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG)), Extra_FFM.GTKWIDGET);
		addInterface(newType, GTK_FFM.gtk_scrollable_get_type(), 0);
		type = newType;
		return type;
	}

	static long stub(String name, FunctionDescriptor descriptor) {
		return FFMGtk4.stub(LOOKUP, FFMSwtFixed4.class, name, descriptor);
	}

	static void setSlot(long klass, long offset, String name, FunctionDescriptor descriptor) {
		putLong(klass, offset, stub(name, descriptor));
	}

	static void classInit(long klass, long data) {
		try {
			classInit0(klass);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void classInit0(long klass) throws Throwable {
		parentClass = OS_FFM.g_type_class_peek_parent(klass);
		setSlot(klass, Structs_FFM.GObjectClass_SET_PROPERTY_OFFSET, "setProperty", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
		setSlot(klass, Structs_FFM.GObjectClass_GET_PROPERTY_OFFSET, "getProperty", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
		setSlot(klass, Structs_FFM.GObjectClass_FINALIZE_OFFSET, "finalizeInstance", FunctionDescriptor.ofVoid(JAVA_LONG));
		setSlot(klass, Structs_FFM.GObjectClass_DISPOSE_OFFSET, "dispose", FunctionDescriptor.ofVoid(JAVA_LONG));

		OS_FFM.g_object_class_override_property(klass, PROP_HADJUSTMENT, literal("hadjustment"));
		OS_FFM.g_object_class_override_property(klass, PROP_VADJUSTMENT, literal("vadjustment"));
		OS_FFM.g_object_class_override_property(klass, PROP_HSCROLL_POLICY, literal("hscroll-policy"));
		OS_FFM.g_object_class_override_property(klass, PROP_VSCROLL_POLICY, literal("vscroll-policy"));

		setSlot(klass, Extra_FFM.GTKWIDGETCLASS_MEASURE, "measure", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
		setSlot(klass, Structs_FFM.GtkWidgetClass_SIZE_ALLOCATE_OFFSET, "sizeAllocate", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT));

		long ownType = FFMGtk4.getLong(klass, 0);
		resizeSignal = OS_FFM.g_signal_new(literal("resize"), ownType, G_SIGNAL_RUN_LAST, 0, 0L, 0L, 0L, G_TYPE_NONE, 2, G_TYPE_INT, G_TYPE_INT);
	}

	/* ---------------------------------------------------------------- GObject */

	static void callParent(long offset, long object) throws Throwable {
		long function = getLong(parentClass, offset);
		PARENT_VOID.invokeExact(MemorySegment.ofAddress(function), object);
	}

	static void dispose(long object) {
		try {
			dispose0(object);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void dispose0(long object) throws Throwable {
		// the parent is chained even when a pending callback exception surfaces from a downcall
		try {
			long child;
			while ((child = GTK4_FFM.gtk_widget_get_first_child(object)) != 0) {
				remove0(object, child);
				// a child that is not one of ours would keep this loop spinning
				if (GTK4_FFM.gtk_widget_get_first_child(object) == child) GTK_FFM.gtk_widget_unparent(child);
			}
		} finally {
			callParent(Structs_FFM.GObjectClass_DISPOSE_OFFSET, object);
		}
	}

	static void finalizeInstance(long object) {
		try {
			finalizeInstance0(object);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void finalizeInstance0(long object) throws Throwable {
		State state = STATE.remove(object);
		try {
			if (state != null) {
				if (state.hadjustment != 0) OS_FFM.g_object_unref(state.hadjustment);
				if (state.vadjustment != 0) OS_FFM.g_object_unref(state.vadjustment);
			}
		} finally {
			callParent(Structs_FFM.GObjectClass_FINALIZE_OFFSET, object);
		}
	}

	static void getProperty(long object, int property, long value, long pspec) {
		try {
			getProperty0(object, property, value);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void getProperty0(long object, int property, long value) throws Throwable {
		State state = state(object);
		switch (property) {
			case PROP_HADJUSTMENT -> OS_FFM.g_value_set_object(value, state.hadjustment);
			case PROP_VADJUSTMENT -> OS_FFM.g_value_set_object(value, state.vadjustment);
			case PROP_HSCROLL_POLICY -> OS_FFM.g_value_set_enum(value, state.hscrollPolicy);
			case PROP_VSCROLL_POLICY -> OS_FFM.g_value_set_enum(value, state.vscrollPolicy);
			default -> { /* GTK warns about the property id, which is not worth reproducing */ }
		}
	}

	static void setProperty(long object, int property, long value, long pspec) {
		try {
			setProperty0(object, property, value);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void setProperty0(long object, int property, long value) throws Throwable {
		State state = state(object);
		switch (property) {
			case PROP_HADJUSTMENT, PROP_VADJUSTMENT -> {
				boolean horizontal = property == PROP_HADJUSTMENT;
				long current = horizontal ? state.hadjustment : state.vadjustment;
				long adjustment = OS_FFM.g_value_get_object(value);
				if (adjustment != 0 && current == adjustment) return;
				if (current != 0) OS_FFM.g_object_unref(current);
				if (adjustment == 0) adjustment = GTK_FFM.gtk_adjustment_new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
				adjustment = OS_FFM.g_object_ref_sink(adjustment);
				if (horizontal) state.hadjustment = adjustment; else state.vadjustment = adjustment;
				OS_FFM.g_object_notify(object, horizontal ? HADJUSTMENT : VADJUSTMENT);
			}
			case PROP_HSCROLL_POLICY -> state.hscrollPolicy = OS_FFM.g_value_get_enum(value);
			case PROP_VSCROLL_POLICY -> state.vscrollPolicy = OS_FFM.g_value_get_enum(value);
			default -> { /* as in getProperty */ }
		}
	}

	/* ---------------------------------------------------------------- GtkWidget */

	static void measure(long widget, int orientation, int forSize, long minimum, long natural, long minimumBaseline, long naturalBaseline) {
		try {
			measure0(widget, orientation, minimum, natural, minimumBaseline, naturalBaseline);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void measure0(long widget, int orientation, long minimum, long natural, long minimumBaseline, long naturalBaseline) {
		int[] childNatural = new int[1];
		for (long child = GTK4_FFM.gtk_widget_get_first_child(widget); child != 0; child = GTK4_FFM.gtk_widget_get_next_sibling(child)) {
			GTK4_FFM.gtk_widget_measure(child, orientation, -1, null, childNatural, null, null);
			putInt(natural, 0, Math.max(getInt(natural, 0), childNatural[0]));
		}
		if (minimum != 0) putInt(minimum, 0, 0);
		if (minimumBaseline != 0) putInt(minimumBaseline, 0, -1);
		if (naturalBaseline != 0) putInt(naturalBaseline, 0, -1);
	}

	static void sizeAllocate(long widget, int width, int height, int baseline) {
		try {
			sizeAllocate0(widget, width, height);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void sizeAllocate0(long widget, int width, int height) throws Throwable {
		OS_FFM.g_signal_emit(widget, resizeSignal, 0, width, height);
		GtkAllocation allocation = new GtkAllocation();
		GtkRequisition requisition = new GtkRequisition();
		// a snapshot: allocating a child sends SWT.Resize, which may add or remove children
		List<Child> live = children(widget);
		Throwable failure = null;
		for (Child child : new ArrayList<>(live)) {
			if (!live.contains(child)) continue;
			try {
				int w = child.width, h = child.height;
				if (w == -1 || h == -1) {
					GTK_FFM.gtk_widget_get_preferred_size(child.widget, requisition, null);
					if (w == -1) w = requisition.width;
					if (h == -1) h = requisition.height;
				}
				allocation.x = child.x;
				allocation.y = child.y;
				allocation.width = w;
				allocation.height = h;
				GTK4_FFM.gtk_widget_size_allocate(child.widget, allocation, -1);
			} catch (Throwable t) {
				// C allocates every child and leaves the exception pending
				if (failure == null) failure = t; else failure.addSuppressed(t);
			}
		}
		if (failure != null) throw failure;
	}

	/* ---------------------------------------------------------------- called from SWT */

	static void restack(long fixed, long widget, long sibling, boolean above) {
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

	static void move(long fixed, long widget, int x, int y) {
		for (Child child : children(fixed)) {
			if (child.widget == widget) {
				child.x = x;
				child.y = y;
				return;
			}
		}
	}

	static void resize(long fixed, long widget, int width, int height) {
		for (Child child : children(fixed)) {
			if (child.widget == widget) {
				child.width = width;
				child.height = height;
				return;
			}
		}
	}

	/** The g_return_if_fail checks of the C code, minus the warning. */
	static boolean valid(long fixed, long widget) {
		return FFMMacros.isA(fixed, getType()) && FFMMacros.isA(widget, widgetType());
	}

	static long widgetType() {
		return GTK_FFM.gtk_widget_get_type();
	}

	static long parent(long widget) {
		return GTK_FFM.gtk_widget_get_parent(widget);
	}

	static void add(long fixed, long widget) {
		if (!valid(fixed, widget) || parent(widget) != 0) return;
		children(fixed).add(new Child(widget));
		GTK_FFM.gtk_widget_set_parent(widget, fixed);
	}

	static void remove(long fixed, long widget) {
		if (!valid(fixed, widget) || parent(widget) != fixed) return;
		remove0(fixed, widget);
	}

	static void remove0(long fixed, long widget) {
		for (Iterator<Child> it = children(fixed).iterator(); it.hasNext();) {
			if (it.next().widget == widget) {
				it.remove();
				GTK_FFM.gtk_widget_unparent(widget);
				return;
			}
		}
	}
}
