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

/**
 * How long one capture attempt may wait for a rendering to hold stable.
 *
 * {@link #DEFAULT} is sized for an uncontended machine: measured GTK theme
 * transition start latencies stay below 100 ms and their animation span is
 * around 200 ms (SCR-1), so anything that will settle at all settles well
 * inside 5 s and 60 grabs there, where one capture costs a few hundred
 * milliseconds.
 *
 * {@link #EXTENDED} is the single retry step for timeouts. A timeout means no
 * rendering ever completed the stability hold within the attempt, and under
 * CPU contention that is usually the machine's fault rather than the
 * specimen's: frames arrive late, so wall-clock bounds that are generous
 * when idle are exhausted by one slow theme transition. Retrying once at
 * four times the time and the grabs rescues that case while keeping the
 * cost off idle machines and off genuinely animated widgets, which fail
 * both attempts either way. T12 verified the mechanics directly: a capture
 * forced through a {@code 100 ms / 4 grab} attempt timed out on
 * {@code button.push.default} and then passed on the EXTENDED retry, and an
 * animated fixture failed both attempts while reporting many distinct
 * renderings, so a retry cannot turn instability into a pass
 * (selftest checks settle-timeout-passes-on-retry-with-larger-budget and
 * lint-animated-specimen-fails-despite-retry).
 */
public record SettleBudget(long timeoutMillis, int maxGrabs) {

	/** The historical bounds; ample when the machine is not contended. */
	public static final SettleBudget DEFAULT = new SettleBudget(5_000, 60);

	/** The retry step: four times the time and the grabs of DEFAULT. */
	public static final SettleBudget EXTENDED = new SettleBudget(20_000, 240);

	public SettleBudget {
		if (timeoutMillis <= 0)
			throw new IllegalArgumentException("timeoutMillis must be positive: " + timeoutMillis);
		if (maxGrabs <= 0)
			throw new IllegalArgumentException("maxGrabs must be positive: " + maxGrabs);
	}

	@Override
	public String toString() {
		return timeoutMillis + " ms/" + maxGrabs + " grabs";
	}
}
