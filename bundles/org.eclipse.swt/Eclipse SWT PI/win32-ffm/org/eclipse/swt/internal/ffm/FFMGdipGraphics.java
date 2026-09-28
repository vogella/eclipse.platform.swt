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

import org.eclipse.swt.internal.gdip.*;

/**
 * The <code>Graphics_*</code>, <code>GdiplusStartup</code> and <code>GdiplusShutdown</code> natives of
 * {@link Gdip} on the flat API of gdiplus.dll, doing what the inline C++ wrapper of gdiplusgraphics.h does.
 * <p>
 * Every GDI+ object handle is the flat API pointer (<code>GpGraphics*</code>, <code>GpPen*</code>, ...),
 * not a C++ wrapper object. Differences to the C++ wrapper: <code>Graphics_new</code> returns 0 when
 * <code>GdipCreateFromHDC</code> fails, where <code>new Graphics</code> returned an object without a
 * native graphics, and getters that leave their result uninitialized in C++ return 0 on failure.
 * The wrapper's <code>lastResult</code> is not kept, since no native reads it.
 * </p>
 */
public final class FFMGdipGraphics {

	static final SymbolLookup GDIPLUS = FFM.library("gdiplus.dll");

	/* SmoothingModeInvalid and InterpolationModeInvalid, the C++ getters' result on failure */
	static final int QualityModeInvalid = -1;
	static final int CombineModeReplace = 0;

	/* Struct layouts, x64 and aarch64 */
	static final long PointF_X = 0, PointF_Y = 4;
	static final long Rect_X = 0, Rect_Y = 4, Rect_Width = 8, Rect_Height = 12, Rect_sizeof = 16;
	static final long GdiplusStartupInput_sizeof = 24;

	private FFMGdipGraphics() {
	}

	static MethodHandle link(String name, FunctionDescriptor descriptor) {
		return FFM.downcall(GDIPLUS, name, descriptor);
	}

	static FunctionDescriptor status(MemoryLayout... arguments) {
		return FunctionDescriptor.of(JAVA_INT, arguments);
	}

	/* ---------------------------------------------------------------- struct helpers */

	static MemorySegment rect(Arena arena, Rect rect) {
		MemorySegment s = arena.allocate(Rect_sizeof, 4);
		if (rect != null) {
			s.set(JAVA_INT, Rect_X, rect.X);
			s.set(JAVA_INT, Rect_Y, rect.Y);
			s.set(JAVA_INT, Rect_Width, rect.Width);
			s.set(JAVA_INT, Rect_Height, rect.Height);
		}
		return s;
	}

	static void rect(MemorySegment s, Rect rect) {
		rect.X = s.get(JAVA_INT, Rect_X);
		rect.Y = s.get(JAVA_INT, Rect_Y);
		rect.Width = s.get(JAVA_INT, Rect_Width);
		rect.Height = s.get(JAVA_INT, Rect_Height);
	}

	static MemorySegment rectF(Arena arena, RectF rect) {
		MemorySegment s = arena.allocate(Rect_sizeof, 4);
		if (rect != null) {
			s.set(JAVA_FLOAT, Rect_X, rect.X);
			s.set(JAVA_FLOAT, Rect_Y, rect.Y);
			s.set(JAVA_FLOAT, Rect_Width, rect.Width);
			s.set(JAVA_FLOAT, Rect_Height, rect.Height);
		}
		return s;
	}

	static void rectF(MemorySegment s, RectF rect) {
		rect.X = s.get(JAVA_FLOAT, Rect_X);
		rect.Y = s.get(JAVA_FLOAT, Rect_Y);
		rect.Width = s.get(JAVA_FLOAT, Rect_Width);
		rect.Height = s.get(JAVA_FLOAT, Rect_Height);
	}

	/** The <code>RectF(origin.X, origin.Y, 0, 0)</code> layout rectangle the C++ wrapper builds from an origin. */
	static MemorySegment layoutRect(Arena arena, PointF origin) {
		MemorySegment s = arena.allocate(Rect_sizeof, 4);
		s.set(JAVA_FLOAT, Rect_X, origin.X);
		s.set(JAVA_FLOAT, Rect_Y, origin.Y);
		return s;
	}

	static MemorySegment pointF(Arena arena, PointF point) {
		if (point == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(8, 4);
		s.set(JAVA_FLOAT, PointF_X, point.X);
		s.set(JAVA_FLOAT, PointF_Y, point.Y);
		return s;
	}

	/* ---------------------------------------------------------------- startup */

	private static final class MH_GdiplusStartup {
		static final MethodHandle MH = link("GdiplusStartup", status(ADDRESS, ADDRESS, JAVA_LONG));
	}
	public static int GdiplusStartup(long[] token, GdiplusStartupInput input, long output) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lptoken = FFM.copyIn(arena, token);
			MemorySegment lpinput = MemorySegment.NULL;
			if (input != null) {
				lpinput = arena.allocate(GdiplusStartupInput_sizeof, 8);
				lpinput.set(JAVA_INT, 0, input.GdiplusVersion);
				lpinput.set(JAVA_LONG, 8, input.DebugEventCallback);
				lpinput.set(JAVA_INT, 16, input.SuppressBackgroundThread ? 1 : 0);
				lpinput.set(JAVA_INT, 20, input.SuppressExternalCodecs ? 1 : 0);
			}
			int rc = (int) MH_GdiplusStartup.MH.invokeExact(lptoken, lpinput, output);
			if (input != null) {
				// JNI SetBooleanField keeps the lowest bit
				input.GdiplusVersion = lpinput.get(JAVA_INT, 0);
				input.DebugEventCallback = lpinput.get(JAVA_LONG, 8);
				input.SuppressBackgroundThread = (lpinput.get(JAVA_INT, 16) & 1) != 0;
				input.SuppressExternalCodecs = (lpinput.get(JAVA_INT, 20) & 1) != 0;
			}
			FFM.copyOut(lptoken, token);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdiplusShutdown {
		static final MethodHandle MH = link("GdiplusShutdown", FunctionDescriptor.ofVoid(JAVA_LONG));
	}
	public static void GdiplusShutdown(long token) {
		try {
			MH_GdiplusShutdown.MH.invokeExact(token);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- life cycle */

	private static final class MH_GdipCreateFromHDC {
		static final MethodHandle MH = link("GdipCreateFromHDC", status(JAVA_LONG, ADDRESS));
	}
	/** <code>new Graphics(hdc)</code>, returning the <code>GpGraphics*</code> or 0 on failure. */
	public static long Graphics_new(long hdc) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment graphics = arena.allocate(JAVA_LONG);
			int status = (int) MH_GdipCreateFromHDC.MH.invokeExact(hdc, graphics);
			return status == 0 ? graphics.get(JAVA_LONG, 0) : 0;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDeleteGraphics {
		static final MethodHandle MH = link("GdipDeleteGraphics", status(JAVA_LONG));
	}
	public static void Graphics_delete(long graphics) {
		if (graphics == 0) return;
		try {
			int status = (int) MH_GdipDeleteGraphics.MH.invokeExact(graphics);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipFlush {
		static final MethodHandle MH = link("GdipFlush", status(JAVA_LONG, JAVA_INT));
	}
	public static void Graphics_Flush(long graphics, int intention) {
		try {
			int status = (int) MH_GdipFlush.MH.invokeExact(graphics, intention);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- GDI interop */

	private static final class MH_GdipGetDC {
		static final MethodHandle MH = link("GdipGetDC", status(JAVA_LONG, ADDRESS));
	}
	public static long Graphics_GetHDC(long graphics) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment hdc = arena.allocate(JAVA_LONG);
			int status = (int) MH_GdipGetDC.MH.invokeExact(graphics, hdc);
			return hdc.get(JAVA_LONG, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipReleaseDC {
		static final MethodHandle MH = link("GdipReleaseDC", status(JAVA_LONG, JAVA_LONG));
	}
	public static void Graphics_ReleaseHDC(long graphics, long hdc) {
		try {
			int status = (int) MH_GdipReleaseDC.MH.invokeExact(graphics, hdc);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- rendering modes */

	/** Calls a getter that fills an enum, returning <code>initial</code> if it fails to set it. */
	static int getInt(MethodHandle getter, long graphics, int initial) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment value = arena.allocateFrom(JAVA_INT, initial);
			int status = (int) getter.invokeExact(graphics, value);
			return value.get(JAVA_INT, 0);
		}
	}

	private static final class MH_GdipSetCompositingQuality {
		static final MethodHandle MH = link("GdipSetCompositingQuality", status(JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_SetCompositingQuality(long graphics, int compositingQuality) {
		try {
			return (int) MH_GdipSetCompositingQuality.MH.invokeExact(graphics, compositingQuality);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipGetInterpolationMode {
		static final MethodHandle MH = link("GdipGetInterpolationMode", status(JAVA_LONG, ADDRESS));
	}
	public static int Graphics_GetInterpolationMode(long graphics) {
		try {
			return getInt(MH_GdipGetInterpolationMode.MH, graphics, QualityModeInvalid);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetInterpolationMode {
		static final MethodHandle MH = link("GdipSetInterpolationMode", status(JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_SetInterpolationMode(long graphics, int mode) {
		try {
			return (int) MH_GdipSetInterpolationMode.MH.invokeExact(graphics, mode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipGetSmoothingMode {
		static final MethodHandle MH = link("GdipGetSmoothingMode", status(JAVA_LONG, ADDRESS));
	}
	public static int Graphics_GetSmoothingMode(long graphics) {
		try {
			return getInt(MH_GdipGetSmoothingMode.MH, graphics, QualityModeInvalid);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetSmoothingMode {
		static final MethodHandle MH = link("GdipSetSmoothingMode", status(JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_SetSmoothingMode(long graphics, int smoothingMode) {
		try {
			return (int) MH_GdipSetSmoothingMode.MH.invokeExact(graphics, smoothingMode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetPixelOffsetMode {
		static final MethodHandle MH = link("GdipSetPixelOffsetMode", status(JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_SetPixelOffsetMode(long graphics, int pixelOffsetMode) {
		try {
			return (int) MH_GdipSetPixelOffsetMode.MH.invokeExact(graphics, pixelOffsetMode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipGetTextRenderingHint {
		static final MethodHandle MH = link("GdipGetTextRenderingHint", status(JAVA_LONG, ADDRESS));
	}
	public static int Graphics_GetTextRenderingHint(long graphics) {
		try {
			return getInt(MH_GdipGetTextRenderingHint.MH, graphics, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetTextRenderingHint {
		static final MethodHandle MH = link("GdipSetTextRenderingHint", status(JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_SetTextRenderingHint(long graphics, int mode) {
		try {
			return (int) MH_GdipSetTextRenderingHint.MH.invokeExact(graphics, mode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetPageUnit {
		static final MethodHandle MH = link("GdipSetPageUnit", status(JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_SetPageUnit(long graphics, int unit) {
		try {
			return (int) MH_GdipSetPageUnit.MH.invokeExact(graphics, unit);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- transform */

	private static final class MH_GdipGetWorldTransform {
		static final MethodHandle MH = link("GdipGetWorldTransform", status(JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_GetTransform(long graphics, long matrix) {
		try {
			return (int) MH_GdipGetWorldTransform.MH.invokeExact(graphics, matrix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetWorldTransform {
		static final MethodHandle MH = link("GdipSetWorldTransform", status(JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_SetTransform(long graphics, long matrix) {
		try {
			return (int) MH_GdipSetWorldTransform.MH.invokeExact(graphics, matrix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipScaleWorldTransform {
		static final MethodHandle MH = link("GdipScaleWorldTransform", status(JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT));
	}
	public static int Graphics_ScaleTransform(long graphics, float sx, float sy, int order) {
		try {
			return (int) MH_GdipScaleWorldTransform.MH.invokeExact(graphics, sx, sy, order);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipTranslateWorldTransform {
		static final MethodHandle MH = link("GdipTranslateWorldTransform", status(JAVA_LONG, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT));
	}
	public static int Graphics_TranslateTransform(long graphics, float dx, float dy, int order) {
		try {
			return (int) MH_GdipTranslateWorldTransform.MH.invokeExact(graphics, dx, dy, order);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- state */

	private static final class MH_GdipSaveGraphics {
		static final MethodHandle MH = link("GdipSaveGraphics", status(JAVA_LONG, ADDRESS));
	}
	public static int Graphics_Save(long graphics) {
		try {
			return getInt(MH_GdipSaveGraphics.MH, graphics, 0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipRestoreGraphics {
		static final MethodHandle MH = link("GdipRestoreGraphics", status(JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_Restore(long graphics, int gstate) {
		try {
			return (int) MH_GdipRestoreGraphics.MH.invokeExact(graphics, gstate);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- clipping */

	private static final class MH_GdipSetClipRegion {
		static final MethodHandle MH = link("GdipSetClipRegion", status(JAVA_LONG, JAVA_LONG, JAVA_INT));
	}
	/** <code>SetClip(Region*, CombineMode)</code>: <code>hrgn</code> is a GDI+ region, not an HRGN. */
	public static int Graphics_SetClip(long graphics, long hrgn, int combineMode) {
		try {
			return (int) MH_GdipSetClipRegion.MH.invokeExact(graphics, hrgn, combineMode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetClipRectI {
		static final MethodHandle MH = link("GdipSetClipRectI", status(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_SetClip(long graphics, Rect rect, int combineMode) {
		try {
			return (int) MH_GdipSetClipRectI.MH.invokeExact(graphics, rect.X, rect.Y, rect.Width, rect.Height, combineMode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipSetClipPath {
		static final MethodHandle MH = link("GdipSetClipPath", status(JAVA_LONG, JAVA_LONG, JAVA_INT));
	}
	public static int Graphics_SetClipPath(long graphics, long path) {
		return Graphics_SetClipPath(graphics, path, CombineModeReplace);
	}

	public static int Graphics_SetClipPath(long graphics, long path, int combineMode) {
		try {
			return (int) MH_GdipSetClipPath.MH.invokeExact(graphics, path, combineMode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipResetClip {
		static final MethodHandle MH = link("GdipResetClip", status(JAVA_LONG));
	}
	public static int Graphics_ResetClip(long graphics) {
		try {
			return (int) MH_GdipResetClip.MH.invokeExact(graphics);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipGetClip {
		static final MethodHandle MH = link("GdipGetClip", status(JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_GetClip(long graphics, long region) {
		try {
			return (int) MH_GdipGetClip.MH.invokeExact(graphics, region);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipGetClipBounds {
		static final MethodHandle MH = link("GdipGetClipBounds", status(JAVA_LONG, ADDRESS));
	}
	public static int Graphics_GetClipBounds(long graphics, RectF rect) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lprect = rect == null ? MemorySegment.NULL : rectF(arena, rect);
			int rc = (int) MH_GdipGetClipBounds.MH.invokeExact(graphics, lprect);
			if (rect != null) rectF(lprect, rect);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipGetClipBoundsI {
		static final MethodHandle MH = link("GdipGetClipBoundsI", status(JAVA_LONG, ADDRESS));
	}
	public static int Graphics_GetClipBounds(long graphics, Rect rect) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lprect = rect == null ? MemorySegment.NULL : rect(arena, rect);
			int rc = (int) MH_GdipGetClipBoundsI.MH.invokeExact(graphics, lprect);
			if (rect != null) rect(lprect, rect);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipGetVisibleClipBoundsI {
		static final MethodHandle MH = link("GdipGetVisibleClipBoundsI", status(JAVA_LONG, ADDRESS));
	}
	public static int Graphics_GetVisibleClipBounds(long graphics, Rect rect) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lprect = rect == null ? MemorySegment.NULL : rect(arena, rect);
			int rc = (int) MH_GdipGetVisibleClipBoundsI.MH.invokeExact(graphics, lprect);
			if (rect != null) rect(lprect, rect);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- lines and shapes */

	private static final class MH_GdipDrawLineI {
		static final MethodHandle MH = link("GdipDrawLineI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_DrawLine(long graphics, long pen, int x1, int y1, int x2, int y2) {
		try {
			return (int) MH_GdipDrawLineI.MH.invokeExact(graphics, pen, x1, y1, x2, y2);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawLinesI {
		static final MethodHandle MH = link("GdipDrawLinesI", status(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT));
	}
	public static int Graphics_DrawLines(long graphics, long pen, int[] points, int count) {
		try (Arena arena = Arena.ofConfined()) {
			return (int) MH_GdipDrawLinesI.MH.invokeExact(graphics, pen, FFM.copyIn(arena, points), count);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawArcI {
		static final MethodHandle MH = link("GdipDrawArcI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_FLOAT, JAVA_FLOAT));
	}
	public static int Graphics_DrawArc(long graphics, long pen, int x, int y, int width, int height, float startAngle, float sweepAngle) {
		try {
			return (int) MH_GdipDrawArcI.MH.invokeExact(graphics, pen, x, y, width, height, startAngle, sweepAngle);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawRectangleI {
		static final MethodHandle MH = link("GdipDrawRectangleI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_DrawRectangle(long graphics, long pen, int x, int y, int width, int height) {
		try {
			return (int) MH_GdipDrawRectangleI.MH.invokeExact(graphics, pen, x, y, width, height);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawEllipseI {
		static final MethodHandle MH = link("GdipDrawEllipseI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_DrawEllipse(long graphics, long pen, int x, int y, int width, int height) {
		try {
			return (int) MH_GdipDrawEllipseI.MH.invokeExact(graphics, pen, x, y, width, height);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawPolygonI {
		static final MethodHandle MH = link("GdipDrawPolygonI", status(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT));
	}
	public static int Graphics_DrawPolygon(long graphics, long pen, int[] points, int count) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lppoints = FFM.copyIn(arena, points);
			int rc = (int) MH_GdipDrawPolygonI.MH.invokeExact(graphics, pen, lppoints, count);
			FFM.copyOut(lppoints, points);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawPath {
		static final MethodHandle MH = link("GdipDrawPath", status(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_DrawPath(long graphics, long pen, long path) {
		try {
			return (int) MH_GdipDrawPath.MH.invokeExact(graphics, pen, path);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipFillRectangleI {
		static final MethodHandle MH = link("GdipFillRectangleI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_FillRectangle(long graphics, long brush, int x, int y, int width, int height) {
		try {
			return (int) MH_GdipFillRectangleI.MH.invokeExact(graphics, brush, x, y, width, height);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipFillEllipseI {
		static final MethodHandle MH = link("GdipFillEllipseI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_FillEllipse(long graphics, long brush, int x, int y, int width, int height) {
		try {
			return (int) MH_GdipFillEllipseI.MH.invokeExact(graphics, brush, x, y, width, height);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipFillPieI {
		static final MethodHandle MH = link("GdipFillPieI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_FLOAT, JAVA_FLOAT));
	}
	public static int Graphics_FillPie(long graphics, long brush, int x, int y, int width, int height, float startAngle, float sweepAngle) {
		try {
			return (int) MH_GdipFillPieI.MH.invokeExact(graphics, brush, x, y, width, height, startAngle, sweepAngle);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipFillPolygonI {
		static final MethodHandle MH = link("GdipFillPolygonI", status(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_FillPolygon(long graphics, long brush, int[] points, int count, int fillMode) {
		try (Arena arena = Arena.ofConfined()) {
			return (int) MH_GdipFillPolygonI.MH.invokeExact(graphics, brush, FFM.copyIn(arena, points), count, fillMode);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipFillPath {
		static final MethodHandle MH = link("GdipFillPath", status(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_FillPath(long graphics, long brush, long path) {
		try {
			return (int) MH_GdipFillPath.MH.invokeExact(graphics, brush, path);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- images */

	private static final class MH_GdipDrawImageI {
		static final MethodHandle MH = link("GdipDrawImageI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT));
	}
	public static int Graphics_DrawImage(long graphics, long image, int x, int y) {
		try {
			return (int) MH_GdipDrawImageI.MH.invokeExact(graphics, image, x, y);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawImageRectRectI {
		static final MethodHandle MH = link("GdipDrawImageRectRectI", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
			JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_DrawImage(long graphics, long image, Rect destRect, int srcx, int srcy, int srcwidth, int srcheight, int srcUnit, long imageAttributes, long callback, long callbackData) {
		try {
			int rc = (int) MH_GdipDrawImageRectRectI.MH.invokeExact(graphics, image, destRect.X, destRect.Y, destRect.Width, destRect.Height,
				srcx, srcy, srcwidth, srcheight, srcUnit, imageAttributes, callback, callbackData);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- text */

	private static final class MH_GdipDrawString {
		static final MethodHandle MH = link("GdipDrawString", status(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_DrawString(long graphics, char[] string, int length, long font, PointF origin, long brush) {
		return Graphics_DrawString(graphics, string, length, font, origin, 0, brush);
	}

	public static int Graphics_DrawString(long graphics, char[] string, int length, long font, PointF origin, long format, long brush) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lpstring = FFM.copyIn(arena, string);
			int rc = (int) MH_GdipDrawString.MH.invokeExact(graphics, lpstring, length, FFMGdipObjects.nativeObject(font), layoutRect(arena, origin), format, brush);
			FFM.copyOut(lpstring, string);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipMeasureString {
		static final MethodHandle MH = link("GdipMeasureString", status(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG));
	}
	public static int Graphics_MeasureString(long graphics, char[] string, int length, long font, PointF origin, RectF boundingBox) {
		return Graphics_MeasureString(graphics, string, length, font, origin, 0, boundingBox);
	}

	public static int Graphics_MeasureString(long graphics, char[] string, int length, long font, PointF origin, long format, RectF boundingBox) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lpstring = FFM.copyIn(arena, string);
			MemorySegment lpbox = boundingBox == null ? MemorySegment.NULL : rectF(arena, boundingBox);
			int rc = (int) MH_GdipMeasureString.MH.invokeExact(graphics, lpstring, length, FFMGdipObjects.nativeObject(font), layoutRect(arena, origin), format, lpbox, 0L, 0L);
			if (boundingBox != null) rectF(lpbox, boundingBox);
			FFM.copyOut(lpstring, string);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipDrawDriverString {
		static final MethodHandle MH = link("GdipDrawDriverString", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG));
	}
	public static int Graphics_DrawDriverString(long graphics, long text, int length, long font, long brush, PointF positions, int flags, long matrix) {
		try (Arena arena = Arena.ofConfined()) {
			return (int) MH_GdipDrawDriverString.MH.invokeExact(graphics, text, length, FFMGdipObjects.nativeObject(font), brush, pointF(arena, positions), flags, matrix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int Graphics_DrawDriverString(long graphics, long text, int length, long font, long brush, float[] positions, int flags, long matrix) {
		try (Arena arena = Arena.ofConfined()) {
			return (int) MH_GdipDrawDriverString.MH.invokeExact(graphics, text, length, FFMGdipObjects.nativeObject(font), brush, FFM.copyIn(arena, positions), flags, matrix);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	private static final class MH_GdipMeasureDriverString {
		static final MethodHandle MH = link("GdipMeasureDriverString", status(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS));
	}
	public static int Graphics_MeasureDriverString(long graphics, long text, int length, long font, float[] positions, int flags, long matrix, RectF boundingBox) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lpbox = boundingBox == null ? MemorySegment.NULL : rectF(arena, boundingBox);
			int rc = (int) MH_GdipMeasureDriverString.MH.invokeExact(graphics, text, length, FFMGdipObjects.nativeObject(font), FFM.copyIn(arena, positions), flags, matrix, lpbox);
			if (boundingBox != null) rectF(lpbox, boundingBox);
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}
}
