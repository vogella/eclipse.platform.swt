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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;

/**
 * The progress bar family: horizontal and vertical at the value extremes and
 * midpoint, plus the SMOOTH style and the PAUSED and ERROR states.
 *
 * Deliberately no {@code SWT.INDETERMINATE} specimen: it animates forever
 * (SWT pulses it on a 100 ms timer) and can never be captured
 * deterministically. Measured on this stack through the real capture runtime,
 * it fails loudly instead of settling: {@code CaptureFailedException}
 * "never held one rendering stable for 250 ms within 5000 ms and 60 grabs".
 * A renderer change to indeterminate drawing therefore needs a dedicated
 * freeze mechanism first and is out of scope here.
 * <p>
 * PAUSED, ERROR and SMOOTH are pixel-identical to the plain variant on GTK
 * ({@code ProgressBar.setState} is a documented no-op there and SMOOTH is
 * Windows chrome; all three measured AE=0 against {@code horizontal.50}),
 * but they stay in the catalog as distinct API surface: Win32 renders them
 * differently and a custom-drawn backend may implement them, which is
 * exactly what a cross-backend comparison should surface.
 * <p>
 * No {@link org.eclipse.swt.visualoracle.spi.Tag#FOCUS_SENSITIVE}: progress
 * bars force {@code SWT.NO_FOCUS}.
 */
public class ProgressBarModule implements SpecimenModule {

	static final int HORIZONTAL_WIDTH = 160;
	static final int HORIZONTAL_HEIGHT = 28;
	static final int VERTICAL_WIDTH = 28;
	static final int VERTICAL_HEIGHT = 160;

	@Override
	public String family() {
		return "progressbar";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new Bar("horizontal.0", SWT.HORIZONTAL, 0, 0),
				new Bar("horizontal.50", SWT.HORIZONTAL, 50, 0),
				new Bar("horizontal.100", SWT.HORIZONTAL, 100, 0),
				new Bar("vertical.0", SWT.VERTICAL, 0, 0),
				new Bar("vertical.50", SWT.VERTICAL, 50, 0),
				new Bar("vertical.100", SWT.VERTICAL, 100, 0),
				new Bar("smooth.50", SWT.HORIZONTAL | SWT.SMOOTH, 50, 0),
				new Bar("paused.50", SWT.HORIZONTAL, 50, SWT.PAUSED),
				new Bar("error.50", SWT.HORIZONTAL, 50, SWT.ERROR));
	}

	private static final class Bar extends FamilySpecimen {
		/** State 0 is {@link SWT#NORMAL}, which never changes rendering. */
		Bar(String suffix, int style, int percent, int state) {
			super("progressbar." + suffix,
					(style & SWT.VERTICAL) != 0 ? VERTICAL_WIDTH : HORIZONTAL_WIDTH,
					(style & SWT.VERTICAL) != 0 ? VERTICAL_HEIGHT : HORIZONTAL_HEIGHT);
			this.style = style;
			this.percent = percent;
			this.state = state;
		}

		private final int style;
		private final int percent;
		private final int state;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.ProgressBar bar =
					new org.eclipse.swt.widgets.ProgressBar(parent, style);
			bar.setMinimum(0);
			bar.setMaximum(100);
			bar.setSelection(percent);
			if (state != 0)
				bar.setState(state);
			ctx.configure(bar);
			return bar;
		}
	}
}
