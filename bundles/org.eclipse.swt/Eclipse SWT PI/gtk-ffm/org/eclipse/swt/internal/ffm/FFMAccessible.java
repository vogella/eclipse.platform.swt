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
import java.util.*;
import java.util.concurrent.*;

import org.eclipse.swt.internal.Converter;
import org.eclipse.swt.internal.accessibility.gtk.ATK;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk3.GTK3;

/**
 * Java port of the SwtFixedAccessible bridge of os_custom.c. Every ATK function of the type is an
 * upcall stub that forwards to the static method of AccessibleObject the C code called through JNI.
 */
public final class FFMAccessible {

	/** What an ATK function does when the widget has no Java Accessible. */
	enum Mode {
		/** Return 0, as the C stubs do. */
		FORWARD,
		/** Call the implementation of the parent class. */
		PARENT,
		/** Always call the parent afterwards, which only finalize does. */
		CHAIN
	}

	record Slot(String struct, String field, String java, MemoryLayout result, boolean booleanResult, Mode mode, MemoryLayout[] arguments) {

		FunctionDescriptor descriptor() {
			return result == null ? FunctionDescriptor.ofVoid(arguments) : FunctionDescriptor.of(result, arguments);
		}
	}

	static Slot slot(String struct, String field, String java, MemoryLayout result, boolean booleanResult, Mode mode, MemoryLayout... arguments) {
		return new Slot(struct, field, java, result, booleanResult, mode, arguments);
	}

	static final String ACCESSIBLE_OBJECT = "org.eclipse.swt.accessibility.AccessibleObject";
	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	static final MethodHandle CONTAINER_ACCESSIBLE_GET_TYPE = FFM.downcall("gtk_container_accessible_get_type", FunctionDescriptor.of(JAVA_LONG));
	static final MethodHandle ADD_INTERFACE = FFM.downcall("g_type_add_interface_static", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle ACCESSIBLE_SET_WIDGET = FFM.downcall("gtk_accessible_set_widget", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
	static final MethodHandle ACCESSIBLE_GET_WIDGET = FFM.downcall("gtk_accessible_get_widget", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle WIDGET_GET_TOPLEVEL = FFM.downcall("gtk_widget_get_toplevel", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));

	/** The ATK interfaces the type implements, with the function returning their GType. */
	static final Map<String, String> INTERFACES = Map.of(
		"AtkActionIface", "atk_action_get_type",
		"AtkComponentIface", "atk_component_get_type",
		"AtkEditableTextIface", "atk_editable_text_get_type",
		"AtkHypertextIface", "atk_hypertext_get_type",
		"AtkSelectionIface", "atk_selection_get_type",
		"AtkTableIface", "atk_table_get_type",
		"AtkTextIface", "atk_text_get_type",
		"AtkValueIface", "atk_value_get_type");

	static final Slot[] SLOTS = {
		slot("AtkActionIface", "do_action", "atkAction_do_action", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkActionIface", "get_description", "atkAction_get_description", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkActionIface", "get_keybinding", "atkAction_get_keybinding", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkActionIface", "get_n_actions", "atkAction_get_n_actions", JAVA_INT, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkActionIface", "get_name", "atkAction_get_name", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkComponentIface", "ref_accessible_at_point", "atkComponent_ref_accessible_at_point", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT),
		slot("AtkEditableTextIface", "copy_text", "atkEditableText_copy_text", null, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkEditableTextIface", "cut_text", "atkEditableText_cut_text", null, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkEditableTextIface", "delete_text", "atkEditableText_delete_text", null, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkEditableTextIface", "insert_text", "atkEditableText_insert_text", null, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG),
		slot("AtkEditableTextIface", "paste_text", "atkEditableText_paste_text", null, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkEditableTextIface", "set_run_attributes", "atkEditableText_set_run_attributes", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkEditableTextIface", "set_text_contents", "atkEditableText_set_text_contents", null, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG),
		slot("AtkHypertextIface", "get_link", "atkHypertext_get_link", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkHypertextIface", "get_link_index", "atkHypertext_get_link_index", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkHypertextIface", "get_n_links", "atkHypertext_get_n_links", JAVA_INT, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkSelectionIface", "is_child_selected", "atkSelection_is_child_selected", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkSelectionIface", "ref_selection", "atkSelection_ref_selection", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "add_column_selection", "atkTable_add_column_selection", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "add_row_selection", "atkTable_add_row_selection", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "get_caption", "atkTable_get_caption", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkTableIface", "get_column_at_index", "atkTable_get_column_at_index", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "get_column_description", "atkTable_get_column_description", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "get_column_extent_at", "atkTable_get_column_extent_at", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkTableIface", "get_column_header", "atkTable_get_column_header", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "get_index_at", "atkTable_get_index_at", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkTableIface", "get_n_columns", "atkTable_get_n_columns", JAVA_INT, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkTableIface", "get_n_rows", "atkTable_get_n_rows", JAVA_INT, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkTableIface", "get_row_at_index", "atkTable_get_row_at_index", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "get_row_description", "atkTable_get_row_description", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "get_row_extent_at", "atkTable_get_row_extent_at", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkTableIface", "get_row_header", "atkTable_get_row_header", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "get_selected_columns", "atkTable_get_selected_columns", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG),
		slot("AtkTableIface", "get_selected_rows", "atkTable_get_selected_rows", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG),
		slot("AtkTableIface", "get_summary", "atkTable_get_summary", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkTableIface", "is_column_selected", "atkTable_is_column_selected", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "is_row_selected", "atkTable_is_row_selected", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "is_selected", "atkTable_is_selected", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkTableIface", "ref_at", "atkTable_ref_at", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkTableIface", "remove_column_selection", "atkTable_remove_row_selection", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTableIface", "remove_row_selection", "atkTable_remove_row_selection", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTextIface", "add_selection", "atkText_add_selection", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkTextIface", "get_bounded_ranges", "atkText_get_bounded_ranges", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT),
		slot("AtkTextIface", "get_caret_offset", "atkText_get_caret_offset", JAVA_INT, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkTextIface", "get_character_at_offset", "atkText_get_character_at_offset", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTextIface", "get_character_count", "atkText_get_character_count", JAVA_INT, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkTextIface", "get_n_selections", "atkText_get_n_selections", JAVA_INT, false, Mode.FORWARD, JAVA_LONG),
		slot("AtkTextIface", "get_offset_at_point", "atkText_get_offset_at_point", JAVA_INT, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT),
		slot("AtkTextIface", "get_range_extents", "atkText_get_range_extents", null, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG),
		slot("AtkTextIface", "get_run_attributes", "atkText_get_run_attributes", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG),
		slot("AtkTextIface", "get_selection", "atkText_get_selection", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG),
		slot("AtkTextIface", "get_text", "atkText_get_text", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT),
		slot("AtkTextIface", "get_text_after_offset", "atkText_get_text_after_offset", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
		slot("AtkTextIface", "get_text_at_offset", "atkText_get_text_at_offset", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
		slot("AtkTextIface", "get_text_before_offset", "atkText_get_text_before_offset", JAVA_LONG, false, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
		slot("AtkTextIface", "remove_selection", "atkText_remove_selection", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTextIface", "set_caret_offset", "atkText_set_caret_offset", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT),
		slot("AtkTextIface", "set_selection", "atkText_set_selection", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT),
		slot("AtkValueIface", "get_current_value", "atkValue_get_current_value", null, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG),
		slot("AtkValueIface", "get_maximum_value", "atkValue_get_maximum_value", null, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG),
		slot("AtkValueIface", "get_minimum_value", "atkValue_get_minimum_value", null, false, Mode.FORWARD, JAVA_LONG, JAVA_LONG),
		slot("AtkValueIface", "set_current_value", "atkValue_set_current_value", JAVA_INT, true, Mode.FORWARD, JAVA_LONG, JAVA_LONG),
		slot("AtkObjectClass", "get_attributes", "atkObject_get_attributes", JAVA_LONG, false, Mode.PARENT, JAVA_LONG),
		slot("AtkObjectClass", "get_description", "atkObject_get_description", JAVA_LONG, false, Mode.PARENT, JAVA_LONG),
		slot("AtkObjectClass", "get_index_in_parent", "atkObject_get_index_in_parent", JAVA_INT, false, Mode.PARENT, JAVA_LONG),
		slot("AtkObjectClass", "get_n_children", "atkObject_get_n_children", JAVA_INT, false, Mode.PARENT, JAVA_LONG),
		slot("AtkObjectClass", "get_name", "atkObject_get_name", JAVA_LONG, false, Mode.PARENT, JAVA_LONG),
		slot("AtkObjectClass", "get_parent", "atkObject_get_parent", JAVA_LONG, false, Mode.PARENT, JAVA_LONG),
		slot("AtkObjectClass", "get_role", "atkObject_get_role", JAVA_INT, false, Mode.PARENT, JAVA_LONG),
		slot("AtkObjectClass", "ref_child", "atkObject_ref_child", JAVA_LONG, false, Mode.PARENT, JAVA_LONG, JAVA_INT),
		// the C function is swt_fixed_accesssible_ref_state_set, whose spelling hid it from the extraction
		slot("AtkObjectClass", "ref_state_set", "atkObject_ref_state_set", JAVA_LONG, false, Mode.PARENT, JAVA_LONG),
		slot("GObjectClass", "finalize", "gObjectClass_finalize", null, false, Mode.CHAIN, JAVA_LONG),
	};

	/** The accessibles SWT registered a Java Accessible for, which is what has_accessible was. */
	static final Set<Long> REGISTERED = ConcurrentHashMap.newKeySet();
	static final Map<String, MethodHandle> METHODS = new ConcurrentHashMap<>();

	static long type, parentClass;

	private FFMAccessible() {
	}

	/* ---------------------------------------------------------------- called from SWT */

	public static synchronized long swt_fixed_accessible_get_type() {
		if (type != 0) return type;
		try {
			GTypeInfo info = new GTypeInfo();
			info.class_size = (short) Extra_FFM.GTKCONTAINERACCESSIBLECLASS;
			info.class_init = stub("classInit", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			info.instance_size = (short) Extra_FFM.GTKCONTAINERACCESSIBLE;
			long pointer = OS.g_malloc(GTypeInfo.sizeof);
			org.eclipse.swt.internal.C.memset(pointer, 0, GTypeInfo.sizeof);
			OS.memmove(pointer, info, GTypeInfo.sizeof);
			type = OS.g_type_register_static((long) CONTAINER_ACCESSIBLE_GET_TYPE.invokeExact(),
				Converter.wcsToMbcs("SwtFixedAccessible", true), pointer, 0);
			MethodHandle init = LOOKUP.findStatic(FFMAccessible.class, "interfaceInit",
				MethodType.methodType(void.class, String.class, long.class, long.class));
			for (Map.Entry<String, String> entry : INTERFACES.entrySet()) {
				long interfaceInfo = OS.g_malloc(3 * 8);
				org.eclipse.swt.internal.C.memset(interfaceInfo, 0, 3 * 8);
				MemorySegment initStub = FFM.LINKER.upcallStub(init.bindTo(entry.getKey()),
					FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG), Arena.global());
				MemorySegment.ofAddress(interfaceInfo).reinterpret(8).set(JAVA_LONG_UNALIGNED, 0, initStub.address());
				ADD_INTERFACE.invokeExact(type, FFMMacros.type(entry.getValue()), interfaceInfo);
			}
			return type;
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/** SWT calls this when an Accessible exists for the widget, so its ATK functions may be used. */
	public static void swt_fixed_accessible_register_accessible(long accessible, boolean isNative, long toMap) {
		REGISTERED.add(accessible);
		try {
			if (!isNative) ACCESSIBLE_SET_WIDGET.invokeExact(accessible, toMap);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/* ---------------------------------------------------------------- the vtables */

	static void classInit(long klass, long data) {
		try {
			classInit0(klass, data);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void classInit0(long klass, long data) {
		parentClass = OS.g_type_class_peek_parent(klass);
		install(klass, "AtkObjectClass");
		install(klass, "GObjectClass");
		try {
			// initialize is the one function that is not a plain forward
			setSlot(klass, offset("AtkObjectClass", "initialize"),
				LOOKUP.findStatic(FFMAccessible.class, "initialize", MethodType.methodType(void.class, long.class, long.class)),
				FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
			setSlot(klass, offset("AtkComponentIface", "get_extents"), null, null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static void interfaceInit(String struct, long iface, long data) {
		try {
			interfaceInit0(struct, iface, data);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void interfaceInit0(String struct, long iface, long data) {
		install(iface, struct);
		if (struct.equals("AtkComponentIface")) {
			try {
				setSlot(iface, offset(struct, "get_extents"),
					LOOKUP.findStatic(FFMAccessible.class, "getExtents",
						MethodType.methodType(void.class, long.class, long.class, long.class, long.class, long.class, int.class)),
					FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT));
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	static void install(long vtable, String struct) {
		for (Slot s : SLOTS) {
			if (!s.struct().equals(struct)) continue;
			if (s.struct().equals("AtkComponentIface") && s.field().equals("get_extents")) continue;
			setSlot(vtable, offset(struct, s.field()), handleFor(s), s.descriptor());
		}
	}

	static void setSlot(long vtable, long offset, MethodHandle handle, FunctionDescriptor descriptor) {
		if (handle == null) return;
		MemorySegment stub = FFM.LINKER.upcallStub(handle, descriptor, Arena.global());
		MemorySegment.ofAddress(vtable + offset).reinterpret(8).set(JAVA_LONG_UNALIGNED, 0, stub.address());
	}

	/** The offset of a field, from the layout probe through the generated struct constants. */
	static long offset(String struct, String field) {
		String packageName = struct.equals("GObjectClass") ? "org.eclipse.swt.internal.gtk" : "org.eclipse.swt.internal.accessibility.gtk";
		try {
			Class<?> structs = Class.forName(packageName + ".Structs_FFM");
			return structs.getField(struct + "_" + field.toUpperCase(Locale.ROOT) + "_OFFSET").getLong(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(struct + "." + field, e);
		}
	}

	/* ---------------------------------------------------------------- dispatching into Java */

	static MethodHandle handleFor(Slot slot) {
		try {
			MethodType type = slot.descriptor().toMethodType();
			MethodHandle dispatch = LOOKUP.findStatic(FFMAccessible.class, "dispatch",
				MethodType.methodType(long.class, String.class, Mode.class, long.class, long[].class));
			MethodHandle bound = MethodHandles.insertArguments(dispatch, 0, slot.java(), slot.mode(), offset(slot.struct(), slot.field()));
			MethodHandle collected = bound.asCollector(long[].class, type.parameterCount());
			if (slot.booleanResult()) {
				collected = MethodHandles.filterReturnValue(collected,
					LOOKUP.findStatic(FFMAccessible.class, "toBoolean", MethodType.methodType(long.class, long.class)));
			}
			return MethodHandles.explicitCastArguments(collected, type);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(slot.java(), e);
		}
	}

	static long toBoolean(long value) {
		return (int) value == 1 ? 1 : 0;
	}

	/** Calls the AccessibleObject method, or the implementation of the parent class. */
	static long dispatch(String method, Mode mode, long offset, long[] arguments) {
		try {
			return dispatch0(method, mode, offset, arguments);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0;
		}
	}

	static long dispatch0(String method, Mode mode, long offset, long[] arguments) {
		long accessible = arguments[0];
		boolean registered = REGISTERED.contains(accessible);
		long result = 0;
		if (registered) result = call(method, arguments);
		// the C kept has_accessible in the instance, so it died with it; an address gets reused
		if (registered && method.equals("gObjectClass_finalize")) REGISTERED.remove(accessible);
		if (registered && mode != Mode.CHAIN) return result;
		if (mode == Mode.FORWARD) return result;
		return parent(offset, arguments);
	}

	static long call(String method, long[] arguments) {
		try {
			MethodHandle handle = METHODS.computeIfAbsent(method + arguments.length, key -> find(method, arguments.length));
			return (long) handle.invokeExact(arguments);
		} catch (Throwable t) {
			// the C code logged the exception and returned the error result, so do the same
			System.err.println("SWT-FFM: exception in accessibility callback " + method);
			t.printStackTrace();
			return 0;
		}
	}

	static MethodHandle find(String method, int count) {
		try {
			Class<?>[] types = new Class<?>[count];
			Arrays.fill(types, long.class);
			Method found = Class.forName(ACCESSIBLE_OBJECT).getDeclaredMethod(method, types);
			found.setAccessible(true);
			return LOOKUP.unreflect(found).asSpreader(long[].class, count);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(method, e);
		}
	}

	/** Calls the function the parent class put into the same slot. */
	static final Map<Integer, MethodHandle> PARENT_CALLS = new ConcurrentHashMap<>();

	static long parent(long offset, long[] arguments) {
		long function = MemorySegment.ofAddress(parentClass + offset).reinterpret(8).get(JAVA_LONG_UNALIGNED, 0);
		if (function == 0) return 0;
		// one handle per argument count, taking the function as its first argument
		MethodHandle handle = PARENT_CALLS.computeIfAbsent(arguments.length, count -> {
			MemoryLayout[] layouts = new MemoryLayout[count];
			Arrays.fill(layouts, JAVA_LONG);
			return FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_LONG, layouts))
				.asSpreader(long[].class, count);
		});
		try {
			return (long) handle.invokeExact(MemorySegment.ofAddress(function), arguments);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/* ---------------------------------------------------------------- the two hand written functions */

	static void initialize(long accessible, long data) {
		try {
			initialize0(accessible, data);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void initialize0(long accessible, long data) {
		long function = MemorySegment.ofAddress(parentClass + offset("AtkObjectClass", "initialize")).reinterpret(8).get(JAVA_LONG_UNALIGNED, 0);
		try {
			if (function != 0) {
				FFM.LINKER.downcallHandle(MemorySegment.ofAddress(function), FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG))
					.invokeExact(accessible, data);
			}
			// only widgets with a Java Accessible get the ATK implementations, see the C comment
			ACCESSIBLE_SET_WIDGET.invokeExact(accessible, REGISTERED.contains(accessible) ? data : 0L);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static void getExtents(long component, long x, long y, long width, long height, int coordinateType) {
		try {
			getExtents0(component, x, y, width, height, coordinateType);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void getExtents0(long component, long x, long y, long width, long height, int coordinateType) {
		if (REGISTERED.contains(component)) {
			call("atkComponent_get_extents", new long[] {component, x, y, width, height, coordinateType});
			return;
		}
		try {
			long widget = (long) ACCESSIBLE_GET_WIDGET.invokeExact(component);
			GtkAllocation allocation = new GtkAllocation();
			GTK.gtk_widget_get_allocation(widget, allocation);
			long[] position = new long[2];
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment fixedX = arena.allocate(JAVA_INT), fixedY = arena.allocate(JAVA_INT);
				call("toDisplay", new long[] {GTK3.gtk_widget_get_window(widget), fixedX.address(), fixedY.address()});
				position[0] = fixedX.get(JAVA_INT, 0);
				position[1] = fixedY.get(JAVA_INT, 0);
				if (coordinateType == ATK.ATK_XY_WINDOW) {
					long top = (long) WIDGET_GET_TOPLEVEL.invokeExact(widget);
					MemorySegment topX = arena.allocate(JAVA_INT), topY = arena.allocate(JAVA_INT);
					call("toDisplay", new long[] {GTK3.gtk_widget_get_window(top), topX.address(), topY.address()});
					position[0] -= topX.get(JAVA_INT, 0);
					position[1] -= topY.get(JAVA_INT, 0);
				}
			}
			MemorySegment.ofAddress(x).reinterpret(4).set(JAVA_INT, 0, (int) position[0]);
			MemorySegment.ofAddress(y).reinterpret(4).set(JAVA_INT, 0, (int) position[1]);
			MemorySegment.ofAddress(width).reinterpret(4).set(JAVA_INT, 0, allocation.width);
			MemorySegment.ofAddress(height).reinterpret(4).set(JAVA_INT, 0, allocation.height);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static long stub(String name, FunctionDescriptor descriptor) throws ReflectiveOperationException {
		MethodHandle handle = LOOKUP.findStatic(FFMAccessible.class, name, descriptor.toMethodType());
		return FFM.LINKER.upcallStub(handle, descriptor, Arena.global()).address();
	}
}
