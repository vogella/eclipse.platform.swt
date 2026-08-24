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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.SwtRenderEnvs;
import org.eclipse.swt.visualoracle.result.CaptureEntry;
import org.eclipse.swt.visualoracle.result.CaptureStatus;
import org.eclipse.swt.visualoracle.result.RunResult;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.CaptureFailedException;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.Theme;

/**
 * Child-process half of the environment control: one JVM serves exactly one
 * backend and one {@link RenderEnv} (decision D6) and captures any number of
 * specimens into one schema-v1 result document plus PNG evidence files.
 *
 * This generalises {@link CaptureProbe} from one hardcoded specimen to a
 * driven batch. The parent side lives in {@org.eclipse.swt.visualoracle.impl.ChildProcessLauncher}.
 *
 * Launch contract (produced by {@org.eclipse.swt.visualoracle.impl.SwtRenderEnvs#launch}):
 * the requested environment arrives as {@code oracle.env.*} JVM properties.
 * After Display creation the child measures what it really got (DPI for
 * zoom, GTK_THEME for theme) and refuses to lie: on mismatch every specimen
 * is recorded FAILED and the exit code says so.
 *
 * Usage: CaptureChild --out DIR [--strategy COPY_AREA|X11_GRAB] [--backend ID]
 *                     [--hang] specimenId...
 *
 * Exit codes: 0 all captured or unsupported, 2 usage error (unknown specimen,
 * unknown backend, missing --out), 3 environment mismatch (result still
 * written, all entries FAILED), 4 font pinning failed, 5 backend unavailable,
 * 6 at least one capture failed (result written).
 */
public final class CaptureChild {

	static final int EXIT_OK = 0;
	static final int EXIT_USAGE = 2;
	static final int EXIT_ENV_MISMATCH = 3;
	static final int EXIT_FONT = 4;
	static final int EXIT_BACKEND = 5;
	static final int EXIT_CAPTURE_FAILURES = 6;

	public static void main(String[] args) {
		System.exit(run(args));
	}

	static int run(String[] args) {
		Path out = null;
		CaptureRuntime.Strategy strategy = CaptureRuntime.Strategy.COPY_AREA;
		String backendId = NativeBackend.ID;
		boolean hang = false;
		List<String> specimenIds = new ArrayList<>();
		try {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				switch (arg) {
					case "--out" -> out = Path.of(requireValue(args, ++i, arg));
					case "--strategy" -> strategy = CaptureRuntime.Strategy.valueOf(requireValue(args, ++i, arg));
					case "--backend" -> backendId = requireValue(args, ++i, arg);
					case "--hang" -> hang = true;
					default -> {
						if (arg.startsWith("-"))
							return usage("unknown option: " + arg);
						specimenIds.add(arg);
					}
				}
			}
		} catch (IllegalArgumentException e) {
			return usage(e.getMessage());
		}
		if (out == null)
			return usage("--out DIR is required");
		if (!NativeBackend.ID.equals(backendId))
			return usage("backend '" + backendId + "' has no child adapter yet");
		if (specimenIds.isEmpty())
			return usage("no specimens requested");

		SpecimenCatalog catalog = SpecimenCatalog.discover();
		List<Specimen> specimens = new ArrayList<>();
		for (String id : specimenIds) {
			Specimen specimen = catalog.byId(id).orElse(null);
			if (specimen == null)
				return usage("unknown specimen id: " + id);
			specimens.add(specimen);
		}

		RenderEnv requested = readRequestedEnvironment();
		if (requested == null)
			return usage("the oracle.env.* properties carrying the requested environment are required");

		if (hang) {
			System.err.println("CHILD-HANGING: parking forever, the launcher must time this out");
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		Display display = new Display();
		final String backend = backendId;
		try {
			NativeBackend nativeBackend = new NativeBackend();
			try {
				nativeBackend.configure(display);
			} catch (BackendUnavailableException e) {
				System.err.println("CHILD-FAILED: backend unavailable: " + e);
				return EXIT_BACKEND;
			}

			RenderEnv actual = SwtRenderEnvs.current(display);
			if (!SwtRenderEnvs.matches(requested, actual)) {
				System.err.println("CHILD-FAILED: environment mismatch, process is pinned to " + actual
						+ " but was asked for " + requested);
				writeResult(out, actual, backend, specimens,
						id -> CaptureEntry.skipped(id, backend, CaptureStatus.FAILED,
								"environment mismatch: process is pinned to " + actual
										+ ", requested " + requested));
				return EXIT_ENV_MISMATCH;
			}

			if (!actual.usesSystemFont()) {
				Font probe = new Font(display, actual.fontFamily(), actual.fontSize(), 0);
				probe.dispose();
			}

			List<CaptureEntry> entries = new ArrayList<>();
			boolean anyFailure = false;
			try (CaptureRuntime runtime = new CaptureRuntime(strategy)) {
				for (Specimen specimen : specimens) {
					CaptureEntry entry = captureOne(runtime, nativeBackend, actual, specimen, out);
					if (entry.status() == CaptureStatus.FAILED)
						anyFailure = true;
					entries.add(entry);
					System.out.println("CAPTURE " + specimen.id() + " " + entry.status());
				}
			}
			writeResult(out, actual, backendId, specimens, id -> entryFor(id, entries));
			return anyFailure ? EXIT_CAPTURE_FAILURES : EXIT_OK;
		} finally {
			display.dispose();
		}
	}

	private static CaptureEntry captureOne(CaptureRuntime runtime, NativeBackend backend, RenderEnv env,
			Specimen specimen, Path out) {
		if (!backend.supports(specimen))
			return CaptureEntry.skipped(specimen.id(), backend.id(), CaptureStatus.UNSUPPORTED,
					"backend '" + backend.id() + "' does not support this specimen");
		try {
			CapturedImage image = runtime.capture(specimen, backend, env);
			Path imagesDir = out.resolve("images");
			Files.createDirectories(imagesDir);
			Path png = imagesDir.resolve(specimen.id() + "-" + backend.id() + ".png");
			Files.write(png, image.pngBytes());
			return CaptureEntry.captured(specimen.id(), backend.id(), image, out.relativize(png).toString());
		} catch (CaptureFailedException e) {
			return CaptureEntry.skipped(specimen.id(), backend.id(), CaptureStatus.FAILED, String.valueOf(e));
		} catch (java.io.IOException e) {
			return CaptureEntry.skipped(specimen.id(), backend.id(), CaptureStatus.FAILED,
					"writing evidence failed: " + e);
		} catch (RuntimeException e) {
			return CaptureEntry.skipped(specimen.id(), backend.id(), CaptureStatus.FAILED,
					"capture crashed: " + e);
		}
	}

	private static CaptureEntry entryFor(String id, List<CaptureEntry> entries) {
		return entries.stream().filter(e -> e.specimen().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalStateException("no entry produced for " + id));
	}

	private static void writeResult(Path out, RenderEnv env, String backendId, List<Specimen> specimens,
			java.util.function.Function<String, CaptureEntry> fallback) {
		try {
			Files.createDirectories(out);
			RunResult result = new RunResult(RunResult.CURRENT_SCHEMA_VERSION,
					"oracle-harness/" + OracleCli.VERSION + "/capture-child", env,
							specimens.stream().map(s -> fallback.apply(s.id())).toList(),
					List.of());
			Files.writeString(out.resolve("result.json"), result.toJson(), StandardCharsets.UTF_8);
		} catch (RuntimeException | java.io.IOException e) {
			System.err.println("CHILD-FAILED: writing result document failed: " + e);
		}
	}

	private static RenderEnv readRequestedEnvironment() {
		String zoomProp = System.getProperty(SwtRenderEnvs.ZOOM_PROPERTY);
		if (zoomProp == null)
			return null;
		int zoom;
		try {
			zoom = Integer.parseInt(zoomProp);
		} catch (NumberFormatException e) {
			throw new IllegalStateException(SwtRenderEnvs.ZOOM_PROPERTY + " is not a number: " + zoomProp);
		}
		Theme theme = new Theme(System.getProperty(SwtRenderEnvs.THEME_PROPERTY, ""));
		Direction direction = Direction.valueOf(
				System.getProperty(SwtRenderEnvs.DIRECTION_PROPERTY, Direction.LTR.name()));
		String family = System.getProperty(SwtRenderEnvs.FONT_FAMILY_PROPERTY, "");
		int size = Integer.parseInt(System.getProperty(SwtRenderEnvs.FONT_SIZE_PROPERTY, "-1"));
		return new RenderEnv(zoom, theme, direction, family, size);
	}

	private static String requireValue(String[] args, int index, String option) {
		if (index >= args.length)
			throw new IllegalArgumentException("option " + option + " needs a value");
		return args[index];
	}

	private static int usage(String message) {
		System.err.println("capture-child: " + message);
		System.err.println("usage: CaptureChild --out DIR [--strategy S] [--backend ID] [--hang] specimenId...");
		return EXIT_USAGE;
	}
}
