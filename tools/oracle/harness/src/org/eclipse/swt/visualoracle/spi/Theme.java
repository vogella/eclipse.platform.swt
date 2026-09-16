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

/**
 * Identifies a widget theme of a rendering environment. An empty id means the
 * platform default theme.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public record Theme(String id) {

	/** The platform default theme, whatever the machine is configured with. */
	public static final Theme PLATFORM_DEFAULT = new Theme("");

	public Theme {
		id = id == null ? "" : id;
	}

	/** Returns true for the platform default theme. */
	public boolean isPlatformDefault() {
		return id.isEmpty();
	}

	@Override
	public String toString() {
		return id;
	}
}
