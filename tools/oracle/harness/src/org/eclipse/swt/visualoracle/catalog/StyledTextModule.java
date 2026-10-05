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
package org.eclipse.swt.visualoracle.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.Bullet;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 * The styled text family: style ranges, underline and border styles, rise and
 * glyph metrics, line attributes, bullets, tabs, wrapping, spacing, selection,
 * margins, bidi and mixed scripts, programmatic scrolling and disabled state.
 * <p>
 * Every specimen drives SWT's own TextLayout and GC drawing. The widget's
 * caret is removed with {@code setCaret(null)} and focus is suppressed by
 * {@link NoCaret}, so no blink phase can reach a capture. No scroll bars are
 * created (they can fade on some platforms); scrolling is set programmatically
 * once the widget has its final size. Fonts and colors come from the context
 * font, system colors and fonts derived from it, never from font names.
 * <p>
 * Selection specimens are tagged {@link Tag#FOCUS_SENSITIVE} because the
 * selection color depends on focus. The emoji and CJK specimens rely on the
 * platform's font fallback, which is stable on one machine but may differ
 * between machines.
 */
public class StyledTextModule implements SpecimenModule {

	@Override
	public String family() {
		return "styledtext";
	}

	/** Fills a freshly created widget; may use the helpers below. */
	private interface Filler {
		void fill(StyledText text);
	}

	private static final String PROSE = "The quick brown fox\njumps over the lazy dog.\nPack my box with\nfive dozen liquor jugs.";
	private static final String WRAP_PROSE = "Styled text wraps long lines at the widget width. "
			+ "Supercalifragilisticexpialidocious_unbreakable_word_follows. And then it continues normally.";

	@Override
	public List<Specimen> specimens() {
		List<Specimen> list = new ArrayList<>();
		int B = SWT.BORDER;
		int wrap = SWT.WRAP;
		list.add(spec("styledtext.plain.border", 260, 110, B | SWT.MULTI, t -> t.setText(PROSE), TEXT));
		list.add(spec("styledtext.plain.noborder", 260, 110, SWT.MULTI, t -> t.setText(PROSE), TEXT));
		list.add(spec("styledtext.plain.empty", 260, 60, B | SWT.MULTI, t -> { }, Set.of(Tag.FOCUS_SENSITIVE)));
		list.add(spec("styledtext.readonly", 260, 110, B | SWT.MULTI | SWT.READ_ONLY, t -> t.setText(PROSE), TEXT));
		list.add(spec("styledtext.disabled", 260, 110, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setEnabled(false);
		}, TEXT));
		list.add(spec("styledtext.single", 260, 36, B | SWT.SINGLE, t -> t.setText("Single line styled text"), TEXT));

		list.add(spec("styledtext.range.bold", 260, 70, B | SWT.MULTI, t -> {
			t.setText("Plain bold italic bold-italic end");
			fontStyle(t, 6, 4, SWT.BOLD);
			fontStyle(t, 11, 6, SWT.ITALIC);
			fontStyle(t, 18, 11, SWT.BOLD | SWT.ITALIC);
		}, TEXT));
		list.add(spec("styledtext.range.fontsize", 300, 90, B | SWT.MULTI, t -> {
			t.setText("small normal LARGE huge normal again");
			fontSize(t, 0, 5, -3);
			fontSize(t, 13, 5, 6);
			fontSize(t, 19, 4, 14);
		}, TEXT));
		list.add(spec("styledtext.range.colors", 260, 70, B | SWT.MULTI, t -> {
			t.setText("Foreground and background colors");
			Display d = t.getDisplay();
			StyleRange fg = new StyleRange(0, 10, d.getSystemColor(SWT.COLOR_RED), null);
			StyleRange bg = new StyleRange(15, 10, d.getSystemColor(SWT.COLOR_BLACK), d.getSystemColor(SWT.COLOR_YELLOW));
			t.setStyleRanges(new StyleRange[] { fg, bg });
		}, TEXT));

		list.add(spec("styledtext.underline.single", 260, 40, B | SWT.SINGLE, t -> underline(t, SWT.UNDERLINE_SINGLE, true), TEXT));
		list.add(spec("styledtext.underline.double", 260, 40, B | SWT.SINGLE, t -> underline(t, SWT.UNDERLINE_DOUBLE, true), TEXT));
		list.add(spec("styledtext.underline.error", 260, 40, B | SWT.SINGLE, t -> underline(t, SWT.UNDERLINE_ERROR, true), TEXT));
		list.add(spec("styledtext.underline.squiggle", 260, 40, B | SWT.SINGLE, t -> underline(t, SWT.UNDERLINE_SQUIGGLE, true), TEXT));
		list.add(spec("styledtext.underline.link", 260, 40, B | SWT.SINGLE, t -> underline(t, SWT.UNDERLINE_LINK, true), TEXT));
		list.add(spec("styledtext.underline.color", 260, 40, B | SWT.SINGLE, t -> underline(t, SWT.UNDERLINE_SINGLE, false), TEXT));
		list.add(spec("styledtext.strikeout.color", 260, 70, B | SWT.MULTI, t -> {
			t.setText("Strikeout default\nStrikeout colored");
			Display d = t.getDisplay();
			StyleRange a = new StyleRange(0, 17, null, null);
			a.strikeout = true;
			StyleRange b = new StyleRange(18, 16, null, null);
			b.strikeout = true;
			b.strikeoutColor = d.getSystemColor(SWT.COLOR_RED);
			t.setStyleRanges(new StyleRange[] { a, b });
		}, TEXT));
		list.add(spec("styledtext.border.styles", 300, 110, B | SWT.MULTI, t -> {
			t.setText("solid box\ndashed box\ndotted box");
			Display d = t.getDisplay();
			int[] styles = { SWT.BORDER_SOLID, SWT.BORDER_DASH, SWT.BORDER_DOT };
			int[] starts = { 0, 10, 21 };
			int[] lengths = { 9, 10, 10 };
			StyleRange[] ranges = new StyleRange[3];
			for (int i = 0; i < 3; i++) {
				ranges[i] = new StyleRange(starts[i], lengths[i], null, null);
				ranges[i].borderStyle = styles[i];
				ranges[i].borderColor = d.getSystemColor(i == 0 ? SWT.COLOR_BLUE : SWT.COLOR_DARK_RED);
			}
			t.setStyleRanges(ranges);
		}, TEXT));

		list.add(spec("styledtext.rise", 260, 70, B | SWT.SINGLE, t -> {
			t.setText("E = mc2 and H2O");
			StyleRange sup = new StyleRange(6, 1, null, null);
			sup.rise = 6;
			StyleRange sub = new StyleRange(14, 1, null, null);
			sub.rise = -4;
			t.setStyleRanges(new StyleRange[] { sup, sub });
		}, TEXT));
		list.add(spec("styledtext.metrics", 260, 70, B | SWT.SINGLE, t -> {
			t.setText("inline ￼ object");
			StyleRange r = new StyleRange(7, 1, null, null);
			r.metrics = new GlyphMetrics(18, 6, 40);
			t.setStyleRange(r);
			Color fill = t.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);
			t.addPaintObjectListener(e -> {
				e.gc.setBackground(fill);
				e.gc.fillRectangle(e.x + 2, e.y + 2, 36, e.ascent + e.descent - 4);
			});
		}, TEXT));
		list.add(spec("styledtext.range.manyadjacent", 300, 70, B | SWT.MULTI | wrap, t -> {
			String s = "Every single character has its own foreground color here";
			t.setText(s);
			int[] palette = { SWT.COLOR_RED, SWT.COLOR_BLUE, SWT.COLOR_DARK_GREEN, SWT.COLOR_DARK_MAGENTA, SWT.COLOR_DARK_YELLOW };
			StyleRange[] ranges = new StyleRange[s.length()];
			for (int i = 0; i < ranges.length; i++)
				ranges[i] = new StyleRange(i, 1, t.getDisplay().getSystemColor(palette[i % palette.length]), null);
			t.setStyleRanges(ranges);
		}, TEXT));

		list.add(spec("styledtext.line.background", 260, 110, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setLineBackground(0, 1, t.getDisplay().getSystemColor(SWT.COLOR_YELLOW));
			t.setLineBackground(2, 2, t.getDisplay().getSystemColor(SWT.COLOR_CYAN));
		}, TEXT));
		list.add(spec("styledtext.line.alignment", 260, 110, B | SWT.MULTI, t -> {
			t.setText("Left aligned\nCenter aligned\nRight aligned");
			t.setLineAlignment(0, 1, SWT.LEFT);
			t.setLineAlignment(1, 1, SWT.CENTER);
			t.setLineAlignment(2, 1, SWT.RIGHT);
		}, TEXT));
		list.add(spec("styledtext.line.indent", 260, 110, B | SWT.MULTI, t -> {
			t.setText("No indent\nIndent 20\nIndent 40");
			t.setLineIndent(1, 1, 20);
			t.setLineIndent(2, 1, 40);
		}, TEXT));
		list.add(spec("styledtext.line.justify", 260, 110, B | SWT.MULTI | wrap, t -> {
			t.setText("Justified text spreads the words of every wrapped line to fill the full width.\nLast line stays short");
			t.setLineJustify(0, 2, true);
		}, TEXT));
		list.add(spec("styledtext.line.wrapindent", 260, 110, B | SWT.MULTI | wrap, t -> {
			t.setText("A long paragraph whose continuation lines are indented by the wrap indent value set.");
			t.setLineWrapIndent(0, 1, 30);
		}, TEXT));

		list.add(spec("styledtext.bullet.dot", 260, 90, B | SWT.MULTI, t -> bullet(t, ST.BULLET_DOT, null), TEXT));
		list.add(spec("styledtext.bullet.number", 260, 90, B | SWT.MULTI, t -> bullet(t, ST.BULLET_NUMBER, null), TEXT));
		list.add(spec("styledtext.bullet.letter", 260, 90, B | SWT.MULTI, t -> bullet(t, ST.BULLET_LETTER_LOWER, null), TEXT));
		list.add(spec("styledtext.bullet.text", 260, 90, B | SWT.MULTI, t -> bullet(t, ST.BULLET_TEXT, ">>"), TEXT));

		list.add(spec("styledtext.tabs.width", 300, 70, B | SWT.MULTI, t -> {
			t.setText("a\tb\tc\nlong text\ttab\tend");
			t.setTabs(4);
		}, TEXT));
		list.add(spec("styledtext.tabs.stops", 300, 70, B | SWT.MULTI, t -> {
			t.setText("a\tb\tc\nlong text\ttab\tend");
			t.setTabStops(new int[] { 40, 100, 180 });
		}, TEXT));

		list.add(spec("styledtext.wrap", 200, 130, B | SWT.MULTI | wrap, t -> t.setText(WRAP_PROSE), TEXT));
		list.add(spec("styledtext.wrap.off", 200, 70, B | SWT.MULTI, t -> t.setText(WRAP_PROSE), TEXT));
		list.add(spec("styledtext.spacing.line", 260, 130, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setLineSpacing(8);
		}, TEXT));
		list.add(spec("styledtext.spacing.variableheight", 300, 150, B | SWT.MULTI, t -> {
			t.setText("small line\nnormal line\nlarge line\nsmall again");
			fontSize(t, 0, 10, -3);
			fontSize(t, 23, 10, 10);
			fontSize(t, 34, 11, -3);
		}, TEXT));

		list.add(spec("styledtext.selection.range", 260, 110, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setSelection(4, 40);
		}, FOCUS_TEXT));
		list.add(spec("styledtext.selection.block", 260, 110, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setBlockSelection(true);
			t.setBlockSelectionBounds(20, 8, 100, 50);
		}, FOCUS_TEXT));
		list.add(spec("styledtext.margins", 260, 110, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setMargins(24, 12, 16, 8);
		}, TEXT));
		list.add(spec("styledtext.alignment.right", 260, 110, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setAlignment(SWT.RIGHT);
		}, TEXT));
		list.add(spec("styledtext.alignment.center", 260, 110, B | SWT.MULTI, t -> {
			t.setText(PROSE);
			t.setAlignment(SWT.CENTER);
		}, TEXT));
		list.add(spec("styledtext.bidi.rtl", 300, 110, B | SWT.MULTI | SWT.RIGHT_TO_LEFT, t -> t.setText(
				"Hello שלום עולם world\n"
				+ "مرحبا بالعالم abc 123\n"
				+ "Latin العربية עברית"), TEXT));
		list.add(spec("styledtext.bidi.ltr", 300, 110, B | SWT.MULTI, t -> t.setText(
				"Hello שלום עולם world\n"
				+ "مرحبا بالعالم abc 123\n"
				+ "Latin العربية עברית"), TEXT));
		list.add(spec("styledtext.script.cjk", 300, 90, B | SWT.MULTI, t -> t.setText(
				"日本語のテキスト\n中文文本 한국어"), TEXT));
		list.add(spec("styledtext.script.combining", 300, 90, B | SWT.MULTI, t -> t.setText(
				"é ä ô ñ ǖ\nÅ कि กิ"), TEXT));
		list.add(spec("styledtext.script.emoji", 300, 90, B | SWT.MULTI, t -> t.setText(
				"Emoji 😀 🚀 🎉\nFlag 🇩🇪 family 👨‍👩‍👧"), TEXT));

		list.add(spec("styledtext.scroll.vertical", 260, 110, B | SWT.MULTI, t -> {
			t.setText(lines(200, ""));
			scrollOnResize(t, 40, 0);
		}, TEXT));
		list.add(spec("styledtext.scroll.horizontal", 260, 70, B | SWT.MULTI, t -> {
			t.setText(lines(5, " with a very long tail that continues far beyond the right edge of the widget"));
			scrollOnResize(t, 0, 150);
		}, TEXT));
		list.add(spec("styledtext.scroll.both", 260, 110, B | SWT.MULTI, t -> {
			t.setText(lines(100, " with a very long tail that continues far beyond the right edge of the widget"));
			scrollOnResize(t, 25, 90);
		}, TEXT));
		list.add(spec("styledtext.mnemonic.ampersand", 260, 70, B | SWT.MULTI, t -> t.setText("&Open && close\nA&B&C"), TEXT));
		return list;
	}

	private static final Set<Tag> TEXT = Set.of(Tag.TEXT_HEAVY);
	private static final Set<Tag> FOCUS_TEXT = Set.of(Tag.TEXT_HEAVY, Tag.FOCUS_SENSITIVE);

	private static Specimen spec(String id, int width, int height, int style, Filler filler, Set<Tag> tags) {
		return new FamilySpecimen(id, width, height) {
			@Override
			public Control create(Composite parent, SpecimenContext ctx) {
				StyledText text = new StyledText(parent, style);
				ctx.configure(text);
				text.setCaret(null);
				NoCaret.ensure(text);
				text.setSize(width, height);
				filler.fill(text);
				return text;
			}

			@Override
			public Set<Tag> tags() {
				return tags;
			}
		};
	}

	private static String lines(int count, String tail) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++)
			sb.append("Line ").append(i).append(tail).append(i < count - 1 ? "\n" : "");
		return sb.toString();
	}

	/** Scrolls once the widget has its final client area, so the clamp is deterministic. */
	private static void scrollOnResize(StyledText text, int topIndex, int horizontalPixel) {
		boolean[] done = { false };
		text.addListener(SWT.Resize, e -> {
			if (done[0] || text.getClientArea().height <= 0)
				return;
			done[0] = true;
			text.setTopIndex(topIndex);
			text.setHorizontalPixel(horizontalPixel);
		});
		text.setTopIndex(topIndex);
		text.setHorizontalPixel(horizontalPixel);
	}

	private static void fontStyle(StyledText text, int start, int length, int fontStyle) {
		StyleRange range = new StyleRange(start, length, null, null);
		range.fontStyle = fontStyle;
		text.setStyleRange(range);
	}

	/** Applies a derived font, disposed with the widget, to the range. */
	private static void fontSize(StyledText text, int start, int length, int delta) {
		FontData[] data = text.getFont().getFontData();
		for (FontData fd : data)
			fd.setHeight(Math.max(4, Math.round(fd.getHeight()) + delta));
		Font font = new Font(text.getDisplay(), data);
		text.addDisposeListener(e -> font.dispose());
		StyleRange range = new StyleRange(start, length, null, null);
		range.font = font;
		text.setStyleRange(range);
	}

	private static void underline(StyledText text, int style, boolean defaultColor) {
		text.setText("Underlined text sample");
		StyleRange range = new StyleRange(0, 10, null, null);
		range.underline = true;
		range.underlineStyle = style;
		if (!defaultColor || style == SWT.UNDERLINE_LINK)
			range.underlineColor = text.getDisplay().getSystemColor(style == SWT.UNDERLINE_LINK ? SWT.COLOR_BLUE : SWT.COLOR_RED);
		text.setStyleRange(range);
	}

	private static void bullet(StyledText text, int type, String bulletText) {
		text.setText("First item\nSecond item\nThird item\nPlain line");
		StyleRange style = new StyleRange();
		style.metrics = new GlyphMetrics(0, 0, 30);
		Bullet bullet = new Bullet(type, style);
		if (bulletText != null)
			bullet.text = bulletText;
		text.setLineBullet(0, 3, bullet);
	}
}
