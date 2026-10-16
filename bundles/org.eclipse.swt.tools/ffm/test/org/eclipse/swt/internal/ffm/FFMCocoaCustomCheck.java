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
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cocoa.*;

/**
 * Compares the hand written Cocoa ports (FFMCocoaCustom, FFMCocoaCallbacks) with the JNI natives of
 * os_custom.c and os.c in one process. Run with -XstartOnFirstThread and the aarch64 fragment on
 * java.library.path; the system property check.os names OS.java, which lists the CALLBACK_ signatures.
 */
public class FFMCocoaCustomCheck {

	static int checks, failures;
	static final boolean VERBOSE = System.getProperty("check.verbose") != null;

	static void check(String what, Object jni, Object ffm) {
		checks++;
		boolean ok = Objects.equals(jni, ffm);
		if (!ok) failures++;
		if (!ok || VERBOSE) System.out.println((ok ? "OK   " : "FAIL ") + what + ": jni=" + jni + " ffm=" + ffm);
	}

	static final Linker LINKER = Linker.nativeLinker();

	static MethodHandle h(String name, FunctionDescriptor descriptor) {
		return LINKER.downcallHandle(FFMCocoaCustom.LOOKUP.findOrThrow(name), descriptor);
	}

	static final MethodHandle MALLOC = h("malloc", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle MSG_SEND = h("objc_msgSend", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle IS_FLIPPED = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_BYTE, JAVA_LONG, JAVA_LONG));
	static final MethodHandle BLOCK_CALL = LINKER.downcallHandle(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
	static final MethodHandle MAIN_DISPLAY = h("CGMainDisplayID", FunctionDescriptor.of(JAVA_INT));
	static final MethodHandle OBJECT_DISPOSE = h("object_dispose", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle GET_CLASS = h("object_getClass", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle ALLOCATE_CLASS = h("objc_allocateClassPair", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG));
	static final MethodHandle REGISTER_CLASS = h("objc_registerClassPair", FunctionDescriptor.ofVoid(JAVA_LONG));
	static final MethodHandle ADD_METHOD = h("class_addMethod", FunctionDescriptor.of(JAVA_BYTE, JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS));
	static final MethodHandle BLOCK_COPY = h("_Block_copy", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle BLOCK_RELEASE = h("_Block_release", FunctionDescriptor.ofVoid(JAVA_LONG));

	static long call(MethodHandle handle, Object... args) {
		try {
			Object result = handle.invokeWithArguments(args);
			return result == null ? 0 : ((Number) result).longValue();
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static String hex(double d) {
		return Long.toHexString(Double.doubleToRawLongBits(d));
	}

	/* ---------------------------------------------------------------- global references */

	static void globalReferences() {
		Object a = new Object(), b = "text";
		long ja = OS.NewGlobalRef(a), fa = FFMCocoaCustom.NewGlobalRef(a);
		long jb = OS.NewGlobalRef(b), fb = FFMCocoaCustom.NewGlobalRef(b);
		check("NewGlobalRef non-zero", ja != 0 && jb != 0, fa != 0 && fb != 0);
		check("NewGlobalRef distinct objects", ja != jb, fa != fb);
		check("JNIGetObject a", OS.JNIGetObject(ja) == a, FFMCocoaCustom.JNIGetObject(fa) == a);
		check("JNIGetObject b", OS.JNIGetObject(jb) == b, FFMCocoaCustom.JNIGetObject(fb) == b);
		long ja2 = OS.NewGlobalRef(a), fa2 = FFMCocoaCustom.NewGlobalRef(a);
		check("NewGlobalRef same object twice", ja2 != ja, fa2 != fa);
		OS.DeleteGlobalRef(ja);
		FFMCocoaCustom.DeleteGlobalRef(fa);
		check("second reference survives delete", OS.JNIGetObject(ja2) == a, FFMCocoaCustom.JNIGetObject(fa2) == a);
		check("other reference survives delete", OS.JNIGetObject(jb) == b, FFMCocoaCustom.JNIGetObject(fb) == b);
		check("deleted handle", true, FFMCocoaCustom.JNIGetObject(fa) == null);
		OS.DeleteGlobalRef(ja2);
		OS.DeleteGlobalRef(jb);
		FFMCocoaCustom.DeleteGlobalRef(fa2);
		FFMCocoaCustom.DeleteGlobalRef(fb);
		check("NewGlobalRef null", OS.NewGlobalRef(null), FFMCocoaCustom.NewGlobalRef(null));
		check("JNIGetObject 0", OS.JNIGetObject(0), FFMCocoaCustom.JNIGetObject(0));
	}

	/* ---------------------------------------------------------------- structs */

	static NSRect rect(double x, double y, double w, double h) {
		NSRect r = new NSRect();
		r.x = x;
		r.y = y;
		r.width = w;
		r.height = h;
		return r;
	}

	static String show(NSRect r) {
		return hex(r.x) + "," + hex(r.y) + "," + hex(r.width) + "," + hex(r.height);
	}

	static void intersectionRect() {
		double[][] cases = {
			{0, 0, 10, 10, 5, 5, 10, 10}, {0, 0, 10, 10, 20, 20, 5, 5}, {0, 0, 10, 10, 10, 0, 5, 5}, {-5, -5, 10, 10, 0, 0, 10, 10},
			{0, 0, 100, 100, 10, 10, 5, 5}, {0.5, 0.25, 3.75, 2.125, 1.5, 1.0, 1.0, 5.0}, {0, 0, 0, 0, 0, 0, 0, 0}, {1, 1, -3, 4, 0, 0, 5, 5},
			{Double.MAX_VALUE, 0, 1, 1, 0, 0, 1, 1}, {Double.NaN, 0, 1, 1, 0, 0, 1, 1},
		};
		int n = 0;
		for (double[] c : cases) {
			NSRect jr = rect(-1, -2, -3, -4), fr = rect(-1, -2, -3, -4);
			NSRect ja = rect(c[0], c[1], c[2], c[3]), fa = rect(c[0], c[1], c[2], c[3]);
			NSRect jb = rect(c[4], c[5], c[6], c[7]), fb = rect(c[4], c[5], c[6], c[7]);
			OS.NSIntersectionRect(jr, ja, jb);
			FFMCocoaCustom.NSIntersectionRect(fr, fa, fb);
			check("NSIntersectionRect " + n, show(jr) + "|" + show(ja) + "|" + show(jb), show(fr) + "|" + show(fa) + "|" + show(fb));
			// result aliasing the first operand
			OS.NSIntersectionRect(ja, ja, jb);
			FFMCocoaCustom.NSIntersectionRect(fa, fa, fb);
			check("NSIntersectionRect aliased " + n++, show(ja), show(fa));
		}
	}

	static String show(CGRect r) {
		return hex(r.origin.x) + "," + hex(r.origin.y) + "," + hex(r.size.width) + "," + hex(r.size.height);
	}

	static void displayBounds() {
		List<Integer> displays = new ArrayList<>(List.of((int) call(MAIN_DISPLAY), 0, 12345));
		for (int display : displays) {
			CGRect jni = new CGRect(), ffm = new CGRect();
			OS.CGDisplayBounds(display, jni);
			FFMCocoaCustom.CGDisplayBounds(display, ffm);
			check("CGDisplayBounds " + display, show(jni), show(ffm));
		}
	}

	/* ---------------------------------------------------------------- strings, instances, constants */

	static void classes() {
		for (String name : new String[] {"NSObject", "NSString", "NSView", "NSWindow", "NSMutableDictionary", "NoSuchClassAnywhere"}) {
			long cls = OS.objc_getClass(name);
			check("class_getName " + name, OS.class_getName(cls), FFMCocoaCustom.class_getName(cls));
		}
		check("class_getName 0", OS.class_getName(0), FFMCocoaCustom.class_getName(0));
		long metaclass = call(GET_CLASS, OS.objc_getClass("NSObject"));
		check("class_getName metaclass", OS.class_getName(metaclass), FFMCocoaCustom.class_getName(metaclass));
		for (String name : new String[] {"NSObject", "NSString", "NSMutableArray"}) {
			long cls = OS.objc_getClass(name);
			long jni = OS.class_createInstance(cls, 0), ffm = FFMCocoaCustom.class_createInstance(cls, 0);
			check("class_createInstance " + name, jni != 0 ? OS.class_getName(call(GET_CLASS, jni)) : null,
				ffm != 0 ? FFMCocoaCustom.class_getName(call(GET_CLASS, ffm)) : null);
			long jniExtra = OS.class_createInstance(cls, 64), ffmExtra = FFMCocoaCustom.class_createInstance(cls, 64);
			check("class_createInstance extra " + name, jniExtra != 0, ffmExtra != 0);
			call(OBJECT_DISPOSE, jni);
			call(OBJECT_DISPOSE, ffm);
			call(OBJECT_DISPOSE, jniExtra);
			call(OBJECT_DISPOSE, ffmExtra);
		}
	}

	static void carbon() {
		check("kTISPropertyUnicodeKeyLayoutData", OS.kTISPropertyUnicodeKeyLayoutData(), FFMCocoaCustom.kTISPropertyUnicodeKeyLayoutData());
		check("kTISPropertyUnicodeKeyLayoutData non-zero", true, FFMCocoaCustom.kTISPropertyUnicodeKeyLayoutData() != 0);
		long source = OS.TISCopyCurrentKeyboardInputSource();
		long key = OS.kTISPropertyUnicodeKeyLayoutData();
		check("TISGetInputSourceProperty", OS.TISGetInputSourceProperty(source, key), FFMCocoaCustom.TISGetInputSourceProperty(source, key));
		check("TISGetInputSourceProperty no source", OS.TISGetInputSourceProperty(0, key), FFMCocoaCustom.TISGetInputSourceProperty(0, key));
		OS.instrumentObjcMessageSends(false);
		FFMCocoaCustom.instrumentObjcMessageSends(false);
		check("instrumentObjcMessageSends", "returned", "returned");
	}

	static void isFlipped() {
		long jni = OS.isFlipped_CALLBACK(), ffm = FFMCocoaCustom.isFlipped_CALLBACK();
		check("isFlipped_CALLBACK non-zero", jni != 0, ffm != 0);
		check("isFlipped_CALLBACK stable", ffm, FFMCocoaCustom.isFlipped_CALLBACK());
		long[][] arguments = {{0, 0}, {0x1000, 0x2000}, {-1, -1}};
		for (long[] a : arguments) {
			check("isFlipped " + a[0], invokeFlipped(jni, a), invokeFlipped(ffm, a));
		}
		// installed as the isFlipped of a real class
		long jniView = subclassWithImp("SWTCheckFlippedJni", "isFlipped", jni, "c@:");
		long ffmView = subclassWithImp("SWTCheckFlippedFfm", "isFlipped", ffm, "c@:");
		long jniObject = OS.class_createInstance(jniView, 0), ffmObject = OS.class_createInstance(ffmView, 0);
		long flipped = OS.sel_registerName("isFlipped");
		check("isFlipped through objc_msgSend", msgSendByte(jniObject, flipped), msgSendByte(ffmObject, flipped));
		call(OBJECT_DISPOSE, jniObject);
		call(OBJECT_DISPOSE, ffmObject);
	}

	static byte msgSendByte(long object, long selector) {
		try {
			MethodHandle send = h("objc_msgSend", FunctionDescriptor.of(JAVA_BYTE, JAVA_LONG, JAVA_LONG));
			return (byte) send.invokeExact(object, selector);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static byte invokeFlipped(long function, long[] a) {
		try {
			return (byte) IS_FLIPPED.invokeExact(MemorySegment.ofAddress(function), a[0], a[1]);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static long subclassWithImp(String className, String selector, long imp, String types) {
		try (Arena arena = Arena.ofConfined()) {
			long cls = call(ALLOCATE_CLASS, OS.objc_getClass("NSObject"), arena.allocateFrom(className), 0L);
			call(ADD_METHOD, cls, OS.sel_registerName(selector), imp, arena.allocateFrom(types));
			call(REGISTER_CLASS, cls);
			return cls;
		}
	}

	/* ---------------------------------------------------------------- completion block */

	static final List<String> BLOCK_LOG = new ArrayList<>();
	static long keptBlock;

	/** The receiver of the sheet: inspects the block, calls it, and keeps a copy to call after the sheet call returned. */
	static void sheetImp(long self, long sel, long window, long block) {
		try {
			MemorySegment literal = FFM.segment(block, 40);
			BLOCK_LOG.add("sheet window=" + window);
			BLOCK_LOG.add("invoke non-null=" + (literal.get(JAVA_LONG, 16) != 0));
			BLOCK_LOG.add("signature flag=" + ((literal.get(JAVA_INT, 8) & (1 << 30)) != 0));
			BLOCK_LOG.add("isa class=" + OS.class_getName(literal.get(JAVA_LONG, 0)));
			keptBlock = call(BLOCK_COPY, block);
			BLOCK_CALL.invokeExact(MemorySegment.ofAddress(literal.get(JAVA_LONG, 16)), block, 42L);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public long handler(long result) {
		BLOCK_LOG.add("handler result=" + result);
		return 0;
	}

	static List<String> runSheet(boolean ffm, long handler, long receiver, long selector, long pool) {
		BLOCK_LOG.clear();
		long autoreleasePool = newPool();
		if (ffm) FFMCocoaCustom.beginSheetModalForWindow(receiver, selector, 0xABCDEFL, handler);
		else OS.beginSheetModalForWindow(receiver, selector, 0xABCDEFL, handler);
		drain(autoreleasePool);
		// the callee's copy has to outlive the caller's pool, and call the handler again
		try {
			MemorySegment literal = FFM.segment(keptBlock, 40);
			BLOCK_LOG.add("kept flags have refcount=" + ((literal.get(JAVA_INT, 8) & 0xFFFF) != 0));
			BLOCK_CALL.invokeExact(MemorySegment.ofAddress(literal.get(JAVA_LONG, 16)), keptBlock, 7L);
			BLOCK_CALL.invokeExact(MemorySegment.ofAddress(literal.get(JAVA_LONG, 16)), keptBlock, -1L);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
		call(BLOCK_RELEASE, keptBlock);
		keptBlock = 0;
		return new ArrayList<>(BLOCK_LOG);
	}

	static long newPool() {
		long pool = call(MSG_SEND, OS.objc_getClass("NSAutoreleasePool"), OS.sel_registerName("alloc"));
		return call(MSG_SEND, pool, OS.sel_registerName("init"));
	}

	static void drain(long pool) {
		call(MSG_SEND, pool, OS.sel_registerName("drain"));
	}

	static void block() throws Exception {
		MethodHandle imp = MethodHandles.lookup().findStatic(FFMCocoaCustomCheck.class, "sheetImp", MethodType.methodType(void.class, long.class, long.class, long.class, long.class));
		MemorySegment stub = LINKER.upcallStub(imp, FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG), Arena.global());
		long cls = subclassWithImp("SWTCheckSheet", "swtCheckBegin:handler:", stub.address(), "v@:@@?");
		long receiver = OS.class_createInstance(cls, 0);
		long selector = OS.sel_registerName("swtCheckBegin:handler:");

		FFMCocoaCustomCheck target = new FFMCocoaCustomCheck();
		Callback jniCallback = new Callback(target, "handler", 1);
		long ffmCallback = FFMCallback.bind(new Object(), target, "handler", "(J)J", 1, false, false, 0);
		List<String> jni = runSheet(false, jniCallback.getAddress(), receiver, selector, 0);
		List<String> ffm = runSheet(true, ffmCallback, receiver, selector, 0);
		check("block log size", jni.size(), ffm.size());
		check("block log", jni, ffm);
		check("block handler called three times", 3L, ffm.stream().filter(s -> s.startsWith("handler")).count());
		for (int round = 0; round < 50; round++) {
			check("block round " + round, runSheet(false, jniCallback.getAddress(), receiver, selector, 0), runSheet(true, ffmCallback, receiver, selector, 0));
		}
		jniCallback.dispose();
		call(OBJECT_DISPOSE, receiver);
	}

	/* ---------------------------------------------------------------- CALLBACK_ trampolines */

	record Spec(String name, String[] types, boolean[] flags) {
		boolean structReturn() {
			return flags[0];
		}
	}

	static final Pattern DECLARATION = Pattern.compile("callback_types=([^,]*),callback_flags=([^ ]*) \\*/\\s*public static final native long (CALLBACK_\\w+)\\(long func\\);");

	static List<Spec> specs() throws Exception {
		List<Spec> result = new ArrayList<>();
		Matcher m = DECLARATION.matcher(Files.readString(Path.of(System.getProperty("check.os"))));
		while (m.find()) {
			String[] types = m.group(1).split(";");
			String[] flags = m.group(2).split(";");
			boolean[] structs = new boolean[flags.length];
			for (int i = 0; i < flags.length; i++) structs[i] = flags[i].equals("struct");
			result.add(new Spec(m.group(3), types, structs));
		}
		return result;
	}

	static boolean structType(String type) {
		return type.equals("NSPoint") || type.equals("NSSize") || type.equals("NSRect") || type.equals("NSRange");
	}

	static MemoryLayout layoutOf(String type) {
		return switch (type) {
			case "NSPoint", "NSSize" -> MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE);
			case "NSRect" -> MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE);
			case "NSRange" -> MemoryLayout.structLayout(JAVA_LONG, JAVA_LONG);
			case "BOOL" -> JAVA_BYTE;
			default -> JAVA_LONG;
		};
	}

	/** The SWT callback: records what it is given and returns what the check asks for. */
	public static class Recorder {
		Spec spec;
		final List<String> log = new ArrayList<>();
		/** 0: the sample value, 1: a null pointer or zero. */
		int returnMode;
		long scalarReturn;

		long record(long... a) {
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < a.length; i++) {
				String type = spec.types[i + 1];
				b.append(type).append('=');
				if (structType(type)) {
					MemorySegment s = FFM.segment(a[i], layoutOf(type).byteSize());
					for (int offset = 0; offset < s.byteSize(); offset += 8) b.append(Long.toHexString(s.get(JAVA_LONG, offset))).append('/');
				} else {
					b.append(Long.toHexString(a[i]));
				}
				b.append(' ');
			}
			log.add(b.toString());
			String type = spec.types[0];
			if (spec.structReturn()) {
				if (returnMode == 1) return 0;
				long size = type.equals("id") ? 8 : layoutOf(type).byteSize();
				long memory = call(MALLOC, size);
				MemorySegment s = FFM.segment(memory, size);
				for (int offset = 0; offset < size; offset += 8) s.set(JAVA_LONG, offset, sample(offset));
				return memory;
			}
			return scalarReturn;
		}

		public long p2(long a, long b) {
			return record(a, b);
		}

		public long p3(long a, long b, long c) {
			return record(a, b, c);
		}

		public long p4(long a, long b, long c, long d) {
			return record(a, b, c, d);
		}

		public long p5(long a, long b, long c, long d, long e) {
			return record(a, b, c, d, e);
		}

		public long p6(long a, long b, long c, long d, long e, long f) {
			return record(a, b, c, d, e, f);
		}
	}

	static long sample(long offset) {
		return Double.doubleToRawLongBits(1.5 + offset) ^ 0x10L;
	}

	/** Calls the Objective-C function pointer with sample arguments, returning the result as text. */
	static String invoke(long proc, Spec spec, int boolArgument, Arena arena) throws Throwable {
		String[] types = spec.types;
		MemoryLayout[] arguments = new MemoryLayout[types.length - 1];
		List<Object> values = new ArrayList<>();
		MemoryLayout returnLayout = types[0].equals("void") ? null : layoutOf(types[0]);
		if (returnLayout instanceof StructLayout) values.add(arena);
		for (int i = 1; i < types.length; i++) {
			arguments[i - 1] = layoutOf(types[i]);
			String type = types[i];
			if (structType(type)) {
				MemorySegment s = arena.allocate(arguments[i - 1]);
				for (int offset = 0; offset < s.byteSize(); offset += 8) {
					s.set(JAVA_LONG, offset, type.equals("NSRange") ? 7 + offset + i : Double.doubleToRawLongBits(1.25 * i + offset / 8.0 + 0.5));
				}
				values.add(s);
			} else if (type.equals("BOOL")) {
				values.add((byte) boolArgument);
			} else {
				values.add(i == 1 ? 0x1000L : i == 2 ? 0x2000L : type.equals("NSInteger") || type.equals("NSToolTipTag") ? -5L : 0x3000L + 16 * i);
			}
		}
		FunctionDescriptor descriptor = returnLayout == null ? FunctionDescriptor.ofVoid(arguments) : FunctionDescriptor.of(returnLayout, arguments);
		MethodHandle handle = LINKER.downcallHandle(MemorySegment.ofAddress(proc), descriptor);
		Object result = handle.invokeWithArguments(values);
		if (result == null) return "void";
		if (result instanceof MemorySegment s) {
			StringBuilder b = new StringBuilder();
			for (int offset = 0; offset < s.byteSize(); offset += 8) b.append(Long.toHexString(s.get(JAVA_LONG, offset))).append('/');
			return b.toString();
		}
		return Long.toHexString(((Number) result).longValue());
	}

	static void callbacks() throws Exception {
		List<Spec> specs = specs();
		check("CALLBACK_ natives found", 42, specs.size());
		Set<String> names = new HashSet<>();
		for (Method method : OS.class.getMethods()) {
			if (method.getName().startsWith("CALLBACK_") && Modifier.isNative(method.getModifiers())) names.add(method.getName());
		}
		check("CALLBACK_ natives of OS", names.size(), specs.size());
		int implemented = 0;
		for (Spec spec : specs) {
			try {
				FFMCocoaCallbacks.class.getMethod(spec.name(), long.class);
				implemented++;
			} catch (NoSuchMethodException e) {
				check("FFMCocoaCallbacks implements " + spec.name(), true, false);
			}
		}
		check("CALLBACK_ natives implemented", specs.size(), implemented);
		for (Spec spec : specs) {
			callback(spec);
		}
	}

	static void callback(Spec spec) throws Exception {
		int arguments = spec.types.length - 1;
		String method = "p" + arguments;
		Recorder jniRecorder = new Recorder(), ffmRecorder = new Recorder();
		jniRecorder.spec = ffmRecorder.spec = spec;
		Callback jniCallback = new Callback(jniRecorder, method, arguments);
		String signature = "(" + "J".repeat(arguments) + ")J";
		Object key = new Object();
		long ffmFunction = FFMCallback.bind(key, ffmRecorder, method, signature, arguments, false, false, 0);
		long jniProc = (long) OS.class.getMethod(spec.name(), long.class).invoke(null, jniCallback.getAddress());
		long ffmProc = FFMCocoaCallbacks.class.getMethod(spec.name(), long.class).invoke(null, ffmFunction) instanceof Long l ? l : 0;
		check(spec.name() + " proc non-zero", jniProc != 0, ffmProc != 0);
		check(spec.name() + " proc stable", ffmProc, FFMCocoaCallbacks.class.getMethod(spec.name(), long.class).invoke(null, ffmFunction));

		boolean hasBool = Arrays.asList(spec.types).subList(1, spec.types.length).contains("BOOL");
		boolean boolReturn = spec.types[0].equals("BOOL");
		int[] boolArguments = hasBool ? new int[] {1, 0} : new int[] {0};
		long[] scalarReturns = boolReturn ? new long[] {1, 0, 0x100, 0x1ff, -1} : new long[] {0x123456789abcdef1L, 0, -5};
		int[] returnModes = spec.structReturn() ? new int[] {0, 1} : new int[] {0};
		for (int boolArgument : boolArguments) {
			for (long scalar : scalarReturns) {
				for (int mode : returnModes) {
					jniRecorder.returnMode = ffmRecorder.returnMode = mode;
					jniRecorder.scalarReturn = ffmRecorder.scalarReturn = scalar;
					jniRecorder.log.clear();
					ffmRecorder.log.clear();
					String jniResult, ffmResult;
					try (Arena arena = Arena.ofConfined()) {
						try {
							jniResult = invoke(jniProc, spec, boolArgument, arena);
							ffmResult = invoke(ffmProc, spec, boolArgument, arena);
						} catch (Throwable e) {
							throw new RuntimeException(spec.name(), e);
						}
					}
					String what = spec.name() + " bool=" + boolArgument + " ret=" + Long.toHexString(scalar) + " mode=" + mode;
					check(what + " arguments", jniRecorder.log, ffmRecorder.log);
					check(what + " result", jniResult, ffmResult);
				}
			}
		}
		jniCallback.dispose();
		FFMCallback.unbind(key);
	}

	/** Callbacks with the argument counts SWT uses and beyond, so that arguments on the stack are covered too. */
	public static class Arity {
		static long sum(long... a) {
			long total = 17;
			for (int i = 0; i < a.length; i++) total = total * 31 + a[i] * (i + 1);
			return total;
		}

		public long a1(long a0) {
			return sum(a0);
		}

		public long a2(long a0, long a1) {
			return sum(a0, a1);
		}

		public long a3(long a0, long a1, long a2) {
			return sum(a0, a1, a2);
		}

		public long a4(long a0, long a1, long a2, long a3) {
			return sum(a0, a1, a2, a3);
		}

		public long a5(long a0, long a1, long a2, long a3, long a4) {
			return sum(a0, a1, a2, a3, a4);
		}

		public long a6(long a0, long a1, long a2, long a3, long a4, long a5) {
			return sum(a0, a1, a2, a3, a4, a5);
		}

		public long a7(long a0, long a1, long a2, long a3, long a4, long a5, long a6) {
			return sum(a0, a1, a2, a3, a4, a5, a6);
		}

		public long a8(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7) {
			return sum(a0, a1, a2, a3, a4, a5, a6, a7);
		}

		public long a9(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7, long a8) {
			return sum(a0, a1, a2, a3, a4, a5, a6, a7, a8);
		}

		public long a10(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7, long a8, long a9) {
			return sum(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9);
		}
	}

	static void arities() throws Throwable {
		Arity target = new Arity();
		for (int n = 1; n <= 10; n++) {
			Callback jni = new Callback(target, "a" + n, n);
			Object key = new Object();
			long ffm = FFMCallback.bind(key, target, "a" + n, "(" + "J".repeat(n) + ")J", n, false, false, 0);
			MemoryLayout[] layouts = new MemoryLayout[n];
			Arrays.fill(layouts, JAVA_LONG);
			FunctionDescriptor descriptor = FunctionDescriptor.of(JAVA_LONG, layouts);
			long[][] samples = {{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, {-1, Long.MIN_VALUE, Long.MAX_VALUE, 0, 0x8000000000L, -2, 3, -4, 5, -6}};
			for (long[] sample : samples) {
				List<Object> values = new ArrayList<>();
				for (int i = 0; i < n; i++) values.add(sample[i]);
				long fromJni = (long) LINKER.downcallHandle(MemorySegment.ofAddress(jni.getAddress()), descriptor).invokeWithArguments(values);
				long fromFfm = (long) LINKER.downcallHandle(MemorySegment.ofAddress(ffm), descriptor).invokeWithArguments(values);
				check("Callback with " + n + " arguments", fromJni, fromFfm);
			}
			jni.dispose();
			FFMCallback.unbind(key);
		}
	}

	public static void main(String[] args) throws Throwable {
		globalReferences();
		intersectionRect();
		displayBounds();
		classes();
		carbon();
		isFlipped();
		block();
		callbacks();
		arities();
		System.out.println(checks + " checks, " + failures + " mismatches");
		System.exit(failures == 0 ? 0 : 1);
	}
}
