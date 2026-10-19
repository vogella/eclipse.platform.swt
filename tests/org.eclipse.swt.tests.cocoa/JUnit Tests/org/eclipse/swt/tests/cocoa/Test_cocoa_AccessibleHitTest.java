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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.internal.cocoa.NSPoint;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hit tests an accessible whose getChildAtPoint listener answers with another accessible.
 */
public class Test_cocoa_AccessibleHitTest {

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
	public void test_getChildAtPoint_controlAccessible() {
		Accessible target = new Canvas(shell, SWT.NONE).getAccessible();
		Accessible accessible = new Canvas(shell, SWT.NONE).getAccessible();
		accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
			@Override
			public void getChildAtPoint(AccessibleControlEvent e) {
				e.accessible = target;
			}
		});
		assertNotNull(accessible.internal_accessibilityHitTest(new NSPoint(), ACC.CHILDID_SELF));
	}

	@Test
	public void test_getChildAtPoint_childAccessible() {
		Accessible accessible = new Canvas(shell, SWT.NONE).getAccessible();
		Accessible child = new Accessible(accessible);
		accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
			@Override
			public void getChildAtPoint(AccessibleControlEvent e) {
				e.accessible = child;
			}
		});
		assertNotNull(accessible.internal_accessibilityHitTest(new NSPoint(), ACC.CHILDID_SELF));
	}
}
