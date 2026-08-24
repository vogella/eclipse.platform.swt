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
package org.eclipse.swt.visualoracle.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a parsed result JSON document against the harness result schema,
 * version 1, as documented in {@code docs/visual-oracle/RESULT-SCHEMA.md}.
 *
 * Validation is strict: unknown properties, wrong types, wrong enum values and
 * conditional-field violations all count as errors. Every error names the
 * field's location (for example {@code captures[2].width}) and what is wrong
 * with it, so a consumer can fix the document without reverse-engineering
 * the schema.
 */
public final class ResultSchemaValidator {

	/** The schema version this validator enforces. */
	public static final int SUPPORTED_SCHEMA_VERSION = 1;

	private static final Set<String> DIRECTIONS = Set.of("LTR", "RTL");
	private static final Set<String> STATUSES = Set.of("CAPTURED", "UNSUPPORTED", "FAILED");
	private static final Set<String> VERDICTS = Set.of("EQUAL", "WITHIN_TOLERANCE", "DIFFERENT");
	private static final Set<String> DEFECT_CLASSES = Set.of("NONE", "UNKNOWN", "SHIFTED",
			"MISSING_ELEMENT", "WRONG_COLOR", "WRONG_GLYPH");

	private final List<String> errors = new ArrayList<>();

	private ResultSchemaValidator() {
	}

	/**
	 * Returns every schema violation in {@code document}; an empty list means
	 * the document conforms.
	 */
	public static List<String> validate(Object document) {
		ResultSchemaValidator v = new ResultSchemaValidator();
		if (!(document instanceof Map<?, ?> root)) {
			v.errors.add("document must be a JSON object");
			return v.errors;
		}
		v.object(root, "", Set.of("schemaVersion", "generator", "environment", "captures", "comparisons"));
		if (!root.containsKey("schemaVersion")) {
			v.errors.add("schemaVersion is required");
		} else {
			long version = v.intField(root, "schemaVersion", "", 1);
			if (version != SUPPORTED_SCHEMA_VERSION)
				v.errors.add("schemaVersion must be " + SUPPORTED_SCHEMA_VERSION + ", found " + version);
		}
		v.stringField(root, "generator", "", true);
		Object env = v.child(root, "environment", "");
		if (env instanceof Map<?, ?> e) {
			v.object(e, "environment", Set.of("zoomPercent", "theme", "direction", "fontFamily", "fontSize"));
			if (v.intField(e, "zoomPercent", "environment", 1) < 1)
				v.errors.add("environment.zoomPercent must be >= 1");
			v.stringField(e, "theme", "environment", false);
			v.enumField(e, "direction", "environment", DIRECTIONS);
			v.stringField(e, "fontFamily", "environment", false);
			if (v.intField(e, "fontSize", "environment", 0) == 0)
				v.errors.add("environment.fontSize must not be 0");
		}
		Object captures = v.child(root, "captures", "");
		if (captures instanceof List<?> list) {
			for (int i = 0; i < list.size(); i++)
				v.captureEntry(list.get(i), "captures[" + i + "]");
		}
		Object comparisons = v.child(root, "comparisons", "");
		if (comparisons instanceof List<?> list) {
			for (int i = 0; i < list.size(); i++)
				v.comparisonEntry(list.get(i), "comparisons[" + i + "]");
		}
		return v.errors;
	}

	private void captureEntry(Object node, String path) {
		if (!(node instanceof Map<?, ?> e)) {
			errors.add(path + " must be an object");
			return;
		}
		object(e, path, Set.of("specimen", "backend", "status", "width", "height", "image", "message"));
		stringField(e, "specimen", path, true);
		stringField(e, "backend", path, true);
		String status = enumField(e, "status", path, STATUSES);
		boolean captured = "CAPTURED".equals(status);
		for (String dimension : List.of("width", "height")) {
			Long value = peekIntField(e, dimension, path);
			if (captured && value == null && !e.containsKey(dimension))
				errors.add(path + "." + dimension + " is required when status is CAPTURED");
			else if (!captured && e.containsKey(dimension))
				errors.add(path + "." + dimension + " is only allowed when status is CAPTURED");
			else if (value != null && value < 1)
				errors.add(path + "." + dimension + " must be >= 1");
		}
		if (e.containsKey("image")) {
			if (!captured)
				errors.add(path + ".image is only allowed when status is CAPTURED");
			else if (optStringField(e, "image", path) == null || String.valueOf(e.get("image")).isEmpty())
				errors.add(path + ".image must be a non-empty string");
		} else if (captured) {
			errors.add(path + ".image is required when status is CAPTURED");
		}
		if (e.containsKey("message")) {
			if (captured)
				errors.add(path + ".message is only allowed when status is not CAPTURED");
			else if (optStringField(e, "message", path) != null && String.valueOf(e.get("message")).isEmpty())
				errors.add(path + ".message must be non-empty when present");
		}
	}

	private void comparisonEntry(Object node, String path) {
		if (!(node instanceof Map<?, ?> e)) {
			errors.add(path + " must be an object");
			return;
		}
		object(e, path, Set.of("specimen", "referenceBackend", "candidateBackend",
				"referenceImage", "candidateImage", "verdict", "changedPixels", "changedFraction",
				"maxChannelDelta", "probableDefectClass", "clusters"));
		stringField(e, "specimen", path, true);
		stringField(e, "referenceBackend", path, true);
		stringField(e, "candidateBackend", path, true);
		stringField(e, "referenceImage", path, true);
		stringField(e, "candidateImage", path, true);
		enumField(e, "verdict", path, VERDICTS);
		Long pixels = peekIntField(e, "changedPixels", path);
		if (pixels == null)
			errors.add(path + ".changedPixels is required");
		else if (pixels < 0)
			errors.add(path + ".changedPixels must be >= 0");
		Double fraction = numberField(e, "changedFraction", path);
		if (fraction == null || fraction < 0.0 || fraction > 1.0)
			errors.add(path + ".changedFraction must be a number in 0..1");
		Long delta = peekIntField(e, "maxChannelDelta", path);
		if (delta == null)
			errors.add(path + ".maxChannelDelta is required");
		else if (delta < 0 || delta > 255)
			errors.add(path + ".maxChannelDelta must be an integer in 0..255");
		enumField(e, "probableDefectClass", path, DEFECT_CLASSES);
		Object clusters = child(e, "clusters", path);
		if (clusters instanceof List<?> list) {
			for (int i = 0; i < list.size(); i++)
				clusterEntry(list.get(i), path + ".clusters[" + i + "]");
		}
	}

	private void clusterEntry(Object node, String path) {
		if (!(node instanceof Map<?, ?> e)) {
			errors.add(path + " must be an object");
			return;
		}
		object(e, path, Set.of("x", "y", "width", "height", "changedPixels"));
		for (String coordinate : List.of("x", "y")) {
			if (peekIntField(e, coordinate, path) == null)
				errors.add(path + "." + coordinate + " is required");
		}
		for (String dimension : List.of("width", "height")) {
			Long value = peekIntField(e, dimension, path);
			if (value == null)
				errors.add(path + "." + dimension + " is required");
			else if (value < 1)
				errors.add(path + "." + dimension + " must be >= 1");
		}
		Long pixels = peekIntField(e, "changedPixels", path);
		if (pixels == null)
			errors.add(path + ".changedPixels is required");
		else if (pixels < 0)
			errors.add(path + ".changedPixels must be >= 0");
	}

	private void object(Map<?, ?> map, String path, Set<String> allowedKeys) {
		for (Object key : map.keySet()) {
			if (!allowedKeys.contains(String.valueOf(key)))
				errors.add((path.isEmpty() ? "document" : path) + ": unknown property '" + key + "'");
		}
	}

	private Object child(Map<?, ?> map, String key, String path) {
		Object value = map.get(key);
		if (value == null)
			errors.add(at(path, key) + " is required");
		return value;
	}

	private String stringField(Map<?, ?> map, String key, String path, boolean nonEmpty) {
		String value = optStringField(map, key, path);
		if (value == null) {
			errors.add(at(path, key) + " is required");
			return "";
		}
		if (nonEmpty && value.isEmpty())
			errors.add(at(path, key) + " must be non-empty");
		return value;
	}

	private String optStringField(Map<?, ?> map, String key, String path) {
		Object value = map.get(key);
		if (value == null)
			return null;
		if (!(value instanceof String s)) {
			errors.add(at(path, key) + " must be a string");
			return "";
		}
		return s;
	}

	private String enumField(Map<?, ?> map, String key, String path, Set<String> allowed) {
		Object value = map.get(key);
		if (value == null) {
			errors.add(at(path, key) + " is required");
			return "";
		}
		if (!(value instanceof String s)) {
			errors.add(at(path, key) + " must be a string");
			return "";
		}
		if (!allowed.contains(s))
			errors.add(at(path, key) + " has invalid value '" + s + "', expected one of "
					+ allowed.stream().sorted().toList());
		return s;
	}

	private long intField(Map<?, ?> map, String key, String path, long fallback) {
		Long value = peekIntField(map, key, path);
		return value == null ? fallback : value.longValue();
	}

	/** Returns the field if present and integral, null otherwise (no error). */
	private Long peekIntField(Map<?, ?> map, String key, String path) {
		Object value = map.get(key);
		if (value instanceof Long l)
			return l;
		if (value != null)
			errors.add(at(path, key) + " must be an integer");
		return null;
	}

	private Double numberField(Map<?, ?> map, String key, String path) {
		Object value = map.get(key);
		if (value instanceof Long l)
			return l.doubleValue();
		if (value instanceof Double d)
			return d;
		if (value == null)
			errors.add(at(path, key) + " is required");
		else
			errors.add(at(path, key) + " must be a number");
		return null;
	}

	private static String at(String path, String key) {
		return path.isEmpty() ? key : path + "." + key;
	}
}
