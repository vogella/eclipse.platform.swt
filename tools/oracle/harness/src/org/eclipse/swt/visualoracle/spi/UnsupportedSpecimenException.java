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
 * Thrown by {@link Capture#capture(Specimen, Backend, RenderEnv)} when called
 * for a specimen whose backend has {@link Backend#supports(Specimen)} ==
 * false, or that cannot be rendered at all in this process. Callers should
 * check {@code supports} first and report status {@code UNSUPPORTED}.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public class UnsupportedSpecimenException extends CaptureFailedException {

	private static final long serialVersionUID = 1L;

	public UnsupportedSpecimenException(String message) {
		super(message);
	}
}
