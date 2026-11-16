/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.PlatformSpecificExecutionExtension;
import org.eclipse.swt.internal.win32.OS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(PlatformSpecificExecutionExtension.class)
class DarkThemeSwitchWin32Tests {

	private static final String SIZE = "size";
	private static final String DARK_KEY = "org.eclipse.swt.internal.win32.useDarkModeExplorerTheme";
	private static final String TITLE_KEY = "org.eclipse.swt.internal.win32.useShellTitleColoring";
	private static final int PW_RENDERFULLCONTENT = 2;

	private Display display;
	private Shell shell;

	@BeforeEach
	void setUp() {
		display = Display.getDefault();
		assumeTrue(OS.IsDarkModeAvailable());
		shell = new Shell(display);
		// out of reach of the mouse, whose hot state differs between the themes
		shell.setLocation(-10000, -10000);
	}

	@AfterEach
	void tearDown() {
		if (shell != null) shell.dispose();
		display.setData(DARK_KEY, false);
		display.setData(TITLE_KEY, false);
	}

	@Test
	void titleBarsMatchFreshTitleBarsAfterSwitch() {
		for (boolean dark : new boolean[] { true, false }) {
			display.setData(TITLE_KEY, !dark);
			Shell switched = titledShell(100);
			display.setData(TITLE_KEY, dark);
			Shell fresh = titledShell(300);
			// both inactive, as the caption of the active shell looks different
			shell.setBounds(500, 100, 100, 100);
			shell.open();
			shell.forceActive();
			dispatchEvents();
			Point size = switched.getSize();
			// the caption is tinted by the wallpaper behind it, so compare its brightness
			int captionHeight = switched.toDisplay(0, 0).y - switched.getLocation().y;
			Rectangle caption = new Rectangle(10, 2, size.x - 20, captionHeight - 4);
			double freshBrightness = brightness(printWindow(fresh, size), caption);
			// DWM repaints a caption that turns light with a delay
			double switchedBrightness = brightness(printWindow(switched, size), caption);
			long end = System.currentTimeMillis() + 2000;
			while (Math.abs(switchedBrightness - freshBrightness) > 10 && System.currentTimeMillis() < end) {
				dispatchEvents();
				switchedBrightness = brightness(printWindow(switched, size), caption);
			}
			assertEquals(freshBrightness, switchedBrightness, 10, "dark=" + dark);
			switched.dispose();
			fresh.dispose();
		}
	}

	private Shell titledShell(int y) {
		Shell titled = new Shell(display);
		titled.setText("Title");
		titled.setBounds(100, y, 300, 150);
		titled.open();
		return titled;
	}

	private static double brightness(ImageData data, Rectangle area) {
		long sum = 0;
		for (int y = area.y; y < area.y + area.height; y++) {
			for (int x = area.x; x < area.x + area.width; x++) {
				RGB rgb = data.palette.getRGB(data.getPixel(x, y));
				sum += rgb.red + rgb.green + rgb.blue;
			}
		}
		return sum / (3.0 * area.width * area.height);
	}

	/** Renders a shell with its DWM caption. */
	private static ImageData printWindow(Shell target, Point size) {
		Image image = new Image(target.getDisplay(), size.x, size.y);
		try {
			GC gc = new GC(image);
			try {
				OS.PrintWindow(target.handle, gc.handle, PW_RENDERFULLCONTENT);
			} finally {
				gc.dispose();
			}
			return image.getImageData();
		} finally {
			image.dispose();
		}
	}

	@Test
	void buttonsMatchFreshButtonsAfterSwitch() {
		Consumer<Boolean> theme = dark -> display.setData(DARK_KEY, dark);
		for (int style : new int[] { SWT.PUSH, SWT.CHECK, SWT.RADIO }) {
			assertSwitchedMatchesFresh(theme, parent -> {
				Button button = new Button(parent, style);
				button.setText("Button");
				return button;
			});
		}
	}

	@Test
	void scrollablesMatchFreshScrollablesAfterSwitch() {
		Consumer<Boolean> theme = dark -> display.setData(DARK_KEY, dark);
		assertSwitchedMatchesFresh(theme, parent -> {
			Table table = new Table(parent, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			table.setHeaderVisible(true);
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText("Column");
			column.setWidth(200);
			for (int i = 0; i < 20; i++) new TableItem(table, SWT.NONE).setText("Item " + i);
			return sized(table);
		});
		assertSwitchedMatchesFresh(theme, parent -> {
			Tree tree = new Tree(parent, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			tree.setHeaderVisible(true);
			TreeColumn column = new TreeColumn(tree, SWT.NONE);
			column.setText("Column");
			column.setWidth(200);
			for (int i = 0; i < 20; i++) new TreeItem(tree, SWT.NONE).setText("Item " + i);
			return sized(tree);
		});
		assertSwitchedMatchesFresh(theme, parent -> {
			List list = new List(parent, SWT.BORDER | SWT.V_SCROLL);
			for (int i = 0; i < 20; i++) list.add("Item " + i);
			return sized(list);
		});
		assertSwitchedMatchesFresh(theme, parent -> {
			Text text = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
			text.setText("Line\n".repeat(20));
			return sized(text);
		});
		assertSwitchedMatchesFresh(theme, parent -> sized(new Composite(parent, SWT.V_SCROLL | SWT.H_SCROLL)));
	}

	/** Gives a scrollable a fixed size, so that its scroll bars show. */
	private static Control sized(Control control) {
		control.setData(SIZE, new Point(120, 80));
		return control;
	}

	/**
	 * Creates a control in one theme, switches to the other and compares it with a
	 * control created in that theme, in both directions.
	 */
	private void assertSwitchedMatchesFresh(Consumer<Boolean> theme, Function<Composite, Control> factory) {
		Canvas focusSink = new Canvas(shell, SWT.NONE);
		focusSink.setBounds(0, 0, 1, 1);
		for (boolean dark : new boolean[] { true, false }) {
			theme.accept(!dark);
			Control switched = factory.apply(shell);
			switched.pack();
			shell.setSize(400, 300);
			shell.open();
			focusSink.forceFocus();
			dispatchEvents();
			theme.accept(dark);
			Control fresh = factory.apply(shell);
			Point size = fresh.getData(SIZE) instanceof Point fixed ? fixed : fresh.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			switched.setBounds(10, 10, size.x, size.y);
			fresh.setBounds(10, size.y + 20, size.x, size.y);
			focusSink.forceFocus();
			dispatchEvents();
			Rectangle area = new Rectangle(0, 0, size.x, size.y);
			assertEquals(0, differentPixels(print(switched, size), print(fresh, size), area), "dark=" + dark + " " + switched);
			switched.dispose();
			fresh.dispose();
		}
	}

	private void dispatchEvents() {
		while (display.readAndDispatch()) {
			// paint
		}
	}

	/** Counts pixels whose colors differ by more than a rounding step. */
	private static int differentPixels(ImageData dataA, ImageData dataB, Rectangle area) {
		int count = 0;
		for (int y = area.y; y < area.y + area.height; y++) {
			for (int x = area.x; x < area.x + area.width; x++) {
				RGB a = dataA.palette.getRGB(dataA.getPixel(x, y));
				RGB b = dataB.palette.getRGB(dataB.getPixel(x, y));
				int distance = Math.max(Math.abs(a.red - b.red), Math.max(Math.abs(a.green - b.green), Math.abs(a.blue - b.blue)));
				if (distance > 2) count++;
			}
		}
		return count;
	}

	private static ImageData print(Control control, Point size) {
		Image image = new Image(control.getDisplay(), size.x, size.y);
		try {
			GC gc = new GC(image);
			try {
				// PrintWindow, which Control.print uses, can return stale content of the shell
				OS.SendMessage(control.handle, OS.WM_PRINT, gc.handle, OS.PRF_NONCLIENT | OS.PRF_CLIENT | OS.PRF_ERASEBKGND | OS.PRF_CHILDREN);
			} finally {
				gc.dispose();
			}
			return image.getImageData();
		} finally {
			image.dispose();
		}
	}
}
