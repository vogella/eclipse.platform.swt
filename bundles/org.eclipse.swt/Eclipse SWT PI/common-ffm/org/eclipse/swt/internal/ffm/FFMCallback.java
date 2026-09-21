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

import static java.lang.invoke.MethodType.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.*;

/**
 * Implements the native part of {@link org.eclipse.swt.internal.Callback} with upcall stubs.
 * Unlike the trampolines of callback.c there is no fixed pool and no fixed set of signatures.
 */
public final class FFMCallback {

	record Stub(Arena arena, MemorySegment address) {
	}

	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
	static final Map<Object, Stub> STUBS = new IdentityHashMap<>();
	static final java.util.concurrent.ConcurrentLinkedQueue<Arena> RETIRED = new java.util.concurrent.ConcurrentLinkedQueue<>();
	static final AtomicInteger ENTRY_COUNT = new AtomicInteger();

	static volatile boolean enabled = true;

	private FFMCallback() {
	}

	public static long bind(Object callback, Object object, String method, String signature, int argCount, boolean isStatic, boolean isArrayBased, long errorResult) {
		try {
			Class<?>[] types = parameterTypes(signature);
			Class<?> returnType = returnType(signature);
			MethodHandle target = target(object, method, isStatic, isArrayBased ? new Class<?>[] {long[].class} : types);
			if (isArrayBased) target = target.asCollector(long[].class, argCount);
			FunctionDescriptor descriptor = descriptor(returnType, isArrayBased ? arrayBasedTypes(argCount) : types);
			Stub stub = stub(target, descriptor, errorResult);
			synchronized (STUBS) {
				STUBS.put(callback, stub);
			}
			return stub.address().address();
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("SWT-FFM: cannot bind callback " + method + signature + ": " + e);
			return 0;
		}
	}

	public static void unbind(Object callback) {
		Stub stub;
		synchronized (STUBS) {
			stub = STUBS.remove(callback);
		}
		if (stub == null) return;
		RETIRED.add(stub.arena());
		release();
	}

	/**
	 * Frees retired stubs once no callback is running. Closing the arena of a stub that is still on a
	 * stack frees its code while native code is inside it, and unlike the trampolines of callback.c a
	 * stale pointer is then fatal rather than harmless.
	 */
	static void release() {
		if (ENTRY_COUNT.get() != 0) return;
		Arena arena;
		while ((arena = RETIRED.poll()) != null) {
			try {
				arena.close();
			} catch (IllegalStateException e) {
				RETIRED.add(arena);
				return;
			}
		}
	}

	public static void reset() {
		List<Stub> stubs;
		synchronized (STUBS) {
			stubs = new ArrayList<>(STUBS.values());
			STUBS.clear();
		}
		for (Stub stub : stubs) {
			RETIRED.add(stub.arena());
		}
		release();
	}

	public static String getPlatform() {
		return SWT.getPlatform();
	}

	public static int getEntryCount() {
		return ENTRY_COUNT.get();
	}

	public static void setEnabled(boolean enable) {
		enabled = enable;
	}

	public static boolean getEnabled() {
		return enabled;
	}

	/* ---------------------------------------------------------------- called from the stubs */

	static boolean isEnabled() {
		return enabled;
	}

	/** The exception pending when the callback started, which callback.c saved the same way. */
	static final ThreadLocal<ArrayDeque<Throwable>> SAVED = ThreadLocal.withInitial(ArrayDeque::new);

	static void enter() {
		ENTRY_COUNT.incrementAndGet();
		Throwable pending = FFM.takePending();
		SAVED.get().push(pending == null ? NONE : pending);
	}

	static void exit() {
		Throwable saved = SAVED.get().pop();
		if (saved != NONE) {
			// the older exception wins, as callback.c rethrows it after the callback
			Throwable mine = FFM.takePending();
			if (mine != null && mine != saved) saved.addSuppressed(mine);
			FFM.setPending(saved);
		}
		if (ENTRY_COUNT.decrementAndGet() == 0 && !RETIRED.isEmpty()) release();
	}

	static final Throwable NONE = new Throwable("no exception was pending");

	/**
	 * Keeps the failure pending for the thread instead of swallowing it, so that it surfaces when the
	 * native call that dispatched the callback returns to Java, which is what JNI did.
	 */
	static void report(Throwable t) {
		FFM.callbackFailed(t);
	}

	/* ---------------------------------------------------------------- stub construction */

	static MethodHandle target(Object object, String method, boolean isStatic, Class<?>[] types) throws ReflectiveOperationException {
		Class<?> clazz = isStatic ? (Class<?>) object : object.getClass();
		for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
			try {
				Method found = c.getDeclaredMethod(method, types);
				found.setAccessible(true);
				MethodHandle handle = LOOKUP.unreflect(found);
				return isStatic ? handle : handle.bindTo(object);
			} catch (NoSuchMethodException e) {
				// declared in a superclass
			}
		}
		throw new NoSuchMethodException(clazz.getName() + "." + method + Arrays.toString(types));
	}

	/**
	 * Wraps the callback so that it never throws into native code, returns 0 while callbacks are
	 * disabled and returns the error result of the callback when the Java side fails, as callback.c does.
	 */
	static Stub stub(MethodHandle target, FunctionDescriptor descriptor, long errorResult) throws ReflectiveOperationException {
		MethodType type = descriptor.toMethodType();
		Class<?> returnType = type.returnType();
		List<Class<?>> parameters = type.parameterList();
		MethodHandle handle = target.asType(type);

		MethodHandle failure = value(returnType, errorResult);
		failure = MethodHandles.dropArguments(failure, 0, Throwable.class);
		failure = MethodHandles.foldArguments(failure, LOOKUP.findStatic(FFMCallback.class, "report", methodType(void.class, Throwable.class)));
		handle = MethodHandles.catchException(handle, Throwable.class, MethodHandles.dropArguments(failure, 1, parameters));

		MethodHandle test = MethodHandles.dropArguments(LOOKUP.findStatic(FFMCallback.class, "isEnabled", methodType(boolean.class)), 0, parameters);
		handle = MethodHandles.guardWithTest(test, handle, MethodHandles.dropArguments(value(returnType, 0), 0, parameters));

		handle = MethodHandles.foldArguments(handle, LOOKUP.findStatic(FFMCallback.class, "enter", methodType(void.class)));
		MethodHandle cleanup = returnType == void.class
			? MethodHandles.empty(methodType(void.class, Throwable.class))
			: MethodHandles.dropArguments(MethodHandles.identity(returnType), 0, Throwable.class);
		cleanup = MethodHandles.foldArguments(cleanup, LOOKUP.findStatic(FFMCallback.class, "exit", methodType(void.class)));
		handle = MethodHandles.tryFinally(handle, MethodHandles.dropArguments(cleanup, returnType == void.class ? 1 : 2, parameters));

		Arena arena = Arena.ofShared();
		return new Stub(arena, FFM.LINKER.upcallStub(handle, descriptor, arena));
	}

	static MethodHandle value(Class<?> type, long value) {
		if (type == void.class) return MethodHandles.empty(methodType(void.class));
		if (type == long.class) return MethodHandles.constant(type, value);
		if (type == int.class) return MethodHandles.constant(type, (int) value);
		if (type == short.class) return MethodHandles.constant(type, (short) value);
		if (type == byte.class) return MethodHandles.constant(type, (byte) value);
		if (type == char.class) return MethodHandles.constant(type, (char) value);
		if (type == boolean.class) return MethodHandles.constant(type, value != 0);
		if (type == float.class) return MethodHandles.constant(type, (float) value);
		return MethodHandles.constant(type, (double) value);
	}

	/* ---------------------------------------------------------------- signatures */

	static Class<?>[] arrayBasedTypes(int argCount) {
		Class<?>[] types = new Class<?>[argCount];
		Arrays.fill(types, long.class);
		return types;
	}

	static Class<?>[] parameterTypes(String signature) {
		List<Class<?>> types = new ArrayList<>();
		for (int i = 1; signature.charAt(i) != ')'; i++) {
			if (signature.charAt(i) == '[') {
				i++;
				types.add(Array.newInstance(type(signature.charAt(i)), 0).getClass());
			} else {
				types.add(type(signature.charAt(i)));
			}
		}
		return types.toArray(new Class<?>[0]);
	}

	static Class<?> returnType(String signature) {
		return type(signature.charAt(signature.indexOf(')') + 1));
	}

	static Class<?> type(char letter) {
		switch (letter) {
			case 'V': return void.class;
			case 'Z': return boolean.class;
			case 'B': return byte.class;
			case 'C': return char.class;
			case 'S': return short.class;
			case 'I': return int.class;
			case 'J': return long.class;
			case 'F': return float.class;
			case 'D': return double.class;
			default: throw new IllegalArgumentException("signature letter " + letter);
		}
	}

	static FunctionDescriptor descriptor(Class<?> returnType, Class<?>[] parameterTypes) {
		MemoryLayout[] layouts = new MemoryLayout[parameterTypes.length];
		for (int i = 0; i < layouts.length; i++) layouts[i] = layout(parameterTypes[i]);
		return returnType == void.class ? FunctionDescriptor.ofVoid(layouts) : FunctionDescriptor.of(layout(returnType), layouts);
	}

	static MemoryLayout layout(Class<?> type) {
		if (type == boolean.class) return ValueLayout.JAVA_BOOLEAN;
		if (type == byte.class) return ValueLayout.JAVA_BYTE;
		if (type == char.class) return ValueLayout.JAVA_CHAR;
		if (type == short.class) return ValueLayout.JAVA_SHORT;
		if (type == int.class) return ValueLayout.JAVA_INT;
		if (type == float.class) return ValueLayout.JAVA_FLOAT;
		if (type == double.class) return ValueLayout.JAVA_DOUBLE;
		return ValueLayout.JAVA_LONG;
	}
}
