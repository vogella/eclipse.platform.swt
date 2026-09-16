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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.Differ;
import org.eclipse.swt.visualoracle.spi.DiffCluster;
import org.eclipse.swt.visualoracle.spi.DiffResult;
import org.eclipse.swt.visualoracle.spi.Tolerance;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * The comparison engine behind the frozen {@link Differ} interface.
 *
 * Per pixel the peak absolute channel delta is computed; a pixel counts as
 * changed when that delta exceeds {@link Tolerance#maxChannelDelta()}. Changed
 * pixels are grouped into connected regions (8-connected, gaps up to
 * {@link #CLOSE_RADIUS} bridged, so the halo around one conceptual change
 * forms one cluster instead of slivers) and reported with tight bounds,
 * ordered by significance.
 *
 * Verdict rule. EQUAL only for bit-identical images. Otherwise DIFFERENT when
 * either trigger fires: the changed fraction exceeds
 * {@link Tolerance#maxChangedFraction()}, or at least one cluster is
 * structural, meaning {@code changedPixels >= MIN_CLUSTER_PIXELS} and mean
 * peak delta >= {@link #MEAN_DELTA_STRUCTURAL}. Everything else is
 * WITHIN_TOLERANCE.
 *
 * Why two triggers. Neither tolerance parameter alone separates anti-aliasing
 * from a thin real defect: widespread AA disagreement covers more pixels than
 * any sane maxChangedFraction, while a missing icon covers far fewer than a
 * focus-ring-sized defect, yet one is normal and the other never is. The
 * separating signal is per-cluster mass, not per-pixel magnitude or raw share:
 * AA disagreement scatters into small low-magnitude clusters, while a removed
 * element forms one coherent strongly-changed region.
 *
 * Classification, implemented by {@link DefectClassifier}, names the
 * probable cause or abstains. Claims in descending specificity: SHIFTED when
 * a small translation explains the change; MISSING_ELEMENT when every
 * cluster carries element mass on one side only (interior colour means far
 * apart while the edge maps share almost nothing, so a solid recolouring
 * cannot read as a removal); WRONG_COLOR when the edge maps of both images
 * agree, so the geometry is intact and only the colours moved; WRONG_GLYPH
 * when the difference concentrates where both renderings carry dense
 * structure, which is where text lives, coherently rather than as scattered
 * halo. Everything else stays UNKNOWN: a confidently wrong class sends
 * someone to the wrong code, an abstention does not. A size mismatch is
 * DIFFERENT over the larger area with fraction 1.0, never an exception, and
 * deliberately stays UNKNOWN because the frozen enum has no constant that
 * says size mismatch.
 *
 * Alpha is ignored because captures are opaque. Instances are
 * not thread-safe; the harness calls a Differ from one thread.
 */
public class ClusterDiffer implements Differ {

	private final DefectClassifier defectClassifier = new DefectClassifier();


	/**
	 * Tolerance proposed for oracle runs until measured otherwise: channel
	 * deltas up to 8 are invisible jitter. The fraction sits above the worst
	 * anti-aliasing coverage measured on text-heavy widgets (edge pixels can
	 * exceed 10% of a small widget), while real widespread drift such as a
	 * theme tint covers the whole canvas and trips it.
	 */
	public static final Tolerance DEFAULT_TOLERANCE = new Tolerance(8, 0.5);

	/**
	 * Mean peak channel delta above which a cluster of at least
	 * {@link #MIN_CLUSTER_PIXELS} changed pixels is a structural defect.
	 * Calibrated between measured synthetic-AA cluster means (at most about
	 * 24) and thin-defect means (about 64 and up).
	 */
	public static final int MEAN_DELTA_STRUCTURAL = 32;

	/**
	 * Chebyshev radius bridged while grouping changed pixels. 1 closes the
	 * one-to-two pixel gaps that sub-pixel AA and small shifts leave between
	 * bands of the same change; larger radii start merging genuinely separate
	 * defects.
	 */
	public static final int CLOSE_RADIUS = 1;

	/** A cluster below this many changed pixels cannot cause DIFFERENT. */
	static final int MIN_CLUSTER_PIXELS = 3;

	/** Maximum number of clusters reported; dropped ones are least significant. */
	static final int MAX_CLUSTERS = 64;

	/** Reach of the translation search behind the SHIFTED classification. */
	static final int SHIFT_SEARCH_RADIUS = 2;

	/**
	 * Component count below which overlap merging runs; a mask fragmented
	 * beyond this is scattered noise, not fragments of one defect.
	 */
	static final int MAX_COMPONENTS_FOR_MERGE = 8192;

	private int[] deltas = new int[0];
	private byte[] mask = new byte[0];
	private byte[] dilateA = new byte[0];
	private byte[] dilateB = new byte[0];
	private int[] labels = new int[0];
	private int[] stack = new int[0];

	@Override
	public DiffResult compare(CapturedImage reference, CapturedImage candidate, Tolerance tolerance) {
		Objects.requireNonNull(tolerance, "tolerance");
		ImageData a = reference.imageData();
		ImageData b = candidate.imageData();
		if (a.width != b.width || a.height != b.height) {
			int w = Math.max(a.width, b.width);
			int h = Math.max(a.height, b.height);
			long area = (long) w * h;
			List<DiffCluster> clusters = List.of(new DiffCluster(0, 0, w, h, area));
			return new DiffResult(Verdict.DIFFERENT, area, 1.0, 255, clusters, DefectClass.UNKNOWN);
		}
		int w = a.width;
		int h = a.height;
		long total = (long) w * h;
		if (total == 0)
			return new DiffResult(Verdict.EQUAL, 0, 0.0, 0, List.of(), DefectClass.NONE);

		PixelReader ra = PixelReader.forImage(a);
		PixelReader rb = PixelReader.forImage(b);
		ensureCapacity(w, h);

		int channelTol = tolerance.maxChannelDelta();
		int maxDelta = 0;
		for (int y = 0, i = 0; y < h; y++) {
			for (int x = 0; x < w; x++, i++) {
				int peak = peakDelta(ra.rgb(x, y), rb.rgb(x, y));
				deltas[i] = peak;
				if (peak > maxDelta)
					maxDelta = peak;
			}
		}
		if (maxDelta == 0)
			return new DiffResult(Verdict.EQUAL, 0, 0.0, 0, List.of(), DefectClass.NONE);

		long changed = 0;
		for (int i = 0; i < w * h; i++) {
			boolean isChanged = deltas[i] > channelTol;
			mask[i] = (byte) (isChanged ? 1 : 0);
			if (isChanged)
				changed++;
		}

		List<RawCluster> clusters = findClusters(w, h, channelTol);

		boolean structural = false;
		for (RawCluster c : clusters) {
			if (c.count >= MIN_CLUSTER_PIXELS && c.sum / c.count >= MEAN_DELTA_STRUCTURAL) {
				structural = true;
				break;
			}
		}
		double fraction = (double) changed / total;
		Verdict verdict = fraction > tolerance.maxChangedFraction() || structural
				? Verdict.DIFFERENT
				: Verdict.WITHIN_TOLERANCE;

		clusters.sort(null);
		if (clusters.size() > MAX_CLUSTERS)
			clusters = new ArrayList<>(clusters.subList(0, MAX_CLUSTERS));
		List<DiffCluster> reported = new ArrayList<>(clusters.size());
		for (RawCluster c : clusters)
			reported.add(new DiffCluster(c.minX, c.minY, c.maxX - c.minX + 1, c.maxY - c.minY + 1, c.count));

		DefectClass defect = verdict == Verdict.DIFFERENT
				? classify(reported, ra, rb, w, h, channelTol)
				: DefectClass.NONE;
		return new DiffResult(verdict, changed, fraction, maxDelta, reported, defect);
	}

	private void ensureCapacity(int w, int h) {
		int size = w * h;
		if (deltas.length < size) {
			deltas = new int[size];
			mask = new byte[size];
			dilateA = new byte[size];
			dilateB = new byte[size];
			labels = new int[size];
			stack = new int[size];
		}
	}

	/**
	 * Groups changed pixels into clusters. The mask is dilated by
	 * {@link #CLOSE_RADIUS} via two separable passes, 8-connected dilated
	 * components are labelled, and components whose tight boxes touch are
	 * merged; both steps only ever reunite fragments of one conceptual change.
	 * Counts, sums and bounds always come from the undilated mask.
	 */
	private List<RawCluster> findClusters(int w, int h, int channelTol) {
		horizontalDilate(mask, dilateB, w, h);
		verticalDilate(dilateB, dilateA, w, h);

		Arrays.fill(labels, 0, w * h, 0);
		List<RawCluster> components = new ArrayList<>();
		int[] s = stack;
		for (int start = 0; start < w * h; start++) {
			if (dilateA[start] == 0 || labels[start] != 0)
				continue;
			RawCluster c = new RawCluster();
			int top = 0;
			s[top++] = start;
			labels[start] = 1;
			while (top > 0) {
				int idx = s[--top];
				int x = idx % w;
				int y = idx / w;
				if (deltas[idx] > channelTol) {
					c.count++;
					c.sum += deltas[idx];
					if (x < c.minX) c.minX = x;
					if (x > c.maxX) c.maxX = x;
					if (y < c.minY) c.minY = y;
					if (y > c.maxY) c.maxY = y;
				}
				int minY = y > 0 ? y - 1 : 0;
				int maxY = y < h - 1 ? y + 1 : h - 1;
				int minX = x > 0 ? x - 1 : 0;
				int maxX = x < w - 1 ? x + 1 : w - 1;
				for (int ny = minY; ny <= maxY; ny++) {
					int rowBase = ny * w;
					for (int nx = minX; nx <= maxX; nx++) {
						int nIdx = rowBase + nx;
						if (dilateA[nIdx] != 0 && labels[nIdx] == 0) {
							labels[nIdx] = 1;
							s[top++] = nIdx;
						}
					}
				}
			}
			components.add(c);
		}

		int n = components.size();
		if (n <= MAX_COMPONENTS_FOR_MERGE) {
			int[] parent = new int[n];
			for (int i = 0; i < n; i++)
				parent[i] = i;
			for (int i = 0; i < n; i++) {
				RawCluster ci = components.get(i);
				for (int j = i + 1; j < n; j++) {
					RawCluster cj = components.get(j);
					if (ci.minX <= cj.maxX && cj.minX <= ci.maxX && ci.minY <= cj.maxY && cj.minY <= ci.maxY)
						parent[findRoot(parent, i)] = findRoot(parent, j);
				}
			}
			RawCluster[] mergedByRoot = new RawCluster[n];
			for (int i = 0; i < n; i++)
				mergeInto(mergedByRoot, parent, i, components.get(i));
			List<RawCluster> merged = new ArrayList<>();
			for (RawCluster c : mergedByRoot)
				if (c != null)
					merged.add(c);
			return merged;
		}
		return components;
	}

	private static void mergeInto(RawCluster[] mergedByRoot, int[] parent, int i, RawCluster c) {
		int root = findRoot(parent, i);
		RawCluster target = mergedByRoot[root];
		if (target == null) {
			mergedByRoot[root] = c;
			return;
		}
		target.count += c.count;
		target.sum += c.sum;
		target.minX = Math.min(target.minX, c.minX);
		target.minY = Math.min(target.minY, c.minY);
		target.maxX = Math.max(target.maxX, c.maxX);
		target.maxY = Math.max(target.maxY, c.maxY);
	}

	static void horizontalDilate(byte[] src, byte[] dst, int w, int h) {
		for (int y = 0; y < h; y++) {
			int row = y * w;
			byte prev = 0;
			for (int x = 0; x < w; x++) {
				byte cur = src[row + x];
				dst[row + x] = (byte) (prev | cur | (x < w - 1 ? src[row + x + 1] : 0));
				prev = cur;
			}
		}
	}

	static void verticalDilate(byte[] src, byte[] dst, int w, int h) {
		System.arraycopy(src, 0, dst, 0, w);
		System.arraycopy(src, (h - 1) * w, dst, (h - 1) * w, w);
		for (int y = 1; y < h - 1; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++)
				dst[row + x] = (byte) (src[row - w + x] | src[row + x] | src[row + w + x]);
		}
	}

	private static int findRoot(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	/**
	 * SHIFTED is claimed here, where the translation search lives; the
	 * remaining classes are the interpretation layer's decision.
	 */
	private DefectClass classify(List<DiffCluster> clusters, PixelReader ra, PixelReader rb,
			int w, int h, int channelTol) {
		if (explainableByShift(clusters, ra, rb, w, h, channelTol))
			return DefectClass.SHIFTED;
		return defectClassifier.classify(clusters, ra, rb, deltas, w, h, channelTol);
	}

	private boolean explainableByShift(List<DiffCluster> clusters, PixelReader ra, PixelReader rb,
			int w, int h, int channelTol) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;
		for (DiffCluster c : clusters) {
			minX = Math.min(minX, c.x());
			minY = Math.min(minY, c.y());
			maxX = Math.max(maxX, c.x() + c.width() - 1);
			maxY = Math.max(maxY, c.y() + c.height() - 1);
		}
		int pad = SHIFT_SEARCH_RADIUS;
		minX = Math.max(minX - pad, 0);
		minY = Math.max(minY - pad, 0);
		maxX = Math.min(maxX + pad, w - 1);
		maxY = Math.min(maxY + pad, h - 1);
		for (int dy = -pad; dy <= pad; dy++) {
			for (int dx = -pad; dx <= pad; dx++) {
				if (dx == 0 && dy == 0)
					continue;
				if (shiftExplains(ra, rb, w, h, minX, minY, maxX, maxY, dx, dy, channelTol))
					return true;
			}
		}
		return false;
	}

	/**
	 * True when the given translation reproduces nearly every changed pixel
	 * and moves comparable content mass on both sides. Changed pixels within
	 * {@link #SHIFT_SEARCH_RADIUS} of an image edge are excluded: that is
	 * where content enters or leaves the canvas and no same-size comparison
	 * can represent the translation. The mass condition matters: a thin shape
	 * that was merely added also self-overlaps under small translations, but
	 * then only one side carries content, while a genuine shift leaves the
	 * element on both sides at different positions. Content means "far from
	 * the window background", estimated as the median border luma.
	 */
	private boolean shiftExplains(PixelReader ra, PixelReader rb, int w, int h,
			int minX, int minY, int maxX, int maxY, int dx, int dy, int channelTol) {
		int bgRef = backgroundLuma(ra);
		int bgCand = backgroundLuma(rb);
		long checked = 0;
		long matched = 0;
		long refContent = 0;
		long candContent = 0;
		int threshold = Math.max(24, channelTol * 3);
		int xLow = Math.max(minX, SHIFT_SEARCH_RADIUS);
		int xHigh = Math.min(maxX, w - 1 - SHIFT_SEARCH_RADIUS);
		int yLow = Math.max(minY, SHIFT_SEARCH_RADIUS);
		int yHigh = Math.min(maxY, h - 1 - SHIFT_SEARCH_RADIUS);
		for (int y = yLow; y <= yHigh; y++) {
			int sy = y + dy;
			if (sy < 0 || sy >= h)
				continue;
			for (int x = xLow; x <= xHigh; x++) {
				int sx = x + dx;
				if (sx < 0 || sx >= w)
					continue;
				if (deltas[y * w + x] <= channelTol)
					continue;
				checked++;
				int r = ra.rgb(x, y);
				int c = rb.rgb(sx, sy);
				if (peakDelta(r, c) <= channelTol)
					matched++;
				if (Math.abs(luma(r) - bgRef) > threshold)
					refContent++;
				if (Math.abs(luma(c) - bgCand) > threshold)
					candContent++;
			}
		}
		return checked >= MIN_CLUSTER_PIXELS
				&& matched * 25 >= checked * 24
				&& refContent * 5 >= checked * 2
				&& candContent * 5 >= checked * 2;
	}

	static int backgroundLuma(PixelReader reader) {
		int w = reader.width;
		int h = reader.height;
		long sum = 0;
		int count = 0;
		for (int x = 0; x < w; x += Math.max(1, w / 32)) {
			sum += luma(reader.rgb(x, 0)) + luma(reader.rgb(x, h - 1));
			count += 2;
		}
		return count == 0 ? 0 : (int) (sum / count);
	}

	static int luma(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (r * 299 + g * 587 + b * 114) / 1000;
	}

	private static int peakDelta(int ca, int cb) {
		int dr = Math.abs(((ca >> 16) & 0xFF) - ((cb >> 16) & 0xFF));
		int dg = Math.abs(((ca >> 8) & 0xFF) - ((cb >> 8) & 0xFF));
		int db = Math.abs((ca & 0xFF) - (cb & 0xFF));
		return Math.max(dr, Math.max(dg, db));
	}

	/**
	 * One component or merged group of changed pixels. Ordered by
	 * significance: more changed pixels first, then tighter concentration,
	 * then position.
	 */
	private static final class RawCluster implements Comparable<RawCluster> {
		int count;
		long sum;
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;

		@Override
		public int compareTo(RawCluster o) {
			if (count != o.count)
				return Long.compare(o.count, count);
			int area = (maxX - minX + 1) * (maxY - minY + 1);
			int otherArea = (o.maxX - o.minX + 1) * (o.maxY - o.minY + 1);
			if (area != otherArea)
				return Integer.compare(area, otherArea);
			if (minY != o.minY)
				return Integer.compare(minY, o.minY);
			return Integer.compare(minX, o.minX);
		}
	}

	/**
	 * Reads packed RGB pixels from one ImageData, decoding direct palettes
	 * inline and indexing indexed palettes through the palette table, so the
	 * compare loop allocates nothing per pixel. Package-private so the
	 * classifier can read pixels through the same decoder.
	 */
	static abstract class PixelReader {
		final int width;
		final int height;

		PixelReader(int width, int height) {
			this.width = width;
			this.height = height;
		}

		abstract int rgb(int x, int y);

		static PixelReader forImage(ImageData data) {
			PaletteData palette = data.palette;
			if (palette.isDirect)
				return new DirectReader(data, palette);
			return new IndexedReader(data, palette);
		}
	}

	private static final class DirectReader extends PixelReader {
		private final ImageData data;
		private final PaletteData palette;
		private final int redMask;
		private final int greenMask;
		private final int blueMask;
		private final int redShift;
		private final int greenShift;
		private final int blueShift;
		private final boolean inline;

		DirectReader(ImageData data, PaletteData palette) {
			super(data.width, data.height);
			this.data = data;
			this.palette = palette;
			this.redMask = palette.redMask;
			this.greenMask = palette.greenMask;
			this.blueMask = palette.blueMask;
			this.redShift = palette.redShift;
			this.greenShift = palette.greenShift;
			this.blueShift = palette.blueShift;
			this.inline = redShift >= 0 && greenShift >= 0 && blueShift >= 0
					&& redShift <= 31 && greenShift <= 31 && blueShift <= 31
					&& redMask != 0 && greenMask != 0 && blueMask != 0;
		}

		@Override
		int rgb(int x, int y) {
			int pixel = data.getPixel(x, y);
			if (!inline)
				return paletteRgb(pixel);
			int r = (pixel & redMask) >>> redShift;
			int g = (pixel & greenMask) >>> greenShift;
			int b = (pixel & blueMask) >>> blueShift;
			return (r << 16) | (g << 8) | b;
		}

		private int paletteRgb(int pixel) {
			RGB c = palette.getRGB(pixel);
			return c == null ? 0 : (c.red << 16) | (c.green << 8) | c.blue;
		}
	}

	private static final class IndexedReader extends PixelReader {
		private final ImageData data;
		private final RGB[] colors;

		IndexedReader(ImageData data, PaletteData palette) {
			super(data.width, data.height);
			this.data = data;
			this.colors = palette.colors;
		}

		@Override
		int rgb(int x, int y) {
			RGB c = colors[data.getPixel(x, y)];
			return (c.red << 16) | (c.green << 8) | c.blue;
		}
	}
}
