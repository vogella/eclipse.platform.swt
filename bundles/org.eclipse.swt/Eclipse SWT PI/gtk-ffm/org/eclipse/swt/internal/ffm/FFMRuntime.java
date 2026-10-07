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

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.concurrent.locks.*;

import org.eclipse.swt.internal.gtk3.GTK3_FFM;

/**
 * Java port of the last helpers of os_custom.c: the GDK lock functions and the debug flag
 * that makes GTK abort on a warning.
 */
public final class FFMRuntime {

	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	/** Replaces the GRecMutex the C code used, with the same reentrant semantics. */
	static final ReentrantLock GDK_LOCK = new ReentrantLock();

	private FFMRuntime() {
	}

	static void enter() {
		try {
			GDK_LOCK.lock();
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	/** Like g_rec_mutex_unlock, ignores a thread that does not hold the lock, which GTK does from nested main loops. */
	static void leave() {
		try {
			if (GDK_LOCK.isHeldByCurrentThread()) GDK_LOCK.unlock();
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	public static void swt_set_lock_functions() {
		try {
			MethodHandle enter = LOOKUP.findStatic(FFMRuntime.class, "enter", MethodType.methodType(void.class));
			MethodHandle leave = LOOKUP.findStatic(FFMRuntime.class, "leave", MethodType.methodType(void.class));
			FunctionDescriptor descriptor = FunctionDescriptor.ofVoid();
			GTK3_FFM.gdk_threads_set_lock_functions(
				FFM.LINKER.upcallStub(enter, descriptor, Arena.global()).address(),
				FFM.LINKER.upcallStub(leave, descriptor, Arena.global()).address());
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	/** gtk_parse_args must run before gtk_init for --g-fatal-warnings to take effect. */
	public static void swt_debug_on_fatal_warnings() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment program = arena.allocateFrom("");
			MemorySegment flag = arena.allocateFrom("--g-fatal-warnings");
			MemorySegment arguments = arena.allocate(JAVA_LONG, 2);
			arguments.setAtIndex(JAVA_LONG, 0, program.address());
			arguments.setAtIndex(JAVA_LONG, 1, flag.address());
			MemorySegment count = arena.allocateFrom(JAVA_INT, 2);
			MemorySegment pointer = arena.allocateFrom(JAVA_LONG, arguments.address());
			if (!GTK3_FFM.gtk_parse_args(count.address(), pointer.address())) {
				System.err.println("SWT-FFM: gtk_parse_args rejected --g-fatal-warnings");
			}
		}
	}
}
