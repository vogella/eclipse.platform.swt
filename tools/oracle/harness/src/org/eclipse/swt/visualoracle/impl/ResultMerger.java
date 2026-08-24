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
package org.eclipse.swt.visualoracle.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.visualoracle.json.JsonWriter;
import org.eclipse.swt.visualoracle.json.ResultSchemaValidator;

/**
 * Merges the schema-v1 result documents of several children into one run
 * result. The JSON is the comparison substrate (decision D6), so merging
 * happens at the document level and invents no second interchange format.
 *
 * Children that ran under different environments cannot share one document:
 * the frozen schema carries exactly one {@code environment} per document,
 * because one process serves exactly one environment. Merging therefore
 * refuses mixed environments rather than writing a document whose label
 * would be a lie; grouping by environment is the caller's job (T20).
 *
 * Merging is deterministic: the same inputs in any arrival order produce
 * byte-identical output. Captures and comparisons are ordered canonically
 * (by specimen id, then backend ids), so the report does not churn between
 * runs.
 */
public final class ResultMerger {

	private ResultMerger() {
	}

	/** One child document plus the directory it was written to. */
	public record MergeInput(Map<String, Object> document, Path documentDir) {
	}

	/**
	 * Merges the inputs into one schema-v1 document map. Image paths are
	 * rebased so they stay relative to {@code mergedDocDir}, where the caller
	 * will write the merged document.
	 */
	public static Map<String, Object> merge(List<MergeInput> inputs, String generator, Path mergedDocDir) {
		if (inputs.isEmpty())
			throw new IllegalArgumentException("nothing to merge");
		Map<String, Object> environment = null;
		List<Object> captures = new ArrayList<>();
		List<Object> comparisons = new ArrayList<>();
		for (MergeInput input : inputs) {
			Map<String, Object> doc = input.document();
			requireMergeable(doc);
			Map<String, Object> docEnv = canonicalEnvironment(envMap(doc));
			if (environment == null) {
				environment = docEnv;
			} else if (!environment.equals(docEnv)) {
				throw new IllegalArgumentException("refusing to merge across environments: "
						+ environment + " vs " + docEnv);
			}
			for (Object entry : listOrEmpty(doc.get("captures")))
				captures.add(rebase(asMap(entry), input.documentDir(), mergedDocDir, "image"));
			for (Object entry : listOrEmpty(doc.get("comparisons")))
				comparisons.add(rebaseComparison(asMap(entry), input.documentDir(), mergedDocDir));
		}
		captures.sort(CAPTURE_ORDER);
		comparisons.sort(COMPARISON_ORDER);
		Map<String, Object> merged = new LinkedHashMap<>();
		// Long, not Integer: the strict validator accepts exactly what JsonParser
		// produces for JSON integers, so an in-memory consumer of this map
		// (T20 run verb) validates the same document that a file round-trip yields.
		merged.put("schemaVersion", Long.valueOf(ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION));
		merged.put("generator", generator);
		merged.put("environment", environment);
		merged.put("captures", captures);
		merged.put("comparisons", comparisons);
		return merged;
	}

	private static final Comparator<Object> CAPTURE_ORDER = Comparator
			.comparing((Object entry) -> text(asMap(entry), "specimen"))
			.thenComparing(entry -> text(asMap(entry), "backend"))
			.thenComparing(ResultMerger::canonicalForm);

	private static final Comparator<Object> COMPARISON_ORDER = Comparator
			.comparing((Object entry) -> text(asMap(entry), "specimen"))
			.thenComparing(entry -> text(asMap(entry), "referenceBackend"))
			.thenComparing(entry -> text(asMap(entry), "candidateBackend"))
			.thenComparing(ResultMerger::canonicalForm);

	/**
	 * Entries equal on all sort keys (duplicates from different children)
	 * still need one fixed relative order, so their serialised form breaks
	 * the tie and arrival order cannot leak into the output.
	 */
	private static String canonicalForm(Object entry) {
		return JsonWriter.write(entry);
	}

	private static String text(Map<?, ?> map, String key) {
		Object value = map.get(key);
		return value instanceof String s ? s : "";
	}

	/**
	 * A merge input must itself be a valid result document of the supported
	 * schema version; merging silently passes garbage through otherwise.
	 */
	private static void requireMergeable(Map<String, Object> doc) {
		Object version = doc.get("schemaVersion");
		long found = version instanceof Number n ? n.longValue() : -1;
		if (found != ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION)
			throw new IllegalArgumentException("refusing to merge: document has schemaVersion "
					+ version + ", expected " + ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION);
		List<String> errors = ResultSchemaValidator.validate(doc);
		if (!errors.isEmpty())
			throw new IllegalArgumentException("refusing to merge: document violates schema version "
					+ ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION + ": " + String.join("; ", errors));
	}

	/** Rebuilds the environment map in documented member order. */
	private static Map<String, Object> canonicalEnvironment(Map<String, Object> env) {
		Map<String, Object> canonical = new LinkedHashMap<>();
		for (String key : List.of("zoomPercent", "theme", "direction", "fontFamily", "fontSize")) {
			if (!env.containsKey(key))
				throw new IllegalArgumentException("environment is missing '" + key + "'");
			canonical.put(key, env.get(key));
		}
		return canonical;
	}

	private static Map<String, Object> rebaseComparison(Map<String, Object> entry, Path from, Path to) {
		entry = rebase(entry, from, to, "referenceImage");
		return rebase(entry, from, to, "candidateImage");
	}

	private static Map<String, Object> rebase(Map<String, Object> entry, Path from, Path to, String pathField) {
		Object relative = entry.get(pathField);
		if (!(relative instanceof String path) || path.isEmpty())
			return entry;
		Path absolute = from.resolve(path).toAbsolutePath().normalize();
		Map<String, Object> rebased = new LinkedHashMap<>(entry);
		rebased.put(pathField, to.toAbsolutePath().normalize().relativize(absolute).toString());
		return rebased;
	}

	private static Map<String, Object> envMap(Map<String, Object> doc) {
		return new LinkedHashMap<>(environmentOf(doc));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> environmentOf(Map<String, Object> doc) {
		Object env = doc.get("environment");
		if (!(env instanceof Map))
			throw new IllegalArgumentException("document has no environment object");
		return (Map<String, Object>) env;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		if (!(value instanceof Map))
			throw new IllegalArgumentException("expected an object, found: " + value);
		return new LinkedHashMap<>((Map<String, Object>) value);
	}

	private static List<?> listOrEmpty(Object value) {
		return value instanceof List<?> list ? list : List.of();
	}
}
