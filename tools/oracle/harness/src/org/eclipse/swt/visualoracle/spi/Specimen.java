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

import java.util.Set;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * Declares one capturable widget configuration: what to render.
 *
 * A specimen is a pure factory. Given a parent composite it creates exactly
 * one control in one defined style and state and returns it. Specimens never
 * open shells, never run or pump the event loop, never capture pixels and
 * never dispose their own control; the capture runtime owns all of that.
 *
 * Rendering must be deterministic: two captures of one specimen under the
 * same environment must produce identical images. Anything that would break
 * that (wall-clock time, animation phase, hover, focus stealing) must be
 * avoided in {@link #create} or explicitly frozen.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public interface Specimen {

	/**
	 * Stable identifier, lowercase dot-separated, unique across the catalog,
	 * e.g. {@code "button.push.default"}. Results reference specimens by this
	 * string, so it must never change meaning.
	 */
	String id();

	/**
	 * The size the widget wants for capture, used by the capture runtime to
	 * size the control before rendering. Must not depend on a created control
	 * or an existing Display.
	 */
	Point preferredSize();

	/**
	 * Creates the specimen's single control inside {@code parent}. The
	 * implementation must call {@link SpecimenContext#configure(Control)} on
	 * the new control before returning it.
	 */
	Control create(Composite parent, SpecimenContext ctx);

	/** Properties of this specimen useful for filtering and reporting. */
	default Set<Tag> tags() {
		return Set.of();
	}
}
