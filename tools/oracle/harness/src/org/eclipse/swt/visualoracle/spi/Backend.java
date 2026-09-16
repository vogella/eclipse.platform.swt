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

import org.eclipse.swt.widgets.Display;

/**
 * A rendering backend the harness can capture widgets through: an adapter in
 * front of one SWT rendering stack.
 *
 * Backend ids are fixed by {@code tools/oracle/build.sh}. Results reference
 * backends by id, so ids must never change meaning.
 *
 * Because each backend links its own natives, at most one backend is active
 * per process; running a comparison means merging results from several
 * single-backend processes.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public interface Backend {

	/** Stable identifier, see class documentation. */
	String id();

	/**
	 * Verifies the backend is genuinely active on {@code display} and prepares
	 * it, before any specimen is created. Must throw {@link
	 * BackendUnavailableException} when activation cannot be proven; a silent
	 * fall-back to native rendering would make comparisons report perfect
	 * agreement between identical renderers, the most dangerous failure this
	 * project has.
	 */
	void configure(Display display);

	/**
	 * Whether this backend renders the given specimen. A backend does not have
	 * to cover every widget; unsupported specimens are reported with status
	 * {@code UNSUPPORTED} instead of being counted as rendering defects.
	 */
	boolean supports(Specimen specimen);
}
