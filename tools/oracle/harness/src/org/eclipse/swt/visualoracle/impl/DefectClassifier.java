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

import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.DiffCluster;

/**
 * The T07 interpretation layer behind {@link DiffResult#probableClass()}:
 * maps the shape of a DIFFERENT verdict onto a probable defect class and
 * abstains ({@code UNKNOWN}) whenever the evidence does not separate the
 * classes, because a confidently wrong class sends someone to the wrong part
 * of the code while an abstention does not.
 *
 * Claims, most specific first. MISSING_ELEMENT when every cluster's interior
 * carries substantially different colour mass on the two sides while their
 * edge maps share almost nothing: an element is present on one side only,
 * whichever side that is (the frozen enum constant is direction-neutral).
 * The interior-mean shift replaces any absolute flatness or background
 * estimate, which themed gradient faces defeat; the edge veto keeps solid
 * recolourings from reading as removals. WRONG_COLOR when the luma edge maps
 * of reference and candidate agree, meaning the geometry is intact and only
 * the colours moved; recolourings whose changed luma grows an anti-aliasing
 * halo are still accepted when their signed gradients keep the directions of
 * the reference. WRONG_GLYPH when most changed pixels sit where both renderings
 * carry dense small-scale structure, which is where text lives, and the
 * change is coherent rather than scattered halo: glyph and metric
 * differences displace strokes without emptying either side. Everything
 * else is UNKNOWN.
 *
 * All thresholds carry calibration notes and were measured on real captures;
 * see the T07 handoff record in TRACKING.md. Instances are not thread-safe,
 * like the differ that owns them.
 */
final class DefectClassifier {

	/**
	 * Luma gradient between orthogonal neighbours above which a pixel counts
	 * as an edge pixel. Matches the perturbation floor used when calibrating
	 * the anti-aliasing envelope; below it, gradients are invisible jitter.
	 */
	static final int EDGE_GRADIENT_THRESHOLD = 24;

	/**
	 * Strict Dice coefficient of the two edge maps above which geometry
	 * counts as preserved outright. Measured on button.push.default captures:
	 * whole-canvas tints and solid-fill recolouring agree at 0.97 or more,
	 * while displaced glyphs land near 0.7 and removed squares at 0.0.
	 */
	static final double EDGE_AGREEMENT_WRONG_COLOR = 0.90;

	/**
	 * Dice agreement above which a high gradient correlation alone still
	 * proves preserved geometry. This second gate rescues recolourings whose
	 * changed luma grows an anti-aliasing halo around surviving strokes:
	 * the halo costs Dice but gradient directions all keep their signs.
	 * Displaced strokes fail both gates, sitting below this floor with
	 * collapsed correlation (measured: squeeze 0.696 dice, 0.130 correlation;
	 * heavy AA noise 0.686 dice; both against a 0.75 floor).
	 */
	static final double EDGE_AGREEMENT_MINIMUM = 0.75;

	/**
	 * Signed-gradient correlation above which colours demonstrably moved on
	 * intact geometry; see {@link #gradientCorrelation}. Measured: ink
	 * recolourings correlate at 0.86 or more while every displaced-stroke
	 * case that passes the Dice floor stays under 0.1, so 0.80 keeps about
	 * six points of margin to both populations.
	 */
	static final double GRADIENT_CORRELATION_WRONG_COLOR = 0.80;

	/**
	 * Strict Dice agreement below which geometry counts as destroyed rather
	 * than intact for MISSING_ELEMENT. Measured on button.push.default:
	 * removed and added solid squares over text-bearing gradient faces sit
	 * at 0.000 (the square's edges exist on one side only), while every
	 * surviving non-member measures 0.69 or higher (heavy AA noise 0.686,
	 * displaced text 0.696, recoloured fill 0.968), so 0.40 keeps a wide
	 * margin to both populations.
	 */
	static final double EDGE_AGREEMENT_MISSING_MAX = 0.40;

	/**
	 * Maximum channel distance between the interior colour means of one
	 * cluster, above which the two sides carry different content mass.
	 * Means are robust here because minority ink such as text under the
	 * defect moves them little: removed dark squares over a text-bearing
	 * gradient face shift 119, over plain ground 255, while recoloured fills
	 * shift 24, whole-canvas tints 16 and displaced text under 20, so 48
	 * keeps roughly factor-two margins.
	 */
	static final int MEAN_SHIFT_MISSING = 48;

	/**
	 * Share of changed pixels inside the clusters' own bounds below which no
	 * WRONG_GLYPH claim is made. Coherence separates displaced strokes from
	 * scattered anti-aliasing disagreement beyond the tolerance envelope:
	 * squeezed text changes 0.61 of its cluster area, AA noise at magnitude
	 * 48 only 0.15, so 0.35 sits between with margin to both.
	 */
	static final double COHERENCE_WRONG_GLYPH = 0.35;

	/**
	 * Share of changed pixels that sit where both images have dense edge
	 * structure (5x5 windows after dilating each edge map twice), above which
	 * the difference counts as concentrated on text-like content. Squeezed or
	 * re-inked text measures well above; removed flat-surface elements and
	 * mixed defects measure far below because their extra changed pixels sit
	 * on featureless ground.
	 */
	static final double BUSY_CONCENTRATION_WRONG_GLYPH = 0.80;

	/**
	 * Changed pixels below which no colour or glyph claim is made at all; the
	 * smallest defect worth naming was calibrated at 14 pixels (a removed 4x4
	 * square), and claims over less evidence than that would be noise.
	 */
	static final int MIN_CHANGED_PIXELS = 12;

	/** Padding around the cluster union while measuring edges and density. */
	static final int REGION_PAD = 2;

	private byte[] edgeRef = new byte[0];
	private byte[] edgeCand = new byte[0];
	private byte[] nearRef = new byte[0];
	private byte[] nearCand = new byte[0];
	private byte[] busyRef = new byte[0];
	private byte[] busyCand = new byte[0];
	private byte[] dilateTmp = new byte[0];
	private int[] gradRefX = new int[0];
	private int[] gradRefY = new int[0];
	private int[] gradCandX = new int[0];
	private int[] gradCandY = new int[0];
	private int[] lumaRowCur = new int[0];
	private int[] lumaRowNext = new int[0];

	DefectClass classify(List<DiffCluster> clusters, ClusterDiffer.PixelReader ra,
			ClusterDiffer.PixelReader rb, int[] deltas, int w, int h, int channelTol) {
		if (clusters.isEmpty())
			return DefectClass.UNKNOWN;

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;
		long clusterArea = 0;
		long changedPixels = 0;
		for (DiffCluster c : clusters) {
			changedPixels += c.changedPixels();
			clusterArea += (long) c.width() * c.height();
			minX = Math.min(minX, c.x() - REGION_PAD);
			minY = Math.min(minY, c.y() - REGION_PAD);
			maxX = Math.max(maxX, c.x() + c.width() - 1 + REGION_PAD);
			maxY = Math.max(maxY, c.y() + c.height() - 1 + REGION_PAD);
		}
		minX = Math.max(minX, 0);
		minY = Math.max(minY, 0);
		maxX = Math.min(maxX, w - 1);
		maxY = Math.min(maxY, h - 1);
		int rw = maxX - minX + 1;
		int rh = maxY - minY + 1;
		ensureCapacity(rw, rh, w);

		computeEdges(ra, minX, minY, maxX, maxY, edgeRef);
		computeEdges(rb, minX, minY, maxX, maxY, edgeCand);
		double agreement = strictAgreement(rw, rh);

		if (changedPixels < MIN_CHANGED_PIXELS)
			return DefectClass.UNKNOWN;

		if (agreement < EDGE_AGREEMENT_MISSING_MAX && allClustersShiftMean(clusters, ra, rb))
			return DefectClass.MISSING_ELEMENT;

		boolean geometryPreserved = agreement >= EDGE_AGREEMENT_WRONG_COLOR
				|| (agreement >= EDGE_AGREEMENT_MINIMUM && gradientCorrelation(ra, rb,
						minX, minY, maxX, maxY) >= GRADIENT_CORRELATION_WRONG_COLOR);
		if (geometryPreserved)
			return DefectClass.WRONG_COLOR;

		dilateOnce(edgeRef, rw, rh, nearRef);
		dilateOnce(nearRef, rw, rh, busyRef);
		dilateOnce(edgeCand, rw, rh, nearCand);
		dilateOnce(nearCand, rw, rh, busyCand);
		double concentration = busyConcentration(deltas, w, channelTol, minX, minY, maxX, maxY, rw);
		double coherence = clusterArea == 0 ? 0.0 : changedPixels / (double) clusterArea;
		if (concentration >= BUSY_CONCENTRATION_WRONG_GLYPH && coherence >= COHERENCE_WRONG_GLYPH)
			return DefectClass.WRONG_GLYPH;
		return DefectClass.UNKNOWN;
	}

	/**
	 * True when every cluster's interior colour means differ by at least
	 * {@link #MEAN_SHIFT_MISSING} per some channel: one side holds element
	 * mass the other lacks. Means are robust here because minority ink such
	 * as text under the defect moves them little; requiring the shift on
	 * every cluster keeps multi-defect comparisons abstaining.
	 */
	private boolean allClustersShiftMean(List<DiffCluster> clusters,
			ClusterDiffer.PixelReader ra, ClusterDiffer.PixelReader rb) {
		for (DiffCluster c : clusters) {
			if (meanShift(c, ra, rb) < MEAN_SHIFT_MISSING)
				return false;
		}
		return true;
	}

	private int meanShift(DiffCluster c, ClusterDiffer.PixelReader ra, ClusterDiffer.PixelReader rb) {
		int inset = 1;
		int minX = Math.min(c.x() + inset, c.x() + c.width() - 1);
		int maxX = Math.max(c.x() + c.width() - 1 - inset, c.x());
		int minY = Math.min(c.y() + inset, c.y() + c.height() - 1);
		int maxY = Math.max(c.y() + c.height() - 1 - inset, c.y());
		long[] ref = interiorMean(ra, minX, minY, maxX, maxY);
		long[] cand = interiorMean(rb, minX, minY, maxX, maxY);
		int shift = 0;
		for (int i = 0; i < 3; i++)
			shift = Math.max(shift, (int) Math.abs(ref[i] - cand[i]));
		return shift;
	}

	/** Per-channel mean of the region's pixels; costs no sort and matches the calibration measurements. */
	private long[] interiorMean(ClusterDiffer.PixelReader reader, int minX, int minY,
			int maxX, int maxY) {
		long sumR = 0;
		long sumG = 0;
		long sumB = 0;
		long n = 0;
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				int rgb = reader.rgb(x, y);
				sumR += (rgb >> 16) & 0xFF;
				sumG += (rgb >> 8) & 0xFF;
				sumB += rgb & 0xFF;
				n++;
			}
		}
		if (n == 0)
			return new long[] { 0, 0, 0 };
		return new long[] { sumR / n, sumG / n, sumB / n };
	}

	private void ensureCapacity(int rw, int rh, int imageWidth) {
		int size = rw * rh;
		if (edgeRef.length < size) {
			edgeRef = new byte[size];
			edgeCand = new byte[size];
			nearRef = new byte[size];
			nearCand = new byte[size];
			busyRef = new byte[size];
			busyCand = new byte[size];
			dilateTmp = new byte[size];
		}
		if (lumaRowCur.length < imageWidth) {
			lumaRowCur = new int[imageWidth];
			lumaRowNext = new int[imageWidth];
		}
	}

	/**
	 * Marks pixels whose luma gradient to the right or downward neighbour
	 * reaches {@link #EDGE_GRADIENT_THRESHOLD}. Gradients crossing the region
	 * border still read image data, so nothing at the border is invented.
	 */
	private void computeEdges(ClusterDiffer.PixelReader reader, int minX, int minY,
			int maxX, int maxY, byte[] out) {
		int rw = maxX - minX + 1;
		fillLumaRow(reader, minY, lumaRowCur);
		for (int y = minY; y <= maxY; y++) {
			if (y + 1 < reader.height)
				fillLumaRow(reader, y + 1, lumaRowNext);
			else
				System.arraycopy(lumaRowCur, 0, lumaRowNext, 0, reader.width);
			int base = (y - minY) * rw - minX;
			for (int x = minX; x <= maxX; x++) {
				boolean edge = x + 1 < reader.width
						&& Math.abs(lumaRowCur[x] - lumaRowCur[x + 1]) >= EDGE_GRADIENT_THRESHOLD;
				if (!edge && y + 1 < reader.height)
					edge = Math.abs(lumaRowCur[x] - lumaRowNext[x]) >= EDGE_GRADIENT_THRESHOLD;
				out[base + x] = (byte) (edge ? 1 : 0);
			}
			int[] swap = lumaRowCur;
			lumaRowCur = lumaRowNext;
			lumaRowNext = swap;
		}
	}

	private void fillLumaRow(ClusterDiffer.PixelReader reader, int y, int[] target) {
		for (int x = 0; x < reader.width; x++)
			target[x] = ClusterDiffer.luma(reader.rgb(x, y));
	}

	/**
	 * Strict Dice coefficient of the two edge maps: 1 when they mark exactly
	 * the same pixels. A region where either image has no edges at all
	 * carries no geometric evidence, so the agreement is 0 and nothing is
	 * claimed.
	 */
	private double strictAgreement(int rw, int rh) {
		long intersection = 0;
		long totalRef = 0;
		long totalCand = 0;
		for (int i = 0; i < rw * rh; i++) {
			totalRef += edgeRef[i];
			totalCand += edgeCand[i];
			intersection += edgeRef[i] & edgeCand[i];
		}
		long denominator = totalRef + totalCand;
		return denominator == 0 ? 0.0 : 2.0 * intersection / denominator;
	}

	/**
	 * Correlation of the signed luma gradients between reference and
	 * candidate, over the region interior: the normalized dot product of all
	 * (gx, gy) pairs. Recolouring scales gradient magnitudes per pixel but
	 * keeps their direction, so the correlation stays near 1; displaced or
	 * redrawn strokes flip and shuffle directions, which collapses it.
	 * The absolute value keeps inverted colours from reading as geometry.
	 */
	private double gradientCorrelation(ClusterDiffer.PixelReader ra, ClusterDiffer.PixelReader rb,
			int minX, int minY, int maxX, int maxY) {
		int gw = maxX - minX;
		int gh = maxY - minY;
		if (gw <= 0 || gh <= 0)
			return 0.0;
		if (gradRefX.length < gw * gh) {
			gradRefX = new int[gw * gh];
			gradRefY = new int[gw * gh];
			gradCandX = new int[gw * gh];
			gradCandY = new int[gw * gh];
		}
		collectGradients(ra, minX, minY, maxX, maxY, gradRefX, gradRefY);
		collectGradients(rb, minX, minY, maxX, maxY, gradCandX, gradCandY);
		long dot = 0;
		long normRef = 0;
		long normCand = 0;
		for (int i = 0; i < gw * gh; i++) {
			long ax = gradRefX[i], ay = gradRefY[i];
			long bx = gradCandX[i], by = gradCandY[i];
			dot += ax * bx + ay * by;
			normRef += ax * ax + ay * ay;
			normCand += bx * bx + by * by;
		}
		if (normRef == 0 || normCand == 0)
			return 0.0;
		return Math.abs((double) dot / Math.sqrt((double) normRef * (double) normCand));
	}

	/** Signed luma gradients at every pixel whose right and lower neighbours exist. */
	private void collectGradients(ClusterDiffer.PixelReader reader, int minX, int minY,
			int maxX, int maxY, int[] outX, int[] outY) {
		fillLumaRow(reader, minY, lumaRowCur);
		for (int y = minY; y < maxY; y++) {
			fillLumaRow(reader, y + 1, lumaRowNext);
			int base = (y - minY) * (maxX - minX) - minX;
			for (int x = minX; x < maxX; x++) {
				outX[base + x] = lumaRowCur[x + 1] - lumaRowCur[x];
				outY[base + x] = lumaRowNext[x] - lumaRowCur[x];
			}
			int[] swap = lumaRowCur;
			lumaRowCur = lumaRowNext;
			lumaRowNext = swap;
		}
	}

	private void dilateOnce(byte[] source, int rw, int rh, byte[] target) {
		ClusterDiffer.horizontalDilate(source, dilateTmp, rw, rh);
		ClusterDiffer.verticalDilate(dilateTmp, target, rw, rh);
	}

	/**
	 * Share of changed pixels whose 5x5 neighbourhood carries dense edges in
	 * both images. Those are places where both renderings independently drew
	 * fine structure, the signature of differing glyphs rather than of an
	 * element appearing on featureless ground.
	 */
	private double busyConcentration(int[] deltas, int imageWidth, int channelTol,
			int minX, int minY, int maxX, int maxY, int rw) {
		long changed = 0;
		long busyBoth = 0;
		for (int y = minY; y <= maxY; y++) {
			int rowStart = (y - minY) * rw - minX;
			int imageRow = y * imageWidth;
			for (int x = minX; x <= maxX; x++) {
				if (deltas[imageRow + x] <= channelTol)
					continue;
				changed++;
				if (busyRef[rowStart + x] != 0 && busyCand[rowStart + x] != 0)
					busyBoth++;
			}
		}
		return changed == 0 ? 0.0 : (double) busyBoth / changed;
	}
}
