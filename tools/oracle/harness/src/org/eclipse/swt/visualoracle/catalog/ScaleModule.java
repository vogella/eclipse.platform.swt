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
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The scale family: horizontal and vertical over the full value range.
 *
 * The extremes are deliberate: a thumb drawn one pixel off at minimum or
 * maximum is exactly the defect this family exists to catch. The page
 * increment variant covers non-default keyboard step settings; on GTK it is
 * pixel-identical to the midpoint (increments are behavior, not chrome), but
 * custom-drawn backends may render it differently.
 * <p>
 * Every specimen carries {@link Tag#FOCUS_SENSITIVE}: scales are focusable
 * and keyboard-driven, so whether the shell's auto-focus lands here can be
 * visible on themes that paint focus rings.
 */
public class ScaleModule implements SpecimenModule {

	static final int HORIZONTAL_WIDTH = 160;
	static final int HORIZONTAL_HEIGHT = 64;
	static final int VERTICAL_WIDTH = 64;
	static final int VERTICAL_HEIGHT = 180;

	@Override
	public String family() {
		return "scale";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new HorizontalMinimum(), new HorizontalMidpoint(), new HorizontalMaximum(),
				new VerticalMinimum(), new VerticalMidpoint(), new VerticalMaximum(),
				new HorizontalPageIncrement(),
				new Disabled());
	}

	static Scale createScale(Composite parent, int style, SpecimenContext ctx,
			int selection, int increment, int pageIncrement) {
		Scale scale = new Scale(parent, style);
		scale.setMinimum(0);
		scale.setMaximum(100);
		scale.setIncrement(increment);
		scale.setPageIncrement(pageIncrement);
		scale.setSelection(selection);
		ctx.configure(scale);
		return scale;
	}

	private abstract static class Horizontal extends FamilySpecimen {
		Horizontal(String suffix, int selection) {
			super("scale.horizontal." + suffix, HORIZONTAL_WIDTH, HORIZONTAL_HEIGHT);
			this.selection = selection;
		}

		private final int selection;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createScale(parent, SWT.HORIZONTAL, ctx, selection, 5, 20);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class HorizontalMinimum extends Horizontal {
		public HorizontalMinimum() {
			super("minimum", 0);
		}
	}

	public static final class HorizontalMidpoint extends Horizontal {
		public HorizontalMidpoint() {
			super("midpoint", 50);
		}
	}

	public static final class HorizontalMaximum extends Horizontal {
		public HorizontalMaximum() {
			super("maximum", 100);
		}
	}

	private abstract static class Vertical extends FamilySpecimen {
		Vertical(String suffix, int selection) {
			super("scale.vertical." + suffix, VERTICAL_WIDTH, VERTICAL_HEIGHT);
			this.selection = selection;
		}

		private final int selection;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createScale(parent, SWT.VERTICAL, ctx, selection, 5, 20);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class VerticalMinimum extends Vertical {
		public VerticalMinimum() {
			super("minimum", 0);
		}
	}

	public static final class VerticalMidpoint extends Vertical {
		public VerticalMidpoint() {
			super("midpoint", 50);
		}
	}

	public static final class VerticalMaximum extends Vertical {
		public VerticalMaximum() {
			super("maximum", 100);
		}
	}

	/** Non-default keyboard stepping; required coverage even if chrome-neutral. */
	public static final class HorizontalPageIncrement extends Horizontal {
		public HorizontalPageIncrement() {
			super("pageincrement", 50);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createScale(parent, SWT.HORIZONTAL, ctx, 50, 25, 50);
		}
	}

	public static final class Disabled extends Horizontal {
		public Disabled() {
			super("disabled", 50);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Scale scale = createScale(parent, SWT.HORIZONTAL, ctx, 50, 5, 20);
			scale.setEnabled(false);
			return scale;
		}
	}
}
