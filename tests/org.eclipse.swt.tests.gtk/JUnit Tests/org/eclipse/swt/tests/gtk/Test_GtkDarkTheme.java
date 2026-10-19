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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.internal.gtk.GTK;
import org.eclipse.swt.internal.gtk3.GTK3;
import org.eclipse.swt.internal.gtk4.GTK4;
import org.eclipse.swt.internal.gtk.GdkRGBA;
import org.eclipse.swt.internal.gtk.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Test_GtkDarkTheme {

	private Display display;
	private Shell shell;
	private byte[] originalTheme;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		originalTheme = OS.getThemeNameBytes();
		// Adwaita ships with GTK and has a dark variant
		setGtkProperty(GTK.gtk_theme_name, "Adwaita\0".getBytes());
		display.setDarkThemePreferred(false);
		shell = new Shell(display);
		shell.setSize(100, 100);
		shell.open();
		processEvents();
	}

	@AfterEach
	public void tearDown() {
		display.setDarkThemePreferred(false);
		shell.dispose();
		if (originalTheme != null) {
			setGtkProperty(GTK.gtk_theme_name, originalTheme);
		}
	}

	@Test
	public void test_setDarkThemePreferred_updatesSystemColors() {
		int gtkBefore = gtkForegroundLuma();
		int widgetBefore = luma(display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND).getRGB());
		int listBefore = luma(display.getSystemColor(SWT.COLOR_LIST_FOREGROUND).getRGB());

		display.setDarkThemePreferred(true);
		processEvents();

		// GTK itself reports the dark variant, with lighter text
		assertEquals(1, preferDarkSetting());
		assertTrue(gtkForegroundLuma() > gtkBefore, "GTK must switch to the lighter dark foreground");
		assertTrue(luma(display.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND).getRGB()) > widgetBefore,
				"COLOR_WIDGET_FOREGROUND must get lighter");
		assertTrue(luma(display.getSystemColor(SWT.COLOR_LIST_FOREGROUND).getRGB()) > listBefore,
				"COLOR_LIST_FOREGROUND must get lighter");
	}

	private static void setGtkProperty(byte[] property, byte[] value) {
		long settings = GTK.gtk_settings_get_default();
		OS.g_object_set(settings, property, value, 0);
	}

	private static int preferDarkSetting() {
		int[] value = new int[1];
		OS.g_object_get(GTK.gtk_settings_get_default(), GTK.gtk_application_prefer_dark_theme, value, 0);
		return value[0];
	}

	private void processEvents() {
		for (int i = 0; i < 50; i++) {
			while (display.readAndDispatch()) {
				// drain
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private int gtkForegroundLuma() {
		long context = GTK.gtk_widget_get_style_context(shell.handle);
		GdkRGBA rgba = new GdkRGBA();
		if (GTK.GTK4) {
			GTK4.gtk_style_context_get_color(context, rgba);
		} else {
			GTK3.gtk_style_context_get_color(context, 0, rgba);
		}
		return luma(new RGB((int) (rgba.red * 255), (int) (rgba.green * 255), (int) (rgba.blue * 255)));
	}

	private static int luma(RGB rgb) {
		return (299 * rgb.red + 587 * rgb.green + 114 * rgb.blue) / 1000;
	}
}
