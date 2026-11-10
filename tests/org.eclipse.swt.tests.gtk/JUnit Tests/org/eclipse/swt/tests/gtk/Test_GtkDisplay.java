/*******************************************************************************
 * Copyright (c) 2026 Lars Vogel and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.gtk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

public class Test_GtkDisplay {

	/**
	 * The default colors must follow a theme switch at once, not only after the
	 * event loop has processed the resulting settings update.
	 */
	@Test
	public void test_setDarkThemePreferred_refreshesDefaultColors() {
		Display display = Display.getDefault();
		Shell shell = new Shell(display);
		Label label = new Label(shell, SWT.NONE);
		boolean dark = Display.isSystemDarkTheme();
		try {
			for (boolean preferred : new boolean[] { !dark, dark }) {
				display.setDarkThemePreferred(preferred);
				RGB systemForeground = display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND).getRGB();
				RGB listBackground = display.getSystemColor(SWT.COLOR_LIST_BACKGROUND).getRGB();
				RGB labelForeground = label.getForeground().getRGB();
				processSettingsUpdate(display);
				assertEquals(display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND).getRGB(), systemForeground);
				assertEquals(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND).getRGB(), listBackground);
				assertEquals(label.getForeground().getRGB(), labelForeground);
			}
		} finally {
			display.setDarkThemePreferred(dark);
			processSettingsUpdate(display);
			shell.dispose();
		}
	}

	private static void processSettingsUpdate(Display display) {
		boolean[] done = new boolean[1];
		display.timerExec(300, () -> done[0] = true);
		while (!done[0]) {
			// Display.sleep() is what turns a GTK settings change into a color refresh
			if (!display.readAndDispatch()) display.sleep();
		}
	}
}
