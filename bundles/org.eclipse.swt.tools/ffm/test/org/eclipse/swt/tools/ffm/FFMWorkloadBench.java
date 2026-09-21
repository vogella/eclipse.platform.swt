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
package org.eclipse.swt.tools.ffm;

import java.lang.management.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Times typical SWT work phase by phase, wall and main thread CPU time; iteration 1 is cold, the rest warm.
 * Prints "phase cold warm-median" per line, run by bench-gtk.sh.
 */
public class FFMWorkloadBench {
	static final int ITERATIONS = Integer.getInteger("iterations", 15);
	static final Map<String, List<Double>> TIMES = new LinkedHashMap<>();
	static final ThreadMXBean MX = ManagementFactory.getThreadMXBean();
	static long cpuStart;

	public static void main(String[] args) {
		long t = mark();
		Display display = new Display();
		record("display", t);
		for (int i = 0; i < ITERATIONS; i++) {
			iteration(display);
		}
		display.dispose();
		for (var e : TIMES.entrySet()) {
			List<Double> v = e.getValue();
			List<Double> warm = new ArrayList<>(v.subList(Math.min(1, v.size() - 1), v.size()));
			Collections.sort(warm);
			System.out.printf(Locale.ROOT, "%s %.2f %.2f%n", e.getKey(), v.get(0), warm.get(warm.size() / 2));
		}
	}

	static void iteration(Display display) {
		long t = mark();
		Shell shell = new Shell(display);
		shell.setLayout(new GridLayout(4, true));
		for (int c = 0; c < 16; c++) {
			Composite composite = new Composite(shell, SWT.BORDER);
			composite.setLayout(new GridLayout(2, false));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			for (int k = 0; k < 4; k++) {
				new Label(composite, SWT.NONE).setText("Label " + k);
				new Text(composite, SWT.BORDER).setText("Text " + k);
				new Button(composite, SWT.CHECK).setText("Check " + k);
				Combo combo = new Combo(composite, SWT.READ_ONLY);
				combo.setItems("one", "two", "three");
				combo.select(1);
			}
			Tree tree = new Tree(composite, SWT.BORDER);
			for (int k = 0; k < 20; k++) {
				TreeItem item = new TreeItem(tree, SWT.NONE);
				item.setText("Node " + k);
				new TreeItem(item, SWT.NONE).setText("Child");
			}
			Table table = new Table(composite, SWT.BORDER);
			for (int k = 0; k < 20; k++) {
				new TableItem(table, SWT.NONE).setText("Row " + k);
			}
			new StyledText(composite, SWT.BORDER | SWT.MULTI).setText("public class Foo {\n\tint bar;\n}\n".repeat(10));
		}
		record("create", t);

		t = mark();
		shell.setSize(1400, 1000);
		shell.open();
		settle(display);
		record("open", t);

		t = mark();
		for (int k = 0; k < 20; k++) {
			shell.setSize(1200 + (k % 2) * 200, 900 + (k % 2) * 100);
			shell.layout(true, true);
			shell.update();
		}
		settle(display);
		record("relayout", t);

		t = mark();
		Image image = new Image(display, 800, 600);
		GC gc = new GC(image);
		for (int k = 0; k < 2000; k++) {
			gc.setForeground(display.getSystemColor(k % 16));
			gc.drawLine(k % 800, 0, 800 - k % 800, 600);
			gc.fillRectangle(k % 700, k % 500, 40, 30);
			gc.drawText("Text " + k, k % 700, k % 550, true);
			gc.textExtent("Measure " + k);
		}
		gc.dispose();
		image.dispose();
		record("gc", t);

		t = mark();
		shell.dispose();
		settle(display);
		record("dispose", t);
	}

	static void settle(Display display) {
		while (display.readAndDispatch()) {
		}
	}

	static long mark() {
		cpuStart = MX.getCurrentThreadCpuTime();
		return System.nanoTime();
	}

	static void record(String phase, long start) {
		TIMES.computeIfAbsent(phase, k -> new ArrayList<>()).add((System.nanoTime() - start) / 1e6);
		TIMES.computeIfAbsent(phase + "-cpu", k -> new ArrayList<>()).add((MX.getCurrentThreadCpuTime() - cpuStart) / 1e6);
	}
}
