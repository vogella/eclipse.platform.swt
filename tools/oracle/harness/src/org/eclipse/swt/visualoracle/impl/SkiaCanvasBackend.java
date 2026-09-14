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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.UnsupportedSpecimenException;

/**
 * Adapter for the {@code SWT.SKIA} canvas of PR 3231, built by
 * {@code tools/oracle/build.sh skia-canvas} (host SWT bundle plus the
 * {@code org.eclipse.swt.skia} fragment plus its Skija 0.143.17 jars). The
 * PR routes a widget through Skia only when the widget is a {@link Canvas}
 * whose creation style carries {@code SWT.SKIA}; the fragment then attaches
 * an external canvas handler, which this class observes by reflection against
 * whatever classes the running JVM actually loaded.
 *
 * Coverage is decided by measurement, never by a hardcoded widget list: a
 * specimen counts as supported only when the control created by its factory
 * actually carries the handler field the PR sets on activation. Because
 * specimen factories are frozen and cannot add style bits, processes driving
 * this backend set the PR's own test property
 * {@code org.eclipse.swt.external.canvas:forceEnabled} (see
 * {@link #activationProperties()}); {@code configure} still proves the
 * documented style-mask path with a probe canvas that carries
 * {@code SWT.SKIA} itself. On this catalog the measurement comes out as: only
 * the CLabel family extends Canvas, so almost the entire catalog reports
 * UNSUPPORTED, which is the honest statement of how much of SWT PR 3231
 * covers today, not a defect of this adapter.
 *
 * Two facts about the PR are encoded here rather than rediscovered:
 * {@code ExternalCanvasHandler.isActive} excludes {@code StyledText} and
 * {@code Decorations} (and {@code Shell} extends {@code Decorations}, so a
 * shell can never carry a handler); and {@code SWT.SKIA} is {@code 1 << 23},
 * the same bit as {@code SWT.FLAT}, so any widget carrying {@code SWT.FLAT}
 * also reports carrying {@code SWT.SKIA}. The collision is reported (see the
 * selftest), not worked around; it is a finding about the PR's API design.
 */
public class SkiaCanvasBackend implements Backend {

	/** The fixed id of this backend, matching tools/oracle/build.sh. */
	public static final String ID = "skia-canvas";

	private static final String HANDLER_FIELD = "externalCanvasHandler";
	private static final String EXPECTED_HANDLER_CLASS =
			"org.eclipse.swt.internal.skia.SkiaGlCanvasExtension";

	/** JVM properties every process driving this backend must carry. */
	public static final String FORCE_ENABLED_PROPERTY = "org.eclipse.swt.external.canvas:forceEnabled";
	public static final String LOG_ACTIVATION_PROPERTY = "org.eclipse.swt.external.canvas:logActivation";

	private final Map<String, Boolean> coverageById = new ConcurrentHashMap<>();

	private Display display;
	private RenderEnv env;
	private String observedHandlerClassName;
	private int observedPaintCount;
	private boolean forceEnabled;

	/**
	 * The JVM options (-D name=value) a process needs for this backend to
	 * route specimen-created canvases through the fragment at all:
	 * {@code forceEnabled} because specimen factories cannot pass
	 * {@code SWT.SKIA}, and {@code logActivation} so the child's stdout keeps
	 * the activation marker tools/oracle/verify-backend.sh asserts.
	 */
	public static List<String> activationJvmProperties() {
		return List.of("-D" + FORCE_ENABLED_PROPERTY + "=true",
				"-D" + LOG_ACTIVATION_PROPERTY + "=true");
	}

	@Override
	public String id() {
		return ID;
	}

	/**
	 * The external canvas handler class the activation proof observed, or
	 * null before {@link #configure(Display)} succeeded; callers can assert
	 * on it the way tools/oracle/verify-backend.sh asserts the activation log
	 * line.
	 */
	public String observedHandlerClassName() {
		return observedHandlerClassName;
	}

	/** Paint events the probe canvas fired between mapping and the proof. */
	public int observedPaintCount() {
		return observedPaintCount;
	}

	/**
	 * Whether the running process carries the {@code forceEnabled} property
	 * that lets specimen-created canvases activate without the
	 * {@code SWT.SKIA} style bit; false means only explicitly styled canvases
	 * activate and every catalog specimen will report unsupported.
	 */
	public boolean isForceEnabled() {
		return forceEnabled;
	}

	@Override
	public void configure(Display display) {
		if (display == null || display.isDisposed())
			throw new BackendUnavailableException("skia-canvas backend needs a live Display");
		if (!"gtk".equals(SWT.getPlatform()))
			throw new BackendUnavailableException("skia-canvas backend verified on gtk only, found: "
					+ SWT.getPlatform());
		this.env = SwtRenderEnvs.current(display);
		this.forceEnabled = System.getProperty(FORCE_ENABLED_PROPERTY) != null;

		// Same evidence verify-backend.sh asserts: a canvas created with the
		// documented SWT.SKIA style gets a real fragment handler attached,
		// which also proves the ServiceLoader factory was found and the
		// Skija native library loaded (a failed init leaves the field null).
		Shell shell = new Shell(display);
		try {
			Canvas probe = new Canvas(shell, SWT.SKIA | SWT.BORDER);
			probe.setBounds(10, 10, 120, 60);
			int[] paints = { 0 };
			probe.addListener(SWT.Paint, e -> paints[0]++);
			shell.open();
			pump(display, 2000);
			observedPaintCount = paints[0];
			Object handler = readHandler(probe);
			if (handler == null)
				throw new BackendUnavailableException(
						"a canvas created with SWT.SKIA carried no external canvas handler; "
								+ "the skia fragment is missing from the classpath or its "
								+ "initialization failed");
			observedHandlerClassName = handler.getClass().getName();
			if (!EXPECTED_HANDLER_CLASS.equals(observedHandlerClassName))
				throw new BackendUnavailableException("expected a " + EXPECTED_HANDLER_CLASS
						+ ", got " + observedHandlerClassName);
		} finally {
			shell.dispose();
		}
		this.display = display;
	}

	@Override
	public boolean supports(Specimen specimen) {
		if (display == null || display.isDisposed())
			throw new IllegalStateException("configure(Display) must succeed before supports(Specimen)");
		return coverageById.computeIfAbsent(specimen.id(), id -> probeCoverage(specimen));
	}

	/**
	 * Creates the specimen once in a scratch shell and measures whether the
	 * PR attached its handler to the resulting control. StyledText and
	 * Decorations are excluded up front exactly like
	 * {@code ExternalCanvasHandler.isActive} excludes them (StyledText is a
	 * Canvas yet deliberately not migrated; Shell extends Decorations), so
	 * the measurement mirrors the PR instead of rediscovering it.
	 */
	private boolean probeCoverage(Specimen specimen) {
		Shell shell = new Shell(display);
		BasicSpecimenContext context = new BasicSpecimenContext(env);
		try {
			Control control = specimen.create(shell, context);
			control.setSize(specimen.preferredSize());
			if (control instanceof StyledText || control instanceof Decorations)
				return false;
			if (!(control instanceof Canvas))
				return false;
			return readHandler(control) != null;
		} catch (UnsupportedSpecimenException | LinkageError e) {
			return false;
		} finally {
			context.dispose();
			shell.dispose();
		}
	}

	/** The PR's handler attached to {@code control}, or null when absent. */
	private static Object readHandler(Control control) {
		try {
			Field field = Canvas.class.getDeclaredField(HANDLER_FIELD);
			field.setAccessible(true);
			return field.get(control);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new BackendUnavailableException(
					"the loaded org.eclipse.swt.widgets.Canvas has no " + HANDLER_FIELD
							+ " field; stock SWT classes without PR 3231 are mixed into a "
							+ "skia-canvas classpath", e);
		}
	}

	private static void pump(Display display, long timeoutMillis) {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (!display.isDisposed() && System.currentTimeMillis() < deadline) {
			if (!display.readAndDispatch())
				display.sleep();
		}
	}
}
