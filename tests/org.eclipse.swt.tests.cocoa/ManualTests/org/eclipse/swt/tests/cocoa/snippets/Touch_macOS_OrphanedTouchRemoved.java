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

package org.eclipse.swt.tests.cocoa.snippets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.TouchEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Touch;

/**
 * Manual test for the orphaned touch workaround in Control (macOS only).
 * <p>
 * Cocoa sometimes does not deliver NSTouchPhaseEnded/Cancelled, most commonly
 * when a 4-finger gesture makes the application switcher appear. SWT then
 * synthesizes a TOUCHSTATE_UP for the orphaned touch and must also forget it.
 * <p>
 * Steps (requires a trackpad):
 * <ol>
 * <li>Run this snippet and put several fingers on the trackpad while the
 * pointer is over the window. The label shows the current touches.</li>
 * <li>Do a 4-finger swipe that opens Mission Control or the app switcher, then
 * lift all fingers.</li>
 * <li>Expected: every touch gets reported with state UP exactly once and the
 * label ends with "0 active touches". Before the fix, orphaned touches were
 * never removed from the internal list, so they were reported as UP again on
 * every following touch event (the label keeps listing stale touches).</li>
 * </ol>
 */
public final class Touch_macOS_OrphanedTouchRemoved {
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setText("Orphaned touch workaround");
		shell.setLayout(new FillLayout());
		shell.setSize(400, 300);
		Label label = new Label(shell, SWT.WRAP);
		label.setText("Touch the trackpad");
		shell.setTouchEnabled(true);
		shell.addTouchListener(e -> {
			String text = describe(e);
			System.out.println(text);
			label.setText(text);
		});
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) display.sleep();
		}
		display.dispose();
	}

	private static String describe(TouchEvent e) {
		StringBuilder sb = new StringBuilder();
		int active = 0;
		for (Touch t : e.touches) {
			sb.append("id=").append(t.id).append(" state=").append(stateName(t.state)).append('\n');
			if (t.state != SWT.TOUCHSTATE_UP) active++;
		}
		sb.append(active).append(" active touches");
		return sb.toString();
	}

	private static String stateName(int state) {
		return switch (state) {
		case SWT.TOUCHSTATE_DOWN -> "DOWN";
		case SWT.TOUCHSTATE_MOVE -> "MOVE";
		case SWT.TOUCHSTATE_UP -> "UP";
		default -> "?" + state;
		};
	}
}
