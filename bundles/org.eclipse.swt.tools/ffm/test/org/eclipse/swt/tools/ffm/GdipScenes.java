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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.widgets.*;

/**
 * Renders GC drawing in advanced (GDI+) mode into images and writes their pixels and the values GC
 * reports, so that a JNI and an FFM build can be compared pixel for pixel.
 *
 * <pre>
 * GdipScenes render &lt;outDir&gt;
 * GdipScenes compare &lt;dirA&gt; &lt;dirB&gt;
 * </pre>
 */
public class GdipScenes {

	static final int SIZE = 320;

	record Scene(String name, BiConsumer<GC, Device> draw) {
	}

	static final List<String> values = new ArrayList<>();

	static void log(String scene, String key, Object value) {
		values.add(scene + "." + key + " = " + value);
	}

	public static void main(String[] args) throws Exception {
		if (args[0].equals("compare")) {
			System.exit(compare(Paths.get(args[1]), Paths.get(args[2])));
		}
		java.nio.file.Path out = Paths.get(args[1]);
		Files.createDirectories(out);
		Display display = new Display();
		try {
			// the first pass warms the font caches, whose first use in a process can render a few glyph pixels differently
			render(display, null);
			values.clear();
			render(display, out);
			Files.write(out.resolve("values.txt"), values);
			System.out.println("GdipScenes: rendered " + values.size() + " values into " + out);
		} finally {
			display.dispose();
		}
	}

	static void render(Display display, java.nio.file.Path out) throws IOException {
		for (Scene scene : scenes(display)) {
			for (int antialias : new int[] { SWT.DEFAULT, SWT.ON, SWT.OFF }) {
				String name = scene.name + "-aa" + antialias;
				Image image = new Image(display, SIZE, SIZE);
				GC gc = new GC(image);
				try {
					gc.setAdvanced(true);
					if (antialias != SWT.DEFAULT) {
						gc.setAntialias(antialias);
						gc.setTextAntialias(antialias);
					}
					gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
					gc.fillRectangle(0, 0, SIZE, SIZE);
					scene.draw.accept(gc, display);
					log(name, "advanced", gc.getAdvanced());
					log(name, "antialias", gc.getAntialias());
					log(name, "textAntialias", gc.getTextAntialias());
					log(name, "interpolation", gc.getInterpolation());
					log(name, "clipping", gc.getClipping());
					log(name, "isClipped", gc.isClipped());
					Transform t = new Transform(display);
					gc.getTransform(t);
					float[] e = new float[6];
					t.getElements(e);
					t.dispose();
					log(name, "transform", Arrays.toString(e));
				} finally {
					gc.dispose();
				}
				ImageData data = image.getImageData();
				image.dispose();
				if (out == null) continue;
				try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out.resolve(name + ".px"))))) {
					for (int y = 0; y < data.height; y++) {
						for (int x = 0; x < data.width; x++) {
							o.writeInt(data.getPixel(x, y));
						}
					}
				}
			}
		}
	}

	static int compare(java.nio.file.Path a, java.nio.file.Path b) throws IOException {
		int failures = 0, scenes = 0;
		try (var files = Files.list(a)) {
			for (var fa : files.sorted().toList()) {
				String name = fa.getFileName().toString();
				if (!name.endsWith(".px")) continue;
				scenes++;
				byte[] pa = Files.readAllBytes(fa);
				var fb = b.resolve(name);
				if (!Files.exists(fb)) {
					System.out.println("MISSING " + name);
					failures++;
					continue;
				}
				byte[] pb = Files.readAllBytes(fb);
				int diff = 0, x0 = SIZE, y0 = SIZE, x1 = -1, y1 = -1;
				for (int i = 0; i + 3 < Math.min(pa.length, pb.length); i += 4) {
					if (pa[i] != pb[i] || pa[i + 1] != pb[i + 1] || pa[i + 2] != pb[i + 2] || pa[i + 3] != pb[i + 3]) {
						diff++;
						int x = (i / 4) % SIZE, y = (i / 4) / SIZE;
						x0 = Math.min(x0, x);
						y0 = Math.min(y0, y);
						x1 = Math.max(x1, x);
						y1 = Math.max(y1, y);
					}
				}
				if (diff != 0 || pa.length != pb.length) {
					System.out.println("DIFF " + name + ": " + diff + " pixels in " + x0 + "," + y0 + " - " + x1 + "," + y1);
					failures++;
				}
			}
		}
		List<String> va = Files.readAllLines(a.resolve("values.txt")), vb = Files.readAllLines(b.resolve("values.txt"));
		for (int i = 0; i < Math.max(va.size(), vb.size()); i++) {
			String x = i < va.size() ? va.get(i) : "<none>", y = i < vb.size() ? vb.get(i) : "<none>";
			if (!x.equals(y)) {
				System.out.println("VALUE " + x + "  <>  " + y);
				failures++;
			}
		}
		System.out.println("GdipScenes: " + scenes + " images, " + va.size() + " values, " + failures + " mismatches");
		return failures == 0 ? 0 : 1;
	}

	static Image testImage(Device device, boolean alpha) {
		ImageData data = new ImageData(40, 30, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
		for (int y = 0; y < 30; y++) {
			for (int x = 0; x < 40; x++) {
				data.setPixel(x, y, ((x * 6) << 16) | ((y * 8) << 8) | ((x + y) * 3));
				if (alpha) data.setAlpha(x, y, (x * 255) / 39);
			}
		}
		return new Image(device, data);
	}

	static List<Scene> scenes(Display display) {
		Color red = display.getSystemColor(SWT.COLOR_RED);
		Color blue = display.getSystemColor(SWT.COLOR_BLUE);
		Color green = display.getSystemColor(SWT.COLOR_DARK_GREEN);
		Color black = display.getSystemColor(SWT.COLOR_BLACK);
		List<Scene> list = new ArrayList<>();
		list.add(new Scene("lines", (gc, d) -> {
			gc.setForeground(black);
			gc.drawLine(10, 10, 300, 40);
			gc.setLineWidth(5);
			gc.setLineCap(SWT.CAP_ROUND);
			gc.setForeground(red);
			gc.drawLine(10, 30, 300, 90);
			gc.setLineStyle(SWT.LINE_DASHDOT);
			gc.setLineWidth(3);
			gc.drawPolyline(new int[] { 10, 100, 60, 150, 110, 100, 160, 170, 210, 110 });
			gc.setLineDash(new int[] { 7, 3, 2, 3 });
			gc.setLineJoin(SWT.JOIN_BEVEL);
			gc.setForeground(blue);
			gc.drawPolygon(new int[] { 20, 200, 120, 190, 90, 300, 30, 260 });
			gc.setLineStyle(SWT.LINE_SOLID);
			gc.setLineWidth(1);
			gc.drawRectangle(150, 190, 60, 40);
			gc.drawOval(220, 190, 80, 50);
			gc.drawArc(150, 240, 70, 60, 30, 250);
			gc.drawRoundRectangle(230, 250, 70, 50, 20, 15);
			gc.drawPoint(5, 5);
			gc.drawFocus(240, 10, 60, 20);
			log("lines", "lineWidth", gc.getLineWidth());
		}));
		list.add(new Scene("fills", (gc, d) -> {
			gc.setBackground(red);
			gc.fillRectangle(10, 10, 80, 50);
			gc.setBackground(blue);
			gc.fillOval(100, 10, 90, 60);
			gc.setBackground(green);
			gc.fillArc(200, 10, 100, 80, 45, 270);
			gc.fillArc(200, 100, 100, 80, 0, -120);
			gc.setFillRule(SWT.FILL_EVEN_ODD);
			gc.fillPolygon(new int[] { 20, 100, 160, 110, 30, 200, 100, 70, 150, 200 });
			gc.setFillRule(SWT.FILL_WINDING);
			gc.fillPolygon(new int[] { 20, 210, 160, 220, 30, 310, 100, 180, 150, 310 });
			gc.setBackground(red);
			gc.fillRoundRectangle(180, 200, 120, 50, 30, 20);
			gc.setForeground(blue);
			gc.fillGradientRectangle(180, 260, 120, 50, false);
			gc.fillGradientRectangle(10, 60, 50, 30, true);
		}));
		list.add(new Scene("paths", (gc, d) -> {
			Path path = new Path(d);
			path.moveTo(20, 20);
			path.lineTo(150, 40);
			path.cubicTo(200, 80, 60, 150, 280, 200);
			path.quadTo(150, 300, 30, 250);
			path.addArc(40, 60, 80, 80, 0, 300);
			path.addRectangle(200, 20, 80, 40);
			Font font = new Font(d, "Arial", 28, SWT.BOLD);
			path.addString("FFM", 150, 230, font);
			path.close();
			gc.setBackground(green);
			gc.fillPath(path);
			gc.setForeground(red);
			gc.setLineWidth(2);
			gc.drawPath(path);
			log("paths", "contains", path.contains(60, 100, gc, false) + "/" + path.contains(150, 40, gc, true));
			float[] bounds = new float[4];
			path.getBounds(bounds);
			log("paths", "bounds", Arrays.toString(bounds));
			font.dispose();
			path.dispose();
		}));
		list.add(new Scene("text", (gc, d) -> {
			gc.setForeground(black);
			gc.drawString("Hello FFM, GDI+ text", 10, 10);
			gc.drawString("transparent", 10, 30, true);
			gc.setBackground(d.getSystemColor(SWT.COLOR_YELLOW));
			gc.drawText("tab\tand\nnew line &mnemonic", 10, 50, SWT.DRAW_DELIMITER | SWT.DRAW_TAB | SWT.DRAW_MNEMONIC);
			log("text", "extent", gc.textExtent("tab\tand\nnew line", SWT.DRAW_DELIMITER | SWT.DRAW_TAB));
			log("text", "stringExtent", gc.stringExtent("Hello FFM"));
			log("text", "fontMetrics", gc.getFontMetrics().getHeight() + "/" + gc.getFontMetrics().getAscent());
			// underlined fonts and missing glyphs take the Graphics_DrawString path
			Font underline = new Font(d, new FontData[] { withStyle(d.getSystemFont().getFontData()[0], true) });
			gc.setFont(underline);
			gc.setForeground(blue);
			gc.drawText("underlined\tstring &path", 10, 100, SWT.DRAW_TAB | SWT.DRAW_MNEMONIC | SWT.DRAW_TRANSPARENT);
			gc.drawText("opaque underlined", 10, 125);
			log("text", "underlineExtent", gc.textExtent("underlined\tstring", SWT.DRAW_TAB));
			gc.setFont(null);
			underline.dispose();
			gc.drawText("mixed 漢字 مرحبا 😀", 10, 150, true);
			log("text", "mixedExtent", gc.textExtent("mixed 漢字 😀"));
			Font big = new Font(d, "Times New Roman", 24, SWT.ITALIC);
			gc.setFont(big);
			gc.setForeground(red);
			gc.drawString("Big italic", 10, 180, true);
			gc.setFont(null);
			big.dispose();
			TextLayout layout = new TextLayout(d);
			layout.setText("TextLayout with a selection and wrapping text over lines");
			layout.setWidth(180);
			TextStyle style = new TextStyle(null, red, null);
			style.underline = true;
			style.underlineStyle = SWT.UNDERLINE_SQUIGGLE;
			layout.setStyle(style, 0, 9);
			layout.draw(gc, 10, 230, 5, 20, d.getSystemColor(SWT.COLOR_WHITE), blue);
			log("text", "layoutBounds", layout.getBounds());
			layout.dispose();
		}));
		list.add(new Scene("images", (gc, d) -> {
			Image plain = testImage(d, false), alpha = testImage(d, true);
			gc.drawImage(plain, 10, 10);
			gc.drawImage(alpha, 60, 10);
			gc.drawImage(plain, 0, 0, 40, 30, 110, 10, 120, 90);
			gc.setInterpolation(SWT.NONE);
			gc.drawImage(plain, 5, 5, 20, 15, 10, 110, 100, 75);
			gc.setInterpolation(SWT.HIGH);
			gc.drawImage(alpha, 0, 0, 40, 30, 120, 110, 180, 130);
			log("images", "interpolationHigh", gc.getInterpolation());
			gc.setInterpolation(SWT.LOW);
			gc.drawImage(plain, 0, 0, 40, 30, 10, 250, 60, 45);
			gc.setAlpha(100);
			gc.drawImage(alpha, 240, 10);
			gc.drawImage(plain, 0, 0, 40, 30, 240, 250, 70, 60);
			plain.dispose();
			alpha.dispose();
		}));
		list.add(new Scene("transforms", (gc, d) -> {
			Transform t = new Transform(d);
			t.translate(160, 160);
			t.rotate(30);
			t.scale(1.5f, 0.8f);
			gc.setTransform(t);
			gc.setBackground(blue);
			gc.fillRectangle(-40, -30, 80, 60);
			gc.setForeground(red);
			gc.setLineWidth(3);
			gc.drawOval(-60, -60, 120, 120);
			gc.drawString("rotated", -20, -10, true);
			Image img = testImage(d, true);
			gc.drawImage(img, -100, 40);
			img.dispose();
			t.shear(0.3f, 0);
			gc.setTransform(t);
			gc.drawRectangle(-20, -20, 40, 40);
			log("transforms", "clippingTransformed", gc.getClipping());
			t.dispose();
		}));
		list.add(new Scene("clipping", (gc, d) -> {
			gc.setClipping(20, 20, 200, 150);
			log("clipping", "rect", gc.getClipping());
			gc.setBackground(red);
			gc.fillOval(0, 0, 300, 300);
			Path clip = new Path(d);
			clip.addArc(100, 100, 200, 200, 0, 360);
			gc.setClipping(clip);
			log("clipping", "path", gc.getClipping());
			gc.setBackground(blue);
			gc.fillRectangle(0, 0, SIZE, SIZE);
			clip.dispose();
			Region region = new Region(d);
			region.add(new int[] { 10, 200, 150, 200, 80, 310 });
			region.add(250, 10, 60, 60);
			gc.setClipping(region);
			Region got = new Region(d);
			gc.getClipping(got);
			log("clipping", "region", gc.getClipping() + " " + got.getBounds() + " " + got.contains(80, 250));
			got.dispose();
			gc.setBackground(green);
			gc.fillRectangle(0, 0, SIZE, SIZE);
			region.dispose();
			Transform t = new Transform(d);
			t.translate(30, 10);
			gc.setTransform(t);
			gc.setClipping(0, 0, 100, 100);
			log("clipping", "translated", gc.getClipping());
			gc.setBackground(red);
			gc.fillRectangle(0, 0, SIZE, SIZE);
			t.dispose();
			gc.setClipping((Rectangle) null);
		}));
		list.add(new Scene("alpha", (gc, d) -> {
			gc.setAlpha(128);
			gc.setBackground(red);
			gc.fillRectangle(20, 20, 150, 150);
			gc.setBackground(blue);
			gc.fillOval(80, 80, 150, 150);
			gc.setAlpha(60);
			gc.setForeground(green);
			gc.setLineWidth(12);
			gc.drawLine(10, 300, 300, 10);
			gc.drawString("translucent text", 20, 250, true);
			log("alpha", "alpha", gc.getAlpha());
			gc.setAlpha(255);
		}));
		list.add(new Scene("patterns", (gc, d) -> {
			Pattern p = new Pattern(d, 0, 0, 300, 300, red, 255, blue, 100);
			gc.setBackgroundPattern(p);
			gc.fillRectangle(10, 10, 300, 140);
			Image img = testImage(d, false);
			Pattern ip = new Pattern(d, img);
			gc.setForegroundPattern(ip);
			gc.setLineWidth(15);
			gc.drawRectangle(30, 170, 250, 120);
			gc.setBackgroundPattern(ip);
			gc.fillOval(60, 190, 180, 80);
			ip.dispose();
			img.dispose();
			p.dispose();
		}));
		list.add(new Scene("saverestore", (gc, d) -> {
			// Save/Restore and GetHDC/ReleaseHDC through GDI calls mixed with GDI+ ones
			Transform t = new Transform(d);
			for (int i = 0; i < 6; i++) {
				t.identity();
				t.translate(40 + i * 45, 40 + i * 40);
				t.rotate(i * 15);
				gc.setTransform(t);
				gc.setClipping(-15, -15, 40, 30);
				gc.setBackground(i % 2 == 0 ? red : blue);
				gc.fillRectangle(-20, -20, 40, 40);
				gc.setClipping((Rectangle) null);
				gc.drawString(Integer.toString(i), 0, 0, true);
			}
			gc.setTransform(null);
			gc.copyArea(0, 0, 60, 60, 250, 250);
			t.dispose();
		}));
		return list;
	}

	static FontData withStyle(FontData data, boolean underline) {
		data.data.lfUnderline = underline ? (byte) 1 : 0;
		return data;
	}
}
