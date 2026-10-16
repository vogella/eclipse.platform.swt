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
package org.eclipse.swt.tools.ffm;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cocoa.*;
import org.eclipse.swt.internal.ffm.*;

/**
 * Runs the JNI and the generated FFM implementation of the Cocoa natives in one process and compares the results.
 * Run against the build of test-cocoa-crosscheck.sh with the JNI libraries on <code>java.library.path</code> and
 * <code>-XstartOnFirstThread</code>. The optional argument is <code>report-cocoa/symbols.txt</code>, whose symbols
 * are resolved through the frameworks.
 */
public class FFMCrossCheckCocoa {

	static final String[] NATIVES = {"org.eclipse.swt.internal.C", "org.eclipse.swt.internal.cocoa.OS"};

	static int checks, failures;

	static void check(String what, Object jni, Object ffm) {
		checks++;
		if (deepEquals(jni, ffm)) return;
		failures++;
		System.out.println("MISMATCH " + what + "\n   JNI: " + describe(jni) + "\n   FFM: " + describe(ffm));
	}

	static void expect(String what, Object expected, Object actual) {
		check(what + " (expected)", expected, actual);
	}

	public static void main(String[] args) throws Exception {
		if (args.length > 0) checkSymbols(args[0]);
		checkLinking();
		checkSizes();
		checkStructs();
		long pool = OS.objc_msgSend(OS.objc_msgSend(cls("NSAutoreleasePool"), sel("alloc")), sel("init"));
		checkConstants();
		checkMessages();
		checkStructReturns();
		checkSuper();
		checkStrings();
		checkRuntime();
		checkDynamic();
		checkCoreGraphics();
		checkC();
		OS.objc_msgSend(pool, sel("release"));
		System.out.println(checks + " checks, " + failures + " mismatches");
		System.exit(failures == 0 ? 0 : 1);
	}

	/* ------------------------------------------------------------------ symbols and linking */

	/** Resolves every symbol the generator linked, in the framework its bundle names. */
	static void checkSymbols(String file) throws Exception {
		int count = 0, optional = 0;
		for (String line : Files.readAllLines(Paths.get(file))) {
			String[] parts = line.split("\t");
			if (parts.length != 2) continue;
			String[] kind = parts[1].split(" ");
			boolean dynamic = kind[0].equals("dynamic");
			SymbolLookup lookup = dynamic && !kind[1].equals("-") ? FFM.bundle(kind[1]) : null;
			boolean found;
			if (lookup != null) {
				found = lookup.find(parts[0]).isPresent();
			} else {
				try {
					FFM.address(parts[0]);
					found = true;
				} catch (UnsatisfiedLinkError e) {
					found = false;
				}
			}
			count++;
			checks++;
			if (!found && dynamic) {
				optional++;
			} else if (!found) {
				failures++;
				System.out.println("MISSING  " + parts[0] + " (" + parts[1] + ")");
			}
		}
		System.out.println("Resolved " + (count - optional) + " symbols, " + optional + " dynamic functions are not present on this system");
	}

	/** Initializes every generated holder, which links its downcall handle with the generated descriptor. */
	static void checkLinking() throws Exception {
		int count = 0, absent = 0;
		for (String natives : NATIVES) {
			Class<?> ffm = Class.forName(natives + "_FFM");
			for (Class<?> holder : ffm.getDeclaredClasses()) {
				if (!holder.getSimpleName().startsWith("MH_")) continue;
				checks++;
				count++;
				try {
					Field field = holder.getDeclaredField("MH");
					field.setAccessible(true);
					if (field.get(null) == null) absent++;
				} catch (Throwable e) {
					failures++;
					System.out.println("LINK     " + holder.getName() + ": " + (e.getCause() != null ? e.getCause() : e));
				}
			}
		}
		System.out.println("Linked " + count + " downcall handles, " + absent + " dynamic ones without a symbol");
	}

	/* ------------------------------------------------------------------ sizes */

	static void checkSizes() throws Exception {
		int count = 0;
		Class<?> structs = Class.forName("org.eclipse.swt.internal.cocoa.Structs_FFM");
		for (Field field : structs.getFields()) {
			if (!field.getName().endsWith("_SIZEOF")) continue;
			String name = field.getName().substring(0, field.getName().length() - "_SIZEOF".length());
			Method sizeof;
			try {
				sizeof = OS.class.getMethod(name + "_sizeof");
			} catch (NoSuchMethodException e) {
				continue;
			}
			check("sizeof " + name, (long) (Integer) sizeof.invoke(null), field.getLong(null));
			count++;
		}
		System.out.println("Compared " + count + " struct sizes");
	}

	/* ------------------------------------------------------------------ struct marshalling */

	/** Reads and writes random data through every JNI struct <code>memmove</code>, the generated one and the struct helpers. */
	static void checkStructs() throws Exception {
		int reads = 0, writes = 0;
		Class<?> structs = Class.forName("org.eclipse.swt.internal.cocoa.Structs_FFM");
		for (Method jni : OS.class.getDeclaredMethods()) {
			if (!jni.getName().equals("memmove") || !Modifier.isNative(jni.getModifiers())) continue;
			Class<?>[] types = jni.getParameterTypes();
			if (types.length != 3) continue;
			boolean read = !types[0].isPrimitive() && !types[0].isArray() && types[1] == long.class;
			boolean write = types[0] == long.class && !types[1].isPrimitive() && !types[1].isArray();
			if (!read && !write) continue;
			Method generated = OS_FFM.class.getMethod(jni.getName(), types);
			Class<?> struct = read ? types[0] : types[1];
			long size = structs.getField(struct.getSimpleName() + "_SIZEOF").getLong(null);
			Method ffmRead = structs.getMethod(struct.getSimpleName() + "_read", MemorySegment.class, struct);
			Random random = new Random(struct.getName().hashCode());
			for (int round = 0; round < 20; round++) {
				long buffer = C.malloc(size), other = C.malloc(size);
				try {
					if (read) {
						byte[] bytes = new byte[(int) size];
						random.nextBytes(bytes);
						C.memmove(buffer, bytes, size);
						Object viaJni = struct.getConstructor().newInstance();
						jni.invoke(null, viaJni, buffer, size);
						Object viaFfm = struct.getConstructor().newInstance();
						generated.invoke(null, viaFfm, buffer, size);
						check("read " + struct.getSimpleName() + " round " + round, viaJni, viaFfm);
						Object viaHelper = struct.getConstructor().newInstance();
						ffmRead.invoke(null, FFM.segment(buffer, size), viaHelper);
						check("read helper " + struct.getSimpleName() + " round " + round, viaJni, viaHelper);
						reads++;
					} else {
						Object value = randomStruct(struct, random);
						C.memset(buffer, 0, size);
						C.memset(other, 0, size);
						jni.invoke(null, buffer, value, size);
						generated.invoke(null, other, value, size);
						// compared as read back, since JNI copies the padding of an uninitialized C struct
						Object fromJni = struct.getConstructor().newInstance(), fromFfm = struct.getConstructor().newInstance();
						ffmRead.invoke(null, FFM.segment(buffer, size), fromJni);
						ffmRead.invoke(null, FFM.segment(other, size), fromFfm);
						check("write " + struct.getSimpleName() + " round " + round, fromJni, fromFfm);
						writes++;
					}
				} finally {
					C.free(buffer);
					C.free(other);
				}
			}
		}
		System.out.println("Compared " + reads + " struct reads and " + writes + " struct writes");
		// the structs without a memmove go through the calls below; their write and read must at least be inverse
		int roundTrips = 0;
		for (Field field : structs.getFields()) {
			if (!field.getName().endsWith("_SIZEOF")) continue;
			String name = field.getName().substring(0, field.getName().length() - "_SIZEOF".length());
			Class<?> struct = Class.forName("org.eclipse.swt.internal.cocoa." + name);
			long size = field.getLong(null);
			Method ffmRead = structs.getMethod(name + "_read", MemorySegment.class, struct);
			Method ffmWrite = structs.getMethod(name + "_write", MemorySegment.class, struct);
			Random random = new Random(name.hashCode());
			for (int round = 0; round < 10; round++) {
				Object value = randomStruct(struct, random), back = struct.getConstructor().newInstance();
				try (Arena arena = Arena.ofConfined()) {
					MemorySegment segment = arena.allocate(size, 16);
					ffmWrite.invoke(null, segment, value);
					ffmRead.invoke(null, segment, back);
				}
				check("round trip " + name, value, back);
				roundTrips++;
			}
		}
		System.out.println("Round-tripped " + roundTrips + " structs through the helpers");
	}

	static Object randomStruct(Class<?> type, Random random) throws Exception {
		Object value = type.getConstructor().newInstance();
		for (Field field : publicFields(type)) {
			Class<?> t = field.getType();
			if (t == int.class) field.setInt(value, random.nextInt());
			else if (t == long.class) field.setLong(value, random.nextLong());
			else if (t == short.class) field.setShort(value, (short) random.nextInt());
			else if (t == byte.class) field.setByte(value, (byte) random.nextInt());
			else if (t == char.class) field.setChar(value, (char) random.nextInt());
			else if (t == boolean.class) field.setBoolean(value, random.nextBoolean());
			else if (t == double.class) field.setDouble(value, random.nextDouble() * 1e6 - 5e5);
			else if (t == float.class) field.setFloat(value, random.nextFloat() * 1000 - 500);
			else field.set(value, randomStruct(t, random));
		}
		return value;
	}

	static List<Field> publicFields(Class<?> type) {
		List<Field> fields = new ArrayList<>();
		for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				int mods = field.getModifiers();
				if (Modifier.isPublic(mods) && !Modifier.isStatic(mods) && !Modifier.isFinal(mods)) fields.add(field);
			}
		}
		return fields;
	}

	static boolean deepEquals(Object a, Object b) {
		if (a == null || b == null) return a == b;
		if (a.getClass().isArray()) return Objects.deepEquals(a, b);
		if (a instanceof Number || a instanceof Boolean || a instanceof Character || a instanceof String) return a.equals(b);
		if (a.getClass() != b.getClass()) return false;
		try {
			for (Field field : publicFields(a.getClass())) {
				if (!deepEquals(field.get(a), field.get(b))) return false;
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return true;
	}

	static String describe(Object value) {
		if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) return String.valueOf(value);
		if (value.getClass().isArray()) return Arrays.deepToString(new Object[] {value});
		StringBuilder b = new StringBuilder(value.getClass().getSimpleName()).append(" {");
		try {
			for (Field field : publicFields(value.getClass())) b.append(' ').append(field.getName()).append('=').append(describe(field.get(value)));
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return b.append(" }").toString();
	}

	/* ------------------------------------------------------------------ helpers */

	static long cls(String name) {
		return OS.objc_getClass(name);
	}

	static long sel(String name) {
		return OS.sel_registerName(name);
	}

	static NSRect rect(double x, double y, double w, double h) {
		NSRect r = new NSRect();
		r.x = x;
		r.y = y;
		r.width = w;
		r.height = h;
		return r;
	}

	static NSPoint point(double x, double y) {
		NSPoint p = new NSPoint();
		p.x = x;
		p.y = y;
		return p;
	}

	static NSSize size(double w, double h) {
		NSSize s = new NSSize();
		s.width = w;
		s.height = h;
		return s;
	}

	static NSRange range(long location, long length) {
		NSRange r = new NSRange();
		r.location = location;
		r.length = length;
		return r;
	}

	static byte[] utf8(String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		return Arrays.copyOf(bytes, bytes.length + 1);
	}

	/** The Java string behind a C string, read with the JNI and the FFM <code>C</code> alike. */
	static String cString(long address) {
		int length = C.strlen(address);
		check("strlen", length, C_FFM.strlen(address));
		byte[] bytes = new byte[length], ffm = new byte[length];
		C.memmove(bytes, address, length);
		C_FFM.memmove(ffm, address, length);
		check("memmove bytes", bytes, ffm);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	/* ------------------------------------------------------------------ constants */

	/** Every exported constant and every macro constant of OS, which the generator reads from the symbol or the probe. */
	static void checkConstants() throws Exception {
		int count = 0;
		Pattern names = Pattern.compile("(NS|kCF|kUT)\\w+");
		for (Method jni : OS.class.getDeclaredMethods()) {
			if (!Modifier.isNative(jni.getModifiers()) || jni.getParameterCount() != 0 || !names.matcher(jni.getName()).matches()) continue;
			if (jni.getReturnType() != long.class && jni.getReturnType() != double.class) continue;
			Method ffm;
			try {
				ffm = OS_FFM.class.getMethod(jni.getName());
			} catch (NoSuchMethodException e) {
				continue;
			}
			check("constant " + jni.getName(), jni.invoke(null), ffm.invoke(null));
			count++;
		}
		System.out.println("Compared " + count + " constants");
		expect("NSNotFound", Long.MAX_VALUE, OS_FFM.NSNotFound());
		expect("NSSquareStatusItemLength", -2.0, OS_FFM.NSSquareStatusItemLength());
	}

	/* ------------------------------------------------------------------ messages */

	static void checkMessages() {
		int start = checks;
		check("objc_getClass", OS.objc_getClass("NSString"), OS_FFM.objc_getClass("NSString"));
		check("objc_getClass missing", OS.objc_getClass("NoSuchClassFFM"), OS_FFM.objc_getClass("NoSuchClassFFM"));
		check("objc_lookUpClass", OS.objc_lookUpClass("NSArray"), OS_FFM.objc_lookUpClass("NSArray"));
		check("objc_getMetaClass", OS.objc_getMetaClass("NSString"), OS_FFM.objc_getMetaClass("NSString"));
		check("objc_getProtocol", OS.objc_getProtocol("NSCopying"), OS_FFM.objc_getProtocol("NSCopying"));
		check("sel_registerName", OS.sel_registerName("stringWithUTF8String:"), OS_FFM.sel_registerName("stringWithUTF8String:"));
		long number = cls("NSNumber");
		// integer, boolean, double and pointer parameters
		long jniNumber = OS.objc_msgSend(number, sel("numberWithDouble:"), 1234.5678);
		long ffmNumber = OS_FFM.objc_msgSend(number, sel("numberWithDouble:"), 1234.5678);
		check("numberWithDouble doubleValue", OS.objc_msgSend_fpret(jniNumber, sel("doubleValue")), OS_FFM.objc_msgSend_fpret(ffmNumber, sel("doubleValue")));
		check("numberWithDouble crossed", OS.objc_msgSend_fpret(ffmNumber, sel("doubleValue")), OS_FFM.objc_msgSend_fpret(jniNumber, sel("doubleValue")));
		check("floatValue", OS.objc_msgSend_floatret(jniNumber, sel("floatValue")), OS_FFM.objc_msgSend_floatret(ffmNumber, sel("floatValue")));
		check("intValue", OS.objc_msgSend(jniNumber, sel("intValue")), OS_FFM.objc_msgSend(ffmNumber, sel("intValue")));
		long jniBool = OS.objc_msgSend(number, sel("numberWithBool:"), true);
		long ffmBool = OS_FFM.objc_msgSend(number, sel("numberWithBool:"), true);
		check("numberWithBool boolValue", OS.objc_msgSend_bool(jniBool, sel("boolValue")) , OS_FFM.objc_msgSend_bool(ffmBool, sel("boolValue")));
		check("numberWithBool false", OS.objc_msgSend_bool(OS.objc_msgSend(number, sel("numberWithBool:"), false), sel("boolValue")),
			OS_FFM.objc_msgSend_bool(OS_FFM.objc_msgSend(number, sel("numberWithBool:"), false), sel("boolValue")));
		long jniInt = OS.objc_msgSend(number, sel("numberWithInt:"), -42);
		long ffmInt = OS_FFM.objc_msgSend(number, sel("numberWithInt:"), -42);
		check("numberWithInt", OS.objc_msgSend(jniInt, sel("intValue")), OS_FFM.objc_msgSend(ffmInt, sel("intValue")));
		check("isEqualToNumber", OS.objc_msgSend_bool(jniInt, sel("isEqualToNumber:"), ffmInt), OS_FFM.objc_msgSend_bool(ffmInt, sel("isEqualToNumber:"), jniInt));
		// arrays of pointers
		long[] items = {jniInt, ffmInt, jniNumber};
		long jniArray = OS.objc_msgSend(OS.objc_msgSend(cls("NSMutableArray"), sel("alloc")), sel("init"));
		long ffmArray = OS_FFM.objc_msgSend(OS_FFM.objc_msgSend(cls("NSMutableArray"), sel("alloc")), sel("init"));
		for (long item : items) {
			OS.objc_msgSend(jniArray, sel("addObject:"), item);
			OS_FFM.objc_msgSend(ffmArray, sel("addObject:"), item);
		}
		check("count", OS.objc_msgSend(jniArray, sel("count")), OS_FFM.objc_msgSend(ffmArray, sel("count")));
		check("objectAtIndex", OS.objc_msgSend(jniArray, sel("objectAtIndex:"), 2), OS_FFM.objc_msgSend(ffmArray, sel("objectAtIndex:"), 2));
		check("indexOfObject", OS.objc_msgSend(jniArray, sel("indexOfObject:"), jniNumber), OS_FFM.objc_msgSend(ffmArray, sel("indexOfObject:"), jniNumber));
		OS.objc_msgSend(jniArray, sel("release"));
		OS_FFM.objc_msgSend(ffmArray, sel("release"));
		check("function pointer call", callBoth(), true);
		System.out.println("Compared " + (checks - start) + " message sends");
	}

	/** call(proc, id, sel) on a harmless C function, through JNI and FFM. */
	static boolean callBoth() {
		long proc = FFM.address("getpid");
		OS.call(proc, 0, 0);
		OS_FFM.call(proc, 0, 0);
		check("getpid", OS.getpid(), OS_FFM.getpid());
		return true;
	}

	/* ------------------------------------------------------------------ struct parameters and results */

	static void checkStructReturns() {
		int start = checks;
		long value = cls("NSValue");
		NSRect r = rect(1.5, -2.5, 300.25, 400.75);
		long jniRect = OS.objc_msgSend(value, sel("valueWithRect:"), r);
		long ffmRect = OS_FFM.objc_msgSend(value, sel("valueWithRect:"), r);
		NSRect jniBack = new NSRect(), ffmBack = new NSRect(), crossed = new NSRect(), crossed2 = new NSRect();
		OS.objc_msgSend_stret(jniBack, jniRect, sel("rectValue"));
		OS_FFM.objc_msgSend_stret(ffmBack, ffmRect, sel("rectValue"));
		OS_FFM.objc_msgSend_stret(crossed, jniRect, sel("rectValue"));
		OS.objc_msgSend_stret(crossed2, ffmRect, sel("rectValue"));
		expect("NSRect JNI round trip", r, jniBack);
		expect("NSRect FFM round trip", r, ffmBack);
		check("NSRect FFM reads JNI value", jniBack, crossed);
		check("NSRect JNI reads FFM value", ffmBack, crossed2);
		check("NSRect null result", true, nullResult(jniRect, ffmRect));

		NSPoint p = point(-7.25, 8.5);
		long jniPoint = OS.objc_msgSend(value, sel("valueWithPoint:"), p);
		long ffmPoint = OS_FFM.objc_msgSend(value, sel("valueWithPoint:"), p);
		NSPoint jp = new NSPoint(), fp = new NSPoint();
		OS.objc_msgSend_stret(jp, jniPoint, sel("pointValue"));
		OS_FFM.objc_msgSend_stret(fp, ffmPoint, sel("pointValue"));
		expect("NSPoint", p, fp);
		check("NSPoint", jp, fp);

		NSSize s = size(640.5, 480.25);
		long jniSize = OS.objc_msgSend(value, sel("valueWithSize:"), s);
		long ffmSize = OS_FFM.objc_msgSend(value, sel("valueWithSize:"), s);
		NSSize js = new NSSize(), fs = new NSSize();
		OS.objc_msgSend_stret(js, jniSize, sel("sizeValue"));
		OS_FFM.objc_msgSend_stret(fs, ffmSize, sel("sizeValue"));
		expect("NSSize", s, fs);
		check("NSSize", js, fs);

		NSRange g = range(12, 345);
		long jniRange = OS.objc_msgSend(value, sel("valueWithRange:"), g);
		long ffmRange = OS_FFM.objc_msgSend(value, sel("valueWithRange:"), g);
		NSRange jg = new NSRange(), fg = new NSRange();
		OS.objc_msgSend_stret(jg, jniRange, sel("rangeValue"));
		OS_FFM.objc_msgSend_stret(fg, ffmRange, sel("rangeValue"));
		expect("NSRange", g, fg);
		check("NSRange", jg, fg);

		// 48 bytes, returned through memory
		NSAffineTransformStruct t = new NSAffineTransformStruct();
		t.m11 = 1.5;
		t.m12 = 2.5;
		t.m21 = -3.5;
		t.m22 = 4.5;
		t.tX = 100.25;
		t.tY = -200.75;
		long transform = cls("NSAffineTransform");
		long jniTransform = OS.objc_msgSend(OS.objc_msgSend(transform, sel("alloc")), sel("init"));
		long ffmTransform = OS_FFM.objc_msgSend(OS_FFM.objc_msgSend(transform, sel("alloc")), sel("init"));
		OS.objc_msgSend(jniTransform, sel("setTransformStruct:"), t);
		OS_FFM.objc_msgSend(ffmTransform, sel("setTransformStruct:"), t);
		NSAffineTransformStruct jt = new NSAffineTransformStruct(), ft = new NSAffineTransformStruct();
		OS.objc_msgSend_stret(jt, jniTransform, sel("transformStruct"));
		OS_FFM.objc_msgSend_stret(ft, ffmTransform, sel("transformStruct"));
		expect("NSAffineTransformStruct", t, ft);
		check("NSAffineTransformStruct", jt, ft);
		// the transform applied to a point: struct parameter and struct result in one call
		NSPoint jniApplied = new NSPoint(), ffmApplied = new NSPoint();
		OS.objc_msgSend_stret(jniApplied, jniTransform, sel("transformPoint:"), p);
		OS_FFM.objc_msgSend_stret(ffmApplied, ffmTransform, sel("transformPoint:"), p);
		check("transformPoint:", jniApplied, ffmApplied);
		NSSize jniAppliedSize = new NSSize(), ffmAppliedSize = new NSSize();
		OS.objc_msgSend_stret(jniAppliedSize, jniTransform, sel("transformSize:"), s);
		OS_FFM.objc_msgSend_stret(ffmAppliedSize, ffmTransform, sel("transformSize:"), s);
		check("transformSize:", jniAppliedSize, ffmAppliedSize);
		OS.objc_msgSend(jniTransform, sel("release"));
		OS_FFM.objc_msgSend(ffmTransform, sel("release"));

		// 24 bytes
		long info = OS.objc_msgSend(cls("NSProcessInfo"), sel("processInfo"));
		NSOperatingSystemVersion jv = new NSOperatingSystemVersion(), fv = new NSOperatingSystemVersion();
		OS.objc_msgSend_stret(jv, info, sel("operatingSystemVersion"));
		OS_FFM.objc_msgSend_stret(fv, info, sel("operatingSystemVersion"));
		check("operatingSystemVersion", jv, fv);
		check("operatingSystemVersion major", true, fv.majorVersion >= 10);

		// struct parameters of the bool variants, and the geometry functions
		check("objc_msgSend_bool NSRect", OS.objc_msgSend_bool(jniRect, sel("isEqualToValue:"), jniRect) , OS_FFM.objc_msgSend_bool(ffmRect, sel("isEqualToValue:"), ffmRect));
		NSRect a = rect(0, 0, 10, 10), b = rect(5, 5, 10, 10);
		check("NSEqualRects", OS.NSEqualRects(a, a), OS_FFM.NSEqualRects(a, a));
		check("NSEqualRects different", OS.NSEqualRects(a, b), OS_FFM.NSEqualRects(a, b));
		check("NSPointInRect", OS.NSPointInRect(point(6, 6), b), OS_FFM.NSPointInRect(point(6, 6), b));
		check("NSPointInRect outside", OS.NSPointInRect(point(1, 1), b), OS_FFM.NSPointInRect(point(1, 1), b));
		check("NSBitsPerPixelFromDepth", OS.NSBitsPerPixelFromDepth(0x0210), OS_FFM.NSBitsPerPixelFromDepth(0x0210));
		check("NSSearchPathForDirectoriesInDomains", OS.NSSearchPathForDirectoriesInDomains(5, 1, true) != 0, OS_FFM.NSSearchPathForDirectoriesInDomains(5, 1, true) != 0);
		System.out.println("Compared " + (checks - start) + " struct results");
	}

	static boolean nullResult(long jniValue, long ffmValue) {
		// JNI dereferences a null result struct; the generated code skips the copy
		try {
			OS_FFM.objc_msgSend_stret((NSRect) null, ffmValue, sel("rectValue"));
		} catch (Throwable e) {
			return false;
		}
		return true;
	}

	/* ------------------------------------------------------------------ messages to the super class */

	static void checkSuper() {
		int start = checks;
		long value = cls("NSValue");
		NSRect r = rect(3, 4, 5, 6);
		long object = OS.objc_msgSend(value, sel("valueWithRect:"), r);
		objc_super jniSuper = new objc_super(), ffmSuper = new objc_super();
		jniSuper.receiver = ffmSuper.receiver = object;
		jniSuper.super_class = ffmSuper.super_class = cls("NSObject");
		check("objc_msgSendSuper hash", OS.objc_msgSendSuper(jniSuper, sel("hash")), OS_FFM.objc_msgSendSuper(ffmSuper, sel("hash")));
		check("objc_msgSendSuper isEqual", OS.objc_msgSendSuper(jniSuper, sel("isEqual:"), object), OS_FFM.objc_msgSendSuper(ffmSuper, sel("isEqual:"), object));
		check("objc_super unchanged", jniSuper, ffmSuper);
		jniSuper.super_class = ffmSuper.super_class = value;
		NSRect jniRect = new NSRect(), ffmRect = new NSRect();
		OS.objc_msgSendSuper_stret(jniRect, jniSuper, sel("rectValue"), 0) ;
		OS_FFM.objc_msgSendSuper_stret(ffmRect, ffmSuper, sel("rectValue"), 0);
		expect("objc_msgSendSuper_stret NSRect", r, ffmRect);
		check("objc_msgSendSuper_stret NSRect", jniRect, ffmRect);
		NSSize jniSize = new NSSize(), ffmSize = new NSSize();
		long sizeValue = OS.objc_msgSend(value, sel("valueWithSize:"), size(9, 8));
		jniSuper.receiver = ffmSuper.receiver = sizeValue;
		OS.objc_msgSendSuper_stret(jniSize, jniSuper, sel("sizeValue"));
		OS_FFM.objc_msgSendSuper_stret(ffmSize, ffmSuper, sel("sizeValue"));
		expect("objc_msgSendSuper_stret NSSize", size(9, 8), ffmSize);
		check("objc_msgSendSuper_stret NSSize", jniSize, ffmSize);
		check("objc_super size", OS.objc_super_sizeof(), OS_FFM.objc_super_sizeof());
		System.out.println("Compared " + (checks - start) + " super sends");
	}

	/* ------------------------------------------------------------------ strings */

	static void checkStrings() {
		int start = checks;
		long string = cls("NSString");
		String text = "Grüße € 😀 äöü";
		byte[] bytes = utf8(text);
		long jniString = OS.objc_msgSend(string, sel("stringWithUTF8String:"), bytes);
		long ffmString = OS_FFM.objc_msgSend(string, sel("stringWithUTF8String:"), bytes);
		check("UTF8String JNI", text, cString(OS.objc_msgSend(jniString, sel("UTF8String"))));
		check("UTF8String FFM", text, cString(OS_FFM.objc_msgSend(ffmString, sel("UTF8String"))));
		check("UTF8String crossed", cString(OS.objc_msgSend(ffmString, sel("UTF8String"))), cString(OS_FFM.objc_msgSend(jniString, sel("UTF8String"))));
		check("isEqualToString", OS.objc_msgSend_bool(jniString, sel("isEqualToString:"), ffmString), OS_FFM.objc_msgSend_bool(ffmString, sel("isEqualToString:"), jniString));
		check("length", OS.objc_msgSend(jniString, sel("length")), OS_FFM.objc_msgSend(ffmString, sel("length")));
		char[] chars = text.toCharArray();
		long jniChars = OS.objc_msgSend(string, sel("stringWithCharacters:length:"), chars, chars.length);
		long ffmChars = OS_FFM.objc_msgSend(string, sel("stringWithCharacters:length:"), chars, chars.length);
		check("stringWithCharacters", OS.objc_msgSend(jniChars, sel("length")), OS_FFM.objc_msgSend(ffmChars, sel("length")));
		char[] jniOut = new char[chars.length], ffmOut = new char[chars.length];
		OS.objc_msgSend(jniChars, sel("getCharacters:range:"), jniOut, range(0, chars.length));
		OS_FFM.objc_msgSend(ffmChars, sel("getCharacters:range:"), ffmOut, range(0, chars.length));
		check("getCharacters:range:", jniOut, ffmOut);
		expect("getCharacters:range:", chars, ffmOut);
		long jniSub = OS.objc_msgSend(jniString, sel("substringWithRange:"), range(2, 4));
		long ffmSub = OS_FFM.objc_msgSend(ffmString, sel("substringWithRange:"), range(2, 4));
		check("substringWithRange:", cString(OS.objc_msgSend(jniSub, sel("UTF8String"))), cString(OS_FFM.objc_msgSend(ffmSub, sel("UTF8String"))));
		check("substringWithRange: text", "üße ", cString(OS.objc_msgSend(ffmSub, sel("UTF8String"))));
		NSRange found = new NSRange(), foundFfm = new NSRange();
		long needle = OS.objc_msgSend(string, sel("stringWithUTF8String:"), utf8("€"));
		OS.objc_msgSend_stret(found, jniString, sel("rangeOfString:"), needle);
		OS_FFM.objc_msgSend_stret(foundFfm, ffmString, sel("rangeOfString:"), needle);
		check("rangeOfString:", found, foundFfm);
		System.out.println("Compared " + (checks - start) + " string calls");
	}

	/* ------------------------------------------------------------------ objc runtime */

	static void checkRuntime() {
		int start = checks;
		long object = cls("NSObject");
		check("class_getSuperclass", OS.class_getSuperclass(cls("NSString")), OS_FFM.class_getSuperclass(cls("NSString")));
		check("object_getClass", OS.object_getClass(object), OS_FFM.object_getClass(object));
		check("object_getClassName", cString(OS.object_getClassName(object)), cString(OS_FFM.object_getClassName(object)));
		check("class_getInstanceMethod", OS.class_getInstanceMethod(object, sel("description")), OS_FFM.class_getInstanceMethod(object, sel("description")));
		check("class_getClassMethod", OS.class_getClassMethod(object, sel("alloc")), OS_FFM.class_getClassMethod(object, sel("alloc")));
		check("class_getMethodImplementation", OS.class_getMethodImplementation(object, sel("description")), OS_FFM.class_getMethodImplementation(object, sel("description")));
		// a class pair per implementation: ivar, method, instance and ivar access
		long proc = FFM.address("getpid");
		long[] jniOut = new long[1], ffmOut = new long[1];
		long jniClass = OS.objc_allocateClassPair(object, "FFMCrossCheckJni", 0);
		long ffmClass = OS_FFM.objc_allocateClassPair(object, "FFMCrossCheckFfm", 0);
		check("objc_allocateClassPair", jniClass != 0, ffmClass != 0);
		check("class_addIvar", OS.class_addIvar(jniClass, utf8("value"), 8, (byte) 3, utf8("q")), OS_FFM.class_addIvar(ffmClass, utf8("value"), 8, (byte) 3, utf8("q")));
		check("class_addMethod", OS.class_addMethod(jniClass, sel("ffmCheck"), proc, "v@:"), OS_FFM.class_addMethod(ffmClass, sel("ffmCheck"), proc, "v@:"));
		check("class_addProtocol", OS.class_addProtocol(jniClass, OS.objc_getProtocol("NSCopying")), OS_FFM.class_addProtocol(ffmClass, OS.objc_getProtocol("NSCopying")));
		OS.objc_registerClassPair(jniClass);
		OS_FFM.objc_registerClassPair(ffmClass);
		long jniInstance = OS.objc_msgSend(OS.objc_msgSend(jniClass, sel("alloc")), sel("init"));
		long ffmInstance = OS_FFM.objc_msgSend(OS_FFM.objc_msgSend(ffmClass, sel("alloc")), sel("init"));
		check("instance", jniInstance != 0, ffmInstance != 0);
		check("class_getInstanceMethod ours", OS.class_getInstanceMethod(jniClass, sel("ffmCheck")) != 0, OS_FFM.class_getInstanceMethod(ffmClass, sel("ffmCheck")) != 0);
		check("class_getMethodImplementation ours", OS.class_getMethodImplementation(jniClass, sel("ffmCheck")), OS_FFM.class_getMethodImplementation(ffmClass, sel("ffmCheck")));
		OS.object_setInstanceVariable(jniInstance, utf8("value"), 0x1122334455667788L);
		OS_FFM.object_setInstanceVariable(ffmInstance, utf8("value"), 0x1122334455667788L);
		OS.object_getInstanceVariable(jniInstance, utf8("value"), jniOut);
		OS_FFM.object_getInstanceVariable(ffmInstance, utf8("value"), ffmOut);
		check("object_getInstanceVariable", jniOut, ffmOut);
		expect("object_getInstanceVariable", 0x1122334455667788L, ffmOut[0]);
		check("object_setClass", OS.object_setClass(jniInstance, jniClass), OS_FFM.object_setClass(ffmInstance, ffmClass) - ffmClass + jniClass);
		OS.objc_msgSend(jniInstance, sel("release"));
		OS_FFM.objc_msgSend(ffmInstance, sel("release"));
		System.out.println("Compared " + (checks - start) + " runtime calls");
	}

	/* ------------------------------------------------------------------ dynamic functions */

	static void checkDynamic() {
		int start = checks;
		check("LMGetKbdType", OS.LMGetKbdType(), OS_FFM.LMGetKbdType());
		int[] jniMode = new int[1], ffmMode = new int[1], jniOptions = new int[1], ffmOptions = new int[1];
		jniMode[0] = ffmMode[0] = 77;
		check("GetSystemUIMode", OS.GetSystemUIMode(jniMode, jniOptions), OS_FFM.GetSystemUIMode(ffmMode, ffmOptions));
		check("GetSystemUIMode out", jniMode, ffmMode);
		check("GetSystemUIMode options", jniOptions, ffmOptions);
		long jniSource = OS.TISCopyCurrentKeyboardInputSource(), ffmSource = OS_FFM.TISCopyCurrentKeyboardInputSource();
		check("TISCopyCurrentKeyboardInputSource", jniSource != 0, ffmSource != 0);
		long key = OS.kTISPropertyUnicodeKeyLayoutData();
		check("TISGetInputSourceProperty", OS.TISGetInputSourceProperty(jniSource, key) != 0, OS_FFM.TISGetInputSourceProperty(ffmSource, key) != 0);
		check("AcquireRootMenu", OS.AcquireRootMenu(), OS_FFM.AcquireRootMenu());
		// QuickDraw regions, which only exist as long as Carbon keeps them
		check("NewRgn", OS.NewRgn() != 0, OS_FFM.NewRgn() != 0);
		check("PtInRgn", OS.PtInRgn(new short[] {1, 2}, 0), OS_FFM.PtInRgn(new short[] {1, 2}, 0));
		check("GetDblTime", OS.getpid(), OS_FFM.getpid());
		int[] jniMetric = {5}, ffmMetric = {5};
		OS.GetThemeMetric(OS.kThemeMetricFocusRectOutset, jniMetric);
		OS_FFM.GetThemeMetric(OS.kThemeMetricFocusRectOutset, ffmMetric);
		check("GetThemeMetric", jniMetric, ffmMetric);
		check("CGDisplayCreateImage", OS.CGDisplayCreateImage(0) != 0, OS_FFM.CGDisplayCreateImage(0) != 0);
		short[] jniRect = {1, 2, 3, 4}, ffmRect = {1, 2, 3, 4};
		check("RectInRgn", OS.RectInRgn(jniRect, 0), OS_FFM.RectInRgn(ffmRect, 0));
		check("UTTypeEqual", OS.UTTypeEqual(OS.kUTTypeURL(), OS.kUTTypeURL()), OS_FFM.UTTypeEqual(OS_FFM.kUTTypeURL(), OS_FFM.kUTTypeURL()));
		check("UTTypeEqual different", OS.UTTypeEqual(OS.kUTTypeURL(), OS.kUTTypeFileURL()), OS_FFM.UTTypeEqual(OS_FFM.kUTTypeURL(), OS_FFM.kUTTypeFileURL()));
		System.out.println("Compared " + (checks - start) + " dynamic and Carbon calls");
	}

	/* ------------------------------------------------------------------ Core Graphics and Core Foundation */

	/** Fills a rect in a bitmap context through each implementation and compares the pixels. */
	static void checkCoreGraphics() {
		int start = checks;
		byte[][] pixels = new byte[2][];
		for (int implementation = 0; implementation < 2; implementation++) {
			boolean ffm = implementation == 1;
			int width = 16, height = 12, stride = width * 4;
			long data = C.malloc(stride * height);
			C.memset(data, 0, stride * height);
			long space = ffm ? OS_FFM.CGColorSpaceCreateDeviceRGB() : OS.CGColorSpaceCreateDeviceRGB();
			long context = ffm ? OS_FFM.CGBitmapContextCreate(data, width, height, 8, stride, space, 1) : OS.CGBitmapContextCreate(data, width, height, 8, stride, space, 1);
			check("CGBitmapContextCreate", true, context != 0);
			double[] components = {0.25, 0.5, 0.75, 1};
			CGRect fill = new CGRect();
			fill.origin.x = 2;
			fill.origin.y = 3;
			fill.size.width = 8;
			fill.size.height = 5;
			if (ffm) {
				OS_FFM.CGContextSetFillColorSpace(context, space);
				OS_FFM.CGContextSetFillColor(context, components);
				OS_FFM.CGContextSaveGState(context);
				OS_FFM.CGContextTranslateCTM(context, 1, 1);
				OS_FFM.CGContextScaleCTM(context, 1, 1);
				OS_FFM.CGContextFillRect(context, fill);
				OS_FFM.CGContextRestoreGState(context);
				OS_FFM.CGContextSetLineWidth(context, 2);
				OS_FFM.CGContextSetLineCap(context, 1);
				OS_FFM.CGContextSetLineJoin(context, 1);
				OS_FFM.CGContextSetMiterLimit(context, 5);
				OS_FFM.CGContextSetLineDash(context, 0, new double[] {2, 1}, 2);
				long path = OS_FFM.CGPathCreateMutable();
				OS_FFM.CGPathMoveToPoint(path, 0, 0.5, 0.5);
				OS_FFM.CGPathAddLineToPoint(path, 0, 15, 11);
				OS_FFM.CGPathAddCurveToPoint(path, 0, 2, 3, 4, 5, 6, 7);
				OS_FFM.CGPathCloseSubpath(path);
				OS_FFM.CGContextAddPath(context, path);
				OS_FFM.CGContextSetBlendMode(context, 0);
				OS_FFM.CGContextStrokePath(context);
				OS_FFM.CGPathRelease(path);
				OS_FFM.CGContextRelease(context);
				OS_FFM.CGColorSpaceRelease(space);
			} else {
				OS.CGContextSetFillColorSpace(context, space);
				OS.CGContextSetFillColor(context, components);
				OS.CGContextSaveGState(context);
				OS.CGContextTranslateCTM(context, 1, 1);
				OS.CGContextScaleCTM(context, 1, 1);
				OS.CGContextFillRect(context, fill);
				OS.CGContextRestoreGState(context);
				OS.CGContextSetLineWidth(context, 2);
				OS.CGContextSetLineCap(context, 1);
				OS.CGContextSetLineJoin(context, 1);
				OS.CGContextSetMiterLimit(context, 5);
				OS.CGContextSetLineDash(context, 0, new double[] {2, 1}, 2);
				long path = OS.CGPathCreateMutable();
				OS.CGPathMoveToPoint(path, 0, 0.5, 0.5);
				OS.CGPathAddLineToPoint(path, 0, 15, 11);
				OS.CGPathAddCurveToPoint(path, 0, 2, 3, 4, 5, 6, 7);
				OS.CGPathCloseSubpath(path);
				OS.CGContextAddPath(context, path);
				OS.CGContextSetBlendMode(context, 0);
				OS.CGContextStrokePath(context);
				OS.CGPathRelease(path);
				OS.CGContextRelease(context);
				OS.CGColorSpaceRelease(space);
			}
			pixels[implementation] = new byte[stride * height];
			C.memmove(pixels[implementation], data, stride * height);
			C.free(data);
		}
		check("bitmap context pixels", pixels[0], pixels[1]);
		boolean drawn = false;
		for (byte b : pixels[1]) drawn |= b != 0;
		check("bitmap context drawn", true, drawn);
		// a struct parameter next to scalars, and a returned reference
		CGPoint position = new CGPoint();
		position.x = 10;
		position.y = 20;
		long jniEvent = OS.CGEventCreateMouseEvent(0, 5, position, 0), ffmEvent = OS_FFM.CGEventCreateMouseEvent(0, 5, position, 0);
		check("CGEventCreateMouseEvent", jniEvent != 0, ffmEvent != 0);
		check("CGEventGetIntegerValueField", OS.CGEventGetIntegerValueField(jniEvent, 1), OS_FFM.CGEventGetIntegerValueField(ffmEvent, 1));
		OS.CFRelease(jniEvent);
		OS_FFM.CFRelease(ffmEvent);
		CGRect everything = new CGRect();
		everything.size.width = everything.size.height = 1e6;
		long jniDisplays = C.malloc(64), ffmDisplays = C.malloc(64), jniCount = C.malloc(8), ffmCount = C.malloc(8);
		C.memset(jniCount, 0, 8);
		C.memset(ffmCount, 0, 8);
		check("CGGetDisplaysWithRect", OS.CGGetDisplaysWithRect(everything, 16, jniDisplays, jniCount), OS_FFM.CGGetDisplaysWithRect(everything, 16, ffmDisplays, ffmCount));
		int[] jniN = new int[1], ffmN = new int[1], jniIds = new int[16], ffmIds = new int[16];
		C.memmove(jniN, jniCount, 4);
		C.memmove(ffmN, ffmCount, 4);
		C.memmove(jniIds, jniDisplays, 64);
		C.memmove(ffmIds, ffmDisplays, 64);
		check("CGGetDisplaysWithRect count", jniN, ffmN);
		check("CGGetDisplaysWithRect ids", jniIds, ffmIds);
		C.free(jniDisplays);
		C.free(ffmDisplays);
		C.free(jniCount);
		C.free(ffmCount);
		check("CFRunLoopGetCurrent", OS.CFRunLoopGetCurrent(), OS_FFM.CFRunLoopGetCurrent());
		check("CGImageGetWidth", OS.CGImageGetWidth(0), OS_FFM.CGImageGetWidth(0));
		System.out.println("Compared " + (checks - start) + " Core Graphics and Core Foundation calls");
	}

	/* ------------------------------------------------------------------ C */

	static void checkC() {
		int start = checks;
		check("PTR_sizeof", C.PTR_sizeof(), C_FFM.PTR_sizeof());
		long jniHome = C.getenv(utf8("HOME")), ffmHome = C_FFM.getenv(utf8("HOME"));
		check("getenv", jniHome, ffmHome);
		check("getenv missing", C.getenv(utf8("FFM_NO_SUCH_VARIABLE")), C_FFM.getenv(utf8("FFM_NO_SUCH_VARIABLE")));
		check("setenv", C.setenv(utf8("FFM_CROSS_CHECK"), utf8("value"), 1), C_FFM.setenv(utf8("FFM_CROSS_CHECK"), utf8("other"), 1));
		check("getenv set", cString(C.getenv(utf8("FFM_CROSS_CHECK"))), "other");
		long buffer = C_FFM.malloc(32);
		check("malloc", true, buffer != 0);
		C_FFM.memset(buffer, 0x41, 31);
		C_FFM.memset(buffer + 31, 0, 1);
		check("strlen", C.strlen(buffer), C_FFM.strlen(buffer));
		int[] ints = {1, 2, 3, 4};
		C_FFM.memmove(buffer, ints, 16);
		int[] jniInts = new int[4], ffmInts = new int[4];
		C.memmove(jniInts, buffer, 16);
		C_FFM.memmove(ffmInts, buffer, 16);
		check("memmove int[]", jniInts, ffmInts);
		expect("memmove int[]", ints, ffmInts);
		double[] doubles = {1.5, -2.5};
		C.memmove(buffer, doubles, 16);
		double[] back = new double[2];
		C_FFM.memmove(back, buffer, 16);
		expect("memmove double[]", doubles, back);
		C_FFM.free(buffer);
		System.out.println("Compared " + (checks - start) + " C calls");
	}
}
