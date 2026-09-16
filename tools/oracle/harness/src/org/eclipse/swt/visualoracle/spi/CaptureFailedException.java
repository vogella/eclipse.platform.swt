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
 * Thrown when a {@link Capture} cannot produce an image. The harness records
 * the specimen as status {@code FAILED} with the exception message and
 * continues with the rest of the run; a failed capture is data, not a crash.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public class CaptureFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CaptureFailedException(String message) {
		super(message);
	}

	public CaptureFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
