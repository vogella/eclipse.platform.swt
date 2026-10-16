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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.internal.cocoa.*;

/**
 * Java port of the natives of os_custom.c and of the other Cocoa natives that the generator cannot
 * produce: global references as a handle table, struct out parameters, strings, the completion block
 * and the dynamic Carbon and Foundation functions.
 */
public final class FFMCocoaCustom {

	/** The natives class whose natives the public static methods of this class implement. */
	static final String IMPLEMENTS = "org.eclipse.swt.internal.cocoa.OS";

	static final Linker LINKER = Linker.nativeLinker();
	static final SymbolLookup LOOKUP = lookup();

	static final StructLayout RECT = MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE);

	static final MethodHandle INTERSECTION_RECT = downcall("NSIntersectionRect", FunctionDescriptor.of(RECT, RECT, RECT));
	static final MethodHandle DISPLAY_BOUNDS = downcall("CGDisplayBounds", FunctionDescriptor.of(RECT, JAVA_INT));
	static final MethodHandle CLASS_GET_NAME = downcall("class_getName", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle CLASS_CREATE_INSTANCE = downcall("class_createInstance", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle MSG_SEND_AUTORELEASE = downcall("objc_msgSend", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
	static final MethodHandle MSG_SEND_BLOCK = downcall("objc_msgSend", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle SEL_REGISTER_NAME = downcall("sel_registerName", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
	static final MethodHandle BLOCK_COPY = downcall("_Block_copy", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));

	static final MethodHandle INSTRUMENT = optional("instrumentObjcMessageSends", FunctionDescriptor.ofVoid(JAVA_BOOLEAN));
	static final MethodHandle GET_INPUT_SOURCE_PROPERTY = optional("TISGetInputSourceProperty", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));

	/* Global references: a handle stands for the object, which only SWT stores in the instance variables of its subclasses */
	static final Map<Long, Object> REFERENCES = new ConcurrentHashMap<>();
	static final AtomicLong NEXT_REFERENCE = new AtomicLong(1);

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(FFMCocoaCustom::detachApplicationDelegate, "SWT-detach-application-delegate"));
	}

	private FFMCocoaCustom() {
	}

	/**
	 * Takes SWTApplicationDelegate, a process wide object that Display never releases, off NSApplication
	 * before the VM is destroyed. AppKit still sends it notifications while the process exits, and an
	 * upcall stub has no code left to run then, where the trampolines of callback.c returned harmlessly.
	 */
	static void detachApplicationDelegate() {
		long delegateClass = OS.objc_lookUpClass("SWTApplicationDelegate");
		if (delegateClass == 0) return;
		long application = OS.objc_msgSend(OS.class_NSApplication, OS.sel_sharedApplication);
		long delegate = OS.objc_msgSend(application, OS.sel_delegate);
		if (delegate != 0 && OS.object_getClass(delegate) == delegateClass) {
			OS.objc_msgSend(application, OS.sel_setDelegate_, 0);
		}
	}

	static SymbolLookup lookup() {
		SymbolLookup lookup = LINKER.defaultLookup();
		for (String framework : new String[] {"Foundation", "AppKit", "Carbon", "ApplicationServices"}) {
			try {
				lookup = lookup.or(SymbolLookup.libraryLookup("/System/Library/Frameworks/" + framework + ".framework/" + framework, Arena.global()));
			} catch (IllegalArgumentException e) {
				// framework not present
			}
		}
		try {
			lookup = lookup.or(SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global()));
		} catch (IllegalArgumentException e) {
			// the default lookup covers libobjc
		}
		return lookup;
	}

	static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
		return LINKER.downcallHandle(LOOKUP.findOrThrow(name), descriptor);
	}

	/** The handle, or <code>null</code> where the function is absent, as the dynamic natives of the C glue. */
	static MethodHandle optional(String name, FunctionDescriptor descriptor) {
		return LOOKUP.find(name).map(symbol -> LINKER.downcallHandle(symbol, descriptor)).orElse(null);
	}

	/* ---------------------------------------------------------------- global references */

	public static long NewGlobalRef(Object object) {
		if (object == null) return 0;
		long handle = NEXT_REFERENCE.getAndIncrement();
		REFERENCES.put(handle, object);
		return handle;
	}

	public static void DeleteGlobalRef(long globalRef) {
		REFERENCES.remove(globalRef);
	}

	public static Object JNIGetObject(long globalRef) {
		return REFERENCES.get(globalRef);
	}

	/* ---------------------------------------------------------------- structs and strings */

	public static void NSIntersectionRect(NSRect result, NSRect aRect, NSRect bRect) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment a = arena.allocate(RECT), b = arena.allocate(RECT);
			put(a, aRect.x, aRect.y, aRect.width, aRect.height);
			put(b, bRect.x, bRect.y, bRect.width, bRect.height);
			MemorySegment r = (MemorySegment) INTERSECTION_RECT.invokeExact((SegmentAllocator) arena, a, b);
			result.x = r.get(JAVA_DOUBLE, 0);
			result.y = r.get(JAVA_DOUBLE, 8);
			result.width = r.get(JAVA_DOUBLE, 16);
			result.height = r.get(JAVA_DOUBLE, 24);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static void CGDisplayBounds(int display, CGRect rect) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment r = (MemorySegment) DISPLAY_BOUNDS.invokeExact((SegmentAllocator) arena, display);
			rect.origin.x = r.get(JAVA_DOUBLE, 0);
			rect.origin.y = r.get(JAVA_DOUBLE, 8);
			rect.size.width = r.get(JAVA_DOUBLE, 16);
			rect.size.height = r.get(JAVA_DOUBLE, 24);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static void put(MemorySegment rect, double x, double y, double width, double height) {
		rect.set(JAVA_DOUBLE, 0, x);
		rect.set(JAVA_DOUBLE, 8, y);
		rect.set(JAVA_DOUBLE, 16, width);
		rect.set(JAVA_DOUBLE, 24, height);
	}

	public static String class_getName(long cls) {
		try {
			long name = (long) CLASS_GET_NAME.invokeExact(cls);
			return name == 0 ? null : FFM.segment(name, Long.MAX_VALUE).getString(0);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long class_createInstance(long cls, long extraBytes) {
		try {
			return (long) CLASS_CREATE_INSTANCE.invokeExact(cls, extraBytes);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/* ---------------------------------------------------------------- dynamic functions and constants */

	public static void instrumentObjcMessageSends(boolean val) {
		if (INSTRUMENT == null) return;
		try {
			INSTRUMENT.invokeExact(val);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long TISGetInputSourceProperty(long inputSource, long propertyKey) {
		if (GET_INPUT_SOURCE_PROPERTY == null) return 0;
		try {
			return (long) GET_INPUT_SOURCE_PROPERTY.invokeExact(inputSource, propertyKey);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** The value of the exported {@code CFStringRef} variable, 0 where Carbon does not have it. */
	public static long kTISPropertyUnicodeKeyLayoutData() {
		return LOOKUP.find("kTISPropertyUnicodeKeyLayoutData").map(symbol -> FFM.segment(symbol.address(), 8).get(JAVA_LONG, 0)).orElse(0L);
	}

	/* ---------------------------------------------------------------- isFlipped */

	/** An {@code isFlipped} implementation returning YES, which lives as long as the process. */
	static final MemorySegment IS_FLIPPED = LINKER.upcallStub(isFlippedHandle(), FunctionDescriptor.of(JAVA_BYTE, JAVA_LONG, JAVA_LONG), Arena.global());

	static MethodHandle isFlippedHandle() {
		try {
			return MethodHandles.lookup().findStatic(FFMCocoaCustom.class, "isFlipped", MethodType.methodType(byte.class, long.class, long.class));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	static byte isFlipped(long id, long sel) {
		return 1;
	}

	public static long isFlipped_CALLBACK() {
		return IS_FLIPPED.address();
	}

	/* ---------------------------------------------------------------- completion block */

	/** Offset of the handler in a block literal, behind isa, flags, reserved, invoke and descriptor. */
	static final long BLOCK_HANDLER = 32;
	static final long BLOCK_SIZE = 40;
	static final int BLOCK_HAS_SIGNATURE = 1 << 30;

	static final long STACK_BLOCK = LOOKUP.findOrThrow("_NSConcreteStackBlock").address();
	static final MethodHandle HANDLER_CALL = LINKER.downcallHandle(FunctionDescriptor.ofVoid(JAVA_LONG));
	static final MemorySegment BLOCK_INVOKE = blockInvoke();
	static final MemorySegment BLOCK_DESCRIPTOR = blockDescriptor();

	/** The invoke function of the block, which calls the handler stored in it with the result. */
	static MemorySegment blockInvoke() {
		try {
			MethodHandle target = MethodHandles.lookup().findStatic(FFMCocoaCustom.class, "invokeBlock", MethodType.methodType(void.class, long.class, long.class));
			return LINKER.upcallStub(target, FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG), Arena.global());
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	static void invokeBlock(long block, long result) {
		try {
			long handler = FFM.segment(block, BLOCK_SIZE).get(JAVA_LONG, BLOCK_HANDLER);
			HANDLER_CALL.invokeExact(MemorySegment.ofAddress(handler), result);
		} catch (Throwable e) {
			FFM.callbackFailed(e);
		}
	}

	static MemorySegment blockDescriptor() {
		MemorySegment descriptor = Arena.global().allocate(24, 8);
		descriptor.set(JAVA_LONG, 8, BLOCK_SIZE);
		descriptor.set(JAVA_LONG, 16, Arena.global().allocateFrom("v16@?0q8").address());
		return descriptor;
	}

	/**
	 * Calls the selector with a block that calls <code>handler</code>. The block is copied to the heap
	 * and autoreleased, so it lives as long as the callee keeps it, as the one the C glue passed.
	 */
	public static void beginSheetModalForWindow(long id, long sel, long window, long handler) {
		try {
			long block = newBlock(handler);
			MSG_SEND_BLOCK.invokeExact(id, sel, window, block);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	/** A heap block with one reference that the current autorelease pool owns. */
	static long newBlock(long handler) throws Throwable {
		long copy;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment literal = arena.allocate(BLOCK_SIZE, 8);
			literal.set(JAVA_LONG, 0, STACK_BLOCK);
			literal.set(JAVA_INT, 8, BLOCK_HAS_SIGNATURE);
			literal.set(JAVA_LONG, 16, BLOCK_INVOKE.address());
			literal.set(JAVA_LONG, 24, BLOCK_DESCRIPTOR.address());
			literal.set(JAVA_LONG, BLOCK_HANDLER, handler);
			copy = (long) BLOCK_COPY.invokeExact(literal.address());
			long autorelease = (long) SEL_REGISTER_NAME.invokeExact(arena.allocateFrom("autorelease"));
			MSG_SEND_AUTORELEASE.invokeExact(copy, autorelease);
		}
		return copy;
	}
}
