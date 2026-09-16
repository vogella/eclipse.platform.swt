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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The button family: PUSH, CHECK, RADIO, TOGGLE and ARROW in every direction,
 * each in the states that change rendering: enabled, with image, aligned,
 * bordered, flat, wrapped.
 * <p>
 * Deliberately no focus-state specimen: focus lands on the lone child when
 * the capture shell opens whether a specimen wants it or not, and it renders
 * pixel-neutral under GTK's focus-visible heuristic (verified for every style
 * here), so a focused variant would test nothing beyond these specimens.
 * <p>
 * The selected and disabled state variants were once dropped because GTK
 * themes animate those state changes with CSS transitions; the capture
 * runtime now proves captured pixels stable before returning them, so they
 * are back.
 */
public class ButtonModule implements SpecimenModule {

	@Override
	public String family() {
		return "button";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new Push(), new PushDisabled(), new PushImage(), new PushImageText(),
				new PushLeft(), new PushRight(), new PushBorder(), new PushFlat(),
				new PushWrap(),
				new Check(), new CheckSelected(), new CheckDisabled(), new CheckImage(),
				new CheckRight(),
				new Radio(), new RadioSelected(), new RadioDisabled(),
				new Toggle(), new ToggleSelected(),
				new ArrowUp(), new ArrowDown(), new ArrowLeft(), new ArrowRight());
	}

	static Button createButton(Composite parent, int style, SpecimenContext ctx, String text) {
		Button button = new Button(parent, style);
		if (text != null)
			button.setText(text);
		ctx.configure(button);
		return button;
	}

	private static void attachIcon(Button button, Composite parent, int size) {
		Image icon = CatalogImages.icon(parent.getDisplay(), size);
		CatalogImages.disposeWith(button, icon);
		button.setImage(icon);
	}

	/** Reference specimen moved here from impl, id unchanged. */
	public static final class Push extends FamilySpecimen {
		public static final String ID = "button.push.default";

		public Push() {
			super(ID, 140, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.PUSH, ctx, "OK");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushDisabled extends FamilySpecimen {
		public PushDisabled() {
			super("button.push.disabled", 140, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.PUSH, ctx, "Unavailable");
			button.setEnabled(false);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushImage extends FamilySpecimen {
		public PushImage() {
			super("button.push.image", 72, 56);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.PUSH, ctx, null);
			attachIcon(button, parent, 32);
			return button;
		}
	}

	public static final class PushImageText extends FamilySpecimen {
		public PushImageText() {
			super("button.push.image.text", 160, 48);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.PUSH, ctx, "Print");
			attachIcon(button, parent, 24);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushLeft extends FamilySpecimen {
		public PushLeft() {
			super("button.push.left", 180, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.PUSH | SWT.LEFT, ctx, "Go left");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushRight extends FamilySpecimen {
		public PushRight() {
			super("button.push.right", 180, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.PUSH | SWT.RIGHT, ctx, "Go right");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushBorder extends FamilySpecimen {
		public PushBorder() {
			super("button.push.border", 140, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.PUSH | SWT.BORDER, ctx, "Bordered");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushFlat extends FamilySpecimen {
		public PushFlat() {
			super("button.push.flat", 140, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.PUSH | SWT.FLAT, ctx, "Flat");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class PushWrap extends FamilySpecimen {
		public PushWrap() {
			super("button.push.wrap", 120, 80);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.PUSH | SWT.WRAP | SWT.CENTER, ctx,
					"This label is long enough to wrap over several lines");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Check extends FamilySpecimen {
		public Check() {
			super("button.check.default", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.CHECK, ctx, "Enable option");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class CheckSelected extends FamilySpecimen {
		public CheckSelected() {
			super("button.check.selected", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.CHECK, ctx, "Enable option");
			button.setSelection(true);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class CheckDisabled extends FamilySpecimen {
		public CheckDisabled() {
			super("button.check.disabled", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.CHECK, ctx, "Enable option");
			button.setEnabled(false);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class CheckImage extends FamilySpecimen {
		public CheckImage() {
			super("button.check.image", 200, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.CHECK, ctx, "With icon");
			attachIcon(button, parent, 16);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class CheckRight extends FamilySpecimen {
		public CheckRight() {
			super("button.check.right", 200, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.CHECK | SWT.RIGHT, ctx, "Right aligned");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Radio extends FamilySpecimen {
		public Radio() {
			super("button.radio.default", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.RADIO, ctx, "First choice");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class RadioSelected extends FamilySpecimen {
		public RadioSelected() {
			super("button.radio.selected", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.RADIO, ctx, "First choice");
			button.setSelection(true);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class RadioDisabled extends FamilySpecimen {
		public RadioDisabled() {
			super("button.radio.disabled", 160, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.RADIO, ctx, "Unavailable");
			button.setEnabled(false);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Toggle extends FamilySpecimen {
		public Toggle() {
			super("button.toggle.default", 140, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.TOGGLE, ctx, "Hold");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ToggleSelected extends FamilySpecimen {
		public ToggleSelected() {
			super("button.toggle.selected", 140, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = createButton(parent, SWT.TOGGLE, ctx, "Held down");
			button.setSelection(true);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	private abstract static class Arrow extends FamilySpecimen {
		Arrow(int direction, String idSuffix) {
			super("button.arrow." + idSuffix, 44, 44);
			this.direction = direction;
		}

		private final int direction;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createButton(parent, SWT.ARROW | direction, ctx, null);
		}
	}

	public static final class ArrowUp extends Arrow {
		public ArrowUp() {
			super(SWT.UP, "up");
		}
	}

	public static final class ArrowDown extends Arrow {
		public ArrowDown() {
			super(SWT.DOWN, "down");
		}
	}

	public static final class ArrowLeft extends Arrow {
		public ArrowLeft() {
			super(SWT.LEFT, "left");
		}
	}

	public static final class ArrowRight extends Arrow {
		public ArrowRight() {
			super(SWT.RIGHT, "right");
		}
	}
}
