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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Test_org_eclipse_swt_custom_SashForm {

	public Shell shell;
	public SashForm sashForm;

	@BeforeEach
	public void setUp() {
		shell = new Shell();
		sashForm = new SashForm(shell, SWT.HORIZONTAL);
		shell.setSize(400, 400);
	}

	@Test
	public void test_initialLayout() {
		Button b1 = new Button(sashForm, SWT.PUSH);
		Button b2 = new Button(sashForm, SWT.PUSH);
		sashForm.setWeights(new int[] {50, 50});
		
		shell.open(); // Trigger layout
		
		Rectangle bounds1 = b1.getBounds();
		Rectangle bounds2 = b2.getBounds();
		
		assertTrue(bounds1.width > 0, "Button 1 width should be > 0");
		assertTrue(bounds2.width > 0, "Button 2 width should be > 0");
		assertEquals(bounds1.width, bounds2.width, 5, "Buttons should have approximately equal width"); // Allow small tolerance
	}
	
	@Test
	public void test_layoutDeferredDuringResize() {
		// This test verifies that we can set layout deferred on SashForm 
		// (simulating what happens internally on Windows during drag)
		// and that the layout updates correctly afterwards.
		Button b1 = new Button(sashForm, SWT.PUSH);
		Button b2 = new Button(sashForm, SWT.PUSH);
		sashForm.setWeights(new int[] {50, 50});
		shell.open();
		
		Rectangle initialBounds1 = b1.getBounds();
		
		sashForm.setLayoutDeferred(true);
		sashForm.setSize(600, 400); // Resize the sash form
		sashForm.setLayoutDeferred(false); // Should trigger layout update
		
		Rectangle newBounds1 = b1.getBounds();
		assertTrue(newBounds1.width > initialBounds1.width, "Button 1 should have grown after SashForm resize");
	}
}
