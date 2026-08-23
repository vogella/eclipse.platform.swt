/*******************************************************************************
 * Copyright (c) 2026 SWT Visual Oracle contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * T01 capture strategy spike. Compares Control.print(GC), GC.copyArea(Image)
 * and an X11 root grab (ImageMagick import, cropped to widget bounds) for
 * capturing SWT widgets as PNG.
 *
 * Modes:
 *   full   <outDir> <tag> [--skia] : measurements, writes PNGs + RESULT lines
 *   single <outDir> <tag> [--skia] : one capture per strategy, HASH lines
 */
public class CaptureSpike {

	private static final int TOL = 20;
	private static final int EDGE_BAND = 4;
	private static final int TIMING_RUNS = 60;
	private static final int DET_RUNS = 5;

	private interface Shot {
		ImageData capture(Control c) throws Exception;
	}

	private static Display display;
	private static int zoom = 100;
	private static File grabTarget;

	public static void main(String[] args) throws Exception {
		String mode = args[0];
		File outDir = new File(args[1]);
		String tag = args[2];
		boolean skia = args.length > 3 && "--skia".equals(args[3]);
		outDir.mkdirs();

		display = new Display();
		try {
			run(mode, outDir, tag, skia);
		} finally {
			display.dispose();
		}
	}

	private static Shell shell;
	private static Button button;
	private static Canvas glCanvas;

	private static void run(String mode, File outDir, String tag, boolean skia) throws Exception {
		Color red = new Color(display, 255, 0, 0);
		Color green = new Color(display, 0, 200, 0);
		Color blue = new Color(display, 40, 80, 255);
		Color magenta = new Color(display, 255, 0, 255);

		shell = new Shell(display);
		shell.setText("capture-spike");
		button = new Button(shell, SWT.PUSH);
		button.setText("Oracle button");
		button.setBounds(60, 50, 220, 64);
		if (skia) {
			glCanvas = new Canvas(shell, SWT.SKIA | SWT.BORDER);
			glCanvas.setBounds(60, 150, 300, 170);
			Canvas canvasRef = glCanvas;
			canvasRef.addPaintListener(e -> {
				GC g = e.gc;
				Rectangle ca = canvasRef.getClientArea();
				g.setBackground(red);
				g.fillRectangle(10, 10, ca.width - 20, ca.height / 2 - 10);
				g.setBackground(green);
				g.fillOval(ca.width / 4, ca.height / 2, ca.width / 2, ca.height / 2 - 16);
				g.setForeground(blue);
				g.setLineWidth(7);
				g.drawLine(10, ca.height - 12, ca.width - 10, ca.height / 2 + 6);
			});
		}
		shell.addPaintListener(e -> {
			e.gc.setBackground(magenta);
			drawMarkers(e.gc, button.getBounds());
			if (glCanvas != null) {
				drawMarkers(e.gc, glCanvas.getBounds());
			}
		});

		boolean[] painted = { false, false, !skia };
		shell.addListener(SWT.Paint, e -> painted[0] = true);
		button.addListener(SWT.Paint, e -> painted[1] = true);
		if (glCanvas != null) {
			glCanvas.addListener(SWT.Paint, e -> painted[2] = true);
		}
		shell.open();
		display.timerExec(8000, () -> {});
		waitUntil(() -> painted[0] && painted[1] && painted[2], 8000);
		pump(400);
		shell.update();
		zoom = DPIUtil.getDeviceZoom();
		grabTarget = new File(outDir, "grab-root.png");

		Map<String, Shot> strategies = new HashMap<>();
		strategies.put("print", CaptureSpike::viaPrint);
		strategies.put("copyarea", CaptureSpike::viaCopyArea);
		strategies.put("xgrab", CaptureSpike::viaXGrab);
		strategies.put("xgrab-crop", CaptureSpike::viaXGrabCrop);

		if ("single".equals(mode)) {
			for (String name : ORDER) {
				ImageData d = strategies.get(name).capture(button);
				File f = new File(outDir, tag + "-" + name + ".png");
				writePng(d, f);
				System.out.println("HASH strategy=" + name + " sha256=" + sha(png(d)));
			}
			return;
		}

		for (String name : ORDER) {
			Shot shot = strategies.get(name);

			List<ImageData> dets = new ArrayList<>();
			for (int i = 0; i < DET_RUNS; i++) {
				dets.add(shot.capture(button));
			}
			writePng(dets.get(DET_RUNS - 1), new File(outDir, tag + "-" + name + "-button.png"));
			double ms = timeMs(shot, button, TIMING_RUNS);
			emit(tag, name, "button", button.getBounds(), dets.get(DET_RUNS - 1),
					false, ms, detLabel(dets));

			if (glCanvas != null) {
				ImageData cap = shot.capture(glCanvas);
				writePng(cap, new File(outDir, tag + "-" + name + "-skiacanvas.png"));
				emit(tag, name, "skiacanvas", glCanvas.getBounds(), cap,
						true, -1, "n/a");
			}

			if (name.equals("xgrab")) {
				ImageData fast = strategies.get("xgrab-crop").capture(button);
				double fastMs = timeMs(strategies.get("xgrab-crop"), button, TIMING_RUNS);
				emit(tag, "xgrab-crop", "button", button.getBounds(), fast,
						false, fastMs, "n/a");
			}
		}
	}

	private static final String[] ORDER = { "print", "copyarea", "xgrab" };

	private static void drawMarkers(GC g, Rectangle b) {
		int m = 6, gap = 3;
		g.fillRectangle(b.x, b.y - gap - m, b.width, m);
		g.fillRectangle(b.x, b.y + b.height + gap, b.width, m);
		g.fillRectangle(b.x - gap - m, b.y, m, b.height);
		g.fillRectangle(b.x + b.width + gap, b.y, m, b.height);
	}

	private static void emit(String tag, String strategy, String control, Rectangle bounds,
			ImageData got, boolean glExpected, double avgMs, String det) {
		int scaleNum = Math.round(bounds.width * zoom / 100.0f);
		int expW = scaleNum;
		int expH = Math.round(bounds.height * zoom / 100.0f);
		Metrics mx = analyze(got);
		System.out.println("RESULT tag=" + tag
				+ " strategy=" + strategy
				+ " control=" + control
				+ " avgMs=" + String.format("%.2f", avgMs)
				+ " expW=" + expW + " expH=" + expH
				+ " gotW=" + got.width + " gotH=" + got.height
				+ " sizeMatch=" + (got.width == expW && got.height == expH)
				+ " markerTop=" + mx.markerTop + " markerBottom=" + mx.markerBottom
				+ " markerLeft=" + mx.markerLeft + " markerRight=" + mx.markerRight
				+ " uniqColors=" + mx.uniqueColors
				+ " domColorMilli=" + mx.domColorMilli
				+ " glMilli=" + mx.glMilli
				+ " det=" + det);
	}

	private static ImageData viaPrint(Control c) {
		Rectangle b = c.getBounds();
		Image img = new Image(display, b.width, b.height);
		try {
			GC gc = new GC(img);
			try {
				c.print(gc);
			} finally {
				gc.dispose();
			}
			return img.getImageData(zoom);
		} finally {
			img.dispose();
		}
	}

	private static ImageData viaCopyArea(Control c) {
		Rectangle b = c.getBounds();
		Image img = new Image(display, b.width, b.height);
		try {
			GC gc = new GC(c);
			try {
				gc.copyArea(img, 0, 0);
			} finally {
				gc.dispose();
			}
			return img.getImageData(zoom);
		} finally {
			img.dispose();
		}
	}

	private static ImageData viaXGrab(Control c) throws Exception {
		Process p = new ProcessBuilder("import", "-window", "root",
				grabTarget.getAbsolutePath()).inheritIO().start();
		int exit = p.waitFor();
		if (exit != 0) {
			throw new IllegalStateException("import -window root failed, exit " + exit);
		}
		ImageData full = new ImageData(grabTarget.getAbsolutePath());
		Point origin = c.toDisplay(0, 0);
		Rectangle b = c.getBounds();
		double s = zoom / 100.0;
		int sx = (int) Math.round(origin.x * s);
		int sy = (int) Math.round(origin.y * s);
		int w = (int) Math.round(b.width * s);
		int h = (int) Math.round(b.height * s);
		ImageData out = new ImageData(w, h, full.depth, full.palette);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				out.setPixel(x, y, full.getPixel(sx + x, sy + y));
			}
		}
		return out;
	}

	private static ImageData viaXGrabCrop(Control c) throws Exception {
		Point origin = c.toDisplay(0, 0);
		Rectangle b = c.getBounds();
		double s = zoom / 100.0;
		int x = (int) Math.round(origin.x * s);
		int y = (int) Math.round(origin.y * s);
		int w = (int) Math.round(b.width * s);
		int h = (int) Math.round(b.height * s);
		File f = new File(grabTarget.getParentFile(), "grab-crop.png");
		Process p = new ProcessBuilder("import", "-window", "root",
				"-crop", w + "x" + h + "+" + x + "+" + y,
				f.getAbsolutePath()).inheritIO().start();
		int exit = p.waitFor();
		if (exit != 0) {
			throw new IllegalStateException("import -window root -crop failed, exit " + exit);
		}
		return new ImageData(f.getAbsolutePath());
	}

	private static class Metrics {
		int markerTop, markerBottom, markerLeft, markerRight;
		int uniqueColors;
		int domColorMilli;
		int glMilli;
	}

	private static Metrics analyze(ImageData d) {
		Metrics mx = new Metrics();
		Map<Integer, Integer> counts = new HashMap<>();
		int total = 0;
		int gl = 0;
		for (int y = 0; y < d.height; y++) {
			for (int x = 0; x < d.width; x++) {
				RGB rgb = d.palette.getRGB(d.getPixel(x, y));
				int key = (rgb.red << 16) | (rgb.green << 8) | rgb.blue;
				counts.merge(key, 1, Integer::sum);
				boolean isMarker = near(rgb.red, 255) && near(rgb.green, 0) && near(rgb.blue, 255);
				if (isMarker) {
					if (y < EDGE_BAND) mx.markerTop++;
					else if (y >= d.height - EDGE_BAND) mx.markerBottom++;
					else if (x < EDGE_BAND) mx.markerLeft++;
					else if (x >= d.width - EDGE_BAND) mx.markerRight++;
				}
				boolean glRed = near(rgb.red, 255) && near(rgb.green, 0) && near(rgb.blue, 0);
				boolean glGreen = near(rgb.red, 0) && near(rgb.green, 200) && near(rgb.blue, 0);
				boolean glBlue = near(rgb.red, 40) && near(rgb.green, 80) && near(rgb.blue, 255);
				if (glRed || glGreen || glBlue) gl++;
				total++;
			}
		}
		mx.uniqueColors = counts.size();
		int max = counts.values().stream().max(Integer::compare).orElse(0);
		mx.domColorMilli = total == 0 ? 0 : max * 1000 / total;
		mx.glMilli = total == 0 ? 0 : gl * 1000 / total;
		return mx;
	}

	private static boolean near(int v, int t) {
		return Math.abs(v - t) <= TOL;
	}

	private static String detLabel(List<ImageData> shots) {
		byte[] ref = png(shots.get(0));
		for (int i = 1; i < shots.size(); i++) {
			byte[] other = png(shots.get(i));
			if (!MessageDigest.isEqual(ref, other)) {
				String where = diff(shots.get(0), shots.get(i));
				return "MISMATCH(" + where + ")";
			}
		}
		return "IDENTICAL";
	}

	private static String diff(ImageData a, ImageData b) {
		if (a.width != b.width || a.height != b.height) {
			return "size " + a.width + "x" + a.height + " vs " + b.width + "x" + b.height;
		}
		int count = 0;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
		int maxX = -1, maxY = -1;
		for (int y = 0; y < a.height; y++) {
			for (int x = 0; x < a.width; x++) {
				RGB ra = a.palette.getRGB(a.getPixel(x, y));
				RGB rb = b.palette.getRGB(b.getPixel(x, y));
				if (ra.red != rb.red || ra.green != rb.green || ra.blue != rb.blue) {
					count++;
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return count + "px bbox=" + minX + "," + minY + ".." + maxX + "," + maxY;
	}

	private static double timeMs(Shot shot, Control c, int n) throws Exception {
		for (int i = 0; i < 5; i++) {
			shot.capture(c);
		}
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			shot.capture(c);
		}
		return (System.nanoTime() - t0) / 1e6 / n;
	}

	private static byte[] png(ImageData d) {
		ImageLoader loader = new ImageLoader();
		loader.data = new ImageData[] { d };
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		loader.save(bos, SWT.IMAGE_PNG);
		return bos.toByteArray();
	}

	private static void writePng(ImageData d, File f) throws Exception {
		try (FileOutputStream fos = new FileOutputStream(f)) {
			ImageLoader loader = new ImageLoader();
			loader.data = new ImageData[] { d };
			loader.save(fos, SWT.IMAGE_PNG);
		}
	}

	private static String sha(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		try {
			for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
				sb.append(String.format("%02x", b));
			}
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		return sb.toString();
	}

	private static void waitUntil(java.util.function.BooleanSupplier cond, long timeoutMs) {
		long end = System.currentTimeMillis() + timeoutMs;
		display.timerExec((int) timeoutMs, () -> {});
		while (!cond.getAsBoolean() && System.currentTimeMillis() < end) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	private static void pump(long ms) {
		long end = System.currentTimeMillis() + ms;
		display.timerExec((int) ms, () -> {});
		while (System.currentTimeMillis() < end) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}
