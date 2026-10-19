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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Test_cocoa_GCCopyArea {

	private Display display;
	private Shell shell;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	@Test
	public void copyAreaOfBorderedCompositeStartsAtClientArea() {
		RGB blue = new RGB(0, 0, 255);
		Composite composite = new Composite(shell, SWT.BORDER);
		composite.setBackground(new Color(blue));
		composite.setBounds(10, 10, 100, 80);
		shell.setSize(200, 200);
		shell.open();
		while (display.readAndDispatch()) {
			// process pending paint events
		}

		Image image = new Image(display, 20, 20);
		GC gc = new GC(composite);
		try {
			gc.copyArea(image, 0, 0);
		} finally {
			gc.dispose();
		}
		ImageData data = image.getImageData();
		image.dispose();
		assertEquals(blue, data.palette.getRGB(data.getPixel(0, 0)));
		assertEquals(blue, data.palette.getRGB(data.getPixel(1, 1)));
		assertEquals(blue, data.palette.getRGB(data.getPixel(19, 19)));
	}
}
