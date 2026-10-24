/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.win32.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;

@ExtendWith(PlatformSpecificExecutionExtension.class)
class ToolTipWin32Tests {

	@Test
	public void balloonIconChangesForTheSameTitle() {
		Shell shell = new Shell(Display.getDefault());
		try {
			shell.setSize(200, 100);
			shell.open();
			ToolTip info = new ToolTip(shell, SWT.BALLOON | SWT.ICON_INFORMATION);
			info.setText("Title");
			info.setMessage("first");
			info.setVisible(true);
			info.setVisible(false);
			assertEquals(OS.TTI_INFO, shell.balloonIcon);

			// same title, no icon: the balloon must drop the information icon
			ToolTip plain = new ToolTip(shell, SWT.BALLOON);
			plain.setText("Title");
			plain.setMessage("second");
			plain.setVisible(true);
			plain.setVisible(false);
			assertEquals(OS.TTI_NONE, shell.balloonIcon);
		} finally {
			shell.dispose();
		}
	}
}
