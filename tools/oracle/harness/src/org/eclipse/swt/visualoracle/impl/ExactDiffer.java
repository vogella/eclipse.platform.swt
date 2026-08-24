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

import java.util.List;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.Differ;
import org.eclipse.swt.visualoracle.spi.DiffResult;
import org.eclipse.swt.visualoracle.spi.DiffCluster;
import org.eclipse.swt.visualoracle.spi.Tolerance;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * Minimal Differ: exact per-pixel comparison with a changed-pixel count.
 * No anti-alias tolerance, no cluster detection; task T06 replaces the engine
 * behind this interface.
 */
public class ExactDiffer implements Differ {

	private static final int MAX_CHANNEL_DELTA = 255;

	@Override
	public DiffResult compare(CapturedImage reference, CapturedImage candidate, Tolerance tolerance) {
		ImageData a = reference.imageData();
		ImageData b = candidate.imageData();
		long total = (long) Math.max(a.width, b.width) * Math.max(a.height, b.height);
		if (a.width != b.width || a.height != b.height) {
			return new DiffResult(Verdict.DIFFERENT, total, 1.0, MAX_CHANNEL_DELTA,
					List.of(), DefectClass.UNKNOWN);
		}

		long changed = 0;
		int maxDelta = 0;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
		int maxX = -1, maxY = -1;
		for (int y = 0; y < a.height; y++) {
			for (int x = 0; x < a.width; x++) {
				int delta = channelDelta(rgb(a, x, y), rgb(b, x, y));
				if (delta > maxDelta)
					maxDelta = delta;
				if (delta > tolerance.maxChannelDelta()) {
					changed++;
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		double fraction = total == 0 ? 0.0 : (double) changed / total;
		Verdict verdict = changed == 0 ? (maxDelta == 0 ? Verdict.EQUAL : Verdict.WITHIN_TOLERANCE)
				: Verdict.DIFFERENT;

		List<DiffCluster> clusters = List.of();
		if (changed == 1) {
			clusters = List.of(new DiffCluster(minX, minY, 1, 1, changed));
		} else if (changed > 1 && verdict == Verdict.DIFFERENT) {
			// One bounding box as a placeholder cluster until T06 detects real ones.
			clusters = List.of(new DiffCluster(minX, minY, maxX - minX + 1, maxY - minY + 1, changed));
		}
		DefectClass defect = verdict == Verdict.EQUAL || verdict == Verdict.WITHIN_TOLERANCE
				? DefectClass.NONE
				: DefectClass.UNKNOWN;
		return new DiffResult(verdict, changed, fraction, maxDelta, clusters, defect);
	}

	private static int channelDelta(RGB first, RGB second) {
		return Math.max(Math.abs(first.red - second.red),
				Math.max(Math.abs(first.green - second.green), Math.abs(first.blue - second.blue)));
	}

	private static RGB rgb(ImageData data, int x, int y) {
		PaletteData palette = data.palette;
		int pixel = data.getPixel(x, y);
		RGB rgb = palette.getRGB(pixel);
		return rgb != null ? rgb : new RGB(0, 0, 0);
	}
}
