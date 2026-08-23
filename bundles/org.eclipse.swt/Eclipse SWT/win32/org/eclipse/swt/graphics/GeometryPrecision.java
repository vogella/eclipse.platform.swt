/*******************************************************************************
 * Copyright (c) 2026 Lars Vogel <Lars.Vogel@vogella.com> and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Lars Vogel <Lars.Vogel@vogella.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.graphics;

/**
 * Defines how a win32 drawing operation converts point coordinates to device pixels.
 * <p>
 * SNAPPED rounds coordinates to whole device pixels, keeping one-pixel
 * decorations such as focus rectangles, gridlines and separators crisp.
 * FRACTIONAL keeps the fractional device coordinate so shapes stay precise at
 * fractional zoom levels; it is not implemented yet.
 */
enum GeometryPrecision {
	SNAPPED,
	FRACTIONAL;
}
