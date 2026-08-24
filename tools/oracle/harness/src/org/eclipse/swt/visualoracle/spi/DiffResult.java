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
package org.eclipse.swt.visualoracle.spi;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of one {@link Differ} comparison, in the shape serialised into the
 * result JSON (schema v1, see {@code docs/visual-oracle/RESULT-SCHEMA.md}).
 *
 * @param verdict overall outcome under the requested tolerance
 * @param changedPixels number of pixels counted as changed
 * @param changedFraction changedPixels as a fraction of all pixels; for a size
 *     mismatch between the two images this is computed against the larger area
 * @param maxChannelDelta largest per-channel absolute difference observed,
 *     0 when the images are bit-identical
 * @param clusters connected regions of change; may be empty while T06 does
 *     not exist yet
 * @param probableClass best guess at the root cause
 */
public record DiffResult(Verdict verdict, long changedPixels, double changedFraction,
		int maxChannelDelta, List<DiffCluster> clusters, DefectClass probableClass) {

	public DiffResult {
		Objects.requireNonNull(verdict, "verdict");
		Objects.requireNonNull(clusters, "clusters");
		Objects.requireNonNull(probableClass, "probableClass");
		clusters = List.copyOf(clusters);
		if (changedPixels < 0)
			throw new IllegalArgumentException("changedPixels must be non-negative");
		if (maxChannelDelta < 0 || maxChannelDelta > 255)
			throw new IllegalArgumentException("maxChannelDelta must be 0..255");
	}
}
