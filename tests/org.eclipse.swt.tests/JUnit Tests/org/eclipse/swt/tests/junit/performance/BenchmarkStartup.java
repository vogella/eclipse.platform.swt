/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit.performance;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Manual benchmark: time from the start of {@code new Display()} to the first
 * {@code Shell.open()} having processed its pending events.
 * <p>
 * The interesting part is class loading and static initialisation (for example
 * the Cocoa {@code OS.<clinit>}), which only happens once per JVM. Therefore
 * each JVM run yields exactly one meaningful "cold" sample; run it several
 * times in separate JVMs, for example
 * {@code for i in 1 2 3 4 5; do java -XstartOnFirstThread ... BenchmarkStartup; done}.
 * A second, "warm" sample (second Display in the same JVM) is printed for
 * reference.
 * </p>
 * Run on demand (not part of any test suite). On macOS add
 * {@code -XstartOnFirstThread}.
 */
public class BenchmarkStartup {

	public static void main(String[] args) {
		System.out.printf(java.util.Locale.ROOT, "Startup cold (Display create -> first Shell.open): %.1f ms%n", sample());
		System.out.printf(java.util.Locale.ROOT, "Startup warm (second Display in same JVM):         %.1f ms%n", sample());
	}

	private static double sample() {
		long start = System.nanoTime();
		Display display = new Display();
		try {
			Shell shell = new Shell(display);
			shell.open();
			BenchmarkUtil.flush(display);
			double ms = (System.nanoTime() - start) / 1_000_000.0;
			shell.dispose();
			return ms;
		} finally {
			display.dispose();
		}
	}
}
