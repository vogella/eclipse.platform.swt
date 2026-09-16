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
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The tab folder family: two and three tabs, first and second tab selected,
 * images on the tab labels, and the bottom-edge tab position.
 * <p>
 * Every page hosts a fixed-text {@link Label} through a FillLayout, so any
 * pixel difference belongs to the folder chrome, never to the content.
 * Selection is applied in {@code create} before the widget is realized; the
 * non-default selections are tagged {@link Tag#FOCUS_SENSITIVE} because the
 * capture shell focuses its lone child and a focused notebook draws its
 * focus indicator on the selected tab.
 */
public class TabFolderModule implements SpecimenModule {

	@Override
	public String family() {
		return "tabfolder";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new TwoFirst(), new TwoSecond(),
				new ThreeSecond(),
				new TabImages(), new BottomTabs());
	}

	static TabFolder createFolder(Composite parent, int style, SpecimenContext ctx) {
		TabFolder folder = new TabFolder(parent, style);
		folder.setLayout(new FillLayout());
		ctx.configure(folder);
		return folder;
	}

	private static void addPage(TabFolder folder, String title, String contentText) {
		TabItem item = new TabItem(folder, SWT.NONE);
		item.setText(title);
		Label page = new Label(folder, SWT.NONE);
		page.setText(contentText);
		item.setControl(page);
	}

	private static Image attachIcon(TabFolder folder, int size) {
		Image icon = CatalogImages.icon(folder.getDisplay(), size);
		CatalogImages.disposeWith(folder, icon);
		return icon;
	}

	public static final class TwoFirst extends FamilySpecimen {
		public TwoFirst() {
			super("tabfolder.two.first", 220, 150);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			TabFolder folder = createFolder(parent, SWT.NONE, ctx);
			addPage(folder, "Source", "Source view contents");
			addPage(folder, "Preview", "Preview pane contents");
			return folder;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class TwoSecond extends FamilySpecimen {
		public TwoSecond() {
			super("tabfolder.two.second", 220, 150);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			TabFolder folder = createFolder(parent, SWT.NONE, ctx);
			addPage(folder, "Source", "Source view contents");
			addPage(folder, "Preview", "Preview pane contents");
			folder.setSelection(1);
			return folder;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class ThreeSecond extends FamilySpecimen {
		public ThreeSecond() {
			super("tabfolder.three.second", 260, 150);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			TabFolder folder = createFolder(parent, SWT.NONE, ctx);
			addPage(folder, "Editor", "Editor area contents");
			addPage(folder, "Outline", "Outline view contents");
			addPage(folder, "Console", "Console output contents");
			folder.setSelection(1);
			return folder;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class TabImages extends FamilySpecimen {
		public TabImages() {
			super("tabfolder.images.default", 260, 150);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			TabFolder folder = createFolder(parent, SWT.NONE, ctx);
			addPage(folder, "Projects", "Projects tree contents");
			addPage(folder, "Markers", "Markers list contents");
			Image icon = attachIcon(folder, 16);
			for (TabItem item : folder.getItems())
				item.setImage(icon);
			return folder;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class BottomTabs extends FamilySpecimen {
		public BottomTabs() {
			super("tabfolder.bottom.default", 220, 150);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			TabFolder folder = createFolder(parent, SWT.BOTTOM, ctx);
			addPage(folder, "Tasks", "Task list contents");
			addPage(folder, "History", "History view contents");
			return folder;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
