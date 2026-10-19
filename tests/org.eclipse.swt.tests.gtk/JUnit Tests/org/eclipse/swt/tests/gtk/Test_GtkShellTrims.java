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
package org.eclipse.swt.tests.gtk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Test_GtkShellTrims {

	private static final int TRIM_TITLE_RESIZE = 4;

	private Display display;
	private String oldUserHome;
	private Path userHome;
	private int[] oldWidths;
	private int[] oldHeights;

	@BeforeEach
	public void setUp() throws Exception {
		display = Display.getDefault();
		oldWidths = trims("trimWidths").clone();
		oldHeights = trims("trimHeights").clone();
		oldUserHome = System.getProperty("user.home");
		// the shared Display reads user.home only at creation, so the cache is set by reflection
		// and user.home is redirected for the write-back only
		userHome = Files.createTempDirectory("swt-trims");
		System.setProperty("user.home", userHome.toString());
	}

	@AfterEach
	public void tearDown() throws Exception {
		System.setProperty("user.home", oldUserHome);
		System.arraycopy(oldWidths, 0, trims("trimWidths"), 0, oldWidths.length);
		System.arraycopy(oldHeights, 0, trims("trimHeights"), 0, oldHeights.length);
		try (Stream<Path> files = Files.walk(userHome)) {
			files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	@Test
	public void test_measuredTrimReplacesStoredValue() throws Exception {
		assumeFalse(System.getProperty("org.eclipse.swt.internal.gtk.version", "").startsWith("4"), "GTK4 does not use the trim cache");
		assumeFalse(ignoreTrim(), "trim measurement is disabled for this window manager");
		trims("trimWidths")[TRIM_TITLE_RESIZE] = 100;
		trims("trimHeights")[TRIM_TITLE_RESIZE] = 100;

		Shell shell = new Shell(display, SWT.SHELL_TRIM);
		try {
			shell.setSize(300, 200);
			shell.open();
			Rectangle client = shell.getClientArea();
			int width = trims("trimWidths")[TRIM_TITLE_RESIZE];
			int height = trims("trimHeights")[TRIM_TITLE_RESIZE];
			assertEquals(shell.getSize().x - client.width, width);
			assertEquals(shell.getSize().y - client.height, height);
			assertFalse(width == 100 || height == 100, "stored trim must be replaced by the measured one");

			Properties props = new Properties();
			try (FileInputStream in = new FileInputStream(userHome.resolve(".swt/trims.prefs").toFile())) {
				props.load(in);
			}
			assertEquals(width, stored(props, "trimWidths"));
			assertEquals(height, stored(props, "trimHeights"));
		} finally {
			shell.dispose();
		}
	}

	private static int stored(Properties props, String key) {
		return Integer.parseInt(props.getProperty(key).trim().split("\\s+")[TRIM_TITLE_RESIZE]);
	}

	private int[] trims(String name) throws Exception {
		Field field = Display.class.getDeclaredField(name);
		field.setAccessible(true);
		return (int[]) field.get(display);
	}

	private boolean ignoreTrim() throws Exception {
		Field field = Display.class.getDeclaredField("ignoreTrim");
		field.setAccessible(true);
		return field.getBoolean(display);
	}
}
