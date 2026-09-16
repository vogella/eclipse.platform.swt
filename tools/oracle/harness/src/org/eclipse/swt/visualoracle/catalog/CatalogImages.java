/*******************************************************************************
 * Copyright (c) 2026 SWT Visual Oracle contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.visualoracle.catalog;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 * Deterministic specimen images drawn from fixed geometry and system colors,
 * never loaded from files or derived from anything environment dependent.
 */
final class CatalogImages {

	private CatalogImages() {
	}

	/**
	 * A small square icon: white ground, blue disc, yellow corner. The same
	 * pixels on every call, so any rendering difference comes from the widget
	 * under test, not from the image content.
	 */
	static Image icon(Display display, int size) {
		Image image = new Image(display, size, size);
		GC gc = new GC(image);
		try {
			gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
			gc.fillRectangle(0, 0, size, size);
			gc.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
			gc.fillOval(size / 4, size / 4, size / 2, size / 2);
			gc.setBackground(display.getSystemColor(SWT.COLOR_YELLOW));
			gc.fillRectangle(0, 0, size / 3, size / 3);
		} finally {
			gc.dispose();
		}
		return image;
	}

	/** Disposes the image when the control is disposed. */
	static void disposeWith(Control control, Image image) {
		control.addDisposeListener(e -> image.dispose());
	}
}
