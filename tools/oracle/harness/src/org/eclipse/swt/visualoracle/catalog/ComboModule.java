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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The combo family: read only versus editable, with and without a selection,
 * disabled, and a narrow box holding an over-long item.
 *
 * Editable combos own an entry with a blinking caret; all specimens are made
 * unfocusable by {@link NoCaret} (the editable focus handle is the inner
 * GtkEntry) so no caret can exist. Disabled controls are insensitive and
 * cannot take focus anyway, so they carry no FOCUS_SENSITIVE tag. No popup
 * ever opens during capture, so NATIVE_POPUP does not apply.
 */
public class ComboModule implements SpecimenModule {

	@Override
	public String family() {
		return "combo";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new ReadonlyEmpty(), new ReadonlySelected(),
				new EditableEmpty(), new EditableText(), new EditableDisabled(),
				new NarrowLongItem());
	}

	private static Combo createCombo(Composite parent, int style, SpecimenContext ctx,
			String[] items, int selectionIndex, String text) {
		Combo combo = new Combo(parent, style);
		combo.setItems(items);
		if (selectionIndex >= 0)
			combo.select(selectionIndex);
		if (text != null)
			combo.setText(text);
		ctx.configure(combo);
		NoCaret.ensure(combo);
		return combo;
	}

	private static final String[] ITEMS = { "Alpha", "Bravo", "Charlie" };
	private static final String TYPED = "Typed text";
	private static final String LONG_ITEM =
			"An item whose text is far too long for the narrowed combo it sits in";

	public static final class ReadonlyEmpty extends FamilySpecimen {
		public ReadonlyEmpty() {
			super("combo.readonly.empty", 200, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCombo(parent, SWT.DROP_DOWN | SWT.READ_ONLY, ctx, ITEMS, -1, null);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class ReadonlySelected extends FamilySpecimen {
		public ReadonlySelected() {
			super("combo.readonly.selected", 200, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCombo(parent, SWT.DROP_DOWN | SWT.READ_ONLY, ctx, ITEMS, 1, null);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class EditableEmpty extends FamilySpecimen {
		public EditableEmpty() {
			super("combo.editable.empty", 200, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCombo(parent, SWT.DROP_DOWN, ctx, ITEMS, -1, null);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class EditableText extends FamilySpecimen {
		public EditableText() {
			super("combo.editable.text", 200, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCombo(parent, SWT.DROP_DOWN, ctx, ITEMS, -1, TYPED);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class EditableDisabled extends FamilySpecimen {
		public EditableDisabled() {
			super("combo.editable.disabled", 200, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Combo combo = createCombo(parent, SWT.DROP_DOWN, ctx, ITEMS, -1, TYPED);
			combo.setEnabled(false);
			return combo;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class NarrowLongItem extends FamilySpecimen {
		public NarrowLongItem() {
			super("combo.narrow.long.item", 110, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createCombo(parent, SWT.DROP_DOWN | SWT.READ_ONLY, ctx,
					new String[] { LONG_ITEM }, 0, null);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}
}
