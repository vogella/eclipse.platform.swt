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
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;

/**
 * Minimal SpecimenContext applying the environment (orientation, font) to
 * specimen controls. Owns the Font it creates, if any; dispose when done.
 */
public class BasicSpecimenContext implements SpecimenContext {

	private final RenderEnv env;
	private Font font;

	public BasicSpecimenContext(RenderEnv env) {
		this.env = env;
	}

	@Override
	public RenderEnv env() {
		return env;
	}

	@Override
	public void configure(Control control) {
		control.setOrientation(env.direction() == Direction.RTL ? SWT.RIGHT_TO_LEFT : SWT.LEFT_TO_RIGHT);
		if (!env.usesSystemFont()) {
			if (font == null)
				font = new Font(control.getDisplay(), env.fontFamily(), env.fontSize(), SWT.NORMAL);
			control.setFont(font);
		}
	}

	/** Releases the font created for this context, if one was created. */
	public void dispose() {
		if (font != null && !font.isDisposed())
			font.dispose();
	}
}
