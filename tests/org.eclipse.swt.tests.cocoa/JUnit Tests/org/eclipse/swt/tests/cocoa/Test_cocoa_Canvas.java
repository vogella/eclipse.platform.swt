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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Caret;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cocoa specific regression tests for {@link Canvas}.
 */
public class Test_cocoa_Canvas {

	private Display display;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
	}

	@AfterEach
	public void tearDown() {
		display.dispose();
	}

	/**
	 * Drawing a caret with an image must leave the graphics state balanced:
	 * output of later paints must not be drawn in difference blend mode.
	 */
	@Test
	public void test_caretWithImage_repeatedRedraw_keepsPaintOutputUnchanged() {
		Shell shell = new Shell(display);
		shell.setSize(100, 100);
		Canvas canvas = new Canvas(shell, SWT.NO_BACKGROUND);
		canvas.setBounds(0, 0, 100, 100);
		Image caretImage = new Image(display, 4, 10);
		GC imageGC = new GC(caretImage);
		imageGC.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		imageGC.fillRectangle(0, 0, 4, 10);
		imageGC.dispose();
		Caret caret = new Caret(canvas, SWT.NONE);
		caret.setImage(caretImage);
		caret.setLocation(80, 80);
		caret.setVisible(true);
		// The paint listener paints a solid red rectangle away from the caret
		canvas.addListener(SWT.Paint, e -> {
			e.gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			e.gc.fillRectangle(0, 0, 50, 50);
		});
		shell.open();
		try {
			for (int i = 0; i < 20; i++) {
				canvas.redraw();
				canvas.update();
				while (display.readAndDispatch()) {
					// process pending events
				}
			}
			Image snapshot = new Image(display, 100, 100);
			GC gc = new GC(canvas);
			try {
				gc.copyArea(snapshot, 0, 0);
			} finally {
				gc.dispose();
			}
			RGB pixel = snapshot.getImageData().palette.getRGB(snapshot.getImageData().getPixel(10, 10));
			snapshot.dispose();
			assertEquals(display.getSystemColor(SWT.COLOR_RED).getRGB(), pixel, "paint output must not be drawn in difference blend mode");
		} finally {
			caret.dispose();
			caretImage.dispose();
			shell.dispose();
		}
	}
}
