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

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The system colors follow the app appearance when it differs from the system appearance.
 */
public class Test_cocoa_SystemColorsAppearance {

	private Display display;
	private boolean wasDark;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		wasDark = OS.isAppDarkAppearance();
	}

	@AfterEach
	public void tearDown() {
		OS.setTheme(wasDark);
		processEvents();
	}

	@Test
	public void testSystemColorsFollowAppAppearance() {
		OS.setTheme(false);
		processEvents();
		RGB[] light = systemColors();
		OS.setTheme(true);
		processEvents();
		RGB[] dark = systemColors();
		for (int i = 0; i < light.length; i++) {
			assertNotEquals(light[i], dark[i], "system color " + i);
		}
	}

	private RGB[] systemColors() {
		return new RGB[] { display.getSystemColor(SWT.COLOR_LIST_BACKGROUND).getRGB(),
				display.getSystemColor(SWT.COLOR_LIST_SELECTION).getRGB(),
				display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND).getRGB() };
	}

	private void processEvents() {
		while (display.readAndDispatch()) {
			// drain
		}
	}
}
