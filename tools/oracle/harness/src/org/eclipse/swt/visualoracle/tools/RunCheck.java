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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.visualoracle.impl.BasicCapturedImage;
import org.eclipse.swt.visualoracle.impl.ChildProcessLauncher;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime.Strategy;
import org.eclipse.swt.visualoracle.json.JsonParser;
import org.eclipse.swt.visualoracle.json.ResultSchemaValidator;
import org.eclipse.swt.visualoracle.result.CaptureEntry;
import org.eclipse.swt.visualoracle.result.CaptureStatus;
import org.eclipse.swt.visualoracle.result.ComparisonEntry;
import org.eclipse.swt.visualoracle.result.RunResult;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.DiffCluster;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.Tolerance;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * Selftest checks for the T20 CLI verbs: prove that filters select what they
 * claim, that a clean run exits zero, that a deliberately broken candidate
 * exits non-zero through the production assembly path, that written result
 * documents validate against the frozen schema, and that triage output is
 * deterministic for identical input and independent of input row order.
 */
public final class RunCheck {

	private static final String REFERENCE_SPECIMEN = "button.push.default";
	private static final RenderEnv ENV = new RenderEnv(100, Theme.PLATFORM_DEFAULT,
			Direction.LTR, "", -1);

	private RunCheck() {
	}

	// ------------------------------------------------------------- filters

	/** Pure filter proofs plus an end-to-end --list dispatch. */
	static void checkFiltersSelectWhatTheyClaim(PrintStream out) throws Exception {
		SpecimenCatalog catalog = SpecimenCatalog.discover();
		int catalogSize = catalog.all().size();
		require(catalogSize >= 100, "catalog unexpectedly small: " + catalogSize);

		RunSelection buttonFamily = new RunSelection(Set.of(), Set.of("button"), Set.of());
		List<String> expected = sortedIds(catalog.family("button"));
		require(expected.size() == 23, "expected 23 button specimens, found " + expected.size());
		require(sortedIds(buttonFamily.applyTo(catalog)).equals(expected),
				"--family button did not select exactly the button family");

		RunSelection pushPrefix = new RunSelection(Set.of("button.push."), Set.of(), Set.of());
		List<String> pushed = sortedIds(pushPrefix.applyTo(catalog));
		require(!pushed.isEmpty() && pushed.size() < expected.size(),
				"--prefix button.push. selected " + pushed.size() + ", expected a strict subset");
		for (String id : pushed)
			require(id.startsWith("button.push."), "prefix filter emitted " + id);

		RunSelection exactId = new RunSelection(Set.of("label.default"), Set.of(), Set.of());
		require(sortedIds(exactId.applyTo(catalog)).equals(List.of("label.default")),
				"an exact specimen id as prefix must select exactly that specimen");

		RunSelection focusedButtons = new RunSelection(Set.of("button."), Set.of(),
				Set.of("FOCUS_SENSITIVE"));
		RunSelection focusedAll = new RunSelection(Set.of(), Set.of(), Set.of("FOCUS_SENSITIVE"));
		List<String> focusedButtonIds = sortedIds(focusedButtons.applyTo(catalog));
		for (String id : focusedButtonIds) {
			require(id.startsWith("button."), "AND intersection leaked " + id);
			catalog.byId(id).ifPresent(s ->
					require(s.tags().contains(org.eclipse.swt.visualoracle.spi.Tag.FOCUS_SENSITIVE),
							id + " selected by --tag FOCUS_SENSITIVE but does not carry it"));
		}
		require(focusedButtonIds.size() <= sortedIds(focusedAll.applyTo(catalog)).size(),
				"tag intersection larger than the tag alone");

		require(new RunSelection(Set.of(), Set.of("no.such.family"), Set.of())
				.applyTo(catalog).isEmpty(), "unknown family selected something");
		try {
			new RunSelection(Set.of(), Set.of(), Set.of("NOT_A_TAG"));
			throw new AssertionError("unknown tag accepted");
		} catch (IllegalArgumentException e) {
			require(e.getMessage().contains("NOT_A_TAG"), "tag error does not name the tag");
		}

		ByteArrayOutputStream json = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int exit = new OracleCli(new PrintStream(json, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8))
				.dispatch(new String[] { "run", "--list", "--family", "link" });
		require(exit == 0, "--list dispatch exited " + exit + ": " + err);
		Map<?, ?> report = (Map<?, ?>) JsonParser.parse(json.toString(StandardCharsets.UTF_8));
		require(Boolean.TRUE.equals(report.get("dryRun")), "--list did not mark the report dryRun");
		require(Integer.valueOf(4).equals(((Number) ((Map<?, ?>) report.get("totals")).get("selected"))
				.intValue()), "--list --family link did not select 4 specimens");
		out.println("      filters select claimed subsets; --list reported "
				+ ((Map<?, ?>) report.get("totals")).get("selected") + " link specimens, exit 0");
	}

	// ------------------------------------------------------------ clean run

	/**
	 * Full verb round trip: two real children over one specimen, same backend
	 * on both sides, so every pixel must agree and the exit code must be zero.
	 */
	static Path checkCleanRunExitsZeroAndValidates(PrintStream out) throws Exception {
		Path outDir = Path.of(RunVerb.SCRATCH_ROOT,
				"selftest-clean-" + Long.toString(System.currentTimeMillis(), 36));
		ByteArrayOutputStream json = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int exit = new OracleCli(new PrintStream(json, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8))
				.dispatch(new String[] { "run",
						"--prefix", REFERENCE_SPECIMEN,
						"--reference", "native", "--candidate", "native",
						"--out", outDir.toString() });
		if (exit != 0)
			throw new AssertionError("clean native-vs-native run exited " + exit
					+ "; stderr tail:\n" + tail(err.toString()));

		Map<?, ?> report = (Map<?, ?>) JsonParser.parse(json.toString(StandardCharsets.UTF_8));
		require(Boolean.TRUE.equals(report.get("pass")), "clean run reported pass=false");
		require("PASS".equals(report.get("verdict")), "clean run verdict not PASS");
		Map<?, ?> totals = (Map<?, ?>) report.get("totals");
		require(intOf(totals.get("selected")) == 1 && intOf(totals.get("compared")) == 1,
				"clean run compared " + totals.get("compared") + " of " + totals.get("selected"));
		require(intOf(totals.get("equal")) == 1, "native-vs-native was not EQUAL: " + totals);
		require(intOf(totals.get("different")) == 0 && intOf(totals.get("failed")) == 0,
				"clean run recorded differences or failures: " + totals);
		requireSet(report.keySet(), Set.of("verb", "tool", "selection", "referenceBackend",
				"candidateBackend", "resultDocument", "environment", "tolerance", "pass",
				"verdict", "totals", "specimens"), "run report");

		String documentPath = String.valueOf(report.get("resultDocument"));
		Object document = JsonParser.parse(Files.readString(Path.of(documentPath),
				StandardCharsets.UTF_8));
		List<String> errors = ResultSchemaValidator.validate(document);
		require(errors.isEmpty(), "run's own result document rejected by the frozen schema: "
				+ String.join("; ", errors));

		List<?> rows = (List<?>) report.get("specimens");
		require(rows.size() == 1 && "EQUAL".equals(((Map<?, ?>) rows.get(0)).get("state")),
				"per-specimen row lost the verdict: " + rows);
		out.println("      clean run exited 0, PASS, result document schema-valid at " + documentPath);
		return Path.of(documentPath);
	}

	// ------------------------------------------------------ broken candidate

	/**
	 * A deliberately broken candidate side, fed through the production
	 * assembly path as fabricated child documents: same specimen, but the
	 * candidate evidence is an all-blue image, so the verdict must be
	 * DIFFERENT and the run must fail.
	 */
	static void checkBrokenCandidateExitsNonZero(Path cleanRunDocument, PrintStream out)
			throws Exception {
		ImageData reference = new ImageData(resolveAgainst(cleanRunDocument).toString());
		PaletteData rgb = new PaletteData(0xFF0000, 0xFF00, 0xFF);
		ImageData blue = new ImageData(reference.width, reference.height, 24, rgb);
		for (int y = 0; y < blue.height; y++)
			for (int x = 0; x < blue.width; x++)
				blue.setPixel(x, y, 0x0000FF);

		Path scratch = Files.createDirectories(Path.of(RunVerb.SCRATCH_ROOT,
				"selftest-broken-" + Long.toString(System.currentTimeMillis(), 36)));
		Path refDir = Files.createDirectories(scratch.resolve("ref-child"));
		Path candDir = Files.createDirectories(scratch.resolve("broken-child"));
		writePng(refDir.resolve("images").resolve("evidence.png"), reference);
		writePng(candDir.resolve("images").resolve("evidence.png"), blue);

		ChildProcessLauncher.ChildRequest refRequest = new ChildProcessLauncher.ChildRequest(
				"native", ENV, List.of(REFERENCE_SPECIMEN), Strategy.COPY_AREA, false);
		ChildProcessLauncher.ChildRequest candRequest = new ChildProcessLauncher.ChildRequest(
				"broken-test", ENV, List.of(REFERENCE_SPECIMEN), Strategy.COPY_AREA, false);
		List<ChildProcessLauncher.ChildOutcome> outcomes = List.of(
				childOutcome(refRequest, refDir, "selftest/broken-fixture/reference"),
				childOutcome(candRequest, candDir, "selftest/broken-fixture/candidate"));

		RunVerb.Config config = new RunVerb.Config(
				new RunSelection(Set.of(REFERENCE_SPECIMEN), Set.of(), Set.of()),
				"native", "broken-test", ENV, new Tolerance(8, 0.5),
				25, 4, 240, scratch.resolve("out"), false, true);
		RunVerb.RunOutcome outcome = RunVerb.assembleFromChildren(outcomes,
				List.of(refRequest, candRequest), List.of(REFERENCE_SPECIMEN), config);

		require(!outcome.totals().pass(), "a deliberately broken candidate passed the run");
		require(outcome.totals().different == 1, "broken candidate produced "
				+ outcome.totals().different + " differences, expected exactly 1");
		require("DIFFERENT".equals(outcome.rows().get(0).state()),
				"row state is " + outcome.rows().get(0).state());

		Path documentPath = scratch.resolve("out").resolve("result.json");
		Object document = JsonParser.parse(Files.readString(documentPath, StandardCharsets.UTF_8));
		List<String> errors = ResultSchemaValidator.validate(document);
		require(errors.isEmpty(), "broken-run result document rejected: " + String.join("; ", errors));
		List<?> comparisons = (List<?>) ((Map<?, ?>) document).get("comparisons");
		require(String.valueOf(((Map<?, ?>) comparisons.get(0)).get("verdict")).equals("DIFFERENT"),
				"written comparison lost the DIFFERENT verdict");

		ByteArrayOutputStream json = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int triageExit = new OracleCli(new PrintStream(json, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8))
				.dispatch(new String[] { "triage", "--from", documentPath.toString() });
		require(triageExit == 0, "triage over the broken run exited " + triageExit);
		Map<?, ?> report = (Map<?, ?>) JsonParser.parse(json.toString(StandardCharsets.UTF_8));
		List<?> groups = (List<?>) report.get("groups");
		require(groups.size() == 1, "one differing specimen produced " + groups.size() + " groups");
		Map<?, ?> group = (Map<?, ?>) groups.get(0);
		require(intOf(group.get("affected")) == 1, "group affected count wrong");
		require(String.valueOf(group.get("hypothesis")).startsWith("isolated difference"),
				"hypothesis text changed shape: " + group.get("hypothesis"));
		out.println("      broken candidate: run FAIL, verdict DIFFERENT ("
				+ ((Map<?, ?>) ((List<?>) group.get("specimens")).get(0)).get("changedPixels")
				+ " px), document schema-valid, triage ranks 1 isolated defect");
	}

	private static ChildProcessLauncher.ChildOutcome childOutcome(
			ChildProcessLauncher.ChildRequest request, Path directory, String generator) {
		CaptureEntry captured = CaptureEntry.captured(REFERENCE_SPECIMEN, request.backendId(),
				new BasicCapturedImage(new ImageData(
						directory.resolve("images").resolve("evidence.png").toString())),
				"images/evidence.png");
		@SuppressWarnings("unchecked")
		Map<String, Object> document = (Map<String, Object>) JsonParser.parse(new RunResult(
				RunResult.CURRENT_SCHEMA_VERSION, generator, ENV, List.of(captured), List.of())
				.toJson());
		return new ChildProcessLauncher.ChildOutcome(request, directory, Integer.valueOf(0),
				false, null, document, "", 1);
	}

	private static void writePng(Path file, ImageData data) throws Exception {
		Files.createDirectories(file.getParent());
		Files.write(file, new BasicCapturedImage(data).pngBytes());
	}

	private static Path resolveAgainst(Path document) throws Exception {
		Map<?, ?> doc = (Map<?, ?>) JsonParser.parse(
				Files.readString(document, StandardCharsets.UTF_8));
		Path base = document.getParent();
		for (Object entry : (List<?>) doc.get("captures")) {
			Map<?, ?> capture = (Map<?, ?>) entry;
			if (REFERENCE_SPECIMEN.equals(capture.get("specimen"))
					&& "native".equals(capture.get("backend")))
				return base.resolve(String.valueOf(capture.get("image"))).normalize();
		}
		throw new AssertionError("no native capture of " + REFERENCE_SPECIMEN + " in " + document);
	}

	// -------------------------------------------------------------- triage

	/** Determinism for identical input, order independence, --top, ranking rules. */
	static void checkTriageDeterministic(PrintStream out) throws Exception {
		List<CaptureEntry> captures = new ArrayList<>();
		captures.add(CaptureEntry.skipped("combo.readonly.empty", "skija-proto",
				CaptureStatus.UNSUPPORTED, "backend does not support this specimen"));
		captures.add(CaptureEntry.skipped("scrollbar.horizontal.scrolled", "native",
				CaptureStatus.FAILED, "child failed: settle timeout"));

		List<ComparisonEntry> comparisons = new ArrayList<>();
		comparisons.add(different("text.single.content", DefectClass.WRONG_GLYPH, 50, 0.05));
		comparisons.add(different("progressbar.vertical", DefectClass.MISSING_ELEMENT, 500, 0.80));
		comparisons.add(equal("label.default"));
		comparisons.add(different("button.push.default", DefectClass.UNKNOWN, 300, 0.30));
		comparisons.add(different("progressbar.horizontal", DefectClass.MISSING_ELEMENT, 900, 0.90));

		Path dir = Files.createDirectories(Path.of(RunVerb.SCRATCH_ROOT,
				"selftest-triage-" + Long.toString(System.currentTimeMillis(), 36)));
		Path original = writeDocument(dir.resolve("result.json"),
				new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "selftest/triage-fixture", ENV,
						captures, comparisons));

		ByteArrayOutputStream first = new ByteArrayOutputStream();
		ByteArrayOutputStream second = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int exit1 = new OracleCli(new PrintStream(first, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8))
				.dispatch(new String[] { "triage", "--from", original.toString() });
		int exit2 = new OracleCli(new PrintStream(second, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8))
				.dispatch(new String[] { "triage", "--from", original.toString() });
		require(exit1 == 0 && exit2 == 0, "triage exited " + exit1 + "/" + exit2);
		require(first.toString(StandardCharsets.UTF_8)
				.equals(second.toString(StandardCharsets.UTF_8)),
				"triage stdout differs between two runs over identical input");

		// Order independence: same entries, shuffled input rows, identical report.
		List<CaptureEntry> shuffledCaptures = new ArrayList<>(captures);
		java.util.Collections.reverse(shuffledCaptures);
		List<ComparisonEntry> shuffledComparisons = new ArrayList<>(comparisons);
		java.util.Collections.reverse(shuffledComparisons);
		Path reordered = writeDocument(dir.resolve("reordered-result.json"),
				new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "selftest/triage-fixture", ENV,
						shuffledCaptures, shuffledComparisons));
		Map<?, ?> parsedOriginal = (Map<?, ?>) JsonParser.parse(
				first.toString(StandardCharsets.UTF_8));
		Map<Object, Object> withoutSourceOriginal = withoutKey(parsedOriginal, "source");
		ByteArrayOutputStream third = new ByteArrayOutputStream();
		int exit3 = new OracleCli(new PrintStream(third, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8))
				.dispatch(new String[] { "triage", "--from", reordered.toString() });
		require(exit3 == 0, "triage over reordered input exited " + exit3);
		Map<Object, Object> withoutSourceReordered = withoutKey(
				(Map<?, ?>) JsonParser.parse(third.toString(StandardCharsets.UTF_8)), "source");
		require(withoutSourceOriginal.equals(withoutSourceReordered),
				"reordering the input rows changed the triage report:\n"
						+ org.eclipse.swt.visualoracle.json.JsonWriter.write(withoutSourceOriginal)
						+ "\nvs\n"
						+ org.eclipse.swt.visualoracle.json.JsonWriter.write(withoutSourceReordered));

		// Ranking rules: biggest affected group first, severity breaks remaining ties.
		List<?> groups = (List<?>) parsedOriginal.get("groups");
		require(groups.size() == 3, "expected 3 defect groups, got " + groups.size());
		Map<?, ?> firstGroup = (Map<?, ?>) groups.get(0);
		require("progressbar".equals(firstGroup.get("family"))
				&& "MISSING_ELEMENT".equals(firstGroup.get("defectClass"))
				&& intOf(firstGroup.get("affected")) == 2,
				"the two-specimen group must rank first, got " + firstGroup);
		List<?> members = (List<?>) firstGroup.get("specimens");
		require("progressbar.horizontal".equals(((Map<?, ?>) members.get(0)).get("specimen"))
				&& "progressbar.vertical".equals(((Map<?, ?>) members.get(1)).get("specimen")),
				"group members must be ordered by severity: " + members);
		require("button".equals(((Map<?, ?>) groups.get(1)).get("family")),
				"severity must rank the button group above the text group");
		require("text".equals(((Map<?, ?>) groups.get(2)).get("family")),
				"text group expected last");

		// --top trims the report.
		Map<String, Object> trimmed = TriageVerb.rank(readDocument(original), original, Integer.valueOf(1));
		require(((List<?>) trimmed.get("groups")).size() == 1, "--top 1 did not trim to 1 group");

		out.println("      triage: byte-identical for identical input, order-independent,"
				+ " ranks 3 groups correctly (2-specimen group first)");
	}

	private static RunResult readDocument(Path file) throws Exception {
		return RunResult.fromJson(Files.readString(file, StandardCharsets.UTF_8));
	}

	private static Path writeDocument(Path file, RunResult document) throws Exception {
		Files.writeString(file, document.toJson(), StandardCharsets.UTF_8);
		return file;
	}

	private static ComparisonEntry different(String specimen, DefectClass defectClass,
			long changedPixels, double fraction) {
		return new ComparisonEntry(specimen, "native", "skija-proto",
				"images/" + specimen + "-native.png", "images/" + specimen + "-candidate.png",
				Verdict.DIFFERENT, changedPixels, fraction, 200, defectClass,
				List.of(new DiffCluster(0, 0, 10, 10, changedPixels)));
	}

	private static ComparisonEntry equal(String specimen) {
		return new ComparisonEntry(specimen, "native", "skija-proto",
				"images/" + specimen + "-native.png", "images/" + specimen + "-candidate.png",
				Verdict.EQUAL, 0, 0.0, 0, DefectClass.NONE, List.of());
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> withoutKey(Map<?, ?> map, String key) {
		Map<Object, Object> copy = new LinkedHashMap<>((Map<Object, Object>) map);
		copy.remove(key);
		return copy;
	}

	// -------------------------------------------------------------- helpers

	private static List<String> sortedIds(List<org.eclipse.swt.visualoracle.spi.Specimen> specimens) {
		List<String> ids = new ArrayList<>();
		for (org.eclipse.swt.visualoracle.spi.Specimen specimen : specimens)
			ids.add(specimen.id());
		java.util.Collections.sort(ids);
		return ids;
	}

	private static int intOf(Object value) {
		return ((Number) value).intValue();
	}

	private static void requireSet(Set<?> actual, Set<String> expected, String what) {
		Set<String> names = new java.util.LinkedHashSet<>();
		for (Object item : actual)
			names.add(String.valueOf(item));
		require(names.equals(expected), what + " fields drifted: " + names);
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static String tail(String text) {
		String[] lines = text.split("\n");
		return String.join("\n", java.util.Arrays.copyOfRange(lines,
				Math.max(0, lines.length - 10), lines.length));
	}
}
