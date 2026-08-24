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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.visualoracle.catalog.ButtonModule;
import org.eclipse.swt.visualoracle.impl.BasicCapturedImage;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.ChildProcessLauncher;
import org.eclipse.swt.visualoracle.impl.LaunchConfig;
import org.eclipse.swt.visualoracle.impl.ClusterDiffer;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.ResultMerger;
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
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.Tolerance;
import org.eclipse.swt.visualoracle.spi.Theme;
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
			check(index++, "catalog-discovered-and-wellformed", CatalogCheck::checkDiscovery);
			check(index++, "catalog-triple-render-deterministic", () ->
					CatalogCheck.checkTripleRender(display, nativeBackend, env, out));
			check(index++, "launch-config-maps-environment-to-process-settings", () ->
					checkLaunchConfigMapping());
			check(index++, "child-parallelism-bound-honored", () -> checkParallelismBound());
			check(index++, "child-zoom-proven-by-captured-size", () -> checkChildZoom());
			check(index++, "child-mirrors-right-to-left", () -> checkChildRtl());
			check(index++, "child-theme-takes-effect", () -> checkChildTheme());
			check(index++, "child-refuses-wrong-environment", () -> checkChildMismatch());
			check(index++, "crashed-child-recorded-run-continues", () -> checkCrashedChild());
			check(index++, "hung-child-times-out-run-continues", () -> checkHungChild());
			check(index++, "merged-child-results-validate-against-schema", () -> checkMergedResults());
			check(index++, "diff-equality-under-default-tolerance", () ->
					DiffCheck.checkEquality(firstCapture, out));
			check(index++, "diff-aa-edge-noise-stays-within-tolerance", () ->
					DiffCheck.checkAntiAliasingWithinTolerance(firstCapture, out));
			check(index++, "diff-global-tint-is-different", () ->
					DiffCheck.checkGlobalTintDetected(firstCapture));
			check(index++, "diff-missing-ring-is-different-and-bounded", () ->
					DiffCheck.checkThinRingDefect(firstCapture, out));
			check(index++, "diff-removed-square-is-different-and-bounded", () ->
					DiffCheck.checkRemovedSquareBoundedByClusters(firstCapture, out));
			check(index++, "diff-shifted-content-classified", () ->
					DiffCheck.checkShiftedContentClassified(firstCapture));
			check(index++, "diff-two-defects-two-clusters", () ->
					DiffCheck.checkTwoDefectsTwoClusters(firstCapture));
			check(index++, "diff-size-mismatch-whole-area", () ->
					DiffCheck.checkSizeMismatchWholeArea(firstCapture));
			check(index++, "diff-throughput-measured", () -> DiffCheck.checkThroughput(out));
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
		specimen = new ButtonModule.Push();
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
		Differ differ = new ClusterDiffer();
		equalResult = differ.compare(firstCapture, secondCapture, Tolerance.EXACT);
		require(equalResult.verdict() == Verdict.EQUAL, "verdict is " + equalResult.verdict());
		require(equalResult.changedPixels() == 0,
				"changedPixels is " + equalResult.changedPixels());
		require(equalResult.maxChannelDelta() == 0, "maxChannelDelta is not 0");
		require(equalResult.probableClass() == DefectClass.NONE, "probableClass is not NONE");
	}

	private void checkDifferDetectsChange() {
		Differ differ = new ClusterDiffer();
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

	// ------------------------------------------------- environment control

	private static final String SCRATCH_ROOT_T05 = "/tmp/opencode/oracle-t05";
	/** The specimen whose pixels visibly move under RTL; centered captions do not. */
	private static final String MIRROR_SPECIMEN = "label.default";
	private static final String REFERENCE_SPECIMEN = "button.push.default";

	private Map<String, ChildProcessLauncher.ChildOutcome> t05Batch;

	/**
	 * Launches the standard T05 child batch once: two left-to-right children
	 * (split specimens, for merging), one right-to-left, one HighContrast
	 * theme and one zoom-200 child. Every child gets its own Xvfb display.
	 */
	private void ensureT05Batch() {
		if (t05Batch != null)
			return;
		RenderEnv ltr = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1);
		List<ChildProcessLauncher.ChildRequest> requests = List.of(
				ChildProcessLauncher.ChildRequest.of(ltr, REFERENCE_SPECIMEN),
				ChildProcessLauncher.ChildRequest.of(ltr, MIRROR_SPECIMEN),
				ChildProcessLauncher.ChildRequest.of(new RenderEnv(100, Theme.PLATFORM_DEFAULT,
						Direction.RTL, "", -1), MIRROR_SPECIMEN, REFERENCE_SPECIMEN),
				ChildProcessLauncher.ChildRequest.of(new RenderEnv(100, new Theme("HighContrast"),
						Direction.LTR, "", -1), REFERENCE_SPECIMEN),
				ChildProcessLauncher.ChildRequest.of(new RenderEnv(200, Theme.PLATFORM_DEFAULT,
						Direction.LTR, "", -1), REFERENCE_SPECIMEN));
		Path scratch = Path.of(SCRATCH_ROOT_T05, "selftest-" + Long.toString(System.currentTimeMillis(), 36));
		t05Batch = new LinkedHashMap<>();
		List<ChildProcessLauncher.ChildOutcome> outcomes =
				new ChildProcessLauncher(ChildProcessLauncher.configFromSystemProperties(scratch)).run(requests);
		String[] tags = {"ltrA", "ltrB", "rtl", "hc", "z200"};
		for (int i = 0; i < tags.length; i++)
			t05Batch.put(tags[i], outcomes.get(i));
	}

	/**
	 * Pure mapping proof: a requested {@link RenderEnv} becomes the process
	 * settings that realise it (zoom property, GTK_THEME variable or its
	 * removal for the platform default, direction and font properties), and
	 * environment matching accepts the system font whatever it is called.
	 */
	private void checkLaunchConfigMapping() {
		LaunchConfig themed = SwtRenderEnvs.launch(
				new RenderEnv(150, new Theme("Adwaita"), Direction.RTL, "Sans", 12));
		require(themed.jvmProperties().contains("-Dswt.autoScale=150"),
				"zoom 150 did not map to the swt.autoScale property: " + themed.jvmProperties());
		require("Adwaita".equals(themed.variables().get("GTK_THEME")),
				"theme Adwaita did not map to the GTK_THEME variable");
		require(!themed.removedVariables().contains("GTK_THEME"), "themed config removes GTK_THEME");
		require(themed.jvmProperties().contains("-D" + SwtRenderEnvs.DIRECTION_PROPERTY + "=RTL"),
				"RTL did not map to the direction property");

		LaunchConfig deflt = SwtRenderEnvs.launch(
				new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1));
		require(deflt.jvmProperties().contains("-Dswt.autoScale=100"),
				"zoom 100 did not map to the swt.autoScale property");
		require(deflt.variables().isEmpty() && deflt.removedVariables().contains("GTK_THEME"),
				"platform default theme must remove GTK_THEME instead of setting it");

		RenderEnv systemFont = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1);
		RenderEnv actual = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "Whatever", 11);
		require(SwtRenderEnvs.matches(systemFont, actual),
				"a system-font request must accept any reported system font");
		require(!SwtRenderEnvs.matches(systemFont,
						new RenderEnv(200, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1)),
				"a zoom mismatch must never match");
	}

	/** Proves the concurrency bound bites: at most N tasks in flight. */
	private void checkParallelismBound() {
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger peak = new AtomicInteger();
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			tasks.add(() -> {
				int now = inFlight.incrementAndGet();
				peak.accumulateAndGet(now, Math::max);
				Thread.sleep(120);
				inFlight.decrementAndGet();
				return now;
			});
		}
		List<Integer> results = ChildProcessLauncher.runBounded(2, tasks);
		require(results.size() == 8, "bounded runner lost results");
		require(peak.get() == 2, "expected peak concurrency exactly 2, observed " + peak.get());
	}

	/**
	 * The zoom-200 child must genuinely render at 200 percent, proven by the
	 * captured extent being twice the specimen's preferred size, both as
	 * recorded in the result document and as decoded from the PNG evidence.
	 */
	private void checkChildZoom() {
		ensureT05Batch();
		ChildProcessLauncher.ChildOutcome outcome = t05Batch.get("z200");
		require(outcome.succeeded(), "zoom-200 child failed: " + outcome.failureReason() + "\n" + outcome.stderrTail());
		Map<?, ?> capture = soleCapture(outcome.resultDocument(), REFERENCE_SPECIMEN);
		Specimen specimen = SpecimenCatalog.discover().byId(REFERENCE_SPECIMEN)
				.orElseThrow(() -> new AssertionError(REFERENCE_SPECIMEN + " missing from catalog"));
		int expectedWidth = Math.round(specimen.preferredSize().x * 2f);
		int expectedHeight = Math.round(specimen.preferredSize().y * 2f);
		require(intValue(capture.get("width")) == expectedWidth && intValue(capture.get("height")) == expectedHeight,
				"child document records " + capture.get("width") + "x" + capture.get("height")
						+ ", expected device extent " + expectedWidth + "x" + expectedHeight + " at zoom 200");
		ImageData png = new ImageData(pngPath(outcome.directory(), capture).toString());
		require(png.width == expectedWidth && png.height == expectedHeight,
				"PNG evidence is " + png.width + "x" + png.height + ", not the zoom-200 extent");
		require(intValue(((Map<?, ?>) outcome.resultDocument().get("environment")).get("zoomPercent")) == 200,
				"document does not record zoomPercent 200");
		out.println("      zoom-200 child rendered " + png.width + "x" + png.height + " (preferred size doubled)");
	}

	/**
	 * The right-to-left child must genuinely mirror: same specimen, same
	 * machine, only the direction differs, yet the pixels move.
	 */
	private void checkChildRtl() {
		ensureT05Batch();
		ChildProcessLauncher.ChildOutcome ltr = t05Batch.get("ltrB");
		ChildProcessLauncher.ChildOutcome rtl = t05Batch.get("rtl");
		require(ltr.succeeded() && rtl.succeeded(),
				"a baseline child failed: " + ltr.failureReason() + " / " + rtl.failureReason());
		int changed = pixelDiff(pngPath(ltr.directory(), soleCapture(ltr.resultDocument(), MIRROR_SPECIMEN)),
				pngPath(rtl.directory(), soleCapture(rtl.resultDocument(), MIRROR_SPECIMEN)));
		require(changed > 100, "RTL child rendered identical pixels to LTR (" + changed
				+ " differing); text direction control has no effect");
		out.println("      RTL mirrors " + MIRROR_SPECIMEN + ": " + changed + " pixels moved vs LTR");
	}

	/** The themed child must render differently from the default theme. */
	private void checkChildTheme() {
		ensureT05Batch();
		ChildProcessLauncher.ChildOutcome plain = t05Batch.get("ltrA");
		ChildProcessLauncher.ChildOutcome hc = t05Batch.get("hc");
		require(plain.succeeded() && hc.succeeded(),
				"a theme child failed: " + plain.failureReason() + " / " + hc.failureReason());
		int changed = pixelDiff(pngPath(plain.directory(), soleCapture(plain.resultDocument(), REFERENCE_SPECIMEN)),
				pngPath(hc.directory(), soleCapture(hc.resultDocument(), REFERENCE_SPECIMEN)));
		require(changed > 500, "HighContrast child differs by only " + changed
				+ " pixels; the GTK_THEME control appears ineffective");
		out.println("      HighContrast theme changes " + changed + " of "
				+ intValue(soleCapture(hc.resultDocument(), REFERENCE_SPECIMEN).get("width")) + "x"
				+ intValue(soleCapture(hc.resultDocument(), REFERENCE_SPECIMEN).get("height"))
				+ " pixels");
	}

	/**
	 * A child that finds itself in a different environment than requested
	 * refuses to lie: every entry FAILED, exit code says so. The conflicting
	 * launch bypasses the launcher on purpose, because the launcher cannot
	 * produce one.
	 */
	private void checkChildMismatch() throws Exception {
		Path dir = Path.of(SCRATCH_ROOT_T05, "selftest-mismatch-" + Long.toString(System.currentTimeMillis(), 36));
		int exit = spawnRawChild(dir, dir.resolve("out"), REFERENCE_SPECIMEN,
				"-Dswt.autoScale=200",
				"-D" + SwtRenderEnvs.ZOOM_PROPERTY + "=100",
				"-D" + SwtRenderEnvs.THEME_PROPERTY + "=",
				"-D" + SwtRenderEnvs.DIRECTION_PROPERTY + "=LTR",
				"-D" + SwtRenderEnvs.FONT_FAMILY_PROPERTY + "=",
				"-D" + SwtRenderEnvs.FONT_SIZE_PROPERTY + "=-1");
		require(exit == CaptureChild.EXIT_ENV_MISMATCH,
				"mismatched child exited with " + exit + ", expected " + CaptureChild.EXIT_ENV_MISMATCH);
		Object doc = JsonParser.parse(Files.readString(dir.resolve("out").resolve("result.json")));
		List<String> errors = ResultSchemaValidator.validate(doc);
		require(errors.isEmpty(), "mismatch result invalid: " + String.join("; ", errors));
		List<?> captures = (List<?>) ((Map<?, ?>) doc).get("captures");
		require(!captures.isEmpty(), "mismatch result carries no captures");
		for (Object o : captures) {
			Map<?, ?> capture = (Map<?, ?>) o;
			require("FAILED".equals(capture.get("status")),
					"mismatched capture not recorded as failure: " + capture);
			require(String.valueOf(capture.get("message")).contains("environment mismatch"),
					"failure message does not name the mismatch: " + capture.get("message"));
		}
	}

	/** A crashing child becomes failure data with its stderr; the run continues. */
	private void checkCrashedChild() {
		Path scratch = Path.of(SCRATCH_ROOT_T05, "selftest-crash-" + Long.toString(System.currentTimeMillis(), 36));
		RenderEnv base = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1);
		List<ChildProcessLauncher.ChildOutcome> outcomes = new ChildProcessLauncher(
				ChildProcessLauncher.configFromSystemProperties(scratch)).run(List.of(
				new ChildProcessLauncher.ChildRequest(NativeBackend.ID, base,
						List.of("test.does.not.exist"), CaptureRuntime.Strategy.COPY_AREA, false),
				ChildProcessLauncher.ChildRequest.of(base, REFERENCE_SPECIMEN)));
		ChildProcessLauncher.ChildOutcome crashed = outcomes.get(0);
		ChildProcessLauncher.ChildOutcome healthy = outcomes.get(1);
		require(crashed.resultDocument() == null && !crashed.succeeded(),
				"a child that exits non-zero must be recorded as failure");
		require(String.valueOf(crashed.failureReason()).contains("exit"),
				"failure reason does not mention the exit code: " + crashed.failureReason());
		require(crashed.stderrTail().contains("unknown specimen id"),
				"stderr tail lacks the child's diagnosis:\n" + crashed.stderrTail());
		require(!crashed.failureEntries().isEmpty()
						&& "FAILED".equals(crashed.failureEntries().get(0).status().name()),
				"synthesized entries are not failures");
		require(healthy.succeeded(), "the run did not continue past the crash: " + healthy.failureReason());
		out.println("      crash recorded: '" + crashed.failureReason() + "', stderr kept ("
				+ crashed.stderrTail().length() + " chars); sibling completed normally");
	}

	/** A hung child is killed at the timeout and recorded; the run continues. */
	private void checkHungChild() {
		Path scratch = Path.of(SCRATCH_ROOT_T05, "selftest-hang-" + Long.toString(System.currentTimeMillis(), 36));
		RenderEnv base = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1);
		ChildProcessLauncher.Config config = new ChildProcessLauncher.Config(2, 8, scratch);
		List<ChildProcessLauncher.ChildOutcome> outcomes = new ChildProcessLauncher(config).run(List.of(
				new ChildProcessLauncher.ChildRequest(NativeBackend.ID, base,
						List.of(REFERENCE_SPECIMEN), CaptureRuntime.Strategy.COPY_AREA, true),
				ChildProcessLauncher.ChildRequest.of(base, MIRROR_SPECIMEN)));
		ChildProcessLauncher.ChildOutcome hung = outcomes.get(0);
		ChildProcessLauncher.ChildOutcome healthy = outcomes.get(1);
		require(hung.timedOut(), "the hanging child was not recognized as timed out");
		require(hung.exitCode() == null, "a killed child cannot report an exit code");
		require(String.valueOf(hung.failureReason()).contains("timed out"),
				"failure reason does not say timed out: " + hung.failureReason());
		require(!hung.failureEntries().isEmpty(), "no failure entries synthesized for the hung child");
		require(healthy.succeeded(), "the run did not continue past the hang: " + healthy.failureReason());
		out.println("      hang killed after 8 s, recorded as failure; sibling completed normally");
	}

	/** Two children's documents merge into one schema-valid run result. */
	private void checkMergedResults() throws IOException {
		ensureT05Batch();
		ChildProcessLauncher.ChildOutcome first = t05Batch.get("ltrA");
		ChildProcessLauncher.ChildOutcome second = t05Batch.get("ltrB");
		require(first.succeeded() && second.succeeded(), "merge inputs failed to run");

		Path mergedDir = Path.of(SCRATCH_ROOT_T05,
				"selftest-merged-" + Long.toString(System.currentTimeMillis(), 36));
		Map<String, Object> merged = ResultMerger.merge(
				List.of(new ResultMerger.MergeInput(first.resultDocument(), first.directory()),
						new ResultMerger.MergeInput(second.resultDocument(), second.directory())),
				"oracle-harness/" + OracleCli.VERSION + "/selftest-merger", mergedDir);
		Path mergedFile = mergedDir.resolve("result.json");
		Files.createDirectories(mergedDir);
		Files.writeString(mergedFile, JsonWriter.write(merged), StandardCharsets.UTF_8);

		Object reparsed = JsonParser.parse(Files.readString(mergedFile, StandardCharsets.UTF_8));
		List<String> errors = ResultSchemaValidator.validate(reparsed);
		require(errors.isEmpty(), "merged result rejected by schema: " + String.join("; ", errors));

		List<?> captures = (List<?>) ((Map<?, ?>) reparsed).get("captures");
		require(captures.size() == 2, "merged document should carry both children's captures, has "
				+ captures.size());
		for (Object o : captures) {
			String image = String.valueOf(((Map<?, ?>) o).get("image"));
			require(Files.isRegularFile(mergedDir.resolve(image)),
					"rebased image path does not resolve: " + image);
			require(!image.startsWith("/"), "image path must stay relative: " + image);
		}
		out.println("      merged 2 children into " + mergedFile + ", schema-valid, images resolve");
	}

	private static Map<?, ?> soleCapture(Map<String, Object> document, String specimenId) {
		List<?> captures = (List<?>) document.get("captures");
		Map<?, ?> found = null;
		for (Object o : captures) {
			Map<?, ?> capture = (Map<?, ?>) o;
			if (specimenId.equals(capture.get("specimen")))
				found = capture;
		}
		if (found == null)
			throw new AssertionError("document has no capture of '" + specimenId + "'");
		if (!"CAPTURED".equals(found.get("status")))
			throw new AssertionError("capture of '" + specimenId + "' is " + found.get("status"));
		return found;
	}

	private static Path pngPath(Path childDir, Map<?, ?> capture) {
		return childDir.resolve(String.valueOf(capture.get("image"))).normalize();
	}

	private static int intValue(Object value) {
		return ((Number) value).intValue();
	}

	/** Counts pixels that differ between two PNG files, numbers only. */
	private static int pixelDiff(Path a, Path b) {
		ImageData dataA = new ImageData(a.toString());
		ImageData dataB = new ImageData(b.toString());
		if (dataA.width != dataB.width || dataA.height != dataB.height)
			throw new AssertionError("extents differ: " + dataA.width + "x" + dataA.height + " vs "
					+ dataB.width + "x" + dataB.height);
		int changed = 0;
		for (int y = 0; y < dataA.height; y++) {
			for (int x = 0; x < dataA.width; x++) {
				if (dataA.getPixel(x, y) != dataB.getPixel(x, y))
					changed++;
				else if (dataA.alphaData != null && dataB.alphaData != null
						&& dataA.getAlpha(x, y) != dataB.getAlpha(x, y))
					changed++;
			}
		}
		return changed;
	}

	/**
	 * Spawns CaptureChild directly with arbitrary JVM options, so tests can
	 * create configurations the launcher itself would never produce.
	 * Returns the child's exit code.
	 */
	private int spawnRawChild(Path dir, Path outDir, String specimenId, String... jvmOptions) throws Exception {
		List<String> command = new ArrayList<>();
		command.add("env");
		command.addAll(List.of("-u", "WAYLAND_DISPLAY", "-u", "XDG_SESSION_TYPE", "-u", "DISPLAY"));
		command.addAll(List.of("GDK_BACKEND=x11", "LIBGL_ALWAYS_SOFTWARE=1"));
		command.add("xvfb-run");
		command.addAll(List.of("-a", "-s", "-screen 0 1600x1200x24"));
		command.add(ProcessHandle.current().info().command().orElse("java"));
		command.add("--enable-native-access=ALL-UNNAMED");
		command.add("-Djava.library.path=" + System.getProperty("java.library.path", ""));
		command.add("-Doracle.repoRoot=" + repoRoot);
		for (String option : jvmOptions)
			command.add(option);
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(CaptureChild.class.getName());
		command.add("--out");
		command.add(outDir.toString());
		command.add(specimenId);

		Files.createDirectories(dir);
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectOutput(dir.resolve("raw.out.log").toFile());
		pb.redirectError(dir.resolve("raw.err.log").toFile());
		Process process = pb.start();
		if (!process.waitFor(180, TimeUnit.SECONDS))
			process.destroyForcibly();
		return process.exitValue();
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
