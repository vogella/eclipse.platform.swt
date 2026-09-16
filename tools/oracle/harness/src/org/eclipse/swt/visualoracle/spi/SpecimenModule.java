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

import java.util.List;

/**
 * One widget family's contribution to the specimen catalog.
 *
 * Implementations live in {@code org.eclipse.swt.visualoracle.catalog}, have a
 * class name ending in {@code Module} and a public no-argument constructor.
 * {@code SpecimenCatalog} discovers them by scanning that package, so adding a
 * family never edits a shared registration file and parallel catalog work
 * never conflicts.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public interface SpecimenModule {

	/**
	 * Short family name, lowercase, matching the first segment of the ids this
	 * module contributes, e.g. {@code "button"}.
	 */
	String family();

	/**
	 * Every specimen of this family. Called once per process; must not create
	 * widgets, touch a Display or depend on one existing.
	 */
	List<Specimen> specimens();
}
