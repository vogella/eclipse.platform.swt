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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.visualoracle.impl.BackendClasspaths;
import org.eclipse.swt.visualoracle.impl.ChildProcessLauncher;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.SkiaCanvasBackend;
import org.eclipse.swt.visualoracle.impl.SwtRenderEnvs;
import org.eclipse.swt.visualoracle.json.JsonParser;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.Theme;

/**
 * Selftest infrastructure for the SWT.SKIA canvas backend adapter (T11):
 * drives {@link CaptureChild} in a JVM running PR 3231's host bundle plus the
 * skia fragment, counts catalog coverage through {@link CoverageProbe}, and
 * measures the zoom-200 rendering discrepancy through per-environment child
 * processes. All pixel analysis produces numbers only; no image is ever read
 * into anything but metrics.
 */
public final class SkiaCanvasCheck {

	private static final String SCRATCH_ROOT = "/tmp/opencode/oracle-T11";
	/** Two covered specimens plus two uncovered families' representatives. */
	private static final List<String> BATCH_SPECIMENS = List.of(
			"clabel.default", "clabel.image.right", "button.push.default", "label.default");
	private static final String ZOOM_SPECIMEN = "clabel.default";
	private static final RenderEnv LTR_100 = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1);

	private static Map<String, Object> batchDocument;
	private static Path batchDirectory;
	private static String batchStdout;

	private SkiaCanvasCheck() {
	}

	// ---------------------------------------------------------------- batch

	private static synchronized void ensureBatch() throws IOException {
		if (batchDocument != null)
			return;
		Path scratch = Path.of(SCRATCH_ROOT, "selftest-" + Long.toString(System.currentTimeMillis(), 36));
		List<ChildProcessLauncher.ChildOutcome> outcomes = new ChildProcessLauncher(
				ChildProcessLauncher.configFromSystemProperties(scratch)).run(List.of(
				new ChildProcessLauncher.ChildRequest(SkiaCanvasBackend.ID, LTR_100, BATCH_SPECIMENS,
						CaptureRuntime.Strategy.COPY_AREA, false)));
		ChildProcessLauncher.ChildOutcome outcome = outcomes.get(0);
		batchDirectory = outcome.directory();
		batchStdout = Files.readString(batchDirectory.resolve("child.out.log"), StandardCharsets.UTF_8);
		require(outcome.succeeded(), "skia-canvas capture child failed: " + outcome.failureReason()
				+ "\n" + outcome.stderrTail());
		batchDocument = outcome.resultDocument();
	}

	/**
	 * The activation proof: the child observed the fragment's external canvas
	 * handler on an SWT.SKIA canvas (the same evidence verify-backend.sh gets
	 * from the activation log line) and carries the force-enabled state that
	 * specimen routing needs.
	 */
	public static void checkActivation(PrintStream out) throws IOException {
		ensureBatch();
		String line = batchStdout.lines().filter(l -> l.startsWith("BACKEND-CANVAS=")).findFirst().orElse(null);
		require(line != null, "child printed no BACKEND-CANVAS evidence line:\n" + tail(batchStdout));
		require(line.startsWith("BACKEND-CANVAS="
				+ "org.eclipse.swt.internal.skia.SkiaGlCanvasExtension paints="),
				"the SWT.SKIA canvas did not receive the fragment handler, evidence line was '"
						+ line + "'");
		int paints = Integer.parseInt(line.substring(
				line.indexOf("paints=") + "paints=".length(), line.indexOf(" force=")));
		require(paints >= 1, "the probe canvas received no paint events: '" + line + "'");
		require(line.endsWith("force=true"),
				"the child does not carry " + SkiaCanvasBackend.FORCE_ENABLED_PROPERTY
						+ ", specimen-created canvases cannot activate: '" + line + "'");
		require(batchStdout.contains("External canvas activated."),
				"child stdout lacks the 'External canvas activated.' marker "
						+ "verify-backend.sh asserts:\n" + tail(batchStdout));
		out.println("      activation evidence: " + line + ", log marker present");
	}

	/** A covered specimen captures successfully through this backend. */
	public static void checkSupportedCapture(PrintStream out) throws IOException {
		ensureBatch();
		for (String id : List.of("clabel.default", "clabel.image.right")) {
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
		out.println("      clabel.default and clabel.image.right captured through skia-canvas at zoom 100");
	}

	/** An unsupported specimen reports as data; the run continues around it. */
	public static void checkUnsupportedReported(PrintStream out) throws IOException {
		ensureBatch();
		Map<?, ?> covered = captureEntry(batchDocument, "clabel.default");
		require("CAPTURED".equals(covered.get("status")),
				"the run did not continue past unsupported specimens: clabel.default is "
						+ covered.get("status"));
		for (String id : List.of("button.push.default", "label.default")) {
			Map<?, ?> capture = captureEntry(batchDocument, id);
			require("UNSUPPORTED".equals(capture.get("status")), id + " is not UNSUPPORTED but "
					+ capture.get("status"));
			require(String.valueOf(capture.get("message")).contains(SkiaCanvasBackend.ID),
					id + " skip message does not name the backend: " + capture.get("message"));
		}
		out.println("      button.push.default and label.default reported UNSUPPORTED, "
				+ "clabel siblings unaffected");
	}

	/**
	 * The whole-catalog coverage count, the migration progress metric: only
	 * the Canvas-derived CLabel family carries the PR's handler, everything
	 * else reports UNSUPPORTED, and the probe's verdict matches the catalog
	 * exactly rather than approximately.
	 */
	public static void checkCatalogCoverage(PrintStream out) throws Exception {
		CoverageResult result = runCoverageProbe();
		SpecimenCatalog catalog = SpecimenCatalog.discover();
		int catalogSize = catalog.all().size();
		// the Canvas-derived specimens: the CLabel family and the image family's drawing canvases
		Set<String> clabelIds = new HashSet<>();
		for (var specimen : catalog.all())
			if (specimen.id().startsWith("clabel.") || specimen.id().startsWith("image.draw."))
				clabelIds.add(specimen.id());
		Set<String> expectedUnsupported = new HashSet<>();
		for (var specimen : catalog.all())
			if (!clabelIds.contains(specimen.id()))
				expectedUnsupported.add(specimen.id());
		require(result.total == catalogSize, "coverage probe saw " + result.total
				+ " specimens, the catalog has " + catalogSize);
		require(result.error == 0, result.error + " coverage probes errored:\n" + result.errorLines);
		require(result.supported == clabelIds.size(),
				"coverage probe counted " + result.supported + " supported specimens, but only the "
						+ clabelIds.size() + " CLabel and image.draw specimens extend Canvas");
		require(new HashSet<>(result.unsupportedIds).equals(expectedUnsupported),
				"unsupported set differs from catalog minus the Canvas-derived specimens");
		out.println("      skia-canvas covers " + result.supported + " of " + result.total
				+ " catalog specimens (" + result.unsupported + " unsupported: everything"
				+ " but clabel and image.draw), matching the catalog exactly");
	}

	/**
	 * The SWT.SKIA / SWT.FLAT style-bit collision, measured and reported as
	 * the finding it is instead of worked around: any widget carrying
	 * SWT.FLAT (Button, ToolBar) also reports carrying SWT.SKIA, so those
	 * specimens could never meaningfully request the Skia canvas through the
	 * style mask.
	 */
	public static void checkFlatSkiaCollision(Display display, PrintStream out) {
		require(SWT.FLAT == SWT.SKIA, "expected the PR to reuse bit " + (1 << 23)
				+ " for both styles, found SWT.FLAT=" + SWT.FLAT + " SWT.SKIA=" + SWT.SKIA);
		Shell shell = new Shell(display);
		try {
			Button flatButton = new Button(shell, SWT.PUSH | SWT.FLAT);
			ToolBar flatToolBar = new ToolBar(shell, SWT.FLAT);
			Canvas skiaCanvas = new Canvas(shell, SWT.SKIA | SWT.BORDER);
			boolean buttonReportsSkia = (flatButton.getStyle() & SWT.SKIA) != 0;
			boolean toolBarReportsSkia = (flatToolBar.getStyle() & SWT.SKIA) != 0;
			boolean canvasReportsFlat = (skiaCanvas.getStyle() & SWT.FLAT) != 0;
			require(buttonReportsSkia && toolBarReportsSkia,
					"flat widgets unexpectedly stopped reporting the SWT.SKIA bit; "
							+ "re-evaluate the collision finding");
			require(canvasReportsFlat,
					"a SWT.SKIA canvas unexpectedly stopped reporting SWT.FLAT; "
							+ "re-evaluate the collision finding");
			out.println("      SWT.FLAT == SWT.SKIA == " + SWT.FLAT + ": flat Button/ToolBar report"
					+ " carrying SKIA, a SWT.SKIA canvas reports carrying FLAT");
		} finally {
			shell.dispose();
		}
	}

	// ------------------------------------------------------------- zoom 200

	/**
	 * The HiDPI defect, reproduced and measured rather than asserted: on a
	 * genuinely scaled display ({@code GDK_SCALE=2}, i.e. zoom percent 200)
	 * the Skia canvas renders the specimen at its logical size while the
	 * native canvas honours the zoom, so the painted content of the same
	 * control occupies about half the area. Sizes come from content bounding
	 * boxes of the captured PNGs, computed numerically.
	 *
	 * Recorded as a contrast, not asserted: under {@code -Dswt.autoScale=200}
	 * alone the fragment scales correctly, so the defect is specific to real
	 * display scale factors, which narrows it upstream.
	 */
	public static void checkZoom200DiscrepancyMeasured(PrintStream out) throws Exception {
		ensureBatch();
		InkBox skia100 = inkOf(evidencePath(batchDirectory, batchDocument, ZOOM_SPECIMEN));

		Path nativeDir = spawnZoomChild(NativeBackend.ID, "gdk2", true, false);
		Path skiaDir = spawnZoomChild(SkiaCanvasBackend.ID, "gdk2", true, true);
		Path skiaAutoDir = spawnZoomChild(SkiaCanvasBackend.ID, "autoscale", false, true);

		InkBox native200 = inkOf(evidencePath(nativeDir.resolve("out"),
				documentOf(nativeDir), ZOOM_SPECIMEN));
		InkBox skia200 = inkOf(evidencePath(skiaDir.resolve("out"),
				documentOf(skiaDir), ZOOM_SPECIMEN));
		InkBox skiaAuto200 = inkOf(evidencePath(skiaAutoDir.resolve("out"),
				documentOf(skiaAutoDir), ZOOM_SPECIMEN));

		// A content box spanning the whole capture means the measurement is
		// looking at garbage (measured T26: controls hanging past an undersized
		// shell read back as black bands that saturate the box) and must fail
		// loudly rather than produce a meaningless ratio.
		for (InkBox box : List.of(skia100, native200, skia200, skiaAuto200))
			require(box.boxWidth() < box.imageWidth(),
					"content box of the zoom measurement saturates the capture (" + box
							+ "); the pixels are corrupted, not the rendering");

		double nativeOverSkia = (double) native200.boxWidth() / skia200.boxWidth();
		double skiaGrowth = (double) skia200.boxWidth() / skia100.boxWidth();
		double skiaAutoGrowth = (double) skiaAuto200.boxWidth() / skia100.boxWidth();

		require(nativeOverSkia > 1.5, "at zoom percent 200 the native content box (" + native200
				+ ") should dwarf the skia canvas' half-size rendering (" + skia200
				+ "), ratio was " + String.format("%.2f", nativeOverSkia));
		require(skiaGrowth < 1.3, "on the genuinely scaled display the skia canvas rendered "
				+ skia200 + " instead of staying near its logical size " + skia100
				+ "; the half-size defect did not reproduce, growth was "
				+ String.format("%.2f", skiaGrowth));

		out.println("      zoom-200 content boxes: native " + native200 + " vs skia-canvas " + skia200
				+ " (ratio " + String.format("%.2f", nativeOverSkia) + "); skia stayed at its"
				+ " zoom-100 logical size " + skia100 + " (growth "
				+ String.format("%.2f", skiaGrowth) + ")");
		out.println("      contrast, informational: under -Dswt.autoScale=200 the same backend grows "
				+ skia100 + " to " + skiaAuto200 + " (growth "
				+ String.format("%.2f", skiaAutoGrowth) + "), so the defect is specific to "
				+ "real display scale factors");
	}

	/**
	 * Runs CaptureChild for one specimen at zoom percent 200, either on a
	 * genuinely scaled display ({@code GDK_SCALE=2}, no autoScale override)
	 * or through {@code -Dswt.autoScale=200} at unit scale, and returns the
	 * child directory holding its out/ document and images.
	 */
	private static Path spawnZoomChild(String backendId, String tag, boolean gdkScale,
			boolean skiaActivationProperties) throws Exception {
		Path dir = Path.of(SCRATCH_ROOT,
				"zoom-" + backendId + "-" + tag + "-" + Long.toString(System.nanoTime(), 36));
		Files.createDirectories(dir);
		List<String> command = new ArrayList<>();
		command.add("env");
		command.addAll(List.of("-u", "WAYLAND_DISPLAY", "-u", "XDG_SESSION_TYPE", "-u", "DISPLAY",
				"GDK_BACKEND=x11", "LIBGL_ALWAYS_SOFTWARE=1"));
		if (gdkScale)
			command.add("GDK_SCALE=2");
		command.add("xvfb-run");
		command.addAll(List.of("-a", "-s", "-screen 0 1600x1200x24"));
		command.add(ProcessHandle.current().info().command().orElse("java"));
		command.add("--enable-native-access=ALL-UNNAMED");
		command.add("-Djava.library.path=" + BackendClasspaths.libraryPathFor(backendId));
		command.add("-Doracle.repoRoot=" + BackendClasspaths.repoRoot());
		if (skiaActivationProperties)
			for (String property : SkiaCanvasBackend.activationJvmProperties())
				command.add(property);
		if (!gdkScale)
			command.add("-Dswt.autoScale=200");
		command.addAll(List.of(
				"-D" + SwtRenderEnvs.ZOOM_PROPERTY + "=200",
				"-D" + SwtRenderEnvs.THEME_PROPERTY + "=",
				"-D" + SwtRenderEnvs.DIRECTION_PROPERTY + "=LTR",
				"-D" + SwtRenderEnvs.FONT_FAMILY_PROPERTY + "=",
				"-D" + SwtRenderEnvs.FONT_SIZE_PROPERTY + "=-1"));
		command.add("-cp");
		command.add(BackendClasspaths.harnessClasspath() + java.io.File.pathSeparator
				+ BackendClasspaths.backendClasspath(backendId));
		command.add(CaptureChild.class.getName());
		command.add("--out");
		command.add(dir.resolve("out").toString());
		command.add("--backend");
		command.add(backendId);
		command.add(ZOOM_SPECIMEN);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectOutput(dir.resolve("child.out.log").toFile());
		pb.redirectError(dir.resolve("child.err.log").toFile());
		Process process = pb.start();
		require(process.waitFor(300, TimeUnit.SECONDS),
				"zoom child '" + backendId + "/" + tag + "' timed out:\n"
						+ Files.readString(dir.resolve("child.out.log")));
		require(process.exitValue() == 0, "zoom child '" + backendId + "/" + tag + "' exited with "
				+ process.exitValue() + ":\n" + tail(Files.readString(dir.resolve("child.out.log")))
				+ "\n" + tail(Files.readString(dir.resolve("child.err.log"))));
		return dir;
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
		command.add("-Djava.library.path=" + BackendClasspaths.libraryPathFor(SkiaCanvasBackend.ID));
		command.add("-Doracle.repoRoot=" + BackendClasspaths.repoRoot());
		for (String property : SkiaCanvasBackend.activationJvmProperties())
			command.add(property);
		command.add("-cp");
		command.add(BackendClasspaths.harnessClasspath() + java.io.File.pathSeparator
				+ BackendClasspaths.backendClasspath(SkiaCanvasBackend.ID));
		command.add(CoverageProbe.class.getName());
		command.add("--backend");
		command.add(SkiaCanvasBackend.ID);

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
		require(process.waitFor(300, TimeUnit.SECONDS),
				"coverage probe timed out:\n" + tail(output.toString()));
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
		return new CoverageResult(total, supported, unsupported, error, unsupportedIds,
				errorLines.toString());
	}

	// --------------------------------------------------------------- helpers

	private record InkBox(int imageWidth, int imageHeight, int boxWidth, int boxHeight) {
		@Override
		public String toString() {
			return "content " + boxWidth + "x" + boxHeight + " in image " + imageWidth + "x"
					+ imageHeight;
		}
	}

	/**
	 * The bounding box of pixels deviating from the dominant color by more
	 * than 16 per channel: the painted content inside a capture, numbers
	 * only.
	 */
	private static InkBox inkOf(Path png) {
		ImageData data = new ImageData(png.toString());
		Map<Integer, Integer> histogram = new HashMap<>();
		for (int y = 0; y < data.height; y++)
			for (int x = 0; x < data.width; x++)
				histogram.merge(data.getPixel(x, y), 1, Integer::sum);
		int dominant = 0;
		int dominantCount = -1;
		for (Map.Entry<Integer, Integer> e : histogram.entrySet())
			if (e.getValue() > dominantCount) {
				dominantCount = e.getValue();
				dominant = e.getKey();
			}
		var background = data.palette.getRGB(dominant);
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < data.height; y++) {
			for (int x = 0; x < data.width; x++) {
				var rgb = data.palette.getRGB(data.getPixel(x, y));
				int delta = Math.max(Math.abs(rgb.red - background.red),
						Math.max(Math.abs(rgb.green - background.green),
								Math.abs(rgb.blue - background.blue)));
				if (delta > 16) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		require(maxX >= 0, "capture " + png + " carries no content pixels at all");
		return new InkBox(data.width, data.height, maxX - minX + 1, maxY - minY + 1);
	}

	private static Map<String, Object> documentOf(Path childDir) throws IOException {
		Object parsed = JsonParser.parse(
				Files.readString(childDir.resolve("out").resolve("result.json"), StandardCharsets.UTF_8));
		@SuppressWarnings("unchecked")
		Map<String, Object> document = (Map<String, Object>) parsed;
		return document;
	}

	/** Image paths are stored relative to the document's own directory. */
	private static Path evidencePath(Path documentDir, Map<String, Object> document, String specimenId) {
		Map<?, ?> capture = captureEntry(document, specimenId);
		require("CAPTURED".equals(capture.get("status")),
				specimenId + " is not CAPTURED but " + capture.get("status") + ": "
						+ capture.get("message"));
		return documentDir.resolve(String.valueOf(capture.get("image")));
	}

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
