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
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Manual benchmark: text measurement ({@code GC.textExtent/stringExtent},
 * StyledText scrolling), GC primitives without state changes, and TextLayout.
 * <p>
 * Run on demand (not part of any test suite), see {@link BenchmarkUtil}. On
 * macOS add {@code -XstartOnFirstThread}.
 * </p>
 */
public class BenchmarkTextAndGC {

	private static final int CALLS = 100_000;
	private static final int STYLED_TEXT_LINES = 10_000;
	private static final int STYLED_TEXT_SCROLL_STEPS = 500;
	private static final int LAYOUT_RUNS = 20;
	private static final int STYLE_RANGES = 1_000;

	private static final String[] MIXED_STRINGS = { "Hello", "A somewhat longer string of text 12345", "&File",
			"Mnemonic && ampersand & more", "line one\nline two", "tab\tseparated\tvalues", "äöü € unicode",
			"" };

	public static void main(String[] args) {
		Display display = new Display();
		try {
			run(display);
		} finally {
			display.dispose();
		}
	}

	static void run(Display display) {
		BenchmarkUtil.header("Text measurement");
		textExtent(display);
		styledTextScroll(display);
		BenchmarkUtil.header("GC primitives");
		gcPrimitives(display);
		BenchmarkUtil.header("TextLayout");
		textLayout(display);
	}

	private static void textExtent(Display display) {
		int calls = BenchmarkUtil.scaled(CALLS);
		Image[] image = new Image[1];
		GC[] gc = new GC[1];
		Runnable prepare = () -> {
			image[0] = new Image(display, 200, 100);
			gc[0] = new GC(image[0]);
		};
		Runnable cleanup = () -> {
			gc[0].dispose();
			image[0].dispose();
		};
		int[] sink = new int[1];
		BenchmarkUtil.measure("GC.textExtent(String) x" + calls + " mixed strings", () -> {
			prepare.run();
			return () -> {
				for (int i = 0; i < calls; i++) {
					sink[0] += gc[0].textExtent(MIXED_STRINGS[i % MIXED_STRINGS.length]).x;
				}
			};
		}, cleanup);
		BenchmarkUtil.measure("GC.stringExtent(String) x" + calls + " mixed strings", () -> {
			prepare.run();
			return () -> {
				for (int i = 0; i < calls; i++) {
					sink[0] += gc[0].stringExtent(MIXED_STRINGS[i % MIXED_STRINGS.length]).x;
				}
			};
		}, cleanup);
		int flags = SWT.DRAW_MNEMONIC | SWT.DRAW_DELIMITER | SWT.DRAW_TAB;
		BenchmarkUtil.measure("GC.textExtent(String, MNEMONIC|DELIMITER|TAB) x" + calls, () -> {
			prepare.run();
			return () -> {
				for (int i = 0; i < calls; i++) {
					sink[0] += gc[0].textExtent(MIXED_STRINGS[i % MIXED_STRINGS.length], flags).x;
				}
			};
		}, cleanup);
		BenchmarkUtil.measure("GC.drawText(String, DRAW_TRANSPARENT) x" + calls / 10, () -> {
			prepare.run();
			return () -> {
				for (int i = 0; i < calls / 10; i++) {
					gc[0].drawText(MIXED_STRINGS[i % MIXED_STRINGS.length], 2, 2, SWT.DRAW_TRANSPARENT);
				}
			};
		}, cleanup);
	}

	private static void styledTextScroll(Display display) {
		int lines = BenchmarkUtil.scaled(STYLED_TEXT_LINES);
		int steps = Math.min(STYLED_TEXT_SCROLL_STEPS, lines);
		Shell[] shell = new Shell[1];
		BenchmarkUtil.measure("StyledText scroll+paint " + steps + " steps through " + lines + " lines", () -> {
			shell[0] = new Shell(display);
			shell[0].setLayout(new FillLayout());
			shell[0].setSize(600, 400);
			StyledText text = new StyledText(shell[0], SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lines; i++) {
				sb.append("Line ").append(i).append(": the quick brown fox jumps over the lazy dog\n");
			}
			text.setText(sb.toString());
			shell[0].open();
			BenchmarkUtil.flush(display);
			return () -> {
				for (int i = 0; i < steps; i++) {
					text.setTopIndex(i * lines / steps);
					BenchmarkUtil.paintNow(display, text);
				}
			};
		}, () -> shell[0].dispose());
	}

	private static void gcPrimitives(Display display) {
		int calls = BenchmarkUtil.scaled(CALLS);
		Image[] image = new Image[1];
		GC[] gc = new GC[1];
		Runnable cleanup = () -> {
			gc[0].dispose();
			image[0].dispose();
		};
		BenchmarkUtil.measure("GC.drawLine x" + calls + " (no state changes)", () -> {
			image[0] = new Image(display, 500, 500);
			gc[0] = new GC(image[0]);
			gc[0].setForeground(display.getSystemColor(SWT.COLOR_RED));
			return () -> {
				for (int i = 0; i < calls; i++) {
					gc[0].drawLine(0, i % 500, 499, (i * 7) % 500);
				}
			};
		}, cleanup);
		BenchmarkUtil.measure("GC.fillRectangle x" + calls + " (no state changes)", () -> {
			image[0] = new Image(display, 500, 500);
			gc[0] = new GC(image[0]);
			gc[0].setBackground(display.getSystemColor(SWT.COLOR_BLUE));
			return () -> {
				for (int i = 0; i < calls; i++) {
					gc[0].fillRectangle(i % 400, (i * 3) % 400, 50, 50);
				}
			};
		}, cleanup);
	}

	private static String layoutText() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; sb.length() < STYLE_RANGES * 4; i++) {
			sb.append("abc ");
		}
		return sb.toString();
	}

	private static void textLayout(Display display) {
		String text = layoutText();
		TextLayout[] layout = new TextLayout[1];
		BenchmarkUtil.measure("TextLayout.setText + getBounds, " + STYLE_RANGES + " style ranges x" + LAYOUT_RUNS, () -> {
			layout[0] = new TextLayout(display);
			layout[0].setWidth(600);
			return () -> {
				for (int run = 0; run < LAYOUT_RUNS; run++) {
					layout[0].setText(text);
					for (int i = 0; i < STYLE_RANGES; i++) {
						TextStyle style = new TextStyle();
						style.foreground = display.getSystemColor(i % 2 == 0 ? SWT.COLOR_RED : SWT.COLOR_BLUE);
						layout[0].setStyle(style, i * 4, i * 4 + 3);
					}
					layout[0].getBounds();
				}
			};
		}, () -> layout[0].dispose());

		Image[] image = new Image[1];
		GC[] gc = new GC[1];
		BenchmarkUtil.measure("TextLayout.draw, " + STYLE_RANGES + " adjacent underline styles x" + LAYOUT_RUNS, () -> {
			layout[0] = new TextLayout(display);
			layout[0].setWidth(780);
			layout[0].setText(text);
			for (int i = 0; i < STYLE_RANGES; i++) {
				TextStyle style = new TextStyle();
				style.underline = true;
				style.underlineStyle = SWT.UNDERLINE_SINGLE;
				layout[0].setStyle(style, i * 4, i * 4 + 3);
			}
			image[0] = new Image(display, 800, 600);
			gc[0] = new GC(image[0]);
			return () -> {
				for (int run = 0; run < LAYOUT_RUNS; run++) {
					layout[0].draw(gc[0], 0, 0);
				}
			};
		}, () -> {
			gc[0].dispose();
			image[0].dispose();
			layout[0].dispose();
		});
	}
}
