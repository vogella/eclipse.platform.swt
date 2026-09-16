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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The label family: plain text in every alignment, image labels, disabled,
 * wrapped with border, and both separator orientations.
 */
public class LabelModule implements SpecimenModule {

	@Override
	public String family() {
		return "label";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new Text(), new Center(), new Right(), new ImageOnly(), new ImageText(),
				new WrapBorder(), new Disabled(),
				new SeparatorHorizontal(), new SeparatorVertical());
	}

	private static Label createLabel(Composite parent, int style, SpecimenContext ctx, String text) {
		Label label = new Label(parent, style);
		if (text != null)
			label.setText(text);
		ctx.configure(label);
		return label;
	}

	public static final class Text extends FamilySpecimen {
		public Text() {
			super("label.default", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLabel(parent, SWT.NONE, ctx, "Full name:");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Center extends FamilySpecimen {
		public Center() {
			super("label.center", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLabel(parent, SWT.CENTER, ctx, "Full name:");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Right extends FamilySpecimen {
		public Right() {
			super("label.right", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLabel(parent, SWT.RIGHT, ctx, "Full name:");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ImageOnly extends FamilySpecimen {
		public ImageOnly() {
			super("label.image", 48, 48);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Label label = createLabel(parent, SWT.NONE, ctx, null);
			Image icon = CatalogImages.icon(parent.getDisplay(), 32);
			CatalogImages.disposeWith(label, icon);
			label.setImage(icon);
			return label;
		}
	}

	public static final class ImageText extends FamilySpecimen {
		public ImageText() {
			super("label.image.text", 180, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Label label = createLabel(parent, SWT.NONE, ctx, "Archive");
			Image icon = CatalogImages.icon(parent.getDisplay(), 24);
			CatalogImages.disposeWith(label, icon);
			label.setImage(icon);
			return label;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class WrapBorder extends FamilySpecimen {
		public WrapBorder() {
			super("label.wrap.border", 140, 80);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLabel(parent, SWT.WRAP | SWT.BORDER, ctx,
					"A wrapped paragraph of static text that needs more than one line");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Disabled extends FamilySpecimen {
		public Disabled() {
			super("label.disabled", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Label label = createLabel(parent, SWT.NONE, ctx, "Full name:");
			label.setEnabled(false);
			return label;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class SeparatorHorizontal extends FamilySpecimen {
		public SeparatorHorizontal() {
			super("label.separator.horizontal", 160, 12);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLabel(parent, SWT.SEPARATOR | SWT.HORIZONTAL, ctx, null);
		}
	}

	public static final class SeparatorVertical extends FamilySpecimen {
		public SeparatorVertical() {
			super("label.separator.vertical", 12, 72);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLabel(parent, SWT.SEPARATOR | SWT.VERTICAL, ctx, null);
		}
	}
}
