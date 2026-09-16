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
package org.eclipse.swt.visualoracle.result;

/**
 * Outcome of one capture attempt, serialised verbatim into result JSON.
 * See {@code docs/visual-oracle/RESULT-SCHEMA.md}.
 */
public enum CaptureStatus {

	/** Pixels were captured; width, height and image are present. */
	CAPTURED,

	/** The backend cannot render this specimen; not a defect. */
	UNSUPPORTED,

	/** The capture failed; message says why. */
	FAILED
}
