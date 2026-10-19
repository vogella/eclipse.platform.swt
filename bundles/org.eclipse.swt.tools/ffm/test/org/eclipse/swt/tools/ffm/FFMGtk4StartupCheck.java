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
package org.eclipse.swt.tools.ffm;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.gtk.GTK;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/**
 * Starts SWT, opens a Shell with a Table (which creates the text, pixbuf and toggle cell renderers) and a Tree,
 * and disposes everything. Needs SWT_GTK4=1 for the GTK4 check and a display, run by test-gtk4-startup.sh.
 */
public class FFMGtk4StartupCheck {

	public static void main(String[] args) {
		boolean expectGtk4 = "1".equals(System.getenv("SWT_GTK4"));
		if (GTK.GTK4 != expectGtk4) {
			System.out.println("FAILED expected GTK4=" + expectGtk4 + " but GTK.GTK4=" + GTK.GTK4);
			System.exit(1);
		}
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setLayout(new FillLayout());
		Table table = new Table(shell, SWT.CHECK | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		for (int i = 0; i < 2; i++) new TableColumn(table, SWT.NONE).setText("column " + i);
		TableItem item = new TableItem(table, SWT.NONE);
		item.setText(new String[] {"a", "b"});
		item.setImage(display.getSystemImage(SWT.ICON_INFORMATION));
		Tree tree = new Tree(shell, SWT.CHECK);
		new TreeItem(tree, SWT.NONE).setText("node");
		shell.setSize(300, 200);
		shell.open();
		while (display.readAndDispatch()) {
			// process the pending events
		}
		shell.dispose();
		display.dispose();
		System.out.println("PASSED GTK" + (GTK.GTK4 ? 4 : 3) + " startup with Table and Tree");
	}
}
