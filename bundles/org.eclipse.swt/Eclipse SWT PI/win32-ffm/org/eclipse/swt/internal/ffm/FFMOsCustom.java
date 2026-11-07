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

import org.eclipse.swt.internal.*;

/**
 * Java port of the natives of os_custom.c: the instance handle of the SWT library, the
 * <code>DPI_AWARENESS_CONTEXT_*</code> constants and the dark mode functions that uxtheme
 * exports by ordinal only, accepted after the same check of their machine code.
 */
public final class FFMOsCustom {

	static final Linker LINKER = Linker.nativeLinker();
	static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
	static final MethodHandle GET_MODULE_HANDLE = LINKER.downcallHandle(KERNEL32.findOrThrow("GetModuleHandleW"),
		FunctionDescriptor.of(JAVA_LONG, ADDRESS));
	static final MethodHandle GET_PROC_ADDRESS = LINKER.downcallHandle(KERNEL32.findOrThrow("GetProcAddress"),
		FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));

	static final String ARCH = System.getProperty("os.arch");
	static final boolean X64 = "amd64".equals(ARCH) || "x86_64".equals(ARCH);
	static final boolean ARM64 = "aarch64".equals(ARCH) || "arm64".equals(ARCH);

	/** Bytes of machine code the validators read, the largest offset is 0x57. */
	static final long CODE_SIZE = 0x60;

	static final FunctionDescriptor ALLOW_DARK_MODE_FOR_WINDOW = FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT);
	static final FunctionDescriptor ALLOW_DARK_MODE_FOR_WINDOW_WITH_TELEMETRY_ID = FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_INT);
	static final FunctionDescriptor SET_PREFERRED_APP_MODE = FunctionDescriptor.of(JAVA_INT, JAVA_INT);

	/* Search results cached per native on its first call, as the static locals of the C code */
	static volatile long libraryHandle;
	static MethodHandle allowDarkModeForWindow, allowDarkModeForWindowWithTelemetryId;
	static volatile boolean allowDarkModeInitialized;
	static MethodHandle setPreferredAppMode;
	static volatile boolean setPreferredAppModeInitialized;
	static boolean darkModeAvailable;
	static volatile boolean darkModeAvailableInitialized;

	private FFMOsCustom() {
	}

	/* ---------------------------------------------------------------- library handle */

	/**
	 * The C returns the <code>DllMain</code> instance handle of swt-win32. The FFM port has no
	 * library of its own, so this is the module handle of swt-win32 while one is loaded (the same
	 * value), else 0. With 0, <code>SetWindowsHookEx</code> for a thread of this process works as
	 * before, but <code>LoadImage</code> finds no Text search and cancel icons (swt.rc 101 to 104).
	 */
	public static long GetLibraryHandle() {
		long handle = libraryHandle;
		if (handle == 0) libraryHandle = handle = findLibraryHandle();
		return handle;
	}

	/** Tries the file names Library.loadLibrary("swt-win32") loads, including the -64 fallback. */
	static long findLibraryHandle() {
		String prefix = "swt-" + Platform.PLATFORM;
		for (String name : new String[] {prefix + "-" + Library.getVersionString(), prefix, "swt"}) {
			for (String suffix : new String[] {"", "-64"}) {
				long handle = moduleHandle(name + suffix + ".dll");
				if (handle != 0) return handle;
			}
		}
		return 0;
	}

	static long moduleHandle(String name) {
		try (Arena arena = Arena.ofConfined()) {
			return (long) GET_MODULE_HANDLE.invokeExact(arena.allocateFrom(name, StandardCharsets.UTF_16LE));
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/* ---------------------------------------------------------------- DPI awareness contexts, from windef.h */

	public static long DPI_AWARENESS_CONTEXT_UNAWARE() {
		return -1;
	}

	public static long DPI_AWARENESS_CONTEXT_SYSTEM_AWARE() {
		return -2;
	}

	public static long DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE() {
		return -3;
	}

	public static long DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2() {
		return -4;
	}

	public static long DPI_AWARENESS_CONTEXT_UNAWARE_GDISCALED() {
		return -5;
	}

	/* ---------------------------------------------------------------- dark mode */

	public static boolean AllowDarkModeForWindow(long hWnd, boolean allow) {
		if (!allowDarkModeInitialized) {
			synchronized (FFMOsCustom.class) {
				if (!allowDarkModeInitialized) {
					allowDarkModeForWindow = handle(locateAllowDarkModeForWindow(), ALLOW_DARK_MODE_FOR_WINDOW);
					allowDarkModeForWindowWithTelemetryId = handle(locateAllowDarkModeForWindowWithTelemetryId(), ALLOW_DARK_MODE_FOR_WINDOW_WITH_TELEMETRY_ID);
					allowDarkModeInitialized = true;
				}
			}
		}
		try {
			if (allowDarkModeForWindow != null) {
				return jboolean((int) allowDarkModeForWindow.invokeExact(hWnd, allow ? 1 : 0));
			}
			// On Win11 AllowDarkModeForWindow only wraps AllowDarkModeForWindowWithTelemetryId, whose code is easier to verify
			if (allowDarkModeForWindowWithTelemetryId != null) {
				return jboolean((int) allowDarkModeForWindowWithTelemetryId.invokeExact(hWnd, allow ? 1 : 0, 0));
			}
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
		return false;
	}

	public static int SetPreferredAppMode(int mode) {
		if (!setPreferredAppModeInitialized) {
			synchronized (FFMOsCustom.class) {
				if (!setPreferredAppModeInitialized) {
					setPreferredAppMode = handle(locateSetPreferredAppMode(), SET_PREFERRED_APP_MODE);
					setPreferredAppModeInitialized = true;
				}
			}
		}
		if (setPreferredAppMode == null) return 0;
		try {
			return (int) setPreferredAppMode.invokeExact(mode);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	public static boolean IsDarkModeAvailable() {
		if (!darkModeAvailableInitialized) {
			synchronized (FFMOsCustom.class) {
				if (!darkModeAvailableInitialized) {
					darkModeAvailable = locateSetPreferredAppMode() != 0
						&& (locateAllowDarkModeForWindow() != 0 || locateAllowDarkModeForWindowWithTelemetryId() != 0);
					darkModeAvailableInitialized = true;
				}
			}
		}
		return darkModeAvailable;
	}

	/** The C casts the BOOL result to jboolean, which keeps its low byte. */
	static boolean jboolean(int result) {
		return (result & 0xFF) != 0;
	}

	static MethodHandle handle(long function, FunctionDescriptor descriptor) {
		return function == 0 ? null : LINKER.downcallHandle(MemorySegment.ofAddress(function), descriptor);
	}

	static long locateAllowDarkModeForWindow() {
		long candidate = uxthemeOrdinal(133);
		return candidate != 0 && validateAllowDarkModeForWindow(code(candidate), ARM64) ? candidate : 0;
	}

	static long locateAllowDarkModeForWindowWithTelemetryId() {
		long candidate = uxthemeOrdinal(140);
		return candidate != 0 && validateAllowDarkModeForWindowWithTelemetryId(code(candidate), ARM64) ? candidate : 0;
	}

	static long locateSetPreferredAppMode() {
		long candidate = uxthemeOrdinal(135);
		return candidate != 0 && validateSetPreferredAppMode(code(candidate), ARM64) ? candidate : 0;
	}

	/**
	 * The function uxtheme exports by <code>ordinal</code> only, or 0. The C only looks for a loaded
	 * uxtheme, which swt-win32 always loads through its import of Uxtheme.lib; without that library
	 * this loads uxtheme first, so the result does not depend on who loaded it. On a processor the
	 * C does not build for it finds nothing.
	 */
	static long uxthemeOrdinal(int ordinal) {
		if (!X64 && !ARM64) return 0;
		loadUxtheme();
		long uxtheme = moduleHandle("uxtheme.dll");
		if (uxtheme == 0) return 0;
		try {
			return (long) GET_PROC_ADDRESS.invokeExact(uxtheme, (long) ordinal);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static volatile boolean uxthemeLoaded;

	static void loadUxtheme() {
		if (uxthemeLoaded) return;
		try {
			SymbolLookup.libraryLookup("uxtheme", Arena.global());
		} catch (IllegalArgumentException e) {
			// not present, GetModuleHandle then fails as in the C
		}
		uxthemeLoaded = true;
	}

	static MemorySegment code(long function) {
		return MemorySegment.ofAddress(function).reinterpret(CODE_SIZE);
	}

	static int u8(MemorySegment code, long offset) {
		return code.get(JAVA_BYTE, offset) & 0xFF;
	}

	static int u32(MemorySegment code, long offset) {
		return code.get(JAVA_INT_UNALIGNED, offset);
	}

	/*
	 * Another function could get the ordinal in a future Windows version, so the candidates are
	 * compared to the known code. AllowDarkModeForWindow and its telemetry variant are long, but
	 * load the ATOM 0xA91E which is unlikely to change.
	 */

	static boolean validateAllowDarkModeForWindow(MemorySegment f, boolean arm64) {
		if (arm64) {
			return u32(f, 0x18) == 0xD29523C1; // mov x1,#0xA91E
		}
		// Win10 builds from 20236: mov edx, 0A91Eh
		if (u8(f, 0x52) == 0xBA && u32(f, 0x53) == 0xA91E) return true;
		// Win10 builds from 17763 to 19041: mov edx, 0A91Eh
		if (u8(f, 0x15) == 0xBA && u32(f, 0x16) == 0xA91E) return true;
		return false;
	}

	static boolean validateAllowDarkModeForWindowWithTelemetryId(MemorySegment f, boolean arm64) {
		if (arm64) {
			return u32(f, 0x18) == 0xD29523C1; // mov x1,#0xA91E
		}
		// Win10 builds from 21301: mov edx, 0A91Eh
		if (u8(f, 0x31) == 0xBA && u32(f, 0x32) == 0xA91E) return true;
		// Win11 builds from 22621: mov edx, 0A91Eh
		if (u8(f, 0x15) == 0xBA && u32(f, 0x16) == 0xA91E) return true;
		// Win11 builds from 26100: mov esi, 0A91Eh
		if (u8(f, 0x16) == 0xBE && u32(f, 0x17) == 0xA91E) return true;
		return false;
	}

	/** The function is short, so its whole body is checked except for the address of the variable. */
	static boolean validateSetPreferredAppMode(MemorySegment f, boolean arm64) {
		if (arm64) {
			int ldr = u32(f, 0x14);
			int add = u32(f, 0x1C);
			if ((ldr & 0xFFC003FF) == 0xB9400113 // ldr w19,[x8, arg0]
				&& u32(f, 0x18) == 0x2A0003E1 // mov w1,w0
				&& (add & 0xFFC003FF) == 0x91000100 // add x0,x8,arg1
				&& u32(f, 0x24) == 0x2A1303E0) { // mov w0,w19
				int arg0 = ((ldr & 0x003FFC00) >>> 10) * 4;
				int arg1 = (add & 0x003FFC00) >>> 10;
				return arg0 == arg1;
			}
			// Win11 builds from 26100
			return (u32(f, 0x00) & 0x9F00001F) == 0x90000008 // adrp x8,#...
				&& (u32(f, 0x04) & 0xFF8003FF) == 0x91000108 // add x8,x8,#...
				&& u32(f, 0x08) == 0x2A0003E9 // mov w9,w0
				&& u32(f, 0x0C) == 0xB9400100 // ldr w0,[x8]
				&& u32(f, 0x10) == 0xB8E98109; // swpal w9,w9,[x8]
		}
		// both instructions address g_preferredAppMode rip relative, 6 bytes apart
		int varOffset1 = u32(f, 0x02);
		int varOffset2 = u32(f, 0x08);
		if (varOffset1 != varOffset2 + 6) return false;
		return u8(f, 0x00) == 0x8B && u8(f, 0x01) == 0x05 // mov eax,dword ptr [uxtheme!g_preferredAppMode]
			&& u8(f, 0x06) == 0x87 && u8(f, 0x07) == 0x0D // xchg ecx,dword ptr [uxtheme!g_preferredAppMode]
			&& u8(f, 0x0C) == 0xC3; // ret
	}
}
