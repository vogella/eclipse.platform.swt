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
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The CLabel family: text and image placement, the three shadow styles,
 * truncation of overlong text.
 * <p>
 * Deliberately no disabled specimen: CLabel never paints its enabled state
 * (it draws text and image without consulting {@code getEnabled}), verified
 * both in the source and by capture comparison; such a specimen would be
 * pixel-identical to {@code clabel.default}.
 */
public class CLabelModule implements SpecimenModule {

	private static final String LONG_TEXT =
			"A very long status message that cannot fit into the available width";

	@Override
	public String family() {
		return "clabel";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new Text(), new ImageRight(), new ImageCenter(),
				new ShadowIn(), new ShadowOut(),
				new Truncated());
	}

	private static CLabel createCLabel(Composite parent, int style, SpecimenContext ctx, String text) {
		CLabel label = new CLabel(parent, style);
		if (text != null)
			label.setText(text);
		ctx.configure(label);
		return label;
	}

	public static final class Text extends FamilySpecimen {
		public Text() {
			super("clabel.default", 160, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCLabel(parent, SWT.NONE, ctx, "Status:");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ImageRight extends FamilySpecimen {
		public ImageRight() {
			super("clabel.image.right", 200, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			CLabel label = createCLabel(parent, SWT.RIGHT, ctx, "Saved");
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

	public static final class ImageCenter extends FamilySpecimen {
		public ImageCenter() {
			super("clabel.image.center", 200, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			CLabel label = createCLabel(parent, SWT.CENTER, ctx, "Saved");
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

	public static final class ShadowIn extends FamilySpecimen {
		public ShadowIn() {
			super("clabel.shadow.in", 160, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCLabel(parent, SWT.SHADOW_IN, ctx, "Sunken");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ShadowOut extends FamilySpecimen {
		public ShadowOut() {
			super("clabel.shadow.out", 160, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCLabel(parent, SWT.SHADOW_OUT, ctx, "Raised");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Truncated extends FamilySpecimen {
		public Truncated() {
			super("clabel.truncated", 90, 32);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCLabel(parent, SWT.NONE, ctx, LONG_TEXT);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
