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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.gtk.GDK;
import org.eclipse.swt.internal.gtk.GTK;
import org.eclipse.swt.internal.gtk3.GTK3;
import org.eclipse.swt.internal.gtk4.GTK4;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.CaptureFailedException;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.Theme;

/**
 * Linux/GTK support: GTK focus control, the X11 grab fallback, Xvfb child
 * wrapping and the {@code GTK_THEME} mapping.
 */
public final class GtkPlatformSupport implements PlatformSupport {

	private static final int GRAB_TIMEOUT_SECONDS = 30;
	private static final int PRINT_ATTEMPTS = 50;
	private static final long FRAME_PERIOD_MILLIS = 17;
	private static final String XVFB_SCREEN = "-screen 0 1600x1200x24";

	@Override
	public String id() {
		return "gtk";
	}

	@Override
	public String fragment() {
		return "gtk.linux." + PlatformSupport.architecture();
	}

	@Override
	public void suppressFocus(Control control) {
		GTK.gtk_widget_set_can_focus(control.handle, false);
		if (control instanceof Combo combo) {
			// An editable combo focuses through its inner GtkEntry, not the box.
			long child = GTK.GTK4 ? GTK4.gtk_combo_box_get_child(combo.handle)
					: GTK3.gtk_bin_get_child(combo.handle);
			if (child != 0 && child != combo.handle)
				GTK.gtk_widget_set_can_focus(child, false);
		}
	}

	@Override
	public String toolkitTag() {
		return "gtk" + GtkVersion.requested();
	}

	@Override
	public void prepareDisplay(Display display) {
		int loaded = GTK.GTK4 ? 4 : 3;
		if (loaded != GtkVersion.requested())
			throw new BackendUnavailableException("GTK" + GtkVersion.requested()
					+ " was requested but the process runs GTK" + loaded + " (SWT fell back to the other natives?)");
	}

	@Override
	public void requireRealizedHandle(Control probe) {
		// A real JNI round trip into the loaded SWT natives; also proves
		// the probe widget is realized with its own window on screen.
		long window = GTK.GTK4 ? (GTK.gtk_widget_get_realized(probe.handle) ? probe.handle : 0)
				: GTK3.gtk_widget_get_window(probe.handle);
		if (window == 0)
			throw new BackendUnavailableException(
					"the probe control owns no native window; SWT natives are not working");
	}

	/** GTK4 {@code GC.copyArea} from a control yields black; {@code Control.print} renders its snapshot node. */
	@Override
	public CapturedImage grabPrimary(Control control) {
		if (!GTK.GTK4)
			return null;
		Display display = control.getDisplay();
		Point size = control.getSize();
		Image image = new Image(display, size.x, size.y);
		try {
			GC gc = new GC(image);
			try {
				// print fails until GTK has allocated the widget
				for (int attempt = 0; !control.print(gc); attempt++) {
					if (attempt >= PRINT_ATTEMPTS)
						throw new CaptureFailedException("'" + control.getClass().getSimpleName()
								+ "' could not be printed on GTK4");
					long end = System.currentTimeMillis() + FRAME_PERIOD_MILLIS;
					while (System.currentTimeMillis() < end) {
						if (!display.readAndDispatch())
							display.sleep();
					}
				}
			} finally {
				gc.dispose();
			}
			return new BasicCapturedImage(image.getImageData(DPIUtil.getDeviceZoom()));
		} finally {
			image.dispose();
		}
	}

	@Override
	public boolean supportsGrabFallback() {
		return true;
	}

	/**
	 * Grabs the control's own X window by id, so the crop is done by the X
	 * server itself and needs no coordinate arithmetic. This is the fix for
	 * the ADR-001 defect: reconstructing device coordinates from scaled
	 * logical ones overshoots whenever the screen density does not match the
	 * reported zoom, which shifted the crop region at zoom 200.
	 *
	 * The grabbed pixels are physical. Their zoom basis follows from the
	 * ratio between physical and logical width; SWT's own DPI machinery then
	 * produces exactly the representation copyArea would deliver, verified
	 * byte-identical at zoom 100 and 200.
	 */
	@Override
	public CapturedImage grabFallback(Control control, Path scratchDir) {
		long window = GTK3.gtk_widget_get_window(control.handle);
		if (window == 0)
			throw new CaptureFailedException("'" + control.getClass().getSimpleName()
					+ "' owns no native window, there is nothing to grab");
		long xid = GDK.gdk_x11_window_get_xid(window);

		Path png = scratchDir.resolve("xgrab-" + Long.toHexString(xid) + "-"
				+ Long.toString(System.nanoTime(), 36) + ".png");
		runImport(xid, png);
		ImageData raw;
		try {
			raw = new ImageData(png.toString());
		} catch (RuntimeException e) {
			throw new CaptureFailedException("cannot decode the grabbed image " + png + ": " + e, e);
		} finally {
			try {
				Files.deleteIfExists(png);
			} catch (IOException e) {
				// best effort; the file lives in scratch space only
			}
		}
		if (raw.width <= 0 || raw.height <= 0)
			throw new CaptureFailedException("grabbed image of '"
					+ control.getClass().getSimpleName() + "' has invalid extent "
					+ raw.width + "x" + raw.height);

		int logicalWidth = Math.max(1, control.getSize().x);
		int logicalHeight = Math.max(1, control.getSize().y);
		int basis = Math.max(100, Math.round(raw.width * 100f / logicalWidth));
		requireConsistentExtent(raw.width, logicalWidth, basis, "width");
		requireConsistentExtent(raw.height, logicalHeight, basis, "height");

		// Declaring the true basis lets SWT scale (or not) exactly as it
		// would scale its own captures.
		Image wrap = new Image(control.getDisplay(), (ImageDataProvider) zoom -> zoom == basis ? raw : null);
		try {
			return new BasicCapturedImage(wrap.getImageData(DPIUtil.getDeviceZoom()));
		} finally {
			wrap.dispose();
		}
	}

	private void requireConsistentExtent(int actual, int logical, int basis, String dimension) {
		int expected = Math.round(logical * basis / 100f);
		if (Math.abs(actual - expected) > 1)
			throw new CaptureFailedException("grabbed " + dimension + " " + actual
					+ " matches neither the logical extent " + logical
					+ " nor the device extent " + expected);
	}

	private void runImport(long xid, Path png) {
		Path log = png.resolveSibling(png.getFileName() + ".log");
		Process process;
		try {
			process = new ProcessBuilder("import", "-window", "0x" + Long.toHexString(xid),
					png.toString())
					.redirectOutput(log.toFile())
					.redirectErrorStream(true)
					.start();
		} catch (IOException e) {
			throw new CaptureFailedException(
					"could not start ImageMagick 'import' (needed for the X11 grab fallback): " + e, e);
		}
		try {
			if (!process.waitFor(GRAB_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new CaptureFailedException("'import' timed out after " + GRAB_TIMEOUT_SECONDS + " s");
			}
			if (process.exitValue() != 0) {
				String stderr = "";
				try {
					stderr = Files.readString(log, StandardCharsets.UTF_8).strip();
				} catch (IOException e) {
					// detail unavailable; the exit code still carries the failure
				}
				throw new CaptureFailedException("'import' failed with exit code " + process.exitValue()
						+ (stderr.isEmpty() ? "" : ": " + stderr));
			}
			if (!Files.isRegularFile(png))
				throw new CaptureFailedException("'import' reported success but wrote no image");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CaptureFailedException("interrupted while waiting for 'import'", e);
		} finally {
			try {
				Files.deleteIfExists(log);
			} catch (IOException e) {
				// best effort
			}
		}
	}

	@Override
	public List<String> childLauncherPrefix() {
		// setsid puts the child in its own process group. Process.destroyForcibly()
		// kills only the direct child, which is the xvfb-run wrapper; the JVM and the
		// Xvfb server it starts are grandchildren and survive, leaking one JVM and one
		// X server per timed-out child. Killing the whole group is what actually reaps them.
		return List.of("setsid", "xvfb-run", "-a", "-s", XVFB_SCREEN);
	}

	@Override
	public void prepareChildEnvironment(Map<String, String> environment) {
		environment.remove("DISPLAY");
		environment.remove("WAYLAND_DISPLAY");
		environment.remove("XDG_SESSION_TYPE");
		environment.put("GDK_BACKEND", "x11");
		environment.put("LIBGL_ALWAYS_SOFTWARE", "1");
		GtkVersion.applyTo(environment);
	}

	@Override
	public void prepareProbeEnvironment(Map<String, String> environment) {
		environment.remove("WAYLAND_DISPLAY");
		environment.remove("XDG_SESSION_TYPE");
		environment.put("GDK_BACKEND", "x11");
		GtkVersion.applyTo(environment);
	}

	@Override
	public List<String> headlessPrefix(String... extraEnvironment) {
		List<String> prefix = new java.util.ArrayList<>();
		prefix.add("env");
		prefix.addAll(List.of("-u", "WAYLAND_DISPLAY", "-u", "XDG_SESSION_TYPE", "-u", "DISPLAY",
				"GDK_BACKEND=x11", "LIBGL_ALWAYS_SOFTWARE=1"));
		prefix.addAll(List.of(extraEnvironment));
		prefix.add("xvfb-run");
		prefix.addAll(List.of("-a", "-s", XVFB_SCREEN));
		return prefix;
	}

	/**
	 * Kills the child and everything it started. Because the child runs under
	 * {@code setsid}, its pid is also its process group id, so a negative pid
	 * signals the whole group: the xvfb-run wrapper, the JVM under it and the
	 * Xvfb server. Falls back to the direct child if the group kill is
	 * unavailable, which at worst restores the previous leaky behaviour rather
	 * than failing the run.
	 */
	@Override
	public void killProcessTree(Process process) {
		long pid = process.pid();
		try {
			new ProcessBuilder("kill", "-KILL", "-" + pid).start().waitFor(5, TimeUnit.SECONDS);
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException)
				Thread.currentThread().interrupt();
		}
		PlatformSupport.super.killProcessTree(process);
	}

	@Override
	public void applyTheme(Theme theme, Map<String, String> variables, Set<String> removedVariables,
			List<String> jvmProperties) {
		if (theme.isPlatformDefault()) {
			removedVariables.add("GTK_THEME");
		} else {
			variables.put("GTK_THEME", theme.id());
		}
	}

	@Override
	public Theme currentTheme() {
		String gtkTheme = System.getenv("GTK_THEME");
		return new Theme(gtkTheme == null ? "" : gtkTheme);
	}

	@Override
	public void verifyThemeMapping(LaunchConfig themed, LaunchConfig platformDefault) {
		require("Adwaita".equals(themed.variables().get("GTK_THEME")),
				"theme Adwaita did not map to the GTK_THEME variable");
		require(!themed.removedVariables().contains("GTK_THEME"), "themed config removes GTK_THEME");
		require(platformDefault.variables().isEmpty() && platformDefault.removedVariables().contains("GTK_THEME"),
				"platform default theme must remove GTK_THEME instead of setting it");
	}

	@Override
	public List<NamedCheck> selfTestChecks(SelfTestHost host) {
		return List.of(
				new NamedCheck("xgrab-agrees-with-copyarea-at-zoom100", () -> checkXGrabZoom100(host)),
				new NamedCheck("xgrab-agrees-with-copyarea-at-zoom200", () -> checkXGrabZoom200(host)));
	}

	/** Fallback and primary strategy must agree exactly at unit zoom. */
	private static void checkXGrabZoom100(SelfTestHost host) throws Exception {
		ProbeResult copyArea = host.runProbe("COPY_AREA", null);
		ProbeResult xgrab = host.runProbe("X11_GRAB", null);
		require(xgrab.width() == copyArea.width() && xgrab.height() == copyArea.height(),
				"strategies disagree on extent at zoom 100: " + xgrab + " vs " + copyArea);
		require(xgrab.sha256().equals(copyArea.sha256()),
				"X11 grab and copyArea disagree at zoom 100: "
						+ xgrab.sha256() + " vs " + copyArea.sha256());
	}

	/**
	 * The zoom-200 agreement is what proves the crop-origin fix recorded in
	 * ADR-001: with the old formula the X11 grab captured a shifted region at
	 * zoom 200, so these two probes could never agree byte for byte.
	 */
	private static void checkXGrabZoom200(SelfTestHost host) throws Exception {
		ProbeResult copyArea = host.runProbe("COPY_AREA", "-Dswt.autoScale=200");
		ProbeResult xgrab = host.runProbe("X11_GRAB", "-Dswt.autoScale=200");
		Point preferred = host.preferredSize();
		int expectedWidth = Math.round(preferred.x * 2f);
		int expectedHeight = Math.round(preferred.y * 2f);
		require(copyArea.width() == expectedWidth && copyArea.height() == expectedHeight,
				"copyArea is not device-resolution at zoom 200: " + copyArea);
		require(xgrab.width() == expectedWidth && xgrab.height() == expectedHeight,
				"X11 grab is not device-resolution at zoom 200: " + xgrab);
		require(xgrab.sha256().equals(copyArea.sha256()),
				"X11 grab and copyArea disagree at zoom 200: "
						+ xgrab.sha256() + " vs " + copyArea.sha256()
						+ "; the crop origin fix regressed");
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
