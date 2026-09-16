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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.swt.visualoracle.json.JsonWriter;
import org.eclipse.swt.visualoracle.result.CaptureEntry;
import org.eclipse.swt.visualoracle.result.CaptureStatus;
import org.eclipse.swt.visualoracle.result.ComparisonEntry;
import org.eclipse.swt.visualoracle.result.RunResult;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * The {@code triage} verb: ranks the differences of a previous run so a
 * person or agent looks at the highest-value thing first.
 *
 * Ranking optimises for investigations per defect fixed, not for raw pixel
 * counts: comparisons are grouped by widget family and probable defect class,
 * and a family whose every specimen differs is reported as one shared defect
 * ahead of forty distinct single-specimen differences of the same size.
 * Groups are ordered by affected specimen count, then by how completely the
 * group covers its family, then by severity; ties break on names, so the
 * output is deterministic for identical input. Documented in
 * docs/visual-oracle/CLI.md.
 */
public final class TriageVerb {

	private static final Path DEFAULT_RUNS_ROOT = Path.of(RunVerb.SCRATCH_ROOT, "runs");

	private final PrintStream stdout;
	private final PrintStream stderr;
	private final String[] args;

	public TriageVerb(PrintStream stdout, PrintStream stderr, String[] args) {
		this.stdout = stdout;
		this.stderr = stderr;
		this.args = args.clone();
	}

	/** Parses flags, ranks the source document, returns the process exit code. */
	public int dispatch() {
		try {
			return execute();
		} catch (UsageError e) {
			stderr.println("oracle triage: " + e.getMessage());
			stderr.println(usage());
			return 2;
		} catch (IllegalArgumentException | IOException e) {
			stderr.println("[triage] cannot read run result: " + e.getMessage());
			return 1;
		}
	}

	private static final class UsageError extends RuntimeException {
		UsageError(String message) {
			super(message);
		}
	}

	private record Config(Path source, Integer top, boolean formatText) {
	}

	private Config parseConfig() {
		Path source = null;
		Integer top = null;
		boolean formatText = false;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "--from" -> {
					String value = requireValue(arg, ++i);
					source = Path.of(value);
					if (!Files.isRegularFile(source) && !Files.isDirectory(source))
						throw new UsageError("--from points at no file or directory: " + value);
				}
				case "--top" -> {
					int value;
					try {
						value = Integer.parseInt(requireValue(arg, ++i));
					} catch (NumberFormatException e) {
						throw new UsageError("--top needs an integer, found: " + args[i]);
					}
					if (value < 0)
						throw new UsageError("--top must be >= 0, found: " + value);
					top = Integer.valueOf(value);
				}
				case "--format" -> {
					String value = requireValue(arg, ++i);
					if ("text".equals(value))
						formatText = true;
					else if (!"json".equals(value))
						throw new UsageError("--format must be json or text, found: " + value);
				}
				default -> throw new UsageError("unknown option '" + arg + "'");
			}
		}
		if (source == null)
			source = latestRunDocument(DEFAULT_RUNS_ROOT);
		return new Config(source, top, formatText);
	}

	private String requireValue(String option, int index) {
		if (index >= args.length)
			throw new UsageError("option " + option + " needs a value");
		return args[index];
	}

	/**
	 * With no --from, triage reads the newest run output under the scratch
	 * root: the lexicographically greatest directory name wins (names carry a
	 * timestamp), then result.json must exist inside.
	 */
	private static Path latestRunDocument(Path runsRoot) throws UsageError {
		if (!Files.isDirectory(runsRoot))
			throw new UsageError("no --from given and no previous run found under " + runsRoot);
		try (Stream<Path> entries = Files.list(runsRoot)) {
			return entries.filter(Files::isDirectory)
					.map(dir -> dir.resolve("result.json"))
					.filter(Files::isRegularFile)
					.max(Comparator.comparing(path -> path.getParent().getFileName().toString()))
					.orElseThrow(() -> new UsageError(
							"no --from given and no result.json under " + runsRoot));
		} catch (IOException e) {
			throw new UsageError("cannot list " + runsRoot + ": " + e.getMessage());
		}
	}

	private static String usage() {
		return """
				usage: oracle triage [flags]
				  --from PATH    result document to rank (a result.json or a directory holding one);
				                 default: newest run under /tmp/swt-visual-oracle/runs
				  --top N        report only the first N defect groups
				  --format json|text   stdout format, default json""";
	}

	private int execute() throws IOException {
		Config config = parseConfig();
		Path documentPath = resolveDocument(config.source());

		RunResult document;
		try {
			document = RunResult.fromJson(Files.readString(documentPath, StandardCharsets.UTF_8));
		} catch (org.eclipse.swt.visualoracle.json.JsonException e) {
			throw new IllegalArgumentException(documentPath + " is not a valid schema-v1"
					+ " result document: " + e.getMessage(), e);
		}

		Map<String, Object> report = rank(document, documentPath, config.top());
		if (config.formatText())
			printTextReport(report);
		else
			stdout.println(JsonWriter.write(report));
		return 0;
	}

	private Path resolveDocument(Path source) throws UsageError {
		if (Files.isRegularFile(source))
			return source;
		Path candidate = source.resolve("result.json");
		if (Files.isRegularFile(candidate))
			return candidate;
		throw new UsageError(source + " holds no result.json");
	}

	// ---------------------------------------------------------------- ranking

	/** One ranked defect group: same widget family, same probable defect class. */
	record Group(String family, String defectClass, List<ComparisonEntry> members) {

		static Group of(String family, String defectClass) {
			return new Group(family, defectClass, new ArrayList<>());
		}

		void add(ComparisonEntry entry) {
			members.add(entry);
		}

		int affected() {
			return members.size();
		}

		long totalChangedPixels() {
			return members.stream().mapToLong(ComparisonEntry::changedPixels).sum();
		}

		double maxChangedFraction() {
			return members.stream().mapToDouble(ComparisonEntry::changedFraction).max().orElse(0.0);
		}
	}

	/**
	 * Pure function from document plus arguments to the report map; identical
	 * inputs always produce identical output, including member order inside
	 * every group.
	 */
	static Map<String, Object> rank(RunResult document, Path documentPath, Integer top) {
		Map<String, Group> groupsById = new LinkedHashMap<>();
		int equal = 0;
		int withinTolerance = 0;
		int different = 0;
		for (ComparisonEntry entry : document.comparisons()) {
			switch (entry.verdict()) {
				case EQUAL -> equal++;
				case WITHIN_TOLERANCE -> withinTolerance++;
				case DIFFERENT -> {
					different++;
					String family = RunSelection.familyOf(entry.specimen());
					Group group = groupsById.computeIfAbsent(family + "\u0000" + entry.probableDefectClass(),
							k -> Group.of(family, entry.probableDefectClass().name()));
					group.add(entry);
				}
			}
		}

		List<Group> groups = new ArrayList<>(groupsById.values());
		for (Group group : groups)
			group.members().sort(Comparator.comparingLong(ComparisonEntry::changedPixels).reversed()
					.thenComparing(ComparisonEntry::specimen));
		groups.sort(Comparator
				.comparingInt((Group g) -> g.affected()).reversed()
				.thenComparing(Comparator.comparingDouble(Group::maxChangedFraction).reversed())
				.thenComparing(Group::family)
				.thenComparing(g -> g.defectClass()));

		Map<String, Long> comparablePerFamily = new LinkedHashMap<>();
		for (ComparisonEntry entry : document.comparisons())
			comparablePerFamily.merge(RunSelection.familyOf(entry.specimen()), 1L, Long::sum);

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("verb", "triage");
		report.put("tool", "oracle-harness/" + OracleCli.VERSION);
		report.put("source", documentPath.normalize().toString());
		report.put("rankingOptimisesFor",
				"fewest investigations per defect fixed: groups by widget family x probable"
						+ " defect class, ordered by affected specimens, family coverage, severity");

		Map<String, Object> totals = new LinkedHashMap<>();
		totals.put("comparisons", Integer.valueOf(document.comparisons().size()));
		totals.put("equal", Integer.valueOf(equal));
		totals.put("withinTolerance", Integer.valueOf(withinTolerance));
		totals.put("different", Integer.valueOf(different));
		totals.put("groups", Integer.valueOf(groups.size()));
		totals.put("unsupportedCaptures", Integer.valueOf(countStatus(document.captures(), CaptureStatus.UNSUPPORTED)));
		totals.put("failedCaptures", Integer.valueOf(countStatus(document.captures(), CaptureStatus.FAILED)));
		report.put("totals", totals);

		int limit = top != null ? Math.min(top.intValue(), groups.size()) : groups.size();
		List<Object> groupMaps = new ArrayList<>();
		for (int rank = 0; rank < limit; rank++) {
			Group group = groups.get(rank);
			String family = group.family();
			long comparable = comparablePerFamily.getOrDefault(family, Long.valueOf(group.affected()))
					.longValue();
			Map<String, Object> groupMap = new LinkedHashMap<>();
			groupMap.put("rank", Integer.valueOf(rank + 1));
			groupMap.put("family", family);
			groupMap.put("defectClass", group.defectClass());
			groupMap.put("affected", Integer.valueOf(group.affected()));
			groupMap.put("comparableInFamily", Long.valueOf(comparable));
			groupMap.put("coverage", comparable > 0 ? Double.valueOf((double) group.affected() / comparable)
					: Double.valueOf(0.0));
			groupMap.put("totalChangedPixels", Long.valueOf(group.totalChangedPixels()));
			groupMap.put("hypothesis", hypothesis(family, group, comparable));
			List<Object> specimenMaps = new ArrayList<>();
			for (ComparisonEntry entry : group.members()) {
				Map<String, Object> specimenMap = new LinkedHashMap<>();
				specimenMap.put("specimen", entry.specimen());
				specimenMap.put("changedPixels", Long.valueOf(entry.changedPixels()));
				specimenMap.put("changedFraction", Double.valueOf(entry.changedFraction()));
				specimenMap.put("maxChannelDelta", Integer.valueOf(entry.maxChannelDelta()));
				specimenMaps.add(specimenMap);
			}
			groupMap.put("specimens", specimenMaps);
			groupMaps.add(groupMap);
		}
		report.put("groups", groupMaps);

		List<Object> failures = new ArrayList<>();
		List<Object> unsupported = new ArrayList<>();
		for (CaptureEntry capture : document.captures()) {
			if (capture.status() == CaptureStatus.FAILED) {
				Map<String, Object> failure = new LinkedHashMap<>();
				failure.put("specimen", capture.specimen());
				failure.put("backend", capture.backend());
				if (capture.message() != null)
					failure.put("message", capture.message());
				failures.add(failure);
			} else if (capture.status() == CaptureStatus.UNSUPPORTED) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("specimen", capture.specimen());
				entry.put("backend", capture.backend());
				unsupported.add(entry);
			}
		}
		failures.sort(Comparator.comparing(map -> (String) ((Map<?, ?>) map).get("specimen")));
		unsupported.sort(Comparator.comparing(map -> (String) ((Map<?, ?>) map).get("specimen")));
		report.put("failedCaptures", failures);
		report.put("unsupportedCaptures", unsupported);
		return report;
	}

	private static int countStatus(List<CaptureEntry> captures, CaptureStatus status) {
		int count = 0;
		for (CaptureEntry capture : captures)
			if (capture.status() == status)
				count++;
		return count;
	}

	private static String hypothesis(String family, Group group, long comparableInFamily) {
		String defect = switch (group.defectClass()) {
			case "SHIFTED" -> "a position or baseline error";
			case "MISSING_ELEMENT" -> "an element drawn on one side only";
			case "WRONG_COLOR" -> "colors resolved differently";
			case "WRONG_GLYPH" -> "text rendered differently";
			case "UNKNOWN" -> "an unclassified rendering difference";
			default -> "a rendering difference";
		};
		if (group.affected() > 1 && group.affected() >= comparableInFamily)
			return "every " + family + " comparison differs (" + defect + "); one shared defect in"
					+ " the " + family + " rendering path likely explains all "
					+ group.affected() + " specimens";
		if (group.affected() > 1)
			return group.affected() + " of " + comparableInFamily + " " + family
					+ " comparisons differ (" + defect + ")";
		return "isolated difference on one specimen (" + defect + ")";
	}

	private void printTextReport(Map<String, Object> report) {
		Map<?, ?> totals = (Map<?, ?>) report.get("totals");
		stdout.println("triage of " + report.get("source"));
		stdout.printf("comparisons=%s equal=%s withinTolerance=%s different=%s failedCaptures=%s%n",
				totals.get("comparisons"), totals.get("equal"), totals.get("withinTolerance"),
				totals.get("different"), totals.get("failedCaptures"));
		Object groups = report.get("groups");
		if (groups instanceof List<?> list && list.isEmpty())
			stdout.println("no differences above tolerance");
		for (Object item : listOrEmpty(groups)) {
			Map<?, ?> group = (Map<?, ?>) item;
			stdout.printf("#%d %s/%s: %s of %s specimens differ%s%n",
					group.get("rank"), group.get("family"), group.get("defectClass"),
					group.get("affected"), group.get("comparableInFamily"),
					coverageSuffix(group.get("coverage")));
			for (Object spec : listOrEmpty(group.get("specimens"))) {
				Map<?, ?> s = (Map<?, ?>) spec;
				stdout.printf("     %-40s %8s px %6.2f%%%n", s.get("specimen"),
						s.get("changedPixels"),
						((Number) s.get("changedFraction")).doubleValue() * 100.0);
			}
		}
	}

	private static String coverageSuffix(Object coverage) {
		if (coverage instanceof Double d && d.doubleValue() >= 0.9999)
			return " (whole family)";
		return "";
	}

	private static List<?> listOrEmpty(Object value) {
		return value instanceof List<?> list ? list : List.of();
	}
}
