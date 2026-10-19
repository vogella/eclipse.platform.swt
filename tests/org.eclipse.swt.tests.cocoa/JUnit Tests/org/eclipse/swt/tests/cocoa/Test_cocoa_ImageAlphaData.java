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
package org.eclipse.swt.tests.cocoa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.internal.C;
import org.eclipse.swt.internal.cocoa.NSBitmapImageRep;
import org.eclipse.swt.internal.cocoa.NSImage;
import org.eclipse.swt.internal.cocoa.NSSize;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.Test;

/**
 * Reads back images that wrap a native bitmap representation whose rows are padded.
 */
public class Test_cocoa_ImageAlphaData {

	@Test
	public void test_getImageData_alphaDataIgnoresRowPadding() {
		for (int width : new int[] { 13, 140 }) {
			int height = 7;
			NSBitmapImageRep rep = (NSBitmapImageRep) new NSBitmapImageRep().alloc();
			rep = rep.initWithBitmapDataPlanes(0, width, height, 8, 4, true, false, OS.NSDeviceRGBColorSpace,
					OS.NSAlphaFirstBitmapFormat | OS.NSAlphaNonpremultipliedBitmapFormat, 64 * ((width * 4 + 63) / 64), 32);
			long bytesPerRow = rep.bytesPerRow();
			byte[] pixels = new byte[(int) (bytesPerRow * height)];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					pixels[(int) (y * bytesPerRow + x * 4)] = (byte) alphaAt(x, y, width);
				}
			}
			C.memmove(rep.bitmapData(), pixels, pixels.length);

			NSImage nsImage = (NSImage) new NSImage().alloc();
			NSSize size = new NSSize();
			size.width = width;
			size.height = height;
			nsImage = nsImage.initWithSize(size);
			nsImage.addRepresentation(rep);
			rep.release();

			Image image = Image.cocoa_new(Display.getDefault(), SWT.BITMAP, nsImage);
			try {
				ImageData data = image.getImageData(100);
				assertEquals(width * height, data.alphaData.length, "alphaData length for width " + width);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						assertEquals(alphaAt(x, y, width), data.getAlpha(x, y), "alpha at " + x + "," + y + " for width " + width);
					}
				}
			} finally {
				image.dispose();
			}
		}
	}

	@Test
	public void test_getImageData_packsPaddedRowsToDeclaredScanlinePad() {
		int width = 340;
		int height = 150;
		NSBitmapImageRep rep = (NSBitmapImageRep) new NSBitmapImageRep().alloc();
		rep = rep.initWithBitmapDataPlanes(0, width, height, 8, 3, false, false, OS.NSDeviceRGBColorSpace,
				OS.NSAlphaFirstBitmapFormat | OS.NSAlphaNonpremultipliedBitmapFormat, 64 * ((width * 4 + 63) / 64), 32);
		long bytesPerRow = rep.bytesPerRow();
		assertEquals(1408, bytesPerRow);
		byte[] pixels = new byte[(int) (bytesPerRow * height)];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int offset = (int) (y * bytesPerRow + x * 4);
				pixels[offset] = (byte) 0xFF;
				pixels[offset + 1] = (byte) x;
				pixels[offset + 2] = (byte) y;
				pixels[offset + 3] = (byte) (x ^ y);
			}
		}
		C.memmove(rep.bitmapData(), pixels, pixels.length);

		NSImage nsImage = (NSImage) new NSImage().alloc();
		NSSize size = new NSSize();
		size.width = width;
		size.height = height;
		nsImage = nsImage.initWithSize(size);
		nsImage.addRepresentation(rep);
		rep.release();

		Image image = Image.cocoa_new(Display.getDefault(), SWT.BITMAP, nsImage);
		try {
			ImageData data = image.getImageData(100);
			assertEquals(((data.width * data.depth + 7) / 8 + data.scanlinePad - 1) / data.scanlinePad * data.scanlinePad,
					data.bytesPerLine);
			ImageData copy = new ImageData(data.width, data.height, data.depth, data.palette, data.scanlinePad, data.data);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageLoader saver = new ImageLoader();
			saver.data = new ImageData[] { data };
			saver.save(out, SWT.IMAGE_PNG);
			ImageData loaded = new ImageLoader().load(new ByteArrayInputStream(out.toByteArray()))[0];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int expected = ((x & 0xFF) << 16) | ((y & 0xFF) << 8) | ((x ^ y) & 0xFF);
					assertEquals(expected, data.getPixel(x, y), "pixel " + x + "," + y);
					assertEquals(expected, copy.getPixel(x, y), "copy pixel " + x + "," + y);
					assertEquals(data.palette.getRGB(expected), loaded.palette.getRGB(loaded.getPixel(x, y)), "png pixel " + x + "," + y);
				}
			}
		} finally {
			image.dispose();
		}
	}

	private static int alphaAt(int x, int y, int width) {
		return (y * width + x) % 255 + 1;
	}
}
