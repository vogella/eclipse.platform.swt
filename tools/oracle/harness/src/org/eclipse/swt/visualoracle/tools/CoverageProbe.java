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
package org.eclipse.swt.visualoracle.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.SkiaCanvasBackend;
import org.eclipse.swt.visualoracle.impl.SkijaProtoBackend;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;

/**
 * Reports backend coverage over specimens: how many of the catalog a backend
 * actually reaches. Counting unsupported specimens is the migration progress
 * metric; this probe produces it without capturing any pixels, so it stays
 * fast enough to run per selftest.
 *
 * Runs inside a JVM whose classpath carries the probed backend's SWT build
 * (see impl/BackendClasspaths callers); on the wrong classes it fails loudly.
 * For skia-canvas it must be launched with
 * {@link SkiaCanvasBackend#activationJvmProperties()}, because specimen
 * factories cannot pass {@code SWT.SKIA} themselves.
 *
 * Usage: CoverageProbe [--backend ID] [specimenId...]   (no ids means the
 * whole catalog; no --backend means skija-proto)
 *
 * Output: one "UNSUPPORTED <id>" or "PROBE-ERROR <id> : <cause>" line per
 * non-supported specimen, then "COVERAGE total=<n> supported=<n>
 * unsupported=<n> error=<n>". Exit 0 unless probing errored.
 */
public final class CoverageProbe {

	static final int EXIT_OK = 0;
	static final int EXIT_PROBE_ERRORS = 1;
	static final int EXIT_USAGE = 2;
	static final int EXIT_BACKEND = 5;

	public static void main(String[] args) {
		System.exit(run(args));
	}

	static int run(String[] args) {
		String backendId = SkijaProtoBackend.ID;
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			if ("--backend".equals(args[i])) {
				if (++i >= args.length) {
					System.err.println("coverage-probe: --backend needs an id");
					return EXIT_USAGE;
				}
				backendId = args[i];
			} else {
				ids.add(args[i]);
			}
		}
		if (!NativeBackend.ID.equals(backendId) && !SkiaCanvasBackend.ID.equals(backendId)
				&& !SkijaProtoBackend.ID.equals(backendId)) {
			System.err.println("coverage-probe: unknown backend: " + backendId);
			return EXIT_USAGE;
		}

		SpecimenCatalog catalog = SpecimenCatalog.discover();
		List<Specimen> targets = new ArrayList<>();
		for (String id : ids) {
			Specimen specimen = catalog.byId(id).orElse(null);
			if (specimen == null) {
				System.err.println("coverage-probe: unknown specimen id: " + id);
				return EXIT_USAGE;
			}
			targets.add(specimen);
		}
		if (targets.isEmpty())
			targets = catalog.all();

		Display display = new Display();
		try {
			Backend backend = createBackend(backendId);
			try {
				backend.configure(display);
			} catch (BackendUnavailableException e) {
				System.err.println("COVERAGE-FAILED: backend unavailable: " + e);
				return EXIT_BACKEND;
			}
			if (backend instanceof SkijaProtoBackend skija)
				System.out.println("BACKEND-GC=" + skija.observedGcClassName());
			if (backend instanceof SkiaCanvasBackend canvas)
				System.out.println("BACKEND-CANVAS=" + canvas.observedHandlerClassName()
						+ " force=" + canvas.isForceEnabled());

			int supported = 0;
			int unsupported = 0;
			int errors = 0;
			for (Specimen specimen : targets) {
				try {
					if (backend.supports(specimen)) {
						supported++;
						continue;
					}
					unsupported++;
					System.out.println("UNSUPPORTED " + specimen.id());
				} catch (Throwable t) {
					errors++;
					System.out.println("PROBE-ERROR " + specimen.id() + " : " + t);
				}
			}
			System.out.println("COVERAGE total=" + targets.size() + " supported=" + supported
					+ " unsupported=" + unsupported + " error=" + errors);
			return errors == 0 ? EXIT_OK : EXIT_PROBE_ERRORS;
		} finally {
			display.dispose();
		}
	}

	private static Backend createBackend(String backendId) {
		return switch (backendId) {
			case NativeBackend.ID -> new NativeBackend();
			case SkijaProtoBackend.ID -> new SkijaProtoBackend();
			case SkiaCanvasBackend.ID -> new SkiaCanvasBackend();
			default -> throw new IllegalArgumentException("backend '" + backendId + "' has no adapter");
		};
	}
}
