/*******************************************************************************
 * Copyright (c) 2026 Lars Vogel <Lars.Vogel@vogella.com> and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.win32.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Automated Tests measuring where the win32 GC places its ink at fractional
 * zoom levels. Known geometry is drawn into an image and read back as
 * ImageData, so the assertions document the current coordinate drift while
 * staying valid once the drawing operations become fractionally precise.
 *
 * @see org.eclipse.swt.graphics.GC
 */
@SuppressWarnings("restriction")
public class GCPrecisionTests {

	/** Worst-case absolute coordinate drift, in device pixels, for cases that keep
	 * snapping or whose edges legitimately round to the nearest pixel: with smoothing
	 * off a fractional coordinate changes which pixel boundary an edge falls on, not
	 * its softness, so it can still land up to half a pixel from the exact position. */
	private static final int MAX_COORDINATE_DRIFT_PIXELS = 1;

	private static final String REPORT_PROPERTY = "swt.test.gcprecision.report";
	private static final boolean REPORTING = System.getProperty(REPORT_PROPERTY) != null;

	private static final int TICK_COUNT = 8;
	private static final int TICK_PITCH_POINTS = 7;
	private static final int TICK_WIDTH_POINTS = 3;
	private static final int TICK_HEIGHT_POINTS = 12;
	private static final int TICK_Y_POINTS = 20;

	// Two probes of one converted shape whose device positions carry different
	// fractional parts at every zoom under test (1.25/2.5px at 125%, 7.5/9px at
	// 150%, 8.75/10.5px at 175%). At 100% and 200% every point coordinate maps
	// to a whole device pixel, so no sub-pixel phase exists and the comparison
	// is excluded at those zooms.
	private static final int PHASE_BASE_X_POINTS = 4;
	private static final int PHASE_OFFSET_A_POINTS = 1;
	private static final int PHASE_OFFSET_B_POINTS = 2;
	private static final int PHASE_OVAL_WIDTH_POINTS = 16;
	private static final int PHASE_OVAL_HEIGHT_POINTS = 12;
	private static final int PHASE_Y_POINTS = 8;
	private static final int PHASE_IMAGE_WIDTH_POINTS = 40;
	private static final int PHASE_IMAGE_HEIGHT_POINTS = 30;

	// 21pt is 31.5px at 150%: the unconverted path rounds that height to 32 and
	// thereby shifts whole interior scanlines, which per-row extents measure.
	private static final int SCANLINE_OVAL_X_POINTS = 10;
	private static final int SCANLINE_OVAL_Y_POINTS = 10;
	private static final int SCANLINE_OVAL_WIDTH_POINTS = 30;
	private static final int SCANLINE_OVAL_HEIGHT_POINTS = 21;

	/** Rows where a pixel centre sits exactly on the predicted curve are decided by
	 * the rasteriser's tie-breaking, not by geometry precision, and are excluded. */
	private static final double CURVE_TIE_EPSILON = 1e-6;

	private static final RGB BLACK = new RGB(0, 0, 0);
	private static final RGB RED = new RGB(255, 0, 0);
	private static final RGB BLUE = new RGB(0, 0, 255);

	private Display display;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testStrokeLineEndpointAccuracy(int zoom) {
		assertLineTerminatesAtScaledEndPoint("diagonalLine", zoom, 0, 0, 100, 100);
		assertLineTerminatesAtScaledEndPoint("fractionalLine", zoom, 33, 47, 101, 103);
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testFillEdgeEndpointAccuracy(int zoom) {
		// stays within the snapped drift bound because FillRectangle deliberately
		// keeps snapping (row backgrounds and selection highlights)
		int x = 65;
		int y = 20;
		int width = 36;
		int height = 60;
		ImageData imageData = render(120, 100, zoom, gc -> {
			gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			gc.fillRectangle(x, y, width, height);
		});
		float scaleFactor = zoom / 100.0f;
		int[] bounds = requireColorBounds("filledRectangle", zoom, imageData, RED, 0, imageData.height - 1);
		// a fill covers columns [left, right) and rows [top, bottom)
		assertPositionWithinDrift("filledRectangle.left", zoom, bounds[0], x * scaleFactor);
		assertPositionWithinDrift("filledRectangle.top", zoom, bounds[1], y * scaleFactor);
		assertPositionWithinDrift("filledRectangle.right", zoom, bounds[2] + 1, (x + width) * scaleFactor);
		assertPositionWithinDrift("filledRectangle.bottom", zoom, bounds[3] + 1, (y + height) * scaleFactor);
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testFillSeamFreedom(int zoom) {
		assertFilledRectanglesShareEdgeWithoutGapOrOverlap("originSeam", zoom, 0, 33, 47, 15, 30);
		assertFilledRectanglesShareEdgeWithoutGapOrOverlap("offsetSeam", zoom, 20, 33, 47, 15, 30);
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testStrokeSeamFreedom(int zoom) {
		// stays within the snapped drift bound because a thin axis-aligned
		// drawRectangle keeps snapping to protect hairline borders
		assertStrokedRectanglesShareEdgeWithoutGap("originSeam", zoom, 0, 33, 47, 15, 30);
		assertStrokedRectanglesShareEdgeWithoutGap("offsetSeam", zoom, 20, 33, 47, 15, 30);
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testFillUniformSpacing(int zoom) {
		// ticks drawn with FillRectangle keep the snapped drift bound because
		// FillRectangle deliberately keeps snapping
		ImageData imageData = render(60, 40, zoom, gc -> {
			gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			drawTickRow(gc, true);
		});
		assertTickSpacing("filledTicks", zoom, imageData, RED, true);
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testStrokeUniformSpacing(int zoom) {
		// ticks drawn with a thin axis-aligned drawRectangle keep the snapped drift
		// bound because thin outlines deliberately keep snapping
		ImageData imageData = render(60, 40, zoom, gc -> {
			gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
			drawTickRow(gc, false);
		});
		assertTickSpacing("strokedTicks", zoom, imageData, BLACK, false);
	}

	@ParameterizedTest
	@ValueSource(ints = { 125, 150, 175 })
	public void testConvertedShapeSubPixelPhaseSensitivity(int zoom) {
		// With antialiasing on, a converted shape sits at a genuine sub-pixel phase,
		// so renderings at positions with different fractional parts cannot be mapped
		// onto each other by a whole-pixel shift. Before the conversion both offsets
		// were rounded to whole pixels first and the second rendering was exactly the
		// first one translated (by round(2*s) - round(1*s)), which this assertion
		// rejects.
		List<byte[]> coverages = new ArrayList<>();
		for (int offset : new int[] { PHASE_OFFSET_A_POINTS, PHASE_OFFSET_B_POINTS }) {
			ImageData imageData = render(PHASE_IMAGE_WIDTH_POINTS, PHASE_IMAGE_HEIGHT_POINTS, zoom, gc -> {
				gc.setAntialias(SWT.ON);
				gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
				gc.fillOval(PHASE_BASE_X_POINTS + offset, PHASE_Y_POINTS, PHASE_OVAL_WIDTH_POINTS,
						PHASE_OVAL_HEIGHT_POINTS);
			});
			coverages.add(redCoverage(imageData));
		}
		int widthPx = Math.round(PHASE_IMAGE_WIDTH_POINTS * zoom / 100.0f);
		int heightPx = Math.round(PHASE_IMAGE_HEIGHT_POINTS * zoom / 100.0f);
		int shift = translationAligning(coverages.get(1), coverages.get(0), widthPx, heightPx);
		report("subPixelPhaseSensitivity zoom=" + zoom + ": aligningShift=" + shift);
		assertTrue(shift < 0, "zoom=" + zoom + ": the renderings at 1pt and 2pt are translations of one another "
				+ "(shift " + shift + " px), so the converted shape carries no sub-pixel information");
	}

	@ParameterizedTest
	@ValueSource(ints = { 125, 150, 175 })
	public void testConvertedOvalInteriorScanlineExtents(int zoom) {
		// With antialiasing off every edge still quantises to whole pixels, so the
		// ink bounding box cannot tell the paths apart. Interior scanlines can: the
		// rendering must deviate from the unconverted path's snapped-bounds
		// prediction in at least one decisive row. Reproducing the exact-bounds
		// model row for row is deliberately not required, so a rasteriser deviating
		// in some row cannot fail the test for modelling reasons.
		float scaleFactor = zoom / 100.0f;
		int widthPx = Math.round(60 * scaleFactor);
		int heightPx = Math.round(40 * scaleFactor);
		Set<Integer> indeterminateRows = new HashSet<>();
		float[] exactRect = new float[] { SCANLINE_OVAL_X_POINTS * scaleFactor, SCANLINE_OVAL_Y_POINTS * scaleFactor,
				SCANLINE_OVAL_WIDTH_POINTS * scaleFactor, SCANLINE_OVAL_HEIGHT_POINTS * scaleFactor };
		List<String> exactPrediction = formatExtents(predictedExtents(exactRect, widthPx, heightPx, indeterminateRows));
		float[] snappedRect = snappedDeviceRectangle(SCANLINE_OVAL_X_POINTS, SCANLINE_OVAL_Y_POINTS,
				SCANLINE_OVAL_WIDTH_POINTS, SCANLINE_OVAL_HEIGHT_POINTS, scaleFactor);
		int[][] snappedExtents = predictedExtents(snappedRect, widthPx, heightPx, indeterminateRows);
		List<String> snappedPrediction = formatExtents(snappedExtents);
		assertFalse(exactPrediction.equals(snappedPrediction),
				"zoom=" + zoom + ": this geometry cannot distinguish the converted path from the snapped one");
		ImageData imageData = render(60, 40, zoom, gc -> {
			gc.setAntialias(SWT.OFF);
			gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			gc.fillOval(SCANLINE_OVAL_X_POINTS, SCANLINE_OVAL_Y_POINTS, SCANLINE_OVAL_WIDTH_POINTS,
					SCANLINE_OVAL_HEIGHT_POINTS);
		});
		int[][] measured = measuredExtents(imageData, RED, indeterminateRows);
		List<String> deviations = deviationsFromSnapped(measured, snappedExtents, indeterminateRows);
		report("ovalInteriorScanlines zoom=" + zoom + ": snappedPrediction=" + snappedPrediction);
		report("ovalInteriorScanlines zoom=" + zoom + ": measured=" + formatExtents(measured));
		report("ovalInteriorScanlines zoom=" + zoom + ": rowsDeviatingFromSnapped=" + deviations.size() + " "
				+ deviations);
		assertFalse(deviations.isEmpty(), "zoom=" + zoom + ": every decisive row reproduces the unconverted "
				+ "snapped-bounds prediction, the fill carries no trace of the conversion");
	}

	private interface GeometryDrawer {
		void draw(GC gc);
	}

	private ImageData render(int widthInPoints, int heightInPoints, int zoom, GeometryDrawer drawer) {
		Image image = new Image(display, widthInPoints, heightInPoints);
		try {
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
				gc.fillRectangle(0, 0, widthInPoints, heightInPoints);
				drawer.draw(gc);
				// requesting image data at a new zoom replays all recorded operations at that zoom
				return image.getImageData(zoom);
			} finally {
				gc.dispose();
			}
		} finally {
			image.dispose();
		}
	}

	private void drawTickRow(GC gc, boolean filled) {
		for (int k = 0; k < TICK_COUNT; k++) {
			int x = k * TICK_PITCH_POINTS;
			if (filled) {
				gc.fillRectangle(x, TICK_Y_POINTS, TICK_WIDTH_POINTS, TICK_HEIGHT_POINTS);
			} else {
				gc.drawRectangle(x, TICK_Y_POINTS, TICK_WIDTH_POINTS, TICK_HEIGHT_POINTS);
			}
		}
	}

	private void assertLineTerminatesAtScaledEndPoint(String caseName, int zoom, int x1, int y1, int x2, int y2) {
		ImageData imageData = render(120, 120, zoom, gc -> {
			gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
			gc.drawLine(x1, y1, x2, y2);
		});
		float scaleFactor = zoom / 100.0f;
		List<int[]> inkedPixels = collectPixels(imageData, BLACK);
		assertFalse(inkedPixels.isEmpty(), caseName + " zoom=" + zoom + ": no ink was drawn");
		report(caseName + " zoom=" + zoom + ": inkedPixels=" + inkedPixels.size());
		double idealStartX = x1 * scaleFactor;
		double idealStartY = y1 * scaleFactor;
		double idealEndX = x2 * scaleFactor;
		double idealEndY = y2 * scaleFactor;
		assertNearestInkWithinDrift(caseName + ".start", zoom, inkedPixels, idealStartX, idealStartY);
		assertNearestInkWithinDrift(caseName + ".end", zoom, inkedPixels, idealEndX, idealEndY);
		for (int[] pixel : inkedPixels) {
			boolean withinExtent = pixel[0] >= Math.min(idealStartX, idealEndX) - MAX_COORDINATE_DRIFT_PIXELS
					&& pixel[0] <= Math.max(idealStartX, idealEndX) + MAX_COORDINATE_DRIFT_PIXELS
					&& pixel[1] >= Math.min(idealStartY, idealEndY) - MAX_COORDINATE_DRIFT_PIXELS
					&& pixel[1] <= Math.max(idealStartY, idealEndY) + MAX_COORDINATE_DRIFT_PIXELS;
			assertTrue(withinExtent, caseName + " zoom=" + zoom + ": ink at (" + pixel[0] + ", " + pixel[1]
					+ ") lies beyond the line's ideal extent");
		}
	}

	private void assertNearestInkWithinDrift(String what, int zoom, List<int[]> inkedPixels, double idealX,
			double idealY) {
		int[] nearest = null;
		double nearestDrift = Double.MAX_VALUE;
		for (int[] pixel : inkedPixels) {
			double drift = Math.max(Math.abs(pixel[0] - idealX), Math.abs(pixel[1] - idealY));
			if (drift < nearestDrift) {
				nearestDrift = drift;
				nearest = pixel;
			}
		}
		report(what + " zoom=" + zoom + ": ideal=(" + idealX + ", " + idealY + ") nearestInk=(" + nearest[0] + ", "
				+ nearest[1] + ") drift=" + nearestDrift);
		assertTrue(nearestDrift <= MAX_COORDINATE_DRIFT_PIXELS,
				what + " zoom=" + zoom + ": no ink within " + MAX_COORDINATE_DRIFT_PIXELS + " px of the ideal position ("
						+ idealX + ", " + idealY + "), nearest ink at (" + nearest[0] + ", " + nearest[1] + ")");
	}

	private void assertFilledRectanglesShareEdgeWithoutGapOrOverlap(String caseName, int zoom, int leftX, int seamX,
			int rightWidth, int y, int height) {
		// the red rectangle is painted last, so an overlap would show up as red right of the seam
		ImageData imageData = render(90, 50, zoom, gc -> {
			gc.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
			gc.fillRectangle(seamX, y, rightWidth, height);
			gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			gc.fillRectangle(leftX, y, seamX - leftX, height);
		});
		float scaleFactor = zoom / 100.0f;
		double seam = seamX * scaleFactor;
		int rowFrom = Math.round(y * scaleFactor) + 1;
		int rowTo = Math.round((y + height) * scaleFactor) - 1;
		boolean[] inkedColumns = new boolean[imageData.width];
		int lastRedColumn = -1;
		int firstBlueColumn = Integer.MAX_VALUE;
		for (int row = rowFrom; row <= rowTo; row++) {
			for (int column = 0; column < imageData.width; column++) {
				RGB rgb = rgbAt(imageData, column, row);
				if (RED.equals(rgb)) {
					inkedColumns[column] = true;
					lastRedColumn = Math.max(lastRedColumn, column);
				} else if (BLUE.equals(rgb)) {
					inkedColumns[column] = true;
					firstBlueColumn = Math.min(firstBlueColumn, column);
				}
			}
		}
		assertTrue(lastRedColumn >= 0 && firstBlueColumn <= imageData.width,
				caseName + " zoom=" + zoom + ": expected both fills to be visible");
		int firstInk = -1;
		int lastInk = -1;
		for (int column = 0; column < inkedColumns.length; column++) {
			if (inkedColumns[column]) {
				if (firstInk < 0) firstInk = column;
				lastInk = column;
			}
		}
		assertPositionWithinDrift(caseName + ".left", zoom, firstInk, leftX * scaleFactor);
		assertPositionWithinDrift(caseName + ".right", zoom, lastInk + 1, (seamX + rightWidth) * scaleFactor);
		assertPositionWithinDrift(caseName + ".redRightOfSeam", zoom, lastRedColumn + 1, seam);
		assertPositionWithinDrift(caseName + ".blueLeftOfSeam", zoom, firstBlueColumn, seam);
		for (int column = firstInk; column <= lastInk; column++) {
			assertTrue(inkedColumns[column],
					caseName + " zoom=" + zoom + ": gap at column " + column + " between the adjacent rectangles");
		}
	}

	private void assertStrokedRectanglesShareEdgeWithoutGap(String caseName, int zoom, int leftX, int seamX,
			int rightWidth, int y, int height) {
		ImageData imageData = render(90, 50, zoom, gc -> {
			gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
			gc.drawRectangle(leftX, y, seamX - leftX, height);
			gc.drawRectangle(seamX, y, rightWidth, height);
		});
		float scaleFactor = zoom / 100.0f;
		double seam = seamX * scaleFactor;
		int rowFrom = Math.round(y * scaleFactor) + 1;
		int rowTo = Math.round((y + height) * scaleFactor) - 1;
		for (int row = rowFrom; row <= rowTo; row++) {
			double smallestDrift = Double.MAX_VALUE;
			int nearestColumn = -1;
			for (int column = 0; column < imageData.width; column++) {
				if (!BLACK.equals(rgbAt(imageData, column, row))) continue;
				double drift = Math.abs(column - seam);
				if (drift < smallestDrift) {
					smallestDrift = drift;
					nearestColumn = column;
				}
			}
			report(caseName + " row=" + row + " zoom=" + zoom + ": idealSeam=" + seam + " nearestInk=" + nearestColumn
					+ " drift=" + smallestDrift);
			assertTrue(smallestDrift <= MAX_COORDINATE_DRIFT_PIXELS,
					caseName + " zoom=" + zoom + ": gap along the shared edge in row " + row + ": no ink within "
							+ MAX_COORDINATE_DRIFT_PIXELS + " px of the ideal seam column " + seam);
		}
		int[] bounds = requireColorBounds(caseName, zoom, imageData, BLACK, rowFrom, rowTo);
		// an outline's outermost ink column sits on its ideal edge position
		assertPositionWithinDrift(caseName + ".leftEdge", zoom, bounds[0], leftX * scaleFactor);
		assertPositionWithinDrift(caseName + ".rightEdge", zoom, bounds[2], (seamX + rightWidth) * scaleFactor);
	}

	private void assertTickSpacing(String caseName, int zoom, ImageData imageData, RGB color, boolean filled) {
		float scaleFactor = zoom / 100.0f;
		int rowFrom = Math.round(TICK_Y_POINTS * scaleFactor) + 1;
		int rowTo = Math.round((TICK_Y_POINTS + TICK_HEIGHT_POINTS) * scaleFactor) - 1;
		List<int[]> marks = contiguousColumnRuns(imageData, color, rowFrom, rowTo);
		assertEquals(TICK_COUNT, marks.size(),
				caseName + " zoom=" + zoom + ": expected " + TICK_COUNT + " separated marks but found " + describeRuns(marks));
		StringBuilder reportedGaps = new StringBuilder();
		for (int k = 0; k < TICK_COUNT; k++) {
			int[] mark = marks.get(k);
			assertPositionWithinDrift(caseName + ".mark[" + k + "].left", zoom, mark[0],
					k * TICK_PITCH_POINTS * scaleFactor);
			double idealRight = (k * TICK_PITCH_POINTS + TICK_WIDTH_POINTS) * scaleFactor;
			// a fill ends just before its right edge, a stroke's outermost ink column sits on it
			assertPositionWithinDrift(caseName + ".mark[" + k + "].right", zoom, filled ? mark[1] + 1 : mark[1], idealRight);
			if (k > 0) {
				if (reportedGaps.length() > 0) reportedGaps.append(", ");
				reportedGaps.append(mark[0] - marks.get(k - 1)[0]);
				assertPositionWithinDrift(caseName + ".gap[" + (k - 1) + "]", zoom, mark[0] - marks.get(k - 1)[0],
						TICK_PITCH_POINTS * scaleFactor);
			}
		}
		report(caseName + " zoom=" + zoom + ": gaps=[" + reportedGaps + "] idealPitch="
				+ TICK_PITCH_POINTS * scaleFactor);
	}

	private void assertPositionWithinDrift(String what, int zoom, double measured, double ideal) {
		double drift = measured - ideal;
		report(what + " zoom=" + zoom + ": ideal=" + ideal + " measured=" + measured + " drift=" + drift);
		assertTrue(Math.abs(drift) <= MAX_COORDINATE_DRIFT_PIXELS,
				what + " zoom=" + zoom + ": measured " + measured + " deviates by more than "
						+ MAX_COORDINATE_DRIFT_PIXELS + " px from the ideal position " + ideal);
	}

	private List<int[]> collectPixels(ImageData imageData, RGB color) {
		List<int[]> pixels = new ArrayList<>();
		for (int y = 0; y < imageData.height; y++) {
			for (int x = 0; x < imageData.width; x++) {
				if (color.equals(rgbAt(imageData, x, y))) {
					pixels.add(new int[] { x, y });
				}
			}
		}
		return pixels;
	}

	private List<int[]> contiguousColumnRuns(ImageData imageData, RGB color, int rowFrom, int rowTo) {
		List<int[]> runs = new ArrayList<>();
		int runStart = -1;
		for (int column = 0; column < imageData.width; column++) {
			boolean inked = false;
			for (int row = rowFrom; row <= rowTo && !inked; row++) {
				inked = color.equals(rgbAt(imageData, column, row));
			}
			if (inked) {
				if (runStart < 0) runStart = column;
			} else if (runStart >= 0) {
				runs.add(new int[] { runStart, column - 1 });
				runStart = -1;
			}
		}
		if (runStart >= 0) runs.add(new int[] { runStart, imageData.width - 1 });
		return runs;
	}

	/** Red ink amount per pixel, 0 on the white background and 255 inside the shape,
	 * with antialiased edge pixels in between. */
	private byte[] redCoverage(ImageData imageData) {
		byte[] coverage = new byte[imageData.width * imageData.height];
		for (int row = 0; row < imageData.height; row++) {
			for (int column = 0; column < imageData.width; column++) {
				coverage[row * imageData.width + column] = (byte) (255 - rgbAt(imageData, column, row).green);
			}
		}
		return coverage;
	}

	/** The horizontal shift d with candidate(x, y) == reference(x - d, y) everywhere,
	 * or -1 when no whole-pixel translation maps one coverage map onto the other. */
	private int translationAligning(byte[] candidate, byte[] reference, int width, int height) {
		for (int d = -(width - 1); d < width; d++) {
			boolean aligned = true;
			for (int row = 0; row < height && aligned; row++) {
				for (int column = 0; column < width && aligned; column++) {
					int source = column - d;
					byte expected = source >= 0 && source < width ? reference[row * width + source] : 0;
					if (candidate[row * width + column] != expected) aligned = false;
				}
			}
			if (aligned) return d;
		}
		return -1;
	}

	/** Mirrors Win32DPIUtils.pointToPixel(Rectangle, ROUND): each edge rounds
	 * independently, so the width comes from two separate roundings. */
	private float[] snappedDeviceRectangle(int x, int y, int width, int height, float scaleFactor) {
		int left = Math.round(x * scaleFactor);
		int top = Math.round(y * scaleFactor);
		int right = Math.round((x + width) * scaleFactor);
		int bottom = Math.round((y + height) * scaleFactor);
		return new float[] { left, top, right - left, bottom - top };
	}

	/** Per-row ink extents a centre-sampling rasteriser produces for a filled ellipse,
	 * as {row, firstColumn, lastColumn} entries for rows with definite ink. Rows where
	 * a pixel centre lies on the curve itself are reported via indeterminateRows
	 * instead, because their outcome is decided by tie-breaking rather than geometry. */
	private int[][] predictedExtents(float[] rect, int widthPx, int heightPx, Set<Integer> indeterminateRows) {
		float centerX = rect[0] + rect[2] / 2f;
		float centerY = rect[1] + rect[3] / 2f;
		float axisX = rect[2] / 2f;
		float axisY = rect[3] / 2f;
		List<int[]> extents = new ArrayList<>();
		for (int row = 0; row < heightPx; row++) {
			double normalizedDy = (row + 0.5 - centerY) / axisY;
			if (normalizedDy * normalizedDy > 1.0) continue;
			double halfWidth = axisX * Math.sqrt(1.0 - normalizedDy * normalizedDy);
			int first = (int) Math.ceil(centerX - halfWidth - 0.5);
			int last = (int) Math.floor(centerX + halfWidth - 0.5);
			if (last < first || first < 0 || last >= widthPx) continue;
			boolean touchesCurve = false;
			for (int column = first - 1; column <= last + 1 && !touchesCurve; column++) {
				double v = squaredDistance((column + 0.5 - centerX) / axisX, normalizedDy);
				if (Math.abs(v - 1.0) <= CURVE_TIE_EPSILON) touchesCurve = true;
			}
			if (touchesCurve) {
				indeterminateRows.add(row);
			} else {
				extents.add(new int[] { row, first, last });
			}
		}
		return extents.toArray(new int[0][]);
	}

	private double squaredDistance(double nx, double ny) {
		return nx * nx + ny * ny;
	}

	/** Measured ink extents as {row, firstColumn, lastColumn} entries for rows with ink,
	 * skipping the given rows. */
	private int[][] measuredExtents(ImageData imageData, RGB color, Set<Integer> rowsToSkip) {
		List<int[]> extents = new ArrayList<>();
		for (int row = 0; row < imageData.height; row++) {
			if (rowsToSkip.contains(row)) continue;
			int first = -1;
			int last = -1;
			for (int column = 0; column < imageData.width; column++) {
				if (color.equals(rgbAt(imageData, column, row))) {
					if (first < 0) first = column;
					last = column;
				}
			}
			if (first >= 0) extents.add(new int[] { row, first, last });
		}
		return extents.toArray(new int[0][]);
	}

	/** Rows where the measured extent differs from the snapped prediction, described with
	 * the per-edge column deltas; tie rows are skipped because their outcome is not a
	 * geometry question. */
	private List<String> deviationsFromSnapped(int[][] measured, int[][] snapped, Set<Integer> indeterminateRows) {
		Map<Integer, int[]> measuredByRow = extentsByRow(measured);
		Map<Integer, int[]> snappedByRow = extentsByRow(snapped);
		Set<Integer> rows = new TreeSet<>();
		rows.addAll(measuredByRow.keySet());
		rows.addAll(snappedByRow.keySet());
		rows.removeAll(indeterminateRows);
		List<String> deviations = new ArrayList<>();
		for (int row : rows) {
			int[] actual = measuredByRow.get(row);
			int[] expected = snappedByRow.get(row);
			if (actual != null && expected != null && actual[1] == expected[1] && actual[2] == expected[2]) continue;
			String deviation = "row " + row + ": measured " + extentText(actual) + ", snapped "
					+ extentText(expected);
			if (actual != null && expected != null) {
				deviation += " (first " + (actual[1] - expected[1]) + ", last " + (actual[2] - expected[2]) + ")";
			}
			deviations.add(deviation);
		}
		return deviations;
	}

	private String extentText(int[] extent) {
		return extent == null ? "none" : extent[1] + ".." + extent[2];
	}

	private Map<Integer, int[]> extentsByRow(int[][] extents) {
		Map<Integer, int[]> result = new HashMap<>();
		for (int[] extent : extents) {
			result.put(extent[0], extent);
		}
		return result;
	}

	private List<String> formatExtents(int[][] extents) {
		List<String> result = new ArrayList<>();
		for (int[] extent : extents) {
			result.add(extent[0] + ":" + extent[1] + ".." + extent[2]);
		}
		return result;
	}

	private int[] requireColorBounds(String what, int zoom, ImageData imageData, RGB color, int rowFrom, int rowTo) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;
		for (int row = rowFrom; row <= rowTo; row++) {
			for (int column = 0; column < imageData.width; column++) {
				if (color.equals(rgbAt(imageData, column, row))) {
					minX = Math.min(minX, column);
					maxX = Math.max(maxX, column);
					minY = Math.min(minY, row);
					maxY = Math.max(maxY, row);
				}
			}
		}
		assertTrue(maxX >= 0, what + " zoom=" + zoom + ": no ink of the expected color was found");
		return new int[] { minX, minY, maxX, maxY };
	}

	private RGB rgbAt(ImageData imageData, int x, int y) {
		return imageData.palette.getRGB(imageData.getPixel(x, y));
	}

	private String describeRuns(List<int[]> runs) {
		StringBuilder result = new StringBuilder("[");
		for (int[] run : runs) {
			if (result.length() > 1) result.append(", ");
			result.append(run[0]).append("..").append(run[1]);
		}
		return result.append("]").toString();
	}

	private void report(String detail) {
		if (REPORTING) System.out.println("[gcprecision] " + detail);
	}
}
