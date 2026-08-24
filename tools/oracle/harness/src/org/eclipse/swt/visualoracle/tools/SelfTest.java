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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.visualoracle.impl.BasicCapturedImage;
import org.eclipse.swt.visualoracle.impl.ButtonPushSpecimen;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.ExactDiffer;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.SwtRenderEnvs;
import org.eclipse.swt.visualoracle.json.JsonParser;
import org.eclipse.swt.visualoracle.json.JsonWriter;
import org.eclipse.swt.visualoracle.json.ResultSchemaValidator;
import org.eclipse.swt.visualoracle.result.CaptureEntry;
import org.eclipse.swt.visualoracle.result.CaptureStatus;
import org.eclipse.swt.visualoracle.result.ComparisonEntry;
import org.eclipse.swt.visualoracle.result.RunResult;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.CaptureFailedException;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.Differ;
import org.eclipse.swt.visualoracle.spi.DiffResult;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.Tolerance;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * The harness selftest: proves the whole pipeline end to end, unattended.
 *
 * Every check either passes or fails loudly; any failure makes the process
 * exit non-zero. Checks never weaken themselves to pass.
 */
public class SelfTest {

	private static final String SCRATCH_ROOT = "/tmp/opencode/oracle-t04";

	private final PrintStream out;
	private final List<Check> checks = new ArrayList<>();
	private Path scratchDir;
	private Path repoRoot;

	public static void main(String[] args) {
		System.exit(new SelfTest(System.out).run());
	}

	public SelfTest(PrintStream out) {
		this.out = out;
	}

	private static final class Check {
		final String name;
		final boolean pass;
		final String detail;

		Check(String name, boolean pass, String detail) {
			this.name = name;
			this.pass = pass;
			this.detail = detail;
		}
	}

	/** Runs all checks; returns the desired process exit code. */
	public int run() {
		long startNanos = System.nanoTime();
		int index = 0;
		try {
			repoRoot = findRepoRoot();
			scratchDir = Files.createDirectories(Path.of(SCRATCH_ROOT,
					"selftest-" + Long.toString(System.currentTimeMillis(), 36)));

			check(index++, "backend-classpath", () -> checkBackendClasspath(repoRoot));
			check(index++, "specimen-created-and-captured", () -> checkSpecimenCapture());
			check(index++, "capture-is-deterministic", () -> checkDeterminism());
			check(index++, "differ-reports-equality", () -> checkDifferEqual());
			check(index++, "differ-detects-altered-image", () -> checkDifferDetectsChange());
			check(index++, "result-json-conforms-to-schema", () -> checkJsonSchema(repoRoot));
			check(index++, "verify-backend-fails-on-disabled-canvas", () ->
					checkVerifyBackend(repoRoot, true));
			check(index++, "verify-backend-passes-on-enabled-canvas", () ->
					checkVerifyBackend(repoRoot, false));
			check(index++, "shell-reuse-prevents-cross-talk", () -> checkShellReuseIsolation());
			check(index++, "throwing-specimen-reported-as-failure", () -> checkThrowingSpecimen());
			check(index++, "capture-deterministic-across-processes", () -> checkAcrossProcesses());
			check(index++, "xgrab-agrees-with-copyarea-at-zoom100", () -> checkXGrabZoom100());
			check(index++, "xgrab-agrees-with-copyarea-at-zoom200", () -> checkXGrabZoom200());
		} catch (Throwable t) {
			out.println("SELFTEST-ABORTED: " + t);
			t.printStackTrace(out);
			return 1;
		}

		for (Check c : checks) {
			out.printf("CHECK %d %s: %s%n", checks.indexOf(c), c.name, c.pass ? "PASS" : "FAIL");
			if (!c.pass && !c.detail.isEmpty()) {
				for (String line : c.detail.split("\n"))
					out.println("      " + line);
			}
		}
		long failed = checks.stream().filter(c -> !c.pass).count();
		double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
		if (failed == 0) {
			out.printf("SELFTEST-OK: %d/%d checks passed (%.1f s)%n", checks.size(), checks.size(), seconds);
			return 0;
		}
		out.printf("SELFTEST-FAILED: %d of %d checks failed (%.1f s)%n", failed, checks.size(), seconds);
		return 1;
	}

	private void check(int index, String name, CheckBody body) {
		try {
			body.run();
			checks.add(new Check(name, true, ""));
		} catch (Throwable t) {
			checks.add(new Check(name, false, t.toString()));
		}
	}

	private interface CheckBody {
		void run() throws Exception;
	}

	// ---------------------------------------------------------------- state

	private Display display;
	private Backend nativeBackend;
	private RenderEnv env;
	private Specimen specimen;
	private CapturedImage firstCapture;
	private CapturedImage secondCapture;
	private DiffResult equalResult;
	private DiffResult alteredResult;
	private DiffResult tolerantResult;

	// --------------------------------------------------------------- checks

	private void checkBackendClasspath(Path repoRoot) {
		display = new Display();
		nativeBackend = new NativeBackend();
		nativeBackend.configure(display);
		if (!"gtk".equals(SWT.getPlatform()))
			throw new IllegalStateException("expected gtk platform, found: " + SWT.getPlatform());
		env = SwtRenderEnvs.current(display);
		specimen = new ButtonPushSpecimen();
	}

	private void checkSpecimenCapture() {
		try (CaptureRuntime capture = new CaptureRuntime()) {
			firstCapture = capture.capture(specimen, nativeBackend, env);
		}
		requireExpectedExtent(firstCapture, specimen);
		byte[] png = firstCapture.pngBytes();
		require(png.length > 8 && (png[1] & 0xFF) == 'P' && (png[2] & 0xFF) == 'N' && (png[3] & 0xFF) == 'G',
				"pngBytes does not carry the PNG signature");
	}

	private void checkDeterminism() {
		try (CaptureRuntime capture = new CaptureRuntime()) {
			secondCapture = capture.capture(specimen, nativeBackend, env);
		}
		require(imageDatasEqual(firstCapture.imageData(), secondCapture.imageData()),
				"two captures of the same specimen differ in raw pixels");
		require(Arrays.equals(sha256(firstCapture.pngBytes()), sha256(secondCapture.pngBytes())),
				"two captures of the same specimen encode to different PNG bytes");
	}

	private void checkDifferEqual() {
		Differ differ = new ExactDiffer();
		equalResult = differ.compare(firstCapture, secondCapture, Tolerance.EXACT);
		require(equalResult.verdict() == Verdict.EQUAL, "verdict is " + equalResult.verdict());
		require(equalResult.changedPixels() == 0,
				"changedPixels is " + equalResult.changedPixels());
		require(equalResult.maxChannelDelta() == 0, "maxChannelDelta is not 0");
		require(equalResult.probableClass() == DefectClass.NONE, "probableClass is not NONE");
	}

	private void checkDifferDetectsChange() {
		Differ differ = new ExactDiffer();
		CapturedImage altered = new BasicCapturedImage(paintPatch(firstCapture.imageData()));
		alteredResult = differ.compare(firstCapture, altered, Tolerance.EXACT);
		require(alteredResult.verdict() == Verdict.DIFFERENT,
				"altered pair verdict is " + alteredResult.verdict());
		require(alteredResult.changedPixels() > 0, "altered pair reports zero changed pixels");
		require(alteredResult.maxChannelDelta() > 0, "altered pair reports zero channel delta");
		require(!alteredResult.clusters().isEmpty(), "altered pair produced no cluster");

		tolerantResult = differ.compare(firstCapture, altered, new Tolerance(255, 1.0));
		require(tolerantResult.verdict() == Verdict.WITHIN_TOLERANCE,
				"tolerant comparison verdict is " + tolerantResult.verdict());
		require(tolerantResult.changedPixels() == 0, "tolerant comparison still counts changed pixels");
	}

	private void checkJsonSchema(Path repoRoot) throws IOException {
		String base = specimen.id() + "-" + NativeBackend.ID;
		Path imagesDir = scratchDir.resolve("images");
		Files.createDirectories(imagesDir);
		Path referencePng = imagesDir.resolve(base + "-reference.png");
		Path candidatePng = imagesDir.resolve(base + "-candidate.png");
		Files.write(referencePng, firstCapture.pngBytes());
		Files.write(candidatePng, firstCapture.pngBytes());

		RunResult result = new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "oracle-harness/selftest",
				env,
				List.of(CaptureEntry.captured(specimen.id(), nativeBackend.id(), firstCapture,
						scratchDir.relativize(referencePng).toString()),
						CaptureEntry.skipped(specimen.id(), "skia-canvas",
								CaptureStatus.UNSUPPORTED, "adapter arrives with T11")),
				List.of(new ComparisonEntry(specimen.id(), NativeBackend.ID, NativeBackend.ID,
						scratchDir.relativize(referencePng).toString(),
						scratchDir.relativize(candidatePng).toString(),
						equalResult.verdict(), equalResult.changedPixels(),
						equalResult.changedFraction(), equalResult.maxChannelDelta(),
						equalResult.probableClass(), equalResult.clusters())));

		Path resultFile = scratchDir.resolve("result.json");
		Files.writeString(resultFile, result.toJson(), StandardCharsets.UTF_8);

		Object document = JsonParser.parse(Files.readString(resultFile, StandardCharsets.UTF_8));
		List<String> errors = ResultSchemaValidator.validate(document);
		require(errors.isEmpty(), "valid result rejected: " + String.join("; ", errors));

		Map<?, ?> map = (Map<?, ?>) document;
		List<?> captures = (List<?>) map.get("captures");
		require(String.valueOf(((Map<?, ?>) captures.get(0)).get("specimen")).equals(specimen.id()),
				"roundtrip lost the specimen id");
		List<?> comparisons = (List<?>) map.get("comparisons");
		require(String.valueOf(((Map<?, ?>) comparisons.get(0)).get("verdict"))
				.equals(equalResult.verdict().name()), "roundtrip lost the verdict");

		requireSchemaRejects(document, doc -> ((Map<?, ?>) doc).remove("schemaVersion"),
				"missing schemaVersion accepted");
		requireSchemaRejects(document, doc -> mutate(doc, "captures", 0, "status", "MAYBE"),
				"invalid capture status accepted");
		requireSchemaRejects(document, doc -> mutate(doc, "comparisons", 0, "verdict", Integer.valueOf(7)),
				"non-string verdict accepted");
		requireSchemaRejects(document, doc -> mutate(doc, null, -1, "surprise", Boolean.TRUE),
				"unknown property accepted");
		Map<Object, Object> badUnsupported = new LinkedHashMap<>();
		badUnsupported.put("specimen", specimen.id());
		badUnsupported.put("backend", "skia-canvas");
		badUnsupported.put("status", "UNSUPPORTED");
		badUnsupported.put("width", Integer.valueOf(10));
		require(!ResultSchemaValidator.validate(badUnsupported).isEmpty(),
				"UNSUPPORTED capture carrying width accepted");

		out.println("      result written to " + resultFile);
	}

	private void checkVerifyBackend(Path repoRoot, boolean disableCanvas) throws Exception {
		Path script = repoRoot.resolve("tools/oracle/verify-backend.sh");
		ProcessBuilder pb = new ProcessBuilder(script.toString(), "skia-canvas");
		pb.environment().putIfAbsent("JDK_JAVA_OPTIONS", "");
		if (disableCanvas)
			pb.environment().put("JDK_JAVA_OPTIONS", "-Dorg.eclipse.swt.external.canvas:disabled=true");
		else
			pb.environment().remove("JDK_JAVA_OPTIONS");
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
		require(process.waitFor(300, TimeUnit.SECONDS), "verify-backend.sh timed out after 300 s");
		reader.join(5000);

		String text = output.toString();
		boolean verifyOk = text.contains("VERIFY-OK");
		if (disableCanvas) {
			require(process.exitValue() != 0,
					"verify-backend.sh reported success although the Skia canvas was disabled");
			require(text.contains("External canvas disabled."),
					"expected the disabled-canvas marker in probe output");
			require(!verifyOk, "probe printed VERIFY-OK while the canvas was disabled");
			out.println("      verify-backend.sh correctly rejected the disabled canvas");
		} else {
			require(process.exitValue() == 0,
					"positive control failed unexpectedly:\n" + tail(text));
			require(verifyOk, "no VERIFY-OK marker in positive control output:\n" + tail(text));
			out.println("      positive control confirmed the script can say yes");
		}
	}

	// -------------------------------------------------- capture runtime checks

	/**
	 * Proves that a reused shell cannot leak one specimen's rendering into
	 * the next: button, label, button again must give identical button bytes,
	 * and the same as a fresh shell produces.
	 */
	private void checkShellReuseIsolation() {
		Specimen label = new LabelSpecimen();
		CapturedImage before;
		CapturedImage after;
		try (CaptureRuntime shared = new CaptureRuntime()) {
			before = shared.capture(specimen, nativeBackend, env);
			CapturedImage other = shared.capture(label, nativeBackend, env);
			requireExpectedExtent(other, label);
			after = shared.capture(specimen, nativeBackend, env);
		}
		require(imageDatasEqual(before.imageData(), after.imageData()),
				"specimen changed after another specimen ran in between (shell cross-talk)");
		try (CaptureRuntime fresh = new CaptureRuntime()) {
			CapturedImage freshCapture = fresh.capture(specimen, nativeBackend, env);
			require(Arrays.equals(before.pngBytes(), freshCapture.pngBytes()),
					"reused-shell capture differs from fresh-shell capture");
		}
	}

	/** A specimen whose creation throws is failure data, not a crashed run. */
	private void checkThrowingSpecimen() {
		Specimen bad = new ThrowingSpecimen();
		try (CaptureRuntime runtime = new CaptureRuntime()) {
			try {
				runtime.capture(bad, nativeBackend, env);
				throw new AssertionError("capture of a throwing specimen did not fail");
			} catch (CaptureFailedException e) {
				require(String.valueOf(e.getMessage()).contains(bad.id()),
						"failure message does not identify the specimen: " + e.getMessage());
				require(e.getCause() != null, "creation failure lost its cause");
			}
			CapturedImage afterFailure = runtime.capture(specimen, nativeBackend, env);
			require(Arrays.equals(afterFailure.pngBytes(), firstCapture.pngBytes()),
					"run did not continue cleanly after a failed specimen");
		}
	}

	/** Same specimen, several separate processes, same bytes. */
	private void checkAcrossProcesses() throws Exception {
		ProbeResult first = runProbe("COPY_AREA", null);
		ProbeResult second = runProbe("COPY_AREA", null);
		require(first.sha256().equals(second.sha256()),
				"two separate processes captured different PNG bytes: "
						+ first.sha256() + " vs " + second.sha256());
		String ownSha = sha256Hex(firstCapture.pngBytes());
		require(first.sha256().equals(ownSha),
				"cross-process capture differs from this process' own capture: "
						+ first.sha256() + " vs " + ownSha);
	}

	/** Fallback and primary strategy must agree exactly at unit zoom. */
	private void checkXGrabZoom100() throws Exception {
		ProbeResult copyArea = runProbe("COPY_AREA", null);
		ProbeResult xgrab = runProbe("X11_GRAB", null);
		require(xgrab.width() == copyArea.width() && xgrab.height() == copyArea.height(),
				"strategies disagree on extent at zoom 100: " + xgrab + " vs " + copyArea);
		require(xgrab.sha256().equals(copyArea.sha256()),
				"X11 grab and copyArea disagree at zoom 100: "
						+ xgrab.sha256() + " vs " + copyArea.sha256());
	}

	/**
	 * The zoom-200 agreement is what proves the crop-origin fix recorded in
	 * ADR-001: with the old formula the X11 grab captured a shifted region at
	 * zoom 200, so these two probes could never agree byte for byte.
	 */
	private void checkXGrabZoom200() throws Exception {
		ProbeResult copyArea = runProbe("COPY_AREA", "-Dswt.autoScale=200");
		ProbeResult xgrab = runProbe("X11_GRAB", "-Dswt.autoScale=200");
		int expectedWidth = Math.round(specimen.preferredSize().x * 2f);
		int expectedHeight = Math.round(specimen.preferredSize().y * 2f);
		require(copyArea.width() == expectedWidth && copyArea.height() == expectedHeight,
				"copyArea is not device-resolution at zoom 200: " + copyArea);
		require(xgrab.width() == expectedWidth && xgrab.height() == expectedHeight,
				"X11 grab is not device-resolution at zoom 200: " + xgrab);
		require(xgrab.sha256().equals(copyArea.sha256()),
				"X11 grab and copyArea disagree at zoom 200: "
						+ xgrab.sha256() + " vs " + copyArea.sha256()
						+ "; the crop origin fix regressed");
	}

	private void requireExpectedExtent(CapturedImage image, Specimen captured) {
		int expectedWidth = Math.round(captured.preferredSize().x * env.zoomPercent() / 100f);
		int expectedHeight = Math.round(captured.preferredSize().y * env.zoomPercent() / 100f);
		require(image.width() == expectedWidth && image.height() == expectedHeight,
				"captured extent " + image.width() + "x" + image.height() + ", expected "
						+ expectedWidth + "x" + expectedHeight + " (preferred size scaled by zoom)");
	}

	// ------------------------------------------------------------ probe runs

	private record ProbeResult(String sha256, int width, int height) {
		@Override
		public String toString() {
			return width + "x" + height + " sha=" + sha256;
		}
	}

	/**
	 * Runs {@link CaptureProbe} in a child process on this display and
	 * returns its single CAPTURE line. Graphical environment variables are
	 * re-pinned so the child cannot silently leave headless mode.
	 */
	private ProbeResult runProbe(String strategy, String extraJvmOption) throws Exception {
		List<String> command = new ArrayList<>();
		command.add(ProcessHandle.current().info().command().orElse("java"));
		command.add("--enable-native-access=ALL-UNNAMED");
		String libPath = System.getProperty("java.library.path", "");
		if (!libPath.isEmpty())
			command.add("-Djava.library.path=" + libPath);
		command.add("-Doracle.repoRoot=" + repoRoot);
		if (extraJvmOption != null && !extraJvmOption.isEmpty())
			command.add(extraJvmOption);
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(CaptureProbe.class.getName());
		command.add(strategy);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.environment().remove("WAYLAND_DISPLAY");
		pb.environment().remove("XDG_SESSION_TYPE");
		pb.environment().put("GDK_BACKEND", "x11");
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
		require(process.waitFor(180, TimeUnit.SECONDS), "capture probe timed out:\n" + tail(output.toString()));
		reader.join(5000);

		String text = output.toString();
		for (String line : text.split("\n")) {
			if (!line.startsWith("CAPTURE "))
				continue;
			String sha = null;
			int width = -1;
			int height = -1;
			for (String field : line.split("\\s+")) {
				int eq = field.indexOf('=');
				if (eq < 0)
					continue;
				String key = field.substring(0, eq);
				String value = field.substring(eq + 1);
				switch (key) {
					case "sha256" -> sha = value;
					case "width" -> width = Integer.parseInt(value);
					case "height" -> height = Integer.parseInt(value);
				}
			}
			if (sha != null && width > 0 && height > 0)
				return new ProbeResult(sha, width, height);
		}
		throw new AssertionError("probe '" + strategy + "' printed no usable CAPTURE line:\n" + tail(text));
	}

	// -------------------------------------------------------------- helpers

	private interface SchemaMutation {
		void apply(Object document);
	}

	private void requireSchemaRejects(Object document, SchemaMutation mutation, String what) {
		Object copy = JsonParser.parse(JsonWriter.write(document));
		mutation.apply(copy);
		List<String> errors = ResultSchemaValidator.validate(copy);
		require(!errors.isEmpty(), what);
	}

	@SuppressWarnings("unchecked")
	private static void mutate(Object document, String arrayKey, int index, String field, Object value) {
		Map<String, Object> map = (Map<String, Object>) document;
		if (arrayKey == null) {
			map.put(field, value);
			return;
		}
		Object list = map.get(arrayKey);
		((Map<String, Object>) ((List<Object>) list).get(index)).put(field, value);
	}

	private ImageData paintPatch(ImageData source) {
		Image image = new Image(display, source);
		try {
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
				gc.fillRectangle(source.width / 4, source.height / 4,
						source.width / 2, source.height / 2);
			} finally {
				gc.dispose();
			}
			return image.getImageData();
		} finally {
			image.dispose();
		}
	}

	private static boolean imageDatasEqual(ImageData a, ImageData b) {
		if (a.width != b.width || a.height != b.height || a.depth != b.depth
				|| a.bytesPerLine != b.bytesPerLine || !palettesEqual(a.palette, b.palette))
			return false;
		if (!Arrays.equals(a.data, b.data))
			return false;
		if (!Arrays.equals(a.alphaData, b.alphaData))
			return false;
		return a.alpha == b.alpha;
	}

	/** PaletteData has no equals(); compare by its defining fields. */
	private static boolean palettesEqual(PaletteData a, PaletteData b) {
		if (a.isDirect != b.isDirect)
			return false;
		if (a.isDirect)
			return a.redMask == b.redMask && a.greenMask == b.greenMask && a.blueMask == b.blueMask
					&& a.redShift == b.redShift && a.greenShift == b.greenShift && a.blueShift == b.blueShift;
		return Arrays.equals(a.colors, b.colors);
	}

	private static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String sha256Hex(byte[] data) {
		StringBuilder sb = new StringBuilder();
		for (byte b : sha256(data))
			sb.append(String.format("%02x", b));
		return sb.toString();
	}

	/** Second widget family for the shell-reuse cross-talk check. */
	private static final class LabelSpecimen implements Specimen {
		@Override
		public String id() {
			return "test.label.default";
		}

		@Override
		public org.eclipse.swt.graphics.Point preferredSize() {
			return new org.eclipse.swt.graphics.Point(180, 48);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Label label = new Label(parent, SWT.BORDER);
			label.setText("ABC");
			ctx.configure(label);
			return label;
		}
	}

	/** A broken factory: creation must surface as failure data. */
	private static final class ThrowingSpecimen implements Specimen {
		@Override
		public String id() {
			return "test.throwing.default";
		}

		@Override
		public org.eclipse.swt.graphics.Point preferredSize() {
			return new org.eclipse.swt.graphics.Point(100, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			throw new IllegalStateException("deliberate failure for selftest");
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static String tail(String text) {
		String[] lines = text.split("\n");
		return String.join("\n", Arrays.copyOfRange(lines, Math.max(0, lines.length - 15), lines.length));
	}

	static Path findRepoRoot() {
		Path candidate = Path.of(System.getProperty("oracle.repoRoot", ""));
		if (Files.isRegularFile(candidate.resolve("tools/oracle/verify-backend.sh")))
			return candidate.toAbsolutePath().normalize();
		Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
		while (dir != null) {
			if (Files.isRegularFile(dir.resolve("tools/oracle/verify-backend.sh")))
				return dir;
			dir = dir.getParent();
		}
		throw new IllegalStateException("cannot locate repository root containing tools/oracle/"
				+ "; start through the oracle wrapper or set -Doracle.repoRoot");
	}
}
