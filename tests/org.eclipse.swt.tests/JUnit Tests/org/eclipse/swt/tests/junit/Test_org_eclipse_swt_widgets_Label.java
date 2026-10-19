/*******************************************************************************
 * Copyright (c) 2000, 2025 IBM Corporation and others.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageGcDrawer;
import org.eclipse.swt.widgets.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Automated Test Suite for class org.eclipse.swt.widgets.Label
 *
 * @see org.eclipse.swt.widgets.Label
 */
public class Test_org_eclipse_swt_widgets_Label extends Test_org_eclipse_swt_widgets_Control {

@Override
@BeforeEach
public void setUp() {
	super.setUp();
	label = new Label(shell, 0);
	setWidget(label);
}

@Override
@Test
public void test_ConstructorLorg_eclipse_swt_widgets_CompositeI(){
	assertThrows(IllegalArgumentException.class, () -> new Label(null, 0), "No exception thrown");

	label = new Label(shell, 0);

	int[] cases = {SWT.LEFT, SWT.RIGHT, SWT.CENTER, SWT.SEPARATOR, SWT.HORIZONTAL, SWT.VERTICAL, SWT.SHADOW_IN, SWT.SHADOW_OUT};
	for (int style : cases)
		label = new Label(shell, style);
}

@Test
public void test_computeSizeWidthHintNarrowerThanBorderedText() {
	String text = "A fairly long label text that is much wider than the hint";
	for (int style : new int[] {SWT.BORDER, SWT.BORDER | SWT.WRAP}) {
		Label bordered = new Label(shell, style);
		bordered.setText(text);
		int natural = bordered.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
		int width = bordered.computeSize(120, SWT.DEFAULT).x;
		assertTrue(natural > 160, "Test text must be wider than the hint plus border");
		assertTrue(width >= 120 && width <= 128, "Width hint 120 with border gave width " + width + " for style " + style);
	}
}

@Override
@Test
public void test_computeSizeIIZ() {
	// super class test is sufficient
}

@Test
public void test_getAlignment(){
	int[] cases = {SWT.LEFT, SWT.RIGHT, SWT.CENTER};
	for (int style : cases) {
		label = new Label(shell, style);
		assertEquals(label.getAlignment(), style);
	}
}

@Test
public void test_getImage(){
	ImageGcDrawer noOpGcDrawer = (gc, width, height) -> {};
	Image[] cases = {null, new Image(null, noOpGcDrawer, 100, 100)};
	for (Image image : cases) {
		label.setImage(image);
		assertEquals(label.getImage(), image);
		if (image!=null)
			image.dispose();
	}
}

@Test
public void test_getText(){
	String[] cases = {"", "some name", "sdasdlkjshcdascecoewcwe"};
	for (String text : cases) {
		label.setText(text);
		assertEquals(label.getText(), text);
	}
}

@Test
public void test_setAlignmentI(){
	int[] cases = {SWT.LEFT, SWT.RIGHT, SWT.CENTER};
	for (int style : cases) {
		label.setAlignment(style);
		assertEquals(label.getAlignment(), style);
	}
}

@Override
@Test
public void test_setFocus() {
	// super class test is sufficient
}

@Test
public void test_setTextLjava_lang_String(){
	assertThrows(IllegalArgumentException.class, () -> label.setText(null), "No exception thrown for string == null");
}

/* custom */
Label label;

@Test
public void test_consistency_MenuDetect () {
	consistencyEvent(10, 5, 3, 0, ConsistencyUtility.MOUSE_CLICK);
}

@Test
public void test_consistency_DragDetect () {
	consistencyEvent(10, 5, 20, 10, ConsistencyUtility.MOUSE_DRAG);
}
}
