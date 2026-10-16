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

import org.eclipse.swt.widgets.*;

/** Creates and disposes a Display and lets main return, so the VM is destroyed with the process wide Cocoa objects alive. */
public class FFMExitCheck {
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.open();
		while (display.readAndDispatch()) {
			// drain
		}
		display.dispose();
	}
}
