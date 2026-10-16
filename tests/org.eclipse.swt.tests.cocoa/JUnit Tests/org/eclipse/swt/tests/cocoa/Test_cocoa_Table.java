/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.cocoa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cocoa specific regression tests for {@link Table}.
 */
public class Test_cocoa_Table {

	private Display display;
	private Shell shell;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
	}

	@AfterEach
	public void tearDown() {
		display.dispose();
	}

	/**
	 * TableColumn.pack() sends MeasureItem for each row. The SELECTED detail
	 * must reflect the selection state of that row, not of the row whose index
	 * equals the column index.
	 */
	@Test
	public void test_columnPack_measureItemSelectedDetailUsesRowIndex() {
		Table table = new Table(shell, SWT.MULTI);
		for (int i = 0; i < 4; i++) {
			new TableColumn(table, SWT.NONE).setText("column " + i);
		}
		for (int i = 0; i < 5; i++) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] { "a" + i, "b" + i, "c" + i, "d" + i });
		}
		shell.open();
		table.setSelection(3); // row 3, while we pack column 1

		List<Integer> selectedRows = new ArrayList<>();
		List<Integer> measuredRows = new ArrayList<>();
		table.addListener(SWT.MeasureItem, e -> {
			if (e.index != 1) return;
			int row = table.indexOf((TableItem) e.item);
			measuredRows.add(row);
			if ((e.detail & SWT.SELECTED) != 0) selectedRows.add(row);
		});
		table.getColumn(1).pack();

		assertTrue(measuredRows.size() >= 5, "MeasureItem must be sent for the rows");
		assertFalse(selectedRows.isEmpty(), "the selected row must be reported as selected");
		for (int row : selectedRows) {
			assertEquals(3, row, "only the selected row may carry SWT.SELECTED");
		}
	}
}
