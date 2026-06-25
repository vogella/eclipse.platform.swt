/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.snippets;

/*
 * Phase 0 reproducer / benchmark for
 *   https://github.com/eclipse-platform/eclipse.platform.swt/issues/1726
 * which evaluates the suggestion from bug 558392 comment 16:
 *   "every Composite.resizeChildren() calls its own DeferWindowPos() ...
 *    I would expect a significant improvement if everything happened under a
 *    single DeferWindowPos()."
 *
 * It builds a deeply nested SashForm tree (the layout shape that produces the
 * cascade of separate DeferWindowPos batches on Windows) and measures, per
 * resize, the number of:
 *   - Begin/EndDeferWindowPos batches      (Control.deferBatchCount)
 *   - batched child moves (DeferWindowPos) (Control.deferEntryCount)
 *   - immediate moves (SetWindowPos)       (Control.immediateMoveCount)
 *   - Paint events                         (proxy for redraw / flicker)
 *   - Resize events                        (proxy for cascade size)
 *   - wall-clock time
 *
 * The DeferWindowPos counters require the Win32 instrumentation enabled via the
 * system property -Dswt.debug.deferwindowpos=true. They are read reflectively so
 * this snippet still runs against a stock SWT (those metrics then show as "n/a").
 *
 * Usage:
 *   Bug558392ResizeBenchmark [--depth D] [--fanout F] [--frames N] [--interactive]
 *
 * Scripted mode (default) oscillates the shell width for N frames and prints a
 * summary. Interactive mode opens the shell so a human can drag the sashes and
 * watch the live counters printed on every resize.
 */
import java.lang.reflect.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

public class Bug558392ResizeBenchmark {

	static int depth = 3;     // nesting levels of SashForm
	static int fanout = 3;    // children per SashForm
	static int frames = 200;  // scripted resize steps
	static boolean interactive = false;

	// Live event counters (reset around each measurement window).
	static long paintCount;
	static long resizeCount;

	public static void main (String[] args) {
		parseArgs (args);

		Display display = new Display ();
		display.addFilter (SWT.Paint, e -> paintCount++);
		display.addFilter (SWT.Resize, e -> resizeCount++);

		Shell shell = new Shell (display);
		shell.setText ("Bug 558392 resize benchmark (depth=" + depth + ", fanout=" + fanout + ")");
		shell.setLayout (new FillLayout ());
		buildTree (shell, depth);
		shell.setSize (1200, 800);
		shell.open ();
		flush (display);

		int leaves = (int) Math.pow (fanout, depth);
		System.out.println ("Tree: depth=" + depth + ", fanout=" + fanout + " -> ~" + leaves + " leaf composites");
		System.out.println ("DeferWindowPos instrumentation: "
				+ (deferStatsAvailable () ? "ENABLED" : "n/a (run with -Dswt.debug.deferwindowpos=true on instrumented SWT)"));
		System.out.println ();

		if (interactive) {
			runInteractive (display, shell);
		} else {
			runScripted (display, shell);
			shell.dispose ();
		}
		display.dispose ();
	}

	/** Oscillate the shell width and report aggregate metrics for the whole sweep. */
	static void runScripted (Display display, Shell shell) {
		// Warm up (first layouts allocate caches, JIT, etc.).
		sweep (display, shell, Math.min (40, frames));
		paintCount = 0;
		resizeCount = 0;
		resetDeferStats ();
		long batches0 = readStat ("deferBatchCount");
		long entries0 = readStat ("deferEntryCount");
		long immediate0 = readStat ("immediateMoveCount");

		long t0 = System.nanoTime ();
		sweep (display, shell, frames);
		long elapsedMs = (System.nanoTime () - t0) / 1_000_000;

		long batches = readStat ("deferBatchCount") - batches0;
		long entries = readStat ("deferEntryCount") - entries0;
		long immediate = readStat ("immediateMoveCount") - immediate0;

		System.out.println ("=== Scripted sweep: " + frames + " resize frames ===");
		System.out.printf ("  time            : %d ms  (%.2f ms/frame)%n", elapsedMs, elapsedMs / (double) frames);
		System.out.printf ("  paint events    : %d  (%.1f /frame)%n", paintCount, paintCount / (double) frames);
		System.out.printf ("  resize events   : %d  (%.1f /frame)%n", resizeCount, resizeCount / (double) frames);
		if (deferStatsAvailable ()) {
			System.out.printf ("  DeferWindowPos batches : %d  (%.1f /frame)   <-- comment 16 focuses on this%n",
					batches, batches / (double) frames);
			System.out.printf ("  batched child moves    : %d  (%.1f /frame)%n", entries, entries / (double) frames);
			System.out.printf ("  immediate moves        : %d  (%.1f /frame)%n", immediate, immediate / (double) frames);
			if (batches > 0) {
				System.out.printf ("  avg moves per batch    : %.2f   (1.0 would mean no batching benefit)%n",
						entries / (double) batches);
			}
		}
	}

	static void sweep (Display display, Shell shell, int count) {
		int base = 700;
		int span = 500;
		for (int i = 0; i < count; i++) {
			int w = base + (i % 2 == 0 ? span : 0) + (i % 20); // vary width, avoid identical no-op sizes
			shell.setSize (w, 800);
			flush (display);
		}
	}

	/** Open and let a human drag sashes; print live counters on every resize. */
	static void runInteractive (Display display, Shell shell) {
		System.out.println ("Interactive mode: drag the sashes; counters print per resize batch.");
		final long[] last = new long[3];
		display.addFilter (SWT.Resize, e -> {
			// Print once per top-level resize gesture by debouncing via asyncExec.
			display.asyncExec (() -> {
				long b = readStat ("deferBatchCount");
				long en = readStat ("deferEntryCount");
				long im = readStat ("immediateMoveCount");
				if (b != last[0] || en != last[1] || im != last[2]) {
					if (deferStatsAvailable ()) {
						System.out.printf ("batches=%d  batchedMoves=%d  immediateMoves=%d  paints=%d%n",
								b, en, im, paintCount);
					}
					last[0] = b;
					last[1] = en;
					last[2] = im;
				}
			});
		});
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
	}

	/** Recursively build nested SashForms; leaves are composites with a few heavyweight controls. */
	static void buildTree (Composite parent, int level) {
		if (level == 0) {
			buildLeaf (parent);
			return;
		}
		SashForm sash = new SashForm (parent, (level % 2 == 0) ? SWT.HORIZONTAL : SWT.VERTICAL);
		for (int i = 0; i < fanout; i++) {
			Composite child = new Composite (sash, SWT.NONE);
			child.setLayout (new FillLayout ());
			buildTree (child, level - 1);
		}
	}

	static void buildLeaf (Composite parent) {
		Composite leaf = new Composite (parent, SWT.BORDER);
		leaf.setLayout (new GridLayout (2, false));
		// Heavyweight native children make the resize layout non-trivial.
		Tree tree = new Tree (leaf, SWT.BORDER | SWT.V_SCROLL);
		tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true, 2, 1));
		for (int i = 0; i < 8; i++) {
			TreeItem it = new TreeItem (tree, SWT.NONE);
			it.setText ("item " + i);
			new TreeItem (it, SWT.NONE).setText ("child");
		}
		for (int i = 0; i < 3; i++) {
			new Label (leaf, SWT.NONE).setText ("Field " + i + ":");
			Text t = new Text (leaf, SWT.BORDER);
			t.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		}
	}

	static void flush (Display display) {
		while (display.readAndDispatch ()) {
			// drain pending events so the resize cascade fully completes
		}
	}

	// --- reflective access to the optional Win32 DeferWindowPos counters -----------------

	static Boolean statsAvailable;

	static boolean deferStatsAvailable () {
		if (statsAvailable == null) {
			try {
				Control.class.getField ("deferBatchCount");
				statsAvailable = Boolean.TRUE;
			} catch (NoSuchFieldException e) {
				statsAvailable = Boolean.FALSE;
			}
		}
		return statsAvailable.booleanValue ();
	}

	static long readStat (String field) {
		if (!deferStatsAvailable ()) return -1;
		try {
			return Control.class.getField (field).getLong (null);
		} catch (ReflectiveOperationException e) {
			return -1;
		}
	}

	static void resetDeferStats () {
		if (!deferStatsAvailable ()) return;
		try {
			Method m = Control.class.getMethod ("resetDeferStats");
			m.invoke (null);
		} catch (ReflectiveOperationException e) {
			// ignore
		}
	}

	static void parseArgs (String[] args) {
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--depth" -> depth = Integer.parseInt (args[++i]);
				case "--fanout" -> fanout = Integer.parseInt (args[++i]);
				case "--frames" -> frames = Integer.parseInt (args[++i]);
				case "--interactive" -> interactive = true;
				default -> System.out.println ("Ignoring unknown arg: " + args[i]);
			}
		}
	}
}
