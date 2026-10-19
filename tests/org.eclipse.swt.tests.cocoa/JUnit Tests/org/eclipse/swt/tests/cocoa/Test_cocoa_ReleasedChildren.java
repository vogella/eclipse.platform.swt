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
package org.eclipse.swt.tests.cocoa;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

public class Test_cocoa_ReleasedChildren {

	@Test
	public void childCreatedByDisposeListenerDoesNotOutliveParent() {
		Display display = Display.getDefault();
		Shell shell = new Shell(display);
		Composite composite = new Composite(shell, SWT.NONE);
		Button first = new Button(composite, SWT.PUSH);
		Button[] late = new Button[1];
		first.addListener(SWT.Dispose, e -> late[0] = new Button(composite, SWT.PUSH));
		shell.open();

		// a still live child of a released parent made the view callbacks during the release throw an NPE
		assertDoesNotThrow(display::dispose);
		assertTrue(late[0].isDisposed());
	}
}
