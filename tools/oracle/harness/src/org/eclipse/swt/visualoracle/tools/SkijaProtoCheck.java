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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.swt.visualoracle.impl.BackendClasspaths;
import org.eclipse.swt.visualoracle.impl.ChildProcessLauncher;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.SkijaProtoBackend;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.Theme;

/**
 * Selftest infrastructure for the skija-proto backend adapter (T10): drives
 * {@link CaptureChild} in a JVM running the fork's own classes and counts
 * catalog coverage through {@link CoverageProbe}. All checks assert on
 * evidence that crossed the process boundary (status entries, log lines,
 * PNG files), never on assumptions about the fork.
 */
public final class SkijaProtoCheck {

	private static final String SCRATCH_ROOT = "/tmp/opencode/oracle-T10";
	/** Two covered specimens plus two known-uncovered families' representatives. */
	private static final List<String> BATCH_SPECIMENS = List.of(
			"button.push.default", "label.default", "combo.readonly.empty", "clabel.default");
	private static final RenderEnv LTR_100 = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1);

	private static Map<String, Object> batchDocument;
	private static Path batchDirectory;
	private static String batchStdout;

	private SkijaProtoCheck() {
	}

	// ---------------------------------------------------------------- batch

	private static synchronized void ensureBatch() throws IOException {
		if (batchDocument != null)
			return;
		Path scratch = Path.of(SCRATCH_ROOT, "selftest-" + Long.toString(System.currentTimeMillis(), 36));
		List<ChildProcessLauncher.ChildOutcome> outcomes = new ChildProcessLauncher(
				ChildProcessLauncher.configFromSystemProperties(scratch)).run(List.of(
				new ChildProcessLauncher.ChildRequest(SkijaProtoBackend.ID, LTR_100, BATCH_SPECIMENS,
						CaptureRuntime.Strategy.COPY_AREA, false)));
		ChildProcessLauncher.ChildOutcome outcome = outcomes.get(0);
		batchDirectory = outcome.directory();
		batchStdout = Files.readString(batchDirectory.resolve("child.out.log"), StandardCharsets.UTF_8);
		require(outcome.succeeded(), "skija-proto capture child failed: " + outcome.failureReason()
				+ "\n" + outcome.stderrTail());
		batchDocument = outcome.resultDocument();
	}

	/** The activation proof: the child observed a SkijaGC, like verify-backend.sh asserts. */
	public static void checkActivation(PrintStream out) throws IOException {
		ensureBatch();
		String line = batchStdout.lines().filter(l -> l.startsWith("BACKEND-GC=")).findFirst().orElse(null);
		require(line != null, "child printed no BACKEND-GC evidence line:\n" + tail(batchStdout));
		String gcClass = line.substring("BACKEND-GC=".length());
		require(gcClass.equals("org.eclipse.swt.graphics.SkijaGC"),
				"the fork did not hand out a SkijaGC, evidence line was '" + line + "'");
		out.println("      fork activation evidence: " + line);
	}

	/** A supported specimen captures successfully through this backend. */
	public static void checkSupportedCapture(PrintStream out) throws IOException {
		ensureBatch();
		for (String id : List.of("button.push.default", "label.default")) {
			Map<?, ?> capture = captureEntry(batchDocument, id);
			require("CAPTURED".equals(capture.get("status")), id + " is not CAPTURED but "
					+ capture.get("status") + ": " + capture.get("message"));
			var found = SpecimenCatalog.discover().byId(id)
					.orElseThrow(() -> new AssertionError(id + " missing from catalog"));
			int expectedWidth = Math.round(found.preferredSize().x * LTR_100.zoomPercent() / 100f);
			int expectedHeight = Math.round(found.preferredSize().y * LTR_100.zoomPercent() / 100f);
			require(intValue(capture.get("width")) == expectedWidth
					&& intValue(capture.get("height")) == expectedHeight,
					id + " captured " + capture.get("width") + "x" + capture.get("height")
							+ ", expected " + expectedWidth + "x" + expectedHeight);
			Path png = batchDirectory.resolve(String.valueOf(capture.get("image")));
			byte[] bytes = Files.readAllBytes(png);
			require(bytes.length > 8 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G',
					id + " evidence is not a PNG file: " + png);
		}
		out.println("      button.push.default and label.default captured through skija-proto at zoom 100");
	}

	/** An unsupported specimen reports as data; the run continues around it. */
	public static void checkUnsupportedReported(PrintStream out) throws IOException {
		ensureBatch();
		Map<?, ?> button = captureEntry(batchDocument, "button.push.default");
		require("CAPTURED".equals(button.get("status")),
				"the run did not continue past unsupported specimens: button.push.default is "
						+ button.get("status"));
		for (String id : List.of("combo.readonly.empty", "clabel.default")) {
			Map<?, ?> capture = captureEntry(batchDocument, id);
			require("UNSUPPORTED".equals(capture.get("status")), id + " is not UNSUPPORTED but "
					+ capture.get("status"));
			require(String.valueOf(capture.get("message")).contains(SkijaProtoBackend.ID),
					id + " skip message does not name the backend: " + capture.get("message"));
		}
		out.println("      combo.readonly.empty and clabel.default reported UNSUPPORTED, siblings unaffected");
	}

	/** The coverage count over the whole catalog, the migration progress metric. */
	public static void checkCatalogCoverage(PrintStream out) throws Exception {
		CoverageResult result = runCoverageProbe();
		int catalogSize = SpecimenCatalog.discover().all().size();
		require(result.total == catalogSize, "coverage probe saw " + result.total
				+ " specimens, the catalog has " + catalogSize);
		require(result.error == 0, result.error + " coverage probes errored:\n" + result.errorLines);
		require(result.supported > 0 && result.supported + result.unsupported == result.total,
				"inconsistent coverage numbers: supported=" + result.supported
						+ " unsupported=" + result.unsupported + " total=" + result.total);
		String families = result.unsupportedIds.stream()
				.map(id -> id.substring(0, id.indexOf('.')))
				.distinct()
				.sorted()
				.collect(Collectors.joining(", "));
		out.println("      skija-proto covers " + result.supported + " of " + result.total
				+ " catalog specimens (" + result.unsupported + " unsupported"
				+ (families.isEmpty() ? "" : ": " + families) + ")");
	}

	// -------------------------------------------------------- coverage probe

	private record CoverageResult(int total, int supported, int unsupported, int error,
			List<String> unsupportedIds, String errorLines) {
	}

	private static CoverageResult runCoverageProbe() throws Exception {
		List<String> command = new ArrayList<>();
		command.add("env");
		command.addAll(List.of("-u", "WAYLAND_DISPLAY", "-u", "XDG_SESSION_TYPE", "-u", "DISPLAY",
				"GDK_BACKEND=x11", "LIBGL_ALWAYS_SOFTWARE=1"));
		command.add("xvfb-run");
		command.addAll(List.of("-a", "-s", "-screen 0 1600x1200x24"));
		command.add(ProcessHandle.current().info().command().orElse("java"));
		command.add("--enable-native-access=ALL-UNNAMED");
		command.add("-Djava.library.path=" + BackendClasspaths.libraryPathFor(SkijaProtoBackend.ID));
		command.add("-Doracle.repoRoot=" + BackendClasspaths.repoRoot());
		command.add("-cp");
		command.add(BackendClasspaths.harnessClasspath() + java.io.File.pathSeparator
				+ BackendClasspaths.backendClasspath(SkijaProtoBackend.ID));
		command.add(CoverageProbe.class.getName());

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		StringBuilder output = new StringBuilder();
		Thread reader = new Thread(() -> {
			try (var in = process.getInputStream()) {
				byte[] buffer = new byte[4096];
				int read;
				while ((read = in.read(buffer)) >= 0)
					output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
			} catch (IOException e) {
				output.append('\n').append(e);
			}
		});
		reader.start();
		require(process.waitFor(300, TimeUnit.SECONDS), "coverage probe timed out:\n" + tail(output.toString()));
		reader.join(5000);

		String text = output.toString();
		require(process.exitValue() == 0,
				"coverage probe exited with " + process.exitValue() + ":\n" + tail(text));

		int total = -1;
		int supported = -1;
		int unsupported = -1;
		int error = -1;
		List<String> unsupportedIds = new ArrayList<>();
		StringBuilder errorLines = new StringBuilder();
		for (String line : text.split("\n")) {
			if (line.startsWith("COVERAGE ")) {
				Map<String, String> fields = new LinkedHashMap<>();
				for (String field : line.split("\\s+")) {
					int eq = field.indexOf('=');
					if (eq > 0)
						fields.put(field.substring(0, eq), field.substring(eq + 1));
				}
				total = Integer.parseInt(fields.get("total"));
				supported = Integer.parseInt(fields.get("supported"));
				unsupported = Integer.parseInt(fields.get("unsupported"));
				error = Integer.parseInt(fields.get("error"));
			} else if (line.startsWith("UNSUPPORTED ")) {
				unsupportedIds.add(line.substring("UNSUPPORTED ".length()).trim());
			} else if (line.startsWith("PROBE-ERROR ")) {
				errorLines.append(line).append('\n');
			}
		}
		require(total >= 0, "coverage probe printed no COVERAGE line:\n" + tail(text));
		return new CoverageResult(total, supported, unsupported, error, unsupportedIds, errorLines.toString());
	}

	// --------------------------------------------------------------- helpers

	private static Map<?, ?> captureEntry(Map<String, Object> document, String specimenId) {
		Object captures = document.get("captures");
		require(captures instanceof List<?> list && !list.isEmpty(), "document carries no captures");
		for (Object o : (List<?>) captures) {
			Map<?, ?> capture = (Map<?, ?>) o;
			if (specimenId.equals(capture.get("specimen")))
				return capture;
		}
		throw new AssertionError("document has no capture of '" + specimenId + "'");
	}

	private static int intValue(Object value) {
		return ((Number) value).intValue();
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static String tail(String text) {
		String[] lines = text.split("\n");
		return String.join("\n", Arrays.copyOfRange(lines, Math.max(0, lines.length - 15), lines.length));
	}

}
