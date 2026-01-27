/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Test_CompositeDeferredLayout {

	public Shell shell;
	public Composite composite;

	@BeforeEach
	public void setUp() {
		shell = new Shell();
		composite = new Composite(shell, SWT.NONE);
	}

	@Test
	public void test_deferredLayout_movesChildren() {
		int numChildren = 5;
		Button[] buttons = new Button[numChildren];
		for (int i = 0; i < numChildren; i++) {
			buttons[i] = new Button(composite, SWT.PUSH);
			buttons[i].setBounds(0, 0, 10, 10);
		}

		composite.setLayoutDeferred(true);

		for (int i = 0; i < numChildren; i++) {
			buttons[i].setBounds(10 * i, 10 * i, 20, 20);
		}

		composite.setLayoutDeferred(false);

		for (int i = 0; i < numChildren; i++) {
			Rectangle bounds = buttons[i].getBounds();
			assertEquals(new Rectangle(10 * i, 10 * i, 20, 20), bounds, "Button " + i + " bounds incorrect after deferred layout");
		}
	}
	
	@Test
	public void test_resizeChildren_batching() {
		// This test indirectly targets the batching optimization in Composite.resizeChildren
		// by verifying that a large number of children are moved correctly.
		int numChildren = 100;
		Button[] buttons = new Button[numChildren];
		for (int i = 0; i < numChildren; i++) {
			buttons[i] = new Button(composite, SWT.PUSH);
			buttons[i].setBounds(0, 0, 10, 10);
		}

		composite.setLayoutDeferred(true);

		for (int i = 0; i < numChildren; i++) {
			buttons[i].setBounds(i, i, 15, 15);
		}

		// This call triggers resizeChildren with the deferred logic
		composite.setLayoutDeferred(false);

		for (int i = 0; i < numChildren; i++) {
			Rectangle bounds = buttons[i].getBounds();
			assertEquals(new Rectangle(i, i, 15, 15), bounds, "Button " + i + " bounds incorrect after large batch deferred layout");
		}
	}
}
