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
package org.eclipse.swt.visualoracle.impl;

import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Theme;

/**
 * Reads the rendering environment the current process is actually pinned to.
 * Task T05 replaces the reading logic with real environment control; the SPI
 * shape stays.
 */
public final class SwtRenderEnvs {

	private SwtRenderEnvs() {
	}

	/** The environment of this process, read from {@code display}. */
	public static RenderEnv current(Display display) {
		int dpi = Math.max(1, display.getDPI().x);
		int zoom = (int) Math.round(dpi * 100.0 / 96.0);
		String gtkTheme = System.getenv("GTK_THEME");
		Theme theme = new Theme(gtkTheme == null ? "" : gtkTheme);
		FontData font = display.getSystemFont().getFontData()[0];
		return new RenderEnv(zoom, theme, Direction.LTR, font.getName(), font.getHeight());
	}
}
