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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.CaptureFailedException;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.UnsupportedEnvironmentException;

/**
 * Everything the harness needs from the windowing platform that plain SWT API
 * cannot express: focus suppression, native handle checks, the optional grab
 * fallback, child process wrapping and the theme mapping.
 *
 * Exactly one implementation exists per platform, named
 * {@code <Gtk|Cocoa|Win32>PlatformSupport} in this package and compiled from
 * {@code harness/src-gtk}, {@code src-cocoa} or {@code src-win32}, so
 * platform-neutral code never links against platform-internal SWT classes.
 */
public interface PlatformSupport {

	/** The SWT platform name this implementation serves: gtk, cocoa or win32. */
	String id();

	/** The SWT binary fragment suffix, for example {@code gtk.linux.x86_64}. */
	String fragment();

	/** The directory holding this platform's SWT natives below {@code repoRoot}. */
	default Path binariesDirectory(Path repoRoot) {
		return repoRoot.resolve("binaries").resolve("org.eclipse.swt." + fragment());
	}

	/** Makes {@code control} (and an inner editable child) unable to take focus or show a caret. */
	void suppressFocus(Control control);

	/** Proves {@code probe} is realized with a native handle of its own, else throws. */
	void requireRealizedHandle(Control probe) throws BackendUnavailableException;

	/** True if {@link #grabFallback} is implemented on this platform. */
	boolean supportsGrabFallback();

	/**
	 * The fallback capture strategy: grabs the control's own native window
	 * through the OS instead of {@code GC.copyArea}, in device pixels at the
	 * process zoom.
	 *
	 * @throws UnsupportedEnvironmentException where {@link #supportsGrabFallback()} is false
	 */
	CapturedImage grabFallback(Control control, Path scratchDir) throws CaptureFailedException;

	/** Command prefix that precedes the JVM when the launcher starts a child. Empty means no wrapper. */
	default List<String> childLauncherPrefix() {
		return List.of();
	}

	/** Adjusts the environment of a launcher child before its theme variables are applied. */
	default void prepareChildEnvironment(Map<String, String> environment) {
	}

	/** Adjusts the environment of a selftest probe that inherits the current display. */
	default void prepareProbeEnvironment(Map<String, String> environment) {
	}

	/**
	 * Command prefix for harness tools that start a child JVM themselves, with
	 * optional {@code NAME=value} variables placed inside the prefix. Empty
	 * (plus an {@code env} prefix for the variables) means no wrapper.
	 */
	default List<String> headlessPrefix(String... extraEnvironment) {
		if (extraEnvironment.length == 0)
			return List.of();
		List<String> prefix = new java.util.ArrayList<>();
		prefix.add("env");
		prefix.addAll(List.of(extraEnvironment));
		return prefix;
	}

	/** Kills a launcher child and everything it started. */
	default void killProcessTree(Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}

	/**
	 * Maps a theme to the settings that realise it in a child. A platform
	 * default theme must remove any inherited override instead of setting one.
	 */
	void applyTheme(Theme theme, Map<String, String> variables, Set<String> removedVariables,
			List<String> jvmProperties);

	/** The theme this process is pinned to; {@link Theme#PLATFORM_DEFAULT} if none. */
	Theme currentTheme();

	/** Selftest hook: asserts {@link #applyTheme} output for a named theme and for the default. */
	default void verifyThemeMapping(LaunchConfig themed, LaunchConfig platformDefault) {
	}

	/** Platform-only selftest checks, run in order after the cross-process determinism check. */
	default List<NamedCheck> selfTestChecks(SelfTestHost host) {
		return List.of();
	}

	/** One selftest check contributed by a platform. */
	record NamedCheck(String name, CheckBody body) {
	}

	/** Body of a {@link NamedCheck}; throws to fail. */
	interface CheckBody {
		void run() throws Exception;
	}

	/** Result line of a capture probe child. */
	record ProbeResult(String sha256, int width, int height) {
		@Override
		public String toString() {
			return width + "x" + height + " sha=" + sha256;
		}
	}

	/** What the selftest offers to platform checks. */
	interface SelfTestHost {
		/** Captures the reference specimen in a child process with {@code strategy}. */
		ProbeResult runProbe(String strategy, String extraJvmOption) throws Exception;

		/** Preferred size of the reference specimen at zoom 100. */
		Point preferredSize();
	}

	/** The cpu architecture part of a fragment name: x86_64 or aarch64. */
	static String architecture() {
		String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
		return switch (arch) {
			case "amd64", "x86_64", "x64" -> "x86_64";
			case "aarch64", "arm64" -> "aarch64";
			default -> arch;
		};
	}

	/** The implementation for the platform SWT runs on. */
	static PlatformSupport current() {
		return Holder.INSTANCE;
	}

	/** Lazy holder so the platform class loads once, on first use. */
	final class Holder {
		private static final PlatformSupport INSTANCE = load();

		private Holder() {
		}

		private static PlatformSupport load() {
			String platform = SWT.getPlatform();
			String name = switch (platform) {
				case "gtk" -> "GtkPlatformSupport";
				case "cocoa" -> "CocoaPlatformSupport";
				case "win32" -> "Win32PlatformSupport";
				default -> throw new BackendUnavailableException("no visual oracle platform support for SWT platform '"
						+ platform + "'");
			};
			try {
				return (PlatformSupport) Class.forName(PlatformSupport.class.getPackageName() + "." + name)
						.getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException e) {
				throw new BackendUnavailableException("platform support " + name
						+ " is not compiled in; build the harness with tools/oracle/build-harness.sh: " + e);
			}
		}
	}
}
