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
import org.eclipse.swt.graphics.Drawing;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.SkijaGC;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Minimal smoke program for the prototype-skija fork backend. The fork routes
 * widget drawing through SkijaGC when SWT.USE_SKIJA is set; this probe asks
 * Drawing for a graphics context and asserts the returned GC really is a
 * SkijaGC (which also proves Skija's native library loaded and created a
 * raster surface).
 */
public class SkijaProtoProbe {
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		int[] paints = { 0 };
		shell.addListener(SWT.Paint, e -> paints[0]++);
		Button button = new Button(shell, SWT.PUSH);
		button.setText("skija");
		button.setBounds(10, 10, 120, 32);
		button.addListener(SWT.Paint, e -> paints[0]++);
		shell.open();
		boolean[] done = { false };
		display.timerExec(1500, () -> done[0] = true);
		while (!done[0] && !shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		GC raw = new GC(button);
		GC wrapped = Drawing.createGraphicsContext(raw, button);
		String gcClass = wrapped.innerGC == null ? "null" : wrapped.innerGC.getClass().getName();
		System.out.println("PROBE-GC=" + gcClass);
		raw.dispose();
		wrapped.dispose();
		if (!shell.isDisposed())
			shell.dispose();
		display.dispose();
		System.out.println("PAINTS=" + paints[0]);
		System.out.println("SKIJA-PROTO-ACTIVE=" + (wrapped != raw && gcClass.equals(SkijaGC.class.getName())));
	}
}
