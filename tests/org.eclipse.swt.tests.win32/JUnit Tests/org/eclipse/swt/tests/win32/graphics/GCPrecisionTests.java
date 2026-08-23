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
import java.util.List;

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

	/** Current worst-case coordinate drift, in device pixels, from integer point-to-pixel
	 * rounding. Lowered to 0 as the drawing operations are converted in step 4 of
	 * docs/gc-fractional-precision.md. */
	private static final int MAX_COORDINATE_DRIFT_PIXELS = 1;

	private static final String REPORT_PROPERTY = "swt.test.gcprecision.report";
	private static final boolean REPORTING = System.getProperty(REPORT_PROPERTY) != null;

	private static final int TICK_COUNT = 8;
	private static final int TICK_PITCH_POINTS = 7;
	private static final int TICK_WIDTH_POINTS = 3;
	private static final int TICK_HEIGHT_POINTS = 12;
	private static final int TICK_Y_POINTS = 20;

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
		assertStrokedRectanglesShareEdgeWithoutGap("originSeam", zoom, 0, 33, 47, 15, 30);
		assertStrokedRectanglesShareEdgeWithoutGap("offsetSeam", zoom, 20, 33, 47, 15, 30);
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testFillUniformSpacing(int zoom) {
		ImageData imageData = render(60, 40, zoom, gc -> {
			gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			drawTickRow(gc, true);
		});
		assertTickSpacing("filledTicks", zoom, imageData, RED, true);
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 125, 150, 175, 200 })
	public void testStrokeUniformSpacing(int zoom) {
		ImageData imageData = render(60, 40, zoom, gc -> {
			gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
			drawTickRow(gc, false);
		});
		assertTickSpacing("strokedTicks", zoom, imageData, BLACK, false);
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
