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
 * Outcome of one comparison between two captured images.
 * Serialised verbatim into result JSON.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public enum Verdict {

	/** Bit-identical images, zero differing pixels. */
	EQUAL,

	/** Differences exist but stay within the requested tolerance. */
	WITHIN_TOLERANCE,

	/** Differences exceed the requested tolerance. */
	DIFFERENT
}
