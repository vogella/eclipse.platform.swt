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
 * One connected region of differing pixels found by a {@link Differ}.
 * Coordinates are in image pixel space, origin top-left.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public record DiffCluster(int x, int y, int width, int height, long changedPixels) {

	public DiffCluster {
		if (x < 0 || y < 0)
			throw new IllegalArgumentException("cluster origin must be non-negative");
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("cluster extent must be positive");
		if (changedPixels < 0)
			throw new IllegalArgumentException("changedPixels must be non-negative");
	}
}
