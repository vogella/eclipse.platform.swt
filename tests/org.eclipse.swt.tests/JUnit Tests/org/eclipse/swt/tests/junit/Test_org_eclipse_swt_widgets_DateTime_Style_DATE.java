/*******************************************************************************
 * Copyright (c) 2025 Kichwa Coders Canada, Inc.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.DateTime;
import org.junit.jupiter.api.Test;

public class Test_org_eclipse_swt_widgets_DateTime_Style_DATE extends Test_org_eclipse_swt_widgets_DateTime {

	public Test_org_eclipse_swt_widgets_DateTime_Style_DATE() {
		super(SWT.DATE);
	}

	@Test
	public void test_longDatePreferredWidthIndependentOfMonth() {
		Locale defaultLocale = Locale.getDefault();
		Locale.setDefault(Locale.UK);
		try {
			DateTime longDate = new DateTime(shell, SWT.DATE | SWT.LONG);
			longDate.setDate(2024, FEB, 15);
			Point february = longDate.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
			longDate.setDate(2024, 4, 15);
			Point may = longDate.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
			longDate.setDate(2024, AUG, 15);
			Point september = longDate.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
			assertEquals(february, may);
			assertEquals(february, september);
		} finally {
			Locale.setDefault(defaultLocale);
		}
	}
}
