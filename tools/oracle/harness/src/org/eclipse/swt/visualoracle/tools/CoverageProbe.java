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
import org.eclipse.swt.visualoracle.impl.SkijaProtoBackend;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;

/**
 * Reports skija-proto coverage over specimens: how many of the catalog the
 * fork's custom-drawn renderers actually reach. Counting unsupported
 * specimens is the migration progress metric; this probe produces it without
 * capturing any pixels, so it stays fast enough to run per selftest.
 *
 * Runs inside a JVM whose classpath carries the prototype-skija build (see
 * impl/BackendClasspaths callers); on stock SWT classes it fails loudly.
 *
 * Usage: CoverageProbe [specimenId...]   (no ids means the whole catalog)
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
		SpecimenCatalog catalog = SpecimenCatalog.discover();
		List<Specimen> targets = new ArrayList<>();
		for (String id : args) {
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
			SkijaProtoBackend backend = new SkijaProtoBackend();
			try {
				backend.configure(display);
			} catch (BackendUnavailableException e) {
				System.err.println("COVERAGE-FAILED: backend unavailable: " + e);
				return EXIT_BACKEND;
			}
			System.out.println("BACKEND-GC=" + backend.observedGcClassName());

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
}
