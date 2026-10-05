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
package org.eclipse.swt.visualoracle.impl;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.UnsupportedEnvironmentException;

/**
 * Win32 support stub: no process wrapper and the platform default theme.
 * Every method that still needs a native implementation throws
 * {@link UnsupportedEnvironmentException}; each is marked {@code TODO(win32)}.
 */
public final class Win32PlatformSupport implements PlatformSupport {

	@Override
	public String id() {
		return "win32";
	}

	@Override
	public String fragment() {
		return "win32.win32." + PlatformSupport.architecture();
	}

	@Override
	public void suppressFocus(Control control) {
		// TODO(win32): stop text controls from taking focus or showing a caret; a no-op until captures need it
	}

	@Override
	public void requireRealizedHandle(Control probe) {
		// TODO(win32): prove the probe owns a realized native window handle through a real native call
		throw notImplemented("the activation probe");
	}

	@Override
	public boolean supportsGrabFallback() {
		// TODO(win32): return true once grabFallback is implemented, if a native grab is worthwhile here
		return false;
	}

	@Override
	public CapturedImage grabFallback(Control control, Path scratchDir) {
		// TODO(win32): optional native grab of the control's own window, device pixels at the process zoom
		throw notImplemented("the native grab fallback");
	}

	// TODO(win32): override childLauncherPrefix, prepareChildEnvironment, headlessPrefix and
	// killProcessTree if children need a wrapper, an isolated session or a different kill (none is used now)

	@Override
	public void applyTheme(Theme theme, Map<String, String> variables, Set<String> removedVariables,
			List<String> jvmProperties) {
		if (!theme.isPlatformDefault())
			// TODO(win32): map a named theme or appearance to environment variables or JVM properties
			throw notImplemented("theme '" + theme.id() + "'");
	}

	@Override
	public Theme currentTheme() {
		// TODO(win32): read back the pinned theme once applyTheme supports named themes
		return Theme.PLATFORM_DEFAULT;
	}

	// TODO(win32): override selfTestChecks for checks that only make sense on this platform

	private static UnsupportedEnvironmentException notImplemented(String what) {
		return new UnsupportedEnvironmentException(
				"visual oracle: Win32 support for " + what + " is not implemented yet");
	}
}
