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
 * Thrown when a {@link Capture} is asked for an environment other than the one
 * the current process was started with. SWT pins zoom, theme and text
 * direction at Display creation, so the request cannot be honoured in-process;
 * the caller must spawn a separate process for the requested environment (see
 * {@link RenderEnv}).
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public class UnsupportedEnvironmentException extends CaptureFailedException {

	private static final long serialVersionUID = 1L;

	public UnsupportedEnvironmentException(String message) {
		super(message);
	}
}
