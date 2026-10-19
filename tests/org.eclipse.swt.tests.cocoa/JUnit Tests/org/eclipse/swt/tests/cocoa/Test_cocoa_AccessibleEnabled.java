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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.internal.cocoa.NSNumber;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks that the enabled attribute follows the enabled state of the control.
 */
public class Test_cocoa_AccessibleEnabled {

	private Shell shell;

	@BeforeEach
	public void setUp() {
		shell = new Shell(Display.getDefault());
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	@Test
	public void test_enabledControl_isEnabled() {
		Button button = new Button(shell, SWT.PUSH);
		assertTrue(enabled(button.getAccessible()));
	}

	@Test
	public void test_disabledControl_isNotEnabled() {
		Button button = new Button(shell, SWT.PUSH);
		Accessible accessible = button.getAccessible();
		button.setEnabled(false);
		assertFalse(enabled(accessible));
		button.setEnabled(true);
		assertTrue(enabled(accessible));
	}

	private static boolean enabled(Accessible accessible) {
		return new NSNumber(accessible.internal_accessibilityAttributeValue(OS.NSAccessibilityEnabledAttribute, ACC.CHILDID_SELF)).boolValue();
	}
}
