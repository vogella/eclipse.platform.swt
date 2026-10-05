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
 * Cocoa support stub: no process wrapper and the platform default theme.
 * Every method that still needs a native implementation throws
 * {@link UnsupportedEnvironmentException}; each is marked {@code TODO(cocoa)}.
 */
public final class CocoaPlatformSupport implements PlatformSupport {

	@Override
	public String id() {
		return "cocoa";
	}

	@Override
	public String fragment() {
		return "cocoa.macosx." + PlatformSupport.architecture();
	}

	@Override
	public void suppressFocus(Control control) {
		// TODO(cocoa): stop text controls from taking focus or showing a caret; a no-op until captures need it
	}

	@Override
	public void requireRealizedHandle(Control probe) {
		// TODO(cocoa): prove the probe owns a realized native view through a real native call
		throw notImplemented("the activation probe");
	}

	@Override
	public boolean supportsGrabFallback() {
		// TODO(cocoa): return true once grabFallback is implemented, if a native grab is worthwhile here
		return false;
	}

	@Override
	public CapturedImage grabFallback(Control control, Path scratchDir) {
		// TODO(cocoa): optional native grab of the control's own window, device pixels at the process zoom
		throw notImplemented("the native grab fallback");
	}

	// TODO(cocoa): override childLauncherPrefix, prepareChildEnvironment, headlessPrefix and
	// killProcessTree if children need a wrapper, an isolated session or a different kill (none is used now)

	@Override
	public void applyTheme(Theme theme, Map<String, String> variables, Set<String> removedVariables,
			List<String> jvmProperties) {
		if (!theme.isPlatformDefault())
			// TODO(cocoa): map a named theme or appearance to environment variables or JVM properties
			throw notImplemented("theme '" + theme.id() + "'");
	}

	@Override
	public Theme currentTheme() {
		// TODO(cocoa): read back the pinned theme once applyTheme supports named themes
		return Theme.PLATFORM_DEFAULT;
	}

	// TODO(cocoa): override selfTestChecks for checks that only make sense on this platform

	private static UnsupportedEnvironmentException notImplemented(String what) {
		return new UnsupportedEnvironmentException(
				"visual oracle: Cocoa support for " + what + " is not implemented yet");
	}
}
