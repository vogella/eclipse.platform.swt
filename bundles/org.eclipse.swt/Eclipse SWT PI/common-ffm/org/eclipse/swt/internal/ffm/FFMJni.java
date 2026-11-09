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
import java.nio.file.*;

/**
 * Calls the JNI functions of the running VM through FFM, for native APIs that are defined in terms
 * of JNI, such as JAWT. Like JNI code, it ignores module encapsulation.
 * <p>
 * The VM clears the local references of a thread whenever a native method returns to Java, which
 * in interpreted code includes memory segment access and <code>Thread.currentThread()</code>. While
 * a local reference is live, callers must therefore only make calls of this class and compute with
 * <code>long</code> values; C strings and function pointers have to be prepared beforehand.
 * </p>
 */
public final class FFMJni implements AutoCloseable {

	static final int JNI_OK = 0, JNI_VERSION_1_8 = 0x10008, LOCAL_CAPACITY = 16;

	/* indices into JNINativeInterface_ and JNIInvokeInterface_ */
	static final int FIND_CLASS = 6, EXCEPTION_OCCURRED = 15, EXCEPTION_CLEAR = 17, PUSH_LOCAL_FRAME = 19, POP_LOCAL_FRAME = 20,
		NEW_GLOBAL_REF = 21, NEW_OBJECT = 28, GET_METHOD_ID = 33, CALL_OBJECT_METHOD = 34, CALL_VOID_METHOD = 61,
		GET_STATIC_METHOD_ID = 113, CALL_STATIC_OBJECT_METHOD = 114, GET_STATIC_FIELD_ID = 144, GET_STATIC_OBJECT_FIELD = 145,
		NEW_STRING_UTF = 167, EXCEPTION_CHECK = 228, GET_ENV = 6;

	/** Every JNI function is entered through these two call sites, so that none of them links while references are live. */
	static final MethodHandle CALL = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle CALL_VARIADIC = FFM.LINKER.downcallHandle(
		FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
		Linker.Option.firstVariadicArg(3));

	/** Hands objects between Java and JNI on the calling thread, since FFM cannot pass references. */
	static final ThreadLocal<Object> SLOT = new ThreadLocal<>();

	static final MemorySegment VM, GET_ENV_FUNCTION;
	static final MemorySegment[] FUNCTIONS = new MemorySegment[EXCEPTION_CHECK + 1];
	/** Global reference to {@link #SLOT} and the IDs of its accessors. */
	static final long SLOT_REF, SLOT_GET, SLOT_SET;

	static {
		VM = createdVM();
		GET_ENV_FUNCTION = VM.get(ADDRESS, 0).reinterpret((GET_ENV + 1) * ADDRESS.byteSize()).getAtIndex(ADDRESS, GET_ENV);
		// the function table is the same for every thread
		MemorySegment table = MemorySegment.ofAddress(currentEnv()).reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0)
			.reinterpret(FUNCTIONS.length * ADDRESS.byteSize());
		for (int i = 0; i < FUNCTIONS.length; i++) FUNCTIONS[i] = table.getAtIndex(ADDRESS, i);
		long[] ids = resolveSlot();
		SLOT_REF = ids[0];
		SLOT_GET = ids[1];
		SLOT_SET = ids[2];
	}

	final long env;

	/**
	 * Enters JNI on the current thread with <code>argument</code> available through {@link #argument()};
	 * closing it releases the local references created meanwhile.
	 */
	public FFMJni(Object argument) {
		SLOT.set(argument);
		env = currentEnv();
		if ((int) call(FUNCTIONS[PUSH_LOCAL_FRAME], env, LOCAL_CAPACITY, 0, 0) != JNI_OK) {
			SLOT.remove();
			throw new OutOfMemoryError("JNI PushLocalFrame");
		}
	}

	static long call(MemorySegment function, long a, long b, long c, long d) {
		try {
			return (long) CALL.invokeExact(function, a, b, c, d);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static long callVariadic(MemorySegment function, long env, long target, long method, long a, long b, long c, long d) {
		try {
			return (long) CALL_VARIADIC.invokeExact(function, env, target, method, a, b, c, d);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** Returns a NUL terminated copy of <code>string</code> that is never freed, for names used again and again. */
	public static MemorySegment cstring(String string) {
		return Arena.global().allocateFrom(string);
	}

	static SymbolLookup jvm() {
		try {
			// the dynamic loader matches the soname of the already loaded VM library
			return SymbolLookup.libraryLookup("libjvm.so", Arena.global());
		} catch (IllegalArgumentException e) {
			return SymbolLookup.libraryLookup(Path.of(System.getProperty("java.home"), "lib", "server", "libjvm.so"), Arena.global());
		}
	}

	static MemorySegment createdVM() {
		MemorySegment symbol = jvm().find("JNI_GetCreatedJavaVMs").orElseThrow(() -> new UnsatisfiedLinkError("JNI_GetCreatedJavaVMs"));
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment vms = arena.allocate(ADDRESS), count = arena.allocate(JAVA_INT);
			int rc = (int) call(symbol, vms.address(), 1, count.address(), 0);
			if (rc != JNI_OK || count.get(JAVA_INT, 0) < 1) throw new IllegalStateException("JNI_GetCreatedJavaVMs failed: " + rc);
			return vms.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize());
		}
	}

	static long currentEnv() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment env = arena.allocate(ADDRESS);
			int rc = (int) call(GET_ENV_FUNCTION, VM.address(), env.address(), JNI_VERSION_1_8, 0);
			if (rc != JNI_OK) throw new IllegalStateException("JNI GetEnv failed: " + rc);
			return env.get(JAVA_LONG, 0);
		}
	}

	/**
	 * JNI FindClass resolves against the loader of the calling Java method, a JDK frame during a
	 * downcall, so this class is loaded through the context class loader instead.
	 */
	static long[] resolveSlot() {
		MemorySegment threadName = cstring("java/lang/Thread"), currentThread = cstring("currentThread"),
			currentThreadSignature = cstring("()Ljava/lang/Thread;"), getLoader = cstring("getContextClassLoader"),
			getLoaderSignature = cstring("()Ljava/lang/ClassLoader;"), loaderName = cstring("java/lang/ClassLoader"),
			loadClass = cstring("loadClass"), loadClassSignature = cstring("(Ljava/lang/String;)Ljava/lang/Class;"),
			selfName = cstring(FFMJni.class.getName()), slotName = cstring("SLOT"), slotSignature = cstring("Ljava/lang/ThreadLocal;"),
			threadLocalName = cstring("java/lang/ThreadLocal"), get = cstring("get"), getSignature = cstring("()Ljava/lang/Object;"),
			set = cstring("set"), setSignature = cstring("(Ljava/lang/Object;)V");
		Thread thread = Thread.currentThread();
		ClassLoader context = thread.getContextClassLoader();
		thread.setContextClassLoader(FFMJni.class.getClassLoader());
		long env = currentEnv();
		// links the variadic call site before any reference is live
		callVariadic(FUNCTIONS[EXCEPTION_CHECK], env, 0, 0, 0, 0, 0, 0);
		try {
			if ((int) call(FUNCTIONS[PUSH_LOCAL_FRAME], env, LOCAL_CAPACITY, 0, 0) != JNI_OK) throw new OutOfMemoryError("JNI PushLocalFrame");
			try {
				long threadClass = pending(env, call(FUNCTIONS[FIND_CLASS], env, threadName.address(), 0, 0));
				long currentThreadId = pending(env, call(FUNCTIONS[GET_STATIC_METHOD_ID], env, threadClass, currentThread.address(), currentThreadSignature.address()));
				long current = pending(env, callVariadic(FUNCTIONS[CALL_STATIC_OBJECT_METHOD], env, threadClass, currentThreadId, 0, 0, 0, 0));
				long getLoaderId = pending(env, call(FUNCTIONS[GET_METHOD_ID], env, threadClass, getLoader.address(), getLoaderSignature.address()));
				long loader = pending(env, callVariadic(FUNCTIONS[CALL_OBJECT_METHOD], env, current, getLoaderId, 0, 0, 0, 0));
				long loaderClass = pending(env, call(FUNCTIONS[FIND_CLASS], env, loaderName.address(), 0, 0));
				long loadClassId = pending(env, call(FUNCTIONS[GET_METHOD_ID], env, loaderClass, loadClass.address(), loadClassSignature.address()));
				long name = pending(env, call(FUNCTIONS[NEW_STRING_UTF], env, selfName.address(), 0, 0));
				long self = pending(env, callVariadic(FUNCTIONS[CALL_OBJECT_METHOD], env, loader, loadClassId, name, 0, 0, 0));
				long field = pending(env, call(FUNCTIONS[GET_STATIC_FIELD_ID], env, self, slotName.address(), slotSignature.address()));
				long threadLocal = pending(env, call(FUNCTIONS[GET_STATIC_OBJECT_FIELD], env, self, field, 0));
				long slot = pending(env, call(FUNCTIONS[NEW_GLOBAL_REF], env, threadLocal, 0, 0));
				long threadLocalClass = pending(env, call(FUNCTIONS[FIND_CLASS], env, threadLocalName.address(), 0, 0));
				long slotGet = pending(env, call(FUNCTIONS[GET_METHOD_ID], env, threadLocalClass, get.address(), getSignature.address()));
				long slotSet = pending(env, call(FUNCTIONS[GET_METHOD_ID], env, threadLocalClass, set.address(), setSignature.address()));
				return new long[] {slot, slotGet, slotSet};
			} finally {
				call(FUNCTIONS[POP_LOCAL_FRAME], env, 0, 0, 0);
			}
		} finally {
			thread.setContextClassLoader(context);
		}
	}

	/** Returns <code>result</code> unless JNI failed, before {@link #SLOT} can carry the exception to Java. */
	static long pending(long env, long result) {
		if ((call(FUNCTIONS[EXCEPTION_CHECK], env, 0, 0, 0) & 0xFF) == 0 && result != 0) return result;
		call(FUNCTIONS[EXCEPTION_CLEAR], env, 0, 0, 0);
		throw new IllegalStateException("JNI could not resolve " + FFMJni.class.getName());
	}

	/** Throws the exception JNI left pending, which a JNI native method would have thrown on return. */
	public void check() {
		if ((call(FUNCTIONS[EXCEPTION_CHECK], env, 0, 0, 0) & 0xFF) == 0) return;
		long throwable = call(FUNCTIONS[EXCEPTION_OCCURRED], env, 0, 0, 0);
		call(FUNCTIONS[EXCEPTION_CLEAR], env, 0, 0, 0);
		Throwable pending = result(throwable);
		throw FFM.rethrow(pending);
	}

	/** The <code>JNIEnv</code> pointer of the current thread. */
	public long env() {
		return env;
	}

	public long findClass(MemorySegment name) {
		long clazz = call(FUNCTIONS[FIND_CLASS], env, name.address(), 0, 0);
		check();
		return clazz;
	}

	public long method(long clazz, MemorySegment name, MemorySegment signature) {
		long id = call(FUNCTIONS[GET_METHOD_ID], env, clazz, name.address(), signature.address());
		check();
		return id;
	}

	/** Calls a constructor with up to four arguments, passed as C default argument promotions do. */
	public long newObject(long clazz, long constructor, long a, long b, long c, long d) {
		long object = callVariadic(FUNCTIONS[NEW_OBJECT], env, clazz, constructor, a, b, c, d);
		check();
		return object;
	}

	/** Calls a void method with up to four arguments, passed as C default argument promotions do. */
	public void callVoid(long object, long method, long a, long b, long c, long d) {
		callVariadic(FUNCTIONS[CALL_VOID_METHOD], env, object, method, a, b, c, d);
		check();
	}

	/** Returns a local reference to the argument given to the constructor. */
	public long argument() {
		long ref = callVariadic(FUNCTIONS[CALL_OBJECT_METHOD], env, SLOT_REF, SLOT_GET, 0, 0, 0, 0);
		check();
		return ref;
	}

	/** Returns the object <code>ref</code> refers to; the references of this scope may be gone afterwards. */
	@SuppressWarnings("unchecked")
	public <T> T result(long ref) {
		if (ref == 0) return null;
		callVariadic(FUNCTIONS[CALL_VOID_METHOD], env, SLOT_REF, SLOT_SET, ref, 0, 0, 0);
		if ((call(FUNCTIONS[EXCEPTION_CHECK], env, 0, 0, 0) & 0xFF) != 0) {
			call(FUNCTIONS[EXCEPTION_CLEAR], env, 0, 0, 0);
			throw new IllegalStateException("JNI could not hand over a reference");
		}
		return (T) SLOT.get();
	}

	@Override
	public void close() {
		call(FUNCTIONS[POP_LOCAL_FRAME], env, 0, 0, 0);
		SLOT.remove();
	}
}
