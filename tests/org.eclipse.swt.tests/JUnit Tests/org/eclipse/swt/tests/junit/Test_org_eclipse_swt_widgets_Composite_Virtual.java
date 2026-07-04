/*******************************************************************************
 * Copyright (c) 2026 Vogella and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the virtual (handle-less) {@link Composite} created with
 * {@link SWT#VIRTUAL}, see
 * <a href="https://github.com/eclipse-platform/eclipse.platform.swt/issues/624">issue 624</a>.
 * <p>
 * On platforms that implement {@code SWT.VIRTUAL} for {@code Composite} the container has
 * no native control and its children are parented to the nearest non-virtual ancestor; on
 * other platforms the style is ignored and the composite behaves like a normal
 * {@code Composite}. The assertions below describe behaviour that must hold either way.
 * <p>
 * These tests deliberately live in their own class (rather than in
 * {@code Test_org_eclipse_swt_widgets_Composite}, which is a base class for many widget
 * tests) so that they run once instead of being inherited by every subclass.
 */
public class Test_org_eclipse_swt_widgets_Composite_Virtual {

	Shell shell;

	@BeforeEach
	public void setUp() {
		shell = new Shell();
	}

	@AfterEach
	public void tearDown() {
		if (shell != null && !shell.isDisposed()) {
			shell.dispose();
		}
	}

	@Test
	public void test_VIRTUAL_actsAsLayoutContainer() {
		Composite virtual = new Composite(shell, SWT.VIRTUAL);
		virtual.setLayout(new FillLayout());
		Button button = new Button(virtual, SWT.PUSH);

		// The virtual composite is a usable layout container: the child is its logical child.
		assertArrayEquals(new Control[] { button }, virtual.getChildren());
		assertTrue(button.getParent() == virtual);

		shell.setLayout(new FillLayout());
		shell.setSize(200, 100);
		shell.layout(true, true);

		// FillLayout makes the child fill the virtual composite's client area.
		Point size = button.getSize();
		assertTrue(size.x > 0);
		assertTrue(size.y > 0);

		// Disposing the virtual composite disposes its children.
		virtual.dispose();
		assertTrue(virtual.isDisposed());
		assertTrue(button.isDisposed());
	}

	@Test
	public void test_VIRTUAL_nested() {
		Composite outer = new Composite(shell, SWT.VIRTUAL);
		outer.setLayout(new FillLayout());
		Composite inner = new Composite(outer, SWT.VIRTUAL);
		inner.setLayout(new FillLayout());
		Button button = new Button(inner, SWT.PUSH);

		assertArrayEquals(new Control[] { inner }, outer.getChildren());
		assertArrayEquals(new Control[] { button }, inner.getChildren());
		assertTrue(inner.getParent() == outer);
		assertTrue(button.getParent() == inner);

		shell.setLayout(new FillLayout());
		shell.setSize(200, 100);
		shell.layout(true, true);

		Point size = button.getSize();
		assertTrue(size.x > 0);
		assertTrue(size.y > 0);

		outer.dispose();
		assertTrue(inner.isDisposed());
		assertTrue(button.isDisposed());
	}
}
