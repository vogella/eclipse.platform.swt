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

import org.eclipse.swt.widgets.Control;

/**
 * Services the harness hands to a {@link Specimen} while it creates its
 * control.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public interface SpecimenContext {

	/** The environment this process is pinned to. */
	RenderEnv env();

	/**
	 * Applies the environment to a freshly created control: text orientation
	 * and base font. Every specimen must call this exactly once on its control
	 * so that all backends render under identical settings.
	 */
	void configure(Control control);
}
