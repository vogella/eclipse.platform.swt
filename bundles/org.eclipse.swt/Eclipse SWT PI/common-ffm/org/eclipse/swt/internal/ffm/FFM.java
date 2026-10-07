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
import java.nio.charset.*;
import java.util.*;

/**
 * Runtime support for the FFM bindings generated from the SWT native declarations.
 */
public final class FFM {

	static final Linker LINKER = Linker.nativeLinker();

	static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

	/** Whether SWT runs on GTK4, decided the way <code>OS</code> picks <code>swt-pi4</code> or <code>swt-pi3</code>. */
	public static final boolean GTK4 = !WINDOWS && useGtk4();

	/** Libraries searched after the SWT libraries, whose dlopen handles already cover their dependencies. */
	static final String[] GTK_LIBRARIES = {
		"libgtk-3.so.0", "libgdk-3.so.0", "libgdk_pixbuf-2.0.so.0", "libgobject-2.0.so.0", "libglib-2.0.so.0", "libgio-2.0.so.0",
		"libpango-1.0.so.0", "libpangocairo-1.0.so.0", "libcairo.so.2", "libatk-1.0.so.0", "libgthread-2.0.so.0",
		"libfontconfig.so.1", "libX11.so.6",
	};

	/** GTK4 contains GDK, and no GTK3 library may be loaded into a GTK4 process. */
	static final String[] GTK4_LIBRARIES = {
		"libgtk-4.so.1", "libgdk_pixbuf-2.0.so.0", "libgobject-2.0.so.0", "libglib-2.0.so.0", "libgio-2.0.so.0",
		"libpango-1.0.so.0", "libpangocairo-1.0.so.0", "libcairo.so.2", "libatk-1.0.so.0", "libgthread-2.0.so.0",
		"libfontconfig.so.1", "libX11.so.6",
	};

	/**
	 * The DLLs swt-win32 links against (make_win32.mak), plus gdiplus. The loader lookup only sees the
	 * JNI exports of the SWT DLL, so on Windows every system DLL has to be listed.
	 */
	static final String[] WIN32_LIBRARIES = {
		"kernel32.dll", "user32.dll", "gdi32.dll", "ole32.dll", "oleaut32.dll", "shell32.dll", "comdlg32.dll", "imm32.dll",
		"usp10.dll", "uxtheme.dll", "msimg32.dll", "dwmapi.dll", "shlwapi.dll", "advapi32.dll", "oleacc.dll", "winspool.drv",
		"urlmon.dll", "wininet.dll", "propsys.dll", "gdiplus.dll", "comctl32.dll",
	};

	static final String[] LIBRARIES = WINDOWS ? WIN32_LIBRARIES : GTK4 ? GTK4_LIBRARIES : GTK_LIBRARIES;

	static final SymbolLookup LOOKUP = createLookup();

	private FFM() {
	}

	/**
	 * <code>SWT_GTK4=1</code> selects GTK4 and anything else GTK3, each falling back to the other if its GTK library
	 * cannot be loaded, as <code>OS</code> does with <code>swt-pi4</code> and <code>swt-pi3</code>.
	 */
	static boolean useGtk4() {
		boolean requested = "1".equals(environment("SWT_GTK4"));
		return GtkLibraries.available(requested) ? requested : GtkLibraries.available(!requested) ? !requested : requested;
	}

	/** Stands in for <code>Library.loadLibrary("swt-pi4")</code> and <code>("swt-pi3")</code> in <code>OS</code>. */
	public static void loadGtk(boolean gtk4) {
		if (!GtkLibraries.available(gtk4)) throw new UnsatisfiedLinkError("Cannot load " + GtkLibraries.name(gtk4));
	}

	/** The C environment, which sees a <code>setenv</code> after the JVM started, as <code>OS</code> does. */
	static String environment(String name) {
		try {
			MemorySegment getenv = LINKER.defaultLookup().find("getenv").orElseThrow();
			MethodHandle handle = LINKER.downcallHandle(getenv, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment value = (MemorySegment) handle.invokeExact(arena.allocateFrom(name));
				return value.equals(MemorySegment.NULL) ? null : value.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.ISO_8859_1);
			}
		} catch (Throwable e) {
			return System.getenv(name);
		}
	}

	/** Which GTK major versions can be loaded, probed on demand and once, so that a version nobody asks about is never mapped. */
	private static final class GtkLibraries {
		private static final Boolean[] LOADABLE = new Boolean[2];

		static String name(boolean gtk4) {
			return gtk4 ? "libgtk-4.so.1" : "libgtk-3.so.0";
		}

		static synchronized boolean available(boolean gtk4) {
			int index = gtk4 ? 1 : 0;
			if (LOADABLE[index] == null) {
				try {
					SymbolLookup.libraryLookup(name(gtk4), Arena.global());
					LOADABLE[index] = true;
				} catch (IllegalArgumentException e) {
					LOADABLE[index] = false;
				}
			}
			return LOADABLE[index];
		}
	}

	static SymbolLookup createLookup() {
		SymbolLookup lookup = SymbolLookup.loaderLookup();
		for (String library : LIBRARIES) {
			if (WINDOWS) {
				lookup = lookup.or(lazy(library));
				continue;
			}
			try {
				lookup = lookup.or(SymbolLookup.libraryLookup(library, Arena.global()));
			} catch (IllegalArgumentException e) {
				// library not present on this system
			}
		}
		return lookup.or(LINKER.defaultLookup());
	}

	/**
	 * Loads <code>library</code> when a lookup first reaches it. comctl32 has to be loaded after
	 * <code>OS</code> activated the SWT manifest, or it resolves to version 5 instead of 6.
	 */
	static SymbolLookup lazy(String library) {
		return new SymbolLookup() {
			volatile SymbolLookup loaded;
			volatile boolean missing;

			@Override
			public Optional<MemorySegment> find(String name) {
				SymbolLookup lookup = loaded;
				if (lookup == null) {
					if (missing) return Optional.empty();
					try {
						loaded = lookup = SymbolLookup.libraryLookup(library, Arena.global());
					} catch (IllegalArgumentException e) {
						missing = true;
						return Optional.empty();
					}
				}
				return lookup.find(name);
			}
		};
	}

	/**
	 * Returns a lookup searching the first of <code>alternatives</code> that loads, then the SWT
	 * libraries, the way the JNI glue dlopens a library that SWT does not link against.
	 */
	public static SymbolLookup library(String... alternatives) {
		for (String library : alternatives) {
			try {
				return SymbolLookup.libraryLookup(library, Arena.global()).or(LOOKUP);
			} catch (IllegalArgumentException e) {
				// try the next one
			}
		}
		return LOOKUP;
	}

	/** Links <code>name</code>, or returns a handle that throws {@link UnsatisfiedLinkError} when invoked. */
	public static MethodHandle downcall(String name, FunctionDescriptor descriptor, Linker.Option... options) {
		return downcall(LOOKUP, name, descriptor, options);
	}

	public static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor, Linker.Option... options) {
		MethodHandle handle = downcallOptional(lookup, name, descriptor, options);
		if (handle != null) return handle;
		return unavailable(descriptor.toMethodType(), name);
	}

	static MethodHandle unavailable(MethodType type, String name) {
		MethodHandle thrower = MethodHandles.throwException(type.returnType(), UnsatisfiedLinkError.class);
		thrower = MethodHandles.insertArguments(thrower, 0, new UnsatisfiedLinkError(name));
		return MethodHandles.dropArguments(thrower, 0, type.parameterList());
	}

	/** The handle under GTK3, one that throws UnsatisfiedLinkError under GTK4, where the native has no ABI of this shape. */
	public static MethodHandle gtk3Only(MethodHandle handle, String name) {
		return GTK4 ? unavailable(handle.type(), name) : handle;
	}

	/** The handle under GTK4, one that throws UnsatisfiedLinkError under GTK3. */
	public static MethodHandle gtk4Only(MethodHandle handle, String name) {
		return GTK4 ? handle : unavailable(handle.type(), name);
	}

	/** Links <code>name</code>, or returns <code>null</code> if the symbol does not exist. */
	public static MethodHandle downcallOptional(String name, FunctionDescriptor descriptor, Linker.Option... options) {
		return downcallOptional(LOOKUP, name, descriptor, options);
	}

	public static MethodHandle downcallOptional(SymbolLookup lookup, String name, FunctionDescriptor descriptor, Linker.Option... options) {
		Optional<MemorySegment> symbol = lookup.find(name);
		return symbol.map(s -> LINKER.downcallHandle(s, descriptor, options)).orElse(null);
	}

	public static long address(String name) {
		return address(LOOKUP, name);
	}

	public static long address(SymbolLookup lookup, String name) {
		return lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)).address();
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

	/** A NULL terminated array of UTF-8 strings, where a <code>null</code> element ends the strings early. */
	public static MemorySegment strings(Arena arena, String[] strings) {
		if (strings == null) return MemorySegment.NULL;
		MemorySegment array = arena.allocate(ADDRESS.byteSize() * (strings.length + 1L), ADDRESS.byteAlignment());
		for (int i = 0; i < strings.length; i++) {
			array.setAtIndex(ADDRESS, i, strings[i] == null ? MemorySegment.NULL : arena.allocateFrom(strings[i]));
		}
		return array;
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
