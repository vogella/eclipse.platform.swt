/*******************************************************************************
 * Copyright (c) 2026 SWT Visual Oracle contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.visualoracle.impl;

import java.util.Map;

/**
 * The GTK major version a run is pinned to. SWT picks GTK4 when the
 * environment variable {@code SWT_GTK4} is {@code 1} and silently falls back
 * to GTK3 if the GTK4 natives are missing, so the request is carried to every
 * child explicitly and the GTK platform support checks it against what the
 * process really loaded.
 */
public final class GtkVersion {

	/** JVM property overriding {@code SWT_GTK4} in the harness process ({@code --gtk}). */
	public static final String PROPERTY = "oracle.gtk";

	private GtkVersion() {
	}

	/** The requested major version, 3 unless GTK4 was asked for. */
	public static int requested() {
		String prop = System.getProperty(PROPERTY);
		if (prop != null)
			return "4".equals(prop) ? 4 : 3;
		return "1".equals(System.getenv("SWT_GTK4")) ? 4 : 3;
	}

	/** Pins the request, for {@code --gtk}. */
	public static void request(int major) {
		System.setProperty(PROPERTY, Integer.toString(major));
	}

	/** Applies the request to a child process environment. */
	public static void applyTo(Map<String, String> environment) {
		if (requested() == 4)
			environment.put("SWT_GTK4", "1");
		else
			environment.remove("SWT_GTK4");
	}
}
