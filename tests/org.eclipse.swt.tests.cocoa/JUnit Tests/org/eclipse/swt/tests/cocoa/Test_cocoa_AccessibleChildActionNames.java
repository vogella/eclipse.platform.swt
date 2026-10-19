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

import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Asks the delegate of an <code>Accessible(Accessible parent)</code> child without
 * role and listeners for its names, the way VoiceOver does.
 */
public class Test_cocoa_AccessibleChildActionNames {

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
	public void test_actionNames_childWithoutRole() throws Exception {
		assertNull(delegateCall(newChild(), "accessibilityActionNames"));
	}

	@Test
	public void test_attributeNames_childWithoutRole() throws Exception {
		assertNull(delegateCall(newChild(), "accessibilityAttributeNames"));
	}

	@Test
	public void test_parameterizedAttributeNames_childWithoutRole() throws Exception {
		assertNull(delegateCall(newChild(), "accessibilityParameterizedAttributeNames"));
	}

	private Accessible newChild() {
		return new Accessible(new Canvas(shell, SWT.NONE).getAccessible());
	}

	private static Object delegateCall(Accessible accessible, String method) throws Exception {
		Field field = Accessible.class.getDeclaredField("delegate");
		field.setAccessible(true);
		Object delegate = field.get(accessible);
		Method m = delegate.getClass().getDeclaredMethod(method);
		m.setAccessible(true);
		return m.invoke(delegate);
	}
}
