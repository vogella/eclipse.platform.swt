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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Theme;

/**
 * Owns the mapping between a {@link RenderEnv} and the process settings that
 * realise it, in both directions: {@link #launch(RenderEnv)} produces the
 * launch configuration for a child process, {@link #current(Display)} reads
 * back what this process is actually pinned to.
 *
 * <h2>How each environment component is realised on Linux/GTK</h2>
 * <ul>
 * <li>Zoom: the JVM property {@code swt.autoScale}, applied by SWT when the
 * {@code Display} is created; verified by measurement (display DPI), never by
 * trusting the property.</li>
 * <li>Theme: the {@code GTK_THEME} environment variable, honoured by GTK at
 * initialisation; removed again for the platform default so an inherited
 * value cannot silently override it.</li>
 * <li>Text direction: applied per control by {@link BasicSpecimenContext},
 * which is the SPI contract for {@code SpecimenContext.configure}. GTK's
 * locale-derived default direction cannot be switched per process without an
 * installed RTL locale, and SWT exposes no Display-wide switch, so the
 * process records its direction in {@code oracle.env.direction} and every
 * specimen control receives it. That this genuinely mirrors pixels is proven
 * by the selftest, not assumed here.</li>
 * <li>Base font: likewise applied per control by {@link BasicSpecimenContext};
 * recorded via {@code oracle.env.fontFamily}/{@code fontSize}.</li>
 * </ul>
 */
public final class SwtRenderEnvs {

	/** JVM property carrying the requested zoom percentage into a child. */
	public static final String ZOOM_PROPERTY = "oracle.env.zoomPercent";
	/** JVM property carrying the requested theme id into a child. */
	public static final String THEME_PROPERTY = "oracle.env.theme";
	/** JVM property carrying the requested text direction into a child. */
	public static final String DIRECTION_PROPERTY = "oracle.env.direction";
	/** JVM property carrying the requested base font family into a child. */
	public static final String FONT_FAMILY_PROPERTY = "oracle.env.fontFamily";
	/** JVM property carrying the requested base font size into a child. */
	public static final String FONT_SIZE_PROPERTY = "oracle.env.fontSize";

	private SwtRenderEnvs() {
	}

	/**
	 * Produces the launch configuration that pins a fresh process to
	 * {@code env}.
	 */
	public static LaunchConfig launch(RenderEnv env) {
		Map<String, String> variables = new LinkedHashMap<>();
		Set<String> removed = new LinkedHashSet<>();
		List<String> jvmProperties = new ArrayList<>();
		jvmProperties.add("-Dswt.autoScale=" + env.zoomPercent());
		jvmProperties.add("-D" + ZOOM_PROPERTY + "=" + env.zoomPercent());
		jvmProperties.add("-D" + THEME_PROPERTY + "=" + env.theme().id());
		jvmProperties.add("-D" + DIRECTION_PROPERTY + "=" + env.direction().name());
		jvmProperties.add("-D" + FONT_FAMILY_PROPERTY + "=" + env.fontFamily());
		jvmProperties.add("-D" + FONT_SIZE_PROPERTY + "=" + env.fontSize());
		if (env.theme().isPlatformDefault()) {
			removed.add("GTK_THEME");
		} else {
			variables.put("GTK_THEME", env.theme().id());
		}
		return new LaunchConfig(env, variables, removed, jvmProperties);
	}

	/** The environment of this process, read from {@code display}. */
	public static RenderEnv current(Display display) {
		// The device zoom, not Display.getDPI(): on GTK the reported DPI does
		// not follow swt.autoScale, while the device zoom is exactly the
		// value SWT scales captures by.
		int zoom = org.eclipse.swt.internal.DPIUtil.getDeviceZoom();
		String gtkTheme = System.getenv("GTK_THEME");
		Theme theme = new Theme(gtkTheme == null ? "" : gtkTheme);
		Direction direction = readDirection();
		String familyProp = System.getProperty(FONT_FAMILY_PROPERTY, "");
		if (!familyProp.isEmpty()) {
			int size = Integer.parseInt(System.getProperty(FONT_SIZE_PROPERTY, "0"));
			return new RenderEnv(zoom, theme, direction, familyProp, size);
		}
		FontData font = display.getSystemFont().getFontData()[0];
		return new RenderEnv(zoom, theme, direction, font.getName(), font.getHeight());
	}

	private static Direction readDirection() {
		String prop = System.getProperty(DIRECTION_PROPERTY, Direction.LTR.name());
		try {
			return Direction.valueOf(prop);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(DIRECTION_PROPERTY + " carries no known direction: " + prop);
		}
	}

	/**
	 * True if {@code actual} satisfies {@code requested}. A request for the
	 * system font accepts whatever font the machine reports; everything else
	 * must match exactly.
	 */
	public static boolean matches(RenderEnv requested, RenderEnv actual) {
		Objects.requireNonNull(requested, "requested");
		Objects.requireNonNull(actual, "actual");
		if (requested.zoomPercent() != actual.zoomPercent())
			return false;
		if (requested.direction() != actual.direction())
			return false;
		if (!requested.theme().id().equals(actual.theme().id()))
			return false;
		if (requested.usesSystemFont())
			return true;
		return requested.fontFamily().equals(actual.fontFamily())
				&& requested.fontSize() == actual.fontSize();
	}
}
