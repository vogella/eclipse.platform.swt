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

import org.eclipse.swt.internal.gdip.*;

/**
 * The GDI+ objects of {@link Gdip} (everything but <code>Graphics_*</code> and the start-up) on the
 * flat API of gdiplus.dll, reproducing the inline C++ wrappers of the Windows SDK that gdip.cpp calls.
 * <p>
 * A handle is the flat API object (<code>GpBitmap*</code>, <code>GpPen*</code>, ...), not a C++
 * wrapper. A C++ wrapper without a native object behaves like a null handle as an argument, since the
 * wrappers pass their null native pointer on. Two cases need a handle standing for such a wrapper,
 * see {@link #nativeObject(long)}: a failed <code>Font_new</code>, which SWT keeps and asks
 * <code>Font_IsAvailable</code>, and the empty <code>FontFamily_new()</code> that
 * <code>Font_GetFamily</code> fills.
 * </p>
 */
public final class FFMGdipObjects {

	/** The natives class whose natives this class implements, read by the rewriter. */
	static final String IMPLEMENTS = "org.eclipse.swt.internal.gdip.Gdip";

	static final int Ok = 0, InvalidParameter = 2, NotImplemented = 6;
	static final int UnitWorld = 0, WrapModeTile = 0;
	static final int ARGB_BLACK = 0xFF000000;
	static final int LOGFONTA_SIZEOF = 60, BitmapData_SIZEOF = 32, ColorPalette_SIZEOF = 12, GdiplusStartupInput_SIZEOF = 24;

	static final SymbolLookup GDIPLUS = FFM.library("gdiplus.dll");
	static final SymbolLookup GDI32 = FFM.library("gdi32.dll");
	static final SymbolLookup KERNEL32 = FFM.library("kernel32.dll");

	static final ValueLayout P = JAVA_LONG, I = JAVA_INT, F = JAVA_FLOAT;

	/** Handles standing for a C++ wrapper, mapped to its native object, 0 while it has none. */
	static final Map<Long, Long> PROXIES = new ConcurrentHashMap<>();

	/** The lastResult of the C++ Image and Brush wrappers, kept only while it is not Ok. */
	static final Map<Long, Integer> LAST_STATUS = new ConcurrentHashMap<>();

	private FFMGdipObjects() {
	}

	static MethodHandle gdip(String name, MemoryLayout... arguments) {
		return FFM.downcall(GDIPLUS, name, FunctionDescriptor.of(I, arguments));
	}

	/**
	 * Returns the flat API object behind a handle this class returned, 0 for a C++ wrapper without
	 * one. Callers passing a font to GDI+, such as <code>Graphics_DrawString</code>, use it.
	 */
	public static long nativeObject(long handle) {
		if (handle == 0 || PROXIES.isEmpty()) return handle;
		Long object = PROXIES.get(handle);
		return object == null ? handle : object;
	}

	static long newProxy(long object) {
		long proxy = heapAlloc(8);
		if (proxy != 0) PROXIES.put(proxy, object);
		return proxy;
	}

	/** Frees <code>handle</code> if it is a proxy. */
	static void releaseProxy(long handle) {
		if (handle == 0 || PROXIES.isEmpty()) return;
		if (PROXIES.remove(handle) != null) heapFree(handle);
	}

	/** SetStatus of the C++ wrappers: remembers a failure as lastResult of the object. */
	static int setStatus(long object, int status) {
		if (status != Ok && object != 0) LAST_STATUS.put(object, status);
		return status;
	}

	static int lastResult(long object) {
		if (LAST_STATUS.isEmpty()) return Ok;
		Integer status = LAST_STATUS.get(object);
		return status == null ? Ok : status;
	}

	/** A C++ wrapper created from another one starts with its lastResult. */
	static long inheritStatus(long from, long to) {
		if (to != 0 && !LAST_STATUS.isEmpty()) {
			Integer status = LAST_STATUS.get(from);
			if (status != null) LAST_STATUS.put(to, status); else LAST_STATUS.remove(to);
		}
		return to;
	}

	static void forget(long object) {
		if (object != 0 && !LAST_STATUS.isEmpty()) LAST_STATUS.remove(object);
	}

	static boolean bool(int value) {
		return (value & 0xFF) != 0;
	}

	/* Memory */

	private static final class MH_GetProcessHeap {
		static final MethodHandle MH = FFM.downcall(KERNEL32, "GetProcessHeap", FunctionDescriptor.of(P));
	}
	private static final class MH_HeapAlloc {
		static final MethodHandle MH = FFM.downcall(KERNEL32, "HeapAlloc", FunctionDescriptor.of(P, P, I, P));
	}
	private static final class MH_HeapFree {
		static final MethodHandle MH = FFM.downcall(KERNEL32, "HeapFree", FunctionDescriptor.of(I, P, I, P));
	}

	/** Zeroed memory from the process heap. */
	static long heapAlloc(long size) {
		try {
			long heap = (long) MH_GetProcessHeap.MH.invokeExact();
			return (long) MH_HeapAlloc.MH.invokeExact(heap, 0x00000008 /* HEAP_ZERO_MEMORY */, size);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static void heapFree(long pointer) {
		if (pointer == 0) return;
		try {
			long heap = (long) MH_GetProcessHeap.MH.invokeExact();
			int ignored = (int) MH_HeapFree.MH.invokeExact(heap, 0, pointer);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Calls a flat API function that returns an object through its last argument. */
	@FunctionalInterface
	interface Create {
		int call(long out) throws Throwable;
	}

	static long create(Create function) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_LONG);
			function.call(out.address());
			return out.get(JAVA_LONG, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Calls a getter of an int (or BOOL) and remembers a failure as lastResult of <code>object</code> when it is tracked. */
	static int getInt(long object, boolean track, Create function) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_INT);
			int status = function.call(out.address());
			if (track) setStatus(object, status);
			return out.get(JAVA_INT, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static MemorySegment floats(Arena arena, float[] array, long minimum) {
		if (array == null) return MemorySegment.NULL;
		MemorySegment segment = arena.allocate(JAVA_FLOAT, Math.max(array.length, minimum));
		MemorySegment.copy(array, 0, segment, JAVA_FLOAT, 0, array.length);
		return segment;
	}

	static MemorySegment ints(Arena arena, int[] array, long count) {
		if (array == null) return MemorySegment.NULL;
		MemorySegment segment = arena.allocate(JAVA_INT, Math.max(count, 1));
		MemorySegment.copy(array, 0, segment, JAVA_INT, 0, (int) Math.min(array.length, Math.max(count, 0)));
		return segment;
	}

	static MemorySegment pointF(Arena arena, PointF point, long count) {
		if (point == null) return MemorySegment.NULL;
		MemorySegment segment = arena.allocate(JAVA_FLOAT, 2 * Math.max(count, 1));
		segment.set(JAVA_FLOAT, 0, point.X);
		segment.set(JAVA_FLOAT, 4, point.Y);
		return segment;
	}

	static void pointF(MemorySegment segment, PointF point) {
		if (point == null) return;
		point.X = segment.get(JAVA_FLOAT, 0);
		point.Y = segment.get(JAVA_FLOAT, 4);
	}

	static MemorySegment rectF(Arena arena, RectF rect) {
		if (rect == null) return MemorySegment.NULL;
		MemorySegment segment = arena.allocate(JAVA_FLOAT, 4);
		segment.set(JAVA_FLOAT, 0, rect.X);
		segment.set(JAVA_FLOAT, 4, rect.Y);
		segment.set(JAVA_FLOAT, 8, rect.Width);
		segment.set(JAVA_FLOAT, 12, rect.Height);
		return segment;
	}

	static void rectF(MemorySegment segment, RectF rect) {
		if (rect == null) return;
		rect.X = segment.get(JAVA_FLOAT, 0);
		rect.Y = segment.get(JAVA_FLOAT, 4);
		rect.Width = segment.get(JAVA_FLOAT, 8);
		rect.Height = segment.get(JAVA_FLOAT, 12);
	}

	/* Struct sizes and copies */

	public static int ColorPalette_sizeof() {
		return ColorPalette_SIZEOF;
	}

	public static int GdiplusStartupInput_sizeof() {
		return GdiplusStartupInput_SIZEOF;
	}

	/** Like the JNI glue, copies into a ColorPalette of one entry, never beyond it. */
	public static void MoveMemory(ColorPalette Destination, long SourcePtr, int Length) {
		if (Destination == null) return;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment palette = arena.allocate(ColorPalette_SIZEOF);
			palette.set(JAVA_INT, 0, Destination.Flags);
			palette.set(JAVA_INT, 4, Destination.Count);
			palette.set(JAVA_INT, 8, Destination.Entries[0]);
			MemorySegment.copy(FFM.segment(SourcePtr, Length), 0, palette, 0, Math.min(Length, ColorPalette_SIZEOF));
			Destination.Flags = palette.get(JAVA_INT, 0);
			Destination.Count = palette.get(JAVA_INT, 4);
			Destination.Entries[0] = palette.get(JAVA_INT, 8);
		}
	}

	public static void MoveMemory(BitmapData Destination, long SourcePtr) {
		if (Destination == null) return;
		MemorySegment data = FFM.segment(SourcePtr, BitmapData_SIZEOF);
		Destination.Width = data.get(JAVA_INT, 0);
		Destination.Height = data.get(JAVA_INT, 4);
		Destination.Stride = data.get(JAVA_INT, 8);
		Destination.PixelFormat = data.get(JAVA_INT, 12);
		Destination.Scan0 = data.get(JAVA_LONG, 16);
		Destination.Reserved = data.get(JAVA_LONG, 24);
	}

	/* Plain structs allocated with new */

	public static long BitmapData_new() {
		return heapAlloc(BitmapData_SIZEOF);
	}

	public static void BitmapData_delete(long bitmapData) {
		heapFree(bitmapData);
	}

	public static long Point_new(int x, int y) {
		long point = heapAlloc(8);
		if (point != 0) {
			MemorySegment segment = FFM.segment(point, 8);
			segment.set(JAVA_INT, 0, x);
			segment.set(JAVA_INT, 4, y);
		}
		return point;
	}

	public static void Point_delete(long point) {
		heapFree(point);
	}

	/* Image and Bitmap */

	private static final class MH_GdipCreateBitmapFromHBITMAP {
		static final MethodHandle MH = gdip("GdipCreateBitmapFromHBITMAP", P, P, P);
	}
	private static final class MH_GdipCreateBitmapFromHICON {
		static final MethodHandle MH = gdip("GdipCreateBitmapFromHICON", P, P);
	}
	private static final class MH_GdipCreateBitmapFromScan0 {
		static final MethodHandle MH = gdip("GdipCreateBitmapFromScan0", I, I, I, I, P, P);
	}
	private static final class MH_GdipCreateBitmapFromFile {
		static final MethodHandle MH = gdip("GdipCreateBitmapFromFile", P, P);
	}
	private static final class MH_GdipCreateBitmapFromFileICM {
		static final MethodHandle MH = gdip("GdipCreateBitmapFromFileICM", P, P);
	}
	private static final class MH_GdipDisposeImage {
		static final MethodHandle MH = gdip("GdipDisposeImage", P);
	}
	private static final class MH_GdipCreateHBITMAPFromBitmap {
		static final MethodHandle MH = gdip("GdipCreateHBITMAPFromBitmap", P, P, I);
	}
	private static final class MH_GdipCreateHICONFromBitmap {
		static final MethodHandle MH = gdip("GdipCreateHICONFromBitmap", P, P);
	}
	private static final class MH_GdipBitmapLockBits {
		static final MethodHandle MH = gdip("GdipBitmapLockBits", P, P, I, I, P);
	}
	private static final class MH_GdipBitmapUnlockBits {
		static final MethodHandle MH = gdip("GdipBitmapUnlockBits", P, P);
	}
	private static final class MH_GdipCloneImage {
		static final MethodHandle MH = gdip("GdipCloneImage", P, P);
	}
	private static final class MH_GdipGetImageWidth {
		static final MethodHandle MH = gdip("GdipGetImageWidth", P, P);
	}
	private static final class MH_GdipGetImageHeight {
		static final MethodHandle MH = gdip("GdipGetImageHeight", P, P);
	}
	private static final class MH_GdipGetImagePixelFormat {
		static final MethodHandle MH = gdip("GdipGetImagePixelFormat", P, P);
	}
	private static final class MH_GdipGetImagePalette {
		static final MethodHandle MH = gdip("GdipGetImagePalette", P, P, I);
	}
	private static final class MH_GdipGetImagePaletteSize {
		static final MethodHandle MH = gdip("GdipGetImagePaletteSize", P, P);
	}

	public static long Bitmap_new(long hbm, long hpal) {
		return forgotten(create(out -> (int) MH_GdipCreateBitmapFromHBITMAP.MH.invokeExact(hbm, hpal, out)));
	}

	public static long Bitmap_new(long hicon) {
		return forgotten(create(out -> (int) MH_GdipCreateBitmapFromHICON.MH.invokeExact(hicon, out)));
	}

	public static long Bitmap_new(int width, int height, int stride, int format, long scan0) {
		return forgotten(create(out -> (int) MH_GdipCreateBitmapFromScan0.MH.invokeExact(width, height, stride, format, scan0, out)));
	}

	public static long Bitmap_new(char[] filename, boolean useIcm) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment name = FFM.copyIn(arena, filename);
			long bitmap = create(out -> useIcm
				? (int) MH_GdipCreateBitmapFromFileICM.MH.invokeExact(name.address(), out)
				: (int) MH_GdipCreateBitmapFromFile.MH.invokeExact(name.address(), out));
			FFM.copyOut(name, filename);
			return forgotten(bitmap);
		}
	}

	/** A new object must not inherit the lastResult of a deleted one at the same address. */
	static long forgotten(long object) {
		forget(object);
		return object;
	}

	public static void Bitmap_delete(long bitmap) {
		Image_delete(bitmap);
	}

	public static void Image_delete(long image) {
		try {
			int ignored = (int) MH_GdipDisposeImage.MH.invokeExact(image);
			forget(image);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Bitmap_GetHBITMAP(long bitmap, int colorBackground, long[] hbmReturn) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = FFM.copyIn(arena, hbmReturn);
			int status = (int) MH_GdipCreateHBITMAPFromBitmap.MH.invokeExact(bitmap, out.address(), colorBackground);
			FFM.copyOut(out, hbmReturn);
			return setStatus(bitmap, status);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Bitmap_GetHICON(long bitmap, long[] hicon) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = FFM.copyIn(arena, hicon);
			int status = (int) MH_GdipCreateHICONFromBitmap.MH.invokeExact(bitmap, out.address());
			FFM.copyOut(out, hicon);
			return setStatus(bitmap, status);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Bitmap_LockBits(long bitmap, long rect, int flags, int pixelFormat, long lockedBitmapData) {
		try {
			return setStatus(bitmap, (int) MH_GdipBitmapLockBits.MH.invokeExact(bitmap, rect, flags, pixelFormat, lockedBitmapData));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Bitmap_UnlockBits(long bitmap, long lockedBitmapData) {
		try {
			return setStatus(bitmap, (int) MH_GdipBitmapUnlockBits.MH.invokeExact(bitmap, lockedBitmapData));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long Image_Clone(long image) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_LONG);
			setStatus(image, (int) MH_GdipCloneImage.MH.invokeExact(image, out.address()));
			return inheritStatus(image, out.get(JAVA_LONG, 0));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Returns and clears the lastResult of the C++ wrapper. */
	public static int Image_GetLastStatus(long image) {
		if (image == 0) return InvalidParameter;
		Integer status = LAST_STATUS.isEmpty() ? null : LAST_STATUS.remove(image);
		return status == null ? Ok : status;
	}

	public static int Image_GetPixelFormat(long image) {
		return getInt(image, true, out -> (int) MH_GdipGetImagePixelFormat.MH.invokeExact(image, out));
	}

	public static int Image_GetWidth(long image) {
		return getInt(image, true, out -> (int) MH_GdipGetImageWidth.MH.invokeExact(image, out));
	}

	public static int Image_GetHeight(long image) {
		return getInt(image, true, out -> (int) MH_GdipGetImageHeight.MH.invokeExact(image, out));
	}

	public static int Image_GetPalette(long image, long palette, int size) {
		try {
			return setStatus(image, (int) MH_GdipGetImagePalette.MH.invokeExact(image, palette, size));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Image_GetPaletteSize(long image) {
		return getInt(image, true, out -> (int) MH_GdipGetImagePaletteSize.MH.invokeExact(image, out));
	}

	/* ImageAttributes */

	private static final class MH_GdipCreateImageAttributes {
		static final MethodHandle MH = gdip("GdipCreateImageAttributes", P);
	}
	private static final class MH_GdipDisposeImageAttributes {
		static final MethodHandle MH = gdip("GdipDisposeImageAttributes", P);
	}
	private static final class MH_GdipSetImageAttributesWrapMode {
		static final MethodHandle MH = gdip("GdipSetImageAttributesWrapMode", P, I, I, I);
	}
	private static final class MH_GdipSetImageAttributesColorMatrix {
		static final MethodHandle MH = gdip("GdipSetImageAttributesColorMatrix", P, I, I, P, P, I);
	}

	public static long ImageAttributes_new() {
		return create(out -> (int) MH_GdipCreateImageAttributes.MH.invokeExact(out));
	}

	public static void ImageAttributes_delete(long attrib) {
		try {
			int ignored = (int) MH_GdipDisposeImageAttributes.MH.invokeExact(attrib);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** SetWrapMode(wrap) with its defaults: color black, no clamp. */
	public static int ImageAttributes_SetWrapMode(long attrib, int wrap) {
		try {
			return (int) MH_GdipSetImageAttributesWrapMode.MH.invokeExact(attrib, wrap, ARGB_BLACK, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int ImageAttributes_SetColorMatrix(long attrib, float[] matrix, int mode, int type) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment m = floats(arena, matrix, 25);
			int status = (int) MH_GdipSetImageAttributesColorMatrix.MH.invokeExact(attrib, type, 1, m.address(), 0L, mode);
			FFM.copyOut(m, matrix);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* Brushes */

	private static final class MH_GdipCloneBrush {
		static final MethodHandle MH = gdip("GdipCloneBrush", P, P);
	}
	private static final class MH_GdipGetBrushType {
		static final MethodHandle MH = gdip("GdipGetBrushType", P, P);
	}
	private static final class MH_GdipDeleteBrush {
		static final MethodHandle MH = gdip("GdipDeleteBrush", P);
	}
	private static final class MH_GdipCreateSolidFill {
		static final MethodHandle MH = gdip("GdipCreateSolidFill", I, P);
	}
	private static final class MH_GdipCreateHatchBrush {
		static final MethodHandle MH = gdip("GdipCreateHatchBrush", I, I, I, P);
	}

	public static long Brush_Clone(long brush) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_LONG);
			setStatus(brush, (int) MH_GdipCloneBrush.MH.invokeExact(brush, out.address()));
			return inheritStatus(brush, out.get(JAVA_LONG, 0));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Returns -1 when the type cannot be read, as the C++ wrapper does. */
	public static int Brush_GetType(long brush) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocateFrom(JAVA_INT, -1);
			setStatus(brush, (int) MH_GdipGetBrushType.MH.invokeExact(brush, out.address()));
			return out.get(JAVA_INT, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Every brush wrapper has the virtual destructor of Brush. */
	static void deleteBrush(long brush) {
		try {
			int ignored = (int) MH_GdipDeleteBrush.MH.invokeExact(brush);
			forget(brush);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long SolidBrush_new(int color) {
		return forgotten(create(out -> (int) MH_GdipCreateSolidFill.MH.invokeExact(color, out)));
	}

	public static void SolidBrush_delete(long brush) {
		deleteBrush(brush);
	}

	public static long HatchBrush_new(int hatchStyle, int foreColor, int backColor) {
		return forgotten(create(out -> (int) MH_GdipCreateHatchBrush.MH.invokeExact(hatchStyle, foreColor, backColor, out)));
	}

	public static void HatchBrush_delete(long brush) {
		deleteBrush(brush);
	}

	/* TextureBrush */

	private static final class MH_GdipCreateTexture2 {
		static final MethodHandle MH = gdip("GdipCreateTexture2", P, I, F, F, F, F, P);
	}
	private static final class MH_GdipCreateTextureIAI {
		static final MethodHandle MH = gdip("GdipCreateTextureIAI", P, P, I, I, I, I, P);
	}
	private static final class MH_GdipSetTextureTransform {
		static final MethodHandle MH = gdip("GdipSetTextureTransform", P, P);
	}
	private static final class MH_GdipResetTextureTransform {
		static final MethodHandle MH = gdip("GdipResetTextureTransform", P);
	}
	private static final class MH_GdipScaleTextureTransform {
		static final MethodHandle MH = gdip("GdipScaleTextureTransform", P, F, F, I);
	}
	private static final class MH_GdipTranslateTextureTransform {
		static final MethodHandle MH = gdip("GdipTranslateTextureTransform", P, F, F, I);
	}
	private static final class MH_GdipGetTextureImage {
		static final MethodHandle MH = gdip("GdipGetTextureImage", P, P);
	}

	public static long TextureBrush_new(long image, int wrapMode, float dstX, float dstY, float dstWidth, float dstHeight) {
		return forgotten(create(out -> (int) MH_GdipCreateTexture2.MH.invokeExact(image, wrapMode, dstX, dstY, dstWidth, dstHeight, out)));
	}

	public static long TextureBrush_new(long image, Rect rect, long attribs) {
		int x = rect.X, y = rect.Y, width = rect.Width, height = rect.Height;
		return forgotten(create(out -> (int) MH_GdipCreateTextureIAI.MH.invokeExact(image, attribs, x, y, width, height, out)));
	}

	public static void TextureBrush_delete(long brush) {
		deleteBrush(brush);
	}

	public static int TextureBrush_SetTransform(long brush, long matrix) {
		try {
			return setStatus(brush, (int) MH_GdipSetTextureTransform.MH.invokeExact(brush, matrix));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int TextureBrush_ResetTransform(long brush) {
		try {
			return setStatus(brush, (int) MH_GdipResetTextureTransform.MH.invokeExact(brush));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int TextureBrush_ScaleTransform(long brush, float sx, float sy, int order) {
		try {
			return setStatus(brush, (int) MH_GdipScaleTextureTransform.MH.invokeExact(brush, sx, sy, order));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int TextureBrush_TranslateTransform(long brush, float dx, float dy, int order) {
		try {
			return setStatus(brush, (int) MH_GdipTranslateTextureTransform.MH.invokeExact(brush, dx, dy, order));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** The image is a new object, which the C++ wrapper hands out with the lastResult of the brush. */
	public static long TextureBrush_GetImage(long brush) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_LONG);
			setStatus(brush, (int) MH_GdipGetTextureImage.MH.invokeExact(brush, out.address()));
			return inheritStatus(brush, out.get(JAVA_LONG, 0));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* LinearGradientBrush */

	private static final class MH_GdipCreateLineBrush {
		static final MethodHandle MH = gdip("GdipCreateLineBrush", P, P, I, I, I, P);
	}
	private static final class MH_GdipSetLinePresetBlend {
		static final MethodHandle MH = gdip("GdipSetLinePresetBlend", P, P, P, I);
	}
	private static final class MH_GdipSetLineWrapMode {
		static final MethodHandle MH = gdip("GdipSetLineWrapMode", P, I);
	}
	private static final class MH_GdipResetLineTransform {
		static final MethodHandle MH = gdip("GdipResetLineTransform", P);
	}
	private static final class MH_GdipScaleLineTransform {
		static final MethodHandle MH = gdip("GdipScaleLineTransform", P, F, F, I);
	}
	private static final class MH_GdipTranslateLineTransform {
		static final MethodHandle MH = gdip("GdipTranslateLineTransform", P, F, F, I);
	}

	public static long LinearGradientBrush_new(PointF point1, PointF point2, int color1, int color2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment p1 = pointF(arena, Objects.requireNonNull(point1), 1);
			MemorySegment p2 = pointF(arena, Objects.requireNonNull(point2), 1);
			return forgotten(create(out -> (int) MH_GdipCreateLineBrush.MH.invokeExact(p1.address(), p2.address(), color1, color2, WrapModeTile, out)));
		}
	}

	public static void LinearGradientBrush_delete(long brush) {
		deleteBrush(brush);
	}

	public static int LinearGradientBrush_SetInterpolationColors(long brush, int[] presetColors, float[] blendPositions, int count) {
		if (count <= 0 || presetColors == null) return setStatus(brush, InvalidParameter);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment argbs = ints(arena, presetColors, count);
			MemorySegment positions = floats(arena, blendPositions, count);
			return setStatus(brush, (int) MH_GdipSetLinePresetBlend.MH.invokeExact(brush, argbs.address(), positions.address(), count));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int LinearGradientBrush_SetWrapMode(long brush, int wrapMode) {
		try {
			return setStatus(brush, (int) MH_GdipSetLineWrapMode.MH.invokeExact(brush, wrapMode));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int LinearGradientBrush_ResetTransform(long brush) {
		try {
			return setStatus(brush, (int) MH_GdipResetLineTransform.MH.invokeExact(brush));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int LinearGradientBrush_ScaleTransform(long brush, float sx, float sy, int order) {
		try {
			return setStatus(brush, (int) MH_GdipScaleLineTransform.MH.invokeExact(brush, sx, sy, order));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int LinearGradientBrush_TranslateTransform(long brush, float dx, float dy, int order) {
		try {
			return setStatus(brush, (int) MH_GdipTranslateLineTransform.MH.invokeExact(brush, dx, dy, order));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* PathGradientBrush */

	private static final class MH_GdipCreatePathGradientFromPath {
		static final MethodHandle MH = gdip("GdipCreatePathGradientFromPath", P, P);
	}
	private static final class MH_GdipSetPathGradientCenterColor {
		static final MethodHandle MH = gdip("GdipSetPathGradientCenterColor", P, I);
	}
	private static final class MH_GdipSetPathGradientCenterPoint {
		static final MethodHandle MH = gdip("GdipSetPathGradientCenterPoint", P, P);
	}
	private static final class MH_GdipSetPathGradientPresetBlend {
		static final MethodHandle MH = gdip("GdipSetPathGradientPresetBlend", P, P, P, I);
	}
	private static final class MH_GdipGetPathGradientPointCount {
		static final MethodHandle MH = gdip("GdipGetPathGradientPointCount", P, P);
	}
	private static final class MH_GdipSetPathGradientSurroundColorsWithCount {
		static final MethodHandle MH = gdip("GdipSetPathGradientSurroundColorsWithCount", P, P, P);
	}
	private static final class MH_GdipSetPathGradientPath {
		static final MethodHandle MH = gdip("GdipSetPathGradientPath", P, P);
	}
	private static final class MH_GdipSetPathGradientWrapMode {
		static final MethodHandle MH = gdip("GdipSetPathGradientWrapMode", P, I);
	}

	public static long PathGradientBrush_new(long path) {
		return forgotten(create(out -> (int) MH_GdipCreatePathGradientFromPath.MH.invokeExact(path, out)));
	}

	public static void PathGradientBrush_delete(long brush) {
		deleteBrush(brush);
	}

	/** Returns lastResult, which keeps an earlier failure, as the C++ wrapper does. */
	public static int PathGradientBrush_SetCenterColor(long brush, int color) {
		try {
			setStatus(brush, (int) MH_GdipSetPathGradientCenterColor.MH.invokeExact(brush, color));
			return lastResult(brush);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int PathGradientBrush_SetCenterPoint(long brush, PointF pt) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment point = pointF(arena, Objects.requireNonNull(pt), 1);
			return setStatus(brush, (int) MH_GdipSetPathGradientCenterPoint.MH.invokeExact(brush, point.address()));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int PathGradientBrush_SetInterpolationColors(long brush, int[] presetColors, float[] blendPositions, int count) {
		if (count <= 0 || presetColors == null) return setStatus(brush, InvalidParameter);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment argbs = ints(arena, presetColors, count);
			MemorySegment positions = floats(arena, blendPositions, count);
			return setStatus(brush, (int) MH_GdipSetPathGradientPresetBlend.MH.invokeExact(brush, argbs.address(), positions.address(), count));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Checks the count against the points of the brush first and returns lastResult, as the C++ wrapper does. */
	public static int PathGradientBrush_SetSurroundColors(long brush, int[] colors, int[] count) {
		if (colors == null || count == null) return setStatus(brush, InvalidParameter);
		int points = getInt(brush, true, out -> (int) MH_GdipGetPathGradientPointCount.MH.invokeExact(brush, out));
		if (count[0] > points || points <= 0) return setStatus(brush, InvalidParameter);
		try (Arena arena = Arena.ofConfined()) {
			int n = count[0];
			MemorySegment argbs = ints(arena, colors, n);
			MemorySegment inOut = arena.allocateFrom(JAVA_INT, n);
			setStatus(brush, (int) MH_GdipSetPathGradientSurroundColorsWithCount.MH.invokeExact(brush, argbs.address(), inOut.address()));
			int status = lastResult(brush);
			if (status == Ok) count[0] = inOut.get(JAVA_INT, 0);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int PathGradientBrush_SetGraphicsPath(long brush, long path) {
		if (path == 0) return setStatus(brush, InvalidParameter);
		try {
			return setStatus(brush, (int) MH_GdipSetPathGradientPath.MH.invokeExact(brush, path));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int PathGradientBrush_SetWrapMode(long brush, int wrapMode) {
		try {
			return setStatus(brush, (int) MH_GdipSetPathGradientWrapMode.MH.invokeExact(brush, wrapMode));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* Pen */

	private static final class MH_GdipCreatePen2 {
		static final MethodHandle MH = gdip("GdipCreatePen2", P, F, I, P);
	}
	private static final class MH_GdipDeletePen {
		static final MethodHandle MH = gdip("GdipDeletePen", P);
	}
	private static final class MH_GdipGetPenFillType {
		static final MethodHandle MH = gdip("GdipGetPenFillType", P, P);
	}
	private static final class MH_GdipGetPenBrushFill {
		static final MethodHandle MH = gdip("GdipGetPenBrushFill", P, P);
	}
	private static final class MH_GdipSetPenBrushFill {
		static final MethodHandle MH = gdip("GdipSetPenBrushFill", P, P);
	}
	private static final class MH_GdipSetPenDashOffset {
		static final MethodHandle MH = gdip("GdipSetPenDashOffset", P, F);
	}
	private static final class MH_GdipSetPenDashArray {
		static final MethodHandle MH = gdip("GdipSetPenDashArray", P, P, I);
	}
	private static final class MH_GdipSetPenDashStyle {
		static final MethodHandle MH = gdip("GdipSetPenDashStyle", P, I);
	}
	private static final class MH_GdipSetPenLineCap197819 {
		static final MethodHandle MH = gdip("GdipSetPenLineCap197819", P, I, I, I);
	}
	private static final class MH_GdipSetPenLineJoin {
		static final MethodHandle MH = gdip("GdipSetPenLineJoin", P, I);
	}
	private static final class MH_GdipSetPenMiterLimit {
		static final MethodHandle MH = gdip("GdipSetPenMiterLimit", P, F);
	}
	private static final class MH_GdipSetPenWidth {
		static final MethodHandle MH = gdip("GdipSetPenWidth", P, F);
	}

	public static long Pen_new(long brush, float width) {
		return create(out -> (int) MH_GdipCreatePen2.MH.invokeExact(brush, width, UnitWorld, out));
	}

	public static void Pen_delete(long pen) {
		try {
			int ignored = (int) MH_GdipDeletePen.MH.invokeExact(pen);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** A new brush for the known pen types (solid, hatch, texture, path and linear gradient), otherwise 0. */
	public static long Pen_GetBrush(long pen) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment type = arena.allocateFrom(JAVA_INT, -1);
			int ignored = (int) MH_GdipGetPenFillType.MH.invokeExact(pen, type.address());
			int penType = type.get(JAVA_INT, 0);
			if (penType < 0 || penType > 4) return 0;
			long brush = create(out -> (int) MH_GdipGetPenBrushFill.MH.invokeExact(pen, out));
			return forgotten(brush);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetBrush(long pen, long brush) {
		try {
			return (int) MH_GdipSetPenBrushFill.MH.invokeExact(pen, brush);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetDashOffset(long pen, float dashOffset) {
		try {
			return (int) MH_GdipSetPenDashOffset.MH.invokeExact(pen, dashOffset);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetDashPattern(long pen, float[] dashArray, int count) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment dashes = floats(arena, dashArray, count);
			int status = (int) MH_GdipSetPenDashArray.MH.invokeExact(pen, dashes.address(), count);
			FFM.copyOut(dashes, dashArray);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetDashStyle(long pen, int dashStyle) {
		try {
			return (int) MH_GdipSetPenDashStyle.MH.invokeExact(pen, dashStyle);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetLineCap(long pen, int startCap, int endCap, int dashCap) {
		try {
			return (int) MH_GdipSetPenLineCap197819.MH.invokeExact(pen, startCap, endCap, dashCap);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetLineJoin(long pen, int lineJoin) {
		try {
			return (int) MH_GdipSetPenLineJoin.MH.invokeExact(pen, lineJoin);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetMiterLimit(long pen, float miterLimit) {
		try {
			return (int) MH_GdipSetPenMiterLimit.MH.invokeExact(pen, miterLimit);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Pen_SetWidth(long pen, float width) {
		try {
			return (int) MH_GdipSetPenWidth.MH.invokeExact(pen, width);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* Fonts */

	private static final class MH_GetObjectA {
		static final MethodHandle MH = FFM.downcall(GDI32, "GetObjectA", FunctionDescriptor.of(I, P, I, P));
	}
	private static final class MH_GdipNewPrivateFontCollection {
		static final MethodHandle MH = gdip("GdipNewPrivateFontCollection", P);
	}
	private static final class MH_GdipDeletePrivateFontCollection {
		static final MethodHandle MH = gdip("GdipDeletePrivateFontCollection", P);
	}
	private static final class MH_GdipPrivateAddFontFile {
		static final MethodHandle MH = gdip("GdipPrivateAddFontFile", P, P);
	}
	private static final class MH_GdipCreateFontFromLogfontA {
		static final MethodHandle MH = gdip("GdipCreateFontFromLogfontA", P, P, P);
	}
	private static final class MH_GdipCreateFontFromDC {
		static final MethodHandle MH = gdip("GdipCreateFontFromDC", P, P);
	}
	private static final class MH_GdipCreateFont {
		static final MethodHandle MH = gdip("GdipCreateFont", P, F, I, I, P);
	}
	private static final class MH_GdipDeleteFont {
		static final MethodHandle MH = gdip("GdipDeleteFont", P);
	}
	private static final class MH_GdipGetFamily {
		static final MethodHandle MH = gdip("GdipGetFamily", P, P);
	}
	private static final class MH_GdipGetFontSize {
		static final MethodHandle MH = gdip("GdipGetFontSize", P, P);
	}
	private static final class MH_GdipGetFontStyle {
		static final MethodHandle MH = gdip("GdipGetFontStyle", P, P);
	}
	private static final class MH_GdipGetLogFontW {
		static final MethodHandle MH = gdip("GdipGetLogFontW", P, P, P);
	}
	private static final class MH_GdipCreateFontFamilyFromName {
		static final MethodHandle MH = gdip("GdipCreateFontFamilyFromName", P, P, P);
	}
	private static final class MH_GdipGetGenericFontFamilySansSerif {
		static final MethodHandle MH = gdip("GdipGetGenericFontFamilySansSerif", P);
	}
	private static final class MH_GdipDeleteFontFamily {
		static final MethodHandle MH = gdip("GdipDeleteFontFamily", P);
	}
	private static final class MH_GdipGetFamilyName {
		static final MethodHandle MH = gdip("GdipGetFamilyName", P, P, JAVA_CHAR);
	}

	public static long PrivateFontCollection_new() {
		return create(out -> (int) MH_GdipNewPrivateFontCollection.MH.invokeExact(out));
	}

	public static void PrivateFontCollection_delete(long collection) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocateFrom(JAVA_LONG, collection);
			int ignored = (int) MH_GdipDeletePrivateFontCollection.MH.invokeExact(pointer.address());
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int PrivateFontCollection_AddFontFile(long collection, char[] filename) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment name = FFM.copyIn(arena, filename);
			int status = (int) MH_GdipPrivateAddFontFile.MH.invokeExact(collection, name.address());
			FFM.copyOut(name, filename);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** A font without a native object is still a non-zero handle, which Font_IsAvailable reports. */
	static long fontHandle(long font) {
		return font != 0 ? font : newProxy(0);
	}

	public static long Font_new(long hdc, long hfont) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_LONG);
			if (hfont != 0) {
				MemorySegment logFont = arena.allocate(LOGFONTA_SIZEOF);
				if ((int) MH_GetObjectA.MH.invokeExact(hfont, LOGFONTA_SIZEOF, logFont.address()) != 0) {
					int ignored = (int) MH_GdipCreateFontFromLogfontA.MH.invokeExact(hdc, logFont.address(), out.address());
				} else {
					int ignored = (int) MH_GdipCreateFontFromDC.MH.invokeExact(hdc, out.address());
				}
			} else {
				int ignored = (int) MH_GdipCreateFontFromDC.MH.invokeExact(hdc, out.address());
			}
			return fontHandle(out.get(JAVA_LONG, 0));
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long Font_new(long family, float emSize, int style, int unit) {
		long nativeFamily = nativeObject(family);
		return fontHandle(create(out -> (int) MH_GdipCreateFont.MH.invokeExact(nativeFamily, emSize, style, unit, out)));
	}

	/** Falls back to the generic sans serif family when the family or the font cannot be created. */
	public static long Font_new(char[] familyName, float emSize, int style, int unit, long fontCollection) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment name = FFM.copyIn(arena, familyName);
			MemorySegment familyOut = arena.allocate(JAVA_LONG);
			MemorySegment fontOut = arena.allocate(JAVA_LONG);
			int status = (int) MH_GdipCreateFontFamilyFromName.MH.invokeExact(name.address(), fontCollection, familyOut.address());
			long family = familyOut.get(JAVA_LONG, 0);
			try {
				long nativeFamily = family;
				if (status != Ok) {
					MemorySegment generic = arena.allocate(JAVA_LONG);
					status = (int) MH_GdipGetGenericFontFamilySansSerif.MH.invokeExact(generic.address());
					if (status != Ok) return fontHandle(0);
					nativeFamily = generic.get(JAVA_LONG, 0);
				}
				status = (int) MH_GdipCreateFont.MH.invokeExact(nativeFamily, emSize, style, unit, fontOut.address());
				if (status != Ok) {
					MemorySegment generic = arena.allocate(JAVA_LONG);
					status = (int) MH_GdipGetGenericFontFamilySansSerif.MH.invokeExact(generic.address());
					if (status != Ok) return fontHandle(fontOut.get(JAVA_LONG, 0));
					status = (int) MH_GdipCreateFont.MH.invokeExact(generic.get(JAVA_LONG, 0), emSize, style, unit, fontOut.address());
				}
				return fontHandle(fontOut.get(JAVA_LONG, 0));
			} finally {
				// the destructor of the local FontFamily
				int ignored = (int) MH_GdipDeleteFontFamily.MH.invokeExact(family);
				FFM.copyOut(name, familyName);
			}
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static void Font_delete(long font) {
		try {
			int ignored = (int) MH_GdipDeleteFont.MH.invokeExact(nativeObject(font));
			releaseProxy(font);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static boolean Font_IsAvailable(long font) {
		return nativeObject(font) != 0;
	}

	/**
	 * Fills an empty family from FontFamily_new(). A family that already has a native object cannot
	 * be refilled, since its handle is that object: the new family is released and NotImplemented returned.
	 */
	public static int Font_GetFamily(long font, long family) {
		if (family == 0) return InvalidParameter;
		boolean proxy = PROXIES.containsKey(family);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocateFrom(JAVA_LONG, proxy ? nativeObject(family) : 0);
			int status = (int) MH_GdipGetFamily.MH.invokeExact(nativeObject(font), out.address());
			long result = out.get(JAVA_LONG, 0);
			if (proxy) {
				PROXIES.put(family, result);
			} else if (result != 0) {
				int ignored = (int) MH_GdipDeleteFontFamily.MH.invokeExact(result);
				return NotImplemented;
			}
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static float Font_GetSize(long font) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_FLOAT);
			int ignored = (int) MH_GdipGetFontSize.MH.invokeExact(nativeObject(font), out.address());
			return out.get(JAVA_FLOAT, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Font_GetStyle(long font) {
		long nativeFont = nativeObject(font);
		return getInt(font, false, out -> (int) MH_GdipGetFontStyle.MH.invokeExact(nativeFont, out));
	}

	public static int Font_GetLogFontW(long font, long g, long logfontW) {
		try {
			return (int) MH_GdipGetLogFontW.MH.invokeExact(nativeObject(font), g, logfontW);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** An empty family, which Font_GetFamily fills. */
	public static long FontFamily_new() {
		return newProxy(0);
	}

	public static long FontFamily_new(char[] name, long fontCollection) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment familyName = FFM.copyIn(arena, name);
			long family = create(out -> (int) MH_GdipCreateFontFamilyFromName.MH.invokeExact(familyName.address(), fontCollection, out));
			FFM.copyOut(familyName, name);
			return family;
		}
	}

	public static void FontFamily_delete(long family) {
		try {
			int ignored = (int) MH_GdipDeleteFontFamily.MH.invokeExact(nativeObject(family));
			releaseProxy(family);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** GDI+ writes up to LF_FACESIZE characters, however short the array. */
	public static int FontFamily_GetFamilyName(long family, char[] name, char language) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment buffer = MemorySegment.NULL;
			if (name != null) {
				buffer = arena.allocate(JAVA_CHAR, Math.max(name.length, 32));
				MemorySegment.copy(name, 0, buffer, JAVA_CHAR, 0, name.length);
			}
			int status = (int) MH_GdipGetFamilyName.MH.invokeExact(nativeObject(family), buffer.address(), language);
			FFM.copyOut(buffer, name);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static boolean FontFamily_IsAvailable(long family) {
		return nativeObject(family) != 0;
	}

	/* GraphicsPath */

	private static final class MH_GdipCreatePath {
		static final MethodHandle MH = gdip("GdipCreatePath", I, P);
	}
	private static final class MH_GdipCreatePath2I {
		static final MethodHandle MH = gdip("GdipCreatePath2I", P, P, I, I, P);
	}
	private static final class MH_GdipDeletePath {
		static final MethodHandle MH = gdip("GdipDeletePath", P);
	}
	private static final class MH_GdipAddPathArc {
		static final MethodHandle MH = gdip("GdipAddPathArc", P, F, F, F, F, F, F);
	}
	private static final class MH_GdipAddPathBezier {
		static final MethodHandle MH = gdip("GdipAddPathBezier", P, F, F, F, F, F, F, F, F);
	}
	private static final class MH_GdipAddPathLine {
		static final MethodHandle MH = gdip("GdipAddPathLine", P, F, F, F, F);
	}
	private static final class MH_GdipAddPathPath {
		static final MethodHandle MH = gdip("GdipAddPathPath", P, P, I);
	}
	private static final class MH_GdipAddPathRectangle {
		static final MethodHandle MH = gdip("GdipAddPathRectangle", P, F, F, F, F);
	}
	private static final class MH_GdipAddPathString {
		static final MethodHandle MH = gdip("GdipAddPathString", P, P, I, P, I, F, P, P);
	}
	private static final class MH_GdipClosePathFigure {
		static final MethodHandle MH = gdip("GdipClosePathFigure", P);
	}
	private static final class MH_GdipClonePath {
		static final MethodHandle MH = gdip("GdipClonePath", P, P);
	}
	private static final class MH_GdipFlattenPath {
		static final MethodHandle MH = gdip("GdipFlattenPath", P, P, F);
	}
	private static final class MH_GdipGetPathWorldBounds {
		static final MethodHandle MH = gdip("GdipGetPathWorldBounds", P, P, P, P);
	}
	private static final class MH_GdipGetPathLastPoint {
		static final MethodHandle MH = gdip("GdipGetPathLastPoint", P, P);
	}
	private static final class MH_GdipGetPathPoints {
		static final MethodHandle MH = gdip("GdipGetPathPoints", P, P, I);
	}
	private static final class MH_GdipGetPathTypes {
		static final MethodHandle MH = gdip("GdipGetPathTypes", P, P, I);
	}
	private static final class MH_GdipGetPointCount {
		static final MethodHandle MH = gdip("GdipGetPointCount", P, P);
	}
	private static final class MH_GdipIsOutlineVisiblePathPoint {
		static final MethodHandle MH = gdip("GdipIsOutlineVisiblePathPoint", P, F, F, P, P, P);
	}
	private static final class MH_GdipIsVisiblePathPoint {
		static final MethodHandle MH = gdip("GdipIsVisiblePathPoint", P, F, F, P, P);
	}
	private static final class MH_GdipSetPathFillMode {
		static final MethodHandle MH = gdip("GdipSetPathFillMode", P, I);
	}
	private static final class MH_GdipStartPathFigure {
		static final MethodHandle MH = gdip("GdipStartPathFigure", P);
	}
	private static final class MH_GdipTransformPath {
		static final MethodHandle MH = gdip("GdipTransformPath", P, P);
	}

	public static long GraphicsPath_new(int fillMode) {
		return create(out -> (int) MH_GdipCreatePath.MH.invokeExact(fillMode, out));
	}

	public static long GraphicsPath_new(int[] points, byte[] types, int count, int fillMode) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment p = FFM.copyIn(arena, points);
			MemorySegment t = FFM.copyIn(arena, types);
			return create(out -> (int) MH_GdipCreatePath2I.MH.invokeExact(p.address(), t.address(), count, fillMode, out));
		}
	}

	public static void GraphicsPath_delete(long path) {
		try {
			int ignored = (int) MH_GdipDeletePath.MH.invokeExact(path);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_AddArc(long path, float x, float y, float width, float height, float startAngle, float sweepAngle) {
		try {
			return (int) MH_GdipAddPathArc.MH.invokeExact(path, x, y, width, height, startAngle, sweepAngle);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_AddBezier(long path, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
		try {
			return (int) MH_GdipAddPathBezier.MH.invokeExact(path, x1, y1, x2, y2, x3, y3, x4, y4);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_AddLine(long path, float x1, float y1, float x2, float y2) {
		try {
			return (int) MH_GdipAddPathLine.MH.invokeExact(path, x1, y1, x2, y2);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_AddPath(long path, long addingPath, boolean connect) {
		try {
			return (int) MH_GdipAddPathPath.MH.invokeExact(path, addingPath, connect ? 1 : 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_AddRectangle(long path, RectF rect) {
		try {
			return (int) MH_GdipAddPathRectangle.MH.invokeExact(path, rect.X, rect.Y, rect.Width, rect.Height);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Lays the string out in an empty rectangle at <code>origin</code>. */
	public static int GraphicsPath_AddString(long path, char[] string, int length, long family, int style, float emSize, PointF origin, long format) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment text = FFM.copyIn(arena, string);
			MemorySegment layout = arena.allocate(JAVA_FLOAT, 4);
			layout.set(JAVA_FLOAT, 0, origin.X);
			layout.set(JAVA_FLOAT, 4, origin.Y);
			int status = (int) MH_GdipAddPathString.MH.invokeExact(path, text.address(), length, nativeObject(family), style, emSize, layout.address(), format);
			FFM.copyOut(text, string);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_CloseFigure(long path) {
		try {
			return (int) MH_GdipClosePathFigure.MH.invokeExact(path);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long GraphicsPath_Clone(long path) {
		return create(out -> (int) MH_GdipClonePath.MH.invokeExact(path, out));
	}

	public static int GraphicsPath_Flatten(long path, long matrix, float flatness) {
		try {
			return (int) MH_GdipFlattenPath.MH.invokeExact(path, matrix, flatness);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_GetBounds(long path, RectF bounds, long matrix, long pen) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment rect = rectF(arena, bounds);
			int status = (int) MH_GdipGetPathWorldBounds.MH.invokeExact(path, rect.address(), matrix, pen);
			rectF(rect, bounds);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_GetLastPoint(long path, PointF lastPoint) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment point = pointF(arena, lastPoint, 1);
			int status = (int) MH_GdipGetPathLastPoint.MH.invokeExact(path, point.address());
			pointF(point, lastPoint);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_GetPathPoints(long path, float[] points, int count) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment buffer = floats(arena, points, 2L * Math.max(count, 0));
			int status = (int) MH_GdipGetPathPoints.MH.invokeExact(path, buffer.address(), count);
			FFM.copyOut(buffer, points);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_GetPathTypes(long path, byte[] types, int count) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment buffer = MemorySegment.NULL;
			if (types != null) {
				buffer = arena.allocate(Math.max(types.length, Math.max(count, 0)));
				MemorySegment.copy(types, 0, buffer, JAVA_BYTE, 0, types.length);
			}
			int status = (int) MH_GdipGetPathTypes.MH.invokeExact(path, buffer.address(), count);
			FFM.copyOut(buffer, types);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_GetPointCount(long path) {
		return getInt(path, false, out -> (int) MH_GdipGetPointCount.MH.invokeExact(path, out));
	}

	public static boolean GraphicsPath_IsOutlineVisible(long path, float x, float y, long pen, long g) {
		return bool(getInt(path, false, out -> (int) MH_GdipIsOutlineVisiblePathPoint.MH.invokeExact(path, x, y, pen, g, out)));
	}

	public static boolean GraphicsPath_IsVisible(long path, float x, float y, long g) {
		return bool(getInt(path, false, out -> (int) MH_GdipIsVisiblePathPoint.MH.invokeExact(path, x, y, g, out)));
	}

	public static int GraphicsPath_SetFillMode(long path, int fillmode) {
		try {
			return (int) MH_GdipSetPathFillMode.MH.invokeExact(path, fillmode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int GraphicsPath_StartFigure(long path) {
		try {
			return (int) MH_GdipStartPathFigure.MH.invokeExact(path);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** A null matrix is Ok without calling GDI+, as the C++ wrapper does. */
	public static int GraphicsPath_Transform(long path, long matrix) {
		if (matrix == 0) return Ok;
		try {
			return (int) MH_GdipTransformPath.MH.invokeExact(path, matrix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* Matrix */

	private static final class MH_GdipCreateMatrix2 {
		static final MethodHandle MH = gdip("GdipCreateMatrix2", F, F, F, F, F, F, P);
	}
	private static final class MH_GdipDeleteMatrix {
		static final MethodHandle MH = gdip("GdipDeleteMatrix", P);
	}
	private static final class MH_GdipGetMatrixElements {
		static final MethodHandle MH = gdip("GdipGetMatrixElements", P, P);
	}
	private static final class MH_GdipInvertMatrix {
		static final MethodHandle MH = gdip("GdipInvertMatrix", P);
	}
	private static final class MH_GdipIsMatrixIdentity {
		static final MethodHandle MH = gdip("GdipIsMatrixIdentity", P, P);
	}
	private static final class MH_GdipMultiplyMatrix {
		static final MethodHandle MH = gdip("GdipMultiplyMatrix", P, P, I);
	}
	private static final class MH_GdipRotateMatrix {
		static final MethodHandle MH = gdip("GdipRotateMatrix", P, F, I);
	}
	private static final class MH_GdipScaleMatrix {
		static final MethodHandle MH = gdip("GdipScaleMatrix", P, F, F, I);
	}
	private static final class MH_GdipShearMatrix {
		static final MethodHandle MH = gdip("GdipShearMatrix", P, F, F, I);
	}
	private static final class MH_GdipTransformMatrixPoints {
		static final MethodHandle MH = gdip("GdipTransformMatrixPoints", P, P, I);
	}
	private static final class MH_GdipVectorTransformMatrixPoints {
		static final MethodHandle MH = gdip("GdipVectorTransformMatrixPoints", P, P, I);
	}
	private static final class MH_GdipTranslateMatrix {
		static final MethodHandle MH = gdip("GdipTranslateMatrix", P, F, F, I);
	}
	private static final class MH_GdipSetMatrixElements {
		static final MethodHandle MH = gdip("GdipSetMatrixElements", P, F, F, F, F, F, F);
	}

	public static long Matrix_new(float m11, float m12, float m21, float m22, float dx, float dy) {
		return create(out -> (int) MH_GdipCreateMatrix2.MH.invokeExact(m11, m12, m21, m22, dx, dy, out));
	}

	public static void Matrix_delete(long matrix) {
		try {
			int ignored = (int) MH_GdipDeleteMatrix.MH.invokeExact(matrix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_GetElements(long matrix, float[] m) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment elements = floats(arena, m, 6);
			int status = (int) MH_GdipGetMatrixElements.MH.invokeExact(matrix, elements.address());
			FFM.copyOut(elements, m);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_Invert(long matrix) {
		try {
			return (int) MH_GdipInvertMatrix.MH.invokeExact(matrix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static boolean Matrix_IsIdentity(long matrix) {
		return bool(getInt(matrix, false, out -> (int) MH_GdipIsMatrixIdentity.MH.invokeExact(matrix, out)));
	}

	public static int Matrix_Multiply(long matrix, long matrix1, int order) {
		try {
			return (int) MH_GdipMultiplyMatrix.MH.invokeExact(matrix, matrix1, order);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_Rotate(long matrix, float angle, int order) {
		try {
			return (int) MH_GdipRotateMatrix.MH.invokeExact(matrix, angle, order);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_Scale(long matrix, float scaleX, float scaleY, int order) {
		try {
			return (int) MH_GdipScaleMatrix.MH.invokeExact(matrix, scaleX, scaleY, order);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_Shear(long matrix, float shearX, float shearY, int order) {
		try {
			return (int) MH_GdipShearMatrix.MH.invokeExact(matrix, shearX, shearY, order);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** One PointF, as the JNI glue copies; a larger count reads beyond it, into zeroes here. */
	public static int Matrix_TransformPoints(long matrix, PointF pts, int count) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment points = pointF(arena, pts, count);
			int status = (int) MH_GdipTransformMatrixPoints.MH.invokeExact(matrix, points.address(), count);
			pointF(points, pts);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_TransformPoints(long matrix, float[] pts, int count) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment points = floats(arena, pts, 2L * Math.max(count, 0));
			int status = (int) MH_GdipTransformMatrixPoints.MH.invokeExact(matrix, points.address(), count);
			FFM.copyOut(points, pts);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_TransformVectors(long matrix, PointF pts, int count) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment points = pointF(arena, pts, count);
			int status = (int) MH_GdipVectorTransformMatrixPoints.MH.invokeExact(matrix, points.address(), count);
			pointF(points, pts);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_Translate(long matrix, float offsetX, float offsetY, int order) {
		try {
			return (int) MH_GdipTranslateMatrix.MH.invokeExact(matrix, offsetX, offsetY, order);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Matrix_SetElements(long matrix, float m11, float m12, float m21, float m22, float dx, float dy) {
		try {
			return (int) MH_GdipSetMatrixElements.MH.invokeExact(matrix, m11, m12, m21, m22, dx, dy);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* Region */

	private static final class MH_GdipCreateRegion {
		static final MethodHandle MH = gdip("GdipCreateRegion", P);
	}
	private static final class MH_GdipCreateRegionHrgn {
		static final MethodHandle MH = gdip("GdipCreateRegionHrgn", P, P);
	}
	private static final class MH_GdipCreateRegionPath {
		static final MethodHandle MH = gdip("GdipCreateRegionPath", P, P);
	}
	private static final class MH_GdipDeleteRegion {
		static final MethodHandle MH = gdip("GdipDeleteRegion", P);
	}
	private static final class MH_GdipGetRegionHRgn {
		static final MethodHandle MH = gdip("GdipGetRegionHRgn", P, P, P);
	}
	private static final class MH_GdipIsInfiniteRegion {
		static final MethodHandle MH = gdip("GdipIsInfiniteRegion", P, P, P);
	}

	public static long Region_new() {
		return create(out -> (int) MH_GdipCreateRegion.MH.invokeExact(out));
	}

	public static long Region_new(long hRgn) {
		return create(out -> (int) MH_GdipCreateRegionHrgn.MH.invokeExact(hRgn, out));
	}

	public static long Region_newGraphicsPath(long path) {
		return create(out -> (int) MH_GdipCreateRegionPath.MH.invokeExact(path, out));
	}

	public static void Region_delete(long region) {
		try {
			int ignored = (int) MH_GdipDeleteRegion.MH.invokeExact(region);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long Region_GetHRGN(long region, long graphics) {
		return create(out -> (int) MH_GdipGetRegionHRgn.MH.invokeExact(region, graphics, out));
	}

	public static boolean Region_IsInfinite(long region, long graphics) {
		return bool(getInt(region, false, out -> (int) MH_GdipIsInfiniteRegion.MH.invokeExact(region, graphics, out)));
	}

	/* StringFormat */

	private static final class MH_GdipDeleteStringFormat {
		static final MethodHandle MH = gdip("GdipDeleteStringFormat", P);
	}
	private static final class MH_GdipCloneStringFormat {
		static final MethodHandle MH = gdip("GdipCloneStringFormat", P, P);
	}
	private static final class MH_GdipStringFormatGetGenericDefault {
		static final MethodHandle MH = gdip("GdipStringFormatGetGenericDefault", P);
	}
	private static final class MH_GdipStringFormatGetGenericTypographic {
		static final MethodHandle MH = gdip("GdipStringFormatGetGenericTypographic", P);
	}
	private static final class MH_GdipGetStringFormatFlags {
		static final MethodHandle MH = gdip("GdipGetStringFormatFlags", P, P);
	}
	private static final class MH_GdipSetStringFormatHotkeyPrefix {
		static final MethodHandle MH = gdip("GdipSetStringFormatHotkeyPrefix", P, I);
	}
	private static final class MH_GdipSetStringFormatFlags {
		static final MethodHandle MH = gdip("GdipSetStringFormatFlags", P, I);
	}
	private static final class MH_GdipSetStringFormatTabStops {
		static final MethodHandle MH = gdip("GdipSetStringFormatTabStops", P, F, I, P);
	}

	public static void StringFormat_delete(long format) {
		try {
			int ignored = (int) MH_GdipDeleteStringFormat.MH.invokeExact(format);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** 0 when cloning fails, as the C++ wrapper returns NULL then. */
	public static long StringFormat_Clone(long format) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(JAVA_LONG);
			int status = (int) MH_GdipCloneStringFormat.MH.invokeExact(format, out.address());
			return status == Ok ? out.get(JAVA_LONG, 0) : 0;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** The shared generic format of GDI+, which must not be deleted. */
	public static long StringFormat_GenericDefault() {
		return create(out -> (int) MH_GdipStringFormatGetGenericDefault.MH.invokeExact(out));
	}

	/** The shared generic format of GDI+, which must not be deleted. */
	public static long StringFormat_GenericTypographic() {
		return create(out -> (int) MH_GdipStringFormatGetGenericTypographic.MH.invokeExact(out));
	}

	public static int StringFormat_GetFormatFlags(long format) {
		return getInt(format, false, out -> (int) MH_GdipGetStringFormatFlags.MH.invokeExact(format, out));
	}

	public static int StringFormat_SetHotkeyPrefix(long format, int hotkeyPrefix) {
		try {
			return (int) MH_GdipSetStringFormatHotkeyPrefix.MH.invokeExact(format, hotkeyPrefix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int StringFormat_SetFormatFlags(long format, int flags) {
		try {
			return (int) MH_GdipSetStringFormatFlags.MH.invokeExact(format, flags);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int StringFormat_SetTabStops(long format, float firstTabOffset, int count, float[] tabStops) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment stops = floats(arena, tabStops, count);
			int status = (int) MH_GdipSetStringFormatTabStops.MH.invokeExact(format, firstTabOffset, count, stops.address());
			FFM.copyOut(stops, tabStops);
			return status;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}
}
