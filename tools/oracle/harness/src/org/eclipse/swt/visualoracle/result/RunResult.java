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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.visualoracle.json.JsonWriter;
import org.eclipse.swt.visualoracle.spi.RenderEnv;

/**
 * The complete machine-readable outcome of one harness run: the result model
 * behind schema version 1.
 *
 * The JSON serialisation is the contract agents parse; its shape, field names
 * and enum values are frozen and documented in
 * {@code docs/visual-oracle/RESULT-SCHEMA.md}. Any change requires a new
 * schema version.
 */
public record RunResult(int schemaVersion, String generator, RenderEnv environment,
		List<CaptureEntry> captures, List<ComparisonEntry> comparisons) {

	/** The schema version this harness writes. */
	public static final int CURRENT_SCHEMA_VERSION = 1;

	public RunResult {
		if (schemaVersion != CURRENT_SCHEMA_VERSION)
			throw new IllegalArgumentException("unsupported schemaVersion: " + schemaVersion);
		captures = List.copyOf(captures);
		comparisons = List.copyOf(comparisons);
	}

	/**
	 * Serialises to JSON text conforming to
	 * {@code docs/visual-oracle/RESULT-SCHEMA.md}.
	 */
	public String toJson() {
		return JsonWriter.write(toJsonMap());
	}

	Map<String, Object> toJsonMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("schemaVersion", Integer.valueOf(CURRENT_SCHEMA_VERSION));
		map.put("generator", generator);
		Map<String, Object> env = new LinkedHashMap<>();
		env.put("zoomPercent", Integer.valueOf(environment.zoomPercent()));
		env.put("theme", environment.theme().id());
		env.put("direction", environment.direction().name());
		env.put("fontFamily", environment.fontFamily());
		env.put("fontSize", Integer.valueOf(environment.fontSize()));
		map.put("environment", env);
		List<Object> captureMaps = new ArrayList<>();
		for (CaptureEntry entry : captures)
			captureMaps.add(entry.toJson());
		map.put("captures", captureMaps);
		List<Object> comparisonMaps = new ArrayList<>();
		for (ComparisonEntry entry : comparisons)
			comparisonMaps.add(entry.toJson());
		map.put("comparisons", comparisonMaps);
		return map;
	}
}
