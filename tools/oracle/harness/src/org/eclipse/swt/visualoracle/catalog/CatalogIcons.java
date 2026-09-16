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
package org.eclipse.swt.visualoracle.catalog;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

/**
 * A 16 point test icon defined per device pixel, so every resolution is drawn
 * natively and a wrongly picked or wrongly scaled variant changes pixels.
 */
final class CatalogIcons {

	static final int POINTS = 16;

	private static Path iconDir;

	private CatalogIcons() {
	}

	/** ARGB of the icon at {@code (x, y)} when rendered at {@code w} by {@code h} pixels. */
	static int argb(int x, int y, int w, int h) {
		if (x == 0 || y == 0 || x == w - 1 || y == h - 1)
			return 0xFF202020;
		double r = 0.28 * Math.min(w, h);
		double d = Math.hypot(x + 0.5 - w / 2.0, y + 0.5 - h / 2.0);
		double coverage = Math.clamp(r + 0.5 - d, 0.0, 1.0);
		if (coverage > 0)
			return (int) Math.round(255 * coverage) << 24 | 0x0050FF;
		if (x * h == y * w)
			return 0xFFE00000;
		if (x >= w / 2 && y < h / 2)
			return ((x + y) & 1) == 0 ? 0xFF000000 : 0xFFFFFFFF;
		if (x < w / 2 && y >= h / 2)
			return 0x8000A000;
		return 0;
	}

	static ImageData data(int w, int h) {
		ImageData data = new ImageData(w, h, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
		data.alphaData = new byte[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = argb(x, y, w, h);
				data.setPixel(x, y, argb & 0xFFFFFF);
				data.setAlpha(x, y, argb >>> 24);
			}
		}
		return data;
	}

	/** Pixel data for zoom 100 and 200, null for any other zoom. */
	static ImageData multiResolution(int zoom) {
		return switch (zoom) {
			case 100 -> data(POINTS, POINTS);
			case 200 -> data(2 * POINTS, 2 * POINTS);
			default -> null;
		};
	}

	/**
	 * A PNG of the icon at {@code scale}, written once per process with ImageIO
	 * so the file does not depend on SWT's own encoder.
	 */
	static synchronized String png(int scale) {
		String name = scale == 1 ? "icon.png" : "icon@" + scale + "x.png";
		return file(name, file -> {
			int size = scale * POINTS;
			BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < size; y++)
				for (int x = 0; x < size; x++)
					image.setRGB(x, y, argb(x, y, size, size));
			ImageIO.write(image, "png", file.toFile());
		});
	}

	/** A text file with {@code content}, written once per process. */
	static synchronized String text(String name, String content) {
		return file(name, file -> Files.writeString(file, content));
	}

	private interface Writer {
		void write(Path file) throws IOException;
	}

	private static String file(String name, Writer writer) {
		try {
			if (iconDir == null) {
				iconDir = Files.createTempDirectory("oracle-icons-");
				iconDir.toFile().deleteOnExit();
			}
			Path file = iconDir.resolve(name);
			if (!Files.exists(file)) {
				writer.write(file);
				file.toFile().deleteOnExit();
			}
			return file.toString();
		} catch (IOException e) {
			throw new UncheckedIOException("cannot write test icon " + name, e);
		}
	}
}
