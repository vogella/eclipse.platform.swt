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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for StyledText measuring and drawing, which go
 * through TextLayout, Font and GC. They assert invariants and relations
 * instead of absolute pixel values.
 */
public class Test_org_eclipse_swt_custom_StyledText_Characterization {

	private static final String ASCII = "Hello World, the quick brown fox";
	private static final String TABS = "a\tbb\tccc\tdddd";
	private static final String CJK = "中文日本語한글";
	private static final String COMBINING = "éäôx";
	private static final String EMOJI = "a😀b👍🏽c";
	private static final String RTL = "abc שלום עולם def";

	Shell shell;
	StyledText text;
	Display display;
	Font bigFont;
	Font boldFont;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		shell = new Shell(display);
		shell.setSize(400, 300);
		text = new StyledText(shell, SWT.NONE);
		text.setBounds(0, 0, 400, 300);
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
		if (bigFont != null) {
			bigFont.dispose();
		}
		if (boldFont != null) {
			boldFont.dispose();
		}
	}

	private Font derivedFont(int style, int height) {
		FontData[] fds = text.getFont().getFontData();
		for (FontData fd : fds) {
			fd.setStyle(style);
			if (height > 0) {
				fd.setHeight(height);
			}
		}
		return new Font(display, fds);
	}

	private StyleRange range(int start, int length, Font font, int fontStyle) {
		StyleRange r = new StyleRange();
		r.start = start;
		r.length = length;
		r.font = font;
		r.fontStyle = fontStyle;
		return r;
	}

	private StyleRange[] mixedStyles(int length) {
		bigFont = derivedFont(SWT.NORMAL, 20);
		return new StyleRange[] { range(0, length / 3, null, SWT.BOLD),
				range(length / 3, length / 3, null, SWT.ITALIC),
				range(2 * (length / 3), length - 2 * (length / 3), bigFont, SWT.NORMAL) };
	}

	private boolean isClusterBoundary(String s, int offset) {
		if (offset <= 0 || offset >= s.length()) {
			return true;
		}
		if (Character.isLowSurrogate(s.charAt(offset)) && Character.isHighSurrogate(s.charAt(offset - 1))) {
			return false;
		}
		int type = Character.getType(s.codePointAt(offset));
		return type != Character.NON_SPACING_MARK && type != Character.ENCLOSING_MARK
				&& type != Character.MODIFIER_SYMBOL;
	}

	private void assertRoundTrip(String s) {
		text.setText(s);
		for (int i = 0; i <= s.length(); i++) {
			if (!isClusterBoundary(s, i)) {
				continue;
			}
			Point p = text.getLocationAtOffset(i);
			int back = text.getOffsetAtPoint(new Point(p.x, p.y + 1));
			assertEquals(i, back, "offset " + i + " at " + p + " in \"" + s.replace('\t', '~') + "\"");
		}
	}

	@Test
	public void roundTripAscii() {
		assertRoundTrip(ASCII);
	}

	@Test
	public void roundTripTabs() {
		assertRoundTrip(TABS);
	}

	@Test
	public void roundTripCjk() {
		assertRoundTrip(CJK);
	}

	@Test
	public void roundTripAsciiWithMixedStyles() {
		text.setText(ASCII);
		text.setStyleRanges(mixedStyles(ASCII.length()));
		for (int i = 0; i <= ASCII.length(); i++) {
			Point p = text.getLocationAtOffset(i);
			assertEquals(i, text.getOffsetAtPoint(new Point(p.x, p.y + 1)), "offset " + i);
		}
	}

	@Test
	public void combiningMarksAndSurrogatePairsDoNotSplit() {
		for (String s : new String[] { COMBINING, EMOJI }) {
			text.setText(s);
			for (int x = 0; x < 80; x++) {
				int o = text.getOffsetAtPoint(new Point(x, 1));
				if (o >= 0 && o <= s.length()) {
					assertFalse(o > 0 && o < s.length() && Character.isLowSurrogate(s.charAt(o))
							&& Character.isHighSurrogate(s.charAt(o - 1)), "split surrogate pair at x=" + x);
				}
			}
		}
		text.setText(COMBINING);
		int prev = -1;
		for (int i = 0; i < COMBINING.length(); i++) {
			if (isClusterBoundary(COMBINING, i)) {
				int x = text.getLocationAtOffset(i).x;
				assertTrue(x >= prev, "x must not decrease at " + i);
				prev = x;
			}
		}
		text.setText(EMOJI);
		int[] starts = { 0, 1, 3, 4 };
		prev = -1;
		for (int i : starts) {
			int x = text.getLocationAtOffset(i).x;
			assertTrue(x >= prev, "x must not decrease at " + i);
			prev = x;
		}
	}

	@Test
	public void rtlSegmentLocationsAreInsideTheText() {
		text.setText(RTL);
		int width = text.getTextBounds(0, RTL.length() - 1).width;
		for (int i = 0; i <= RTL.length(); i++) {
			Point p = text.getLocationAtOffset(i);
			assertTrue(p.x >= 0 && p.x <= width + 2, "offset " + i + " x=" + p.x + " width=" + width);
			int back = text.getOffsetAtPoint(new Point(p.x, p.y + 1));
			assertTrue(back >= 0 && back <= RTL.length());
		}
		Point start = text.getLocationAtOffset(0);
		Point end = text.getLocationAtOffset(RTL.length());
		assertTrue(end.x > start.x);
	}

	@Test
	public void locationsGrowMonotonicallyInLtrText() {
		text.setText(ASCII + TABS + CJK);
		text.setStyleRanges(mixedStyles(text.getCharCount()));
		int prev = -1;
		for (int i = 0; i <= text.getCharCount(); i++) {
			int x = text.getLocationAtOffset(i).x;
			assertTrue(x >= prev, "offset " + i);
			prev = x;
		}
	}

	@Test
	public void textBoundsAgreeWithLocationAtOffset() {
		text.setText(ASCII);
		int start = 3;
		int end = 12;
		Rectangle b = text.getTextBounds(start, end);
		assertEquals(text.getLocationAtOffset(start).x, b.x, 1);
		assertEquals(text.getLocationAtOffset(end + 1).x, b.x + b.width, 1);
		assertEquals(text.getLocationAtOffset(start).y, b.y);
		assertTrue(b.height >= text.getLineHeight());
	}

	@Test
	public void textBoundsGrowWithTheRange() {
		text.setText(ASCII);
		text.setStyleRanges(mixedStyles(ASCII.length()));
		int prev = 0;
		for (int end = 0; end < ASCII.length(); end++) {
			int w = text.getTextBounds(0, end).width;
			assertTrue(w >= prev, "end " + end);
			prev = w;
		}
	}

	@Test
	public void boldTextIsNotNarrowerThanPlain() {
		text.setText(ASCII);
		int plain = text.getTextBounds(0, ASCII.length() - 1).width;
		text.setStyleRange(range(0, ASCII.length(), null, SWT.BOLD));
		int bold = text.getTextBounds(0, ASCII.length() - 1).width;
		assertTrue(bold >= plain, "bold " + bold + " plain " + plain);
	}

	@Test
	public void biggerFontIncreasesWidthAndLineHeight() {
		text.setText(ASCII);
		int plainWidth = text.getTextBounds(0, ASCII.length() - 1).width;
		int plainHeight = text.getLineHeight(0);
		bigFont = derivedFont(SWT.NORMAL, 24);
		text.setStyleRange(range(0, ASCII.length(), bigFont, SWT.NORMAL));
		assertTrue(text.getTextBounds(0, ASCII.length() - 1).width > plainWidth);
		assertTrue(text.getLineHeight(0) > plainHeight);
	}

	@Test
	public void ampersandMeasuresLikeAnyOtherCharacter() {
		text.setText("&&&&");
		int amp = text.getTextBounds(0, 3).width;
		text.setText("xxxx");
		int x = text.getTextBounds(0, 3).width;
		text.setText("&File & more&");
		Rectangle b = text.getTextBounds(0, 12);
		assertTrue(amp > 0 && x > 0 && b.width > 0);
		for (int i = 0; i < 13; i++) {
			assertTrue(text.getLocationAtOffset(i + 1).x > text.getLocationAtOffset(i).x, "offset " + i);
		}
		text.setText("&");
		assertTrue(text.getTextBounds(0, 0).width > 0);
		text.setText("a&b");
		int withAmp = text.getTextBounds(0, 2).width;
		text.setText("a_b");
		int withUnderscore = text.getTextBounds(0, 2).width;
		assertTrue(Math.abs(withAmp - withUnderscore) < text.getLineHeight(), "&: " + withAmp + " _: " + withUnderscore);
	}

	@Test
	public void lineSpacingIsAddedPerLine() {
		text.setText("a\nb\nc\nd");
		int h = text.getLineHeight();
		assertEquals(h, text.getLinePixel(1) - text.getLinePixel(0));
		text.setLineSpacing(7);
		int spaced = text.getLinePixel(1) - text.getLinePixel(0);
		assertTrue(spaced >= h + 7, "spaced " + spaced + " plain " + h);
		assertEquals(spaced, text.getLinePixel(2) - text.getLinePixel(1));
		assertEquals(spaced, text.getLinePixel(3) - text.getLinePixel(2));
		text.setLineSpacing(0);
		assertEquals(h, text.getLinePixel(1) - text.getLinePixel(0));
	}

	@Test
	public void linePixelAndLineIndexAreConsistent() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			sb.append("line ").append(i).append('\n');
		}
		text.setText(sb.toString());
		text.setLineSpacing(3);
		text.setStyleRange(range(20, 30, derivedFont(SWT.BOLD, 18), SWT.NORMAL));
		boldFont = text.getStyleRangeAtOffset(25).font;
		int prev = Integer.MIN_VALUE;
		for (int line = 0; line < text.getLineCount(); line++) {
			int y = text.getLinePixel(line);
			assertTrue(y > prev, "line " + line);
			prev = y;
			assertEquals(line, text.getLineIndex(y));
			assertEquals(line, text.getLineIndex(y + 1));
		}
	}

	@Test
	public void variableLineHeightFollowsTheLargestFontOnTheLine() {
		text.setText("one\ntwo\nthree");
		int plain = text.getLineHeight(0);
		bigFont = derivedFont(SWT.NORMAL, 30);
		text.setStyleRange(range(4, 3, bigFont, SWT.NORMAL));
		assertEquals(plain, text.getLineHeight(0));
		assertTrue(text.getLineHeight(4) > plain);
		assertEquals(plain, text.getLineHeight(8));
		assertEquals(text.getLineHeight(4), text.getLinePixel(2) - text.getLinePixel(1));
	}

	@Test
	public void riseAndMetricsChangeLineHeightAndCharacterWidth() {
		text.setText("abc\ndef\nghi");
		int plain = text.getLineHeight(0);
		StyleRange rise = new StyleRange();
		rise.start = 0;
		rise.length = 3;
		rise.rise = 12;
		text.setStyleRange(rise);
		assertTrue(text.getLineHeight(0) >= plain);
		assertEquals(plain, text.getLineHeight(4));
		StyleRange metrics = new StyleRange();
		metrics.start = 4;
		metrics.length = 1;
		metrics.metrics = new GlyphMetrics(10, 5, 40);
		text.setStyleRange(metrics);
		assertEquals(40, text.getLocationAtOffset(5).x - text.getLocationAtOffset(4).x);
		assertTrue(text.getLineHeight(4) >= 15);
	}

	@Test
	public void lineSpacingProviderValuesAreUsedPerLine() {
		text.setText("a\nb\nc");
		int h = text.getLineHeight();
		text.setLineSpacingProvider(line -> line == 1 ? 9 : 0);
		assertEquals(h, text.getLinePixel(1) - text.getLinePixel(0));
		assertTrue(text.getLinePixel(2) - text.getLinePixel(1) >= h + 9);
		text.setLineSpacingProvider(null);
		assertEquals(h, text.getLinePixel(2) - text.getLinePixel(1));
	}

	private int visualLines() {
		return text.getLinePixel(text.getLineCount()) / text.getLineHeight();
	}

	@Test
	public void wordWrapVisualLinesFollowWidth() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 40; i++) {
			sb.append("word").append(i).append(' ');
		}
		text.setText(sb.toString());
		assertEquals(1, text.getLineCount());
		int unwrapped = visualLines();
		assertEquals(1, unwrapped);
		text.setWordWrap(true);
		int[] counts = new int[3];
		int[] widths = { 400, 250, 120 };
		for (int i = 0; i < widths.length; i++) {
			text.setBounds(0, 0, widths[i], 300);
			counts[i] = visualLines();
		}
		assertTrue(counts[0] > 1);
		assertTrue(counts[1] >= counts[0]);
		assertTrue(counts[2] > counts[1]);
		text.setBounds(0, 0, 400, 300);
		assertEquals(counts[0], visualLines());
		text.setWordWrap(false);
		assertEquals(1, visualLines());
	}

	@Test
	public void wordWrapKeepsOffsetRoundTrip() {
		text.setWordWrap(true);
		text.setBounds(0, 0, 150, 300);
		text.setText("alpha beta gamma delta epsilon zeta eta theta iota kappa");
		int lastY = -1;
		for (int i = 0; i < text.getCharCount(); i++) {
			Point p = text.getLocationAtOffset(i);
			assertTrue(p.y >= lastY);
			lastY = p.y;
			if (text.getText().charAt(i) != ' ') {
				assertEquals(i, text.getOffsetAtPoint(new Point(p.x, p.y + 1)), "offset " + i);
			}
		}
		assertTrue(lastY > 0);
	}

	@Test
	public void computeSizeWithWrapIsNarrowerAndTaller() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 30; i++) {
			sb.append("word").append(i).append(' ');
		}
		text.setText(sb.toString());
		text.setWordWrap(true);
		Point natural = text.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		Point narrow = text.computeSize(100, SWT.DEFAULT);
		assertTrue(narrow.y > natural.y, "narrow " + narrow + " natural " + natural);
		assertTrue(narrow.x <= natural.x);
		assertTrue(narrow.x <= 100 + text.getBorderWidth() * 2 + text.getLineHeight());
	}

	@Test
	public void tabStopsSetByTabsAndExplicitStops() {
		text.setText("a\tb\tc");
		text.setTabs(4);
		int x4 = text.getLocationAtOffset(2).x;
		text.setTabs(8);
		int x8 = text.getLocationAtOffset(2).x;
		assertTrue(x8 > x4);
		text.setTabStops(new int[] { 50, 120 });
		assertArrayEquals(new int[] { 50, 120 }, text.getTabStops());
		int b = text.getLocationAtOffset(2).x;
		int c = text.getLocationAtOffset(4).x;
		assertEquals(50, b - text.getLeftMargin(), 1);
		assertEquals(120, c - text.getLeftMargin(), 1);
	}

	@Test
	public void alignmentIndentAndJustifyMoveTheText() {
		text.setText("abc def");
		text.setWordWrap(true);
		int left = text.getLocationAtOffset(0).x;
		text.setIndent(15);
		assertEquals(left + 15, text.getLocationAtOffset(0).x);
		text.setIndent(0);
		text.setAlignment(SWT.RIGHT);
		int right = text.getLocationAtOffset(0).x;
		assertTrue(right > left);
		text.setAlignment(SWT.CENTER);
		int center = text.getLocationAtOffset(0).x;
		assertTrue(center > left && center < right);
		text.setAlignment(SWT.LEFT);
		assertEquals(left, text.getLocationAtOffset(0).x);
		text.setLineAlignment(0, 1, SWT.RIGHT);
		assertEquals(right, text.getLocationAtOffset(0).x);
		text.setLineAlignment(0, 1, SWT.LEFT);
		text.setJustify(true);
		assertEquals(left, text.getLocationAtOffset(0).x);
	}

	@Test
	public void horizontalScrollWidthGrowsAfterLongLine() {
		text.setText("short");
		text.setHorizontalPixel(10000);
		assertEquals(0, text.getHorizontalPixel());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 300; i++) {
			sb.append("0123456789");
		}
		text.setText(sb.toString());
		int endX = text.getLocationAtOffset(sb.length()).x;
		assertTrue(endX > 400);
		text.setHorizontalPixel(10000);
		int scrolled = text.getHorizontalPixel();
		assertTrue(scrolled > 0);
		assertEquals(endX - scrolled, text.getLocationAtOffset(sb.length()).x);
		text.setHorizontalPixel(0);
		assertEquals(0, text.getHorizontalPixel());
		assertEquals(endX, text.getLocationAtOffset(sb.length()).x);
	}

	@Test
	public void underlineStrikeoutBorderAndColorStylesRoundTrip() {
		text.setText("0123456789012345678901234567890123456789");
		Color red = display.getSystemColor(SWT.COLOR_RED);
		Color blue = display.getSystemColor(SWT.COLOR_BLUE);
		int[] underlines = { SWT.UNDERLINE_SINGLE, SWT.UNDERLINE_DOUBLE, SWT.UNDERLINE_ERROR, SWT.UNDERLINE_SQUIGGLE,
				SWT.UNDERLINE_LINK };
		int[] borders = { SWT.BORDER_SOLID, SWT.BORDER_DASH, SWT.BORDER_DOT };
		StyleRange[] ranges = new StyleRange[underlines.length + 3];
		for (int i = 0; i < underlines.length; i++) {
			StyleRange r = new StyleRange(i * 5, 5, red, blue);
			r.underline = true;
			r.underlineStyle = underlines[i];
			r.underlineColor = blue;
			ranges[i] = r;
		}
		StyleRange strike = new StyleRange(25, 5, null, null);
		strike.strikeout = true;
		strike.strikeoutColor = red;
		ranges[5] = strike;
		StyleRange border = new StyleRange(30, 5, blue, red);
		border.borderStyle = borders[0];
		border.borderColor = blue;
		ranges[6] = border;
		StyleRange dash = new StyleRange(35, 5, null, null);
		dash.borderStyle = borders[1];
		ranges[7] = dash;
		text.setStyleRanges(ranges);
		StyleRange[] back = text.getStyleRanges();
		assertEquals(ranges.length, back.length);
		for (int i = 0; i < ranges.length; i++) {
			assertEquals(ranges[i], back[i], "range " + i);
		}
		assertEquals(SWT.UNDERLINE_SQUIGGLE, back[3].underlineStyle);
		assertTrue(back[5].strikeout);
		Point before = text.getLocationAtOffset(40);
		text.setStyleRanges(new StyleRange[0]);
		assertEquals(before, text.getLocationAtOffset(40));
	}

	private ImageData render() {
		Image image = new Image(display, 400, 300);
		GC gc = new GC(image);
		try {
			text.print(gc);
		} finally {
			gc.dispose();
		}
		ImageData data = image.getImageData();
		image.dispose();
		return data;
	}

	@Test
	public void renderingTwiceGivesIdenticalPixels() {
		text.setText("Hello styled\n\ttext with tabs\nthird line 中文");
		text.setStyleRanges(mixedStyles(text.getCharCount()));
		ImageData first = render();
		ImageData second = render();
		assertArrayEquals(first.data, second.data);
	}

	@Test
	public void backgroundStyleChangesRenderedPixels() {
		text.setText("Hello styled text");
		ImageData plain = render();
		StyleRange r = new StyleRange(0, 5, display.getSystemColor(SWT.COLOR_RED), display.getSystemColor(SWT.COLOR_GREEN));
		text.setStyleRange(r);
		ImageData styled = render();
		text.setStyleRange(null);
		text.setText("");
		assumeFalse(java.util.Arrays.equals(plain.data, render().data), "print draws nothing on this platform");
		assertFalse(java.util.Arrays.equals(plain.data, styled.data));
		text.setText("Hello styled text");
		assertArrayEquals(plain.data, render().data);
		assertNotEquals(0, plain.data.length);
	}
}
