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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.Specimen;

/**
 * Adapter for stock native SWT rendering. This is the oracle side of every
 * comparison; task T09 hardens it.
 */
public class NativeBackend implements Backend {

	/** The fixed id of this backend, matching tools/oracle/build.sh. */
	public static final String ID = "native";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void configure(Display display) {
		if (display == null || display.isDisposed())
			throw new BackendUnavailableException("native backend needs a live Display");
		if (!"gtk".equals(SWT.getPlatform()))
			throw new BackendUnavailableException("native backend verified on gtk only, found: "
					+ SWT.getPlatform());
	}

	@Override
	public boolean supports(Specimen specimen) {
		return true;
	}
}
