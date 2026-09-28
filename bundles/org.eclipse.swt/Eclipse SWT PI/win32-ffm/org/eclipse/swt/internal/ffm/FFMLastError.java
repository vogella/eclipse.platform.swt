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
 * Implements <code>OS.GetLastError</code> for the FFM bindings.
 * <p>
 * The JVM may change the last error of a thread between a downcall and a later call of
 * <code>GetLastError</code>, so a binding whose error SWT reads links with {@link #CAPTURE} and passes
 * {@link #callState()} as the leading argument; <code>GetLastError</code> then returns what the last
 * such call on this thread left.
 * </p>
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

	/** The capture buffer of the calling thread, the leading argument of a capturing binding. */
	public static MemorySegment callState() {
		return STATE.get();
	}

	/** Links <code>name</code> so that it records the last error, see {@link FFM#downcall}. */
	public static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
		MethodHandle handle = FFM.downcallOptional(name, descriptor, CAPTURE);
		if (handle != null) return handle;
		return MethodHandles.dropArguments(FFM.downcall(name, descriptor), 0, MemorySegment.class);
	}

	public static int GetLastError() {
		return (int) ERROR.get(STATE.get(), 0L);
	}
}
