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
import java.util.Optional;
import java.util.Set;

import org.eclipse.swt.internal.cocoa.NSAppearance;
import org.eclipse.swt.internal.cocoa.NSApplication;
import org.eclipse.swt.internal.cocoa.NSView;
import org.eclipse.swt.internal.cocoa.NSWindow;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.UnsupportedEnvironmentException;

/**
 * macOS support. Captures are taken in-process by {@code GC.copyArea}, which on
 * Cocoa renders the view hierarchy into a bitmap ({@code cacheDisplayInRect}),
 * so no screen grab, no Screen Recording permission and no window ordering is
 * involved; see ADR-003. Children never activate (prohibited activation policy),
 * so they cannot steal focus and every window renders in its inactive state.
 */
public final class CocoaPlatformSupport implements PlatformSupport {

	/** JVM property carrying the pinned appearance, Light or Dark, into a child. */
	static final String APPEARANCE_PROPERTY = "oracle.cocoa.appearance";
	private static final long ACTIVATION_POLICY_PROHIBITED = 2;
	private static final long LAUNCH_TIMEOUT_MILLIS = 5000;

	@Override
	public String id() {
		return "cocoa";
	}

	@Override
	public String fragment() {
		return "cocoa.macosx." + PlatformSupport.architecture();
	}

	@Override
	public List<String> jvmArguments() {
		return List.of("-XstartOnFirstThread");
	}

	@Override
	public boolean paintEventOptional() {
		return true;
	}

	@Override
	public boolean allowsNativeSizeClamp() {
		return true;
	}

	@Override
	public void suppressFocus(Control control) {
		control.view.setFocusRingType(OS.NSFocusRingTypeNone);
	}

	@Override
	public void requireRealizedHandle(Control probe) {
		// view.window() is a real Objective-C message send into the loaded natives
		NSView view = probe.view;
		if (view == null || view.id == 0)
			throw new BackendUnavailableException("the probe control owns no native view; SWT natives are not working");
		NSWindow window = view.window();
		if (window == null || window.id == 0)
			throw new BackendUnavailableException("the probe control's view is not in a native window");
	}

	@Override
	public boolean supportsGrabFallback() {
		return false;
	}

	@Override
	public CapturedImage grabFallback(Control control, Path scratchDir) {
		throw new UnsupportedEnvironmentException(
				"visual oracle: Cocoa has no grab fallback; copyArea already renders the view itself (ADR-003)");
	}

	@Override
	public void prepareDisplay(Display display) {
		NSApplication app = NSApplication.sharedApplication();
		// SWT's applicationDidFinishLaunching sets the regular policy and activates; it
		// arrives from the event loop, so wait for it or it can undo the pin mid-run.
		long deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT_MILLIS;
		while (!finishedLaunching() && System.currentTimeMillis() < deadline) {
			if (!display.readAndDispatch())
				sleepQuietly(10);
		}
		if (!finishedLaunching())
			throw new BackendUnavailableException(
					"NSApplication did not finish launching within " + LAUNCH_TIMEOUT_MILLIS + " ms");
		while (display.readAndDispatch()) {
			// drain the launch events
		}
		app.setActivationPolicy(ACTIVATION_POLICY_PROHIBITED);
		OS.objc_msgSend(app.id, OS.sel_registerName("deactivate"));
		String appearance = System.getProperty(APPEARANCE_PROPERTY, "");
		if (appearance.isEmpty())
			return;
		try {
			display.setDarkThemePreferred("Dark".equals(appearance));
		} catch (LinkageError e) {
			throw new UnsupportedEnvironmentException(
					"visual oracle: this SWT cannot pin the appearance (Display.setDarkThemePreferred missing)");
		}
	}

	/** Measured: no catalog specimen renders differently under RIGHT_TO_LEFT on Cocoa. */
	@Override
	public String mirrorSpecimenId() {
		return null;
	}

	@Override
	public Optional<Theme> alternateTheme() {
		return Optional.of(new Theme(Display.isSystemDarkTheme() ? "Light" : "Dark"));
	}

	@Override
	public Theme mappingTestTheme() {
		return new Theme("Dark");
	}

	@Override
	public void applyTheme(Theme theme, Map<String, String> variables, Set<String> removedVariables,
			List<String> jvmProperties) {
		if (theme.isPlatformDefault())
			return;
		if (!theme.id().equals("Light") && !theme.id().equals("Dark"))
			throw new UnsupportedEnvironmentException(
					"visual oracle: Cocoa knows the themes Light and Dark, not '" + theme.id() + "'");
		jvmProperties.add("-D" + APPEARANCE_PROPERTY + "=" + theme.id());
	}

	@Override
	public Theme currentTheme() {
		if (System.getProperty(APPEARANCE_PROPERTY, "").isEmpty())
			return Theme.PLATFORM_DEFAULT;
		// read back what the application really renders with, not what was requested
		long appearance = OS.objc_msgSend(NSApplication.sharedApplication().id,
				OS.sel_registerName("effectiveAppearance"));
		String name = appearance == 0 ? "" : new NSAppearance(appearance).name().getString();
		return new Theme(name.contains("Dark") ? "Dark" : "Light");
	}

	@Override
	public void verifyThemeMapping(LaunchConfig themed, LaunchConfig platformDefault) {
		require(themed.jvmProperties().contains("-D" + APPEARANCE_PROPERTY + "=Dark"),
				"theme Dark did not map to the appearance property: " + themed.jvmProperties());
		require(platformDefault.jvmProperties().stream().noneMatch(p -> p.startsWith("-D" + APPEARANCE_PROPERTY)),
				"the platform default theme must not pin an appearance");
	}

	@Override
	public List<NamedCheck> selfTestChecks(SelfTestHost host) {
		return List.of(new NamedCheck("cocoa-app-never-activates", CocoaPlatformSupport::checkNeverActivates));
	}

	/** Children must not be able to take the focus, or active-window rendering would leak into captures. */
	private static void checkNeverActivates() {
		NSApplication app = NSApplication.sharedApplication();
		long policy = OS.objc_msgSend(app.id, OS.sel_registerName("activationPolicy"));
		require(policy == ACTIVATION_POLICY_PROHIBITED, "activation policy is " + policy + ", not prohibited");
		require(!app.isActive(), "the application is active");
	}

	private static boolean finishedLaunching() {
		long current = OS.objc_msgSend(OS.objc_getClass("NSRunningApplication"), OS.sel_registerName("currentApplication"));
		return OS.objc_msgSend_bool(current, OS.sel_registerName("isFinishedLaunching"));
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
