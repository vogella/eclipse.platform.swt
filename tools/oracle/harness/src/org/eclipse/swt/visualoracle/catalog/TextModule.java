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
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The text family: single line and multi line, bordered and plain, with and
 * without content, placeholder message, read only, disabled, password echo,
 * search icons, alignments.
 *
 * Every enabled specimen's control owns a blinking caret when focused; all
 * are made unfocusable by {@link NoCaret} so no caret can exist and the
 * capture cannot depend on focus history or blink phase. Disabled controls
 * are insensitive and cannot take focus anyway, so they carry no
 * FOCUS_SENSITIVE tag.
 */
public class TextModule implements SpecimenModule {

	@Override
	public String family() {
		return "text";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new SingleEmpty(), new SingleContent(),
				new SingleBorderEmpty(), new SingleBorderContent(),
				new SingleMessage(), new SingleReadonly(), new SingleDisabled(),
				new SinglePassword(), new SearchEmpty(), new SearchContent(),
				new SingleRight(), new SingleCenter(),
				new MultiContent(), new MultiPlain());
	}

	private static Text createText(Composite parent, int style, SpecimenContext ctx, String content) {
		Text text = new Text(parent, style);
		if (content != null)
			text.setText(content);
		ctx.configure(text);
		NoCaret.ensure(text);
		return text;
	}

	private static final String CONTENT = "Hello oracle";

	public static final class SingleEmpty extends FamilySpecimen {
		public SingleEmpty() {
			super("text.single.empty", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE, ctx, null);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class SingleContent extends FamilySpecimen {
		public SingleContent() {
			super("text.single.content", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE, ctx, CONTENT);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class SingleBorderEmpty extends FamilySpecimen {
		public SingleBorderEmpty() {
			super("text.single.border.empty", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE | SWT.BORDER, ctx, null);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class SingleBorderContent extends FamilySpecimen {
		public SingleBorderContent() {
			super("text.single.border.content", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE | SWT.BORDER, ctx, CONTENT);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class SingleMessage extends FamilySpecimen {
		public SingleMessage() {
			super("text.single.message.empty", 220, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Text text = createText(parent, SWT.SINGLE | SWT.BORDER, ctx, null);
			text.setMessage("Type here");
			return text;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class SingleReadonly extends FamilySpecimen {
		public SingleReadonly() {
			super("text.single.readonly.content", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY, ctx, CONTENT);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class SingleDisabled extends FamilySpecimen {
		public SingleDisabled() {
			super("text.single.disabled.content", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Text text = createText(parent, SWT.SINGLE | SWT.BORDER, ctx, CONTENT);
			text.setEnabled(false);
			return text;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class SinglePassword extends FamilySpecimen {
		public SinglePassword() {
			super("text.single.password.content", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE | SWT.BORDER | SWT.PASSWORD, ctx, "secret");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class SearchEmpty extends FamilySpecimen {
		public SearchEmpty() {
			super("text.single.search.empty", 220, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SEARCH | SWT.BORDER, ctx, null);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class SearchContent extends FamilySpecimen {
		public SearchContent() {
			super("text.single.search.content", 220, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SEARCH | SWT.ICON_CANCEL | SWT.ICON_SEARCH | SWT.BORDER,
					ctx, "query");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class SingleRight extends FamilySpecimen {
		public SingleRight() {
			super("text.single.right.content", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE | SWT.BORDER | SWT.RIGHT, ctx, CONTENT);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class SingleCenter extends FamilySpecimen {
		public SingleCenter() {
			super("text.single.center.content", 180, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, SWT.SINGLE | SWT.BORDER | SWT.CENTER, ctx, CONTENT);
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	private abstract static class Multi extends FamilySpecimen {
		private static final String LINES = "One\nTwo\nThree\nFour\nFive\nSix\nSeven\nEight";

		Multi(String id) {
			super(id, 200, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createText(parent, style(), ctx, LINES);
		}

		abstract int style();

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.FOCUS_SENSITIVE, Tag.TEXT_HEAVY);
		}
	}

	public static final class MultiContent extends Multi {
		public MultiContent() {
			super("text.multi.border.content");
		}

		@Override
		int style() {
			return SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL;
		}
	}

	public static final class MultiPlain extends Multi {
		public MultiPlain() {
			super("text.multi.plain.content");
		}

		@Override
		int style() {
			return SWT.MULTI | SWT.WRAP | SWT.V_SCROLL;
		}
	}
}
