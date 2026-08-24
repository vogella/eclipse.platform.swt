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

import org.eclipse.swt.graphics.ImageData;

/**
 * One captured rendering of one specimen through one backend.
 *
 * Implementations hold plain image data, not live SWT resources; callers can
 * keep a CapturedImage after shells and displays are disposed.
 * {@link #pngBytes()} is the serialisation used in result files, so it must be
 * byte-identical for identical pixels.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public interface CapturedImage {

	int width();

	int height();

	/** The captured pixels. Callers must not modify the returned data. */
	ImageData imageData();

	/** PNG-encoded pixels, deterministic for identical pixel content. */
	byte[] pngBytes();
}
