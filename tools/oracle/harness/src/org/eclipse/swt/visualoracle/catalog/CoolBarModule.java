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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.CoolItem;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The cool bar family: one row, two items, an item row forced to wrap into
 * two rows because the declared item widths exceed the bar width, and the
 * locked bar against the same unlocked geometry.
 * <p>
 * Items host fixed-text {@link Label}s at fixed pixel sizes; nothing calls
 * {@code computeSize} or {@code pack}, so font matching cannot move
 * geometry between backends. The locked specimen shares its exact geometry
 * with {@code coolbar.two.items} so a pixel comparison isolates the style.
 */
public class CoolBarModule implements SpecimenModule {

	@Override
	public String family() {
		return "coolbar";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new OneRow(), new TwoItems(), new WrappedRows(), new LockedDefault());
	}

	private static void addItem(CoolBar bar, String text, int width, int height) {
		Label content = new Label(bar, SWT.BORDER);
		content.setText(text);
		CoolItem item = new CoolItem(bar, SWT.NONE);
		item.setControl(content);
		item.setPreferredSize(new Point(width, height));
		item.setMinimumSize(new Point(width / 2, height));
	}

	static CoolBar createBar(Composite parent, int style, SpecimenContext ctx) {
		CoolBar bar = new CoolBar(parent, style);
		ctx.configure(bar);
		return bar;
	}

	public static final class OneRow extends FamilySpecimen {
		public OneRow() {
			super("coolbar.one.row", 240, 72);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			CoolBar bar = createBar(parent, SWT.NONE, ctx);
			addItem(bar, "Alpha", 150, 44);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class TwoItems extends FamilySpecimen {
		public TwoItems() {
			super("coolbar.two.items", 300, 72);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			CoolBar bar = createBar(parent, SWT.NONE, ctx);
			addItem(bar, "Alpha", 120, 44);
			addItem(bar, "Bravo", 120, 44);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	/**
	 * Two items forced onto two rows with {@code setWrapIndices}, the API
	 * that decides the wrap explicitly; relying on item widths exceeding the
	 * bar width would not wrap at all, because the emulated bar wraps rows
	 * from minimum widths, not preferred ones.
	 */
	public static final class WrappedRows extends FamilySpecimen {
		public WrappedRows() {
			super("coolbar.wrapped.rows", 240, 128);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			CoolBar bar = createBar(parent, SWT.NONE, ctx);
			addItem(bar, "Alpha", 160, 48);
			addItem(bar, "Bravo", 160, 48);
			bar.setWrapIndices(new int[] { 1 });
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	/**
	 * The locked bar, same geometry as {@code coolbar.two.items}. Locking is
	 * a property on this emulated widget, not a style bit.
	 */
	public static final class LockedDefault extends FamilySpecimen {
		public LockedDefault() {
			super("coolbar.locked.default", 300, 72);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			CoolBar bar = createBar(parent, SWT.NONE, ctx);
			bar.setLocked(true);
			addItem(bar, "Alpha", 120, 44);
			addItem(bar, "Bravo", 120, 44);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
