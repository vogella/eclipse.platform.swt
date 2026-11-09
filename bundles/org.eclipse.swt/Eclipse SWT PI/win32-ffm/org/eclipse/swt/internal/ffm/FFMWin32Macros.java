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

import org.eclipse.swt.internal.ole.win32.GUID;
import org.eclipse.swt.internal.win32.RECT;

/**
 * The Win32 natives that are macros or constants in C, without a symbol to link against.
 */
public final class FFMWin32Macros {

	/** <code>CCSIZEOF_STRUCT(NOTIFYICONDATAW, dwInfoFlags)</code>, the same on x64 and aarch64. */
	static final int NOTIFYICONDATAW_V2_SIZE = 952;
	static final int TVM_GETITEMRECT = 0x1104;

	private static final class MH_SendMessageW {
		static final MethodHandle MH = FFM.downcall(FFMLibraries.get("user32.dll"), "SendMessageW",
			FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, ADDRESS));
	}

	private FFMWin32Macros() {
	}

	public static int PTR_sizeof() {
		return (int) ADDRESS.byteSize();
	}

	/** Compiled out of the JNI library on Windows (<code>NO_setenv</code>). */
	public static int setenv(byte[] env, byte[] value, int overwrite) {
		throw new UnsatisfiedLinkError("setenv is not available on Windows");
	}

	public static int NOTIFYICONDATA_V2_SIZE() {
		return NOTIFYICONDATAW_V2_SIZE;
	}

	public static double GID_ROTATE_ANGLE_FROM_ARGUMENT(long arg) {
		return ((((double) arg / 65535.0) * 4.0 * 3.14159265) - 2.0 * 3.14159265);
	}

	/** Compares the 16 bytes, as the C++ <code>IsEqualGUID</code>; a null GUID throws where C crashes. */
	public static boolean IsEqualGUID(GUID a, GUID b) {
		if (a.Data1 != b.Data1 || a.Data2 != b.Data2 || a.Data3 != b.Data3) return false;
		for (int i = 0; i < 8; i++) {
			if (a.Data4[i] != b.Data4[i]) return false;
		}
		return true;
	}

	/** Stores the item in the first 8 bytes of the rectangle and sends <code>TVM_GETITEMRECT</code>. */
	public static boolean TreeView_GetItemRect(long hwnd, long item, RECT prc, boolean code) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment rect = arena.allocate(16, 8);
			if (prc != null) org.eclipse.swt.internal.win32.Structs_FFM.RECT_write(rect, prc);
			rect.set(JAVA_LONG, 0, item);
			long rc = (long) MH_SendMessageW.MH.invokeExact(hwnd, TVM_GETITEMRECT, code ? 1L : 0L, rect);
			FFM.checkCallbackException();
			if (prc != null) org.eclipse.swt.internal.win32.Structs_FFM.RECT_read(rect, prc);
			return (byte) rc != 0;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}
}
