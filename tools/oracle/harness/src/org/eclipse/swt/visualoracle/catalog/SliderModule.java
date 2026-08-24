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
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The slider family: horizontal and vertical with the thumb at start,
 * middle and end, plus enlarged-thumb variants and the disabled state.
 *
 * The thumb positions are the reason this family exists: a thumb drawn one
 * pixel off at either extreme is exactly the renderer defect that only an
 * end-to-end capture catches. The thumblarge variants pin a page size far
 * above the default so thumb extent and thumb position can diverge.
 * <p>
 * Every specimen carries {@link Tag#FOCUS_SENSITIVE}: sliders are focusable
 * and keyboard-driven, so whether the shell's auto-focus lands here can be
 * visible on themes that paint focus rings.
 */
public class SliderModule implements SpecimenModule {

	static final int DEFAULT_THUMB = 10;
	static final int LARGE_THUMB = 40;

	static final int HORIZONTAL_WIDTH = 160;
	static final int HORIZONTAL_HEIGHT = 36;
	static final int VERTICAL_WIDTH = 36;
	static final int VERTICAL_HEIGHT = 160;

	@Override
	public String family() {
		return "slider";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new HorizontalStart(), new HorizontalMiddle(), new HorizontalEnd(),
				new HorizontalThumbLarge(),
				new VerticalStart(), new VerticalMiddle(), new VerticalEnd(),
				new VerticalThumbLarge(),
				new Disabled());
	}

	static Slider createSlider(Composite parent, int style, SpecimenContext ctx,
			int selection, int thumb) {
		Slider slider = new Slider(parent, style);
		slider.setMinimum(0);
		slider.setMaximum(100);
		slider.setIncrement(5);
		slider.setPageIncrement(20);
		if (thumb != DEFAULT_THUMB)
			slider.setThumb(thumb);
		slider.setSelection(selection);
		ctx.configure(slider);
		return slider;
	}

	private abstract static class Horizontal extends FamilySpecimen {
		Horizontal(String suffix, int selection, int thumb) {
			super("slider.horizontal." + suffix, HORIZONTAL_WIDTH, HORIZONTAL_HEIGHT);
			this.selection = selection;
			this.thumb = thumb;
		}

		private final int selection;
		private final int thumb;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createSlider(parent, SWT.HORIZONTAL, ctx, selection, thumb);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class HorizontalStart extends Horizontal {
		public HorizontalStart() {
			super("start", 0, DEFAULT_THUMB);
		}
	}

	public static final class HorizontalMiddle extends Horizontal {
		public HorizontalMiddle() {
			super("middle", 45, DEFAULT_THUMB);
		}
	}

	public static final class HorizontalEnd extends Horizontal {
		public HorizontalEnd() {
			super("end", 100 - DEFAULT_THUMB, DEFAULT_THUMB);
		}
	}

	public static final class HorizontalThumbLarge extends Horizontal {
		public HorizontalThumbLarge() {
			super("thumblarge", 30, LARGE_THUMB);
		}
	}

	private abstract static class Vertical extends FamilySpecimen {
		Vertical(String suffix, int selection, int thumb) {
			super("slider.vertical." + suffix, VERTICAL_WIDTH, VERTICAL_HEIGHT);
			this.selection = selection;
			this.thumb = thumb;
		}

		private final int selection;
		private final int thumb;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createSlider(parent, SWT.VERTICAL, ctx, selection, thumb);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class VerticalStart extends Vertical {
		public VerticalStart() {
			super("start", 0, DEFAULT_THUMB);
		}
	}

	public static final class VerticalMiddle extends Vertical {
		public VerticalMiddle() {
			super("middle", 45, DEFAULT_THUMB);
		}
	}

	public static final class VerticalEnd extends Vertical {
		public VerticalEnd() {
			super("end", 100 - DEFAULT_THUMB, DEFAULT_THUMB);
		}
	}

	public static final class VerticalThumbLarge extends Vertical {
		public VerticalThumbLarge() {
			super("thumblarge", 30, LARGE_THUMB);
		}
	}

	public static final class Disabled extends Horizontal {
		public Disabled() {
			super("disabled", 45, DEFAULT_THUMB);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Slider slider = createSlider(parent, SWT.HORIZONTAL, ctx, 45, DEFAULT_THUMB);
			slider.setEnabled(false);
			return slider;
		}
	}
}
