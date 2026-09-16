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
package org.eclipse.swt.visualoracle.tools;

import java.io.PrintStream;
import java.util.List;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.visualoracle.impl.BasicCapturedImage;
import org.eclipse.swt.visualoracle.impl.ClusterDiffer;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.DefectClass;
import org.eclipse.swt.visualoracle.spi.DiffCluster;
import org.eclipse.swt.visualoracle.spi.DiffResult;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * Selftest checks for the diff engine, plus the deterministic synthesis
 * of comparison pairs from one real capture, so no image files are needed.
 *
 * All variants are built by pixel arithmetic on a copy of a real capture;
 * every method here is pure Java and needs no Display.
 */
public final class DiffCheck {

	private DiffCheck() {
	}

	// ---------------------------------------------------------------- checks

	/**
	 * Identical images under the default tolerance: EQUAL, nothing reported.
	 */
	public static void checkEquality(CapturedImage capture, PrintStream out) {
		DiffResult result = new ClusterDiffer().compare(capture,
				new BasicCapturedImage(capture.imageData()), ClusterDiffer.DEFAULT_TOLERANCE);
		require(result.verdict() == Verdict.EQUAL, "verdict is " + result.verdict());
		require(result.changedPixels() == 0, "changedPixels is " + result.changedPixels());
		require(result.maxChannelDelta() == 0, "maxChannelDelta is " + result.maxChannelDelta());
		require(result.clusters().isEmpty(), "equal images produced clusters");
		require(result.probableClass() == DefectClass.NONE, "probableClass is " + result.probableClass());
	}

	/**
	 * An element present on one side only, whichever side that is: removing a
	 * rectangle from the candidate and adding one to it must both classify as
	 * MISSING_ELEMENT. The frozen enum constant is direction-neutral.
	 */
	public static void checkMissingElementClassified(CapturedImage capture, PrintStream out) {
		ImageData plain = capture.imageData();
		Rect square = new Rect(plain.width / 2 - 12, plain.height / 2 - 10, 24, 20);
		ImageData withSquare = fillRect(plain, square, contrastingColor(plain, square));

		DiffResult removed = compare(withSquare, plain);
		require(removed.verdict() == Verdict.DIFFERENT, "removed rectangle verdict is " + removed.verdict());
		require(removed.probableClass() == DefectClass.MISSING_ELEMENT,
				"removed rectangle classified as " + removed.probableClass());

		DiffResult added = compare(plain, withSquare);
		require(added.verdict() == Verdict.DIFFERENT, "added rectangle verdict is " + added.verdict());
		require(added.probableClass() == DefectClass.MISSING_ELEMENT,
				"added rectangle classified as " + added.probableClass());
		out.printf("      removed/added 24x20 rectangle: changed=%d, class=%s both directions%n",
				removed.changedPixels(), removed.probableClass());
	}

	/**
	 * The same geometry with different fill must classify as WRONG_COLOR,
	 * bounded to a region and spread over the whole canvas. Both variants
	 * darken uniformly so every luma gradient survives intact and only the
	 * colours move.
	 */
	public static void checkWrongColorClassified(CapturedImage capture, PrintStream out) {
		ImageData plain = capture.imageData();
		Rect region = new Rect(plain.width / 2 - 15, plain.height / 2 - 8, 30, 16);
		DiffResult bounded = compare(plain, tintRegion(plain, region, -48));
		require(bounded.verdict() == Verdict.DIFFERENT, "bounded recolour verdict is " + bounded.verdict());
		require(bounded.probableClass() == DefectClass.WRONG_COLOR,
				"bounded recolour classified as " + bounded.probableClass());

		DiffResult everywhere = compare(plain, tinted(plain, -16));
		require(everywhere.verdict() == Verdict.DIFFERENT, "global tint verdict is " + everywhere.verdict());
		require(everywhere.probableClass() == DefectClass.WRONG_COLOR,
				"global tint classified as " + everywhere.probableClass());
		out.printf("      recoloured fill and global tint: class=%s%n", bounded.probableClass());
	}

	/**
	 * Displaced strokes on dense structure, the signature of font
	 * substitution or metric differences, must classify as WRONG_GLYPH. The
	 * bars are stamped by pure pixel arithmetic, no GC and no Display.
	 */
	public static void checkWrongGlyphClassified(CapturedImage capture, PrintStream out) {
		ImageData base = capture.imageData();
		DiffResult result = compare(patternBars(base, false), patternBars(base, true));
		require(result.verdict() == Verdict.DIFFERENT, "displaced strokes verdict is " + result.verdict());
		require(result.probableClass() == DefectClass.WRONG_GLYPH,
				"displaced strokes classified as " + result.probableClass());
		out.printf("      displaced stroke bars: changed=%d, class=%s%n",
				result.changedPixels(), result.probableClass());
	}

	/**
	 * A size mismatch must stay one whole-area DIFFERENT rather than explode
	 * into many small defects, and stays UNKNOWN because the frozen enum has
	 * no constant that names a size mismatch.
	 */
	public static void checkSizeMismatchStaysWholeArea(CapturedImage capture) {
		ImageData data = capture.imageData();
		ImageData cropped = new ImageData(data.width - 24, data.height, data.depth, data.palette);
		copyRegion(data, cropped, 0, 0);
		DiffResult result = new ClusterDiffer().compare(capture, new BasicCapturedImage(cropped),
				ClusterDiffer.DEFAULT_TOLERANCE);
		require(result.verdict() == Verdict.DIFFERENT, "size mismatch verdict is " + result.verdict());
		require(result.changedFraction() == 1.0, "size mismatch fraction is " + result.changedFraction());
		require(result.clusters().size() == 1, "size mismatch should report one full-area cluster");
		require(result.probableClass() == DefectClass.UNKNOWN,
				"size mismatch classified as " + result.probableClass());
	}

	/**
	 * Abstention is an explicit outcome: a difference that matches no class
	 * cleanly must come out UNKNOWN rather than being forced into the
	 * nearest one. Two proofs: an element removed here while another is
	 * added there does not separate cleanly once the edge veto sees shared
	 * geometry, and anti-aliasing disagreement beyond the tolerance envelope
	 * is scattered halo, not glyphs, colours or a shift.
	 */
	public static void checkAmbiguousDifferenceAbstains(CapturedImage capture, PrintStream out) {
		ImageData plain = capture.imageData();
		Rect gone = new Rect(plain.width / 6 - 8, plain.height / 4 - 6, 16, 12);
		Rect appeared = new Rect(plain.width / 2 - 8, plain.height / 4 - 6, 16, 12);
		ImageData refSide = fillRect(plain, gone, contrastingColor(plain, gone));
		ImageData candSide = fillRect(plain, appeared, contrastingColor(plain, appeared));
		DiffResult mixed = compare(refSide, candSide);
		require(mixed.verdict() == Verdict.DIFFERENT, "mixed defects verdict is " + mixed.verdict());
		require(mixed.probableClass() == DefectClass.UNKNOWN,
				"mixed defects forced into class " + mixed.probableClass());

		DiffResult scattered = compareWithVariant(capture, antiAliased(plain, 48));
		require(scattered.verdict() == Verdict.DIFFERENT,
				"AA disagreement beyond the envelope verdict is " + scattered.verdict());
		require(scattered.probableClass() == DefectClass.UNKNOWN,
				"scattered AA disagreement forced into class " + scattered.probableClass());
		out.printf("      mixed defects and scattered AA: class=%s (abstained)%n",
				mixed.probableClass());
	}

	/**
	 * Edge-only perturbations up to the calibrated AA envelope stay within
	 * tolerance; the same perturbation beyond the envelope must be flagged,
	 * proving the excuse has a measured boundary rather than being blanket.
	 */
	public static void checkAntiAliasingWithinTolerance(CapturedImage capture, PrintStream out) {
		int[] excused = { 4, 8, 12, 16, 24 };
		for (int magnitude : excused) {
			DiffResult result = compareWithVariant(capture, antiAliased(capture.imageData(), magnitude));
			require(result.verdict() == Verdict.WITHIN_TOLERANCE,
					"AA magnitude " + magnitude + " gave " + result.verdict()
							+ " (changed=" + result.changedPixels() + ", fraction=" + result.changedFraction()
							+ ", clusters=" + result.clusters().size() + ")");
			out.printf("      aa magnitude %2d: %s, changed=%d, clusters=%d%n",
					magnitude, result.verdict(), result.changedPixels(), result.clusters().size());
		}
		int[] flagged = { 40, 64 };
		for (int magnitude : flagged) {
			DiffResult result = compareWithVariant(capture, antiAliased(capture.imageData(), magnitude));
			require(result.verdict() == Verdict.DIFFERENT,
					"AA magnitude " + magnitude + " should exceed the envelope but gave "
							+ result.verdict());
			out.printf("      aa magnitude %2d: %s (beyond envelope)%n", magnitude, result.verdict());
		}
	}

	/**
	 * A uniform darkening over every pixel is never anti-aliasing; this
	 * variant trips the fraction trigger with per-pixel deltas below
	 * {@link ClusterDiffer#MEAN_DELTA_STRUCTURAL}, so it also proves that
	 * trigger works on its own. Darkening instead of brightening keeps every
	 * delta intact on near-white widget faces, where brightening would clamp.
	 */
	public static void checkGlobalTintDetected(CapturedImage capture) {
		DiffResult result = compareWithVariant(capture, tinted(capture.imageData(), -16));
		require(result.verdict() == Verdict.DIFFERENT, "global tint verdict is " + result.verdict());
	}

	/**
	 * A missing one-pixel ring: thin and edge-hugging like anti-aliasing, but
	 * high-contrast. Must be DIFFERENT with exactly one cluster whose bounds
	 * are exactly the ring.
	 */
	public static void checkThinRingDefect(CapturedImage capture, PrintStream out) {
		ImageData source = capture.imageData();
		int insetX = Math.max(8, source.width / 7);
		int insetY = Math.max(4, source.height / 5);
		int x = insetX;
		int y = insetY;
		int w = source.width - 2 * insetX;
		int h = source.height - 2 * insetY;
		long expectedRingPixels = 2L * w + 2L * h - 4;
		DiffResult result = compare(drawRing(source, x, y, w, h), source);
		require(result.verdict() == Verdict.DIFFERENT, "missing ring verdict is " + result.verdict());
		require(result.clusters().size() == 1,
				"expected one cluster for one ring, got " + result.clusters().size()
						+ ": " + result.clusters());
		DiffCluster cluster = result.clusters().get(0);
		require(cluster.x() == x && cluster.y() == y && cluster.width() == w && cluster.height() == h,
				"cluster " + cluster + " does not bound the ring at " + x + "," + y + " " + w + "x" + h);
		require(result.changedPixels() >= expectedRingPixels * 9 / 10,
				"changedPixels " + result.changedPixels() + " below ring area " + expectedRingPixels);
		out.printf("      missing ring: changed=%d of %d ring pixels, class=%s%n",
				result.changedPixels(), expectedRingPixels, result.probableClass());
	}

	/**
	 * An element present in the reference but absent in the candidate: must
	 * be DIFFERENT with exactly one cluster whose bounds are exactly the
	 * defect square.
	 */
	public static void checkRemovedSquareBoundedByClusters(CapturedImage capture, PrintStream out) {
		ImageData plain = capture.imageData();
		Rect square = new Rect(plain.width / 2 - 12, plain.height / 2 - 10, 24, 20);
		ImageData withSquare = fillRect(plain, square, contrastingColor(plain, square));
		DiffResult result = compare(withSquare, plain);
		require(result.verdict() == Verdict.DIFFERENT, "removed square verdict is " + result.verdict());
		require(result.clusters().size() == 1,
				"expected one cluster for one removed square, got " + result.clusters());
		DiffCluster cluster = result.clusters().get(0);
		require(cluster.x() == square.x() && cluster.y() == square.y()
				&& cluster.width() == square.w() && cluster.height() == square.h(),
				"cluster " + cluster + " does not equal the removed square " + square);
		out.printf("      removed square: changed=%d of %d square pixels, class=%s%n",
				result.changedPixels(), (long) square.w() * square.h(), result.probableClass());
	}

	/** Whole-image translation: DIFFERENT and classified SHIFTED. */
	public static void checkShiftedContentClassified(CapturedImage capture) {
		DiffResult result = compareWithVariant(capture, shifted(capture.imageData(), 2, 0));
		require(result.verdict() == Verdict.DIFFERENT, "shifted image verdict is " + result.verdict());
		require(result.probableClass() == DefectClass.SHIFTED,
				"shifted image classified as " + result.probableClass());
	}

	/** Two separated defects produce two clusters that each bound one defect. */
	public static void checkTwoDefectsTwoClusters(CapturedImage capture) {
		ImageData plain = capture.imageData();
		Rect left = new Rect(plain.width / 4 - 10, plain.height / 2 - 10, 20, 20);
		Rect right = new Rect(3 * plain.width / 4 - 10, plain.height / 2 - 10, 20, 20);
		if (right.x() - (left.x() + left.w()) < 10)
			throw new AssertionError("test squares not separated enough");
		ImageData both = fillRect(fillRect(plain, left, contrastingColor(plain, left)),
				right, contrastingColor(plain, right));
		DiffResult result = compare(both, plain);
		require(result.verdict() == Verdict.DIFFERENT, "two defects verdict is " + result.verdict());
		List<DiffCluster> clusters = result.clusters();
		require(clusters.size() >= 2, "expected at least two clusters, got " + clusters.size());
		Rect leftBound = expanded(left, ClusterDiffer.CLOSE_RADIUS + 1);
		Rect rightBound = expanded(right, ClusterDiffer.CLOSE_RADIUS + 1);
		boolean leftCovered = false;
		boolean rightCovered = false;
		for (DiffCluster c : clusters) {
			boolean inLeft = contains(leftBound, c);
			boolean inRight = contains(rightBound, c);
			require(inLeft || inRight, "cluster outside both defect regions: " + c);
			if (inLeft)
				leftCovered = true;
			if (inRight)
				rightCovered = true;
		}
		require(leftCovered && rightCovered, "a defect region has no cluster of its own: " + clusters);
		for (int i = 1; i < clusters.size(); i++)
			require(clusters.get(i - 1).changedPixels() >= clusters.get(i).changedPixels(),
					"clusters not ordered by significance: " + clusters);
	}

	/** Size mismatch is DIFFERENT over the whole larger area, never an exception. */
	public static void checkSizeMismatchWholeArea(CapturedImage capture) {
		ImageData data = capture.imageData();
		ImageData cropped = new ImageData(data.width - 24, data.height, data.depth, data.palette);
		copyRegion(data, cropped, 0, 0);
		DiffResult result = new ClusterDiffer().compare(capture, new BasicCapturedImage(cropped),
				ClusterDiffer.DEFAULT_TOLERANCE);
		require(result.verdict() == Verdict.DIFFERENT, "size mismatch verdict is " + result.verdict());
		require(result.changedFraction() == 1.0, "size mismatch fraction is " + result.changedFraction());
		require(result.changedPixels() == (long) data.width * data.height,
				"size mismatch changedPixels is " + result.changedPixels());
		require(result.clusters().size() == 1, "size mismatch should report one full-area cluster");
		DiffCluster cluster = result.clusters().get(0);
		require(cluster.x() == 0 && cluster.y() == 0 && cluster.width() == data.width
				&& cluster.height() == data.height,
				"size mismatch cluster does not span the larger area: " + cluster);
	}

	/**
	 * Measures sustained diff throughput on a synthetic one-megapixel pair
	 * and asserts a floor far above the one-second-per-pair failure mode the
	 * catalog cannot afford. Prints ms per pair and megapixels per second.
	 */
	public static void checkThroughput(PrintStream out) {
		ImageData base = syntheticPattern(1000, 1000);
		ImageData noisy = antiAliased(base, 12);
		CapturedImage a = new BasicCapturedImage(base);
		CapturedImage b = new BasicCapturedImage(noisy);
		ClusterDiffer differ = new ClusterDiffer();
		for (int warmup = 0; warmup < 3; warmup++)
			differ.compare(a, b, ClusterDiffer.DEFAULT_TOLERANCE);
		int runs = 30;
		long bestNanos = Long.MAX_VALUE;
		for (int i = 0; i < runs; i++) {
			long start = System.nanoTime();
			differ.compare(a, b, ClusterDiffer.DEFAULT_TOLERANCE);
			bestNanos = Math.min(bestNanos, System.nanoTime() - start);
		}
		double millisPerPair = bestNanos / 1_000_000.0;
		double megapixelsPerSecond = base.width * (double) base.height / (millisPerPair / 1000.0) / 1_000_000;
		out.printf("      throughput: %.2f ms per 1 MP pair, %.0f MP/s (best of %d)%n",
				millisPerPair, megapixelsPerSecond, runs);
		require(megapixelsPerSecond >= 20,
				"diff throughput " + megapixelsPerSecond + " MP/s below the 20 MP/s floor");
	}

	// ------------------------------------------------------------- synthesis

	private static DiffResult compareWithVariant(CapturedImage capture, ImageData variant) {
		return compare(capture.imageData(), variant);
	}

	private static DiffResult compare(ImageData reference, ImageData candidate) {
		return new ClusterDiffer().compare(new BasicCapturedImage(reference),
				new BasicCapturedImage(candidate), ClusterDiffer.DEFAULT_TOLERANCE);
	}

	/**
	 * Perturbs edge pixels (local contrast of at least 24 to any neighbour)
	 * by a deterministic signed offset of the given magnitude per channel,
	 * mimicking two rasterizers disagreeing about coverage.
	 */
	static ImageData antiAliased(ImageData source, int magnitude) {
		ImageData out = copy(source);
		for (int y = 0; y < source.height; y++) {
			for (int x = 0; x < source.width; x++) {
				if (localContrast(source, x, y) < 24)
					continue;
				long hash = mix(x, y);
				RGB c = rgbAt(source, x, y);
				int r = clamp(c.red + sign(hash, 0) * magnitude);
				int g = clamp(c.green + sign(hash, 1) * magnitude);
				int b = clamp(c.blue + sign(hash, 2) * magnitude);
				setRgb(out, x, y, r, g, b);
			}
		}
		return out;
	}

	/** Adds delta to every channel of every pixel, clamped. */
	static ImageData tinted(ImageData source, int delta) {
		return tintRegion(source, new Rect(0, 0, source.width, source.height), delta);
	}

	/** Adds delta to every channel inside the rect, clamped. */
	static ImageData tintRegion(ImageData source, Rect rect, int delta) {
		ImageData out = copy(source);
		for (int y = rect.y(); y < rect.y() + rect.h(); y++)
			for (int x = rect.x(); x < rect.x() + rect.w(); x++) {
				RGB c = rgbAt(source, x, y);
				setRgb(out, x, y, clamp(c.red + delta), clamp(c.green + delta), clamp(c.blue + delta));
			}
		return out;
	}

	/**
	 * Clears a box and stamps vertical glyph-like bars; pure pixel arithmetic,
	 * no GC and no Display. The displaced variant shifts each bar by two
	 * pixels and thickens it by one, the signature of font substitution:
	 * strokes move slightly while both sides stay dense.
	 */
	static ImageData patternBars(ImageData source, boolean displaced) {
		ImageData out = copy(source);
		int x0 = source.width / 6;
		int y0 = source.height / 3;
		int bw = 2 * source.width / 3;
		int bh = Math.max(8, source.height / 3);
		RGB bg = rgbAt(source, source.width / 2, 2);
		for (int y = y0; y < y0 + bh && y < source.height; y++)
			for (int x = x0; x < x0 + bw && x < source.width; x++)
				setRgb(out, x, y, bg);
		RGB ink = new RGB(clamp(bg.red - 120), clamp(bg.green - 120), clamp(bg.blue - 120));
		int spacing = (bw - 12) / 7;
		for (int i = 0; i < 7; i++) {
			int bx = x0 + 4 + i * spacing + (displaced ? (i % 2 == 0 ? -2 : 2) : 0);
			int barWidth = 5 + (displaced ? 1 : 0);
			for (int y = y0 + 1; y < y0 + bh - 1 && y < source.height; y++)
				for (int x = bx; x < bx + barWidth && x < x0 + bw - 1 && x < source.width; x++)
					setRgb(out, x, y, ink);
		}
		return out;
	}

	static ImageData drawRing(ImageData source, int x, int y, int w, int h) {
		ImageData out = copy(source);
		RGB color = inverted(rgbAt(source, x + w / 2, y + h / 2));
		for (int i = 0; i < w; i++) {
			setRgb(out, x + i, y, color);
			setRgb(out, x + i, y + h - 1, color);
		}
		for (int j = 0; j < h; j++) {
			setRgb(out, x, y + j, color);
			setRgb(out, x + w - 1, y + j, color);
		}
		return out;
	}

	static ImageData fillRect(ImageData source, Rect rect, RGB color) {
		ImageData out = copy(source);
		for (int y = rect.y(); y < rect.y() + rect.h(); y++)
			for (int x = rect.x(); x < rect.x() + rect.w(); x++)
				setRgb(out, x, y, color);
		return out;
	}

	/** Shifts content left/up by (dx, dy); exposed strips repeat the edge. */
	static ImageData shifted(ImageData source, int dx, int dy) {
		ImageData out = copy(source);
		for (int y = 0; y < source.height; y++) {
			int sy = Math.min(y + dy, source.height - 1);
			for (int x = 0; x < source.width; x++) {
				int sx = Math.min(x + dx, source.width - 1);
				setRgb(out, x, y, rgbAt(source, sx, sy));
			}
		}
		return out;
	}

	static void copyRegion(ImageData source, ImageData target, int offsetX, int offsetY) {
		for (int y = 0; y < target.height; y++)
			for (int x = 0; x < target.width; x++)
				setRgb(target, x, y, rgbAt(source, x + offsetX, y + offsetY));
	}

	/** Deterministic pattern for the throughput benchmark, glyph-like blobs included. */
	private static ImageData syntheticPattern(int w, int h) {
		PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
		ImageData data = new ImageData(w, h, 24, palette);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int r = (x * 255) / w;
				int g = (y * 255) / h;
				setRgb(data, x, y, r, g, (r + g) / 2);
			}
		}
		for (int row = 0; row < 10; row++) {
			for (int col = 0; col < 14; col++) {
				int bx = 20 + col * 70;
				int by = 15 + row * 95;
				drawBlob(data, bx, by, 40, 60, (row * 31 + col * 17) % 256);
			}
		}
		return data;
	}

	private static void drawBlob(ImageData data, int x, int y, int w, int h, int shade) {
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				boolean ink = (i % 9) < 5 || (j % 11) < 4;
				if (!ink || x + i >= data.width || y + j >= data.height)
					continue;
				setRgb(data, x + i, y + j, shade, shade, shade);
			}
		}
	}

	private static int localContrast(ImageData data, int x, int y) {
		int center = packed(data, x, y);
		int result = 0;
		result = Math.max(result, channelPeak(center, packedOrNull(data, x - 1, y)));
		result = Math.max(result, channelPeak(center, packedOrNull(data, x + 1, y)));
		result = Math.max(result, channelPeak(center, packedOrNull(data, x, y - 1)));
		result = Math.max(result, channelPeak(center, packedOrNull(data, x, y + 1)));
		return result == Integer.MIN_VALUE ? 0 : result;
	}

	private static int channelPeak(int a, int b) {
		if (b == Integer.MIN_VALUE)
			return Integer.MIN_VALUE;
		int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
		int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
		int db = Math.abs((a & 0xFF) - (b & 0xFF));
		return Math.max(dr, Math.max(dg, db));
	}

	private static int packedOrNull(ImageData data, int x, int y) {
		if (x < 0 || y < 0 || x >= data.width || y >= data.height)
			return Integer.MIN_VALUE;
		return packed(data, x, y);
	}

	private static int packed(ImageData data, int x, int y) {
		RGB c = data.palette.getRGB(data.getPixel(x, y));
		return (c.red << 16) | (c.green << 8) | c.blue;
	}

	private static long mix(int x, int y) {
		long h = (x * 73856093L) ^ (y * 19349663L);
		h ^= h >>> 17;
		h *= 0x9E3779B97F4A7C15L;
		h ^= h >>> 29;
		return h;
	}

	private static int sign(long hash, int channel) {
		return ((hash >>> (channel * 13)) & 1) == 0 ? -1 : 1;
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : v > 255 ? 255 : v;
	}

	private static RGB contrastingColor(ImageData data, Rect rect) {
		RGB center = rgbAt(data, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2);
		return inverted(center);
	}

	private static RGB inverted(RGB c) {
		return new RGB(255 - c.red, 255 - c.green, 255 - c.blue);
	}

	private static Rect expanded(Rect r, int margin) {
		return new Rect(r.x() - margin, r.y() - margin, r.w() + 2 * margin, r.h() + 2 * margin);
	}

	private static boolean contains(Rect r, DiffCluster c) {
		return r.x() <= c.x() && r.y() <= c.y()
				&& c.x() + c.width() <= r.x() + r.w()
				&& c.y() + c.height() <= r.y() + r.h();
	}

	private static ImageData copy(ImageData source) {
		return (ImageData) source.clone();
	}

	private static RGB rgbAt(ImageData data, int x, int y) {
		return data.palette.getRGB(data.getPixel(x, y));
	}

	private static void setRgb(ImageData data, int x, int y, RGB c) {
		data.setPixel(x, y, data.palette.getPixel(new RGB(c.red, c.green, c.blue)));
	}

	private static void setRgb(ImageData data, int x, int y, int r, int g, int b) {
		data.setPixel(x, y, data.palette.getPixel(new RGB(r, g, b)));
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private record Rect(int x, int y, int w, int h) {
	}
}
