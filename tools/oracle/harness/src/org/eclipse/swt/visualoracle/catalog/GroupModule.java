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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The group family: titled against untitled, a nested group inside a group,
 * and every border style Group accepts.
 * <p>
 * Children are fixed-text {@link Label}s at hard-coded bounds; nothing is
 * derived from font metrics at runtime, so the caption chrome is the only
 * thing that can differ between backends. On GTK the four shadow styles
 * render identically at rest (measured, see the T16 handoff), so those
 * specimens are deliberately kept as distinct API surface for cross-backend
 * comparison rather than dropped.
 */
public class GroupModule implements SpecimenModule {

	@Override
	public String family() {
		return "group";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new TextDefault(), new EmptyDefault(), new NestedChild(),
				new ShadowEtchedOut(), new ShadowIn(), new ShadowOut(),
				new BorderStyle());
	}

	static Group createGroup(Composite parent, int style, String text, SpecimenContext ctx) {
		Group group = new Group(parent, style);
		if (text != null)
			group.setText(text);
		ctx.configure(group);
		return group;
	}

	private static void addLabel(Group group, String text) {
		Label label = new Label(group, SWT.NONE);
		label.setText(text);
		label.setBounds(14, 24, 120, 22);
	}

	private static Set<Tag> textTags() {
		return Set.of(Tag.TEXT_HEAVY);
	}

	public static final class TextDefault extends FamilySpecimen {
		public TextDefault() {
			super("group.text.default", 170, 80);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Group group = createGroup(parent, SWT.NONE, "Settings", ctx);
			addLabel(group, "User name:");
			return group;
		}

		@Override
		public Set<Tag> tags() {
			return textTags();
		}
	}

	public static final class EmptyDefault extends FamilySpecimen {
		public EmptyDefault() {
			super("group.empty.default", 170, 80);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Group group = createGroup(parent, SWT.NONE, null, ctx);
			addLabel(group, "User name:");
			return group;
		}

		@Override
		public Set<Tag> tags() {
			return textTags();
		}
	}

	public static final class NestedChild extends FamilySpecimen {
		public NestedChild() {
			super("group.nested.child", 220, 150);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Group outer = createGroup(parent, SWT.NONE, "Outer", ctx);
			Group inner = createGroup(outer, SWT.NONE, "Inner", ctx);
			inner.setBounds(12, 26, 190, 105);
			Label innerLabel = new Label(inner, SWT.NONE);
			innerLabel.setText("Deep content");
			innerLabel.setBounds(12, 22, 130, 22);
			return outer;
		}

		@Override
		public Set<Tag> tags() {
			return textTags();
		}
	}

	private abstract static class ShadowVariant extends FamilySpecimen {
		ShadowVariant(String idSuffix, int style) {
			super("group.shadow." + idSuffix, 170, 80);
			this.style = style;
		}

		private final int style;

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Group group = createGroup(parent, style, "Settings", ctx);
			addLabel(group, "User name:");
			return group;
		}

		@Override
		public Set<Tag> tags() {
			return textTags();
		}
	}

	/** Same geometry as {@code group.text.default}, style differs only. */
	public static final class ShadowEtchedOut extends ShadowVariant {
		public ShadowEtchedOut() {
			super("etched.out", SWT.SHADOW_ETCHED_OUT);
		}
	}

	public static final class ShadowIn extends ShadowVariant {
		public ShadowIn() {
			super("in", SWT.SHADOW_IN);
		}
	}

	public static final class ShadowOut extends ShadowVariant {
		public ShadowOut() {
			super("out", SWT.SHADOW_OUT);
		}
	}

	public static final class BorderStyle extends FamilySpecimen {
		public BorderStyle() {
			super("group.border.style", 170, 80);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Group group = createGroup(parent, SWT.BORDER, "Settings", ctx);
			addLabel(group, "User name:");
			return group;
		}

		@Override
		public Set<Tag> tags() {
			return textTags();
		}
	}
}
