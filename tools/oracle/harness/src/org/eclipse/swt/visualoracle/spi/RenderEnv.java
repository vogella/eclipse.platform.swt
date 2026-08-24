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
package org.eclipse.swt.visualoracle.spi;

import java.util.Objects;

/**
 * The rendering environment a specimen is captured in: DPI zoom, theme, text
 * direction and base font.
 *
 * <h2>One process per environment</h2>
 * SWT reads zoom, theme and text direction once when the {@link
 * org.eclipse.swt.widgets.Display} is created. An environment is therefore not
 * something a running process can switch; it is pinned at process start.
 * Callers that want captures under several environments spawn one harness
 * process per environment (task T05 automates this). A {@link Capture} must
 * fail with {@link UnsupportedEnvironmentException} rather than silently
 * capture under a different environment than the one requested.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 *
 * @param zoomPercent device zoom as a percentage, 100 is unscaled
 * @param theme widget theme, never null, empty id means platform default
 * @param direction text base direction, never null
 * @param fontFamily base font family name, never null, empty means the
 *     platform system font is used unchanged
 * @param fontSize font size as reported by {@code FontData.getHeight()},
 *     never zero; positive values are points, negative values pixels,
 *     mirroring the FontData convention
 */
public record RenderEnv(int zoomPercent, Theme theme, Direction direction,
		String fontFamily, int fontSize) {

	public RenderEnv {
		Objects.requireNonNull(theme, "theme");
		Objects.requireNonNull(direction, "direction");
		Objects.requireNonNull(fontFamily, "fontFamily");
		if (zoomPercent <= 0)
			throw new IllegalArgumentException("zoomPercent must be positive: " + zoomPercent);
		if (fontSize == 0)
			throw new IllegalArgumentException("fontSize must not be zero");
	}

	/** True if this environment requests the platform default font. */
	public boolean usesSystemFont() {
		return fontFamily.isEmpty();
	}
}
