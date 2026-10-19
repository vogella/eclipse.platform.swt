/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
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
 *******************************************************************************/
package org.eclipse.swt.tests.junit;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Caret;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Automated Test Suite for class org.eclipse.swt.widgets.Canvas
 *
 * @see org.eclipse.swt.widgets.Canvas
 */
public class Test_org_eclipse_swt_widgets_Canvas extends Test_org_eclipse_swt_widgets_Composite {

Canvas canvas;

@Override
@BeforeEach
public void setUp() {
	super.setUp();
	canvas = new Canvas(shell, 0);
	super.setWidget(canvas);
}

@Override
protected void setWidget(Widget w) {
	if (!canvas.isDisposed())
		canvas.dispose();
	canvas = (Canvas)w;
	super.setWidget(w);
}

@Override
@Test
public void test_ConstructorLorg_eclipse_swt_widgets_CompositeI() {
	assertThrows(IllegalArgumentException.class, () -> new Canvas(null, SWT.NONE), "No exception thrown for parent == null");
}

@Test
public void test_scrollIIIIIIZ() {
	canvas.scroll(100, 100, 0, 0, 50, 50, false);
	canvas.scroll(100, 100, 0, 0, 50, 50, true);

	canvas.scroll(10000, 10000, 100, 100, 500, 500, false);
	canvas.scroll(10000, 10000, 100, 100, 500, 500, true);

	canvas.scroll(-100, -100, 10, 10, 30, 30, false);
	canvas.scroll(-100, -100, 10, 10, 30, 30, true);

	canvas.scroll(10, 10, -200, -200, 100, 100, false);
	canvas.scroll(10, 10, -200, -200, 100, 100, true);

	canvas.scroll(100, 100, 50, 50, -50, -50, false);
	canvas.scroll(100, 100, 50, 50, -50, -50, true);
}

@Test
public void test_setCaretLorg_eclipse_swt_widgets_Caret() {
	int number = 5;
	Caret[] carets = new Caret[number];
	for (int i = 0; i < number; i++) {
		carets[i] = new Caret(canvas, SWT.NONE);
	}
	for (int i = 0; i < number; i++) {
		canvas.setCaret(carets[i]);
		assertEquals(canvas.getCaret(), carets[i]);
	}

	canvas.setCaret(null);
	assertNull(canvas.getCaret());
}

@Override
@Test
public void test_setFontLorg_eclipse_swt_graphics_Font() {
	FontData fontData = canvas.getFont().getFontData()[0];
	Font font = new Font(canvas.getDisplay(), fontData.getName(), 8, fontData.getStyle());
	canvas.setFont(font);
	assertEquals(font, canvas.getFont());
	canvas.setFont(null);
	font.dispose();
}

@Test
public void test_CaretWithDisposedFontDoesNotThrowException_issue2323() {
	Caret caret = new Caret(canvas, SWT.NONE);
	Font font = new Font(canvas.getDisplay(), "Default", 10, SWT.BOLD);
	shell.open();
	caret.setFont(font);
	font.dispose();
	canvas.setFocus();
	canvas.setCaret(caret);
}

private Image createRedOverBlueImage() {
	Image image = new Image(canvas.getDisplay(), 40, 40);
	GC gc = new GC(image);
	gc.setBackground(canvas.getDisplay().getSystemColor(SWT.COLOR_RED));
	gc.fillRectangle(0, 0, 40, 25);
	gc.setBackground(canvas.getDisplay().getSystemColor(SWT.COLOR_BLUE));
	gc.fillRectangle(0, 25, 40, 15);
	gc.dispose();
	return image;
}

private void assertRedOverBlueTiles(Control control) {
	Image shot = new Image(control.getDisplay(), 100, 100);
	GC gc = new GC(control);
	gc.copyArea(shot, 0, 0);
	gc.dispose();
	ImageData data = shot.getImageData();
	shot.dispose();
	RGB red = new RGB(255, 0, 0);
	RGB blue = new RGB(0, 0, 255);
	assertEquals(red, data.palette.getRGB(data.getPixel(20, 10)), "first tile, red band");
	assertEquals(blue, data.palette.getRGB(data.getPixel(20, 32)), "first tile, blue band");
	assertEquals(red, data.palette.getRGB(data.getPixel(20, 50)), "second tile, red band");
	assertEquals(blue, data.palette.getRGB(data.getPixel(20, 72)), "second tile, blue band");
}

private void openAndPaint(Shell s) {
	s.setSize(300, 300);
	s.open();
	for (int i = 0; i < 20; i++) {
		while (s.getDisplay().readAndDispatch()) {
		}
		try {
			Thread.sleep(20);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}

@Test
public void test_backgroundImageIsNotMirrored() {
	Image image = createRedOverBlueImage();
	try {
		canvas.setBounds(0, 0, 100, 100);
		canvas.setBackgroundImage(image);
		openAndPaint(shell);
		assertRedOverBlueTiles(canvas);
	} finally {
		image.dispose();
	}
}

@Test
public void test_inheritedBackgroundImageIsNotMirrored() {
	Image image = createRedOverBlueImage();
	try {
		canvas.setBounds(0, 0, 100, 100);
		canvas.setBackgroundImage(image);
		canvas.setBackgroundMode(SWT.INHERIT_FORCE);
		Canvas child = new Canvas(canvas, SWT.NONE);
		child.setBounds(0, 0, 100, 100);
		openAndPaint(shell);
		assertRedOverBlueTiles(child);
	} finally {
		image.dispose();
	}
}

@Test
public void test_drawBackgroundIntoImageIsNotMirrored() {
	Image image = createRedOverBlueImage();
	Image target = new Image(canvas.getDisplay(), 100, 100);
	try {
		canvas.setBounds(0, 0, 100, 100);
		canvas.setBackgroundImage(image);
		GC gc = new GC(target);
		canvas.drawBackground(gc, 0, 0, 100, 100, 0, 0);
		gc.dispose();
		ImageData data = target.getImageData();
		assertEquals(new RGB(255, 0, 0), data.palette.getRGB(data.getPixel(20, 10)), "first tile, red band");
		assertEquals(new RGB(0, 0, 255), data.palette.getRGB(data.getPixel(20, 32)), "first tile, blue band");
		assertEquals(new RGB(255, 0, 0), data.palette.getRGB(data.getPixel(20, 50)), "second tile, red band");
		assertEquals(new RGB(0, 0, 255), data.palette.getRGB(data.getPixel(20, 72)), "second tile, blue band");
	} finally {
		target.dispose();
		image.dispose();
	}
}

/* custom*/
@Test
public void test_consistency_MenuDetect() {
	consistencyEvent(10, 10, 3, 0, ConsistencyUtility.MOUSE_CLICK);
}

@Test
public void test_consistency_DragDetect() {
	consistencyEvent(10, 10, 20, 20, ConsistencyUtility.MOUSE_DRAG);
}

}
