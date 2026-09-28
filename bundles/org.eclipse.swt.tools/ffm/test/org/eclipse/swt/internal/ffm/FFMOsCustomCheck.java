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

import org.eclipse.swt.internal.win32.*;

/**
 * Compares FFMOsCustom with the JNI natives of os_custom.c, and its validators with the C ones
 * (validators.dll, compiled from os_custom.c) on real and random code.
 * Args: "ffm-only" runs without loading the JNI library.
 */
public class FFMOsCustomCheck {

	static int failures;

	static void check(String what, Object jni, Object ffm) {
		boolean ok = Objects.equals(jni, ffm);
		if (!ok) failures++;
		System.out.println((ok ? "OK   " : "FAIL ") + what + ": jni=" + jni + " ffm=" + ffm);
	}

	public static void main(String[] args) throws Throwable {
		if (args.length > 0 && args[0].equals("ffm-only")) {
			ffmOnly();
		} else {
			againstJni();
			validators(args.length > 0 ? args[0] : "validators.dll");
		}
		System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILURES");
		System.exit(failures == 0 ? 0 : 1);
	}

	/** No SWT library loaded: no library handle, and dark mode depends on uxtheme being loaded already. */
	static void ffmOnly() {
		long uxtheme = FFMOsCustom.moduleHandle("uxtheme.dll");
		System.out.println("uxtheme loaded before: " + (uxtheme != 0));
		check("GetLibraryHandle without swt-win32", 0L, FFMOsCustom.GetLibraryHandle());
		// the values the JNI run printed on this machine, where swt-win32 had loaded uxtheme
		check("IsDarkModeAvailable without swt-win32 loading uxtheme", true, FFMOsCustom.IsDarkModeAvailable());
		check("SetPreferredAppMode(1) returns the initial mode", 0, FFMOsCustom.SetPreferredAppMode(1));
		check("SetPreferredAppMode(0) returns the mode set before", 1, FFMOsCustom.SetPreferredAppMode(0));
	}

	static void againstJni() {
		check("GetLibraryHandle before swt-win32 loaded", 0L, FFMOsCustom.GetLibraryHandle());
		long jniHandle = OS.GetLibraryHandle(); // loads swt-win32
		check("GetLibraryHandle", jniHandle, FFMOsCustom.GetLibraryHandle());
		check("GetLibraryHandle is the module of swt-win32", FFMOsCustom.moduleHandle("swt-win32-" + org.eclipse.swt.internal.Library.getVersionString() + ".dll"), FFMOsCustom.GetLibraryHandle());
		for (int id = 101; id <= 104; id++) {
			long icon = OS.LoadImage(FFMOsCustom.GetLibraryHandle(), id, OS.IMAGE_ICON, 16, 16, 0);
			check("LoadImage icon " + id + " from FFM handle", true, icon != 0);
			if (icon != 0) OS.DestroyIcon(icon);
		}

		check("DPI_AWARENESS_CONTEXT_UNAWARE", OS.DPI_AWARENESS_CONTEXT_UNAWARE(), FFMOsCustom.DPI_AWARENESS_CONTEXT_UNAWARE());
		check("DPI_AWARENESS_CONTEXT_UNAWARE_GDISCALED", OS.DPI_AWARENESS_CONTEXT_UNAWARE_GDISCALED(), FFMOsCustom.DPI_AWARENESS_CONTEXT_UNAWARE_GDISCALED());
		check("DPI_AWARENESS_CONTEXT_SYSTEM_AWARE", OS.DPI_AWARENESS_CONTEXT_SYSTEM_AWARE(), FFMOsCustom.DPI_AWARENESS_CONTEXT_SYSTEM_AWARE());
		check("DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE", OS.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE(), FFMOsCustom.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE());
		check("DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2", OS.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2(), FFMOsCustom.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2());

		System.out.println("uxtheme loaded: " + (FFMOsCustom.moduleHandle("uxtheme.dll") != 0));
		System.out.println("ordinals 133/135/140 located: " + FFMOsCustom.locateAllowDarkModeForWindow() + " "
			+ FFMOsCustom.locateSetPreferredAppMode() + " " + FFMOsCustom.locateAllowDarkModeForWindowWithTelemetryId());
		check("IsDarkModeAvailable", OS.IsDarkModeAvailable(), FFMOsCustom.IsDarkModeAvailable());

		// both set the same uxtheme variable and return its previous value
		int a = OS.SetPreferredAppMode(1);
		int b = FFMOsCustom.SetPreferredAppMode(2);
		int c = OS.SetPreferredAppMode(3);
		int d = FFMOsCustom.SetPreferredAppMode(a);
		System.out.println("initial preferred app mode: " + a);
		check("SetPreferredAppMode returns what the other one set", OS.IsDarkModeAvailable() ? List.of(1, 2, 3) : List.of(0, 0, 0), List.of(b, c, d));

		long hwnd = OS.CreateWindowEx(0, "STATIC\0".toCharArray(), null, 0, 0, 0, 10, 10, 0, 0, 0, null);
		check("window created", true, hwnd != 0);
		long atom = 0xA91E;
		for (boolean allow : new boolean[] {true, false, true}) {
			boolean jni = OS.AllowDarkModeForWindow(hwnd, allow);
			long jniProp = OS.GetProp(hwnd, atom);
			OS.AllowDarkModeForWindow(hwnd, !allow);
			boolean ffm = FFMOsCustom.AllowDarkModeForWindow(hwnd, allow);
			long ffmProp = OS.GetProp(hwnd, atom);
			check("AllowDarkModeForWindow(" + allow + ")", jni, ffm);
			check("window property 0xA91E after AllowDarkModeForWindow(" + allow + ")", jniProp, ffmProp);
		}
		check("AllowDarkModeForWindow(0, true)", OS.AllowDarkModeForWindow(0, true), FFMOsCustom.AllowDarkModeForWindow(0, true));
		check("AllowDarkModeForWindow(-1, true)", OS.AllowDarkModeForWindow(-1, true), FFMOsCustom.AllowDarkModeForWindow(-1, true));
		OS.DestroyWindow(hwnd);
		check("AllowDarkModeForWindow(destroyed, true)", OS.AllowDarkModeForWindow(hwnd, true), FFMOsCustom.AllowDarkModeForWindow(hwnd, true));
	}

	interface Validator {
		boolean validate(MemorySegment code, boolean arm64);
	}

	/** Random code, code with a known signature planted, and planted code with one byte changed. */
	static void validators(String dll) throws Throwable {
		SymbolLookup lookup = SymbolLookup.libraryLookup(java.nio.file.Path.of(dll), Arena.global());
		Linker linker = Linker.nativeLinker();
		FunctionDescriptor fd = FunctionDescriptor.of(JAVA_INT, ADDRESS);
		String[] names = {"AllowDarkModeForWindow", "AllowDarkModeForWindowWithTelemetryId", "SetPreferredAppMode"};
		Validator[] java = {FFMOsCustom::validateAllowDarkModeForWindow, FFMOsCustom::validateAllowDarkModeForWindowWithTelemetryId, FFMOsCustom::validateSetPreferredAppMode};
		long[] real = {FFMOsCustom.uxthemeOrdinal(133), FFMOsCustom.uxthemeOrdinal(140), FFMOsCustom.uxthemeOrdinal(135)};
		Random random = new Random(42);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment buffer = arena.allocate(FFMOsCustom.CODE_SIZE);
			for (int v = 0; v < names.length; v++) {
				for (boolean arm64 : new boolean[] {false, true}) {
					MethodHandle c = linker.downcallHandle(lookup.findOrThrow((arm64 ? "arm64_" : "x64_") + names[v]), fd);
					String label = names[v] + (arm64 ? " arm64" : " x64");
					if (!arm64) {
						boolean cReal = (int) c.invokeExact(FFMOsCustom.code(real[v])) != 0;
						check(label + " on the uxtheme code", cReal, java[v].validate(FFMOsCustom.code(real[v]), false));
					}
					int mismatches = 0, accepted = 0, runs = 300_000;
					for (int i = 0; i < runs; i++) {
						byte[] bytes = new byte[(int) FFMOsCustom.CODE_SIZE];
						random.nextBytes(bytes);
						int mode = i % 3;
						if (mode > 0) plant(bytes, v, arm64, random);
						if (mode == 2) bytes[random.nextInt(0x28 + (arm64 ? 0 : 0x30))] ^= (byte) (1 << random.nextInt(8));
						MemorySegment.copy(bytes, 0, buffer, JAVA_BYTE, 0, bytes.length);
						boolean cResult = (int) c.invokeExact(buffer) != 0;
						boolean javaResult = java[v].validate(buffer, arm64);
						if (cResult != javaResult) mismatches++;
						if (cResult) accepted++;
					}
					check(label + " on " + runs + " random buffers (" + accepted + " accepted), mismatches", 0, mismatches);
				}
			}
		}
	}

	static void putInt(byte[] b, int offset, int value) {
		for (int i = 0; i < 4; i++) b[offset + i] = (byte) (value >>> (8 * i));
	}

	/** Plants one of the signatures the C accepts, picked at random. */
	static void plant(byte[] b, int validator, boolean arm64, Random random) {
		if (arm64) {
			if (validator < 2) {
				putInt(b, 0x18, 0xD29523C1);
			} else if (random.nextBoolean()) {
				int imm = random.nextInt(0x1000) & ~3;
				putInt(b, 0x14, 0xB9400113 | ((imm / 4 + (random.nextInt(4) == 0 ? 1 : 0)) & 0xFFF) << 10);
				putInt(b, 0x18, 0x2A0003E1);
				putInt(b, 0x1C, 0x91000100 | (imm & 0xFFF) << 10);
				putInt(b, 0x24, 0x2A1303E0);
			} else {
				putInt(b, 0x00, 0x90000008 | (random.nextInt() & 0x60FFFFE0));
				putInt(b, 0x04, 0x91000108 | (random.nextInt() & 0x007FFC00));
				putInt(b, 0x08, 0x2A0003E9);
				putInt(b, 0x0C, 0xB9400100);
				putInt(b, 0x10, 0xB8E98109);
			}
			return;
		}
		switch (validator) {
			case 0 -> {
				int at = random.nextBoolean() ? 0x52 : 0x15;
				b[at] = (byte) 0xBA;
				putInt(b, at + 1, 0xA91E);
			}
			case 1 -> {
				int pick = random.nextInt(3);
				int at = pick == 0 ? 0x31 : pick == 1 ? 0x15 : 0x16;
				b[at] = (byte) (pick == 2 ? 0xBE : 0xBA);
				putInt(b, at + 1, 0xA91E);
			}
			default -> {
				int offset = random.nextInt();
				b[0] = (byte) 0x8B;
				b[1] = 0x05;
				putInt(b, 2, offset + (random.nextInt(4) == 0 ? 0 : 6));
				b[6] = (byte) 0x87;
				b[7] = 0x0D;
				putInt(b, 8, offset);
				b[0x0C] = (byte) 0xC3;
			}
		}
	}
}
