/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.ffm;

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.*;
import java.lang.invoke.*;

/**
 * The Win32 last error of the calling thread as captured by the downcalls linked with
 * <code>Linker.Option.captureCallState("GetLastError")</code>, since the JVM may change the thread's real
 * last error between two downcalls. <code>OS.GetLastError()</code> returns it in the FFM build.
 */
public final class FFMLastError {

	static final StructLayout LAYOUT = Linker.Option.captureStateLayout();
	static final VarHandle LAST_ERROR = LAYOUT.varHandle(PathElement.groupElement("GetLastError"));
	static final ThreadLocal<MemorySegment> STATE = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(LAYOUT));

	private FFMLastError() {
	}

	/** The capture segment of this thread, passed as first argument of a capturing downcall. */
	public static MemorySegment state() {
		return STATE.get();
	}

	/** The last error the most recent capturing downcall of this thread left. */
	public static int get() {
		return (int) LAST_ERROR.get(STATE.get(), 0L);
	}
}
