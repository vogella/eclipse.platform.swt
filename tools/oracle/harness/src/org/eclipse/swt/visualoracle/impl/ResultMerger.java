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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
			Map<String, Object> docEnv = envMap(doc);
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
		Map<String, Object> merged = new LinkedHashMap<>();
		merged.put("schemaVersion", Integer.valueOf(1));
		merged.put("generator", generator);
		merged.put("environment", environment);
		merged.put("captures", captures);
		merged.put("comparisons", comparisons);
		return merged;
	}

	private static Map<String, Object> rebaseComparison(Map<String, Object> entry, Path from, Path to) {
		entry = rebase(entry, from, to, "referenceImage");
		return rebase(entry, from, to, "candidateImage");
	}

	@SuppressWarnings("unchecked")
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
