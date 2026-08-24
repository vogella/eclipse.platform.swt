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
 * Compares two captures of the same specimen under one tolerance.
 *
 * The reference implementation reports exact equality plus a changed-pixel
 * count; task T06 replaces it with metrics, anti-alias-tolerant comparison
 * and cluster detection behind this same interface. Implementations must not
 * mutate the images they are given and must be deterministic.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public interface Differ {

	/**
	 * Compares {@code reference} (the oracle side, normally native) with
	 * {@code candidate}. Both must have identical dimensions; a size mismatch
	 * is a DIFFERENT result covering every pixel, not an exception.
	 */
	DiffResult compare(CapturedImage reference, CapturedImage candidate, Tolerance tolerance);
}
