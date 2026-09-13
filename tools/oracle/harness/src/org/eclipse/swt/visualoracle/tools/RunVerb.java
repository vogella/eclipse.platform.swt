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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.visualoracle.impl.BasicCapturedImage;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.ChildProcessLauncher;
import org.eclipse.swt.visualoracle.impl.ClusterDiffer;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.ResultMerger;
import org.eclipse.swt.visualoracle.impl.SkijaProtoBackend;
import org.eclipse.swt.visualoracle.json.JsonWriter;
import org.eclipse.swt.visualoracle.result.CaptureEntry;
import org.eclipse.swt.visualoracle.result.CaptureStatus;
import org.eclipse.swt.visualoracle.result.ComparisonEntry;
import org.eclipse.swt.visualoracle.result.RunResult;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.Differ;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.Tolerance;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * The {@code run} verb: renders a filtered specimen set through a reference
 * and a candidate backend in child processes (decision D6), merges their
 * result documents at the JSON level and compares the evidence PNGs with the
 * diff engine.
 *
 * Output contract: stdout carries machine-readable JSON (or text behind
 * {@code --format text}); every human-readable progress line goes to stderr.
 * Exit code 0 means every selected specimen compared within tolerance or was
 * unsupported coverage data; differences and failed captures exit 1; usage
 * errors exit 2. Documented in docs/visual-oracle/CLI.md.
 */
public final class RunVerb {

	/** Scratch and default output root, per the task brief's path rules. */
	public static final String SCRATCH_ROOT = "/tmp/opencode/oracle-T20";
	private static final Path DEFAULT_RUNS_ROOT = Path.of(SCRATCH_ROOT, "runs");

	private static final Set<String> CHILD_BACKENDS = Set.of(NativeBackend.ID, NativeBackend.BASELINE_ID,
			NativeBackend.CANDIDATE_ID, SkijaProtoBackend.ID);
	private static final int DEFAULT_BATCH_SIZE = 25;

	private final PrintStream stdout;
	private final PrintStream stderr;
	private final String[] args;

	public RunVerb(PrintStream stdout, PrintStream stderr, String[] args) {
		this.stdout = stdout;
		this.stderr = stderr;
		this.args = args.clone();
	}

	/** Parses flags, executes the run, returns the process exit code. */
	public int dispatch() {
		try {
			return execute();
		} catch (UsageError e) {
			stderr.println("oracle run: " + e.getMessage());
			stderr.println(usage());
			return 2;
		} catch (BackendUnavailableException e) {
			stderr.println("[run] backend unavailable: " + e.getMessage());
			return 1;
		} catch (Exception e) {
			stderr.println("[run] failed: " + e);
			e.printStackTrace(stderr);
			return 1;
		}
	}

	private static final class UsageError extends RuntimeException {
		UsageError(String message) {
			super(message);
		}
	}

	// ------------------------------------------------------------------ config

	/** Parsed flags; package-visible so RunCheck can drive assembly paths directly. */
	record Config(RunSelection selection, String referenceBackend, String candidateBackend,
			RenderEnv env, Tolerance tolerance, int batchSize, int parallelism, int childTimeoutSeconds,
			Path outDir, boolean listOnly, boolean formatText) {
	}

	private Config parseConfig() {
		Set<String> prefixes = new LinkedHashSet<>();
		Set<String> families = new LinkedHashSet<>();
		Set<String> tags = new LinkedHashSet<>();
		String referenceBackend = NativeBackend.ID;
		String candidateBackend = SkijaProtoBackend.ID;
		int zoom = 100;
		String themeId = "";
		String directionName = Direction.LTR.name();
		String fontFamily = "";
		int fontSize = -1;
		int maxChannelDelta = ClusterDiffer.DEFAULT_TOLERANCE.maxChannelDelta();
		double maxChangedFraction = ClusterDiffer.DEFAULT_TOLERANCE.maxChangedFraction();
		int batchSize = DEFAULT_BATCH_SIZE;
		Integer parallelism = null;
		Integer childTimeoutSeconds = null;
		Path outDir = null;
		boolean listOnly = false;
		boolean formatText = false;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "--prefix" -> prefixes.add(requireValue(arg, ++i));
				case "--family" -> families.add(requireValue(arg, ++i).toLowerCase(Locale.ROOT));
				case "--tag" -> tags.add(requireValue(arg, ++i));
				case "--reference" -> referenceBackend = requireValue(arg, ++i);
				case "--candidate" -> candidateBackend = requireValue(arg, ++i);
				case "--dpi" -> zoom = parseInt(arg, requireValue(arg, ++i));
				case "--theme" -> themeId = requireValue(arg, ++i);
				case "--direction" -> directionName = requireValue(arg, ++i);
				case "--font-family" -> fontFamily = requireValue(arg, ++i);
				case "--font-size" -> fontSize = parseInt(arg, requireValue(arg, ++i));
				case "--max-channel-delta" -> maxChannelDelta = parseInt(arg, requireValue(arg, ++i));
				case "--max-changed-fraction" -> maxChangedFraction = parseFraction(arg, requireValue(arg, ++i));
				case "--batch-size" -> batchSize = parseInt(arg, requireValue(arg, ++i));
				case "--children" -> parallelism = parseInt(arg, requireValue(arg, ++i));
				case "--child-timeout-seconds" -> childTimeoutSeconds = parseInt(arg, requireValue(arg, ++i));
				case "--out" -> outDir = Path.of(requireValue(arg, ++i));
				case "--format" -> {
					String value = requireValue(arg, ++i);
					if ("text".equals(value))
						formatText = true;
					else if (!"json".equals(value))
						throw new UsageError("--format must be json or text, found: " + value);
				}
				case "--list" -> listOnly = true;
				default -> throw new UsageError("unknown option '" + arg + "'");
			}
		}

		if (zoom <= 0)
			throw new UsageError("--dpi must be positive, found: " + zoom);
		if (fontSize == 0)
			throw new UsageError("--font-size must not be zero");
		Direction direction;
		try {
			direction = Direction.valueOf(directionName);
		} catch (IllegalArgumentException e) {
			throw new UsageError("--direction must be LTR or RTL, found: " + directionName);
		}
		if (!CHILD_BACKENDS.contains(referenceBackend))
			throw new UsageError("no child adapter for reference backend '" + referenceBackend
					+ "'; available: " + String.join(", ", CHILD_BACKENDS)
					+ " (skia-canvas arrives with T11)");
		if (!CHILD_BACKENDS.contains(candidateBackend))
			throw new UsageError("no child adapter for candidate backend '" + candidateBackend
					+ "'; available: " + String.join(", ", CHILD_BACKENDS)
					+ " (skia-canvas arrives with T11)");
		if (batchSize < 1)
			throw new UsageError("--batch-size must be >= 1, found: " + batchSize);
		if (parallelism != null && parallelism < 1)
			throw new UsageError("--children must be >= 1, found: " + parallelism);
		if (childTimeoutSeconds != null && childTimeoutSeconds < 1)
			throw new UsageError("--child-timeout-seconds must be >= 1, found: " + childTimeoutSeconds);

		RunSelection selection;
		try {
			selection = new RunSelection(prefixes, families, tags);
		} catch (IllegalArgumentException e) {
			throw new UsageError(e.getMessage());
		}

		Tolerance tolerance;
		try {
			tolerance = new Tolerance(maxChannelDelta, maxChangedFraction);
		} catch (IllegalArgumentException e) {
			throw new UsageError(e.getMessage());
		}
		if (outDir == null)
			outDir = DEFAULT_RUNS_ROOT.resolve(defaultRunName());
		return new Config(selection, referenceBackend, candidateBackend,
				new RenderEnv(zoom, themeId.isEmpty() ? Theme.PLATFORM_DEFAULT : new Theme(themeId),
						direction, fontFamily, fontSize),
				tolerance, batchSize,
				parallelism != null ? parallelism : ChildProcessLauncher.DEFAULT_PARALLELISM,
				childTimeoutSeconds != null ? childTimeoutSeconds : ChildProcessLauncher.DEFAULT_TIMEOUT_SECONDS,
				outDir, listOnly, formatText);
	}

	private String requireValue(String option, int index) {
		if (index >= args.length)
			throw new UsageError("option " + option + " needs a value");
		return args[index];
	}

	private static int parseInt(String option, String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new UsageError("option " + option + " needs an integer, found: " + value);
		}
	}

	private static double parseFraction(String option, String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new UsageError("option " + option + " needs a number between 0 and 1, found: " + value);
		}
	}

	private static String defaultRunName() {
		return "run-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now())
				+ "-" + ProcessHandle.current().pid();
	}

	private static String usage() {
		return """
				usage: oracle run [flags]
				  --prefix ID             select specimens whose id starts with ID (repeatable)
				  --family NAME           select widget family NAME (repeatable)
				  --tag TAG               select specimens tagged TAG (repeatable); no flags = whole catalog
				  --reference BACKEND     oracle side, default native
				  --candidate BACKEND     side under test, default skija-proto
				                          backends: native, skija-proto, native-baseline (SWT from
				                          $ORACLE_BASELINE, default master), native-candidate (SWT from
				                          $ORACLE_CANDIDATE); both take a git ref or an SWT directory
				  --dpi N                 zoom percentage for the environment, default 100
				  --theme ID              GTK theme id, default platform theme
				  --direction LTR|RTL     default LTR
				  --font-family NAME      default system font
				  --font-size N           points if positive, pixels if negative
				  --max-channel-delta N   per-channel pixel tolerance, default 8
				  --max-changed-fraction F  fraction separating WITHIN_TOLERANCE from DIFFERENT, default 0.5
				  --batch-size N          specimens per child process, default 25
				  --children N            concurrent child processes, default 4
				  --child-timeout-seconds N  per-child wall-clock bound, default 240
				  --out DIR               output directory, default /tmp/opencode/oracle-T20/runs/<timestamp>
				  --format json|text      stdout format, default json
				  --list                  print the selection without capturing""";
	}

	// ---------------------------------------------------------------- execution

	private int execute() throws Exception {
		Config config = parseConfig();
		SpecimenCatalog catalog = SpecimenCatalog.discover();
		List<Specimen> selected = config.selection().applyTo(catalog);
		if (selected.isEmpty()) {
			throw new UsageError("the selection matches no specimen of the "
					+ catalog.all().size() + "-specimen catalog");
		}

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("verb", "run");
		report.put("tool", "oracle-harness/" + OracleCli.VERSION);
		report.put("selection", selectionMap(config.selection(), selected.size()));
		report.put("referenceBackend", config.referenceBackend());
		report.put("candidateBackend", config.candidateBackend());

		List<String> ids = selected.stream().map(Specimen::id).toList();
		if (config.listOnly()) {
			report.put("dryRun", Boolean.TRUE);
			report.put("totals", totalsMap(new Totals(selected.size())));
			report.put("specimens", ids);
			emit(report, config);
			return 0;
		}

		stderr.println("[run] selected " + ids.size() + " specimen(s), environment "
				+ describeEnv(config.env()));
		Files.createDirectories(config.outDir());
		stderr.println("[run] output directory " + config.outDir());

		RunOutcome outcome = runComparison(config, ids);
		report.put("resultDocument", config.outDir().resolve("result.json").normalize().toString());
		report.put("environment", environmentMap(config.env()));
		report.put("tolerance", toleranceMap(config.tolerance()));
		report.put("pass", Boolean.valueOf(outcome.totals.pass()));
		report.put("verdict", outcome.totals.pass() ? "PASS" : "FAIL");
		report.put("totals", totalsMap(outcome.totals));
		List<Object> rowMaps = new ArrayList<>();
		for (Row row : outcome.rows)
			rowMaps.add(row.toJson());
		report.put("specimens", rowMaps);
		emit(report, config);
		return outcome.totals.pass() ? 0 : 1;
	}

	private void emit(Map<String, Object> report, Config config) {
		if (config.formatText()) {
			printTextReport(report);
		} else {
			stdout.println(JsonWriter.write(report));
		}
	}

	private void printTextReport(Map<String, Object> report) {
		stdout.println("verdict " + report.get("verdict") + "  (reference="
				+ report.get("referenceBackend") + " candidate=" + report.get("candidateBackend") + ")");
		stdout.println("result   " + report.get("resultDocument"));
		Object totals = report.get("totals");
		if (totals instanceof Map<?, ?> t && !Boolean.TRUE.equals(report.get("dryRun"))) {
			stdout.printf("totals   selected=%s compared=%s equal=%s withinTolerance=%s different=%s"
					+ " unsupported=%s failed=%s%n",
					t.get("selected"), t.get("compared"), t.get("equal"), t.get("withinTolerance"),
					t.get("different"), t.get("unsupported"), t.get("failed"));
		}
		Object specimens = report.get("specimens");
		if (specimens instanceof List<?> rows) {
			for (Object row : rows) {
				Map<?, ?> r = (Map<?, ?>) row;
				if (r.containsKey("verdict"))
					stdout.printf("%-40s %-17s %8s px %6.2f%% maxDelta=%s%n",
							r.get("specimen"), r.get("state"), r.get("changedPixels"),
							toFraction(r.get("changedFraction")) * 100.0, r.get("maxChannelDelta"));
				else
					stdout.printf("%-40s %-17s %s%n", r.get("specimen"), r.get("state"),
							String.valueOf(r.get("message")));
			}
		}
	}

	private static double toFraction(Object value) {
		return value instanceof Number n ? n.doubleValue() : 0.0;
	}

	// ------------------------------------------------------------ child driving

	/**
	 * Runs both backends' children and turns their documents into a verdict.
	 */
	private RunOutcome runComparison(Config config, List<String> ids) {
		List<List<String>> chunks = partition(ids, config.batchSize());
		List<ChildProcessLauncher.ChildRequest> requests = new ArrayList<>();
		for (List<String> chunk : chunks) {
			requests.add(new ChildProcessLauncher.ChildRequest(config.referenceBackend(), config.env(),
					chunk, CaptureRuntime.Strategy.COPY_AREA, false));
			requests.add(new ChildProcessLauncher.ChildRequest(config.candidateBackend(), config.env(),
					chunk, CaptureRuntime.Strategy.COPY_AREA, false));
		}
		stderr.println("[run] launching " + requests.size() + " child process(es), at most "
				+ config.parallelism() + " concurrent, timeout " + config.childTimeoutSeconds() + " s each");

		ChildProcessLauncher.Config launcherConfig = new ChildProcessLauncher.Config(
				config.parallelism(), config.childTimeoutSeconds(), Path.of(SCRATCH_ROOT, "children"));
		long startNanos = System.nanoTime();
		List<ChildProcessLauncher.ChildOutcome> outcomes =
				new ChildProcessLauncher(launcherConfig).run(requests);
		for (int i = 0; i < outcomes.size(); i++) {
			ChildProcessLauncher.ChildOutcome outcome = outcomes.get(i);
			stderr.println("[run] child for " + requests.get(i).backendId() + ": "
					+ (outcome.succeeded() ? "ok" : "FAILED") + " after "
					+ outcome.wallMillis() / 1000.0 + " s"
					+ (outcome.succeeded() ? "" : ", reason: " + outcome.failureReason()));
		}
		stderr.println("[run] children finished in "
				+ (System.nanoTime() - startNanos) / 1_000_000_000.0 + " s");

		return assembleFromChildren(outcomes, requests, ids, config);
	}

	/**
	 * Builds the verdict and the merged result document from what the
	 * children produced. Package-visible so the selftest can feed fabricated
	 * child documents through the exact production path, including a
	 * deliberately broken candidate side.
	 */
	static RunOutcome assembleFromChildren(List<ChildProcessLauncher.ChildOutcome> outcomes,
			List<ChildProcessLauncher.ChildRequest> requests, List<String> ids, Config config) {
		Differ differ = new ClusterDiffer();
		boolean referenceRan = anyChildSucceeded(outcomes, requests, config.referenceBackend());
		boolean candidateRan = anyChildSucceeded(outcomes, requests, config.candidateBackend());

		List<ResultMerger.MergeInput> mergeInputs = new ArrayList<>();
		RenderEnv actualEnv = null;
		for (int i = 0; i < outcomes.size(); i++) {
			ChildProcessLauncher.ChildOutcome outcome = outcomes.get(i);
			if (!outcome.succeeded())
				continue;
			mergeInputs.add(new ResultMerger.MergeInput(outcome.resultDocument(), outcome.directory()));
			if (actualEnv == null)
				actualEnv = RunResult.fromJsonMap(outcome.resultDocument()).environment();
		}

		// Successful children merge through impl.ResultMerger, which validates,
		// canonicalises order and rebases image paths to the out directory.
		Map<String, CaptureEntry> mergedCaptures = new LinkedHashMap<>();
		String generator = "oracle-harness/" + OracleCli.VERSION + "/run";
		if (!mergeInputs.isEmpty()) {
			RunResult merged = RunResult.fromJsonMap(
					ResultMerger.merge(mergeInputs, generator, config.outDir()));
			for (CaptureEntry entry : merged.captures())
				mergedCaptures.put(key(entry.specimen(), entry.backend()), entry);
		}

		List<CaptureEntry> documentCaptures = new ArrayList<>(mergedCaptures.values());
		documentCaptures.addAll(synthesizedFailures(ids, config.referenceBackend(),
				referenceRan, outcomes, requests));
		documentCaptures.addAll(synthesizedFailures(ids, config.candidateBackend(),
				candidateRan, outcomes, requests));

		List<ComparisonEntry> comparisons = new ArrayList<>();
		List<Row> rows = new ArrayList<>();
		Totals totals = new Totals(ids.size());
		for (String id : ids) {
			CaptureEntry reference = mergedCaptures.get(key(id, config.referenceBackend()));
			CaptureEntry candidate = mergedCaptures.get(key(id, config.candidateBackend()));
			if (reference == null && !referenceRan)
				reference = CaptureEntry.skipped(id, config.referenceBackend(), CaptureStatus.FAILED,
						childFailureReason(outcomes, requests, config.referenceBackend()));
			if (candidate == null && !candidateRan)
				candidate = CaptureEntry.skipped(id, config.candidateBackend(), CaptureStatus.FAILED,
						childFailureReason(outcomes, requests, config.candidateBackend()));

			if (reference != null && candidate != null
					&& reference.status() == CaptureStatus.CAPTURED
					&& candidate.status() == CaptureStatus.CAPTURED) {
				ComparisonEntry comparison = comparePair(differ, id, config, reference, candidate);
				comparisons.add(comparison);
				rows.add(Row.compared(comparison));
				totals.countVerdict(comparison.verdict());
			} else {
				CaptureEntry entry = nonCapturedOf(reference, candidate);
				if (entry == null || entry.status() == CaptureStatus.CAPTURED)
					entry = CaptureEntry.skipped(id,
							reference != null ? reference.backend() : config.referenceBackend(),
							CaptureStatus.FAILED,
							"no comparable pair: reference="
									+ statusName(reference) + ", candidate=" + statusName(candidate));
				rows.add(Row.notCaptured(entry));
				totals.countStatus(entry.status());
			}
		}
		comparisons.sort(Comparator.comparing(ComparisonEntry::specimen)
				.thenComparing(ComparisonEntry::referenceBackend)
				.thenComparing(ComparisonEntry::candidateBackend));
		documentCaptures.sort(Comparator.comparing(CaptureEntry::specimen)
				.thenComparing(CaptureEntry::backend));

		writeResultDocument(config.outDir(), generator, actualEnv != null ? actualEnv : config.env(),
				documentCaptures, comparisons);
		return new RunOutcome(rows, totals);
	}

	private static String key(String specimen, String backend) {
		return specimen + "\u0000" + backend;
	}

	private static boolean anyChildSucceeded(List<ChildProcessLauncher.ChildOutcome> outcomes,
			List<ChildProcessLauncher.ChildRequest> requests, String backend) {
		for (int i = 0; i < outcomes.size(); i++)
			if (requests.get(i).backendId().equals(backend) && outcomes.get(i).succeeded())
				return true;
		return false;
	}

	private static String childFailureReason(List<ChildProcessLauncher.ChildOutcome> outcomes,
			List<ChildProcessLauncher.ChildRequest> requests, String backend) {
		for (int i = 0; i < outcomes.size(); i++)
			if (requests.get(i).backendId().equals(backend) && !outcomes.get(i).succeeded())
				return "backend " + backend + " child failed: " + outcomes.get(i).failureReason();
		return "backend " + backend + " produced no result document";
	}

	/**
	 * When one backend's children all failed, its specimens never reached the
	 * merge; they enter the document as FAILED data so consumers see why.
	 */
	private static List<CaptureEntry> synthesizedFailures(List<String> ids, String backend,
			boolean anySucceeded, List<ChildProcessLauncher.ChildOutcome> outcomes,
			List<ChildProcessLauncher.ChildRequest> requests) {
		if (anySucceeded)
			return List.of();
		String reason = childFailureReason(outcomes, requests, backend);
		List<CaptureEntry> entries = new ArrayList<>();
		for (String id : ids)
			entries.add(CaptureEntry.skipped(id, backend, CaptureStatus.FAILED, reason));
		return entries;
	}

	private static CaptureEntry nonCapturedOf(CaptureEntry a, CaptureEntry b) {
		CaptureEntry[] entries = { a, b };
		for (CaptureEntry entry : entries)
			if (entry != null && entry.status() == CaptureStatus.UNSUPPORTED)
				return entry;
		for (CaptureEntry entry : entries)
			if (entry != null && entry.status() == CaptureStatus.FAILED)
				return entry;
		return a != null ? a : b;
	}

	private static String statusName(CaptureEntry entry) {
		return entry != null ? entry.status().name() : "MISSING";
	}

	private static ComparisonEntry comparePair(Differ differ, String id, Config config,
			CaptureEntry reference, CaptureEntry candidate) {
		Path referencePng = config.outDir().resolve(reference.image()).normalize();
		Path candidatePng = config.outDir().resolve(candidate.image()).normalize();
		try {
			CapturedImage referenceImage = new BasicCapturedImage(new ImageData(referencePng.toString()));
			CapturedImage candidateImage = new BasicCapturedImage(new ImageData(candidatePng.toString()));
			var diff = differ.compare(referenceImage, candidateImage, config.tolerance());
			return new ComparisonEntry(id, reference.backend(), candidate.backend(),
					reference.image(), candidate.image(), diff.verdict(), diff.changedPixels(),
					diff.changedFraction(), diff.maxChannelDelta(), diff.probableClass(),
					diff.clusters());
		} catch (RuntimeException e) {
			return new ComparisonEntry(id, reference.backend(), candidate.backend(),
					reference.image(), candidate.image(), Verdict.DIFFERENT, 0, 1.0, 255,
					DefectClass.UNKNOWN, List.of());
		}
	}

	private static void writeResultDocument(Path outDir, String generator, RenderEnv env,
			List<CaptureEntry> captures, List<ComparisonEntry> comparisons) {
		RunResult document = new RunResult(RunResult.CURRENT_SCHEMA_VERSION, generator, env,
				captures, comparisons);
		Path file = outDir.resolve("result.json");
		try {
			Files.createDirectories(outDir);
			Files.writeString(file, document.toJson(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("cannot write result document " + file, e);
		}
	}

	// -------------------------------------------------------------- result types

	static final class Totals {
		final int selected;
		int equal;
		int withinTolerance;
		int different;
		int unsupported;
		int failed;

		Totals(int selected) {
			this.selected = selected;
		}

		void countVerdict(Verdict verdict) {
			switch (verdict) {
				case EQUAL -> equal++;
				case WITHIN_TOLERANCE -> withinTolerance++;
				case DIFFERENT -> different++;
			}
		}

		void countStatus(CaptureStatus status) {
			if (status == CaptureStatus.UNSUPPORTED)
				unsupported++;
			else
				failed++;
		}

		int compared() {
			return equal + withinTolerance + different;
		}

		/** Unsupported is coverage data and never blocks; failures and differences do. */
		boolean pass() {
			return different == 0 && failed == 0;
		}
	}

	record RunOutcome(List<Row> rows, Totals totals) {
	}

	/**
	 * One line of the stdout report: either a comparison result or the
	 * reason a specimen could not be compared.
	 */
	record Row(String specimen, String state, Long changedPixels, Double changedFraction,
			Integer maxChannelDelta, String probableDefectClass, Integer clusters,
			String backend, String message) {

		static Row compared(ComparisonEntry entry) {
			return new Row(entry.specimen(), entry.verdict().name(),
					Long.valueOf(entry.changedPixels()), Double.valueOf(entry.changedFraction()),
					Integer.valueOf(entry.maxChannelDelta()), entry.probableDefectClass().name(),
					Integer.valueOf(entry.clusters().size()), null, null);
		}

		static Row notCaptured(CaptureEntry entry) {
			return new Row(entry.specimen(), entry.status().name(), null, null, null, null, null,
					entry.backend(), entry.message());
		}

		Map<String, Object> toJson() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("specimen", specimen);
			map.put("state", state);
			if (changedPixels != null) {
				map.put("changedPixels", changedPixels);
				map.put("changedFraction", changedFraction);
				map.put("maxChannelDelta", maxChannelDelta);
				map.put("probableDefectClass", probableDefectClass);
				map.put("clusters", clusters);
			} else {
				map.put("backend", backend);
				if (message != null)
					map.put("message", message);
			}
			return map;
		}
	}

	// ------------------------------------------------------------------- helpers

	private static Map<String, Object> selectionMap(RunSelection selection, int matched) {
		Map<String, Object> criteria = new LinkedHashMap<>();
		criteria.put("prefixes", new ArrayList<>(selection.prefixes()));
		criteria.put("families", new ArrayList<>(selection.families()));
		criteria.put("tags", new ArrayList<>(selection.tags()));
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("criteria", criteria);
		map.put("matched", Integer.valueOf(matched));
		return map;
	}

	private static Map<String, Object> environmentMap(RenderEnv env) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("zoomPercent", Integer.valueOf(env.zoomPercent()));
		map.put("theme", env.theme().id());
		map.put("direction", env.direction().name());
		map.put("fontFamily", env.fontFamily());
		map.put("fontSize", Integer.valueOf(env.fontSize()));
		return map;
	}

	private static Map<String, Object> toleranceMap(Tolerance tolerance) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("maxChannelDelta", Integer.valueOf(tolerance.maxChannelDelta()));
		map.put("maxChangedFraction", Double.valueOf(tolerance.maxChangedFraction()));
		return map;
	}

	private static Map<String, Object> totalsMap(Totals totals) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("selected", Integer.valueOf(totals.selected));
		map.put("compared", Integer.valueOf(totals.compared()));
		map.put("equal", Integer.valueOf(totals.equal));
		map.put("withinTolerance", Integer.valueOf(totals.withinTolerance));
		map.put("different", Integer.valueOf(totals.different));
		map.put("unsupported", Integer.valueOf(totals.unsupported));
		map.put("failed", Integer.valueOf(totals.failed));
		return map;
	}

	private static String describeEnv(RenderEnv env) {
		return "zoom=" + env.zoomPercent()
				+ " theme=" + (env.theme().isPlatformDefault() ? "<default>" : env.theme().id())
				+ " direction=" + env.direction()
				+ " font=" + (env.usesSystemFont() ? "<system>" : env.fontFamily() + " " + env.fontSize());
	}

	private static List<List<String>> partition(List<String> ids, int size) {
		List<List<String>> chunks = new ArrayList<>();
		for (int i = 0; i < ids.size(); i += size)
			chunks.add(ids.subList(i, Math.min(ids.size(), i + size)));
		return chunks;
	}
}
