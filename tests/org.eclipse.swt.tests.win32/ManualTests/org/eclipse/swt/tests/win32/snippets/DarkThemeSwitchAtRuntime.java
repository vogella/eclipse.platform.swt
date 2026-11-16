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
package org.eclipse.swt.tests.win32.snippets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

/**
 * Toggles light and dark at runtime. Every widget has to look like the ones in
 * a shell freshly opened in that mode with the second button.
 */
public class DarkThemeSwitchAtRuntime {
	private static final String PREFIX = "org.eclipse.swt.internal.win32.";
	private static boolean dark;

	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = createShell(display);
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}

	private static void setDark(Display display, boolean value) {
		dark = value;
		display.setData(PREFIX + "useDarkModeExplorerTheme", value);
		display.setData(PREFIX + "useShellTitleColoring", value);
		display.setData(PREFIX + "menuBarForegroundColor", value ? new Color(0xD0, 0xD0, 0xD0) : null);
		display.setData(PREFIX + "menuBarBackgroundColor", value ? new Color(0x30, 0x30, 0x30) : null);
		display.setData(PREFIX + "menuBarBorderColor", value ? new Color(0x50, 0x50, 0x50) : null);
		display.setData(PREFIX + "Combo.useDarkTheme", value);
		display.setData(PREFIX + "ProgressBar.useColors", value);
		display.setData(PREFIX + "Text.useDarkThemeIcons", value);
	}

	private static Shell createShell(Display display) {
		Shell shell = new Shell(display);
		shell.setText("Dark theme switch");
		shell.setLayout(new GridLayout(2, true));

		Menu bar = new Menu(shell, SWT.BAR);
		shell.setMenuBar(bar);
		MenuItem file = new MenuItem(bar, SWT.CASCADE);
		file.setText("&File");
		Menu fileMenu = new Menu(shell, SWT.DROP_DOWN);
		file.setMenu(fileMenu);
		new MenuItem(fileMenu, SWT.PUSH).setText("&Open");
		new MenuItem(fileMenu, SWT.CHECK).setText("&Check");
		new MenuItem(bar, SWT.CASCADE).setText("&Edit");
		Menu popup = new Menu(shell, SWT.POP_UP);
		new MenuItem(popup, SWT.PUSH).setText("Popup item");
		shell.setMenu(popup);

		Button toggle = new Button(shell, SWT.PUSH);
		toggle.setText("Toggle light/dark");
		toggle.addListener(SWT.Selection, e -> setDark(display, !dark));
		Button fresh = new Button(shell, SWT.PUSH);
		fresh.setText("Open fresh shell");
		fresh.addListener(SWT.Selection, e -> createShell(display).open());

		Tree tree = new Tree(shell, SWT.BORDER | SWT.CHECK | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree.setHeaderVisible(true);
		for (String title : new String[] { "Tree one", "Tree two" }) {
			TreeColumn column = new TreeColumn(tree, SWT.NONE);
			column.setText(title);
			column.setWidth(150);
			column.setToolTipText(title);
		}
		for (int i = 0; i < 30; i++) {
			TreeItem item = new TreeItem(tree, SWT.NONE);
			item.setText(new String[] { "Item " + i, "Value " + i });
			new TreeItem(item, SWT.NONE).setText("Child " + i);
		}

		Table table = new Table(shell, SWT.BORDER | SWT.CHECK | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		table.setHeaderVisible(true);
		for (String title : new String[] { "Table one", "Table two" }) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(title);
			column.setWidth(150);
			column.setToolTipText(title);
		}
		for (int i = 0; i < 30; i++) {
			new TableItem(table, SWT.NONE).setText(new String[] { "Item " + i, "Value " + i });
		}

		new Text(shell, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL).setText("Search text");
		Combo combo = new Combo(shell, SWT.DROP_DOWN);
		combo.setItems("One", "Two", "Three");
		combo.select(0);

		Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayout(new FillLayout());
		new Button(buttons, SWT.PUSH).setText("Push");
		new Button(buttons, SWT.CHECK).setText("Check");
		new Button(buttons, SWT.RADIO).setText("Radio");
		new Button(buttons, SWT.ARROW | SWT.DOWN);

		ToolBar toolBar = new ToolBar(shell, SWT.FLAT);
		for (String title : new String[] { "Tool one", "Tool two" }) {
			ToolItem item = new ToolItem(toolBar, SWT.PUSH);
			item.setText(title);
			item.setToolTipText(title);
		}

		ProgressBar progress = new ProgressBar(shell, SWT.NONE);
		progress.setSelection(40);
		Text multi = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		multi.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		multi.setText("line\n".repeat(20));
		multi.setToolTipText("Text tooltip");

		shell.setSize(800, 600);
		return shell;
	}
}
