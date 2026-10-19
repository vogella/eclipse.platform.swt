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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Field;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleValueAdapter;
import org.eclipse.swt.accessibility.AccessibleValueEvent;
import org.eclipse.swt.internal.cocoa.NSNumber;
import org.eclipse.swt.internal.cocoa.NSRange;
import org.eclipse.swt.internal.cocoa.NSString;
import org.eclipse.swt.internal.cocoa.NSValue;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.internal.cocoa.id;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sends the messages that take two arguments to the native object of an
 * <code>Accessible(Accessible parent)</code> child, the way VoiceOver does.
 */
public class Test_cocoa_AccessibleChildValue {

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
	public void test_setValue_reachesValueListener() throws Exception {
		Accessible child = new Accessible(new Canvas(shell, SWT.NONE).getAccessible());
		double[] received = { Double.NaN };
		child.addAccessibleValueListener(new AccessibleValueAdapter() {
			@Override
			public void setCurrentValue(AccessibleValueEvent e) {
				received[0] = e.value.doubleValue();
			}
		});
		OS.objc_msgSend(delegateId(child), OS.sel_accessibilitySetValue_forAttribute_,
				NSNumber.numberWithDouble(42).id, OS.NSAccessibilityValueAttribute.id);
		assertEquals(42.0, received[0]);
	}

	@Test
	public void test_parameterizedAttribute_answeredByChild() throws Exception {
		Accessible child = new Accessible(new Canvas(shell, SWT.NONE).getAccessible());
		child.addAccessibleControlListener(new AccessibleControlAdapter() {
			@Override
			public void getRole(AccessibleControlEvent e) {
				e.detail = ACC.ROLE_TEXT;
			}

			@Override
			public void getValue(AccessibleControlEvent e) {
				e.result = "child text";
			}
		});
		NSRange range = new NSRange();
		range.location = 0;
		range.length = 5;
		long result = OS.objc_msgSend(delegateId(child), OS.sel_accessibilityAttributeValue_forParameter_,
				OS.NSAccessibilityStringForRangeParameterizedAttribute.id, NSValue.valueWithRange(range).id);
		assertNotEquals(0, result);
		assertEquals("child", new NSString(result).getString());
	}

	private static long delegateId(Accessible accessible) throws Exception {
		Field field = Accessible.class.getDeclaredField("delegate");
		field.setAccessible(true);
		return ((id) field.get(accessible)).id;
	}
}
