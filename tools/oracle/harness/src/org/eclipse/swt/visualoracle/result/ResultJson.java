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
package org.eclipse.swt.visualoracle.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.visualoracle.json.JsonException;
import org.eclipse.swt.visualoracle.json.JsonParser;
import org.eclipse.swt.visualoracle.json.ResultSchemaValidator;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.DiffCluster;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * Reads a result document back into the {@link RunResult} model, the inverse
 * of {@link RunResult#toJson()}.
 *
 * Reading never guesses: the schema version is checked before any field is
 * interpreted, so an unknown version fails with a clear message instead of
 * misreading fields; the full strict validation runs next; only then is the
 * model built. See {@code docs/visual-oracle/RESULT-SCHEMA.md}.
 */
public final class ResultJson {

	private ResultJson() {
	}

	/** Parses and validates {@code text}; throws {@link JsonException} on any problem. */
	public static RunResult read(String text) {
		return readDocument(JsonParser.parse(text));
	}

	/** Validates and maps an already-parsed result document. */
	public static RunResult readDocument(Object document) {
		if (!(document instanceof Map<?, ?> root))
			throw new JsonException("result document must be a JSON object");
		requireSupportedVersion(root);
		List<String> errors = ResultSchemaValidator.validate(root);
		if (!errors.isEmpty())
			throw new JsonException("result document violates schema version "
					+ ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION + ": " + String.join("; ", errors));
		return new RunResult(ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION,
				str(root, "generator"),
				readEnvironment(asMap(root.get("environment"))),
				readCaptures(root.get("captures")),
				readComparisons(root.get("comparisons")));
	}

	private static void requireSupportedVersion(Map<?, ?> root) {
		Object value = root.get("schemaVersion");
		if (!(value instanceof Number number) || value instanceof Double)
			throw new JsonException("schemaVersion must be an integer, found: " + describe(value)
					+ "; this reader reads result schema version "
					+ ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION + " only");
		long version = number.longValue();
		if (version != ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION)
			throw new JsonException("unsupported result schema version " + version
					+ "; this build reads version " + ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION
					+ " only (docs/visual-oracle/RESULT-SCHEMA.md)");
	}

	private static RenderEnv readEnvironment(Map<?, ?> env) {
		return new RenderEnv(intValue(env.get("zoomPercent")), new Theme(str(env, "theme")),
				Direction.valueOf(str(env, "direction")), str(env, "fontFamily"),
				intValue(env.get("fontSize")));
	}

	private static List<CaptureEntry> readCaptures(Object captures) {
		List<CaptureEntry> entries = new ArrayList<>();
		for (Object node : asList(captures)) {
			Map<?, ?> e = asMap(node);
			CaptureStatus status = CaptureStatus.valueOf(str(e, "status"));
			String specimen = str(e, "specimen");
			String backend = str(e, "backend");
			if (status == CaptureStatus.CAPTURED)
				entries.add(new CaptureEntry(specimen, backend, status, Integer.valueOf(intValue(e.get("width"))),
						Integer.valueOf(intValue(e.get("height"))), str(e, "image"), null));
			else
				entries.add(CaptureEntry.skipped(specimen, backend, status,
						e.containsKey("message") ? str(e, "message") : null));
		}
		return entries;
	}

	private static List<ComparisonEntry> readComparisons(Object comparisons) {
		List<ComparisonEntry> entries = new ArrayList<>();
		for (Object node : asList(comparisons)) {
			Map<?, ?> e = asMap(node);
			List<DiffCluster> clusters = new ArrayList<>();
			for (Object cluster : asList(e.get("clusters"))) {
				Map<?, ?> c = asMap(cluster);
				clusters.add(new DiffCluster(intValue(c.get("x")), intValue(c.get("y")),
						intValue(c.get("width")), intValue(c.get("height")), longValue(c.get("changedPixels"))));
			}
			entries.add(new ComparisonEntry(str(e, "specimen"), str(e, "referenceBackend"),
					str(e, "candidateBackend"), str(e, "referenceImage"), str(e, "candidateImage"),
					Verdict.valueOf(str(e, "verdict")), longValue(e.get("changedPixels")),
					doubleValue(e.get("changedFraction")), intValue(e.get("maxChannelDelta")),
					DefectClass.valueOf(str(e, "probableDefectClass")), clusters));
		}
		return entries;
	}

	private static Map<?, ?> asMap(Object value) {
		if (!(value instanceof Map<?, ?> map))
			throw new JsonException("expected a JSON object, found: " + describe(value));
		return map;
	}

	private static List<?> asList(Object value) {
		if (!(value instanceof List<?> list))
			throw new JsonException("expected a JSON array, found: " + describe(value));
		return list;
	}

	private static String str(Map<?, ?> map, String key) {
		Object value = map.get(key);
		if (!(value instanceof String s))
			throw new JsonException("expected string field '" + key + "', found: " + describe(value));
		return s;
	}

	private static int intValue(Object value) {
		long raw = longValue(value);
		if (raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE)
			throw new JsonException("integer field out of range: " + raw);
		return (int) raw;
	}

	private static long longValue(Object value) {
		if (!(value instanceof Number number) || value instanceof Double)
			throw new JsonException("expected integer field, found: " + describe(value));
		return number.longValue();
	}

	private static double doubleValue(Object value) {
		if (value instanceof Number number)
			return number.doubleValue();
		throw new JsonException("expected number field, found: " + describe(value));
	}

	private static String describe(Object value) {
		return value == null ? "null" : value.getClass().getSimpleName();
	}
}
