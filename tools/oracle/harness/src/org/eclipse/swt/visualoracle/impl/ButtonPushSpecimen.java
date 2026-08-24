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

import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * The one reference specimen of the skeleton: a push button with a fixed
 * label. Deterministic by construction: no focus, hover or time dependence.
 */
public class ButtonPushSpecimen implements Specimen {

	public static final String ID = "button.push.default";

	private static final Point SIZE = new Point(140, 40);

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Point preferredSize() {
		return new Point(SIZE.x, SIZE.y);
	}

	@Override
	public Control create(Composite parent, SpecimenContext ctx) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText("OK");
		ctx.configure(button);
		return button;
	}

	@Override
	public Set<Tag> tags() {
		return Set.of(Tag.TEXT_HEAVY);	}
}
