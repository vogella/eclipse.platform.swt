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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
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
 * Skeleton capture implementing the ADR-001 decision: {@code GC.copyArea}
 * from the on-screen control.
 *
 * Deliberately minimal: it owns the shell, the event loop settling and the
 * capture in one place so the pipeline runs end to end. Task T04 replaces the
 * shell management, settling, forced redraw and the xgrab fallback with the
 * real capture runtime behind this same interface.
 */
public class CopyAreaCapture implements Capture {

	private static final int MARGIN = 12;
	private static final int SETTLE_MILLIS = 150;
	private static final int PAINT_TIMEOUT_MILLIS = 5000;

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
		Shell shell = new Shell(display);
		try {
			Control control = specimen.create(shell, ctx);
			if (control == null || control.isDisposed())
				throw new CaptureFailedException("specimen '" + specimen.id() + "' created no usable control");
			layoutAndSettle(shell, control, specimen);
			return grab(control);
		} finally {
			ctx.dispose();
			shell.dispose();
		}
	}

	private void layoutAndSettle(Shell shell, Control control, Specimen specimen) {
		org.eclipse.swt.graphics.Point preferred = specimen.preferredSize();
		control.setBounds(MARGIN, MARGIN, preferred.x, preferred.y);
		// Wait for the first real paint of the control, exactly what the T01
		// spike proved deterministic; a fixed sleep alone is racy under load.
		boolean[] painted = { false };
		control.addListener(SWT.Paint, e -> painted[0] = true);
		shell.layout();
		shell.open();
		long deadline = System.currentTimeMillis() + PAINT_TIMEOUT_MILLIS;
		while (!painted[0] && System.currentTimeMillis() < deadline && !control.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				sleepBriefly();
		}
		if (!painted[0])
			throw new CaptureFailedException(
					"no paint event observed for '" + control.getClass().getSimpleName() + "' within "
							+ PAINT_TIMEOUT_MILLIS + " ms");
		settle(shell.getDisplay(), control);
	}

	private void settle(Display display, Control control) {
		pump(display, SETTLE_MILLIS);
		if (control.isDisposed())
			throw new CaptureFailedException("control disposed during event loop settling");
		control.update();
		while (display.readAndDispatch()) {
			// drain everything the update flushed
		}
	}

	private void pump(Display display, int millis) {
		boolean[] done = { false };
		display.timerExec(millis, () -> done[0] = true);
		while (!done[0]) {
			if (!display.readAndDispatch())
				sleepBriefly();
		}
	}

	private void sleepBriefly() {
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private CapturedImage grab(Control control) {
		Image image = new Image(control.getDisplay(), control.getSize().x, control.getSize().y);
		GC gc = new GC(control);
		try {
			gc.copyArea(image, 0, 0);
		} finally {
			gc.dispose();
		}
		try {
			return new BasicCapturedImage(image.getImageData());
		} finally {
			image.dispose();
		}
	}

	private void requireSameEnvironment(RenderEnv requested, Display display) {
		RenderEnv actual = SwtRenderEnvs.current(display);
		if (!actual.equals(requested))
			throw new UnsupportedEnvironmentException(
					"process is pinned to " + actual + ", cannot capture under " + requested
							+ "; spawn one harness process per environment (see RenderEnv)");
	}
}
