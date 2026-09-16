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
 * The list family: single and multi selection each with and without a
 * selection, the disabled list, and the overflowing list whose clipped
 * viewport is part of what gets compared.
 * <p>
 * Compact specimens carry few items so they fit their bounds completely and
 * no scrollbar ever enters their rendering; only {@code list.overflow.items}
 * overflows, deliberately. Selections are set in {@code create} before the
 * widget is realized and tagged {@link Tag#FOCUS_SENSITIVE}, because the
 * capture shell focuses its lone child and a focused selection draws
 * differently.
 */
public class ListModule implements SpecimenModule {

	@Override
	public String family() {
		return "list";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new SingleDefault(), new SingleSelected(),
				new MultiDefault(), new MultiSelected(),
				new DisabledItems(), new OverflowItems());
	}

	static org.eclipse.swt.widgets.List createList(Composite parent, int style, SpecimenContext ctx) {
		org.eclipse.swt.widgets.List list = new org.eclipse.swt.widgets.List(parent, style);
		ctx.configure(list);
		return list;
	}

	private static void fill(org.eclipse.swt.widgets.List list) {
		list.setItems(new String[] { "Alpha", "Bravo", "Charlie", "Delta" });
	}

	public static final class SingleDefault extends FamilySpecimen {
		public SingleDefault() {
			super("list.single.default", 160, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.List list = createList(parent, SWT.SINGLE | SWT.BORDER, ctx);
			fill(list);
			return list;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class SingleSelected extends FamilySpecimen {
		public SingleSelected() {
			super("list.single.selected", 160, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.List list = createList(parent, SWT.SINGLE | SWT.BORDER, ctx);
			fill(list);
			list.setSelection(1);
			return list;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class MultiDefault extends FamilySpecimen {
		public MultiDefault() {
			super("list.multi.default", 160, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.List list = createList(parent, SWT.MULTI | SWT.BORDER, ctx);
			fill(list);
			return list;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class MultiSelected extends FamilySpecimen {
		public MultiSelected() {
			super("list.multi.selected", 160, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.List list = createList(parent, SWT.MULTI | SWT.BORDER, ctx);
			fill(list);
			list.setSelection(new int[] { 0, 2 });
			return list;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class DisabledItems extends FamilySpecimen {
		public DisabledItems() {
			super("list.disabled.items", 160, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.List list = createList(parent, SWT.SINGLE | SWT.BORDER, ctx);
			fill(list);
			list.setEnabled(false);
			return list;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	/**
	 * Thirty items in a viewport for roughly five: content overflows, rows
	 * clip at the bottom edge, and the scrolled chrome region is part of what
	 * gets compared.
	 * <p>
	 * V_SCROLL switches the internal GtkScrolledWindow from
	 * GTK_POLICY_NEVER to GTK_POLICY_AUTOMATIC and classic scrollbar mode
	 * keeps the bar permanently mapped, so on platforms where classic bars
	 * render normally they show here. Measured under Yaru on GTK3/Xvfb:
	 * the GTK scrollbar paints no pixels at rest in any combination
	 * of overlay and classic mode, so on this stack the specimen covers
	 * overflow clipping only, deterministically.
	 */
	public static final class OverflowItems extends FamilySpecimen {
		private static final String[] MANY = {
				"Alpha", "Bravo", "Charlie", "Delta", "Echo",
				"Foxtrot", "Golf", "Hotel", "India", "Juliett",
				"Kilo", "Lima", "Mike", "November", "Oscar",
				"Papa", "Quebec", "Romeo", "Sierra", "Tango",
				"Uniform", "Victor", "Whiskey", "Xray", "Yankee",
				"Zulu", "Amber", "Basalt", "Cobalt", "Dolomite" };

		public OverflowItems() {
			super("list.overflow.items", 160, 110);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			org.eclipse.swt.widgets.List list = createList(parent,
					SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL, ctx);
			list.setScrollbarsMode(SWT.NONE);
			list.setItems(MANY);
			return list;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
