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
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The link family: markup with hyperlinks, plain text, multiline, disabled.
 * All strings are fixed; no link is activated, so no network is involved.
 */
public class LinkModule implements SpecimenModule {

	@Override
	public String family() {
		return "link";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(new Markup(), new Plain(), new Multiline(), new Disabled());
	}

	private static Link createLink(Composite parent, SpecimenContext ctx, String text) {
		Link link = new Link(parent, SWT.NONE);
		link.setText(text);
		ctx.configure(link);
		return link;
	}

	public static final class Markup extends FamilySpecimen {
		public Markup() {
			super("link.markup", 240, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLink(parent, ctx,
					"See the <a href=\"https://example.org/docs\">documentation</a> for details");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Plain extends FamilySpecimen {
		public Plain() {
			super("link.plain", 240, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLink(parent, ctx, "Plain text without any markup at all");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Multiline extends FamilySpecimen {
		public Multiline() {
			super("link.multiline", 200, 64);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createLink(parent, ctx,
					"<a href=\"https://example.org/a\">First</a>\n<a href=\"https://example.org/b\">Second</a>");
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class Disabled extends FamilySpecimen {
		public Disabled() {
			super("link.disabled", 240, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Link link = createLink(parent, ctx,
					"See the <a href=\"https://example.org/docs\">documentation</a> for details");
			link.setEnabled(false);
			return link;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
