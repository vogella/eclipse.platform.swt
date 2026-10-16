/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit.performance;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

/**
 * Manual benchmark: cell drawing of a 10k row, 5 column Table and Tree, with and
 * without MeasureItem/PaintItem listeners. Times a full paint and a scroll (each step paints the visible cells via
 * {@code Control.print(GC)}, see {@code BenchmarkUtil.paintNow}).
 * <p>
 * Run on demand (not part of any test suite), see {@link BenchmarkUtil}. On
 * macOS add {@code -XstartOnFirstThread}.
 * </p>
 */
public class BenchmarkCellDrawing {

	private static final int ROWS = 10_000;
	private static final int COLUMNS = 5;
	private static final int SCROLL_STEPS = 300;
	private static final int REDRAWS = 20;

	public static void main(String[] args) {
		Display display = new Display();
		try {
			run(display);
		} finally {
			display.dispose();
		}
	}

	static void run(Display display) {
		int rows = BenchmarkUtil.scaled(ROWS);
		BenchmarkUtil.header("Cell drawing, " + rows + " rows x " + COLUMNS + " columns");
		for (boolean listeners : new boolean[] { false, true }) {
			String suffix = listeners ? " with MeasureItem+PaintItem" : " no custom draw";
			table(display, rows, listeners, suffix);
			tree(display, rows, listeners, suffix);
		}
	}

	private static Listener customDraw() {
		return e -> {
			switch (e.type) {
			case SWT.MeasureItem:
				e.height = Math.max(e.height, e.gc.getFontMetrics().getHeight() + 2);
				break;
			case SWT.PaintItem:
				String text = e.item instanceof TableItem t ? t.getText(e.index) : ((TreeItem) e.item).getText(e.index);
				e.gc.drawText(text, e.x + 2, e.y + 1, true);
				break;
			default:
				break;
			}
		};
	}

	private static void table(Display display, int rows, boolean listeners, String suffix) {
		Shell[] shell = new Shell[1];
		Table[] table = new Table[1];
		Runnable prepareShell = () -> {
			shell[0] = new Shell(display);
			shell[0].setLayout(new FillLayout());
			shell[0].setSize(800, 600);
			table[0] = new Table(shell[0], SWT.BORDER | SWT.FULL_SELECTION);
			table[0].setHeaderVisible(true);
			table[0].setLinesVisible(true);
			for (int c = 0; c < COLUMNS; c++) {
				TableColumn column = new TableColumn(table[0], SWT.NONE);
				column.setText("Column " + c);
				column.setWidth(150);
			}
			String[] texts = new String[COLUMNS];
			table[0].setRedraw(false);
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < COLUMNS; c++) {
					texts[c] = "Cell " + r + ":" + c;
				}
				new TableItem(table[0], SWT.NONE).setText(texts);
			}
			table[0].setRedraw(true);
			if (listeners) {
				Listener l = customDraw();
				table[0].addListener(SWT.MeasureItem, l);
				table[0].addListener(SWT.PaintItem, l);
			}
			shell[0].open();
			BenchmarkUtil.flush(display);
		};
		BenchmarkUtil.measure("Table full paint x" + REDRAWS + suffix, () -> {
			prepareShell.run();
			return () -> redraw(display, table[0]);
		}, () -> shell[0].dispose());
		BenchmarkUtil.measure("Table scroll " + SCROLL_STEPS + " steps" + suffix, () -> {
			prepareShell.run();
			return () -> {
				for (int i = 0; i < SCROLL_STEPS; i++) {
					table[0].setTopIndex(i * 5 % table[0].getItemCount());
					BenchmarkUtil.paintNow(display, table[0]);
				}
			};
		}, () -> shell[0].dispose());
	}

	private static void tree(Display display, int rows, boolean listeners, String suffix) {
		Shell[] shell = new Shell[1];
		Tree[] tree = new Tree[1];
		Runnable prepareShell = () -> {
			shell[0] = new Shell(display);
			shell[0].setLayout(new FillLayout());
			shell[0].setSize(800, 600);
			tree[0] = new Tree(shell[0], SWT.BORDER | SWT.FULL_SELECTION);
			tree[0].setHeaderVisible(true);
			tree[0].setLinesVisible(true);
			for (int c = 0; c < COLUMNS; c++) {
				TreeColumn column = new TreeColumn(tree[0], SWT.NONE);
				column.setText("Column " + c);
				column.setWidth(150);
			}
			String[] texts = new String[COLUMNS];
			tree[0].setRedraw(false);
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < COLUMNS; c++) {
					texts[c] = "Cell " + r + ":" + c;
				}
				new TreeItem(tree[0], SWT.NONE).setText(texts);
			}
			tree[0].setRedraw(true);
			if (listeners) {
				Listener l = customDraw();
				tree[0].addListener(SWT.MeasureItem, l);
				tree[0].addListener(SWT.PaintItem, l);
			}
			shell[0].open();
			BenchmarkUtil.flush(display);
		};
		BenchmarkUtil.measure("Tree full paint x" + REDRAWS + suffix, () -> {
			prepareShell.run();
			return () -> redraw(display, tree[0]);
		}, () -> shell[0].dispose());
		BenchmarkUtil.measure("Tree scroll " + SCROLL_STEPS + " steps" + suffix, () -> {
			prepareShell.run();
			return () -> {
				for (int i = 0; i < SCROLL_STEPS; i++) {
					tree[0].setTopItem(tree[0].getItem(i * 5 % tree[0].getItemCount()));
					BenchmarkUtil.paintNow(display, tree[0]);
				}
			};
		}, () -> shell[0].dispose());
	}

	private static void redraw(Display display, Control control) {
		for (int i = 0; i < REDRAWS; i++) {
			BenchmarkUtil.paintNow(display, control);
		}
	}
}
