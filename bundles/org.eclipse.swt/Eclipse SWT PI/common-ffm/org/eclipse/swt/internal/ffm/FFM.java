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

/**
 * Runtime support for the FFM bindings generated from the SWT native declarations.
 */
public final class FFM {

	static final Linker LINKER = Linker.nativeLinker();

	/** Libraries searched after the SWT libraries, whose dlopen handles already cover their dependencies. */
	static final String[] LIBRARIES = {
		"libgtk-3.so.0", "libgdk-3.so.0", "libgdk_pixbuf-2.0.so.0", "libgobject-2.0.so.0", "libglib-2.0.so.0", "libgio-2.0.so.0",
		"libpango-1.0.so.0", "libpangocairo-1.0.so.0", "libcairo.so.2", "libatk-1.0.so.0", "libgthread-2.0.so.0",
		"libfontconfig.so.1", "libX11.so.6",
	};

	static final SymbolLookup LOOKUP = createLookup();

	private FFM() {
	}

	static SymbolLookup createLookup() {
		SymbolLookup lookup = SymbolLookup.loaderLookup();
		for (String library : LIBRARIES) {
			try {
				lookup = lookup.or(SymbolLookup.libraryLookup(library, Arena.global()));
			} catch (IllegalArgumentException e) {
				// library not present on this system
			}
		}
		return lookup.or(LINKER.defaultLookup());
	}

	/** Links <code>name</code>, or returns a handle that throws {@link UnsatisfiedLinkError} when invoked. */
	public static MethodHandle downcall(String name, FunctionDescriptor descriptor, Linker.Option... options) {
		MethodHandle handle = downcallOptional(name, descriptor, options);
		if (handle != null) return handle;
		MethodType type = descriptor.toMethodType();
		MethodHandle thrower = MethodHandles.throwException(type.returnType(), UnsatisfiedLinkError.class);
		thrower = MethodHandles.insertArguments(thrower, 0, new UnsatisfiedLinkError(name));
		return MethodHandles.dropArguments(thrower, 0, type.parameterList());
	}

	/** Links <code>name</code>, or returns <code>null</code> if the symbol does not exist. */
	public static MethodHandle downcallOptional(String name, FunctionDescriptor descriptor, Linker.Option... options) {
		Optional<MemorySegment> symbol = LOOKUP.find(name);
		return symbol.map(s -> LINKER.downcallHandle(s, descriptor, options)).orElse(null);
	}

	public static long address(String name) {
		return LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)).address();
	}

	/**
	 * The exception a callback left behind, which the JNI glue kept pending on the thread so that it
	 * surfaced when the native call that dispatched the callback returned to Java.
	 */
	static volatile Throwable pendingException;
	static volatile Thread pendingThread;

	/** Called by the upcall stubs when the Java side of a callback failed. */
	public static void callbackFailed(Throwable failure) {
		Throwable pending = pendingException;
		if (pending != null && pendingThread == Thread.currentThread()) {
			if (pending != failure) pending.addSuppressed(failure);
			return;
		}
		pendingException = failure;
		pendingThread = Thread.currentThread();
	}

	/** Takes the exception pending for this thread, as JNI's ExceptionOccurred plus ExceptionClear did. */
	public static Throwable takePending() {
		Throwable pending = pendingException;
		if (pending == null || pendingThread != Thread.currentThread()) return null;
		pendingException = null;
		pendingThread = null;
		return pending;
	}

	public static void setPending(Throwable failure) {
		pendingException = failure;
		pendingThread = failure == null ? null : Thread.currentThread();
	}

	/**
	 * Throws what a callback left behind. The generated bindings call this after every downcall, which
	 * is where the JNI glue let a pending exception surface.
	 */
	public static void checkCallbackException() {
		if (pendingException == null) return;
		Throwable pending = takePending();
		if (pending != null) throw rethrow(pending);
	}

	public static RuntimeException rethrow(Throwable t) {
		if (t instanceof RuntimeException e) throw e;
		if (t instanceof Error e) throw e;
		throw new IllegalStateException(t);
	}

	public static MemorySegment segment(long address, long size) {
		return MemorySegment.ofAddress(address).reinterpret(size);
	}

	public static MemorySegment string(Arena arena, String string) {
		return string == null ? MemorySegment.NULL : arena.allocateFrom(string);
	}

	public static MemorySegment copyIn(Arena arena, byte[] array) {
		return array == null ? MemorySegment.NULL : arena.allocateFrom(JAVA_BYTE, array);
	}

	public static MemorySegment copyIn(Arena arena, short[] array) {
		return array == null ? MemorySegment.NULL : arena.allocateFrom(JAVA_SHORT, array);
	}

	public static MemorySegment copyIn(Arena arena, char[] array) {
		return array == null ? MemorySegment.NULL : arena.allocateFrom(JAVA_CHAR, array);
	}

	public static MemorySegment copyIn(Arena arena, int[] array) {
		return array == null ? MemorySegment.NULL : arena.allocateFrom(JAVA_INT, array);
	}

	public static MemorySegment copyIn(Arena arena, long[] array) {
		return array == null ? MemorySegment.NULL : arena.allocateFrom(JAVA_LONG, array);
	}

	public static MemorySegment copyIn(Arena arena, float[] array) {
		return array == null ? MemorySegment.NULL : arena.allocateFrom(JAVA_FLOAT, array);
	}

	public static MemorySegment copyIn(Arena arena, double[] array) {
		return array == null ? MemorySegment.NULL : arena.allocateFrom(JAVA_DOUBLE, array);
	}

	public static void copyOut(MemorySegment segment, byte[] array) {
		if (array != null) MemorySegment.copy(segment, JAVA_BYTE, 0, array, 0, array.length);
	}

	public static void copyOut(MemorySegment segment, short[] array) {
		if (array != null) MemorySegment.copy(segment, JAVA_SHORT, 0, array, 0, array.length);
	}

	public static void copyOut(MemorySegment segment, char[] array) {
		if (array != null) MemorySegment.copy(segment, JAVA_CHAR, 0, array, 0, array.length);
	}

	public static void copyOut(MemorySegment segment, int[] array) {
		if (array != null) MemorySegment.copy(segment, JAVA_INT, 0, array, 0, array.length);
	}

	public static void copyOut(MemorySegment segment, long[] array) {
		if (array != null) MemorySegment.copy(segment, JAVA_LONG, 0, array, 0, array.length);
	}

	public static void copyOut(MemorySegment segment, float[] array) {
		if (array != null) MemorySegment.copy(segment, JAVA_FLOAT, 0, array, 0, array.length);
	}

	public static void copyOut(MemorySegment segment, double[] array) {
		if (array != null) MemorySegment.copy(segment, JAVA_DOUBLE, 0, array, 0, array.length);
	}

	public static MemorySegment heap(byte[] array) {
		return array == null ? MemorySegment.NULL : MemorySegment.ofArray(array);
	}

	public static MemorySegment heap(short[] array) {
		return array == null ? MemorySegment.NULL : MemorySegment.ofArray(array);
	}

	public static MemorySegment heap(char[] array) {
		return array == null ? MemorySegment.NULL : MemorySegment.ofArray(array);
	}

	public static MemorySegment heap(int[] array) {
		return array == null ? MemorySegment.NULL : MemorySegment.ofArray(array);
	}

	public static MemorySegment heap(long[] array) {
		return array == null ? MemorySegment.NULL : MemorySegment.ofArray(array);
	}

	public static MemorySegment heap(float[] array) {
		return array == null ? MemorySegment.NULL : MemorySegment.ofArray(array);
	}

	public static MemorySegment heap(double[] array) {
		return array == null ? MemorySegment.NULL : MemorySegment.ofArray(array);
	}

	/** Reads an unsigned bit-field of <code>width</code> bits starting <code>bit</code> bits into the segment. */
	public static long getBits(MemorySegment segment, long bit, int width) {
		long value = 0;
		for (int i = 0; i < width; i++) {
			long b = bit + i;
			if ((segment.get(JAVA_BYTE, b >>> 3) & (1 << (b & 7))) != 0) value |= 1L << i;
		}
		return value;
	}

	public static void setBits(MemorySegment segment, long bit, int width, long value) {
		for (int i = 0; i < width; i++) {
			long b = bit + i;
			byte current = segment.get(JAVA_BYTE, b >>> 3);
			int mask = 1 << (b & 7);
			segment.set(JAVA_BYTE, b >>> 3, (byte) (((value >>> i) & 1) != 0 ? current | mask : current & ~mask));
		}
	}
}
