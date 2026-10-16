/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GCData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.internal.cocoa.NSAffineTransform;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cocoa specific regression tests for {@link GC}.
 */
public class Test_cocoa_GC {

	private Display display;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
	}

	@AfterEach
	public void tearDown() {
		display.dispose();
	}

	private static boolean isBlack(ImageData data, int x, int y) {
		RGB rgb = data.palette.getRGB(data.getPixel(x, y));
		return rgb.red == 0 && rgb.green == 0 && rgb.blue == 0;
	}

	@Test
	public void test_setTransform_null_resetsTransform() {
		Image image = new Image(display, 20, 20);
		GC gc = new GC(image);
		Transform t = new Transform(display);
		try {
			gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
			gc.fillRectangle(0, 0, 20, 20);
			gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
			t.translate(10, 10);
			for (int i = 0; i < 10000; i++) {
				gc.setTransform(t);
				gc.setTransform(null);
				gc.drawLine(2, 2, 2, 2);
			}
		} finally {
			t.dispose();
			gc.dispose();
		}
		ImageData data = image.getImageData();
		image.dispose();
		assertTrue(isBlack(data, 2, 2), "pixel must be drawn at the untransformed position");
		assertTrue(!isBlack(data, 12, 12), "pixel must not be drawn at the translated position");
	}

	@Test
	public void test_copyArea_displayGC_toImage() {
		GC gc = new GC(display);
		Image image = new Image(display, 50, 40);
		try {
			gc.copyArea(image, 0, 0);
			assertTrue(!image.isDisposed());
			assertEquals(50, image.getBounds().width);
			assertEquals(40, image.getBounds().height);
			ImageData data = image.getImageData();
			assertEquals(50, data.width);
			assertEquals(40, data.height);
		} finally {
			image.dispose();
			gc.dispose();
		}
	}

	/**
	 * Leak check: setTransform(null) must release the NSAffineTransforms it drops.
	 */
	@Test
	public void test_setTransform_null_releasesTransforms() {
		Image image = new Image(display, 10, 10);
		GC gc = new GC(image);
		Transform t = new Transform(display);
		try {
			GCData data = gc.getGCData();
			gc.setTransform(t);
			NSAffineTransform transform = data.transform;
			NSAffineTransform inverse = data.inverseTransform;
			assertNotNull(transform);
			assertNotNull(inverse);
			transform.retain();
			inverse.retain();
			long transformCount = transform.retainCount();
			long inverseCount = inverse.retainCount();
			gc.setTransform(null);
			assertNull(data.transform);
			assertNull(data.inverseTransform);
			assertEquals(transformCount - 1, transform.retainCount(), "transform leaked");
			assertEquals(inverseCount - 1, inverse.retainCount(), "inverseTransform leaked");
			transform.release();
			inverse.release();
		} finally {
			t.dispose();
			gc.dispose();
			image.dispose();
		}
	}
}
