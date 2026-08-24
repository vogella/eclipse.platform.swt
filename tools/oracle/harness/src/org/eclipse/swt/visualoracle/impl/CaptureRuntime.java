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
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.gtk.GDK;
import org.eclipse.swt.internal.gtk3.GTK3;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.Capture;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.CaptureFailedException;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.UnsupportedEnvironmentException;
import org.eclipse.swt.visualoracle.spi.UnsupportedSpecimenException;

/**
 * Production capture runtime behind the frozen {@link Capture} interface,
 * implementing the ADR-001 decision.
 *
 * The runtime owns everything around the pixels: one shell per display,
 * reused across specimens (children disposed and bounds reset in between, so
 * specimen N cannot see anything of specimen N-1), event loop settling until
 * the widget has really finished painting, and a forced final redraw before
 * pixels are taken.
 *
 * Two strategies exist behind the same interface: {@link Strategy#COPY_AREA}
 * is the primary path and {@link Strategy#X11_GRAB} is the fallback for
 * content {@code copyArea} cannot reach, such as native popups outside the
 * control's own window.
 */
public class CaptureRuntime implements Capture, AutoCloseable {

	/** How pixels are taken; ADR-001 decided the order of preference. */
	public enum Strategy {
		/** {@code GC.copyArea} from the on-screen control (primary). */
		COPY_AREA,
		/** X11 grab of the control's own window by id via ImageMagick import (fallback). */
		X11_GRAB
	}

	private static final int MARGIN = 12;
	private static final int SETTLE_TIMEOUT_MILLIS = 5000;
	private static final int MAX_SETTLE_CYCLES = 50;
	private static final int GRAB_TIMEOUT_SECONDS = 30;

	private final Strategy strategy;
	private Shell shell;

	public CaptureRuntime() {
		this(Strategy.COPY_AREA);
	}

	public CaptureRuntime(Strategy strategy) {
		if (strategy == null)
			throw new IllegalArgumentException("strategy must not be null");
		this.strategy = strategy;
	}

	@Override
	public CapturedImage capture(Specimen specimen, Backend backend, RenderEnv env) {
		if (!backend.supports(specimen))
			throw new UnsupportedSpecimenException(
					"backend '" + backend.id() + "' does not support specimen '" + specimen.id() + "'");
		Display display = Display.getCurrent();
		if (display == null || display.isDisposed())
			throw new CaptureFailedException("capture needs a current Display on the UI thread");
		requireSameEnvironment(env, display);

		BasicSpecimenContext ctx = new BasicSpecimenContext(env);
		try {
			Control control = host(specimen, display, ctx);
			settle(display, control);
			return switch (strategy) {
				case COPY_AREA -> grabByCopyArea(control);
				case X11_GRAB -> grabByX11(control);
			};
		} catch (UnsupportedSpecimenException | UnsupportedEnvironmentException e) {
			throw e;
		} catch (CaptureFailedException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new CaptureFailedException("capture of '" + specimen.id() + "' failed: " + e, e);
		} finally {
			ctx.dispose();
		}
	}

	/** Releases the reused shell; the display stays owned by the caller. */
	@Override
	public void close() {
		if (shell != null && !shell.isDisposed())
			shell.dispose();
		shell = null;
	}

	// ------------------------------------------------------------- hosting

	/**
	 * Prepares the shared shell for one specimen: clears the previous
	 * specimen's controls, resizes to the declared preferred size and creates
	 * the new control. Nothing captured later can contain leftovers, because
	 * every old child is disposed and the whole client area is invalidated
	 * before settling starts.
	 */
	private Control host(Specimen specimen, Display display, SpecimenContext ctx) {
		Shell host = shellFor(display);
		for (Control child : host.getChildren())
			child.dispose();

		org.eclipse.swt.graphics.Point preferred = specimen.preferredSize();
		if (preferred == null || preferred.x <= 0 || preferred.y <= 0)
			throw new CaptureFailedException("specimen '" + specimen.id()
					+ "' declared an invalid preferred size " + preferred);

		host.setSize(preferred.x + 2 * MARGIN, preferred.y + 2 * MARGIN);
		Control control;
		try {
			control = specimen.create(host, ctx);
		} catch (Throwable t) {
			throw new CaptureFailedException(
					"specimen '" + specimen.id() + "' threw during creation: " + t, t);
		}
		if (control == null || control.isDisposed())
			throw new CaptureFailedException("specimen '" + specimen.id() + "' created no usable control");

		control.setBounds(MARGIN, MARGIN, preferred.x, preferred.y);
		host.layout();
		host.redraw();
		if (!host.isVisible())
			host.open();
		return control;
	}

	private Shell shellFor(Display display) {
		if (shell == null || shell.isDisposed() || shell.getDisplay() != display)
			shell = new Shell(display);
		return shell;
	}

	// ------------------------------------------------------------ settling

	private static final class Activity {
		boolean paint;
		boolean resizedOrMoved;

		void reset() {
			paint = false;
			resizedOrMoved = false;
		}

		boolean any() {
			return paint || resizedOrMoved;
		}
	}

	/**
	 * Waits until {@code control} has genuinely finished painting.
	 *
	 * Phase 1 waits for the first Paint event, which proves the widget is
	 * realized and drew once. Phase 2 then forces pending damage out with
	 * {@link Control#update()} and drains the queue until two consecutive
	 * cycles pass without any paint, resize or move activity.
	 *
	 * That condition is sufficient because on X11/GTK all drawing happens in
	 * response to events processed on the display thread: after the first
	 * paint, any remaining work can only arrive as further configure/expose
	 * events. Forcing them out and seeing a full drain produce no activity
	 * means every damage region has been drawn into the window buffer and no
	 * redraw is queued; nothing can change the pixels afterwards without a
	 * new event arriving spontaneously. The second quiet cycle closes the
	 * race where an expose was already queued but not yet dispatched when the
	 * first quiet check ran. Time-driven repaints (animations) are outside
	 * this guarantee; they are the determinism lint's concern (T12), not the
	 * settler's.
	 */
	private void settle(Display display, Control control) {
		Activity activity = new Activity();
		Listener recorder = e -> {
			if (e.type == SWT.Paint)
				activity.paint = true;
			else
				activity.resizedOrMoved = true;
		};
		control.addListener(SWT.Paint, recorder);
		control.addListener(SWT.Resize, recorder);
		control.addListener(SWT.Move, recorder);

		long deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MILLIS;
		while (!activity.paint && System.currentTimeMillis() < deadline && !control.isDisposed()) {
			if (!display.readAndDispatch())
				sleepBriefly();
		}
		if (control.isDisposed())
			throw new CaptureFailedException("control disposed while waiting for its first paint");
		if (!activity.paint)
			throw new CaptureFailedException("no paint event observed for '"
					+ control.getClass().getSimpleName() + "' within " + SETTLE_TIMEOUT_MILLIS + " ms");

		int quietCycles = 0;
		int cycles = 0;
		while (System.currentTimeMillis() < deadline && !control.isDisposed()) {
			activity.reset();
			control.update();
			while (display.readAndDispatch()) {
				// drain everything the update flushed
			}
			cycles++;
			quietCycles = activity.any() ? 0 : quietCycles + 1;
			if (quietCycles >= 2 || cycles >= MAX_SETTLE_CYCLES)
				break;
		}
		if (control.isDisposed())
			throw new CaptureFailedException("control disposed during event loop settling");
		if (quietCycles < 2)
			throw new CaptureFailedException("'" + control.getClass().getSimpleName()
					+ "' did not stabilize within " + SETTLE_TIMEOUT_MILLIS + " ms");
		control.update();
	}

	private static void sleepBriefly() {
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ------------------------------------------------------------ grabbing

	private CapturedImage grabByCopyArea(Control control) {
		org.eclipse.swt.graphics.Point size = control.getSize();
		Image image = new Image(control.getDisplay(), size.x, size.y);
		try {
			GC gc = new GC(control);
			try {
				gc.copyArea(image, 0, 0);
			} finally {
				gc.dispose();
			}
			// Device-zoom representation: what is really on screen, not a
			// downscaled variant of it.
			return new BasicCapturedImage(image.getImageData(DPIUtil.getDeviceZoom()));
		} finally {
			image.dispose();
		}
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
	private CapturedImage grabByX11(Control control) {
		if (!"gtk".equals(SWT.getPlatform()))
			throw new UnsupportedEnvironmentException(
					"the X11 grab fallback requires gtk/X11, found platform: " + SWT.getPlatform());
		long window = GTK3.gtk_widget_get_window(control.handle);
		if (window == 0)
			throw new CaptureFailedException("'" + control.getClass().getSimpleName()
					+ "' owns no native window, there is nothing to grab");
		long xid = GDK.gdk_x11_window_get_xid(window);

		Path png = scratchDir().resolve("xgrab-" + Long.toHexString(xid) + "-"
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

	/**
	 * Directory for temporary grab files; inside the task scratch root so
	 * runs never scatter files elsewhere. Override with -Doracle.scratchDir.
	 */
	static Path scratchDir() {
		Path dir = Path.of(System.getProperty("oracle.scratchDir", "/tmp/opencode/oracle-t04"), "grabs");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new CaptureFailedException("cannot create scratch directory " + dir + ": " + e, e);
		}
		return dir;
	}

	private void requireSameEnvironment(RenderEnv requested, Display display) {
		RenderEnv actual = SwtRenderEnvs.current(display);
		if (!actual.equals(requested))
			throw new UnsupportedEnvironmentException(
					"process is pinned to " + actual + ", cannot capture under " + requested
							+ "; spawn one harness process per environment (see RenderEnv)");
	}
}
