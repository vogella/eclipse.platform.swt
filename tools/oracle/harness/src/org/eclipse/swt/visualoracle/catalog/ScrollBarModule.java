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
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The scroll bar family, captured through its scrollable host.
 *
 * Why the host and not the bar itself: {@code ScrollBar} extends
 * {@code Widget}, not {@code Control}, on GTK, so a specimen cannot return a
 * bare bar from {@code create} and the runtime cannot capture its bounds
 * alone. Each specimen therefore owns one {@link List} host whose overflow
 * forces real scroll bars, captured whole.
 *
 * Bars are pinned to classic (non-overlay) mode with
 * {@code setScrollbarsMode(SWT.NONE)}: overlay bars fade after scrolling and
 * were measured non-deterministic (two captures of one host disagreed), while
 * classic bars settle under the stability window.
 *
 * Measured limitation of this stack, kept rather than dropped: GTK paints a
 * static trough but no moving thumb here, so the right 14 px strip is
 * byte-identical across scroll positions (AE=0, 3 unique colors); the pixel
 * differences these specimens carry come from the scrolled content itself
 * (AE 7744 vertical, 23187 horizontal against their start positions). On
 * stacks whose themes paint thumbs, the same specimens cover the chrome too.
 */
public class ScrollBarModule implements SpecimenModule {

	static final int HOST_WIDTH = 220;
	static final int HOST_HEIGHT = 160;
	static final int ITEM_COUNT = 120;

	@Override
	public String family() {
		return "scrollbar";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new VerticalTop(), new VerticalScrolled(),
				new HorizontalStart(), new HorizontalScrolled(),
				new BothScrolled());
	}

	private abstract static class Hosted extends FamilySpecimen {
		Hosted(String suffix, int style, int topIndex, int hSelection) {
			super("scrollbar." + suffix, HOST_WIDTH, HOST_HEIGHT);
			this.style = style;
			this.topIndex = topIndex;
			this.hSelection = hSelection;
		}

		private final int style;
		private final int topIndex;
		private final int hSelection;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.List host = new org.eclipse.swt.widgets.List(parent, style | SWT.BORDER);
			for (int i = 0; i < ITEM_COUNT; i++)
				host.add("Item " + i + " some longer text to force horizontal overflow");
			host.setScrollbarsMode(SWT.NONE);
			ctx.configure(host);
			host.setBounds(0, 0, HOST_WIDTH, HOST_HEIGHT);
			if (topIndex > 0)
				host.setTopIndex(topIndex);
			if (hSelection > 0)
				host.getHorizontalBar().setSelection(hSelection);
			return host;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class VerticalTop extends Hosted {
		public VerticalTop() {
			super("vertical.top", SWT.V_SCROLL, 0, 0);
		}
	}

	public static final class VerticalScrolled extends Hosted {
		public VerticalScrolled() {
			super("vertical.scrolled", SWT.V_SCROLL, ITEM_COUNT - 10, 0);
		}
	}

	public static final class HorizontalStart extends Hosted {
		public HorizontalStart() {
			super("horizontal.start", SWT.H_SCROLL, 0, 0);
		}
	}

	public static final class HorizontalScrolled extends Hosted {
		public HorizontalScrolled() {
			super("horizontal.scrolled", SWT.H_SCROLL, 0, 150);
		}
	}

	public static final class BothScrolled extends Hosted {
		public BothScrolled() {
			super("both.scrolled", SWT.V_SCROLL | SWT.H_SCROLL, ITEM_COUNT - 10, 150);
		}
	}
}
