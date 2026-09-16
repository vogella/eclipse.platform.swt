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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Minimal smoke program for the stock native backend. Verifies that the SWT
 * natives load, a shell opens, and real paint events are delivered.
 */
public class NativeProbe {
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		int[] paints = { 0 };
		shell.addListener(SWT.Paint, e -> paints[0]++);
		Button button = new Button(shell, SWT.PUSH);
		button.setText("native");
		button.setBounds(10, 10, 120, 32);
		button.addListener(SWT.Paint, e -> paints[0]++);
		shell.open();
		boolean[] done = { false };
		display.timerExec(1500, () -> done[0] = true);
		while (!done[0] && !shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		if (!shell.isDisposed())
			shell.dispose();
		display.dispose();
		System.out.println("PAINTS=" + paints[0]);
		System.out.println("NATIVE-ACTIVE=" + (paints[0] > 0));
	}
}
