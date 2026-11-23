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
package org.eclipse.test;

/** Stand-in for the Eclipse test framework class, so the SWT tests run outside of OSGi. */
public class Screenshots {

	public static String takeScreenshot(Class<?> testClass, String name) {
		return null;
	}
}
