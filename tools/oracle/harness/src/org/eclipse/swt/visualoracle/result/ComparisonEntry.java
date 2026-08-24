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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.DiffCluster;
import org.eclipse.swt.visualoracle.spi.DiffResult;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * One entry of the {@code comparisons} array in a result document: the diff
 * of two captures of the same specimen from different backends.
 *
 * @param specimen specimen id
 * @param referenceBackend backend id of the oracle side
 * @param candidateBackend backend id of the side under test
 * @param referenceImage PNG path relative to the result document
 * @param candidateImage PNG path relative to the result document
 * @param verdict overall outcome under the requested tolerance
 * @param changedPixels number of differing pixels
 * @param changedFraction changedPixels over total pixels
 * @param maxChannelDelta largest per-channel absolute difference, 0..255
 * @param probableDefectClass best guess at the root cause
 * @param clusters connected regions of change, empty until T06 exists
 */
public record ComparisonEntry(String specimen, String referenceBackend, String candidateBackend,
		String referenceImage, String candidateImage, Verdict verdict,
		long changedPixels, double changedFraction, int maxChannelDelta,
		DefectClass probableDefectClass, List<DiffCluster> clusters) {

	public ComparisonEntry {
		clusters = List.copyOf(clusters);
	}

	Map<String, Object> toJson() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("specimen", specimen);
		map.put("referenceBackend", referenceBackend);
		map.put("candidateBackend", candidateBackend);
		map.put("referenceImage", referenceImage);
		map.put("candidateImage", candidateImage);
		map.put("verdict", verdict.name());
		map.put("changedPixels", Long.valueOf(changedPixels));
		map.put("changedFraction", Double.valueOf(changedFraction));
		map.put("maxChannelDelta", Integer.valueOf(maxChannelDelta));
		map.put("probableDefectClass", probableDefectClass.name());
		map.put("clusters", clusters.stream().map(c -> {
			Map<String, Object> cm = new LinkedHashMap<>();
			cm.put("x", Integer.valueOf(c.x()));
			cm.put("y", Integer.valueOf(c.y()));
			cm.put("width", Integer.valueOf(c.width()));
			cm.put("height", Integer.valueOf(c.height()));
			cm.put("changedPixels", Long.valueOf(c.changedPixels()));
			return cm;
		}).toList());
		return map;
	}
}
