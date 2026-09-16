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
package org.eclipse.swt.visualoracle.catalog;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.visualoracle.spi.Specimen;

/**
 * Shared plumbing for catalog specimens: a stable id and a fixed preferred
 * size, both decided once per specimen and never derived from the live
 * widget, so discovery needs no Display.
 */
abstract class FamilySpecimen implements Specimen {

	private final String id;
	private final int width;
	private final int height;

	FamilySpecimen(String id, int width, int height) {
		this.id = id;
		this.width = width;
		this.height = height;
	}

	@Override
	public final String id() {
		return id;
	}

	@Override
	public final Point preferredSize() {
		return new Point(width, height);
	}

	@Override
	public String toString() {
		return id;
	}
}
