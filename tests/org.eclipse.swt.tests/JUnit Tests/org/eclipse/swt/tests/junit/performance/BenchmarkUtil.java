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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 * Minimal helper for the manual {@code Benchmark*} classes in this package.
 * <p>
 * These benchmarks are plain {@code main} programs (like
 * {@link BenchmarkSwtMultithreading}). They are not JUnit tests and are not part
 * of {@code AllTests} or {@code PerformanceTests}, so they never run in the
 * normal test suite.
 * </p>
 * <p>
 * System properties:
 * </p>
 * <ul>
 * <li>{@code swt.benchmark.repeats} number of timed runs per measurement
 * (default 3, the median is reported)</li>
 * <li>{@code swt.benchmark.scale} divisor applied to all item/call counts
 * (default 1); use e.g. 10 for a quick smoke run</li>
 * </ul>
 */
public final class BenchmarkUtil {

	static final int REPEATS = Integer.getInteger("swt.benchmark.repeats", 3);
	static final int SCALE = Math.max(1, Integer.getInteger("swt.benchmark.scale", 1));

	private static final Map<String, Double> RESULTS = new LinkedHashMap<>();

	private BenchmarkUtil() {
	}

	/** @return {@code n} divided by the configured scale, at least 1 */
	static int scaled(int n) {
		return Math.max(1, n / SCALE);
	}

	/**
	 * Runs {@code prepare} (not timed) which returns the action to time, times the
	 * action, then runs {@code cleanup} (not timed). Repeated {@link #REPEATS}
	 * times after one untimed warm-up run; prints and records the median in ms.
	 */
	static double measure(String name, Supplier<Runnable> prepare, Runnable cleanup) {
		runOnce(prepare, cleanup); // warm-up
		List<Double> samples = new ArrayList<>();
		for (int i = 0; i < REPEATS; i++) {
			samples.add(runOnce(prepare, cleanup));
		}
		Collections.sort(samples);
		double median = samples.get(samples.size() / 2);
		RESULTS.put(name, median);
		System.out.printf(java.util.Locale.ROOT, "%-70s %10.1f ms  (min %.1f, max %.1f, n=%d)%n", name, median, samples.get(0),
				samples.get(samples.size() - 1), samples.size());
		return median;
	}

	/** Like {@link #measure(String, Supplier, Runnable)} without cleanup. */
	static double measure(String name, Supplier<Runnable> prepare) {
		return measure(name, prepare, () -> {
		});
	}

	private static double runOnce(Supplier<Runnable> prepare, Runnable cleanup) {
		Runnable timed = prepare.get();
		long start = System.nanoTime();
		timed.run();
		long end = System.nanoTime();
		cleanup.run();
		return (end - start) / 1_000_000.0;
	}

	/**
	 * Forces the control to draw itself right now, including MeasureItem and
	 * PaintItem for the visible cells. {@code redraw()}/{@code update()} is not
	 * reliable for this: on macOS drawing is deferred to the window server and
	 * does not happen when the window is occluded or inactive, so it would time
	 * nothing. {@code Control.print(GC)} always draws.
	 */
	static void paintNow(Display display, Control control) {
		Point size = control.getSize();
		Image image = new Image(display, Math.max(1, size.x), Math.max(1, size.y));
		GC gc = new GC(image);
		try {
			control.print(gc);
		} finally {
			gc.dispose();
			image.dispose();
		}
	}

	/** Processes all pending events. */
	static void flush(Display display) {
		while (display.readAndDispatch()) {
			// process events
		}
	}

	/** @return an unmodifiable copy of all medians (ms) measured so far, by name */
	static Map<String, Double> results() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(RESULTS));
	}

	static void header(String title) {
		System.out.println();
		System.out.println("== " + title + " (repeats=" + REPEATS + ", scale=1/" + SCALE + ") ==");
	}
}
