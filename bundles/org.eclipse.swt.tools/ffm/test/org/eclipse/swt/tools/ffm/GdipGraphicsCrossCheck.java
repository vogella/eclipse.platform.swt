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

import java.lang.reflect.*;
import java.util.*;

import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.gdip.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;

/**
 * Calls every Graphics native through the JNI C++ wrapper on one bitmap and through
 * {@link FFMGdipGraphics} (via {@link GdipGraphicsBridge}) on another, with the same arguments,
 * and compares results, out-parameters, the resulting clip regions and transforms, and the pixels.
 * Runs on the stock JNI build.
 */
public class GdipGraphicsCrossCheck {

	static final int SIZE = 200;
	static int checks, mismatches;
	static long gA, gB;
	static final Set<String> covered = new TreeSet<>();

	public static void main(String[] args) throws Exception {
		Display display = new Display();
		try {
			Image imageA = new Image(display, SIZE, SIZE), imageB = new Image(display, SIZE, SIZE);
			// starts GDI+ for the device
			Image first = new Image(display, 1, 1);
			GC gc = new GC(first);
			gc.setAdvanced(true);
			gc.dispose();
			first.dispose();
			long hdcA = OS.CreateCompatibleDC(0), hdcB = OS.CreateCompatibleDC(0);
			long oldA = OS.SelectObject(hdcA, Image.win32_getHandle(imageA, 100));
			long oldB = OS.SelectObject(hdcB, Image.win32_getHandle(imageB, 100));
			gA = (long) call(Gdip.class, "Graphics_new", hdcA);
			gB = (long) call(GdipGraphicsBridge.class, "Graphics_new", hdcB);
			check("Graphics_new", gA != 0, gB != 0);
			covered.add("Graphics_new");
			draw(display);
			both("Graphics_Flush", Gdip.FlushIntentionSync);
			call(Gdip.class, "Graphics_delete", gA);
			call(GdipGraphicsBridge.class, "Graphics_delete", gB);
			covered.add("Graphics_delete");
			OS.SelectObject(hdcA, oldA);
			OS.SelectObject(hdcB, oldB);
			OS.DeleteDC(hdcA);
			OS.DeleteDC(hdcB);
			ImageData a = imageA.getImageData(), b = imageB.getImageData();
			int diff = 0;
			for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (a.getPixel(x, y) != b.getPixel(x, y)) diff++;
			check("pixels", 0, diff);
			if (diff != 0) System.out.println("  pixel(100,100) " + Integer.toHexString(a.getPixel(100, 100)) + " " + Integer.toHexString(b.getPixel(100, 100)));
			imageA.dispose();
			startup();
			imageB.dispose();
			// a Graphics on a DC that is not a DC fails in both
			long bad = Gdip.Graphics_new(0), badFFM = FFMGdipGraphics.Graphics_new(0);
			System.out.println("  Graphics_new(0): C++ object " + (bad != 0) + ", FFM handle " + badFFM);
			if (bad != 0) Gdip.Graphics_delete(bad);
			Set<String> natives = new TreeSet<>();
			for (Method m : Gdip.class.getDeclaredMethods()) {
				if (Modifier.isNative(m.getModifiers()) && (m.getName().startsWith("Graphics_") || m.getName().equals("GdiplusStartup") || m.getName().equals("GdiplusShutdown"))) natives.add(m.getName());
			}
			natives.removeAll(covered);
			System.out.println("GdipGraphicsCrossCheck: " + checks + " checks, " + mismatches + " mismatches, natives not called: " + natives);
			if (mismatches != 0 || !natives.isEmpty()) System.exit(1);
		} finally {
			display.dispose();
		}
	}

	static void startup() throws Exception {
		GdiplusStartupInput inA = new GdiplusStartupInput(), inB = new GdiplusStartupInput();
		inA.GdiplusVersion = inB.GdiplusVersion = 1;
		long[] tokenA = new long[1], tokenB = new long[1];
		int a = Gdip.GdiplusStartup(tokenA, inA, 0), b = FFMGdipGraphics.GdiplusStartup(tokenB, inB, 0);
		check("GdiplusStartup", a, b);
		check("GdiplusStartup.token", tokenA[0] != 0, tokenB[0] != 0);
		check("GdiplusStartup.input", fields(inA), fields(inB));
		Gdip.GdiplusShutdown(tokenA[0]);
		FFMGdipGraphics.GdiplusShutdown(tokenB[0]);
		inA.GdiplusVersion = inB.GdiplusVersion = 99;
		check("GdiplusStartup.badVersion", Gdip.GdiplusStartup(tokenA, inA, 0), FFMGdipGraphics.GdiplusStartup(tokenB, inB, 0));
		covered.add("GdiplusStartup");
		covered.add("GdiplusShutdown");
	}

	static void draw(Display display) throws Exception {
		long brush = Gdip.SolidBrush_new(0xFFCC2020), brush2 = Gdip.SolidBrush_new(0x802040FF);
		long pen = Gdip.Pen_new(brush, 3f), pen2 = Gdip.Pen_new(brush2, 1.5f);
		Gdip.Pen_SetDashStyle(pen2, Gdip.DashStyleDashDot);
		long path = Gdip.GraphicsPath_new(Gdip.FillModeAlternate);
		Gdip.GraphicsPath_AddArc(path, 20, 20, 80, 60, 10, 280);
		Gdip.GraphicsPath_AddBezier(path, 10, 150, 60, 90, 120, 190, 190, 120);
		Gdip.GraphicsPath_AddLine(path, 190, 120, 150, 30);
		long matrix = Gdip.Matrix_new(1, 0, 0, 1, 0, 0), matrixA = Gdip.Matrix_new(1, 0, 0, 1, 0, 0), matrixB = Gdip.Matrix_new(1, 0, 0, 1, 0, 0);
		Gdip.Matrix_Rotate(matrix, 12, Gdip.MatrixOrderPrepend);
		long regionA = Gdip.Region_new(), regionB = Gdip.Region_new();
		long hrgn = OS.CreateRectRgn(30, 40, 170, 150);
		long clipRegion = Gdip.Region_new(hrgn);
		OS.DeleteObject(hrgn);
		long bitmap = bitmap();
		long attributes = Gdip.ImageAttributes_new();
		Gdip.ImageAttributes_SetWrapMode(attributes, Gdip.WrapModeTileFlipXY);
		long font = Gdip.Font_new("Arial".toCharArray(), 14, 0, Gdip.UnitPixel, 0);
		long format = Gdip.StringFormat_Clone(Gdip.StringFormat_GenericTypographic());
		char[] chars = "Glyph run".toCharArray();
		long text = OS.HeapAlloc(OS.GetProcessHeap(), OS.HEAP_ZERO_MEMORY, chars.length * 2);
		OS.MoveMemory(text, chars, chars.length * 2);
		float[] positions = new float[chars.length * 2];
		for (int i = 0; i < chars.length; i++) {
			positions[i * 2] = 20 + i * 9;
			positions[i * 2 + 1] = 190;
		}
		int cmapLookup = 1;

		// rendering modes, including invalid values
		for (int mode : new int[] { Gdip.SmoothingModeAntiAlias, Gdip.SmoothingModeNone, 42 }) {
			both("Graphics_SetSmoothingMode", mode);
			both("Graphics_GetSmoothingMode");
		}
		for (int mode : new int[] { Gdip.InterpolationModeNearestNeighbor, Gdip.InterpolationModeHighQualityBicubic, -7 }) {
			both("Graphics_SetInterpolationMode", mode);
			both("Graphics_GetInterpolationMode");
		}
		for (int mode : new int[] { Gdip.TextRenderingHintAntiAlias, Gdip.TextRenderingHintSingleBitPerPixelGridFit, 77 }) {
			both("Graphics_SetTextRenderingHint", mode);
			both("Graphics_GetTextRenderingHint");
		}
		both("Graphics_SetTextRenderingHint", Gdip.TextRenderingHintAntiAliasGridFit);
		both("Graphics_SetCompositingQuality", 99);
		both("Graphics_SetCompositingQuality", 2);
		both("Graphics_SetPixelOffsetMode", 99);
		both("Graphics_SetPixelOffsetMode", Gdip.PixelOffsetModeHalf);
		both("Graphics_SetPageUnit", 99);
		both("Graphics_SetPageUnit", Gdip.UnitPixel);
		both("Graphics_SetSmoothingMode", Gdip.SmoothingModeAntiAlias);

		// shapes
		both("Graphics_FillRectangle", brush2, 5, 5, 190, 190);
		both("Graphics_DrawLine", pen, 10, 10, 190, 60);
		both("Graphics_DrawLines", pen2, new int[] { 10, 60, 50, 20, 90, 70, 130, 30 }, 4);
		both("Graphics_DrawArc", pen, 30, 30, 120, 90, 20f, 250f);
		both("Graphics_DrawRectangle", pen2, 40, 110, 70, 50);
		both("Graphics_DrawEllipse", pen, 110, 100, 70, 40);
		both("Graphics_DrawPolygon", pen2, new int[] { 150, 10, 190, 40, 170, 80, 130, 50 }, 4);
		both("Graphics_DrawPath", pen, path);
		both("Graphics_DrawPath", 0L, path);
		both("Graphics_FillEllipse", brush, 60, 60, 40, 30);
		both("Graphics_FillPie", brush, 120, 130, 60, 60, 30f, 200f);
		both("Graphics_FillPolygon", brush2, new int[] { 10, 100, 60, 180, 90, 90, 20, 170 }, 4, Gdip.FillModeWinding);
		both("Graphics_FillPolygon", brush2, new int[] { 10, 100, 60, 180 }, 2, Gdip.FillModeAlternate);
		both("Graphics_FillPath", brush2, path);

		// images
		both("Graphics_DrawImage", bitmap, 150, 150);
		both("Graphics_DrawImage", 0L, 150, 150);
		Rect dest = new Rect();
		dest.X = 5;
		dest.Y = 120;
		dest.Width = 70;
		dest.Height = 50;
		both("Graphics_DrawImage", bitmap, dest, 2, 3, 20, 14, Gdip.UnitPixel, attributes, 0L, 0L);
		both("Graphics_DrawImage", bitmap, dest, 0, 0, 30, 20, Gdip.UnitPixel, 0L, 0L, 0L);

		// transforms and state
		int stateA = (int) call(Gdip.class, "Graphics_Save", gA), stateB = (int) call(GdipGraphicsBridge.class, "Graphics_Save", gB);
		// the state is an opaque id, which differs between two Graphics
		check("Graphics_Save", stateA != 0, stateB != 0);
		covered.add("Graphics_Save");
		both("Graphics_TranslateTransform", 30f, 20f, Gdip.MatrixOrderPrepend);
		both("Graphics_ScaleTransform", 1.25f, 0.75f, Gdip.MatrixOrderAppend);
		pair("Graphics_GetTransform", matrixA, matrixB);
		both("Graphics_SetTransform", matrix);
		sameMatrix(matrixA, matrixB);
		both("Graphics_DrawRectangle", pen, 10, 10, 60, 40);
		pair("Graphics_Restore", stateA, stateB);
		pair("Graphics_Restore", 12345, 12345);
		pair("Graphics_GetTransform", matrixA, matrixB);
		sameMatrix(matrixA, matrixB);

		// clipping
		both("Graphics_GetVisibleClipBounds", new Rect());
		both("Graphics_GetClipBounds", new Rect());
		both("Graphics_GetClipBounds", new RectF());
		pair("Graphics_GetClip", regionA, regionB);
		sameRegion(regionA, regionB);
		both("Graphics_SetClip", clipRegion, Gdip.CombineModeReplace);
		Rect clip = new Rect();
		clip.X = 60;
		clip.Y = 20;
		clip.Width = 100;
		clip.Height = 100;
		both("Graphics_SetClip", clip, Gdip.CombineModeExclude);
		both("Graphics_GetClipBounds", new RectF());
		pair("Graphics_GetClip", regionA, regionB);
		sameRegion(regionA, regionB);
		both("Graphics_FillRectangle", brush, 0, 0, SIZE, SIZE);
		both("Graphics_SetClipPath", path);
		both("Graphics_GetVisibleClipBounds", new Rect());
		both("Graphics_SetClipPath", path, Gdip.CombineModeXor);
		both("Graphics_SetClip", clip, 42);
		both("Graphics_GetClipBounds", new Rect());
		both("Graphics_FillRectangle", brush2, 0, 0, SIZE, SIZE);
		both("Graphics_ResetClip");
		pair("Graphics_GetClip", regionA, regionB);
		sameRegion(regionA, regionB);

		// GDI interop
		long hdcA = (long) call(Gdip.class, "Graphics_GetHDC", gA), hdcB = (long) call(GdipGraphicsBridge.class, "Graphics_GetHDC", gB);
		check("Graphics_GetHDC", hdcA != 0, hdcB != 0);
		covered.add("Graphics_GetHDC");
		OS.PatBlt(hdcA, 170, 5, 20, 20, OS.BLACKNESS);
		OS.PatBlt(hdcB, 170, 5, 20, 20, OS.BLACKNESS);
		// a second GetHDC while locked fails with ObjectBusy in both
		check("Graphics_GetHDC.locked", Gdip.Graphics_GetHDC(gA) != 0, GdipGraphicsBridge.Graphics_GetHDC(gB) != 0);
		call(Gdip.class, "Graphics_ReleaseHDC", gA, hdcA);
		call(GdipGraphicsBridge.class, "Graphics_ReleaseHDC", gB, hdcB);
		covered.add("Graphics_ReleaseHDC");

		// text
		PointF origin = new PointF();
		origin.X = 12;
		origin.Y = 150;
		char[] string = "Text 漢字".toCharArray();
		both("Graphics_DrawString", string, string.length, font, origin, brush);
		origin.Y = 165;
		both("Graphics_DrawString", string, string.length, font, origin, format, brush);
		both("Graphics_DrawString", string, string.length, 0L, origin, brush);
		both("Graphics_MeasureString", string, string.length, font, origin, new RectF());
		both("Graphics_MeasureString", string, 4, font, origin, format, new RectF());
		both("Graphics_MeasureString", string, string.length, 0L, origin, format, new RectF());
		both("Graphics_DrawDriverString", text, chars.length, font, brush, positions, cmapLookup, 0L);
		both("Graphics_DrawDriverString", text, chars.length, font, brush, positions, cmapLookup, matrix);
		PointF start = new PointF();
		start.X = 100;
		start.Y = 30;
		both("Graphics_DrawDriverString", text, chars.length, font, brush2, start, cmapLookup | Gdip.DriverStringOptionsRealizedAdvance, 0L);
		both("Graphics_MeasureDriverString", text, chars.length, font, positions, cmapLookup, 0L, new RectF());
		both("Graphics_MeasureDriverString", text, chars.length, font, positions, cmapLookup, matrix, new RectF());
		both("Graphics_MeasureDriverString", text, chars.length, 0L, positions, cmapLookup, 0L, new RectF());

		OS.HeapFree(OS.GetProcessHeap(), 0, text);
		Gdip.StringFormat_delete(format);
		Gdip.Font_delete(font);
		Gdip.ImageAttributes_delete(attributes);
		Gdip.Bitmap_delete(bitmap);
		Gdip.Region_delete(clipRegion);
		Gdip.Region_delete(regionA);
		Gdip.Region_delete(regionB);
		Gdip.Matrix_delete(matrix);
		Gdip.Matrix_delete(matrixA);
		Gdip.Matrix_delete(matrixB);
		Gdip.GraphicsPath_delete(path);
		Gdip.Pen_delete(pen);
		Gdip.Pen_delete(pen2);
		Gdip.SolidBrush_delete(brush);
		Gdip.SolidBrush_delete(brush2);
	}

	static long bitmap() {
		int w = 30, h = 20;
		int[] pixels = new int[w * h];
		for (int i = 0; i < pixels.length; i++) pixels[i] = ((i % w) * 8 << 16) | ((i / w) * 12 << 8) | 0x40 | (((i % w) * 8) << 24);
		long scan0 = OS.HeapAlloc(OS.GetProcessHeap(), 0, pixels.length * 4);
		OS.MoveMemory(scan0, pixels, pixels.length * 4);
		// the bitmap keeps pointing to scan0, which is never freed here
		return Gdip.Bitmap_new(w, h, w * 4, Gdip.PixelFormat32bppARGB, scan0);
	}

	static void sameMatrix(long a, long b) {
		float[] ea = new float[6], eb = new float[6];
		Gdip.Matrix_GetElements(a, ea);
		Gdip.Matrix_GetElements(b, eb);
		check("matrix", Arrays.toString(ea), Arrays.toString(eb));
	}

	static void sameRegion(long a, long b) {
		check("region.infinite", Gdip.Region_IsInfinite(a, gA), Gdip.Region_IsInfinite(b, gB));
		long ra = Gdip.Region_GetHRGN(a, gA), rb = Gdip.Region_GetHRGN(b, gB);
		RECT boxA = new RECT(), boxB = new RECT();
		int ta = ra == 0 ? -1 : OS.GetRgnBox(ra, boxA), tb = rb == 0 ? -1 : OS.GetRgnBox(rb, boxB);
		check("region.box", ta + " " + fields(boxA), tb + " " + fields(boxB));
		if (ra != 0) OS.DeleteObject(ra);
		if (rb != 0) OS.DeleteObject(rb);
	}

	/** Calls the native through the C++ wrapper and through FFM with the same arguments and compares. */
	static void both(String name, Object... args) throws Exception {
		Object[] argsB = new Object[args.length];
		for (int i = 0; i < args.length; i++) argsB[i] = copy(args[i]);
		both(name, args, argsB);
	}

	static void both(String name, Object[] argsA, Object[] argsB) throws Exception {
		Object a = call(Gdip.class, name, prepend(gA, argsA));
		Object b = call(GdipGraphicsBridge.class, name, prepend(gB, argsB));
		covered.add(name);
		check(name, a, b);
		for (int i = 0; i < argsA.length; i++) {
			if (argsA[i] != null && !(argsA[i] instanceof Number)) check(name + ".arg" + i, fields(argsA[i]), fields(argsB[i]));
		}
	}

	static void pair(String name, long regionOrMatrixA, long regionOrMatrixB) throws Exception {
		both(name, new Object[] { regionOrMatrixA }, new Object[] { regionOrMatrixB });
	}

	static void pair(String name, int stateA, int stateB) throws Exception {
		both(name, new Object[] { stateA }, new Object[] { stateB });
	}

	static Object[] prepend(long graphics, Object[] args) {
		Object[] all = new Object[args.length + 1];
		all[0] = graphics;
		System.arraycopy(args, 0, all, 1, args.length);
		return all;
	}

	static Object call(Class<?> type, String name, Object... args) throws Exception {
		for (Method m : type.getDeclaredMethods()) {
			if (!m.getName().equals(name) || m.getParameterCount() != args.length || !Modifier.isPublic(m.getModifiers())) continue;
			Class<?>[] types = m.getParameterTypes();
			boolean match = true;
			for (int i = 0; i < types.length && match; i++) match = accepts(types[i], args[i]);
			if (match) return m.invoke(null, args);
		}
		throw new NoSuchMethodException(type.getSimpleName() + "." + name + Arrays.toString(args));
	}

	static boolean accepts(Class<?> type, Object arg) {
		if (type == long.class) return arg instanceof Long;
		if (type == int.class) return arg instanceof Integer;
		if (type == float.class) return arg instanceof Float;
		return arg == null || type.isInstance(arg);
	}

	static Object copy(Object o) throws Exception {
		if (o instanceof int[] a) return a.clone();
		if (o instanceof float[] a) return a.clone();
		if (o instanceof char[] a) return a.clone();
		if (o == null || o instanceof Number) return o;
		Object c = o.getClass().getDeclaredConstructor().newInstance();
		for (Field f : o.getClass().getFields()) if (!Modifier.isStatic(f.getModifiers())) f.set(c, f.get(o));
		return c;
	}

	static String fields(Object o) {
		if (o instanceof int[] a) return Arrays.toString(a);
		if (o instanceof float[] a) return Arrays.toString(a);
		if (o instanceof char[] a) return new String(a);
		StringBuilder b = new StringBuilder("{");
		for (Field f : o.getClass().getFields()) {
			if (Modifier.isStatic(f.getModifiers())) continue;
			try {
				b.append(f.getName()).append('=').append(f.get(o)).append(' ');
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return b.append('}').toString();
	}

	static void check(String what, Object a, Object b) {
		checks++;
		if (!Objects.equals(a, b)) {
			mismatches++;
			System.out.println("MISMATCH " + what + ": C++ " + a + ", FFM " + b);
		}
	}
}
