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
import java.util.Arrays;
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
 * the widget has really finished painting, a forced final redraw before
 * pixels are taken, and a stability check that returns pixels only once one
 * rendering has persisted unchanged across a sustained observation window,
 * which is what makes theme transition animation unable to leak into
 * captures.
 *
 * Two strategies exist behind the same interface: {@link Strategy#COPY_AREA}
 * is the primary path and {@link Strategy#X11_GRAB} is the fallback for
 * content {@code copyArea} cannot reach, such as native popups outside the
 * control's own window.
 *
 * A stability wait that only proves nothing settled <em>yet</em> is retried
 * once with a larger {@link SettleBudget} before it becomes failure data,
 * because under CPU contention a slow machine and an unstable widget look
 * identical to a single attempt; see {@link #grabWithStablePixels}.
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
	/**
	 * How long the grabbed bytes must persist unchanged, while the event loop
	 * stays live, before they are accepted as the settled rendering. Must
	 * outlast any transient state: measured start latencies of GTK theme
	 * transitions are below 100 ms and their animation spans around 200 ms
	 * (SCR-1), so no mid-transition value survives a 250 ms observation.
	 *
	 * This window is a correctness parameter, not a load parameter, and is
	 * never scaled: shortening it under load would let a mid-transition frame
	 * pass as settled. Load only stretches wall-clock time between frames,
	 * which is what {@link SettleBudget} bounds.
	 */
	private static final long STABILITY_HOLD_MILLIS = 250;
	/**
	 * Event-loop time between two grabs of the stability check, roughly one
	 * display refresh period, so any frame the clock owes the window can
	 * actually land before the next grab.
	 */
	private static final long FRAME_PERIOD_MILLIS = 17;
	/**
	 * How long the forced repaint of the faithfulness check may take to be
	 * delivered before a hold is treated as unconfirmed. Generous, because
	 * starvation is exactly the case it must survive; missing it only costs
	 * budget, never correctness.
	 */
	private static final long REPAINT_GRACE_MILLIS = 1000;
	private static final int GRAB_TIMEOUT_SECONDS = 30;

	private final Strategy strategy;
	private final SettleBudget defaultBudget;
	private final SettleBudget retryBudget;
	private Shell shell;

	public CaptureRuntime() {
		this(Strategy.COPY_AREA);
	}

	public CaptureRuntime(Strategy strategy) {
		this(strategy, SettleBudget.DEFAULT, SettleBudget.EXTENDED);
	}

	/**
	 * @param defaultBudget bound for the first settle attempt
	 * @param retryBudget   bound for the one retry after a timeout, or null to
	 *                      fail on the first timeout (tests use this to prove
	 *                      the retry path earns its keep)
	 */
	public CaptureRuntime(Strategy strategy, SettleBudget defaultBudget, SettleBudget retryBudget) {
		if (strategy == null)
			throw new IllegalArgumentException("strategy must not be null");
		if (defaultBudget == null)
			throw new IllegalArgumentException("defaultBudget must not be null");
		this.strategy = strategy;
		this.defaultBudget = defaultBudget;
		this.retryBudget = retryBudget;
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
			return grabWithStablePixels(control);
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
	 * Waits until {@code control} has painted once and drained its event
	 * backlog.
	 *
	 * Phase 1 waits for the first Paint event, which proves the widget is
	 * realized and drew once. Phase 2 then forces pending damage out with
	 * {@link Control#update()} and drains the queue until two consecutive
	 * cycles pass without any paint, resize or move activity, so the vast
	 * majority of specimens are fully settled by the time this returns.
	 *
	 * This drain alone is not a stability guarantee: theme CSS transitions
	 * redraw on the frame clock without producing paint/resize/move events,
	 * so pixels can still be changing while the queue looks quiet. Proving
	 * that the pixels themselves no longer change is the stability check's
	 * job ({@link #grabWithStablePixels}); settling here only lets that
	 * check start from an already mostly-quiet widget.
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

	/**
	 * Grabs pixels until one byte-identical rendering has persisted across a
	 * pumped observation window of {@link #STABILITY_HOLD_MILLIS} <em>and</em>
	 * one forced full repaint has reproduced it byte-identically, then
	 * returns it.
	 *
	 * This, not a guessed delay, is what makes a capture deterministic by
	 * construction. On GTK every late pixel change, including theme CSS
	 * transitions running on the frame clock, reaches the captured buffer
	 * through frames; the loop keeps the event loop live and regrabs every
	 * {@link #FRAME_PERIOD_MILLIS}, so any frame the clock owes the window
	 * lands and is compared. Accepting the first pair of identical grabs is
	 * not enough, measured on this stack: a transition can start tens of
	 * milliseconds after hosting (the quiet gap before its first frame) and
	 * easing can hold one intermediate value for a frame or two, and in both
	 * cases consecutive grabs agree while animation is still pending or in
	 * flight. Requiring one unchanged value to persist across an interval
	 * longer than any measured start latency plus transition span closes
	 * both: whatever survives 250 ms of continuous observation has no theme
	 * transition left in it.
	 *
	 * Persistence alone is still only half the proof. "Nothing changed for
	 * 250 ms" can equally mean "the frame that should have replaced this one
	 * never got painted": under CPU contention, state that GTK applies after
	 * the first paint (focus highlighting, lazy style and font computation)
	 * can arrive late, and a frame that predates it is perfectly stable,
	 * because nothing ever damages it again. The second half of the contract
	 * closes this: once a value has persisted across the window, the control
	 * is fully invalidated and repainted, and the value counts as settled
	 * only when that forced repaint reproduces it byte for byte. A stale or
	 * half-styled frame fails the repaint; the settled rendering reproduces
	 * itself. That makes the captured pixels faithful, not merely quiet.
	 *
	 * What this contract deliberately does not cover is the first capture of
	 * a widget class in a fresh process: GTK computes styles and metrics
	 * lazily, so capture one of a specimen can differ from every later one
	 * while each is faithful on its own terms. That is a convergence cost,
	 * not instability, and it is handled by consumers priming a specimen
	 * before judging it ({@code DeterminismLint} discards one prime capture
	 * per specimen); measured on this stack, renderings are byte-stable from
	 * the second capture on, idle and loaded alike.
	 *
	 * The give-up bound is where machine speed enters, and it is treated as
	 * the two different things it can mean:
	 *
	 * <ul>
	 * <li><b>Did not settle in time.</b> Under CPU contention frames arrive
	 * late and one wall-clock second of theme animation can stretch far past
	 * {@link SettleBudget#DEFAULT}. That is a property of the machine, not of
	 * the specimen, so the first timeout is retried once at
	 * {@link SettleBudget#EXTENDED}; only a second timeout becomes failure
	 * data. Retry was chosen over scaling the bound with observed system
	 * load on purpose: load averages count unrelated processes, they are
	 * unreliable inside containers, and coupling rendering behaviour to them
	 * makes runs incomparable across machines; the retry keeps idle-machine
	 * cost unchanged and still fails loudly and bounded when nothing ever
	 * settles.</li>
	 * <li><b>Never settles.</b> A specimen whose pixels keep changing, such
	 * as an indeterminate progress bar, times out twice like everything else,
	 * but its exception carries the counters to tell the cases apart: many
	 * distinct renderings means animation, one distinct rendering with holds
	 * approaching the window means the machine was starved. Either way it is
	 * loud, and neither attempt can turn instability into a pass.</li>
	 * </ul>
	 *
	 * A timeout is therefore the only retryable outcome. Everything else the
	 * loop can report (extent mismatch, grab failure) is thrown directly;
	 * there is no path that observes a settled value here and later returns a
	 * different one from the same call, because the call returns at the
	 * moment the first confirmed hold completes. Cross-call instability, a
	 * specimen returning different settled values from successive captures,
	 * is determinism failure data by definition and is caught by comparing
	 * rounds in {@code DeterminismLint}, never by retrying here.
	 *
	 * Only {@link Strategy#COPY_AREA} is verified this way. Measured on GTK3
	 * under Xvfb, the X11 fallback must instead keep T04's single grab right
	 * after the drain: once extra frame cycles elapse between mapping and
	 * importing, the imported window content develops rounded-corner pixels
	 * that diverge from what copyArea reports (44 of 5600 pixels on
	 * {@code button.push.default}) and vary with the capture's history
	 * rather than converging with elapsed time, so delaying imports amplifies
	 * variance there instead of reducing it. Byte agreement between the
	 * strategies is enforced separately by the selftest at zoom 100 and 200.
	 */
	private CapturedImage grabWithStablePixels(Control control) {
		if (strategy == Strategy.X11_GRAB)
			return grabByX11(control);
		try {
			return waitForStableRendering(control, defaultBudget);
		} catch (SettleTimeoutException first) {
			if (retryBudget == null)
				throw first;
			System.err.println("oracle-capture: '" + control.getClass().getSimpleName()
					+ "' did not settle within " + defaultBudget + ", retrying once with " + retryBudget);
			try {
				return waitForStableRendering(control, retryBudget);
			} catch (SettleTimeoutException second) {
				throw new SettleTimeoutException("'" + control.getClass().getSimpleName()
						+ "' produced no stable rendering in two attempts (" + defaultBudget + ", then "
						+ retryBudget + "); last attempt saw " + second.distinctRenderings()
						+ " distinct renderings, longest held " + second.longestHoldMillis() + " ms; "
						+ (second.distinctRenderings() <= 1
								? "the machine looks starved rather than the widget animated"
								: "the widget looks animated rather than the machine starved"),
						second.elapsedMillis(), second.grabs(), second.distinctRenderings(),
						second.longestHoldMillis());
			}
		}
	}

	/**
	 * One settle attempt under the given budget. Returns the first rendering
	 * that persisted across the stability hold and survived its forced
	 * repaint, or throws {@link SettleTimeoutException} when the budget is
	 * exhausted without any rendering achieving that.
	 */
	private CapturedImage waitForStableRendering(Control control, SettleBudget budget) {
		Display display = control.getDisplay();
		CapturedImage candidate = null;
		long heldSince = 0;
		int distinctRenderings = 0;
		long longestHoldMillis = 0;
		long start = System.currentTimeMillis();
		long deadline = start + budget.timeoutMillis();
		for (int grabs = 1;; grabs++) {
			long now = System.currentTimeMillis();
			if (grabs > budget.maxGrabs() || now >= deadline)
				throw new SettleTimeoutException("'" + control.getClass().getSimpleName()
						+ "' produced no rendering that held stable for " + STABILITY_HOLD_MILLIS
						+ " ms and survived a forced repaint within " + budget.timeoutMillis()
						+ " ms and " + budget.maxGrabs() + " grabs; "
						+ distinctRenderings + " distinct renderings seen, longest held "
						+ longestHoldMillis + " ms", now - start, grabs - 1, distinctRenderings,
						longestHoldMillis);
			pump(display, FRAME_PERIOD_MILLIS);
			CapturedImage current = grabByCopyArea(control);
			now = System.currentTimeMillis();
			if (candidate != null && Arrays.equals(candidate.pngBytes(), current.pngBytes())) {
				long heldFor = now - heldSince;
				longestHoldMillis = Math.max(longestHoldMillis, heldFor);
				if (heldFor >= STABILITY_HOLD_MILLIS && repaintReproduces(control, display, current)) {
					longestHoldMillis = Math.max(longestHoldMillis,
							System.currentTimeMillis() - heldSince);
					return current;
				}
			} else {
				candidate = current;
				heldSince = now;
				distinctRenderings++;
			}
		}
	}

	/**
	 * Invalidates the whole control, waits for the resulting repaint to be
	 * delivered, and reports whether the pixels after it are byte-identical
	 * to {@code held}. Used as the faithfulness half of the settling
	 * contract: a frame that only looks stable because its replacement never
	 * got painted fails here.
	 *
	 * When the repaint cannot be delivered within the grace period the answer
	 * is "no", which is the safe direction: it costs budget instead of
	 * accepting an unverified frame.
	 */
	private boolean repaintReproduces(Control control, Display display, CapturedImage held) {
		boolean[] painted = new boolean[1];
		Listener once = e -> {
			if (e.type == SWT.Paint && e.widget == control)
				painted[0] = true;
		};
		control.addListener(SWT.Paint, once);
		try {
			control.redraw();
			control.update();
			long graceEnd = System.currentTimeMillis() + REPAINT_GRACE_MILLIS;
			while (!painted[0] && System.currentTimeMillis() < graceEnd) {
				if (!display.readAndDispatch())
					sleepBriefly();
			}
		} finally {
			control.removeListener(SWT.Paint, once);
		}
		if (!painted[0])
			return false;
		pump(display, FRAME_PERIOD_MILLIS);
		return Arrays.equals(grabByCopyArea(control).pngBytes(), held.pngBytes());
	}

	/** Dispatches events for the given duration, so scheduled frames can run. */
	private static void pump(Display display, long millis) {
		long end = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < end) {
			if (!display.readAndDispatch())
				sleepBriefly();
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
