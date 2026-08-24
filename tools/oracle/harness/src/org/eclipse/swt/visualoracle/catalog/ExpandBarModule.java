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
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.ExpandBar;
import org.eclipse.swt.widgets.ExpandItem;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The expand bar family: all headers collapsed against one and both items
 * expanded, and header images.
 * <p>
 * Each item hosts a fixed-text {@link Label} with a fixed pixel height, so
 * expansion state changes exactly the chrome around it, not the content.
 * Expansion is applied in {@code create} before the widget is realized, so
 * nothing animates after map; the capture runtime's stability window absorbs
 * any theme transition the expander state triggers anyway.
 */
public class ExpandBarModule implements SpecimenModule {

	@Override
	public String family() {
		return "expandbar";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new CollapsedDefault(), new ExpandedFirst(),
				new ExpandedBoth(), new HeaderImages());
	}

	static ExpandBar createBar(Composite parent, int style, SpecimenContext ctx) {
		ExpandBar bar = new ExpandBar(parent, style);
		ctx.configure(bar);
		return bar;
	}

	private static void addItem(ExpandBar bar, String title, String contentText, int contentHeight) {
		ExpandItem item = new ExpandItem(bar, SWT.NONE);
		item.setText(title);
		Label content = new Label(bar, SWT.BORDER);
		content.setText(contentText);
		item.setHeight(contentHeight);
		item.setControl(content);
	}

	public static final class CollapsedDefault extends FamilySpecimen {
		public CollapsedDefault() {
			super("expandbar.collapsed.default", 200, 110);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ExpandBar bar = createBar(parent, SWT.NONE, ctx);
			addItem(bar, "Appearance", "Theme and colors panel", 48);
			addItem(bar, "Sounds", "System sounds panel", 48);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ExpandedFirst extends FamilySpecimen {
		public ExpandedFirst() {
			super("expandbar.expanded.first", 200, 170);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ExpandBar bar = createBar(parent, SWT.NONE, ctx);
			addItem(bar, "Appearance", "Theme and colors panel", 48);
			addItem(bar, "Sounds", "System sounds panel", 48);
			bar.getItem(0).setExpanded(true);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ExpandedBoth extends FamilySpecimen {
		public ExpandedBoth() {
			super("expandbar.expanded.both", 200, 230);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ExpandBar bar = createBar(parent, SWT.NONE, ctx);
			addItem(bar, "Appearance", "Theme and colors panel", 48);
			addItem(bar, "Sounds", "System sounds panel", 48);
			bar.getItem(0).setExpanded(true);
			bar.getItem(1).setExpanded(true);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class HeaderImages extends FamilySpecimen {
		public HeaderImages() {
			super("expandbar.images.expanded", 220, 190);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ExpandBar bar = createBar(parent, SWT.NONE, ctx);
			addItem(bar, "Network", "Wired and proxy settings", 48);
			addItem(bar, "Privacy", "Screen lock settings", 48);
			Image icon = CatalogImages.icon(bar.getDisplay(), 16);
			CatalogImages.disposeWith(bar, icon);
			bar.getItem(0).setImage(icon);
			bar.getItem(1).setImage(icon);
			bar.getItem(1).setExpanded(true);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
