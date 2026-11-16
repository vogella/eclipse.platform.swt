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
package org.eclipse.swt.tests.win32.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Test_org_eclipse_swt_widgets_DateTime {

	private Display display;
	private Shell shell;

	@BeforeEach
	void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
	}

	@AfterEach
	void tearDown() {
		shell.dispose();
	}

	@Test
	void dropDownIsLaidOutWhenColorsChangeItsVisualStyles() {
		shell.setLayout(new RowLayout());
		DateTime dateTime = new DateTime(shell, SWT.DATE | SWT.DROP_DOWN);
		shell.pack();
		shell.open();

		for (Color background : new Color[] { new Color(0xFB, 0xF6, 0xE9), null }) {
			dateTime.setBackground(background);
			while (display.readAndDispatch()) {
				// run the deferred layout
			}
			Point preferred = dateTime.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			assertEquals(preferred.x, dateTime.getSize().x, "background=" + background);
		}
	}
}
