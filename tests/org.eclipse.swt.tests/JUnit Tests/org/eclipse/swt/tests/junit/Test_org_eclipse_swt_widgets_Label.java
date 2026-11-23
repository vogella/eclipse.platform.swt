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

import static org.eclipse.swt.tests.junit.SwtTestUtil.JENKINS_DETECT_ENV_VAR;
import static org.eclipse.swt.tests.junit.SwtTestUtil.JENKINS_DETECT_REGEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageGcDrawer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

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

/** DragDetect must fire once when the pointer leaves the drag threshold with button 1 down. */
@Test
@DisabledIfEnvironmentVariable(named = JENKINS_DETECT_ENV_VAR, matches = JENKINS_DETECT_REGEX, disabledReason = "Display.post tests don't run reliably on Jenkins - see https://github.com/eclipse-platform/eclipse.platform.swt/issues/2571")
public void test_dragDetectAfterPostedMouseMove() throws InterruptedException {
	AtomicInteger dragDetects = new AtomicInteger();
	label.addListener(SWT.DragDetect, e -> dragDetects.incrementAndGet());
	label.setText("drag me");
	label.setBounds(0, 0, 100, 100);
	shell.setSize(200, 200);
	shell.open();
	SwtTestUtil.processEvents();

	Display display = label.getDisplay();
	Point origin = label.toDisplay(10, 10);
	// Display.post is not implemented on GTK4, there the direct test below covers the logic
	assumeTrue(post(display, SWT.MouseMove, origin.x, origin.y, 0));
	assumeTrue(post(display, SWT.MouseDown, 0, 0, 1));
	assumeTrue(post(display, SWT.MouseMove, origin.x + 40, origin.y, 0));
	post(display, SWT.MouseUp, 0, 0, 1);

	SwtTestUtil.processEvents(2000, () -> dragDetects.get() > 0);
	assertEquals(1, dragDetects.get());
}

private static boolean post(Display display, int type, int x, int y, int button) {
	Event event = new Event();
	event.type = type;
	event.x = x;
	event.y = y;
	event.button = button;
	boolean posted = display.post(event);
	// GTK3 polls for the drag, so each posted event must be dispatched before the next one
	try {
		SwtTestUtil.processEvents(150, () -> false);
	} catch (InterruptedException e) {
		Thread.currentThread().interrupt();
	}
	return posted;
}

/** Drives the GTK4 press/motion bookkeeping directly because Display.post does not work there. */
@Test
public void test_dragDetectOnGtk4PressAndMotion() throws Exception {
	assumeTrue(SwtTestUtil.isGTK4());
	AtomicInteger dragDetects = new AtomicInteger();
	label.addListener(SWT.DragDetect, e -> dragDetects.incrementAndGet());
	shell.open();

	Method motion = label.getClass().getSuperclass().getDeclaredMethod("gtk4DragDetectMotion", int.class, int.class);
	motion.setAccessible(true);
	Field start = label.getClass().getSuperclass().getDeclaredField("gtk4DragStart");
	start.setAccessible(true);

	assertEquals(false, motion.invoke(label, 50, 10), "no press yet");
	start.set(label, new Point(10, 10));
	assertEquals(false, motion.invoke(label, 12, 10), "inside the threshold");
	assertEquals(true, motion.invoke(label, 50, 10), "beyond the threshold");
	assertEquals(false, motion.invoke(label, 60, 10), "only once per press");
	SwtTestUtil.processEvents();
	assertEquals(1, dragDetects.get());
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
