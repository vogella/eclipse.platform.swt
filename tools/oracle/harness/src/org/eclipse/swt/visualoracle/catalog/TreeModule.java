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
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The tree family: collapsed against expanded (the pair that catches an
 * expander arrow rendering wrongly only in one state), two level nesting,
 * check boxes including the grayed state, node selection, item images, and
 * the empty tree.
 * <p>
 * Expansion, selection, checks and images are all applied in {@code create}
 * before the widget is realized; nothing animates after map. The collapsed
 * and expanded specimens share one structure so a pixel diff between their
 * captures isolates exactly the expander and child rows.
 */
public class TreeModule implements SpecimenModule {

	@Override
	public String family() {
		return "tree";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new CollapsedDefault(), new ExpandedDefault(),
				new ExpandedChecked(), new NodeSelected(),
				new ItemImages(), new EmptyDefault());
	}

	static Tree createTree(Composite parent, int style, SpecimenContext ctx) {
		Tree tree = new Tree(parent, style);
		ctx.configure(tree);
		return tree;
	}

	private static Image newIcon(Tree tree, int size) {
		Image icon = CatalogImages.icon(tree.getDisplay(), size);
		CatalogImages.disposeWith(tree, icon);
		return icon;
	}

	/** Roots with two children each: the shared structure of the family. */
	private static void fillHierarchy(Tree tree) {
		String[] roots = { "Projects", "Modules", "Plugins", "Features" };
		for (int i = 0; i < roots.length; i++) {
			TreeItem root = new TreeItem(tree, SWT.NONE);
			root.setText(roots[i]);
			new TreeItem(root, SWT.NONE).setText("Source");
			new TreeItem(root, SWT.NONE).setText("Tests");
		}
	}

	public static final class CollapsedDefault extends FamilySpecimen {
		public CollapsedDefault() {
			super("tree.collapsed.default", 200, 160);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Tree tree = createTree(parent, SWT.NONE, ctx);
			fillHierarchy(tree);
			return tree;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ExpandedDefault extends FamilySpecimen {
		public ExpandedDefault() {
			super("tree.expanded.default", 200, 220);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Tree tree = createTree(parent, SWT.NONE, ctx);
			fillHierarchy(tree);
			expandAll(tree);
			return tree;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	private static void expandAll(Tree tree) {
		for (TreeItem root : tree.getItems()) {
			root.setExpanded(true);
		}
	}

	public static final class ExpandedChecked extends FamilySpecimen {
		public ExpandedChecked() {
			super("tree.expanded.checked", 210, 200);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Tree tree = createTree(parent, SWT.CHECK, ctx);
			fillHierarchy(tree);
			tree.getItem(0).setExpanded(true);
			tree.getItem(0).getItem(1).setChecked(true);
			// Grayed requires checked: the half-filled box render.
			tree.getItem(1).setChecked(true);
			tree.getItem(1).setGrayed(true);
			tree.getItem(1).setExpanded(true);
			return tree;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class NodeSelected extends FamilySpecimen {
		public NodeSelected() {
			super("tree.node.selected", 200, 200);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Tree tree = createTree(parent, SWT.NONE, ctx);
			fillHierarchy(tree);
			expandAll(tree);
			tree.setSelection(tree.getItem(2).getItem(0));
			return tree;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class ItemImages extends FamilySpecimen {
		public ItemImages() {
			super("tree.item.images", 230, 200);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Tree tree = createTree(parent, SWT.NONE, ctx);
			fillHierarchy(tree);
			expandAll(tree);
			Image folder = newIcon(tree, 16);
			for (TreeItem root : tree.getItems()) {
				root.setImage(folder);
				root.getItem(0).setImage(folder);
			}
			return tree;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class EmptyDefault extends FamilySpecimen {
		public EmptyDefault() {
			super("tree.empty.default", 200, 100);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			return createTree(parent, SWT.NONE, ctx);
		}
	}
}
