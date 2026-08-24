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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.internal.gtk3.GTK3;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.Tag;
import org.eclipse.swt.visualoracle.spi.UnsupportedEnvironmentException;

/**
 * Adapter for stock native SWT rendering, built by {@code tools/oracle/build.sh}
 * native. This is the reference oracle every comparison is measured against.
 *
 * Activation is proven, not assumed, with the same discipline as
 * {@link SkijaProtoBackend}: {@link #configure(Display)} requires a paint
 * event from a probe control, executes a JNI call against the loaded SWT
 * natives, verifies that the GC handed out for that control is the plain
 * stock {@code org.eclipse.swt.graphics.GC}, refuses any classpath whose
 * foreign drawing layer wraps it (the prototype-skija fork's Drawing API is
 * the known case), and requires the GC to actually put glyph ink on a
 * surface. A comparison silently running native against native would report
 * perfect agreement and be worthless; these proofs are what rule that out.
 *
 * The process environment is recorded at activation time ({@link #environment()})
 * and callers must confirm it with {@link #requireEnvironment(RenderEnv)},
 * which fails with {@link UnsupportedEnvironmentException} exactly as the
 * frozen Capture contract prescribes.
 */
public class NativeBackend implements Backend {

	/** The fixed id of this backend, matching tools/oracle/build.sh. */
	public static final String ID = "native";

	private static final String STOCK_GC_CLASS = "org.eclipse.swt.graphics.GC";
	private static final String FORK_DRAWING_CLASS = "org.eclipse.swt.graphics.Drawing";
	/** How long the activation proof waits for the probe control's first paint. */
	private static final int ACTIVATION_TIMEOUT_MILLIS = 5000;
	/** Distinct-from-background pixels a text drawing must produce to count as ink. */
	private static final int MIN_GLYPH_INK_PIXELS = 20;

	private final Map<String, Boolean> coverageById = new ConcurrentHashMap<>();

	private Display display;
	private RenderEnv env;
	private String observedGcClassName;

	@Override
	public String id() {
		return ID;
	}

	/**
	 * The GC class the activation proof observed in use for real controls, or
	 * null before {@link #configure(Display)} succeeded; callers can assert on
	 * it the way tools/oracle/verify-backend.sh does.
	 */
	public String observedGcClassName() {
		return observedGcClassName;
	}

	/** The environment this process is pinned to, as measured at activation. */
	public RenderEnv environment() {
		requireConfigured();
		return env;
	}

	/**
	 * Refuses a mismatch between {@code requested} and the environment this
	 * process was actually started in, exactly as the frozen Capture contract
	 * does: SWT pins zoom, theme and direction at Display creation, so a
	 * request for anything else would silently mislabel every result. The
	 * comparison is semantic (a system-font request accepts whatever font the
	 * machine reports), matching {@link SwtRenderEnvs#matches}.
	 *
	 * @throws UnsupportedEnvironmentException naming both environments
	 */
	public void requireEnvironment(RenderEnv requested) {
		requireConfigured();
		if (!SwtRenderEnvs.matches(requested, env))
			throw new UnsupportedEnvironmentException(
					"environment mismatch: process is pinned to " + env + ", requested "
							+ requested + "; spawn one harness process per environment (see RenderEnv)");
	}

	@Override
	public void configure(Display display) {
		if (display == null || display.isDisposed())
			throw new BackendUnavailableException("native backend needs a live Display");
		if (!"gtk".equals(SWT.getPlatform()))
			throw new BackendUnavailableException("native backend verified on gtk only, found: "
					+ SWT.getPlatform());
		this.env = SwtRenderEnvs.current(display);

		Shell shell = new Shell(display);
		try {
			Button probe = new Button(shell, SWT.PUSH);
			probe.setText("native");
			probe.setSize(120, 32);
			boolean[] painted = new boolean[1];
			Listener recorder = e -> {
				if (e.type == SWT.Paint && e.widget == probe)
					painted[0] = true;
			};
			probe.addListener(SWT.Paint, recorder);
			shell.open();
			pump(display, painted, probe);
			if (probe.isDisposed())
				throw new BackendUnavailableException("the activation probe was disposed before painting");
			if (!painted[0])
				throw new BackendUnavailableException(
						"no paint event observed on the probe control within " + ACTIVATION_TIMEOUT_MILLIS
								+ " ms; nothing proves the native renderer drew anything");

			// A real JNI round trip into the loaded SWT natives; also proves
			// the probe widget is realized with its own window on screen.
			long window = GTK3.gtk_widget_get_window(probe.handle);
			if (window == 0)
				throw new BackendUnavailableException(
						"the probe control owns no native window; SWT natives are not working");

			GC raw = new GC(probe);
			try {
				String className = raw.getClass().getName();
				if (!STOCK_GC_CLASS.equals(className))
					throw new BackendUnavailableException(
							"expected the stock " + STOCK_GC_CLASS + ", got " + className);
				refuseForeignRenderer(raw, probe);
				requireGlyphInk(display);
				observedGcClassName = className;
			} finally {
				raw.dispose();
			}
		} finally {
			shell.dispose();
		}
		this.display = display;
	}

	/**
	 * Reports coverage as data, cached per specimen id like every adapter:
	 * stock native SWT renders every catalog family on this platform except
	 * specimens tagged {@link Tag#NATIVE_POPUP}. Such widgets put their
	 * distinguishing content into transient windows outside their own bounds,
	 * while the captured region is exactly the control bounds, so the popup
	 * content can never reach the comparison substrate; reporting UNSUPPORTED
	 * is honest where comparing chrome-only images would lie.
	 */
	@Override
	public boolean supports(Specimen specimen) {
		requireConfigured();
		return coverageById.computeIfAbsent(specimen.id(), id -> decideCoverage(specimen));
	}

	private static boolean decideCoverage(Specimen specimen) {
		return specimen.tags() == null || !specimen.tags().contains(Tag.NATIVE_POPUP);
	}

	private void requireConfigured() {
		if (display == null || display.isDisposed())
			throw new IllegalStateException("configure(Display) must succeed before this backend is used");
	}

	/**
	 * Refuses any classpath whose foreign drawing layer wraps the GC instead
	 * of leaving the stock instance in place. When the prototype-skija fork's
	 * Drawing API is absent this is trivially satisfied; when present but
	 * inert (it hands back the identical raw GC) rendering genuinely falls
	 * through to native; when it returns a wrapper, this process renders some
	 * other stack and comparisons run against the wrong oracle.
	 */
	private static void refuseForeignRenderer(GC raw, Control control) {
		Class<?> drawing;
		try {
			drawing = Class.forName(FORK_DRAWING_CLASS);
		} catch (ClassNotFoundException e) {
			return;
		}
		Object wrapped;
		try {
			Method create = drawing.getMethod("createGraphicsContext", GC.class, Control.class);
			wrapped = create.invoke(null, raw, control);
		} catch (InvocationTargetException e) {
			throw new BackendUnavailableException(
					"the prototype-skija drawing layer is on the classpath and fails on invocation;"
							+ " mixed classpath",
					e.getCause() != null ? e.getCause() : e);
		} catch (ReflectiveOperationException e) {
			throw new BackendUnavailableException(
					"the prototype-skija drawing layer is on the classpath but incomplete;"
							+ " mixed classpath", e);
		}
		if (wrapped == null || wrapped == raw)
			return;
		Object inner;
		try {
			inner = GC.class.getField("innerGC").get(wrapped);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			inner = null;
		}
		throw new BackendUnavailableException(
				"a foreign renderer is active: Drawing.createGraphicsContext wrapped the GC in "
						+ wrapped.getClass().getName() + " around "
						+ (inner == null ? "an inaccessible inner GC" : inner.getClass().getName())
						+ "; comparisons against this process would not be against native SWT");
	}

	/**
	 * Proves the GC stack really draws by putting text on an offscreen
	 * surface and counting pixels distinct from the background; works for any
	 * palette because it compares against pixel(0,0) instead of decoding
	 * channels.
	 */
	private static void requireGlyphInk(Display display) {
		Image image = new Image(display, 96, 28);
		try {
			GC gc = new GC(image);
			try {
				gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
				gc.fillRectangle(0, 0, 96, 28);
				gc.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
				gc.setFont(display.getSystemFont());
				gc.drawText("native", 2, 2, true);
			} finally {
				gc.dispose();
			}

			ImageData data = image.getImageData();
			int background = data.getPixel(0, 0);
			int ink = 0;
			for (int y = 0; y < data.height && ink < MIN_GLYPH_INK_PIXELS; y++)
				for (int x = 0; x < data.width && ink < MIN_GLYPH_INK_PIXELS; x++)
					if (data.getPixel(x, y) != background)
						ink++;
			if (ink < MIN_GLYPH_INK_PIXELS)
				throw new BackendUnavailableException("the native GC produced no glyph ink ("
						+ ink + " pixels differ from the background); the drawing stack is not working");
		} finally {
			image.dispose();
		}
	}

	private static void pump(Display display, boolean[] painted, Control probe) {
		long deadline = System.currentTimeMillis() + ACTIVATION_TIMEOUT_MILLIS;
		while (!painted[0] && !probe.isDisposed() && System.currentTimeMillis() < deadline) {
			if (!display.readAndDispatch())
				sleepBriefly();
		}
	}

	private static void sleepBriefly() {
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
