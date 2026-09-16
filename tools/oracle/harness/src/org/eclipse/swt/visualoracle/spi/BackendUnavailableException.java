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
 * Thrown by {@link Backend#configure(org.eclipse.swt.widgets.Display)} when a
 * backend cannot prove it is genuinely active. Never caught and swallowed by
 * the harness: without a proven-active backend every comparison would be
 * meaningless.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public class BackendUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public BackendUnavailableException(String message) {
		super(message);
	}

	public BackendUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
