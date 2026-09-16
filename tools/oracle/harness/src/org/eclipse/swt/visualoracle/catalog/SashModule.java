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

import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;

/**
 * The sash family: both orientations and the smooth (live drag feedback)
 * style, each as the bare divider chrome with no hosted content.
 * <p>
 * A Sash owns no caret and takes keyboard focus only on interaction, none of
 * which happens during capture; its rendering is a fixed handle glyph.
 */
public class SashModule implements SpecimenModule {

	@Override
	public String family() {
		return "sash";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new HorizontalDefault(), new VerticalDefault(), new SmoothHorizontal());
	}

	static Sash createSash(Composite parent, int style, SpecimenContext ctx) {
		Sash sash = new Sash(parent, style);
		ctx.configure(sash);
		return sash;
	}

	public static final class HorizontalDefault extends FamilySpecimen {
		public HorizontalDefault() {
			super("sash.horizontal.default", 180, 32);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createSash(parent, SWT.HORIZONTAL, ctx);
		}
	}

	public static final class VerticalDefault extends FamilySpecimen {
		public VerticalDefault() {
			super("sash.vertical.default", 32, 180);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createSash(parent, SWT.VERTICAL, ctx);
		}
	}

	public static final class SmoothHorizontal extends FamilySpecimen {
		public SmoothHorizontal() {
			super("sash.smooth.horizontal", 180, 32);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createSash(parent, SWT.HORIZONTAL | SWT.SMOOTH, ctx);
		}
	}
}
