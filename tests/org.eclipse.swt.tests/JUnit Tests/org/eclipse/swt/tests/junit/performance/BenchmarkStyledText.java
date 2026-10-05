/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit.performance;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Manual benchmark: StyledText text and style handling, measuring and
 * drawing, which go through TextLayout, Font and GC. Scrolling through 10k
 * lines is covered by {@link BenchmarkTextAndGC}.
 * <p>
 * Run on demand (not part of any test suite), see {@link BenchmarkUtil}. On
 * macOS add {@code -XstartOnFirstThread}.
 * </p>
 */
public class BenchmarkStyledText {

	private static final int LINES = 10_000;
	private static final int STYLE_RANGES = 10_000;
	private static final int LOCATION_CALLS = 20_000;
	private static final int TYPING_EDITS = 2_000;
	private static final int RESIZES = 50;

	public static void main(String[] args) {
		Display display = new Display();
		try {
			run(display);
		} finally {
			display.dispose();
		}
	}

	static void run(Display display) {
		BenchmarkUtil.header("StyledText");
		setTextLarge(display);
		setStyleRangesMany(display);
		paintAdjacentStyles(display);
		locationAndOffsetLoops(display);
		typing(display);
		wordWrapReflow(display);
		longLineHorizontalScroll(display);
	}

	private static String document(int lines) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines; i++) {
			sb.append("Line ").append(i).append(": ");
			// mixed line lengths
			int words = 1 + (i * 7) % 20;
			for (int w = 0; w < words; w++) {
				sb.append("word").append(w).append(w % 5 == 0 ? "\t" : " ");
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static StyleRange[] adjacentRanges(Display display, int text, int count) {
		Color[] colors = { display.getSystemColor(SWT.COLOR_RED), display.getSystemColor(SWT.COLOR_BLUE),
				display.getSystemColor(SWT.COLOR_DARK_GREEN), display.getSystemColor(SWT.COLOR_DARK_MAGENTA) };
		int len = Math.max(1, text / count);
		StyleRange[] ranges = new StyleRange[count];
		for (int i = 0; i < count; i++) {
			StyleRange r = new StyleRange(i * len, len, colors[i % colors.length], null);
			if (i % 3 == 0) {
				r.fontStyle = SWT.BOLD;
			} else if (i % 3 == 1) {
				r.fontStyle = SWT.ITALIC;
			}
			if (i % 7 == 0) {
				r.underline = true;
			}
			ranges[i] = r;
		}
		return ranges;
	}

	private static StyledText open(Display display, Shell[] shell, int style) {
		shell[0] = new Shell(display);
		shell[0].setLayout(new FillLayout());
		shell[0].setSize(600, 400);
		StyledText text = new StyledText(shell[0], style);
		shell[0].open();
		BenchmarkUtil.flush(display);
		return text;
	}

	private static void setTextLarge(Display display) {
		int lines = BenchmarkUtil.scaled(LINES);
		String doc = document(lines);
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("StyledText.setText " + lines + " mixed lines + first paint", () -> {
			StyledText text = open(display, shell, SWT.V_SCROLL | SWT.H_SCROLL);
			return () -> {
				text.setText(doc);
				BenchmarkUtil.paintNow(display, text);
			};
		}, () -> shell[0].dispose());
	}

	private static void setStyleRangesMany(Display display) {
		int lines = BenchmarkUtil.scaled(LINES);
		int count = BenchmarkUtil.scaled(STYLE_RANGES);
		String doc = document(lines);
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("StyledText.setStyleRanges " + count + " ranges over " + lines + " lines", () -> {
			StyledText text = open(display, shell, SWT.V_SCROLL | SWT.H_SCROLL);
			text.setText(doc);
			StyleRange[] ranges = adjacentRanges(display, doc.length(), count);
			return () -> {
				text.setStyleRanges(ranges);
				BenchmarkUtil.paintNow(display, text);
			};
		}, () -> shell[0].dispose());
	}

	private static void paintAdjacentStyles(Display display) {
		int paints = BenchmarkUtil.scaled(200);
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("StyledText paint x" + paints + " (many adjacent style ranges per line)", () -> {
			StyledText text = open(display, shell, SWT.V_SCROLL);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 40; i++) {
				sb.append("0123456789abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz\n");
			}
			text.setText(sb.toString());
			text.setStyleRanges(adjacentRanges(display, sb.length(), sb.length() / 3));
			return () -> {
				for (int i = 0; i < paints; i++) {
					text.redraw();
					BenchmarkUtil.paintNow(display, text);
				}
			};
		}, () -> shell[0].dispose());
	}

	private static void locationAndOffsetLoops(Display display) {
		int calls = BenchmarkUtil.scaled(LOCATION_CALLS);
		int lines = BenchmarkUtil.scaled(2_000);
		String doc = document(lines);
		Shell[] shell = new Shell[1];
		int[] sink = new int[1];
		BenchmarkUtil.measure("StyledText.getLocationAtOffset x" + calls + " styled", () -> {
			StyledText text = open(display, shell, SWT.V_SCROLL | SWT.H_SCROLL);
			text.setText(doc);
			text.setStyleRanges(adjacentRanges(display, doc.length(), doc.length() / 40));
			return () -> {
				for (int i = 0; i < calls; i++) {
					sink[0] += text.getLocationAtOffset((int) ((long) i * 7919 % doc.length())).x;
				}
			};
		}, () -> shell[0].dispose());
		BenchmarkUtil.measure("StyledText.getOffsetAtPoint x" + calls + " styled", () -> {
			StyledText text = open(display, shell, SWT.V_SCROLL | SWT.H_SCROLL);
			text.setText(doc);
			text.setStyleRanges(adjacentRanges(display, doc.length(), doc.length() / 40));
			return () -> {
				Point p = new Point(0, 0);
				for (int i = 0; i < calls; i++) {
					p.x = i * 13 % 600;
					p.y = i * 7 % 400;
					sink[0] += text.getOffsetAtPoint(p);
				}
			};
		}, () -> shell[0].dispose());
	}

	private static void typing(Display display) {
		int edits = BenchmarkUtil.scaled(TYPING_EDITS);
		int lines = BenchmarkUtil.scaled(2_000);
		String doc = document(lines);
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("StyledText typing simulation: " + edits + " replaceTextRange + paint", () -> {
			StyledText text = open(display, shell, SWT.V_SCROLL | SWT.H_SCROLL);
			text.setText(doc);
			text.setStyleRanges(adjacentRanges(display, doc.length(), doc.length() / 40));
			return () -> {
				int offset = doc.length() / 2;
				for (int i = 0; i < edits; i++) {
					text.replaceTextRange(offset + i, 0, "x");
					text.setCaretOffset(offset + i + 1);
					BenchmarkUtil.paintNow(display, text);
				}
			};
		}, () -> shell[0].dispose());
	}

	private static void wordWrapReflow(Display display) {
		int resizes = BenchmarkUtil.scaled(RESIZES);
		int lines = BenchmarkUtil.scaled(2_000);
		String doc = document(lines);
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("StyledText word-wrap reflow on " + resizes + " resizes of " + lines + " lines", () -> {
			StyledText text = open(display, shell, SWT.V_SCROLL | SWT.WRAP);
			text.setText(doc);
			return () -> {
				for (int i = 0; i < resizes; i++) {
					shell[0].setSize(400 + (i % 2) * 200 + i, 400);
					BenchmarkUtil.paintNow(display, text);
				}
			};
		}, () -> shell[0].dispose());
	}

	private static void longLineHorizontalScroll(Display display) {
		int chars = BenchmarkUtil.scaled(30_000);
		int steps = BenchmarkUtil.scaled(100);
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("StyledText long single line (" + chars + " chars) horizontal scroll " + steps + " steps", () -> {
			StyledText text = open(display, shell, SWT.H_SCROLL);
			StringBuilder sb = new StringBuilder();
			while (sb.length() < chars) {
				sb.append("0123456789 ");
			}
			text.setText(sb.toString());
			text.setStyleRanges(adjacentRanges(display, sb.length(), sb.length() / 50));
			return () -> {
				int max = text.getLocationAtOffset(sb.length()).x;
				for (int i = 0; i < steps; i++) {
					text.setHorizontalPixel(i * max / steps);
					BenchmarkUtil.paintNow(display, text);
				}
			};
		}, () -> shell[0].dispose());
	}
}
