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

import org.eclipse.swt.internal.gtk.*;

/**
 * Initializes <code>OS</code> and prints which GTK major version it ended up with, which GTK libraries the process
 * mapped and how the initialization failed. Run on the JNI and on the FFM build by test-gtk-select.sh.
 */
public class FFMGtkSelectCheck {

	public static void main(String[] args) throws Exception {
		String outcome;
		try {
			boolean linux = OS.IsLinux;
			outcome = "gtk=" + (GTK.GTK4 ? 4 : 3) + " major=" + GTK.gtk_get_major_version() + (linux ? "" : "?");
		} catch (Throwable e) {
			outcome = "failed=" + e.getClass().getSimpleName();
			System.out.println("MESSAGE " + e.getMessage().lines().findFirst().orElse(""));
		}
		String maps = Files.readString(Path.of("/proc/self/maps"));
		System.out.println("OUTCOME " + outcome + " mapped=" + (maps.contains("libgtk-3.so.") ? "3" : "")
				+ (maps.contains("libgtk-4.so.") ? "4" : ""));
	}
}
