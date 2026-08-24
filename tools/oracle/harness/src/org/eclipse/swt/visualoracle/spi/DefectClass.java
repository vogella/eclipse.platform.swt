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
 * Probable root cause of a rendering difference. The mapping from difference
 * shape to class is task T07; until then only {@code NONE} and {@code UNKNOWN}
 * are produced.
 * Serialised verbatim into result JSON.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public enum DefectClass {

	/** No defect: images agree within tolerance. */
	NONE,

	/** A difference exists but its cause has not been classified. */
	UNKNOWN,

	/** Same content drawn at a shifted position. */
	SHIFTED,

	/** An element present in one image is absent in the other. */
	MISSING_ELEMENT,

	/** Content drawn with the wrong color. */
	WRONG_COLOR,

	/** Text rendered with wrong or substituted glyphs. */
	WRONG_GLYPH
}
