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
package org.eclipse.swt.tools.ffm;

import java.nio.file.*;

import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk3.*;
import org.eclipse.swt.internal.gtk4.*;

/**
 * Runs generated FFM natives without any SWT library, on the GTK version <code>SWT_GTK4</code> selects, and fails if the
 * process loaded a GTK library of the other version. Run against the "ffm" build of build-gtk.sh.
 */
public class FFMGtkVersionCheck {

	public static void main(String[] args) throws Exception {
		boolean gtk4 = "1".equals(System.getenv("SWT_GTK4"));
		int failures = 0;
		failures += expect("FFM.GTK4", FFM.GTK4, gtk4);
		failures += expect("gtk_get_major_version", GTK_FFM.gtk_get_major_version(), gtk4 ? 4 : 3);
		boolean initialized = gtk4 ? GTK4_FFM.gtk_init_check() : GTK3_FFM.gtk_init_check(new long[1], new long[1]);
		failures += expect("gtk_init_check", initialized, true);
		failures += expect("gdk_display_get_default", GDK_FFM.gdk_display_get_default() != 0, true);
		long window = gtk4 ? GTK4_FFM.gtk_window_new() : GTK3_FFM.gtk_window_new(0);
		failures += expect("gtk_window_new", window != 0, true);
		failures += expect("GdkRGBA_SIZEOF", org.eclipse.swt.internal.gtk.Structs_FFM.GdkRGBA_SIZEOF, gtk4 ? 16L : 32L);
		failures += expect("gtk_accel_group_new (GTK3 only, dynamic)", GTK_FFM.gtk_accel_group_new() != 0, !gtk4);
		String maps = Files.readString(Path.of("/proc/self/maps"));
		boolean gtk3Loaded = maps.contains("libgtk-3.so") || maps.contains("libgdk-3.so");
		boolean gtk4Loaded = maps.contains("libgtk-4.so");
		failures += expect("GTK3 libraries loaded", gtk3Loaded, !gtk4);
		failures += expect("GTK4 libraries loaded", gtk4Loaded, gtk4);
		System.out.println((gtk4 ? "GTK4" : "GTK3") + " without SWT libraries: " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}

	static int expect(String what, Object actual, Object expected) {
		if (actual.equals(expected)) return 0;
		System.out.println("FAILED " + what + ": " + actual + ", expected " + expected);
		return 1;
	}
}
