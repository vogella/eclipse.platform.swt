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

import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/**
 * Manual benchmark: item addition (Table, List, Tree, with redraw on and off),
 * virtual Tree population and ToolBar construction/first layout.
 * <p>
 * Run on demand (not part of any test suite), see {@link BenchmarkUtil}. On
 * macOS add {@code -XstartOnFirstThread}.
 * </p>
 */
public class BenchmarkItems {

	private static final int ITEM_COUNT = 50_000;
	private static final int TOOL_ITEM_COUNT = 500;

	public static void main(String[] args) {
		Display display = new Display();
		try {
			run(display);
		} finally {
			display.dispose();
		}
	}

	static void run(Display display) {
		BenchmarkUtil.header("Item addition, " + BenchmarkUtil.scaled(ITEM_COUNT) + " items one at a time");
		int n = BenchmarkUtil.scaled(ITEM_COUNT);
		for (boolean redraw : new boolean[] { true, false }) {
			String suffix = redraw ? " redraw on" : " redraw off";
			addItems(display, "Table.add" + suffix, redraw, shell -> new Table(shell, SWT.BORDER),
					(Control c, int i) -> new TableItem((Table) c, SWT.NONE), n);
			addItems(display, "List.add" + suffix, redraw, shell -> new List(shell, SWT.BORDER),
					(Control c, int i) -> {
						((List) c).add("Item " + i);
						return null;
					}, n);
			addItems(display, "Tree.add (root items)" + suffix, redraw, shell -> new Tree(shell, SWT.BORDER),
					(Control c, int i) -> new TreeItem((Tree) c, SWT.NONE), n);
		}

		BenchmarkUtil.header("Virtual Tree population, " + n + " root items, SetData forced for every item");
		virtualTree(display, n);

		BenchmarkUtil.header("ToolBar, " + TOOL_ITEM_COUNT + " items, create until first layout");
		toolBar(display);
	}

	private interface ItemFactory {
		Object create(Control parent, int index);
	}

	private static void addItems(Display display, String name, boolean redraw, Function<Shell, Control> factory,
			ItemFactory itemFactory, int count) {
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure(name + " x" + count, () -> {
			shell[0] = new Shell(display);
			shell[0].setLayout(new FillLayout());
			shell[0].setSize(400, 400);
			Control control = factory.apply(shell[0]);
			shell[0].open();
			BenchmarkUtil.flush(display);
			return () -> {
				if (!redraw) {
					control.setRedraw(false);
				}
				for (int i = 0; i < count; i++) {
					itemFactory.create(control, i);
				}
				if (!redraw) {
					control.setRedraw(true);
				}
				BenchmarkUtil.flush(display);
			};
		}, () -> shell[0].dispose());
	}

	private static void virtualTree(Display display, int count) {
		Shell[] shell = new Shell[1];
		int[] setDataEvents = new int[1];
		BenchmarkUtil.measure("Virtual Tree setItemCount + SetData x" + count, () -> {
			shell[0] = new Shell(display);
			shell[0].setLayout(new FillLayout());
			shell[0].setSize(400, 400);
			Tree tree = new Tree(shell[0], SWT.VIRTUAL | SWT.BORDER);
			setDataEvents[0] = 0;
			tree.addListener(SWT.SetData, e -> {
				setDataEvents[0]++;
				((TreeItem) e.item).setText("Item " + e.index);
			});
			shell[0].open();
			BenchmarkUtil.flush(display);
			return () -> {
				tree.setItemCount(count);
				// getText() on a virtual item forces SetData for it
				for (int i = 0; i < count; i++) {
					tree.getItem(i).getText();
				}
				BenchmarkUtil.flush(display);
			};
		}, () -> {
			if (setDataEvents[0] < count) {
				throw new IllegalStateException("Expected at least " + count + " SetData events, got " + setDataEvents[0]);
			}
			shell[0].dispose();
		});
	}

	private static void toolBar(Display display) {
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("ToolBar create " + TOOL_ITEM_COUNT + " items + pack + open", () -> {
			shell[0] = new Shell(display);
			shell[0].setLayout(new FillLayout());
			return () -> {
				ToolBar toolBar = new ToolBar(shell[0], SWT.WRAP);
				for (int i = 0; i < TOOL_ITEM_COUNT; i++) {
					ToolItem item = new ToolItem(toolBar, SWT.PUSH);
					item.setText("Item " + i);
				}
				shell[0].pack();
				shell[0].layout(true, true);
				shell[0].open();
				BenchmarkUtil.flush(display);
			};
		}, () -> shell[0].dispose());
	}
}
