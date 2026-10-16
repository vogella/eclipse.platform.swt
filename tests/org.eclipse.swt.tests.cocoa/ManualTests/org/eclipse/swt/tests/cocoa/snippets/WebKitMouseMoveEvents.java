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

package org.eclipse.swt.tests.cocoa.snippets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Manual test for the Cocoa WebKit Browser mouse handling: the DOM "mousemove"
 * handling (which skips duplicate mouse positions) and the link hover status text
 * ({@code webView:mouseDidMoveOverElement:modifierFlags:}).
 * <p>
 * Run on macOS with {@code -XstartOnFirstThread} and check the console and the
 * labels, before and after any change to {@code WebKit.handleEvent} or
 * {@code webView_mouseDidMoveOverElement_modifierFlags}.
 * </p>
 */
public class WebKitMouseMoveEvents {

	static final String HTML = "<html><body style='font-family:sans-serif'>"
			+ "<p>Move the mouse over this page. Every distinct position must produce one MouseMove.</p>"
			+ "<p><a href='https://www.eclipse.org/'>Link 1 (eclipse.org)</a> &nbsp; "
			+ "<a href='https://www.eclipse.org/swt/'>Link 2 (eclipse.org/swt)</a></p>"
			+ "<div style='width:300px;height:200px;background:#cde'>Plain area (no link)</div>"
			+ "</body></html>";

	public static void main(String[] args) {
		final Display display = new Display();
		final Shell shell = new Shell(display);
		shell.setText("WebKit mouse move");
		shell.setLayout(new GridLayout(1, false));

		Label hint = new Label(shell, SWT.WRAP);
		hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		hint.setText(
				"1) Move the mouse slowly inside the page: 'MouseMove count' increases with every new position\n"
				+ "   and the reported x/y follow the cursor (browser-relative coordinates).\n"
				+ "2) Keep the mouse perfectly still, press and release a button: no new MouseMove must be\n"
				+ "   reported for the same position (duplicate positions are skipped), but MouseDown/MouseUp are.\n"
				+ "3) Hover Link 1, then Link 2: status text shows the link URL and changes between links.\n"
				+ "4) Move from a link to the plain area: status text is cleared (empty string reported once).\n"
				+ "5) Hold Cmd/Shift/Alt while moving: the stateMask reported must contain the held modifiers.\n"
				+ "6) Leave the page and re-enter: MouseExit / MouseEnter are reported.");

		Browser browser = new Browser(shell, SWT.NONE);
		browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Label moveLabel = new Label(shell, SWT.NONE);
		moveLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		moveLabel.setText("MouseMove count: 0");
		Label statusLabel = new Label(shell, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		statusLabel.setText("Status text: ");

		final int[] moveCount = new int[1];
		browser.addListener(SWT.MouseMove, e -> {
			moveCount[0]++;
			moveLabel.setText(String.format("MouseMove count: %d   last x=%d y=%d stateMask=0x%x", moveCount[0], e.x,
					e.y, e.stateMask));
			System.out.format("MouseMove #%d x=%d y=%d stateMask=0x%x%n", moveCount[0], e.x, e.y, e.stateMask);
		});
		browser.addListener(SWT.MouseDown, e -> System.out.format("MouseDown button=%d x=%d y=%d%n", e.button, e.x, e.y));
		browser.addListener(SWT.MouseUp, e -> System.out.format("MouseUp   button=%d x=%d y=%d%n", e.button, e.x, e.y));
		browser.addListener(SWT.MouseEnter, e -> System.out.println("MouseEnter"));
		browser.addListener(SWT.MouseExit, e -> System.out.println("MouseExit"));
		browser.addStatusTextListener(new StatusTextListener() {
			@Override
			public void changed(StatusTextEvent event) {
				statusLabel.setText("Status text: " + event.text);
				System.out.println("StatusText: '" + event.text + "'");
			}
		});

		browser.setText(HTML);

		shell.setSize(700, 600);
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}
}
