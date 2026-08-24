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
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;

/**
 * Adapter for the prototype-skija fork (swt-initiative31/prototype-skija),
 * built by {@code tools/oracle/build.sh skija-proto}. The fork keeps a native
 * peer per widget but draws custom-drawn widgets through its
 * {@code org.eclipse.swt.graphics.SkijaGC}, so this class must not reference
 * fork types at compile time; everything fork-specific goes through
 * reflection against whatever SWT classes the running JVM actually loaded.
 *
 * Coverage is decided by evidence from the loaded classes, never by a
 * hardcoded widget list: a specimen counts as supported only when its control
 * carries a fork renderer (a field whose type extends the fork's
 * ControlRenderer, which is exactly what routes painting through
 * Drawing.drawWithGC) and a GC asked from the fork for that very control wraps
 * a real SkijaGC.
 */
public class SkijaProtoBackend implements Backend {

	/** The fixed id of this backend, matching tools/oracle/build.sh. */
	public static final String ID = "skija-proto";

	private static final String DRAWING_CLASS = "org.eclipse.swt.graphics.Drawing";
	private static final String SKIJA_GC_CLASS = "org.eclipse.swt.graphics.SkijaGC";
	private static final String CONTROL_RENDERER_CLASS = "org.eclipse.swt.widgets.ControlRenderer";

	private final Map<String, Boolean> coverageById = new ConcurrentHashMap<>();

	private Display display;
	private RenderEnv env;
	private String observedGcClassName;

	@Override
	public String id() {
		return ID;
	}

	/**
	 * The GC class the activation proof observed wrapped by the fork's
	 * drawing API, or null before {@link #configure(Display)} succeeded;
	 * callers can assert on it the way tools/oracle/verify-backend.sh does.
	 */
	public String observedGcClassName() {
		return observedGcClassName;
	}

	@Override
	public void configure(Display display) {
		if (display == null || display.isDisposed())
			throw new BackendUnavailableException("skija-proto backend needs a live Display");
		if (!"gtk".equals(SWT.getPlatform()))
			throw new BackendUnavailableException("skija-proto backend verified on gtk only, found: "
					+ SWT.getPlatform());
		Class<?> drawing = loadForkClass();
		this.env = SwtRenderEnvs.current(display);

		// Same assertion as tools/oracle/verify-backend.sh: ask the fork's
		// drawing API for a graphics context and require it to hand out a
		// genuine SkijaGC, which also proves Skija's native library loaded.
		Shell shell = new Shell(display);
		try {
			Button probe = new Button(shell, SWT.PUSH);
			probe.setText("skija-proto");
			probe.setSize(120, 32);
			shell.open();
			pump(display, 2000);
			observedGcClassName = requireSkijaWrap(drawing, probe);
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
	 * Creates the specimen once in a scratch shell and interrogates the loaded
	 * fork classes: without a renderer field the widget paints natively and
	 * comparing it would report perfect agreement between two copies of the
	 * same renderer; with one, the GC evidence must still hold.
	 */
	private boolean probeCoverage(Specimen specimen) {
		Class<?> drawing = loadForkClass();
		Shell shell = new Shell(display);
		BasicSpecimenContext context = new BasicSpecimenContext(env);
		try {
			Control control = specimen.create(shell, context);
			control.setSize(specimen.preferredSize());
			if (!hasRendererField(control.getClass()))
				return false;
			requireSkijaWrap(drawing, control);
			return true;
		} finally {
			context.dispose();
			shell.dispose();
		}
	}

	/** True when any field in the hierarchy is typed as a fork renderer. */
	private static boolean hasRendererField(Class<?> type) {
		Class<?> rendererType = loadClass(CONTROL_RENDERER_CLASS,
				"the prototype-skija classes on the classpath lack " + CONTROL_RENDERER_CLASS);
		for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
			for (Field field : k.getDeclaredFields()) {
				Class<?> fieldType = field.getType();
				while (fieldType.isArray())
					fieldType = fieldType.getComponentType();
				if (rendererType.isAssignableFrom(fieldType))
					return true;
			}
		}
		return false;
	}

	/**
	 * Asks the fork for a drawing context on {@code control} and returns the
	 * name of the class wrapped in the result's innerGC; throws
	 * BackendUnavailableException unless that is the SkijaGC.
	 */
	private static String requireSkijaWrap(Class<?> drawing, Control control) {
		GC raw = new GC(control);
		try {
			Object wrapped;
			try {
				Method create = drawing.getMethod("createGraphicsContext", GC.class, Control.class);
				wrapped = create.invoke(null, raw, control);
			} catch (ReflectiveOperationException e) {
				throw new BackendUnavailableException("cannot invoke the fork drawing API: " + e, e);
			}
			if (wrapped == null || wrapped == raw)
				throw new BackendUnavailableException(
						"Drawing.createGraphicsContext returned the original GC; USE_SKIJA seems to be off");
			Object inner;
			try {
				inner = GC.class.getField("innerGC").get(wrapped);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new BackendUnavailableException(
						"the loaded org.eclipse.swt.graphics.GC has no innerGC field; stock SWT classes "
								+ "are mixed into a skija-proto classpath", e);
			}
			String className = inner == null ? "null" : inner.getClass().getName();
			if (!SKIJA_GC_CLASS.equals(className))
				throw new BackendUnavailableException("expected a " + SKIJA_GC_CLASS + ", got " + className);
			return className;
		} finally {
			raw.dispose();
		}
	}

	private static Class<?> loadForkClass() {
		return loadClass(DRAWING_CLASS, "prototype-skija classes are not on the classpath; build and run "
				+ "this backend through tools/oracle/build.sh skija-proto");
	}

	private static Class<?> loadClass(String name, String unavailableMessage) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new BackendUnavailableException(unavailableMessage, e);
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
