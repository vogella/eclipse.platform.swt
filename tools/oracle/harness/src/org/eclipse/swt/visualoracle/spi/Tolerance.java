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
 * How much pixel difference a {@link Differ} may excuse.
 *
 * A pixel counts as changed when any of its channels differs by more than
 * {@code maxChannelDelta} (0..255). {@code maxChangedFraction} (0..1) is the
 * share of changed pixels above which the result is a hard DIFFERENT verdict;
 * values at or below it are WITHIN_TOLERANCE.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public record Tolerance(int maxChannelDelta, double maxChangedFraction) {

	/** No difference excused: bit-identical comparison. */
	public static final Tolerance EXACT = new Tolerance(0, 0.0);

	public Tolerance {
		if (maxChannelDelta < 0 || maxChannelDelta > 255)
			throw new IllegalArgumentException("maxChannelDelta must be 0..255: " + maxChannelDelta);
		if (maxChangedFraction < 0.0 || maxChangedFraction > 1.0)
			throw new IllegalArgumentException("maxChangedFraction must be 0..1: " + maxChangedFraction);
	}
}
