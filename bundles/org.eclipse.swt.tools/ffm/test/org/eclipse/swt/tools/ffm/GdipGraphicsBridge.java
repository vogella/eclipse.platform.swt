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
package org.eclipse.swt.tools.ffm;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.util.*;

import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.gdip.*;

/**
 * Test only: runs the Graphics natives through {@link FFMGdipGraphics} while every other GDI+ native
 * is still the JNI C++ wrapper. Object handles of the wrapper are unwrapped to their flat API pointer,
 * and a Graphics is handed out as a fake C++ <code>Graphics</code> ({GpGraphics*, Status}), which is all
 * the JNI natives taking a <code>Graphics*</code> read.
 * <p>
 * The offsets of the native pointer in the C++ objects come from <code>gdip-objects-probe.cpp</code>:
 * <code>Brush</code> and <code>Image</code> have a vtable, the other classes do not.
 * </p>
 */
public final class GdipGraphicsBridge {

	static final long PEN = 0, BRUSH = 8, IMAGE = 8, FONT = 0, MATRIX = 0, REGION = 0, PATH = 0, FORMAT = 0, ATTRIBUTES = 0;

	static final Map<Long, Arena> GRAPHICS = new HashMap<>();

	private GdipGraphicsBridge() {
	}

	static long unwrap(long object, long offset) {
		return object == 0 ? 0 : MemorySegment.ofAddress(object).reinterpret(offset + 8).get(JAVA_LONG, offset);
	}

	static long g(long graphics) {
		return unwrap(graphics, 0);
	}

	public static int GdiplusStartup(long[] token, GdiplusStartupInput input, long output) {
		return FFMGdipGraphics.GdiplusStartup(token, input, output);
	}

	public static void GdiplusShutdown(long token) {
		FFMGdipGraphics.GdiplusShutdown(token);
	}

	public static synchronized long Graphics_new(long hdc) {
		long graphics = FFMGdipGraphics.Graphics_new(hdc);
		if (graphics == 0) return 0;
		Arena arena = Arena.ofShared();
		MemorySegment object = arena.allocate(16, 8);
		object.set(JAVA_LONG, 0, graphics);
		GRAPHICS.put(object.address(), arena);
		return object.address();
	}

	public static synchronized void Graphics_delete(long graphics) {
		FFMGdipGraphics.Graphics_delete(g(graphics));
		Arena arena = GRAPHICS.remove(graphics);
		if (arena != null) arena.close();
	}

	public static int Graphics_DrawArc(long graphics, long pen, int x, int y, int width, int height, float startAngle, float sweepAngle) {
		return FFMGdipGraphics.Graphics_DrawArc(g(graphics), unwrap(pen, PEN), x, y, width, height, startAngle, sweepAngle);
	}

	public static int Graphics_DrawDriverString(long graphics, long text, int length, long font, long brush, PointF positions, int flags, long matrix) {
		return FFMGdipGraphics.Graphics_DrawDriverString(g(graphics), text, length, unwrap(font, FONT), unwrap(brush, BRUSH), positions, flags, unwrap(matrix, MATRIX));
	}

	public static int Graphics_DrawDriverString(long graphics, long text, int length, long font, long brush, float[] positions, int flags, long matrix) {
		return FFMGdipGraphics.Graphics_DrawDriverString(g(graphics), text, length, unwrap(font, FONT), unwrap(brush, BRUSH), positions, flags, unwrap(matrix, MATRIX));
	}

	public static int Graphics_DrawEllipse(long graphics, long pen, int x, int y, int width, int height) {
		return FFMGdipGraphics.Graphics_DrawEllipse(g(graphics), unwrap(pen, PEN), x, y, width, height);
	}

	public static int Graphics_DrawImage(long graphics, long image, int x, int y) {
		return FFMGdipGraphics.Graphics_DrawImage(g(graphics), unwrap(image, IMAGE), x, y);
	}

	public static int Graphics_DrawImage(long graphics, long image, Rect destRect, int srcx, int srcy, int srcwidth, int srcheight, int srcUnit, long imageAttributes, long callback, long callbackData) {
		return FFMGdipGraphics.Graphics_DrawImage(g(graphics), unwrap(image, IMAGE), destRect, srcx, srcy, srcwidth, srcheight, srcUnit, unwrap(imageAttributes, ATTRIBUTES), callback, callbackData);
	}

	public static int Graphics_DrawLine(long graphics, long pen, int x1, int y1, int x2, int y2) {
		return FFMGdipGraphics.Graphics_DrawLine(g(graphics), unwrap(pen, PEN), x1, y1, x2, y2);
	}

	public static int Graphics_DrawLines(long graphics, long pen, int[] points, int count) {
		return FFMGdipGraphics.Graphics_DrawLines(g(graphics), unwrap(pen, PEN), points, count);
	}

	public static int Graphics_DrawPath(long graphics, long pen, long path) {
		return FFMGdipGraphics.Graphics_DrawPath(g(graphics), unwrap(pen, PEN), unwrap(path, PATH));
	}

	public static int Graphics_DrawPolygon(long graphics, long pen, int[] points, int count) {
		return FFMGdipGraphics.Graphics_DrawPolygon(g(graphics), unwrap(pen, PEN), points, count);
	}

	public static int Graphics_DrawRectangle(long graphics, long pen, int x, int y, int width, int height) {
		return FFMGdipGraphics.Graphics_DrawRectangle(g(graphics), unwrap(pen, PEN), x, y, width, height);
	}

	public static int Graphics_DrawString(long graphics, char[] string, int length, long font, PointF origin, long brush) {
		return FFMGdipGraphics.Graphics_DrawString(g(graphics), string, length, unwrap(font, FONT), origin, unwrap(brush, BRUSH));
	}

	public static int Graphics_DrawString(long graphics, char[] string, int length, long font, PointF origin, long format, long brush) {
		return FFMGdipGraphics.Graphics_DrawString(g(graphics), string, length, unwrap(font, FONT), origin, unwrap(format, FORMAT), unwrap(brush, BRUSH));
	}

	public static int Graphics_FillEllipse(long graphics, long brush, int x, int y, int width, int height) {
		return FFMGdipGraphics.Graphics_FillEllipse(g(graphics), unwrap(brush, BRUSH), x, y, width, height);
	}

	public static int Graphics_FillPath(long graphics, long brush, long path) {
		return FFMGdipGraphics.Graphics_FillPath(g(graphics), unwrap(brush, BRUSH), unwrap(path, PATH));
	}

	public static void Graphics_Flush(long graphics, int intention) {
		FFMGdipGraphics.Graphics_Flush(g(graphics), intention);
	}

	public static int Graphics_FillPie(long graphics, long brush, int x, int y, int width, int height, float startAngle, float sweepAngle) {
		return FFMGdipGraphics.Graphics_FillPie(g(graphics), unwrap(brush, BRUSH), x, y, width, height, startAngle, sweepAngle);
	}

	public static int Graphics_FillPolygon(long graphics, long brush, int[] points, int count, int fillMode) {
		return FFMGdipGraphics.Graphics_FillPolygon(g(graphics), unwrap(brush, BRUSH), points, count, fillMode);
	}

	public static int Graphics_FillRectangle(long graphics, long brush, int x, int y, int width, int height) {
		return FFMGdipGraphics.Graphics_FillRectangle(g(graphics), unwrap(brush, BRUSH), x, y, width, height);
	}

	public static int Graphics_GetClipBounds(long graphics, RectF rect) {
		return FFMGdipGraphics.Graphics_GetClipBounds(g(graphics), rect);
	}

	public static int Graphics_GetClipBounds(long graphics, Rect rect) {
		return FFMGdipGraphics.Graphics_GetClipBounds(g(graphics), rect);
	}

	public static int Graphics_GetClip(long graphics, long region) {
		return FFMGdipGraphics.Graphics_GetClip(g(graphics), unwrap(region, REGION));
	}

	public static long Graphics_GetHDC(long graphics) {
		return FFMGdipGraphics.Graphics_GetHDC(g(graphics));
	}

	public static void Graphics_ReleaseHDC(long graphics, long hdc) {
		FFMGdipGraphics.Graphics_ReleaseHDC(g(graphics), hdc);
	}

	public static int Graphics_GetInterpolationMode(long graphics) {
		return FFMGdipGraphics.Graphics_GetInterpolationMode(g(graphics));
	}

	public static int Graphics_GetSmoothingMode(long graphics) {
		return FFMGdipGraphics.Graphics_GetSmoothingMode(g(graphics));
	}

	public static int Graphics_GetTextRenderingHint(long graphics) {
		return FFMGdipGraphics.Graphics_GetTextRenderingHint(g(graphics));
	}

	public static int Graphics_GetTransform(long graphics, long matrix) {
		return FFMGdipGraphics.Graphics_GetTransform(g(graphics), unwrap(matrix, MATRIX));
	}

	public static int Graphics_GetVisibleClipBounds(long graphics, Rect rect) {
		return FFMGdipGraphics.Graphics_GetVisibleClipBounds(g(graphics), rect);
	}

	public static int Graphics_MeasureDriverString(long graphics, long text, int length, long font, float[] positions, int flags, long matrix, RectF boundingBox) {
		return FFMGdipGraphics.Graphics_MeasureDriverString(g(graphics), text, length, unwrap(font, FONT), positions, flags, unwrap(matrix, MATRIX), boundingBox);
	}

	public static int Graphics_MeasureString(long graphics, char[] string, int length, long font, PointF origin, RectF boundingBox) {
		return FFMGdipGraphics.Graphics_MeasureString(g(graphics), string, length, unwrap(font, FONT), origin, boundingBox);
	}

	public static int Graphics_MeasureString(long graphics, char[] string, int length, long font, PointF origin, long format, RectF boundingBox) {
		return FFMGdipGraphics.Graphics_MeasureString(g(graphics), string, length, unwrap(font, FONT), origin, unwrap(format, FORMAT), boundingBox);
	}

	public static int Graphics_ResetClip(long graphics) {
		return FFMGdipGraphics.Graphics_ResetClip(g(graphics));
	}

	public static int Graphics_Restore(long graphics, int gstate) {
		return FFMGdipGraphics.Graphics_Restore(g(graphics), gstate);
	}

	public static int Graphics_Save(long graphics) {
		return FFMGdipGraphics.Graphics_Save(g(graphics));
	}

	public static int Graphics_ScaleTransform(long graphics, float sx, float sy, int order) {
		return FFMGdipGraphics.Graphics_ScaleTransform(g(graphics), sx, sy, order);
	}

	public static int Graphics_SetClip(long graphics, long hrgn, int combineMode) {
		return FFMGdipGraphics.Graphics_SetClip(g(graphics), unwrap(hrgn, REGION), combineMode);
	}

	public static int Graphics_SetClip(long graphics, Rect rect, int combineMode) {
		return FFMGdipGraphics.Graphics_SetClip(g(graphics), rect, combineMode);
	}

	public static int Graphics_SetClipPath(long graphics, long path) {
		return FFMGdipGraphics.Graphics_SetClipPath(g(graphics), unwrap(path, PATH));
	}

	public static int Graphics_SetClipPath(long graphics, long path, int combineMode) {
		return FFMGdipGraphics.Graphics_SetClipPath(g(graphics), unwrap(path, PATH), combineMode);
	}

	public static int Graphics_SetCompositingQuality(long graphics, int compositingQuality) {
		return FFMGdipGraphics.Graphics_SetCompositingQuality(g(graphics), compositingQuality);
	}

	public static int Graphics_SetPageUnit(long graphics, int unit) {
		return FFMGdipGraphics.Graphics_SetPageUnit(g(graphics), unit);
	}

	public static int Graphics_SetPixelOffsetMode(long graphics, int pixelOffsetMode) {
		return FFMGdipGraphics.Graphics_SetPixelOffsetMode(g(graphics), pixelOffsetMode);
	}

	public static int Graphics_SetSmoothingMode(long graphics, int smoothingMode) {
		return FFMGdipGraphics.Graphics_SetSmoothingMode(g(graphics), smoothingMode);
	}

	public static int Graphics_SetTransform(long graphics, long matrix) {
		return FFMGdipGraphics.Graphics_SetTransform(g(graphics), unwrap(matrix, MATRIX));
	}

	public static int Graphics_SetInterpolationMode(long graphics, int mode) {
		return FFMGdipGraphics.Graphics_SetInterpolationMode(g(graphics), mode);
	}

	public static int Graphics_SetTextRenderingHint(long graphics, int mode) {
		return FFMGdipGraphics.Graphics_SetTextRenderingHint(g(graphics), mode);
	}

	public static int Graphics_TranslateTransform(long graphics, float dx, float dy, int order) {
		return FFMGdipGraphics.Graphics_TranslateTransform(g(graphics), dx, dy, order);
	}
}
