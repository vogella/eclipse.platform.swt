/*******************************************************************************
 * Copyright (c) 2000, 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Red Hat Inc. - Bug 462631
 *******************************************************************************/
package org.eclipse.swt.tests.junit;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.ImageLoaderEvent;
import org.eclipse.swt.graphics.ImageLoaderListener;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.internal.DPIUtil.ElementAtZoom;
import org.eclipse.swt.internal.NativeImageLoader;
import org.eclipse.swt.internal.image.FileFormat;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.Test;

/**
 * Automated Test Suite for class org.eclipse.swt.graphics.ImageLoader
 *
 * @see org.eclipse.swt.graphics.ImageLoader
 */
public class Test_org_eclipse_swt_graphics_ImageLoader {

@Test
public void test_Constructor() {
	new ImageLoader();
}

@Test
public void test_addImageLoaderListenerLorg_eclipse_swt_graphics_ImageLoaderListener() throws IOException {
	ImageLoader loader = new ImageLoader();
	ImageLoaderListener loaderListener = e -> loaderListenerCalled = true;

	assertThrows(IllegalArgumentException.class, () -> loader.addImageLoaderListener(null),
			"No exception thrown for addImageLoaderListener with null argument");
	assertFalse(loader.hasListeners());
	loader.addImageLoaderListener(loaderListener);
	assertTrue(loader.hasListeners());

	loaderListenerCalled = false;
	try (InputStream stream = SwtTestUtil.class.getResourceAsStream("interlaced_target.png")) {
		loader.load(stream);
	}
	assertTrue(loaderListenerCalled);

	loaderListenerCalled = false;
	try (InputStream stream = SwtTestUtil.class.getResourceAsStream("target.png")) {
		loader.load(stream);
	}
	assertFalse(loaderListenerCalled);

	loaderListenerCalled = false;
	loader.notifyListeners(new ImageLoaderEvent(loader, loader.data[0], 0, true));
	assertTrue(loaderListenerCalled);

	loader.removeImageLoaderListener(loaderListener);
	assertFalse(loader.hasListeners());
}

@Test
public void test_loadLjava_io_InputStream() throws IOException {
	ImageLoader loader = new ImageLoader();
	assertThrows(IllegalArgumentException.class, () -> loader.load((InputStream) null),
			"No exception thrown for load inputStream == null");

	try (InputStream stream = SwtTestUtil.class.getResourceAsStream("empty.txt")) {
		assertThrows(SWTException.class, () -> loader.load(stream),
				"No exception thrown for load from invalid inputStream");
	}

	String fileName = SwtTestUtil.imageFilenames[0];
	for (String format : SwtTestUtil.imageFormats) {
		try (InputStream stream = SwtTestUtil.class.getResourceAsStream(fileName + "." + format)) {
			loader.load(stream);
		}
	}
}

@Test
public void test_loadLjava_lang_String() {
	ImageLoader loader = new ImageLoader();
	String filename = null;
	assertThrows(IllegalArgumentException.class, () -> loader.load(filename),
			"No exception thrown for load filename == null");
}

@Test
public void test_saveLjava_io_OutputStreamI() throws IOException {
	ImageLoader loader = new ImageLoader();

	OutputStream outStream1 = null;
	assertThrows(IllegalArgumentException.class, () -> loader.save(outStream1, 0),
			"No exception thrown for save outputStream == null");

	OutputStream outStream2 = new ByteArrayOutputStream();
	assertThrows(SWTException.class, () -> loader.save(outStream2, -1),
			"No exception thrown for save to invalid outputStream format");

	boolean jpgSupported = Arrays.asList(SwtTestUtil.imageFormats).contains("jpg");
	if (jpgSupported) {
		String filename = SwtTestUtil.imageFilenames[0];
		// must use jpg since save is not implemented yet in png format
		String filetype = "jpg";
		try (InputStream inStream = SwtTestUtil.class.getResourceAsStream(filename + "." + filetype)) {
			loader.load(inStream);
		}
		OutputStream outStream = new ByteArrayOutputStream();
		int i = 0;
		for (String format : SwtTestUtil.imageFormats) {
			if (format.equals(filetype)) {
				// save using the appropriate format
				loader.save(outStream, i++);
				break;
			}
		}
	}
}

@Test
public void test_saveLjava_lang_StringI() {
	ImageLoader loader = new ImageLoader();
	String filename = null;
	assertThrows(IllegalArgumentException.class, () -> loader.save(filename, 0),
			"No exception thrown for save filename == null");
}

/**
 * Ensure that saving and loading an image with {@link ImageLoader}
 * does not result in different {@link ImageData#data} arrays.
 */
@Test
public void test_bug547529() {
	Display display = Display.getDefault();
	try {
		int imageWidth = 8;
		int imageHeight = 8;
		int imageDepth;
		/*
		 * Native ImageLoader on GTK uses GdkPixbuf, which only supports
		 * 3/4 channels with 8 bits per channel. Since we use the alpha channel
		 * in all cases, ImageData loaded by GdkPixbuf will be 32 bit color depth.
		 * Furthermore, loaded images will result have direct PaletteData with RGB masks, since
		 * there is no way to determine indexed PaletteData info.
		 *
		 *  Because of this, adjust the image depth for this test case accordingly.
		 */
		if (SwtTestUtil.isGTK) {
			imageDepth = 32;
		} else {
			imageDepth = 24;
		}
		int RED = 0xFF0000;
		int GREEN = 0x00FF00;
		int BLUE = 0x0000FF;
		PaletteData palette = new PaletteData(RED, GREEN, BLUE);
		ImageData imageData = new ImageData(imageWidth, imageHeight, imageDepth, palette);
		int w = imageWidth / 2;
		int h = imageHeight / 2;

		for (int y = 0; y < imageHeight; ++y) {
			for (int x = 0; x < imageWidth; ++x) {
				int color = 0x000000;
				if (x < w && y < h) {
					color = RED;
				} else if (x < w && y >= h) {
					color = GREEN;
				} else if (x >= w && y < h) {
					color = BLUE;
				}
				imageData.setPixel(x, y, color);
			}
		}

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageLoader saver = new ImageLoader();
		saver.data = new ImageData[] { imageData };
		saver.save(outputStream, SWT.IMAGE_PNG);
		byte[] savedBytes = imageData.data;
		ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		ImageLoader loader = new ImageLoader();
		loader.load(inputStream);
		ImageData[] loadedData = loader.data;
		assertEquals(1, loadedData.length);
		byte[] loadedBytes = loadedData[0].data;
		assertArrayEquals(savedBytes, loadedBytes);
	} finally {
		display.dispose();
	}
}

/**
 * Regression test for https://github.com/eclipse-platform/eclipse.platform.swt/issues/3404
 *
 * A single-frame GIF carrying a graphic control extension, such a GIF is reported as a
 * non-static animation whose first frame step yields no update (since gdk-pixbuf
 * 2.44 delegates decoding to glycin), so the GTK image loader used
 * to return an empty list. JFace requests image data at the monitor's zoom this exact way
 * (see org.eclipse.jface.resource.URLImageDescriptor#loadImageFromStream) and an empty
 * list made its {@code .get(0)} throw ArrayIndexOutOfBoundsException, which is only reached
 * when running at a zoom other than 100%. The call below mirrors that line verbatim, so it
 * reproduces the original failure unless loading always yields at least one frame.
 */
@Test
public void test_loadSingleFrameGifReportedAsAnimation_bug3404() throws IOException {
	byte[] bytes;
	try (InputStream in = getClass().getResourceAsStream("tc_empty.gif")) {
		assertNotNull(in, "Test resource c_empty.gif not found");
		bytes = in.readAllBytes();
	}
	for (int zoom : new int[] { 100, 200 }) {
		// Copy of URLImageDescriptor.loadImageFromStream(stream, fileZoom, targetZoom):
		// an empty list throws ArrayIndexOutOfBoundsException on get(0), exactly as JFace did.
		@SuppressWarnings("restriction")
		ImageData imageData = NativeImageLoader
				.load(new ElementAtZoom<>(new ByteArrayInputStream(bytes), zoom), new ImageLoader(), zoom).get(0)
				.element();
		assertNotNull(imageData, "No ImageData returned at zoom " + zoom);
		assertTrue(imageData.width > 0 && imageData.height > 0, "ImageData has no pixels at zoom " + zoom);
	}
}

/**
 * Every frame of an animated GIF is loaded once, in order, with its delay in hundredths of a
 * second, also beyond 32 frames.
 */
@Test
public void test_loadAnimatedGifFrames() {
	for (int frameCount : new int[] { 2, 5, 40 }) {
		ImageData[] frames = new ImageData[frameCount];
		RGB[] colors = new RGB[frameCount];
		for (int i = 0; i < frameCount; i++) {
			colors[i] = new RGB(i * 6, 255 - i * 6, (i * 37) % 256);
			frames[i] = new ImageData(4, 3, 8, new PaletteData(colors[i], new RGB(0, 0, 0)));
			frames[i].delayTime = 5 + i % 3 * 5;
		}
		ImageLoader saver = new ImageLoader();
		saver.data = frames;
		saver.logicalScreenWidth = 4;
		saver.logicalScreenHeight = 3;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		saver.save(out, SWT.IMAGE_GIF);
		ImageData[] loaded = new ImageLoader().load(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(frameCount, loaded.length, "frames");
		for (int i = 0; i < frameCount; i++) {
			assertEquals(colors[i], loaded[i].palette.getRGB(loaded[i].getPixel(1, 1)), "color of frame " + i);
			assertEquals(frames[i].delayTime, loaded[i].delayTime, "delay of frame " + i);
		}
	}
}

/**
 * Regression test for https://github.com/eclipse-platform/eclipse.platform.swt/issues/3545
 *
 * A one pixel wide RGB image has a row stride of 3 bytes when gdk-pixbuf decodes through glycin.
 */
@Test
public void test_loadOnePixelWideRgbPng_bug3545() throws IOException {
	ImageData imageData;
	try (InputStream in = getClass().getResourceAsStream("rgb_1x3.png")) {
		assertNotNull(in, "Test resource rgb_1x3.png not found");
		imageData = new ImageData(in);
	}
	assertEquals(1, imageData.width);
	assertEquals(3, imageData.height);
	RGB[] expected = { new RGB(0x00, 0x35, 0x6a), new RGB(0x5b, 0x90, 0xc5), new RGB(0xb6, 0xeb, 0x20) };
	for (int y = 0; y < expected.length; y++) {
		assertEquals(expected[y], imageData.palette.getRGB(imageData.getPixel(0, y)), "Wrong pixel in row " + y);
	}
}

/**
 * Saving must take the channels from the palette masks and honour the scanline pad, whatever
 * byte order and row padding the direct palette image uses.
 */
@Test
public void test_saveDirectImagesWithAnyByteOrderAndPad() {
	PaletteData[] palettes = {
			new PaletteData(0xFF0000, 0xFF00, 0xFF), new PaletteData(0xFF, 0xFF00, 0xFF0000),
			new PaletteData(0xFF00, 0xFF0000, 0xFF000000), new PaletteData(0xFF0000, 0xFF00, 0xFF) };
	int[] depths = { 24, 24, 32, 32 };
	RGB[] colors = { new RGB(0x11, 0x22, 0x33), new RGB(0xC0, 0x80, 0x40), new RGB(0xFF, 0x00, 0x7F) };
	for (int i = 0; i < palettes.length; i++) {
		for (int pad : new int[] { 1, 4 }) {
			for (int width : new int[] { 1, 3, 7 }) {
				int height = 2;
				int bytesPerLine = ((width * depths[i] + 7) / 8 + pad - 1) / pad * pad;
				ImageData source = new ImageData(width, height, depths[i], palettes[i], pad, new byte[bytesPerLine * height]);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						source.setPixel(x, y, palettes[i].getPixel(colors[(x + y) % colors.length]));
					}
				}
				for (int format : new int[] { SWT.IMAGE_PNG, SWT.IMAGE_BMP }) {
					ImageLoader saver = new ImageLoader();
					saver.data = new ImageData[] { source };
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					saver.save(out, format);
					ImageData saved = new ImageLoader().load(new ByteArrayInputStream(out.toByteArray()))[0];
					String what = "depth " + depths[i] + ", palette " + i + ", pad " + pad + ", width " + width + ", format " + format;
					assertEquals(width, saved.width, what);
					assertEquals(height, saved.height, what);
					for (int y = 0; y < height; y++) {
						for (int x = 0; x < width; x++) {
							assertEquals(colors[(x + y) % colors.length], saved.palette.getRGB(saved.getPixel(x, y)),
									what + ", pixel " + x + "," + y);
						}
					}
				}
			}
		}
	}
}

/**
 * The GIF specification requires an LZW minimum code size of at least 2. With the size 1 that
 * was written for bilevel images, decoders (also SWT's own) read most images wrongly or fail.
 */
@Test
public void test_saveBilevelGif() throws IOException {
	RGB[] colors = { new RGB(10, 20, 30), new RGB(200, 100, 50) };
	for (int width = 1; width <= 9; width += 4) {
		for (int height = 1; height <= 4; height++) {
			ImageData source = new ImageData(width, height, 1, new PaletteData(colors));
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					source.setPixel(x, y, (x * 3 + y) % 5 == 0 ? 1 : 0);
				}
			}
			ImageLoader saver = new ImageLoader();
			saver.data = new ImageData[] { source };
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			saver.save(out, SWT.IMAGE_GIF);
			byte[] bytes = out.toByteArray();
			// header (6), logical screen descriptor (7), global color table (6), image descriptor (10)
			assertEquals(2, bytes[29], "LZW minimum code size");
			ImageData javaDecoded = FileFormat
					.load(new ElementAtZoom<>(new ByteArrayInputStream(bytes), 100), new ImageLoader(), 100).get(0)
					.element();
			ImageData decoded = new ImageLoader().load(new ByteArrayInputStream(bytes))[0];
			for (ImageData data : new ImageData[] { javaDecoded, decoded }) {
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						assertEquals(colors[source.getPixel(x, y)], data.palette.getRGB(data.getPixel(x, y)),
								width + "x" + height + ", pixel " + x + "," + y);
					}
				}
			}
		}
	}
}

/**
 * The Java TIFF encoder, used on win32 and cocoa, wrote the image bytes as they are: a 24 bit
 * image whose palette is not RGB came out with swapped channels, and padded rows were refused.
 */
@Test
public void test_saveTiffWithJavaCodec() {
	RGB[] colors = { new RGB(0x11, 0x22, 0x33), new RGB(0xC0, 0x80, 0x40), new RGB(0xFF, 0x00, 0x7F) };
	RGB[] indexed = new RGB[256];
	for (int i = 0; i < indexed.length; i++) {
		indexed[i] = i < colors.length ? colors[i] : new RGB(i, i, i);
	}
	PaletteData[] palettes = { new PaletteData(0xFF, 0xFF00, 0xFF0000), new PaletteData(0xFF0000, 0xFF00, 0xFF),
			new PaletteData(Arrays.copyOf(indexed, 16)), new PaletteData(indexed) };
	int[] depths = { 24, 24, 4, 8 };
	for (int i = 0; i < palettes.length; i++) {
		for (int pad : new int[] { 1, 4 }) {
			int width = 3, height = 2;
			int bytesPerLine = ((width * depths[i] + 7) / 8 + pad - 1) / pad * pad;
			ImageData source = new ImageData(width, height, depths[i], palettes[i], pad, new byte[bytesPerLine * height]);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					RGB color = colors[(x + y) % colors.length];
					source.setPixel(x, y, palettes[i].isDirect ? palettes[i].getPixel(color) : (x + y) % colors.length);
				}
			}
			ImageLoader saver = new ImageLoader();
			saver.data = new ImageData[] { source };
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			FileFormat.save(out, SWT.IMAGE_TIFF, saver);
			ImageData saved = FileFormat
					.load(new ElementAtZoom<>(new ByteArrayInputStream(out.toByteArray()), 100), new ImageLoader(), 100)
					.get(0).element();
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					assertEquals(colors[(x + y) % colors.length], saved.palette.getRGB(saved.getPixel(x, y)),
							"depth " + depths[i] + ", palette " + i + ", pad " + pad + ", pixel " + x + "," + y);
				}
			}
		}
	}
}

/**
 * The Java decoders in FileFormat are the only path on win32 and cocoa but are bypassed by
 * GdkPixbuf on GTK, so they are called directly.
 */
@Test
public void test_decodeBaselineJpeg() throws IOException {
	assertRedBlueHalves(decodeWithJavaCodec("baseline_16x16.jpg"), 40);
}

@Test
public void test_decodeProgressiveJpeg() throws IOException {
	assertRedBlueHalves(decodeWithJavaCodec("progressive_16x16.jpg"), 40);
}

@Test
public void test_decodeInterlacedPng() throws IOException {
	assertRedBlueHalves(decodeWithJavaCodec("interlaced_16x16.png"), 0);
}

@Test
public void test_decodeLzwTiff() throws IOException {
	assertRedBlueHalves(decodeWithJavaCodec("lzw_16x16.tif"), 0);
	assertRedBlueHalves(decodeWithJavaCodec("lzw_predictor_16x16.tif"), 0);
	// Long enough for 12 bit codes and a clear code
	ImageData lzw = decodeWithJavaCodec("pattern_64x48_lzw.tif");
	ImageData uncompressed = decodeWithJavaCodec("pattern_64x48.tif");
	for (int y = 0; y < uncompressed.height; y++) {
		for (int x = 0; x < uncompressed.width; x++) {
			assertEquals(uncompressed.palette.getRGB(uncompressed.getPixel(x, y)), lzw.palette.getRGB(lzw.getPixel(x, y)),
					"pixel " + x + "," + y);
		}
	}
}

@Test
public void test_decodeDeflateTiff() throws IOException {
	assertRedBlueHalves(decodeWithJavaCodec("deflate_16x16.tif"), 0);
}

/**
 * A compression the decoder does not know must be reported instead of decoding to a black image.
 */
@Test
public void test_decodeJpegCompressedTiffIsUnsupported() {
	SWTException e = assertThrows(SWTException.class, () -> decodeWithJavaCodec("jpeg_16x16.tif"));
	assertEquals(SWT.ERROR_UNSUPPORTED_FORMAT, e.code);
}

@Test
public void test_encodeJpegRoundTrip() throws IOException {
	ImageData source = decodeWithJavaCodec("interlaced_16x16.png");
	ImageLoader saver = new ImageLoader();
	saver.data = new ImageData[] { source };
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	FileFormat.save(out, SWT.IMAGE_JPEG, saver);
	ImageData decoded = FileFormat
			.load(new ElementAtZoom<>(new ByteArrayInputStream(out.toByteArray()), 100), new ImageLoader(), 100).get(0)
			.element();
	assertRedBlueHalves(decoded, 40);
}

private ImageData decodeWithJavaCodec(String resource) throws IOException {
	try (InputStream in = getClass().getResourceAsStream(resource)) {
		assertNotNull(in, "Test resource " + resource + " not found");
		return FileFormat.load(new ElementAtZoom<>(in, 100), new ImageLoader(), 100).get(0).element();
	}
}

private static void assertRedBlueHalves(ImageData data, int tolerance) {
	assertEquals(16, data.width);
	assertEquals(16, data.height);
	for (int y : new int[] { 1, 14 }) {
		assertNear(new RGB(255, 0, 0), data.palette.getRGB(data.getPixel(2, y)), tolerance);
		assertNear(new RGB(0, 0, 255), data.palette.getRGB(data.getPixel(13, y)), tolerance);
	}
}

private static void assertNear(RGB expected, RGB actual, int tolerance) {
	assertTrue(Math.abs(expected.red - actual.red) <= tolerance && Math.abs(expected.green - actual.green) <= tolerance
			&& Math.abs(expected.blue - actual.blue) <= tolerance, "Expected " + expected + " but was " + actual);
}

/* custom */
boolean loaderListenerCalled;
}
