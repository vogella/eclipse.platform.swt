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

import org.eclipse.swt.visualoracle.spi.CaptureFailedException;

/**
 * A capture that ended because no rendering held stable within its
 * {@link SettleBudget}, as distinct from a capture that settled and later
 * produced different pixels.
 *
 * The distinction is load-bearing for determinism verdicts: a timeout under
 * CPU contention usually means the machine was too slow, so consumers retry
 * once with {@link SettleBudget#EXTENDED} before calling a specimen
 * non-deterministic. A specimen that settled and then changed is a real
 * determinism failure and must never be retried into a pass; that case does
 * not carry this exception.
 *
 * The counters name what actually happened, so a persistent timeout can be
 * read as "machine too slow" (few distinct renderings, holds approaching the
 * observation window) or "never settles" (many distinct renderings, e.g. an
 * animation), rather than guessed from a message.
 */
public class SettleTimeoutException extends CaptureFailedException {

	private final long elapsedMillis;
	private final int grabs;
	private final int distinctRenderings;
	private final long longestHoldMillis;

	public SettleTimeoutException(String message, long elapsedMillis, int grabs,
			int distinctRenderings, long longestHoldMillis) {
		super(message);
		this.elapsedMillis = elapsedMillis;
		this.grabs = grabs;
		this.distinctRenderings = distinctRenderings;
		this.longestHoldMillis = longestHoldMillis;
	}

	/** Wall-clock time spent in the settle loop before giving up. */
	public long elapsedMillis() {
		return elapsedMillis;
	}

	/** Pixel grabs taken before giving up. */
	public int grabs() {
		return grabs;
	}

	/** How many different renderings were observed; 1 means pixels never changed. */
	public int distinctRenderings() {
		return distinctRenderings;
	}

	/** Longest time one single rendering persisted unchanged. */
	public long longestHoldMillis() {
		return longestHoldMillis;
	}
}
