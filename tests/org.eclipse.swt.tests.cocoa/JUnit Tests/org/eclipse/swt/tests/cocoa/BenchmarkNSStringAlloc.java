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
package org.eclipse.swt.tests.cocoa;

import org.eclipse.swt.internal.cocoa.NSString;
import org.eclipse.swt.widgets.Display;

/**
 * Manual benchmark (macOS only, not part of {@link AllCocoaTests}): object
 * allocation through the SWT Cocoa bindings. Measures
 * {@code new NSString().alloc().init().release()}, which goes through
 * {@code id.objc_getClass} for the class lookup on every call.
 * <p>
 * Run with {@code -XstartOnFirstThread}. System properties:
 * {@code swt.benchmark.calls} (default 1000000) and {@code swt.benchmark.repeats}
 * (default 5). The first round is a warm-up and not reported in the median.
 * </p>
 */
public class BenchmarkNSStringAlloc {

	public static void main(String[] args) {
		int calls = Integer.getInteger("swt.benchmark.calls", 1_000_000);
		int repeats = Integer.getInteger("swt.benchmark.repeats", 5);
		Display display = new Display(); // initializes the Cocoa application and libraries
		try {
			double[] samples = new double[repeats];
			run(calls); // warm-up
			for (int r = 0; r < repeats; r++) {
				long start = System.nanoTime();
				run(calls);
				samples[r] = (System.nanoTime() - start) / 1_000_000.0;
				System.out.printf(java.util.Locale.ROOT, "NSString alloc/init/release x%d: %.1f ms (%.0f ns/call)%n", calls, samples[r],
						samples[r] * 1_000_000.0 / calls);
			}
			java.util.Arrays.sort(samples);
			System.out.printf(java.util.Locale.ROOT, "MEDIAN NSString alloc/init/release x%d: %.1f ms%n", calls, samples[repeats / 2]);
		} finally {
			display.dispose();
		}
	}

	private static void run(int calls) {
		for (int i = 0; i < calls; i++) {
			new NSString().alloc().init().release();
		}
	}
}
