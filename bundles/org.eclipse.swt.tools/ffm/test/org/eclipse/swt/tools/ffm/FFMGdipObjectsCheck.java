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

import java.io.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.gdip.*;
import org.eclipse.swt.internal.win32.*;

/**
 * Runs the same GDI+ scenarios once through the JNI natives of {@link Gdip} (C++ wrapper objects) and
 * once through {@link FFMGdipObjects} (flat API objects) and compares what they observe: struct
 * sizes, matrix elements, path points and types, region data, font metrics and names, locked bitmap
 * bits, palettes, HBITMAP and HICON bits, and the pixels brushes, pens, string formats and image
 * attributes render. Run it on the stock (JNI) build with the SWT natives on java.library.path; the
 * FFM side draws through the flat API directly, independent of the FFM Graphics_* natives.
 * <p>
 * Arguments: directories searched for .bmp, .gif, .png, .jpg and .ico files to load.
 * </p>
 */
public class FFMGdipObjectsCheck {

	static int checks, failures;
	static final boolean TRACE = Boolean.getBoolean("trace");

	/** Calls a Gdip native by name on one implementation. */
	interface Api {
		Object c(String name, Object... args);

		default long h(String name, Object... args) {
			return (Long) c(name, args);
		}

		default int i(String name, Object... args) {
			return (Integer) c(name, args);
		}
	}

	static final class Reflective implements Api {
		final Class<?>[] classes;
		final String label;

		Reflective(String label, Class<?>... classes) {
			this.label = label;
			this.classes = classes;
		}

		@Override
		public Object c(String name, Object... args) {
			if (TRACE) System.out.println(label + " " + name + Arrays.toString(args));
			for (Class<?> c : classes) {
				for (Method m : c.getMethods()) {
					if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers()) || !matches(m.getParameterTypes(), args)) continue;
					try {
						return m.invoke(null, args);
					} catch (InvocationTargetException e) {
						throw new RuntimeException(label + " " + name, e.getCause());
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}
				}
			}
			throw new IllegalArgumentException(label + ": no " + name + Arrays.toString(args));
		}

		static boolean matches(Class<?>[] types, Object[] args) {
			if (types.length != args.length) return false;
			for (int i = 0; i < types.length; i++) {
				Object a = args[i];
				Class<?> t = types[i];
				if (a == null) {
					if (t.isPrimitive()) return false;
				} else if (t == long.class) {
					if (!(a instanceof Long)) return false;
				} else if (t == int.class) {
					if (!(a instanceof Integer)) return false;
				} else if (t == float.class) {
					if (!(a instanceof Float)) return false;
				} else if (t == boolean.class) {
					if (!(a instanceof Boolean)) return false;
				} else if (t == char.class) {
					if (!(a instanceof Character)) return false;
				} else if (!t.isInstance(a)) {
					return false;
				}
			}
			return true;
		}
	}

	/** The Graphics_* natives the scenarios need, on the flat API, for the FFM side. */
	public static final class FlatGraphics {
		static final SymbolLookup GDIPLUS = SymbolLookup.libraryLookup("gdiplus.dll", Arena.global());
		static final Linker LINKER = Linker.nativeLinker();

		static MethodHandle gdip(String name, MemoryLayout... args) {
			return LINKER.downcallHandle(GDIPLUS.find(name).orElseThrow(), FunctionDescriptor.of(JAVA_INT, args));
		}

		static final MethodHandle CREATE = gdip("GdipCreateFromHDC", JAVA_LONG, JAVA_LONG);
		static final MethodHandle DELETE = gdip("GdipDeleteGraphics", JAVA_LONG);
		static final MethodHandle SMOOTHING = gdip("GdipSetSmoothingMode", JAVA_LONG, JAVA_INT);
		static final MethodHandle HINT = gdip("GdipSetTextRenderingHint", JAVA_LONG, JAVA_INT);
		static final MethodHandle FILL_RECT = gdip("GdipFillRectangleI", JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
		static final MethodHandle FILL_PATH = gdip("GdipFillPath", JAVA_LONG, JAVA_LONG, JAVA_LONG);
		static final MethodHandle DRAW_PATH = gdip("GdipDrawPath", JAVA_LONG, JAVA_LONG, JAVA_LONG);
		static final MethodHandle DRAW_STRING = gdip("GdipDrawString", JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG);
		static final MethodHandle DRAW_IMAGE = gdip("GdipDrawImageRectRectI", JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG);

		public static long Graphics_new(long hdc) throws Throwable {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment out = arena.allocate(JAVA_LONG);
				int status = (int) CREATE.invokeExact(hdc, out.address());
				return out.get(JAVA_LONG, 0);
			}
		}

		public static void Graphics_delete(long g) throws Throwable {
			int status = (int) DELETE.invokeExact(g);
		}

		public static int Graphics_SetSmoothingMode(long g, int mode) throws Throwable {
			return (int) SMOOTHING.invokeExact(g, mode);
		}

		public static int Graphics_SetTextRenderingHint(long g, int mode) throws Throwable {
			return (int) HINT.invokeExact(g, mode);
		}

		public static int Graphics_FillRectangle(long g, long brush, int x, int y, int width, int height) throws Throwable {
			return (int) FILL_RECT.invokeExact(g, brush, x, y, width, height);
		}

		public static int Graphics_FillPath(long g, long brush, long path) throws Throwable {
			return (int) FILL_PATH.invokeExact(g, brush, path);
		}

		public static int Graphics_DrawPath(long g, long pen, long path) throws Throwable {
			return (int) DRAW_PATH.invokeExact(g, pen, path);
		}

		public static int Graphics_DrawString(long g, char[] string, int length, long font, PointF origin, long format, long brush) throws Throwable {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment text = arena.allocateFrom(JAVA_CHAR, string);
				MemorySegment rect = arena.allocate(16);
				rect.set(JAVA_FLOAT, 0, origin.X);
				rect.set(JAVA_FLOAT, 4, origin.Y);
				return (int) DRAW_STRING.invokeExact(g, text.address(), length, FFMGdipObjects.nativeObject(font), rect.address(), format, brush);
			}
		}

		public static int Graphics_DrawImage(long g, long image, Rect dest, int srcx, int srcy, int srcwidth, int srcheight, int srcUnit, long attributes, long callback, long data) throws Throwable {
			return (int) DRAW_IMAGE.invokeExact(g, image, dest.X, dest.Y, dest.Width, dest.Height, srcx, srcy, srcwidth, srcheight, srcUnit, attributes, callback, data);
		}
	}

	static final Api JNI = new Reflective("JNI", Gdip.class);
	static final Api FFM_API = new Reflective("FFM", FFMGdipObjects.class, FlatGraphics.class);

	/** One scenario, run on each implementation, returns what it observed. */
	interface Scenario {
		void run(Api api, List<Object> out) throws Exception;
	}

	static void compare(String name, Scenario scenario) {
		String only = System.getProperty("only");
		if (only != null && !name.startsWith(only)) return;
		List<Object> jni = new ArrayList<>(), ffm = new ArrayList<>();
		try {
			scenario.run(JNI, jni);
			scenario.run(FFM_API, ffm);
		} catch (Exception e) {
			failures++;
			System.out.println("ERROR    " + name + ": " + e);
			e.printStackTrace(System.out);
			return;
		}
		if (jni.size() != ffm.size()) {
			failures++;
			checks++;
			System.out.println("MISMATCH " + name + ": " + jni.size() + " JNI observations, " + ffm.size() + " FFM");
			return;
		}
		int before = failures;
		for (int i = 0; i < jni.size(); i++) {
			checks++;
			if (!same(jni.get(i), ffm.get(i))) {
				failures++;
				if (failures - before <= 5) {
					String label = "";
					for (int j = i - 1; j >= 0 && label.isEmpty(); j--) if (jni.get(j) instanceof String s) label = " after \"" + s + "\"";
					System.out.println("MISMATCH " + name + " #" + i + label + ": JNI " + describe(jni.get(i)) + ", FFM " + describe(ffm.get(i)));
				}
			}
		}
		System.out.println((failures == before ? "OK       " : "FAILED   ") + name + " (" + jni.size() + " observations)");
	}

	static boolean same(Object a, Object b) {
		if (a instanceof Float x && b instanceof Float y) return Float.floatToIntBits(x) == Float.floatToIntBits(y);
		if (a instanceof float[] x && b instanceof float[] y) {
			if (x.length != y.length) return false;
			for (int i = 0; i < x.length; i++) if (Float.floatToIntBits(x[i]) != Float.floatToIntBits(y[i])) return false;
			return true;
		}
		return Objects.deepEquals(a, b);
	}

	static String describe(Object o) {
		if (o instanceof float[] a) return Arrays.toString(a);
		if (o instanceof int[] a) return Arrays.toString(a);
		if (o instanceof byte[] a) return "byte[" + a.length + "] hash " + Arrays.hashCode(a);
		if (o instanceof char[] a) return new String(a);
		return String.valueOf(o);
	}

	/* Win32 helpers, through the JNI OS natives on both sides */

	static long screenDC;

	static byte[] bitmapInfo(int width, int height, int bits) {
		byte[] bmi = new byte[40 + 4 * 256];
		setInt(bmi, 0, 40);
		setInt(bmi, 4, width);
		setInt(bmi, 8, -height);
		bmi[12] = 1;
		bmi[14] = (byte) bits;
		return bmi;
	}

	static void setInt(byte[] b, int offset, int value) {
		b[offset] = (byte) value;
		b[offset + 1] = (byte) (value >> 8);
		b[offset + 2] = (byte) (value >> 16);
		b[offset + 3] = (byte) (value >> 24);
	}

	/** A 32 bpp top-down DIB section and a memory DC it is selected into. */
	static final class Surface implements AutoCloseable {
		final long dc, bitmap, old, bits;
		final int width, height;

		Surface(int width, int height) {
			this.width = width;
			this.height = height;
			long[] pBits = new long[1];
			bitmap = OS.CreateDIBSection(0, bitmapInfo(width, height, 32), OS.DIB_RGB_COLORS, pBits, 0, 0);
			bits = pBits[0];
			dc = OS.CreateCompatibleDC(screenDC);
			old = OS.SelectObject(dc, bitmap);
		}

		byte[] pixels() {
			byte[] data = new byte[width * height * 4];
			OS.MoveMemory(data, bits, data.length);
			return data;
		}

		@Override
		public void close() {
			OS.SelectObject(dc, old);
			OS.DeleteDC(dc);
			OS.DeleteObject(bitmap);
		}
	}

	interface Painter {
		void paint(Api api, long graphics) throws Exception;
	}

	static byte[] render(Api api, int width, int height, Painter painter) throws Exception {
		try (Surface s = new Surface(width, height)) {
			long g = api.h("Graphics_new", s.dc);
			api.c("Graphics_SetSmoothingMode", g, Gdip.SmoothingModeAntiAlias);
			api.c("Graphics_SetTextRenderingHint", g, Gdip.TextRenderingHintAntiAlias);
			painter.paint(api, g);
			api.c("Graphics_delete", g);
			return s.pixels();
		}
	}

	static byte[] dibBits(long hBitmap) {
		BITMAP bm = new BITMAP();
		OS.GetObject(hBitmap, BITMAP.sizeof, bm);
		byte[] data = new byte[bm.bmWidth * Math.abs(bm.bmHeight) * 4];
		OS.GetDIBits(screenDC, hBitmap, 0, Math.abs(bm.bmHeight), data, bitmapInfo(bm.bmWidth, Math.abs(bm.bmHeight), 32), OS.DIB_RGB_COLORS);
		return data;
	}

	static byte[] regionData(long hRgn) {
		if (hRgn == 0) return new byte[0];
		int size = OS.GetRegionData(hRgn, 0, null);
		int[] data = new int[(size + 3) / 4];
		OS.GetRegionData(hRgn, size, data);
		byte[] bytes = new byte[size];
		for (int i = 0; i < size; i++) bytes[i] = (byte) (data[i / 4] >> (8 * (i % 4)));
		return bytes;
	}

	static long nativeMemory(int size) {
		return OS.HeapAlloc(OS.GetProcessHeap(), OS.HEAP_ZERO_MEMORY, size);
	}

	static void free(long pointer) {
		OS.HeapFree(OS.GetProcessHeap(), 0, pointer);
	}

	static char[] chars(String s) {
		char[] c = new char[s.length() + 1];
		s.getChars(0, s.length(), c, 0);
		return c;
	}

	static PointF pointF(float x, float y) {
		PointF p = new PointF();
		p.X = x;
		p.Y = y;
		return p;
	}

	static void observePath(Api api, long path, List<Object> out) {
		int count = api.i("GraphicsPath_GetPointCount", path);
		out.add(count);
		float[] points = new float[count * 2];
		byte[] types = new byte[count];
		out.add(api.c("GraphicsPath_GetPathPoints", path, points, count));
		out.add(points);
		out.add(api.c("GraphicsPath_GetPathTypes", path, types, count));
		out.add(types);
		PointF last = new PointF();
		out.add(api.c("GraphicsPath_GetLastPoint", path, last));
		out.add(new float[] {last.X, last.Y});
		RectF bounds = new RectF();
		out.add(api.c("GraphicsPath_GetBounds", path, bounds, 0L, 0L));
		out.add(new float[] {bounds.X, bounds.Y, bounds.Width, bounds.Height});
	}

	/* Scenarios */

	static void sizes(Api api, List<Object> out) {
		out.add(api.c("ColorPalette_sizeof"));
		out.add(api.c("GdiplusStartupInput_sizeof"));
		out.add(ColorPalette.sizeof);
	}

	static void matrices(Api api, List<Object> out) {
		Random random = new Random(42);
		for (int n = 0; n < 200; n++) {
			float[] e = new float[6];
			for (int i = 0; i < 6; i++) e[i] = (random.nextFloat() - 0.5f) * 8;
			long m = api.h("Matrix_new", e[0], e[1], e[2], e[3], e[4], e[5]);
			long m2 = api.h("Matrix_new", 1.5f, 0.25f, -0.5f, 2f, 3f, -4f);
			for (int op = 0; op < 12; op++) {
				int order = random.nextInt(2);
				float a = (random.nextFloat() - 0.5f) * 360, b = (random.nextFloat() - 0.5f) * 4;
				Object status = switch (random.nextInt(8)) {
					case 0 -> api.c("Matrix_Rotate", m, a, order);
					case 1 -> api.c("Matrix_Scale", m, b, b + 0.5f, order);
					case 2 -> api.c("Matrix_Shear", m, b, -b, order);
					case 3 -> api.c("Matrix_Translate", m, a, b, order);
					case 4 -> api.c("Matrix_Multiply", m, m2, order);
					case 5 -> api.c("Matrix_Invert", m);
					case 6 -> api.c("Matrix_SetElements", m, a, b, b, a, b, a);
					default -> api.c("Matrix_SetElements", m, 1f, 0f, 0f, 1f, 0f, 0f);
				};
				out.add(status);
				float[] elements = new float[6];
				out.add(api.c("Matrix_GetElements", m, elements));
				out.add(elements);
				out.add(api.c("Matrix_IsIdentity", m));
			}
			float[] pts = {1, 2, -3, 4, 5.5f, -6.25f, 100, 200};
			out.add(api.c("Matrix_TransformPoints", m, pts, 4));
			out.add(pts);
			PointF p = pointF(3, -7);
			out.add(api.c("Matrix_TransformPoints", m, p, 1));
			out.add(new float[] {p.X, p.Y});
			PointF v = pointF(3, -7);
			out.add(api.c("Matrix_TransformVectors", m, v, 1));
			out.add(new float[] {v.X, v.Y});
			api.c("Matrix_delete", m2);
			api.c("Matrix_delete", m);
		}
	}

	static void paths(Api api, List<Object> out) {
		Random random = new Random(7);
		long family = api.h("FontFamily_new", chars("Arial"), 0L);
		long format = api.h("StringFormat_Clone", api.h("StringFormat_GenericTypographic"));
		long matrix = api.h("Matrix_new", 0.9f, 0.1f, -0.2f, 1.1f, 5f, -3f);
		long brush = api.h("SolidBrush_new", 0xFF000000);
		long pen = api.h("Pen_new", brush, 3f);
		try (Surface s = new Surface(8, 8)) {
			long g = api.h("Graphics_new", s.dc);
			for (int n = 0; n < 60; n++) {
				long path = api.h("GraphicsPath_new", random.nextInt(2));
				long sub = api.h("GraphicsPath_new", Gdip.FillModeAlternate);
				api.c("GraphicsPath_AddArc", sub, 10f, 10f, 40f, 30f, 15f, 250f);
				for (int op = 0; op < 8; op++) {
					float x = random.nextFloat() * 100, y = random.nextFloat() * 100, w = random.nextFloat() * 50, h = random.nextFloat() * 50;
					Object status = switch (random.nextInt(10)) {
						case 0 -> api.c("GraphicsPath_AddLine", path, x, y, w, h);
						case 1 -> api.c("GraphicsPath_AddArc", path, x, y, w, h, x, y * 3);
						case 2 -> api.c("GraphicsPath_AddBezier", path, x, y, w, h, y, x, h, w);
						case 3 -> {
							RectF r = new RectF();
							r.X = x; r.Y = y; r.Width = w; r.Height = h;
							yield api.c("GraphicsPath_AddRectangle", path, r);
						}
						case 4 -> api.c("GraphicsPath_StartFigure", path);
						case 5 -> api.c("GraphicsPath_CloseFigure", path);
						case 6 -> api.c("GraphicsPath_AddPath", path, sub, random.nextBoolean());
						case 7 -> api.c("GraphicsPath_Transform", path, random.nextBoolean() ? matrix : 0L);
						case 8 -> api.c("GraphicsPath_AddString", path, chars("Ag\u00e9"), 3, family, random.nextInt(4), 10f + w, pointF(x, y), random.nextBoolean() ? format : 0L);
						default -> api.c("GraphicsPath_SetFillMode", path, random.nextInt(2));
					};
					out.add(status);
				}
				observePath(api, path, out);
				RectF bounds = new RectF();
				out.add(api.c("GraphicsPath_GetBounds", path, bounds, matrix, pen));
				out.add(new float[] {bounds.X, bounds.Y, bounds.Width, bounds.Height});
				for (int k = 0; k < 10; k++) {
					float x = random.nextFloat() * 120, y = random.nextFloat() * 120;
					out.add(api.c("GraphicsPath_IsVisible", path, x, y, k % 2 == 0 ? 0L : g));
					out.add(api.c("GraphicsPath_IsOutlineVisible", path, x, y, pen, k % 2 == 0 ? 0L : g));
				}
				long clone = api.h("GraphicsPath_Clone", path);
				observePath(api, clone, out);
				out.add(api.c("GraphicsPath_Flatten", clone, random.nextBoolean() ? matrix : 0L, 0.1f + random.nextFloat()));
				observePath(api, clone, out);
				long region = api.h("Region_newGraphicsPath", clone);
				out.add(api.c("Region_IsInfinite", region, g));
				long hRgn = api.h("Region_GetHRGN", region, g);
				out.add(regionData(hRgn));
				if (hRgn != 0) OS.DeleteObject(hRgn);
				api.c("Region_delete", region);
				api.c("GraphicsPath_delete", clone);
				api.c("GraphicsPath_delete", sub);
				api.c("GraphicsPath_delete", path);
			}
			int[] points = {0, 0, 50, 0, 50, 50, 10, 60, 20, 70, 30, 80, 0, 40};
			byte[] types = {0, 1, 1, 3, 3, 3, (byte) 0x81};
			long path = api.h("GraphicsPath_new", points, types, 7, Gdip.FillModeWinding);
			observePath(api, path, out);
			api.c("GraphicsPath_delete", path);
			api.c("Graphics_delete", g);
		}
		api.c("Pen_delete", pen);
		api.c("SolidBrush_delete", brush);
		api.c("Matrix_delete", matrix);
		api.c("StringFormat_delete", format);
		api.c("FontFamily_delete", family);
	}

	static void regions(Api api, List<Object> out) {
		try (Surface s = new Surface(8, 8)) {
			long g = api.h("Graphics_new", s.dc);
			long region = api.h("Region_new");
			out.add(api.c("Region_IsInfinite", region, g));
			long hRgn = api.h("Region_GetHRGN", region, g);
			out.add(hRgn == 0);
			api.c("Region_delete", region);
			long rect = OS.CreateRectRgn(3, 4, 50, 60);
			region = api.h("Region_new", rect);
			OS.DeleteObject(rect);
			out.add(api.c("Region_IsInfinite", region, g));
			hRgn = api.h("Region_GetHRGN", region, g);
			out.add(regionData(hRgn));
			OS.DeleteObject(hRgn);
			api.c("Region_delete", region);
			api.c("Graphics_delete", g);
		}
	}

	static final String[][] FONTS = {
		{"Segoe UI", "-12", "400", "0", "0", "0"}, {"Arial", "-20", "700", "1", "0", "0"}, {"Courier New", "-15", "400", "0", "1", "1"},
		{"Times New Roman", "-33", "700", "1", "1", "0"}, {"MS Sans Serif", "-11", "400", "0", "0", "0"}, {"Terminal", "-12", "400", "0", "0", "0"},
		{"Fixedsys", "-12", "400", "0", "0", "0"}, {"NoSuchFontXYZ", "-14", "400", "0", "0", "0"}, {"Consolas", "-13", "400", "1", "0", "0"},
	};

	static String familyName(Api api, long family, List<Object> out) {
		char[] name = new char[32];
		out.add(api.c("FontFamily_GetFamilyName", family, name, (char) 0));
		return new String(name).trim();
	}

	static void fonts(Api api, List<Object> out) {
		try (Surface s = new Surface(8, 8)) {
			long g = api.h("Graphics_new", s.dc);
			for (String[] f : FONTS) {
				LOGFONT lf = new LOGFONT();
				lf.lfHeight = Integer.parseInt(f[1]);
				lf.lfWeight = Integer.parseInt(f[2]);
				lf.lfItalic = Byte.parseByte(f[3]);
				lf.lfUnderline = Byte.parseByte(f[4]);
				lf.lfStrikeOut = Byte.parseByte(f[5]);
				f[0].getChars(0, f[0].length(), lf.lfFaceName, 0);
				long hFont = OS.CreateFontIndirect(lf);
				long old = OS.SelectObject(s.dc, hFont);
				long font = api.h("Font_new", s.dc, hFont);
				OS.SelectObject(s.dc, old);
				out.add(font != 0);
				boolean available = (Boolean) api.c("Font_IsAvailable", font);
				out.add(available);
				// the C++ getters return an uninitialized value for a font without a native object
				if (available) out.add(api.c("Font_GetSize", font));
				if (available) out.add(api.c("Font_GetStyle", font));
				long logFont = nativeMemory(92);
				out.add(api.c("Font_GetLogFontW", font, g, logFont));
				byte[] logFontW = new byte[92];
				OS.MoveMemory(logFontW, logFont, 92);
				out.add(logFontW);
				free(logFont);
				long family = api.h("FontFamily_new");
				out.add(api.c("FontFamily_IsAvailable", family));
				out.add(api.c("Font_GetFamily", font, family));
				out.add(api.c("FontFamily_IsAvailable", family));
				out.add(familyName(api, family, out));
				long font2 = api.h("Font_new", family, 17.5f, Gdip.FontStyleBoldItalic, Gdip.UnitPixel);
				boolean available2 = (Boolean) api.c("Font_IsAvailable", font2);
				out.add(available2);
				if (available2) out.add(api.c("Font_GetSize", font2));
				if (available2) out.add(api.c("Font_GetStyle", font2));
				// Path.addString uses the family of Font_GetFamily
				long path = api.h("GraphicsPath_new", Gdip.FillModeAlternate);
				out.add(api.c("GraphicsPath_AddString", path, chars("Hi"), 2, family, 0, 20f, pointF(1, 2), 0L));
				out.add(api.c("GraphicsPath_GetPointCount", path));
				api.c("GraphicsPath_delete", path);
				api.c("Font_delete", font2);
				api.c("FontFamily_delete", family);
				api.c("Font_delete", font);
				OS.DeleteObject(hFont);
				// the fallback of GC.createGdipFont
				long font3 = api.h("Font_new", chars(f[0]), 13f, Gdip.FontStyleItalic, Gdip.UnitPixel, 0L);
				out.add(font3 != 0);
				boolean available3 = (Boolean) api.c("Font_IsAvailable", font3);
				out.add(available3);
				if (available3) out.add(api.c("Font_GetSize", font3));
				if (available3) out.add(api.c("Font_GetStyle", font3));
				long family3 = api.h("FontFamily_new");
				out.add(api.c("Font_GetFamily", font3, family3));
				out.add(familyName(api, family3, out));
				api.c("FontFamily_delete", family3);
				api.c("Font_delete", font3);
				long family4 = api.h("FontFamily_new", chars(f[0]), 0L);
				out.add(api.c("FontFamily_IsAvailable", family4));
				if ((Boolean) api.c("FontFamily_IsAvailable", family4)) out.add(familyName(api, family4, out));
				api.c("FontFamily_delete", family4);
			}
			// an empty family and a null family
			long empty = api.h("FontFamily_new");
			out.add(api.c("FontFamily_IsAvailable", empty));
			api.c("FontFamily_delete", empty);
			long font = api.h("Font_new", 0L, 12f, 0, Gdip.UnitPixel);
			out.add(api.c("Font_IsAvailable", font));
			api.c("Font_delete", font);
			// a private collection, as Device.addFont does
			long collection = api.h("PrivateFontCollection_new");
			out.add(collection != 0);
			out.add(api.c("PrivateFontCollection_AddFontFile", collection, chars(System.getenv("WINDIR") + "\\Fonts\\georgia.ttf")));
			out.add(api.c("PrivateFontCollection_AddFontFile", collection, chars("C:\\no\\such\\font.ttf")));
			long family = api.h("FontFamily_new", chars("Georgia"), collection);
			out.add(api.c("FontFamily_IsAvailable", family));
			out.add(familyName(api, family, out));
			long f2 = api.h("Font_new", family, 20f, 0, Gdip.UnitPixel);
			out.add(api.c("Font_GetSize", f2));
			api.c("Font_delete", f2);
			api.c("FontFamily_delete", family);
			family = api.h("FontFamily_new", chars("Arial"), collection);
			out.add(api.c("FontFamily_IsAvailable", family));
			api.c("FontFamily_delete", family);
			long f3 = api.h("Font_new", chars("Georgia"), 11f, 1, Gdip.UnitPixel, collection);
			out.add(api.c("Font_IsAvailable", f3));
			out.add(api.c("Font_GetSize", f3));
			// GDI+ crashes, with JNI as with FFM, when the family GdipGetFamily returns for a font of a private collection is deleted
			api.c("Font_delete", f3);
			api.c("PrivateFontCollection_delete", collection);
			api.c("Graphics_delete", g);
		}
	}

	static final int[] FORMATS = {Gdip.PixelFormat32bppARGB, Gdip.PixelFormat32bppPARGB, Gdip.PixelFormat32bppRGB, Gdip.PixelFormat24bppRGB, Gdip.PixelFormat16bppRGB565, Gdip.PixelFormat16bppRGB555};

	static void lockBits(Api api, long bitmap, long rect, int format, List<Object> out) {
		long data = api.h("BitmapData_new");
		byte[] zero = new byte[32];
		OS.MoveMemory(zero, data, 32);
		out.add(zero);
		int status = api.i("Bitmap_LockBits", bitmap, rect, 1 /* ImageLockModeRead */, format, data);
		out.add(status);
		if (status == 0) {
			BitmapData bd = new BitmapData();
			api.c("MoveMemory", bd, data);
			out.add(new int[] {bd.Width, bd.Height, bd.Stride, bd.PixelFormat});
			// the pixels only, GDI+ leaves the padding of a converted buffer uninitialized
			int row = bd.Width * (((bd.PixelFormat >> 8) & 0xFF) / 8);
			byte[] bytes = new byte[row * bd.Height], line = new byte[row];
			for (int y = 0; y < bd.Height; y++) {
				OS.MoveMemory(line, bd.Scan0 + (long) y * bd.Stride, row);
				System.arraycopy(line, 0, bytes, y * row, row);
			}
			out.add(bytes);
			out.add(api.c("Bitmap_UnlockBits", bitmap, data));
		}
		api.c("BitmapData_delete", data);
	}

	static void observeImage(Api api, long image, List<Object> out) {
		out.add(api.c("Image_GetWidth", image));
		out.add(api.c("Image_GetHeight", image));
		int format = api.i("Image_GetPixelFormat", image);
		out.add(format);
		int size = api.i("Image_GetPaletteSize", image);
		out.add(size);
		if (size > 0) {
			long palette = nativeMemory(size);
			out.add(api.c("Image_GetPalette", image, palette, size));
			ColorPalette colorPalette = new ColorPalette();
			api.c("MoveMemory", colorPalette, palette, ColorPalette.sizeof);
			out.add(new int[] {colorPalette.Flags, colorPalette.Count, colorPalette.Entries[0]});
			byte[] entries = new byte[size];
			OS.MoveMemory(entries, palette, size);
			out.add(entries);
			free(palette);
		}
		lockBits(api, image, 0, format, out);
		lockBits(api, image, 0, Gdip.PixelFormat32bppARGB, out);
		out.add(api.c("Image_GetLastStatus", image));
	}

	static void bitmaps(Api api, List<Object> out, List<Path> files) {
		int width = 37, height = 23;
		for (int format : FORMATS) {
			int bpp = (format >> 8) & 0xFF;
			int stride = ((width * bpp + 31) / 32) * 4;
			long scan0 = nativeMemory(stride * height);
			byte[] pattern = new byte[stride * height];
			for (int i = 0; i < pattern.length; i++) pattern[i] = (byte) (i * 31 + (i >> 3));
			OS.MoveMemory(scan0, pattern, pattern.length);
			long bitmap = api.h("Bitmap_new", width, height, stride, format, scan0);
			observeImage(api, bitmap, out);
			for (int target : FORMATS) lockBits(api, bitmap, 0, target, out);
			long rect = nativeMemory(16);
			OS.MoveMemory(rect, new int[] {3, 5, 11, 7}, 16);
			lockBits(api, bitmap, rect, Gdip.PixelFormat32bppARGB, out);
			OS.MoveMemory(rect, new int[] {30, 20, 11, 7}, 16);
			lockBits(api, bitmap, rect, Gdip.PixelFormat32bppARGB, out);
			free(rect);
			long clone = api.h("Image_Clone", bitmap);
			observeImage(api, clone, out);
			long[] hbm = new long[1];
			out.add(api.c("Bitmap_GetHBITMAP", bitmap, 0xFF102030, hbm));
			out.add(dibBits(hbm[0]));
			OS.DeleteObject(hbm[0]);
			api.c("Image_delete", clone);
			api.c("Bitmap_delete", bitmap);
			free(scan0);
		}
		// from an HBITMAP, as Image.createGdipImage does
		try (Surface s = new Surface(19, 13)) {
			byte[] pattern = new byte[19 * 13 * 4];
			for (int i = 0; i < pattern.length; i++) pattern[i] = (byte) (i * 7);
			OS.MoveMemory(s.bits, pattern, pattern.length);
			OS.SelectObject(s.dc, s.old);
			long bitmap = api.h("Bitmap_new", s.bitmap, 0L);
			observeImage(api, bitmap, out);
			api.c("Bitmap_delete", bitmap);
			OS.SelectObject(s.dc, s.bitmap);
		}
		// from an HICON, and back
		long hIcon = OS.LoadIcon(0, OS.IDI_APPLICATION);
		long bitmap = api.h("Bitmap_new", hIcon);
		observeImage(api, bitmap, out);
		long[] icon = new long[1];
		out.add(api.c("Bitmap_GetHICON", bitmap, icon));
		observeIcon(icon[0], out);
		OS.DestroyIcon(icon[0]);
		api.c("Bitmap_delete", bitmap);
		// files, as Image.initNative does
		for (Path file : files) {
			for (boolean icm : new boolean[] {false, true}) {
				long b = api.h("Bitmap_new", chars(file.toString()), icm);
				int status = b == 0 ? -1 : api.i("Image_GetLastStatus", b);
				out.add(b != 0 && status == 0);
				if (b != 0 && status == 0) {
					observeImage(api, b, out);
					long[] h = new long[1];
					out.add(api.c("Bitmap_GetHBITMAP", b, 0, h));
					out.add(dibBits(h[0]));
					OS.DeleteObject(h[0]);
					if (file.toString().endsWith(".ico")) {
						long[] hi = new long[1];
						out.add(api.c("Bitmap_GetHICON", b, hi));
						observeIcon(hi[0], out);
						OS.DestroyIcon(hi[0]);
					}
				}
				if (b != 0) api.c("Bitmap_delete", b);
			}
		}
		long missing = api.h("Bitmap_new", chars("C:\\no\\such\\image.png"), false);
		out.add(missing == 0 || api.i("Image_GetLastStatus", missing) != 0);
		if (missing != 0) api.c("Bitmap_delete", missing);
		// a failing call is kept as lastResult until read
		long b = api.h("Bitmap_new", 4, 4, 16, Gdip.PixelFormat32bppARGB, 0L);
		long data = api.h("BitmapData_new");
		out.add(api.c("Bitmap_LockBits", b, 0L, 1, 12345, data));
		out.add(api.c("Image_GetLastStatus", b));
		out.add(api.c("Image_GetLastStatus", b));
		api.c("BitmapData_delete", data);
		api.c("Bitmap_delete", b);
		long point = api.h("Point_new", 17, -4);
		int[] xy = new int[2];
		OS.MoveMemory(xy, point, 8);
		out.add(xy);
		api.c("Point_delete", point);
	}

	static void observeIcon(long hIcon, List<Object> out) {
		ICONINFO info = new ICONINFO();
		OS.GetIconInfo(hIcon, info);
		out.add(info.fIcon);
		out.add(info.hbmColor == 0 ? new byte[0] : dibBits(info.hbmColor));
		out.add(dibBits(info.hbmMask));
		if (info.hbmColor != 0) OS.DeleteObject(info.hbmColor);
		OS.DeleteObject(info.hbmMask);
	}

	/** A 16x16 ARGB test image. */
	static long testImage(Api api, long[] scan0) {
		scan0[0] = nativeMemory(16 * 16 * 4);
		int[] argb = new int[256];
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) argb[y * 16 + x] = ((x * 16) << 24) | ((x * 15) << 16) | ((y * 15) << 8) | ((x ^ y) * 15);
		OS.MoveMemory(scan0[0], argb, argb.length * 4);
		return api.h("Bitmap_new", 16, 16, 64, Gdip.PixelFormat32bppARGB, scan0[0]);
	}

	interface BrushFactory {
		long create(Api api, long image, long path) throws Exception;
	}

	static final Map<String, BrushFactory> BRUSHES = new LinkedHashMap<>();
	static {
		BRUSHES.put("solid", (api, image, path) -> api.h("SolidBrush_new", 0x80FF4020));
		BRUSHES.put("hatch", (api, image, path) -> api.h("HatchBrush_new", 5, 0xFF0000FF, 0x4000FF00));
		BRUSHES.put("hatch2", (api, image, path) -> api.h("HatchBrush_new", 38, 0xFFFF0000, 0xFF000000));
		BRUSHES.put("texture", (api, image, path) -> {
			long brush = api.h("TextureBrush_new", image, Gdip.WrapModeTileFlipXY, 2f, 3f, 11f, 9f);
			api.c("TextureBrush_ScaleTransform", brush, 1.5f, 0.75f, Gdip.MatrixOrderAppend);
			api.c("TextureBrush_TranslateTransform", brush, 3f, 1f, Gdip.MatrixOrderPrepend);
			return brush;
		});
		BRUSHES.put("texture transform", (api, image, path) -> {
			long brush = api.h("TextureBrush_new", image, Gdip.WrapModeTile, 0f, 0f, 16f, 16f);
			long m = api.h("Matrix_new", 0.8f, 0.3f, -0.3f, 0.8f, 1f, 2f);
			api.c("TextureBrush_SetTransform", brush, m);
			api.c("Matrix_delete", m);
			return brush;
		});
		BRUSHES.put("texture reset", (api, image, path) -> {
			long brush = api.h("TextureBrush_new", image, Gdip.WrapModeTile, 0f, 0f, 16f, 16f);
			api.c("TextureBrush_ScaleTransform", brush, 3f, 3f, Gdip.MatrixOrderAppend);
			api.c("TextureBrush_ResetTransform", brush);
			return brush;
		});
		BRUSHES.put("texture attributes", (api, image, path) -> {
			long attrib = api.h("ImageAttributes_new");
			api.c("ImageAttributes_SetWrapMode", attrib, Gdip.WrapModeTile);
			float[] matrix = {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0.4f, 0, 0, 0, 0, 0, 1};
			api.c("ImageAttributes_SetColorMatrix", attrib, matrix, Gdip.ColorMatrixFlagsDefault, Gdip.ColorAdjustTypeBitmap);
			Rect rect = new Rect();
			rect.Width = 12;
			rect.Height = 10;
			long brush = api.h("TextureBrush_new", image, rect, attrib);
			api.c("ImageAttributes_delete", attrib);
			return brush;
		});
		BRUSHES.put("linear", (api, image, path) -> {
			long brush = api.h("LinearGradientBrush_new", pointF(2, 3), pointF(40, 30), 0xFFFF0000, 0x800000FF);
			api.c("LinearGradientBrush_SetInterpolationColors", brush, new int[] {0xFFFF0000, 0x8000FF00, 0x800000FF}, new float[] {0, 0.3f, 1}, 3);
			api.c("LinearGradientBrush_SetWrapMode", brush, Gdip.WrapModeTileFlipX);
			api.c("LinearGradientBrush_ScaleTransform", brush, 0.5f, 1.25f, Gdip.MatrixOrderAppend);
			api.c("LinearGradientBrush_TranslateTransform", brush, 4f, -2f, Gdip.MatrixOrderAppend);
			return brush;
		});
		BRUSHES.put("linear reset", (api, image, path) -> {
			long brush = api.h("LinearGradientBrush_new", pointF(0, 0), pointF(20, 0), 0xFF00FF00, 0xFF000000);
			api.c("LinearGradientBrush_ScaleTransform", brush, 3f, 1f, Gdip.MatrixOrderAppend);
			api.c("LinearGradientBrush_ResetTransform", brush);
			return brush;
		});
		BRUSHES.put("path gradient", (api, image, path) -> {
			long brush = api.h("PathGradientBrush_new", path);
			api.c("PathGradientBrush_SetCenterColor", brush, 0xFFFFFF00);
			api.c("PathGradientBrush_SetCenterPoint", brush, pointF(20, 25));
			api.c("PathGradientBrush_SetWrapMode", brush, Gdip.WrapModeClamp);
			return brush;
		});
		BRUSHES.put("path gradient blend", (api, image, path) -> {
			long brush = api.h("PathGradientBrush_new", path);
			api.c("PathGradientBrush_SetInterpolationColors", brush, new int[] {0xFF0000FF, 0xFFFF0000, 0xFF00FF00}, new float[] {0, 0.5f, 1}, 3);
			return brush;
		});
		BRUSHES.put("clone", (api, image, path) -> {
			long original = api.h("HatchBrush_new", 12, 0xFF123456, 0xFFFFFFFF);
			long brush = api.h("Brush_Clone", original);
			api.c("HatchBrush_delete", original);
			return brush;
		});
	}

	static void brushes(Api api, List<Object> out) throws Exception {
		long[] scan0 = new long[1];
		long image = testImage(api, scan0);
		long path = api.h("GraphicsPath_new", Gdip.FillModeAlternate);
		api.c("GraphicsPath_AddArc", path, 2f, 4f, 44f, 40f, 0f, 360f);
		for (Map.Entry<String, BrushFactory> entry : BRUSHES.entrySet()) {
			long brush = entry.getValue().create(api, image, path);
			out.add(entry.getKey());
			out.add(api.c("Brush_GetType", brush));
			out.add(render(api, 48, 48, (a, g) -> {
				out.add(a.c("Graphics_FillRectangle", g, brush, 0, 0, 48, 24));
				out.add(a.c("Graphics_FillPath", g, brush, path));
			}));
			long pen = api.h("Pen_new", brush, 4.5f);
			long penBrush = api.h("Pen_GetBrush", pen);
			out.add(penBrush != 0);
			if (penBrush != 0) {
				out.add(api.c("Brush_GetType", penBrush));
				api.c("SolidBrush_delete", penBrush);
			}
			out.add(render(api, 48, 48, (a, g) -> out.add(a.c("Graphics_DrawPath", g, pen, path))));
			api.c("Pen_delete", pen);
			if (entry.getKey().startsWith("texture")) {
				long textureImage = api.h("TextureBrush_GetImage", brush);
				out.add(api.c("Image_GetWidth", textureImage));
				out.add(api.c("Image_GetHeight", textureImage));
				lockBits(api, textureImage, 0, Gdip.PixelFormat32bppARGB, out);
				api.c("Image_delete", textureImage);
			}
			int type = api.i("Brush_GetType", brush);
			switch (type) {
				case Gdip.BrushTypeSolidColor -> api.c("SolidBrush_delete", brush);
				case Gdip.BrushTypeHatchFill -> api.c("HatchBrush_delete", brush);
				case Gdip.BrushTypeTextureFill -> api.c("TextureBrush_delete", brush);
				case Gdip.BrushTypeLinearGradient -> api.c("LinearGradientBrush_delete", brush);
				default -> api.c("PathGradientBrush_delete", brush);
			}
		}
		// surround colors check the count against the points of the path
		out.add("surround colors");
		long brush = api.h("PathGradientBrush_new", path);
		int points = api.i("GraphicsPath_GetPointCount", path);
		for (int n : new int[] {1, 3, points, points + 1, 0}) {
			int[] count = {n};
			int[] colors = new int[Math.max(n, 1)];
			for (int i = 0; i < colors.length; i++) colors[i] = 0xFF000000 | (i * 0x1F3D5B);
			out.add(api.c("PathGradientBrush_SetSurroundColors", brush, colors, count));
			out.add(count[0]);
		}
		out.add(api.c("PathGradientBrush_SetSurroundColors", brush, null, new int[] {1}));
		out.add(api.c("PathGradientBrush_SetCenterColor", brush, 0xFF00FFFF));
		long path2 = api.h("GraphicsPath_new", Gdip.FillModeAlternate);
		api.c("GraphicsPath_AddLine", path2, 0f, 0f, 40f, 5f);
		api.c("GraphicsPath_AddLine", path2, 40f, 5f, 10f, 45f);
		out.add(api.c("PathGradientBrush_SetGraphicsPath", brush, path2));
		out.add(api.c("PathGradientBrush_SetGraphicsPath", brush, 0L));
		out.add(render(api, 48, 48, (a, g) -> out.add(a.c("Graphics_FillRectangle", g, brush, 0, 0, 48, 48))));
		api.c("PathGradientBrush_delete", brush);
		api.c("GraphicsPath_delete", path2);
		// pens
		long solid = api.h("SolidBrush_new", 0xC0203040);
		long other = api.h("SolidBrush_new", 0xFF20A040);
		String[] setups = {"dash", "pattern", "caps", "join", "width", "brush"};
		for (String setup : setups) {
			long pen = api.h("Pen_new", solid, 3f);
			out.add("pen " + setup);
			out.add(switch (setup) {
				case "dash" -> api.c("Pen_SetDashStyle", pen, Gdip.DashStyleDashDot);
				case "pattern" -> {
					api.c("Pen_SetDashStyle", pen, Gdip.DashStyleCustom);
					float[] dashes = {3, 1, 0.5f, 2};
					Object s = api.c("Pen_SetDashPattern", pen, dashes, 4);
					api.c("Pen_SetDashOffset", pen, 1.5f);
					yield s;
				}
				case "caps" -> api.c("Pen_SetLineCap", pen, Gdip.LineCapRound, Gdip.LineCapSquare, Gdip.DashCapTriangle);
				case "join" -> {
					api.c("Pen_SetLineJoin", pen, Gdip.LineJoinMiter);
					yield api.c("Pen_SetMiterLimit", pen, 1.2f);
				}
				case "width" -> api.c("Pen_SetWidth", pen, 7.25f);
				default -> api.c("Pen_SetBrush", pen, other);
			});
			long zig = api.h("GraphicsPath_new", Gdip.FillModeAlternate);
			api.c("GraphicsPath_AddLine", zig, 4f, 4f, 40f, 10f);
			api.c("GraphicsPath_AddLine", zig, 40f, 10f, 8f, 30f);
			api.c("GraphicsPath_AddLine", zig, 8f, 30f, 44f, 44f);
			out.add(render(api, 48, 48, (a, g) -> out.add(a.c("Graphics_DrawPath", g, pen, zig))));
			api.c("GraphicsPath_delete", zig);
			api.c("Pen_delete", pen);
		}
		api.c("SolidBrush_delete", other);
		// string formats
		long font = api.h("Font_new", chars("Segoe UI"), 13f, 0, Gdip.UnitPixel, 0L);
		for (boolean typographic : new boolean[] {true, false}) {
			long format = api.h("StringFormat_Clone", api.h(typographic ? "StringFormat_GenericTypographic" : "StringFormat_GenericDefault"));
			out.add("string format " + (typographic ? "typographic" : "default"));
			out.add(api.c("StringFormat_GetFormatFlags", format));
			out.add(api.c("StringFormat_SetFormatFlags", format, api.i("StringFormat_GetFormatFlags", format) | Gdip.StringFormatFlagsMeasureTrailingSpaces));
			out.add(api.c("StringFormat_GetFormatFlags", format));
			out.add(api.c("StringFormat_SetHotkeyPrefix", format, Gdip.HotkeyPrefixShow));
			float[] tabs = {24, 16};
			out.add(api.c("StringFormat_SetTabStops", format, 4f, 2, tabs));
			char[] text = chars("&File\tA\tB&x");
			out.add(render(api, 96, 24, (a, g) -> out.add(a.c("Graphics_DrawString", g, text, text.length - 1, font, pointF(1, 2), format, solid))));
			api.c("StringFormat_delete", format);
		}
		api.c("Font_delete", font);
		out.add("image attributes");
		long attrib = api.h("ImageAttributes_new");
		out.add(api.c("ImageAttributes_SetWrapMode", attrib, Gdip.WrapModeTileFlipXY));
		float[] matrix = {0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0.5f, 0, 0.1f, 0, 0, 0, 1};
		out.add(api.c("ImageAttributes_SetColorMatrix", attrib, matrix, Gdip.ColorMatrixFlagsDefault, Gdip.ColorAdjustTypeBitmap));
		out.add(render(api, 48, 48, (a, g) -> {
			Rect dest = new Rect();
			dest.X = 1; dest.Y = 2; dest.Width = 45; dest.Height = 40;
			out.add(a.c("Graphics_DrawImage", g, image, dest, 0, 0, 16, 16, Gdip.UnitPixel, attrib, 0L, 0L));
		}));
		api.c("ImageAttributes_delete", attrib);
		api.c("SolidBrush_delete", solid);
		api.c("GraphicsPath_delete", path);
		api.c("Bitmap_delete", image);
		free(scan0[0]);
	}

	public static void main(String[] args) throws Exception {
		GdiplusStartupInput input = new GdiplusStartupInput();
		input.GdiplusVersion = 1;
		long[] token = new long[1];
		if (Gdip.GdiplusStartup(token, input, 0) != 0) throw new IllegalStateException("GdiplusStartup");
		screenDC = OS.GetDC(0);
		List<Path> files = new ArrayList<>();
		for (String dir : args) {
			try (Stream<Path> tree = Files.walk(Paths.get(dir))) {
				tree.filter(p -> p.toString().toLowerCase().matches(".*\\.(bmp|gif|png|jpg|ico)")).sorted().forEach(files::add);
			}
		}
		System.out.println("Images: " + files.size());
		compare("sizes", (api, out) -> sizes(api, out));
		compare("matrices", (api, out) -> matrices(api, out));
		compare("paths", (api, out) -> paths(api, out));
		compare("regions", (api, out) -> regions(api, out));
		compare("fonts", (api, out) -> fonts(api, out));
		compare("bitmaps", (api, out) -> bitmaps(api, out, files));
		compare("brushes, pens, string formats, image attributes", (api, out) -> brushes(api, out));
		OS.ReleaseDC(0, screenDC);
		Gdip.GdiplusShutdown(token[0]);
		System.out.println(checks + " checks, " + failures + " mismatches");
		System.exit(failures == 0 ? 0 : 1);
	}
}
