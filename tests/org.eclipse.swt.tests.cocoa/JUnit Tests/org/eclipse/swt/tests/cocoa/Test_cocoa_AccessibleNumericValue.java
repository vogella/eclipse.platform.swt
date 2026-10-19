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

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.internal.cocoa.NSNumber;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks the value attribute of roles whose value is a number or a flag.
 */
public class Test_cocoa_AccessibleNumericValue {

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
	public void test_slider_keepsNumber() {
		assertEquals(42, value(ACC.ROLE_SLIDER, "42"));
	}

	@Test
	public void test_scrollBar_keepsNumber() {
		assertEquals(42, value(ACC.ROLE_SCROLLBAR, "42"));
	}

	@Test
	public void test_progressBar_keepsNumber() {
		assertEquals(42, value(ACC.ROLE_PROGRESSBAR, "42"));
	}

	@Test
	public void test_numericRole_withoutValue_isZero() {
		assertEquals(0, value(ACC.ROLE_SLIDER, null));
	}

	@Test
	public void test_checkButton_isFlag() {
		assertEquals(1, value(ACC.ROLE_CHECKBUTTON, "1"));
		assertEquals(0, value(ACC.ROLE_CHECKBUTTON, "0"));
		assertEquals(1, value(ACC.ROLE_CHECKBUTTON, "true"));
	}

	private int value(int role, String value) {
		Accessible accessible = new Canvas(shell, SWT.NONE).getAccessible();
		accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
			@Override
			public void getRole(AccessibleControlEvent e) {
				e.detail = role;
			}

			@Override
			public void getValue(AccessibleControlEvent e) {
				e.result = value;
			}
		});
		return new NSNumber(accessible.internal_accessibilityAttributeValue(OS.NSAccessibilityValueAttribute, ACC.CHILDID_SELF)).intValue();
	}
}
