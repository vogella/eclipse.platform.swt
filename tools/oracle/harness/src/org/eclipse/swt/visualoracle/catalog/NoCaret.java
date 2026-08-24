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
package org.eclipse.swt.visualoracle.catalog;

import org.eclipse.swt.internal.gtk.GTK;
import org.eclipse.swt.internal.gtk3.GTK3;
import org.eclipse.swt.internal.gtk4.GTK4;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;

/**
 * Keeps text entry controls from ever owning a caret during capture.
 *
 * A focused entry blinks its caret, and a blinking caret cannot survive the
 * capture runtime's byte-identical stability hold reliably: the hold is
 * shorter than a blink cycle, so captures pass sometimes with the caret on
 * and sometimes with it off. The capture shell also focuses a lone focusable
 * child when it first opens (measured in T13), so whether an entry shows a
 * caret would depend on its position in the run unless something removes the
 * possibility.
 *
 * Clearing GTK's can-focus on the widget closes both problems at the source:
 * no focus can land on it when the shell opens, and no later grab-focus call
 * can succeed either. The control always renders its unfocused state, so the
 * captured pixels are deterministic by construction, independent of specimen
 * order and shell history. The trade-off is that caret painting itself is not
 * covered by these specimens.
 */
final class NoCaret {

	private NoCaret() {
	}

	/** Applies before the shell realizes the control, in the specimen factory. */
	static void ensure(Control control) {
		GTK.gtk_widget_set_can_focus(control.handle, false);
		if (control instanceof Combo combo) {
			// An editable combo focuses through its inner GtkEntry, not the box.
			long child = GTK.GTK4 ? GTK4.gtk_combo_box_get_child(combo.handle)
					: GTK3.gtk_bin_get_child(combo.handle);
			if (child != 0 && child != combo.handle)
				GTK.gtk_widget_set_can_focus(child, false);
		}
	}
}
