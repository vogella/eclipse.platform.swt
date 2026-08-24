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

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.visualoracle.impl.ChildProcessLauncher;
import org.eclipse.swt.visualoracle.impl.ResultMerger;
import org.eclipse.swt.visualoracle.json.JsonException;
import org.eclipse.swt.visualoracle.json.JsonParser;
import org.eclipse.swt.visualoracle.json.JsonWriter;
import org.eclipse.swt.visualoracle.json.ResultSchemaValidator;
import org.eclipse.swt.visualoracle.result.CaptureEntry;
import org.eclipse.swt.visualoracle.result.CaptureStatus;
import org.eclipse.swt.visualoracle.result.ComparisonEntry;
import org.eclipse.swt.visualoracle.result.RunResult;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.DiffCluster;
import org.eclipse.swt.visualoracle.spi.DiffResult;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * Selftest checks for the T08 result contract: explicit versioning, strict
 * validation with precise messages, write-read round-trip equality including
 * edge cases, and merge determinism.
 *
 * Every method here is pure Java over the model and JSON layer; no Display,
 * no images.
 */
public final class ResultCheck {

	private ResultCheck() {
	}

	// ---------------------------------------------------------------- checks

	/** The empty run: nothing captured, nothing compared, still a full contract document. */
	public static void checkRoundTripEmptyRun(RenderEnv env) {
		RunResult original = new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "oracle-harness/selftest",
				env, List.of(), List.of());
		assertRoundTrip(original, "empty run");
	}

	/**
	 * A specimen whose capture failed and a backend that does not support a
	 * specimen survive the round trip, message included; a failure without a
	 * message omits the field entirely instead of writing an empty string the
	 * schema forbids.
	 */
	public static void checkRoundTripFailureStatuses(RenderEnv env) {
		RunResult original = new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "oracle-harness/selftest",
				env,
				List.of(CaptureEntry.skipped("button.push.default", "native",
								CaptureStatus.FAILED, "capture crashed: deliberate"),
						CaptureEntry.skipped("button.push.default", "skia-canvas",
								CaptureStatus.UNSUPPORTED, "backend cannot render this specimen"),
						CaptureEntry.skipped("label.default", "skia-canvas",
								CaptureStatus.FAILED, null)),
				List.of());
		String json = original.toJson();
		Object document = JsonParser.parse(json);
		List<String> errors = ResultSchemaValidator.validate(document);
		require(errors.isEmpty(), "failure-status document rejected by its own schema: "
				+ String.join("; ", errors));
		Map<?, ?> root = (Map<?, ?>) document;
		Map<?, ?> silent = ((List<Map<?, ?>>) root.get("captures")).get(2);
		require(!silent.containsKey("message"),
				"a FAILED entry without message must omit it, wrote: " + silent.get("message"));
		assertRoundTrip(original, "failure statuses");
	}

	/** Comparisons with no clusters and with a zero-pixel cluster round-trip unchanged. */
	public static void checkRoundTripClusters(RenderEnv env) {
		List<DiffCluster> zeroPixels = List.of(new DiffCluster(12, 8, 40, 20, 0L));
		RunResult original = new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "oracle-harness/selftest",
				env,
				List.of(CaptureEntry.captured("button.push.default", "native",
						new FixedCapture(140, 40), "images/button-native.png")),
				List.of(new ComparisonEntry("button.push.default", "native", "native",
								"images/a.png", "images/b.png", Verdict.EQUAL, 0L, 0.0d, 0,
								DefectClass.NONE, List.of()),
						new ComparisonEntry("label.default", "native", "skia-canvas",
								"images/c.png", "images/d.png", Verdict.DIFFERENT, 0L, 0.0d, 0,
								DefectClass.UNKNOWN, zeroPixels)));
		assertRoundTrip(original, "clusters incl. zero pixels");
	}

	/**
	 * The realistic document of the running harness (one real capture, one
	 * real diff) must come back as an equal model, proving the guarantee for
	 * the shape consumers actually parse.
	 */
	public static void checkRoundTripFullModel(RenderEnv env, String specimenId,
			CapturedImage capture, DiffResult diff) {
		RunResult original = new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "oracle-harness/selftest",
				env,
				List.of(CaptureEntry.captured(specimenId, "native", capture,
						"images/" + specimenId + "-native.png")),
				List.of(new ComparisonEntry(specimenId, "native", "native",
						"images/ref.png", "images/cand.png", diff.verdict(), diff.changedPixels(),
						diff.changedFraction(), diff.maxChannelDelta(), diff.probableClass(),
						diff.clusters())));
		assertRoundTrip(original, "full model");
	}

	/**
	 * Version enforcement: every written document carries the current
	 * version; the reader rejects a missing, fractional or unknown version
	 * with a message that names the versions involved, and does so before any
	 * field is interpreted; the merger refuses mixed-version input.
	 */
	public static void checkVersionEnforcement() {
		RunResult current = fullExample();
		Map<String, Object> document = parsed(current);
		require(((Number) document.get("schemaVersion")).longValue() == RunResult.CURRENT_SCHEMA_VERSION,
				"written document does not carry the current schema version");

		document.remove("schemaVersion");
		requireFailsWith(document, "schemaVersion", "missing schemaVersion accepted");

		Map<String, Object> future = parsed(current);
		future.put("schemaVersion", Long.valueOf(ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION + 1));
		requireFailsWith(future, "unsupported result schema version "
				+ (ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION + 1), "unknown version accepted");

		Map<String, Object> fractional = parsed(current);
		fractional.put("schemaVersion", Double.valueOf(1.0d));
		requireFailsWith(fractional, "must be an integer", "fractional schemaVersion accepted");

		Map<String, Object> misleading = new LinkedHashMap<>();
		misleading.put("schemaVersion", Long.valueOf(ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION + 1));
		misleading.put("generator", 42);
		misleading.put("environment", "not an object");
		try {
			RunResult.fromJsonMap(misleading);
			throw new AssertionError("a future-version document was mapped instead of rejected");
		} catch (JsonException e) {
			require(String.valueOf(e.getMessage()).contains("version"),
					"version gate lost the race against field errors: " + e.getMessage());
		}

		Path dirA = Path.of("/tmp/opencode/oracle-T08/check-v-a");
		Path dirB = Path.of("/tmp/opencode/oracle-T08/check-v-b");
		Map<String, Object> older = parsed(current);
		older.put("schemaVersion", Long.valueOf(ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION + 9));
		try {
			ResultMerger.merge(List.of(new ResultMerger.MergeInput(parsed(current), dirA),
					new ResultMerger.MergeInput(older, dirB)), "gen", dirA);
			throw new AssertionError("merger accepted mixed schema versions");
		} catch (IllegalArgumentException e) {
			require(String.valueOf(e.getMessage()).contains("schemaVersion"),
					"mixed-version refusal does not say what is wrong: " + e.getMessage());
		}
	}

	/**
	 * Validation failures must say precisely what is wrong and where: the
	 * message names the offending field, its location in the document and,
	 * for enums, the allowed values.
	 */
	public static void checkValidatorMessages() {
		RunResult current = fullExample();

		Map<String, Object> surpriseTop = parsed(current);
		surpriseTop.put("surprise", Boolean.TRUE);
		requireError(surpriseTop, "document: unknown property 'surprise'",
				"unknown top-level property not reported with location");

		Map<String, Object> surpriseNested = parsed(current);
		capturesOf(surpriseNested).get(1).put("extra", 1);
		requireError(surpriseNested, "captures[1]: unknown property 'extra'",
				"unknown nested property not reported with location");

		Map<String, Object> wrongType = parsed(current);
		capturesOf(wrongType).get(0).put("width", "140");
		requireError(wrongType, "captures[0].width must be an integer",
				"wrong type not reported with location");

		Map<String, Object> missingField = parsed(current);
		comparisonsOf(missingField).get(0).remove("specimen");
		requireError(missingField, "comparisons[0].specimen is required",
				"missing required field not reported with location");

		Map<String, Object> badEnum = parsed(current);
		comparisonsOf(badEnum).get(0).put("verdict", "MOSTLY_EQUAL");
		requireError(badEnum, "comparisons[0].verdict has invalid value 'MOSTLY_EQUAL', "
						+ "expected one of [DIFFERENT, EQUAL, WITHIN_TOLERANCE]",
				"invalid enum value not reported with location and choices");

		Map<String, Object> negativePixels = parsed(current);
		comparisonsOf(negativePixels).get(0).put("changedPixels", Long.valueOf(-1));
		requireError(negativePixels, "comparisons[0].changedPixels must be >= 0",
				"negative changedPixels accepted");
	}

	/**
	 * Merge stability under arrival order: merging the same two documents in
	 * both orders yields byte-identical output, canonically ordered, and the
	 * merged document itself validates.
	 */
	public static void checkMergeOrderIndependentBytes(ChildProcessLauncher.ChildOutcome first,
			ChildProcessLauncher.ChildOutcome second, String version, PrintStream out) {
		require(first.succeeded() && second.succeeded(), "merge inputs did not run cleanly");
		Path target = Path.of("/tmp/opencode/oracle-T08/check-merge-target");
		String generator = "oracle-harness/" + version + "/order-check";
		byte[] forward = serialise(ResultMerger.merge(
				List.of(input(first), input(second)), generator, target));
		byte[] backward = serialise(ResultMerger.merge(
				List.of(input(second), input(first)), generator, target));
		require(Arrays.equals(forward, backward),
				"merging the same two documents in arrival order changed the bytes");

		Object merged = JsonParser.parse(new String(forward, StandardCharsets.UTF_8));
		List<String> errors = ResultSchemaValidator.validate(merged);
		require(errors.isEmpty(), "merged document invalid: " + String.join("; ", errors));

		List<Map<String, Object>> captures = castList(((Map<?, ?>) merged).get("captures"));
		require(captures.size() == 2, "expected two merged captures, found " + captures.size());
		for (int i = 1; i < captures.size(); i++)
			require(key(captures.get(i - 1)).compareTo(key(captures.get(i))) <= 0,
					"merged captures not in canonical order");
		out.printf("      merged %d bytes, identical in both orders%n", forward.length);
	}

	// -------------------------------------------------------------- examples

	/** Shared example documents so every check mutates the same realistic shape. */
	private static RunResult fullExample() {
		RenderEnv env = new RenderEnv(100, Theme.PLATFORM_DEFAULT, Direction.LTR, "Sans", 11);
		return new RunResult(RunResult.CURRENT_SCHEMA_VERSION, "oracle-harness/selftest", env,
				List.of(CaptureEntry.captured("button.push.default", "native",
								new FixedCapture(140, 40), "images/button-native.png"),
						CaptureEntry.skipped("button.push.default", "skia-canvas",
								CaptureStatus.UNSUPPORTED, "adapter arrives later")),
				List.of(new ComparisonEntry("button.push.default", "native",
						"native", "images/r.png", "images/c.png",
						Verdict.EQUAL, 0L, 0.0d, 0, DefectClass.NONE, List.of())));
	}

	/** Shape-only capture: carries extents, never asked for pixels or PNGs. */
	private record FixedCapture(int width, int height) implements CapturedImage {
		@Override
		public org.eclipse.swt.graphics.ImageData imageData() {
			throw new UnsupportedOperationException("shape-only example capture");
		}

		@Override
		public byte[] pngBytes() {
			throw new UnsupportedOperationException("shape-only example capture");
		}
	}

	// --------------------------------------------------------------- helpers

	private static void assertRoundTrip(RunResult original, String what) {
		String json = original.toJson();
		List<String> errors = ResultSchemaValidator.validate(JsonParser.parse(json));
		require(errors.isEmpty(), what + " document rejected by its own schema: "
				+ String.join("; ", errors));
		RunResult readBack = RunResult.fromJson(json);
		require(readBack.equals(original),
				what + ": read-back model differs from the written model:\n  written "
						+ original + "\n  read    " + readBack);
		require(readBack.schemaVersion() == RunResult.CURRENT_SCHEMA_VERSION,
				"read-back schemaVersion is " + readBack.schemaVersion());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> parsed(RunResult result) {
		return (Map<String, Object>) JsonParser.parse(result.toJson());
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> capturesOf(Map<String, Object> document) {
		return (List<Map<String, Object>>) document.get("captures");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> comparisonsOf(Map<String, Object> document) {
		return (List<Map<String, Object>>) document.get("comparisons");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> castList(Object value) {
		return (List<Map<String, Object>>) value;
	}

	private static String key(Map<String, Object> capture) {
		return capture.get("specimen") + "/" + capture.get("backend");
	}

	private static ResultMerger.MergeInput input(ChildProcessLauncher.ChildOutcome outcome) {
		return new ResultMerger.MergeInput(outcome.resultDocument(), outcome.directory());
	}

	private static byte[] serialise(Map<String, Object> document) {
		return JsonWriter.write(document).getBytes(StandardCharsets.UTF_8);
	}

	private static void requireFailsWith(Object document, String expectedFragment, String what) {
		try {
			RunResult.fromJsonMap(document);
			throw new AssertionError(what);
		} catch (JsonException e) {
			require(String.valueOf(e.getMessage()).contains(expectedFragment),
					"rejection message lacks '" + expectedFragment + "': " + e.getMessage());
		}
	}

	private static void requireError(Object document, String expectedMessage, String what) {
		List<String> errors = ResultSchemaValidator.validate(document);
		require(!errors.isEmpty(), what);
		require(errors.contains(expectedMessage),
				"errors " + errors + " do not contain the precise message '" + expectedMessage + "'");
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
