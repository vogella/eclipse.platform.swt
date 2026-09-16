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
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The tool bar family: push items, a dropdown item, check items including a
 * selected one, a separator, images, the flat and vertical styles, and a
 * disabled item among enabled ones.
 * <p>
 * No pointer interaction happens during capture, so no hover or prelight
 * state can enter the pixels; the capture runtime's stability window would
 * fail loudly if GTK animated one anyway. The checked specimen carries
 * {@link Tag#FOCUS_SENSITIVE} because the capture shell focuses its lone
 * child and a focused tool bar draws focus indicators on its items.
 */
public class ToolBarModule implements SpecimenModule {

	@Override
	public String family() {
		return "toolbar";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new PushText(), new PushImages(),
				new DropdownItem(), new CheckItems(),
				new SeparatorBetween(), new FlatStyle(),
				new VerticalStyle(), new DisabledItem());
	}

	static ToolBar createBar(Composite parent, int style, SpecimenContext ctx) {
		ToolBar bar = new ToolBar(parent, style);
		ctx.configure(bar);
		return bar;
	}

	private static Image attachIcon(ToolBar bar, int size) {
		Image icon = CatalogImages.icon(bar.getDisplay(), size);
		CatalogImages.disposeWith(bar, icon);
		return icon;
	}

	public static final class PushText extends FamilySpecimen {
		public PushText() {
			super("toolbar.push.text", 280, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.NONE, ctx);
			for (String label : new String[] { "Open", "Save", "Export" }) {
				new ToolItem(bar, SWT.PUSH).setText(label);
			}
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushImages extends FamilySpecimen {
		public PushImages() {
			super("toolbar.push.images", 140, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.NONE, ctx);
			Image icon = attachIcon(bar, 24);
			for (int i = 0; i < 3; i++)
				new ToolItem(bar, SWT.PUSH).setImage(icon);
			return bar;
		}
	}

	public static final class DropdownItem extends FamilySpecimen {
		public DropdownItem() {
			super("toolbar.dropdown.item", 240, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.NONE, ctx);
			new ToolItem(bar, SWT.PUSH).setText("Run");
			new ToolItem(bar, SWT.DROP_DOWN).setText("Target");
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class CheckItems extends FamilySpecimen {
		public CheckItems() {
			super("toolbar.check.items", 220, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.NONE, ctx);
			ToolItem first = new ToolItem(bar, SWT.CHECK);
			first.setText("Grid");
			first.setSelection(true);
			new ToolItem(bar, SWT.CHECK).setText("Ruler");
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class SeparatorBetween extends FamilySpecimen {
		public SeparatorBetween() {
			super("toolbar.separator.between", 300, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.NONE, ctx);
			new ToolItem(bar, SWT.PUSH).setText("Cut");
			new ToolItem(bar, SWT.PUSH).setText("Copy");
			ToolItem gap = new ToolItem(bar, SWT.SEPARATOR);
			gap.setWidth(24);
			new ToolItem(bar, SWT.PUSH).setText("Paste");
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class FlatStyle extends FamilySpecimen {
		public FlatStyle() {
			super("toolbar.flat.style", 260, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.FLAT, ctx);
			Image icon = attachIcon(bar, 16);
			ToolItem withImage = new ToolItem(bar, SWT.PUSH);
			withImage.setImage(icon);
			new ToolItem(bar, SWT.SEPARATOR).setWidth(20);
			ToolItem withText = new ToolItem(bar, SWT.PUSH);
			withText.setText("Build");
			withText.setImage(icon);
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class VerticalStyle extends FamilySpecimen {
		public VerticalStyle() {
			super("toolbar.vertical.style", 64, 230);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.VERTICAL, ctx);
			for (String label : new String[] { "Top", "Middle", "Last" }) {
				new ToolItem(bar, SWT.PUSH).setText(label);
			}
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class DisabledItem extends FamilySpecimen {
		public DisabledItem() {
			super("toolbar.disabled.item", 300, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar bar = createBar(parent, SWT.NONE, ctx);
			new ToolItem(bar, SWT.PUSH).setText("Before");
			ToolItem middle = new ToolItem(bar, SWT.PUSH);
			middle.setText("Locked");
			middle.setEnabled(false);
			new ToolItem(bar, SWT.PUSH).setText("After");
			return bar;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
