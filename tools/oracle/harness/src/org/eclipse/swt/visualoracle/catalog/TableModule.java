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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The table family: headers on and off, gridlines, single and multi column,
 * check boxes, cell and header images, row selection with and without
 * {@code SWT.FULL_SELECTION}, the three column alignments, and the empty
 * table.
 * <p>
 * All state (selection, checks, expansion) is applied in {@code create}
 * before the widget is ever realized, so no GTK state transition runs after
 * map. Column widths are fixed pixels: deriving them from text metrics would
 * make layouts differ between backends with different font matching.
 * <p>
 * Measured Yaru/GTK3 quirk: the header band only
 * paints when the hosting shell opens after the table exists; hosted into an
 * already open shell, GTK collapses it. The header specimens therefore apply
 * the API state correctly and stay deterministic either way, but on this
 * stack they differ mainly by the extra visible row, not by a header band.
 * <p>
 * Selection specimens are tagged {@link Tag#FOCUS_SENSITIVE}: the capture
 * shell focuses its lone child, and a selected row draws differently in a
 * focused widget. The focus itself lands deterministically, so the renders
 * stay stable.
 */
public class TableModule implements SpecimenModule {

	@Override
	public String family() {
		return "table";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new HeadersDefault(), new HeadersHidden(), new HeadersGridlines(),
				new ColumnSingle(), new HeaderImage(), new CellImages(),
				new CheckRows(),
				new RowSelected(), new FullSelectionSelected(),
				new EmptyHeaders(),
				new AlignmentLeft(), new AlignmentCenter(), new AlignmentRight());
	}

	static Table createTable(Composite parent, int style, SpecimenContext ctx) {
		Table table = new Table(parent, style);
		ctx.configure(table);
		return table;
	}

	private static TableColumn addColumn(Table table, String title, int width) {
		TableColumn column = new TableColumn(table, SWT.NONE);
		column.setText(title);
		column.setWidth(width);
		return column;
	}

	private static void fill(Table table, String[][] rows) {
		for (String[] row : rows) {
			new TableItem(table, SWT.NONE).setText(row);
		}
	}

	private static Image newIcon(Control owner, int size) {
		Image icon = CatalogImages.icon(owner.getDisplay(), size);
		CatalogImages.disposeWith(owner, icon);
		return icon;
	}

	private static final String[][] THREE_COLUMN_ROWS = {
			{ "Alpha", "one", "10" },
			{ "Bravo", "two", "20" },
			{ "Charlie", "three", "30" },
			{ "Delta", "four", "40" },
			{ "Echo", "five", "50" },
			{ "Foxtrot", "six", "60" } };

	public static final class HeadersDefault extends FamilySpecimen {
		public HeadersDefault() {
			super("table.headers.default", 260, 160);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			addColumn(table, "Name", 100);
			addColumn(table, "Rank", 80);
			addColumn(table, "Count", 60);
			fill(table, THREE_COLUMN_ROWS);
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class HeadersHidden extends FamilySpecimen {
		public HeadersHidden() {
			super("table.headers.hidden", 260, 160);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(false);
			addColumn(table, "Name", 100);
			addColumn(table, "Rank", 80);
			addColumn(table, "Count", 60);
			fill(table, THREE_COLUMN_ROWS);
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class HeadersGridlines extends FamilySpecimen {
		public HeadersGridlines() {
			super("table.headers.gridlines", 260, 160);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			table.setLinesVisible(true);
			addColumn(table, "Name", 100);
			addColumn(table, "Rank", 80);
			addColumn(table, "Count", 60);
			fill(table, THREE_COLUMN_ROWS);
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ColumnSingle extends FamilySpecimen {
		public ColumnSingle() {
			super("table.column.single", 180, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			addColumn(table, "Name", 160);
			fill(table, new String[][] { { "Alpha" }, { "Bravo" }, { "Charlie" }, { "Delta" }, { "Echo" } });
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class HeaderImage extends FamilySpecimen {
		public HeaderImage() {
			super("table.header.image", 240, 140);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			TableColumn first = addColumn(table, "Name", 140);
			first.setImage(newIcon(table, 16));
			addColumn(table, "Size", 80);
			fill(table, new String[][] { { "report", "12 KB" }, { "notes", "4 KB" }, { "data", "40 KB" } });
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class CellImages extends FamilySpecimen {
		public CellImages() {
			super("table.cell.images", 240, 170);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			addColumn(table, "Name", 140);
			addColumn(table, "Kind", 80);
			Image icon = newIcon(table, 16);
			String[][] rows = { { "home", "dir" }, { "docs", "dir" }, { "readme", "file" },
					{ "build", "file" }, { "src", "dir" } };
			for (String[] row : rows) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(row);
				item.setImage(icon);
			}
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class CheckRows extends FamilySpecimen {
		public CheckRows() {
			super("table.check.rows", 200, 150);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.CHECK, ctx);
			table.setHeaderVisible(true);
			addColumn(table, "Task", 180);
			String[] tasks = { "Compile", "Test", "Package", "Sign", "Publish" };
			for (int i = 0; i < tasks.length; i++) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(tasks[i]);
				item.setChecked(i == 0 || i == 2);
			}
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class RowSelected extends FamilySpecimen {
		public RowSelected() {
			super("table.row.selected", 260, 160);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			addColumn(table, "Name", 100);
			addColumn(table, "Rank", 80);
			addColumn(table, "Count", 60);
			fill(table, THREE_COLUMN_ROWS);
			table.setSelection(2);
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class FullSelectionSelected extends FamilySpecimen {
		public FullSelectionSelected() {
			super("table.fullselection.selected", 280, 160);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.FULL_SELECTION, ctx);
			table.setHeaderVisible(true);
			addColumn(table, "Name", 110);
			addColumn(table, "Rank", 90);
			addColumn(table, "Count", 60);
			fill(table, THREE_COLUMN_ROWS);
			table.setSelection(3);
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);
		}
	}

	public static final class EmptyHeaders extends FamilySpecimen {
		public EmptyHeaders() {
			super("table.empty.headers", 240, 100);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			addColumn(table, "Name", 150);
			addColumn(table, "Size", 70);
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of();
		}
	}

	private abstract static class AlignmentRow extends FamilySpecimen {
		private final int alignment;

		AlignmentRow(String idSuffix, int alignment) {
			super("table.alignment." + idSuffix, 190, 130);
			this.alignment = alignment;
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = createTable(parent, SWT.NONE, ctx);
			table.setHeaderVisible(true);
			TableColumn first = new TableColumn(table, alignment);
			first.setText("File");
			first.setWidth(105);
			TableColumn second = new TableColumn(table, alignment);
			second.setText("State");
			second.setWidth(65);
			fill(table, new String[][] { { "pom.xml", "kept" }, { "readme", "gone" },
					{ "index", "kept" }, { "notes", "draft" } });
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class AlignmentLeft extends AlignmentRow {
		public AlignmentLeft() {
			super("left", SWT.LEFT);
		}
	}

	public static final class AlignmentCenter extends AlignmentRow {
		public AlignmentCenter() {
			super("center", SWT.CENTER);
		}
	}

	public static final class AlignmentRight extends AlignmentRow {
		public AlignmentRight() {
			super("right", SWT.RIGHT);
		}
	}
}
