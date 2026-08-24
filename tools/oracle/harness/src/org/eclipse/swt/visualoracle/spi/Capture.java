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
 * Produces the pixels of a rendered specimen.
 *
 * The reference strategy, decided by ADR-001, is {@code GC.copyArea} from the
 * on-screen control; task T04 owns the production capture runtime (shell
 * management, event loop settling, forced redraw, xgrab fallback) and will
 * implement this interface against that decision. Until then the harness
 * ships a minimal {@code copyarea} implementation so the pipeline runs end to
 * end.
 *
 * Implementations must be deterministic: two calls with the same specimen,
 * backend and environment produce identical images.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public interface Capture {

	/**
	 * Renders {@code specimen} through {@code backend} under {@code env} and
	 * captures the resulting on-screen pixels of exactly the control's bounds.
	 *
	 * @throws UnsupportedEnvironmentException if {@code env} does not match the
	 *     environment this process was started with
	 * @throws UnsupportedSpecimenException if the backend cannot render this
	 *     specimen ({@link Backend#supports(Specimen)} false or equivalent)
	 * @throws CaptureFailedException for any other capture failure; the run
	 *     records status {@code FAILED} and continues
	 */
	CapturedImage capture(Specimen specimen, Backend backend, RenderEnv env);
}
