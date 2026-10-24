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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.cocoa.NSView;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cocoa specific regression tests for {@link Button}.
 */
public class Test_cocoa_Button {

	private Display display;
	private Shell shell;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	/**
	 * Leak check: a radio button wraps its NSButton in a parent view, which
	 * must be released together with the button.
	 */
	@Test
	public void test_dispose_releasesRadioParent() {
		Button radio = new Button(shell, SWT.RADIO);
		shell.open();
		NSView radioParent = radio.view.superview();
		assertNotNull(radioParent);
		radioParent.retain();
		try {
			drainEvents();
			long count = radioParent.retainCount();
			radio.dispose();
			drainEvents();
			// one reference less for the superview, one for the button's own
			assertEquals(count - 2, radioParent.retainCount(), "radio parent view leaked");
		} finally {
			radioParent.release();
		}
	}

	private void drainEvents() {
		while (display.readAndDispatch()) {
			// process pending events, which also drains the autorelease pool
		}
	}
}
