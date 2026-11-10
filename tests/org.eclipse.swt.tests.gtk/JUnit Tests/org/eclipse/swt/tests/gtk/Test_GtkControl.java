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

import java.util.List;
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.Tree;
import org.junit.jupiter.api.Test;

public class Test_GtkControl {

	/**
	 * A control whose foreground was reset to the default must follow a later
	 * theme switch like a control that never had a foreground set.
	 */
	@Test
	public void test_setForegroundNull_followsThemeSwitch() {
		Display display = Display.getDefault();
		Shell shell = new Shell(display);
		Composite parent = new Composite(shell, SWT.NONE);
		List<Function<Composite, Control>> factories = List.of(c -> new Label(c, SWT.NONE),
				c -> new Button(c, SWT.PUSH), c -> new Button(c, SWT.CHECK), c -> new Text(c, SWT.BORDER),
				c -> new Tree(c, SWT.NONE), c -> new ToolBar(c, SWT.FLAT), c -> new Composite(c, SWT.NONE));
		boolean dark = Display.isSystemDarkTheme();
		try {
			for (Function<Composite, Control> factory : factories) {
				Control untouched = factory.apply(parent);
				Control reset = factory.apply(parent);
				reset.setForeground(display.getSystemColor(SWT.COLOR_RED));
				reset.setForeground(null);
				untouched.setData(reset);
			}
			display.setDarkThemePreferred(!dark);
			processSettingsUpdate(display);
			for (Control untouched : parent.getChildren()) {
				if (untouched.getData() instanceof Control reset) {
					assertEquals(untouched.getForeground().getRGB(), reset.getForeground().getRGB(),
							untouched.getClass().getSimpleName());
				}
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
