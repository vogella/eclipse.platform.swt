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
package org.eclipse.swt.visualoracle.tools;

import java.security.MessageDigest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.SwtRenderEnvs;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.CapturedImage;

/**
 * Selftest helper that captures one reference specimen in its own process and
 * prints a single machine-readable line, so the selftest can prove capture
 * determinism and strategy agreement across process boundaries.
 *
 * Usage: CaptureProbe (COPY_AREA|X11_GRAB)
 * Output on success: CAPTURE sha256=... width=... height=...
 */
public final class CaptureProbe {

	public static void main(String[] args) {
		String name = args.length > 0 ? args[0] : "COPY_AREA";
		Display display = null;
		try {
			CaptureRuntime.Strategy strategy = CaptureRuntime.Strategy.valueOf(name);
			display = new Display();
			NativeBackend backend = new NativeBackend();
			try {
				backend.configure(display);
			} catch (BackendUnavailableException e) {
				System.err.println("CAPTURE-FAILED: " + e);
				System.exit(1);
			}
			CapturedImage image = new CaptureRuntime(strategy).capture(
					SpecimenCatalog.discover().byId("button.push.default")
							.orElseThrow(() -> new IllegalStateException("reference specimen button.push.default is missing from the catalog")),
					backend, SwtRenderEnvs.current(display));
			System.out.println("CAPTURE sha256=" + hex(image.pngBytes())
					+ " width=" + image.width() + " height=" + image.height());
			display.dispose();
		} catch (Throwable t) {
			if (display != null && !display.isDisposed())
				display.dispose();
			System.err.println("CAPTURE-FAILED: " + t);
			t.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static String hex(byte[] bytes) {
		try {
			return hexOf(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String hexOf(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
			sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		return sb.toString();
	}
}
