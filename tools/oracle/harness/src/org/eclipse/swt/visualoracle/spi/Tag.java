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
 * Classification tags a specimen can carry so runs and reports can filter by
 * property instead of by widget type alone.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public enum Tag {

	/** Rendering depends noticeably on rendered text content. */
	TEXT_HEAVY,

	/** Content changes over time, so the specimen can never be captured stably. */
	ANIMATED,

	/** Appearance depends on keyboard focus ownership. */
	FOCUS_SENSITIVE,

	/** Pops up native windows outside its own bounds (menus, dropdowns). */
	NATIVE_POPUP
}
