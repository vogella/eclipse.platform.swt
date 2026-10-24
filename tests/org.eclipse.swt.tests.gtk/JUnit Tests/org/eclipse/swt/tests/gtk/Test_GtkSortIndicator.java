/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.gtk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.Converter;
import org.eclipse.swt.internal.gtk.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Test_GtkSortIndicator {

	private Display display;
	private Shell shell;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	@Test
	public void test_tableSortIndicatorSurvivesNewColumns() {
		Table table = new Table(shell, SWT.NONE);
		table.setHeaderVisible(true);
		TableColumn first = new TableColumn(table, SWT.NONE);
		TableColumn sorted = new TableColumn(table, SWT.NONE);
		table.setSortColumn(sorted);
		table.setSortDirection(SWT.UP);
		// the native model grows at the 2nd, 6th, 10th, ... column
		for (int i = 0; i < 10; i++) {
			new TableColumn(table, SWT.NONE);
			assertEquals(1, nativeSortIndicator(sorted.handle), "after column " + (i + 3));
			assertEquals(0, nativeSortIndicator(first.handle), "after column " + (i + 3));
		}
		assertEquals(sorted, table.getSortColumn());
		assertEquals(SWT.UP, table.getSortDirection());
	}

	@Test
	public void test_treeSortIndicatorSurvivesNewColumns() {
		Tree tree = new Tree(shell, SWT.NONE);
		tree.setHeaderVisible(true);
		TreeColumn first = new TreeColumn(tree, SWT.NONE);
		TreeColumn sorted = new TreeColumn(tree, SWT.NONE);
		tree.setSortColumn(sorted);
		tree.setSortDirection(SWT.UP);
		for (int i = 0; i < 10; i++) {
			new TreeColumn(tree, SWT.NONE);
			assertEquals(1, nativeSortIndicator(sorted.handle), "after column " + (i + 3));
			assertEquals(0, nativeSortIndicator(first.handle), "after column " + (i + 3));
		}
		assertEquals(sorted, tree.getSortColumn());
		assertEquals(SWT.UP, tree.getSortDirection());
	}

	private static int nativeSortIndicator(long columnHandle) {
		int[] value = new int[1];
		OS.g_object_get(columnHandle, Converter.wcsToMbcs("sort-indicator", true), value, 0);
		return value[0];
	}
}
