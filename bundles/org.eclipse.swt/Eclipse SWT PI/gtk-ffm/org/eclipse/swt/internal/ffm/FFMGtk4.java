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

import org.eclipse.swt.internal.Converter;
import org.eclipse.swt.internal.gtk.*;

/**
 * What the hand written GTK4 ports share: GType registration, upcall stubs and raw memory access.
 * Struct layouts come from the generated {@link Extra_FFM} and {@link Structs_FFM}.
 */
final class FFMGtk4 {

	/* fundamental GTypes (G_TYPE_MAKE_FUNDAMENTAL) and the GdkPaintableFlags */
	static final long G_TYPE_NONE = 1L << 2, G_TYPE_INT = 6L << 2, G_TYPE_OBJECT = 20L << 2;
	static final int GDK_PAINTABLE_STATIC_SIZE = 1, GDK_PAINTABLE_STATIC_CONTENTS = 2;

	private FFMGtk4() {
	}

	/** A NUL terminated native copy of <code>text</code> that lives forever, as a C string literal does. */
	static long literal(String text) {
		return Arena.global().allocateFrom(text).address();
	}

	/** Allocates an upcall stub for the static method <code>name</code> of <code>owner</code>. */
	static long stub(MethodHandles.Lookup lookup, Class<?> owner, String name, FunctionDescriptor descriptor) {
		try {
			MethodHandle handle = lookup.findStatic(owner, name, descriptor.toMethodType());
			return FFM.LINKER.upcallStub(handle, descriptor, Arena.global()).address();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Registers <code>name</code> as a subclass of <code>parent</code> the way G_DEFINE_TYPE does. */
	static long registerType(long parent, String name, long classSize, long classInit, long instanceSize) {
		try (Arena arena = Arena.ofConfined()) {
			GTypeInfo info = new GTypeInfo();
			info.class_size = (short) classSize;
			info.class_init = classInit;
			info.instance_size = (short) instanceSize;
			MemorySegment segment = arena.allocate(Structs_FFM.GTypeInfo_SIZEOF);
			Structs_FFM.GTypeInfo_write(segment, info);
			return OS_FFM.g_type_register_static(parent, Converter.wcsToMbcs(name, true), segment.address(), 0);
		}
	}

	/** Registers <code>interfaceType</code> on <code>type</code> with the given <code>interface_init</code> function, or none. */
	static void addInterface(long type, long interfaceType, long interfaceInit) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment info = arena.allocate(Extra_FFM.GINTERFACEINFO);
			info.set(JAVA_LONG_UNALIGNED, Extra_FFM.GINTERFACEINFO_INTERFACE_INIT, interfaceInit);
			OS_FFM.g_type_add_interface_static(type, interfaceType, info.address());
		}
	}

	static void putLong(long address, long offset, long value) {
		FFM.segment(address, offset + 8).set(JAVA_LONG_UNALIGNED, offset, value);
	}

	static long getLong(long address, long offset) {
		return FFM.segment(address, offset + 8).get(JAVA_LONG_UNALIGNED, offset);
	}

	static void putInt(long address, long offset, int value) {
		FFM.segment(address, offset + 4).set(JAVA_INT_UNALIGNED, offset, value);
	}

	static int getInt(long address, long offset) {
		return FFM.segment(address, offset + 4).get(JAVA_INT_UNALIGNED, offset);
	}
}
