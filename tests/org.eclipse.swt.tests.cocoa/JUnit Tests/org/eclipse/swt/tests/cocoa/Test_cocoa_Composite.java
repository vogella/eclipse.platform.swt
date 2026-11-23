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
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cocoa specific regression tests for {@link Composite}.
 */
public class Test_cocoa_Composite {

	private Display display;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
	}

	@AfterEach
	public void tearDown() {
		display.dispose();
	}

	@Test
	public void test_getBorderWidth_matchesClientAreaAndComputeTrim() {
		Shell shell = new Shell(display);
		Composite composite = new Composite(shell, SWT.BORDER);
		composite.setSize(100, 80);
		int border = composite.getBorderWidth();
		assertEquals(1, border);
		assertEquals(2 * border, composite.getSize().x - composite.getClientArea().width);
		assertEquals(2 * border, composite.getSize().y - composite.getClientArea().height);
		Rectangle trim = composite.computeTrim(0, 0, 10, 10);
		assertEquals(2 * border, trim.width - 10);
		shell.dispose();
	}

	@Test
	public void test_getBorderWidth_zeroWithoutBorder() {
		Shell shell = new Shell(display);
		Composite composite = new Composite(shell, SWT.NONE);
		assertEquals(0, composite.getBorderWidth());
		shell.dispose();
	}
}
