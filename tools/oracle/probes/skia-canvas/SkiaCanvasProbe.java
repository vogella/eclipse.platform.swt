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
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Minimal smoke program for the SWT.SKIA canvas backend (PR 3231). Run with
 * -Dorg.eclipse.swt.external.canvas:logActivation=true so that
 * ExternalCanvasHandler reports on stdout whether the external canvas factory
 * was found and activated; verify-backend.sh asserts that line.
 */
public class SkiaCanvasProbe {
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		Canvas canvas = new Canvas(shell, SWT.SKIA | SWT.BORDER);
		canvas.setBounds(10, 10, 200, 100);
		int[] paints = { 0 };
		shell.addListener(SWT.Paint, e -> paints[0]++);
		shell.open();
		boolean[] done = { false };
		display.timerExec(2500, () -> done[0] = true);
		while (!done[0] && !shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		if (!shell.isDisposed())
			shell.dispose();
		display.dispose();
		System.out.println("PAINTS=" + paints[0]);
	}
}
