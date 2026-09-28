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
import java.lang.invoke.*;

/**
 * The Win32 last error of the calling thread as captured by the downcalls linked with {@link #CAPTURE},
 * since the JVM may change the thread's real last error between two downcalls.
 * <code>OS.GetLastError()</code> returns {@link #get()} in the FFM build.
 */
public final class FFMLastError {

	/** The linker option of a capturing binding. */
	public static final Linker.Option CAPTURE = Linker.Option.captureCallState("GetLastError");

	static final StructLayout LAYOUT = Linker.Option.captureStateLayout();
	static final VarHandle ERROR = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

	/** One capture buffer per thread, freed with the thread. */
	static final ThreadLocal<MemorySegment> STATE = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(LAYOUT));

	private FFMLastError() {
	}

	/** The capture buffer of this thread, the leading argument of a capturing downcall. */
	public static MemorySegment state() {
		return STATE.get();
	}

	/** The last error the most recent capturing downcall of this thread left. */
	public static int get() {
		return (int) ERROR.get(STATE.get(), 0L);
	}

	/** Links <code>name</code> through {@link FFM#LOOKUP} so that it records the last error. */
	public static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
		MethodHandle handle = FFM.downcallOptional(name, descriptor, CAPTURE);
		if (handle != null) return handle;
		return MethodHandles.dropArguments(FFM.downcall(name, descriptor), 0, MemorySegment.class);
	}
}
