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

/**
 * Java port of the UTF-16 offset helpers of os_custom.c, which translate between
 * byte offsets into a UTF-8 string and the UTF-16 offsets SWT works with.
 */
public final class FFMUtf16 {

	private FFMUtf16() {
	}

	/** Bytes of the character starting with <code>b</code>, following glib's g_utf8_skip table. */
	static long skip(int b) {
		if (b < 0xc0 || b > 0xfd) return 1;
		if (b < 0xe0) return 2;
		if (b < 0xf0) return 3;
		if (b < 0xf8) return 4;
		return b < 0xfc ? 5 : 6;
	}

	/** Whether the character starting with <code>b</code> needs a surrogate pair in UTF-16. */
	static boolean isSurrogatePair(int b) {
		return 0xf0 <= b && b <= 0xfd;
	}

	static MemorySegment string(long pointer) {
		return MemorySegment.ofAddress(pointer).reinterpret(Long.MAX_VALUE);
	}

	static int at(MemorySegment string, long index) {
		return string.get(JAVA_BYTE, index) & 0xff;
	}

	public static long g_utf16_strlen(long str, long max) {
		if (str == 0 || max == 0) return 0;
		MemorySegment string = string(str);
		long offset = 0, i = 0, b;
		while ((b = at(string, i)) != 0) {
			long next = i + skip((int) b);
			if (max >= 0 && next > max) break;
			if (isSurrogatePair((int) b)) offset++;
			offset++;
			i = next;
		}
		return offset;
	}

	public static long g_utf16_pointer_to_offset(long str, long pos) {
		if (str == 0 || pos == 0) return 0;
		MemorySegment string = string(str);
		long offset = 0, i = 0, b;
		while (str + i < pos && (b = at(string, i)) != 0) {
			if (isSurrogatePair((int) b)) offset++;
			offset++;
			i += skip((int) b);
		}
		return offset;
	}

	public static long g_utf16_offset_to_pointer(long str, long offset) {
		if (str == 0) return 0;
		MemorySegment string = string(str);
		long i = 0, b;
		while (offset-- > 0 && (b = at(string, i)) != 0) {
			if (isSurrogatePair((int) b)) offset--;
			i += skip((int) b);
		}
		return str + i;
	}

	public static long g_utf16_offset_to_utf8_offset(long str, long offset) {
		if (str == 0) return 0;
		MemorySegment string = string(str);
		long result = 0, i = 0, b;
		while (offset-- > 0 && (b = at(string, i)) != 0) {
			if (isSurrogatePair((int) b)) offset--;
			i += skip((int) b);
			result++;
		}
		return result;
	}

	public static long g_utf8_offset_to_utf16_offset(long str, long offset) {
		if (str == 0) return 0;
		MemorySegment string = string(str);
		long result = 0, i = 0, b;
		while (offset-- > 0 && (b = at(string, i)) != 0) {
			if (isSurrogatePair((int) b)) result++;
			i += skip((int) b);
			result++;
		}
		return result;
	}
}
