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
package org.eclipse.swt.tools.internal;

import java.lang.foreign.*;
import java.lang.reflect.*;
import java.util.*;

import org.eclipse.swt.tools.internal.CTypes.*;

/**
 * Generates Java classes that implement SWT native declarations with the Foreign Function and Memory API.
 * C types come from a clang AST dump of the JNI C file and struct layouts from {@link FFMProbeGenerator}.
 */
public class FFMGenerator extends JNIGenerator {

	public static final String SUFFIX = "_FFM";
	public static final String STRUCTS = "Structs" + SUFFIX;

	/** Everything the generator needs to know about one generated struct. */
	public static class StructInfo {
		JNIClass clazz;
		String packageName;
		long size;
		Map<String, String> fields = new HashMap<>();
		/** Passed by value somewhere, so <code>Structs_FFM</code> needs its <code>MemoryLayout</code>. */
		boolean byValue;

		String helper() {
			return packageName + "." + STRUCTS;
		}
	}

	/** Natives whose C code needs to be entered from a JNI native method, with the reason. */
	static final Map<String, String> JNI_ONLY = Map.of(
		"swt_fixed_accessible_register_accessible", "JNI caller context: caches the SWT class for JNI FindClass");

	/** Libraries the JNI glue of a natives class dlopens itself instead of linking against, as a lookup expression. */
	static final Map<String, String> LOOKUPS = Map.of(
		"org.eclipse.swt.internal.opengl.glx.GLX", "FFM.library(\"libGL.so.1\")",
		"org.eclipse.swt.internal.webkit.WebKitGTK", "\"1\".equals(System.getenv(\"SWT_GTK4\")) ? FFM.library(\"libwebkitgtk-6.0.so.4\") : FFM.library(\"libwebkit2gtk-4.1.so.0\", \"libwebkit2gtk-4.0.so.37\")");

	/** Natives classes left to hand written code as a whole, with the reason. */
	static final Map<String, String> EXCLUDED = Map.of(
		"org.eclipse.swt.internal.gdip.Gdip", "GDI+: inline C++ wrapper classes without exported symbols, ported by hand on the flat API");

	/**
	 * Win32 functions whose last error SWT reads with <code>OS.GetLastError()</code> afterwards, see its call sites
	 * (Device.getLastError via Image, Menu, MenuItem, PDFDocument, KeyboardLayoutTest). The JVM may change the
	 * thread's last error between two downcalls, so these capture it and <code>GetLastError</code> returns the capture.
	 */
	static final Set<String> CAPTURE_LAST_ERROR = Set.of(
		"ActivateKeyboardLayout", "CreateCompatibleBitmap", "CreateDIBSection", "GetKeyboardLayoutList",
		"GetMenuDefaultItem", "GetMenuItemCount", "GetMenuItemInfo", "InsertMenuItem", "SendInput",
		"SetMenuItemInfo", "StartDoc", "StartPage");

	final CTypes ctypes;
	final Map<String, StructInfo> structs;
	final Map<String, String> unsupported = new TreeMap<>();
	final Set<String> supported = new TreeSet<>();
	/** Lookup of the class being generated, <code>null</code> for the SWT libraries. */
	String lookup;
	/**
	 * Windows: exported function name to the DLL its import library names, from the link libraries of the
	 * native build; <code>null</code> on other platforms, where symbols resolve through {@code FFM}'s lookup.
	 */
	Map<String, String> imports;
	/** Symbol and library of every generated downcall, for the report. */
	final Map<String, String> symbols = new TreeMap<>();

	public FFMGenerator(CTypes ctypes, Map<String, StructInfo> structs) {
		this.ctypes = ctypes;
		this.structs = structs;
	}

	public void setImports(Map<String, String> imports) {
		this.imports = imports;
	}

	/** Sizes of C types without a Java struct class, from the probe of the unit, generated as <code>Extra_FFM</code>. */
	Map<String, String> extras = Map.of();

	public void setExtras(Map<String, String> extras) {
		this.extras = extras;
	}

	public Map<String, String> getSymbols() {
		return symbols;
	}

	static String packageOf(String qualifiedName) {
		int dot = qualifiedName.lastIndexOf('.');
		return dot == -1 ? "" : qualifiedName.substring(0, dot);
	}

	static String simpleName(String name) {
		return name.substring(name.lastIndexOf('.') + 1);
	}

	/** Key identifying a native declaration by class, name and simple parameter type names. */
	public static String key(String className, String methodName, List<String> paramTypes) {
		StringBuilder b = new StringBuilder(className).append('#').append(methodName).append('(');
		for (int i = 0; i < paramTypes.size(); i++) {
			if (i > 0) b.append(',');
			b.append(simpleName(paramTypes.get(i).replace(" ", "")));
		}
		return b.append(')').toString();
	}

	static String key(JNIMethod method) {
		List<String> types = new ArrayList<>();
		for (JNIType type : method.getParameterTypes()) types.add(type.getTypeSignature3());
		return key(method.getDeclaringClass().getName(), method.getName(), types);
	}

	/* ---------------------------------------------------------------- kinds and conversions */

	static Kind javaKind(JNIType type) {
		switch (type.getName()) {
			case "void": return Kind.VOID;
			case "boolean": return Kind.U8;
			case "byte": return Kind.I8;
			case "char": return Kind.U16;
			case "short": return Kind.I16;
			case "int": return Kind.I32;
			case "long": return Kind.I64;
			case "float": return Kind.F32;
			case "double": return Kind.F64;
			default: return type.isArray() ? Kind.PTR : Kind.UNKNOWN;
		}
	}

	static Kind promote(Kind kind) {
		switch (kind) {
			case I8: case U8: case I16: case U16: return Kind.I32;
			case F32: return Kind.F64;
			default: return kind;
		}
	}

	static String carrier(Kind kind) {
		switch (kind) {
			case I8: case U8: return "byte";
			case I16: case U16: return "short";
			case I32: case U32: return "int";
			case I64: case U64: case PTR: return "long";
			case F32: return "float";
			case F64: return "double";
			default: throw new IllegalArgumentException(kind.toString());
		}
	}

	static String layout(Kind kind) {
		switch (kind) {
			case I8: case U8: return "JAVA_BYTE";
			case I16: case U16: return "JAVA_SHORT";
			case I32: case U32: return "JAVA_INT";
			case I64: case U64: case PTR: return "JAVA_LONG";
			case F32: return "JAVA_FLOAT";
			case F64: return "JAVA_DOUBLE";
			default: throw new IllegalArgumentException(kind.toString());
		}
	}

	/** Java value converted the way a C assignment converts the JNI type to the C type. */
	static String toC(String javaType, Kind kind, String value) {
		String v = javaType.equals("boolean") ? "(" + value + " ? 1 : 0)" : value;
		String carrier = carrier(kind);
		if (carrier.equals(javaType)) return value;
		return "(" + carrier + ") " + v;
	}

	/** C value of <code>kind</code> converted the way a C cast converts it to the JNI type. */
	static String toJava(Kind kind, String javaType, String value) {
		if (javaType.equals("boolean")) {
			if (kind == Kind.F32 || kind == Kind.F64) return "(" + value + " != 0)";
			return kind == Kind.I8 || kind == Kind.U8 ? "(" + value + " != 0)" : "((byte) " + value + " != 0)";
		}
		if (kind == Kind.F32 || kind == Kind.F64 || javaType.equals(carrier(kind))) {
			return carrier(kind).equals(javaType) ? value : "(" + javaType + ") " + value;
		}
		String asLong;
		switch (kind) {
			case U8: asLong = "Byte.toUnsignedLong(" + value + ")"; break;
			case U16: asLong = "Short.toUnsignedLong(" + value + ")"; break;
			case U32: asLong = "Integer.toUnsignedLong(" + value + ")"; break;
			default: asLong = "(long) " + value;
		}
		switch (javaType) {
			case "long": return asLong;
			case "int":
				if (kind == Kind.U8) return "Byte.toUnsignedInt(" + value + ")";
				if (kind == Kind.U16) return "Short.toUnsignedInt(" + value + ")";
				return "(int) " + value;
			default: return "(" + javaType + ") " + asLong;
		}
	}

	/* ---------------------------------------------------------------- method planning */

	static class Param {
		JNIParameter param;
		JNIType type;
		Kind kind;
		String mode;
		StructInfo struct;
		/** A copied array, string or struct whose address is passed as an integer. */
		boolean address;
	}

	class Plan {
		JNIMethod method;
		String cName;
		String special;
		StructInfo struct;
		Kind returnKind;
		List<Param> params = new ArrayList<>();
		int firstVariadic = -1;
		boolean dynamic, critical, arena, captureLastError;
	}

	static boolean isMemmove(JNIMethod method) {
		String name = method.getName();
		if (name.startsWith("_")) name = name.substring(1);
		return (name.equals("memmove") || name.equals("MoveMemory")) && method.getParameters().length == 2 && method.getReturnType().isType("void");
	}

	/** Returns the plan for <code>method</code>, or <code>null</code> after recording why it stays on JNI. */
	Plan plan(JNIMethod method) {
		String reason = reject(method);
		if (reason != null) {
			unsupported.put(key(method), reason);
			return null;
		}
		Plan plan = new Plan();
		plan.method = method;
		String accessor = method.getAccessor();
		String name = method.getName();
		plan.cName = accessor.length() != 0 ? accessor : name.startsWith("_") ? name.substring(1) : name;
		reason = fill(plan);
		if (reason != null) {
			unsupported.put(key(method), reason);
			return null;
		}
		supported.add(key(method));
		return plan;
	}

	String reject(JNIMethod method) {
		String excluded = EXCLUDED.get(method.getDeclaringClass().getName());
		if (excluded != null) return excluded;
		if (method.getFlag(FLAG_NO_GEN)) return "no_gen: hand written C";
		String jniOnly = JNI_ONLY.get(method.getName());
		if (jniOnly != null) return jniOnly;
		for (String flag : new String[] {FLAG_JNI, FLAG_CPP, FLAG_SETTER, FLAG_GETTER, FLAG_ADDER, FLAG_NEW, FLAG_DELETE, FLAG_GCNEW, FLAG_OBJECT, FLAG_CAST}) {
			if (method.getFlag(flag)) return "flag: " + flag;
		}
		if (method.getFlag(FLAG_CONST) && !method.getFlag(FLAG_ADDRESS)) return "const: macro or constant";
		String name = method.getName();
		if (name.startsWith("CALLBACK_")) return "callback: " + name;
		if (name.equalsIgnoreCase("call") || name.startsWith("callFunc") || name.contains("VtblCall")) return "function pointer call: " + name;
		JNIType returnType = method.getReturnType();
		if (!returnType.isPrimitive()) return "Java return type: " + returnType.getSimpleName();
		return null;
	}

	String fill(Plan plan) {
		JNIMethod method = plan.method;
		JNIParameter[] params = method.getParameters();
		JNIType returnType = method.getReturnType();

		if (method.getFlag(FLAG_ADDRESS)) {
			if (!returnType.isType("long") || params.length != 0) return "address: has parameters";
			plan.special = "address";
			return null;
		}
		if (isMemmove(method)) {
			boolean toNative = params[0].getType().isPrimitive();
			JNIType structType = params[toNative ? 1 : 0].getType();
			if (structType.isPrimitive() || structType.isArray()) return "memmove: between primitives";
			StructInfo info = structs.get(structType.getName());
			if (info == null) return "struct without layout: " + structType.getSimpleName();
			plan.special = toNative ? "memmove_write" : "memmove_read";
			plan.struct = info;
			return null;
		}
		if (method.getName().endsWith("_sizeof") && params.length == 0 && returnType.isType("int")) {
			String structName = method.getName().substring(0, method.getName().length() - "_sizeof".length());
			StructInfo match = structs.get(packageOf(method.getDeclaringClass().getName()) + "." + structName);
			if (match == null) {
				for (StructInfo info : structs.values()) {
					if (match == null && info.clazz.getSimpleName().equals(structName)) match = info;
				}
			}
			if (match != null) {
				plan.special = "sizeof";
				plan.struct = match;
				return null;
			}
			// Windows only: GTK implements these in FFMTypes
			if (imports != null && extras.containsKey(structName)) {
				plan.special = "sizeof_extra";
				return null;
			}
		}

		if (imports != null && plan.cName.equals("GetLastError") && params.length == 0 && returnType.isType("int")) {
			plan.special = "last_error";
			return null;
		}

		plan.dynamic = method.getFlag(FLAG_DYNAMIC);
		plan.captureLastError = imports != null && CAPTURE_LAST_ERROR.contains(plan.cName);
		Function function = null;
		if (plan.dynamic) {
			plan.returnKind = javaKind(returnType);
		} else {
			String resolved = ctypes.resolve(plan.cName);
			if (resolved == null) return "no C function declaration (macro or custom C): " + plan.cName;
			plan.cName = resolved;
			// Windows: the symbol comes from a DLL of the import libraries or from the C runtime of the default lookup
			if (imports != null && !imports.containsKey(plan.cName) && Linker.nativeLinker().defaultLookup().find(plan.cName).isEmpty()) {
				return "no exported symbol (custom C): " + plan.cName;
			}
			function = ctypes.getFunction(plan.cName);
			if (function.inline) return "static inline: " + plan.cName;
			plan.returnKind = ctypes.classify(function.returnType);
			if (plan.returnKind == Kind.UNKNOWN || plan.returnKind == Kind.STRUCT) return "C return type: " + function.returnType;
			int javaCount = params.length;
			if (javaCount < function.params.size() || (javaCount > function.params.size() && !function.variadic)) {
				return "parameter count: Java " + javaCount + ", C " + function.params.size();
			}
			if (function.variadic) plan.firstVariadic = function.params.size();
		}
		if (plan.returnKind == Kind.VOID) {
			if (!returnType.isType("void")) return "C returns void: " + plan.cName;
		} else if (returnType.isType("void")) {
			plan.returnKind = Kind.VOID;
		}

		for (int i = 0; i < params.length; i++) {
			JNIParameter param = params[i];
			Param p = new Param();
			p.param = param;
			p.type = param.getType();
			String cast = param.getCast();
			boolean variadic = plan.firstVariadic != -1 && i >= plan.firstVariadic;
			if (function != null && !variadic) {
				p.kind = ctypes.classify(function.params.get(i));
			} else if (cast.length() > 2) {
				p.kind = ctypes.classify(cast);
			} else {
				p.kind = p.type.isPrimitive() ? javaKind(p.type) : Kind.PTR;
			}
			if (variadic) p.kind = promote(p.kind);
			if (param.getFlag(FLAG_SENTINEL) && i == params.length - 1) {
				p.mode = "sentinel";
				p.kind = Kind.PTR;
			} else if (param.getFlag(FLAG_STRUCT)) {
				// passed by value: the JNI glue dereferences the Java value, a pointer or a struct object
				if (function == null || variadic) return "struct by value without prototype: " + plan.cName;
				Kind cKind = ctypes.classify(function.params.get(i));
				if (p.type.isPrimitive()) {
					if (!p.type.isType("long") || !(cKind.isInteger() || cKind == Kind.PTR || cKind == Kind.F32 || cKind == Kind.F64)) {
						return "struct by value of C type: " + function.params.get(i);
					}
					p.mode = "deref";
					p.kind = cKind;
				} else {
					if (cKind != Kind.STRUCT) return "struct by value, C parameter is not a struct: " + function.params.get(i);
					p.struct = structs.get(p.type.getName());
					if (p.struct == null) return "struct without layout: " + p.type.getSimpleName();
					String reason = structLayout(p.struct, new ArrayList<>());
					if (reason != null) return "struct by value: " + reason;
					p.struct.byValue = true;
					p.mode = "byvalue";
					p.kind = Kind.STRUCT;
					plan.arena = true;
				}
			} else if (p.type.isPrimitive()) {
				p.mode = "value";
				if (!(p.kind.isInteger() || p.kind == Kind.PTR || p.kind == Kind.F32 || p.kind == Kind.F64)) {
					return "C parameter type: " + (function != null && !variadic ? function.params.get(i) : cast);
				}
			} else {
				if (p.kind == Kind.I64 || p.kind == Kind.U64) {
					// the JNI glue casts the pointer to a pointer sized integer, such as the LPARAM of SendMessage
					p.address = true;
				} else if (p.kind != Kind.PTR) {
					return "C parameter is not a pointer: " + p.type.getSimpleName();
				}
				if (p.type.isArray()) {
					JNIType component = p.type.getComponentType();
					if (!component.isPrimitive() || component.isType("boolean")) return "array type: " + p.type.getTypeSignature3();
					// a heap segment has no address to pass as an integer
					p.mode = isCritical(param) && !p.address ? "critical" : "array";
				} else if (p.type.isType("java.lang.String")) {
					if (param.getFlag(FLAG_UNICODE)) return "unicode string: " + plan.cName;
					p.mode = "string";
				} else if (p.type.isType("java.lang.Object") || p.type.isType("java.lang.Class")) {
					return "Object parameter: " + plan.cName;
				} else {
					p.struct = structs.get(p.type.getName());
					if (p.struct == null) return "struct without layout: " + p.type.getSimpleName();
					p.mode = "struct";
				}
				p.kind = Kind.PTR;
			}
			if (p.mode.equals("critical")) plan.critical = true;
			if (p.mode.equals("array") || p.mode.equals("string") || p.mode.equals("struct")) plan.arena = true;
			plan.params.add(p);
		}
		return null;
	}

	static boolean isCritical(JNIParameter param) {
		JNIType type = param.getType();
		return type.isArray() && type.getComponentType().isPrimitive() && param.getFlag(FLAG_CRITICAL);
	}

	/* ---------------------------------------------------------------- output */

	@Override
	public void generateCopyright() {
		outputln("/*******************************************************************************");
		outputln(" * Copyright (c) 2026 vogella GmbH and others.");
		outputln(" *");
		outputln(" * This program and the accompanying materials");
		outputln(" * are made available under the terms of the Eclipse Public License 2.0");
		outputln(" * which accompanies this distribution, and is available at");
		outputln(" * https://www.eclipse.org/legal/epl-2.0/");
		outputln(" *");
		outputln(" * SPDX-License-Identifier: EPL-2.0");
		outputln(" *******************************************************************************/");
	}

	void generateHeader(String packageName) {
		generateCopyright();
		outputln("/* Note: This file was auto-generated by " + FFMGenerator.class.getName() + " */");
		outputln("/* DO NOT EDIT - your changes will be lost. */");
		output("package ");
		output(packageName);
		outputln(";");
		outputln();
		outputln("import static java.lang.foreign.ValueLayout.*;");
		outputln();
		outputln("import java.lang.foreign.*;");
		outputln("import java.lang.invoke.*;");
		outputln();
		outputln("import org.eclipse.swt.internal.ffm.*;");
		outputln();
	}

	/** Generates the <code>_FFM</code> class for one natives class. */
	@Override
	public void generate(JNIClass clazz) {
		JNIMethod[] methods = clazz.getDeclaredMethods();
		sort(methods);
		List<Plan> plans = new ArrayList<>();
		for (JNIMethod method : methods) {
			if ((method.getModifiers() & Modifier.NATIVE) == 0) continue;
			Plan plan = plan(method);
			if (plan != null) plans.add(plan);
		}
		generateHeader(packageOf(clazz.getName()));
		outputln("@SuppressWarnings(\"all\")");
		output("public final class ");
		output(clazz.getSimpleName() + SUFFIX);
		outputln(" {");
		outputln();
		lookup = LOOKUPS.get(clazz.getName());
		if (lookup != null) {
			outputln("private static final SymbolLookup LOOKUP = " + lookup + ";");
			outputln();
		}
		for (Plan plan : plans) {
			generate(plan);
		}
		outputln("}");
	}

	void generate(Plan plan) {
		JNIMethod method = plan.method;
		String returnJava = method.getReturnType().getTypeSignature3();
		String holder = "MH_" + getFunctionName(method);
		if (plan.special == null) generateHolder(plan, holder);

		output("public static ");
		output(returnJava);
		output(" ");
		output(method.getName());
		output("(");
		JNIParameter[] params = method.getParameters();
		for (int i = 0; i < params.length; i++) {
			if (i != 0) output(", ");
			output(params[i].getType().getTypeSignature3());
			output(" arg" + i);
		}
		outputln(") {");
		if (plan.special != null) {
			generateSpecial(plan);
			outputln("}");
			outputln();
			return;
		}
		output(plan.arena ? "\ttry (Arena arena = Arena.ofConfined()) {" : "\ttry {");
		outputln();
		for (int i = 0; i < plan.params.size(); i++) {
			Param p = plan.params.get(i);
			switch (p.mode) {
				case "array":
					outputln("\t\tMemorySegment lparg" + i + " = FFM.copyIn(arena, arg" + i + ");");
					break;
				case "string":
					outputln("\t\tMemorySegment lparg" + i + " = FFM.string(arena, arg" + i + ");");
					break;
				case "struct":
					outputln("\t\tMemorySegment lparg" + i + " = arg" + i + " == null ? MemorySegment.NULL : arena.allocate(" + p.struct.helper() + "." + p.struct.clazz.getSimpleName() + "_SIZEOF, 16);");
					if (!p.param.getFlag(FLAG_NO_IN)) {
						outputln("\t\tif (arg" + i + " != null) " + p.struct.helper() + "." + p.struct.clazz.getSimpleName() + "_write(lparg" + i + ", arg" + i + ");");
					}
					break;
				case "byvalue":
					// JNI dereferences a null struct, here it is passed zeroed
					outputln("\t\tMemorySegment lparg" + i + " = arena.allocate(" + p.struct.helper() + "." + p.struct.clazz.getSimpleName() + "_LAYOUT);");
					if (!p.param.getFlag(FLAG_NO_IN)) {
						outputln("\t\tif (arg" + i + " != null) " + p.struct.helper() + "." + p.struct.clazz.getSimpleName() + "_write(lparg" + i + ", arg" + i + ");");
					}
					break;
				default:
			}
		}
		String indent = "\t\t";
		boolean returns = plan.returnKind != Kind.VOID;
		if (plan.dynamic) {
			if (returns) outputln("\t\t" + carrier(plan.returnKind) + " rc = 0;");
			outputln("\t\tif (" + holder + ".MH != null) {");
			indent = "\t\t\t";
		}
		output(indent);
		if (returns) {
			if (!plan.dynamic) output(carrier(plan.returnKind) + " ");
			output("rc = (" + carrier(plan.returnKind) + ") ");
		}
		output(holder + ".MH.invokeExact(");
		if (plan.captureLastError) output(plan.params.isEmpty() ? "FFMLastError.state()" : "FFMLastError.state(), ");
		for (int i = 0; i < plan.params.size(); i++) {
			if (i != 0) output(", ");
			Param p = plan.params.get(i);
			switch (p.mode) {
				case "sentinel": output("0L"); break;
				case "value": output(toC(p.type.getName(), p.kind, "arg" + i)); break;
				case "critical": output("FFM.heap(arg" + i + ")"); break;
				case "deref": output("FFM.segment(arg" + i + ", " + byteSize(p.kind) + ").get(" + layout(p.kind) + ", 0)"); break;
				default: output(p.address ? "lparg" + i + ".address()" : "lparg" + i);
			}
		}
		outputln(");");
		if (plan.dynamic) outputln("\t\t}");
		// same order as the JNI setters, so the first of several aliased arrays is copied back last
		for (int i = plan.params.size() - 1; i >= 0; i--) {
			Param p = plan.params.get(i);
			if (p.param.getFlag(FLAG_NO_OUT)) continue;
			if (p.mode.equals("array")) {
				outputln("\t\tFFM.copyOut(lparg" + i + ", arg" + i + ");");
			} else if (p.mode.equals("struct") || p.mode.equals("byvalue")) {
				outputln("\t\tif (arg" + i + " != null) " + p.struct.helper() + "." + p.struct.clazz.getSimpleName() + "_read(lparg" + i + ", arg" + i + ");");
			}
		}
		// a callback may have failed during the call, and JNI let that exception surface here
		outputln("\t\tFFM.checkCallbackException();");
		if (returns) outputln("\t\treturn " + toJava(plan.returnKind, returnJava, "rc") + ";");
		outputln("\t} catch (Throwable e) {");
		outputln("\t\tthrow FFM.rethrow(e);");
		outputln("\t}");
		outputln("}");
		outputln();
	}

	void generateHolder(Plan plan, String holder) {
		output("private static final class ");
		output(holder);
		outputln(" {");
		output("\tstatic final MethodHandle MH = FFM.");
		output(plan.dynamic ? "downcallOptional" : "downcall");
		output("(" + lookupArgument(plan) + "\"");
		output(plan.cName);
		output("\", ");
		StringBuilder layouts = new StringBuilder();
		for (Param p : plan.params) {
			if (layouts.length() != 0) layouts.append(", ");
			boolean segment = p.mode.equals("array") || p.mode.equals("string") || p.mode.equals("struct") || p.mode.equals("critical");
			if (p.mode.equals("byvalue")) layouts.append(p.struct.helper() + "." + p.struct.clazz.getSimpleName() + "_LAYOUT");
			else layouts.append(segment && !p.address ? "ADDRESS" : layout(p.kind));
		}
		if (plan.returnKind == Kind.VOID) {
			output("FunctionDescriptor.ofVoid(" + layouts + ")");
		} else {
			output("FunctionDescriptor.of(" + layout(plan.returnKind) + (layouts.length() != 0 ? ", " : "") + layouts + ")");
		}
		if (plan.firstVariadic != -1) output(", Linker.Option.firstVariadicArg(" + plan.firstVariadic + ")");
		if (plan.critical) output(", Linker.Option.critical(true)");
		if (plan.captureLastError) output(", Linker.Option.captureCallState(\"GetLastError\")");
		outputln(");");
		outputln("}");
	}

	/**
	 * The lookup argument, with trailing comma, for {@code FFM.downcall} and {@code FFM.address}: the class lookup, or
	 * on Windows the DLL that the import library or the <code>name_LIB</code> macro of a dynamic function names.
	 * Functions of neither kind, the C runtime, resolve through the default lookup of {@code FFM}.
	 */
	String lookupArgument(Plan plan) {
		if (imports == null) return lookup != null ? "LOOKUP, " : "";
		String dll = plan.dynamic ? ctypes.getLibrary(plan.cName) : imports.get(plan.cName);
		symbols.put(plan.cName, dll != null ? dll : "-");
		return dll != null ? "FFMLibraries.get(\"" + dll + "\"), " : "";
	}

	static int byteSize(Kind kind) {
		switch (kind) {
			case I8: case U8: return 1;
			case I16: case U16: return 2;
			case I32: case U32: case F32: return 4;
			default: return 8;
		}
	}

	void generateSpecial(Plan plan) {
		String struct = plan.struct != null ? plan.struct.helper() + "." + plan.struct.clazz.getSimpleName() : null;
		switch (plan.special) {
			case "address":
				outputln("\treturn FFM.address(" + lookupArgument(plan) + "\"" + plan.cName + "\");");
				break;
			case "sizeof_extra": {
				String name = plan.method.getName();
				outputln("\treturn (int) Extra" + SUFFIX + "." + name.substring(0, name.length() - "_sizeof".length()).toUpperCase(Locale.ROOT) + ";");
				break;
			}
			case "last_error":
				// what the last function with a captured call state left, see CAPTURE_LAST_ERROR
				outputln("\treturn FFMLastError.get();");
				break;
			case "sizeof":
				outputln("\treturn (int) " + struct + "_SIZEOF;");
				break;
			case "memmove_write":
				outputln("\tif (arg1 != null) " + struct + "_write(FFM.segment(arg0, " + struct + "_SIZEOF), arg1);");
				break;
			case "memmove_read": {
				// the JNI glue copied the given number of bytes, so never read beyond it
				boolean sized = plan.method.getParameters().length == 3;
				String size = sized ? "Math.min(arg2, " + struct + "_SIZEOF)" : struct + "_SIZEOF";
				outputln("\tif (arg0 != null) " + struct + "_read(FFM.segment(arg1, " + size + "), arg0);");
				break;
			}
			default:
				throw new IllegalStateException(plan.special);
		}
	}

	/* ---------------------------------------------------------------- structs */

	static boolean ignoreField(JNIField field) {
		int mods = field.getModifiers();
		return field.getFlag(FLAG_NO_GEN) || (mods & Modifier.PUBLIC) == 0 || (mods & Modifier.FINAL) != 0 || (mods & Modifier.STATIC) != 0;
	}

	static Kind fieldKind(long size, int typeClass, boolean signed) {
		if (typeClass == 8) return size == 4 ? Kind.F32 : Kind.F64;
		if (typeClass == 5) return Kind.PTR;
		switch ((int) size) {
			case 1: return signed ? Kind.I8 : Kind.U8;
			case 2: return signed ? Kind.I16 : Kind.U16;
			case 4: return signed ? Kind.I32 : Kind.U32;
			default: return signed ? Kind.I64 : Kind.U64;
		}
	}

	static String unaligned(Kind kind) {
		String layout = layout(kind);
		return layout.equals("JAVA_BYTE") ? layout : layout + "_UNALIGNED";
	}

	static int javaSize(JNIType type) {
		switch (type.getName()) {
			case "byte": case "boolean": return 1;
			case "short": case "char": return 2;
			case "int": case "float": return 4;
			default: return 8;
		}
	}

	/**
	 * Builds the <code>MemoryLayout</code> expression of a struct passed by value from its probed fields, with padding
	 * for C fields that Java does not map; returns why that is not possible, or <code>null</code>.
	 * <code>out</code> receives the expression and the alignment.
	 */
	String structLayout(StructInfo info, List<Object> out) {
		TreeMap<Long, Object[]> members = new TreeMap<>();
		for (JNIClass c = info.clazz; !c.getName().equals("java.lang.Object"); c = c.getSuperclass()) {
			StructInfo owner = structs.get(c.getName());
			if (owner == null) return "struct without layout: " + c.getSimpleName();
			for (JNIField field : c.getDeclaredFields()) {
				if (ignoreField(field)) continue;
				String layout = owner.fields.get(field.getName());
				if (layout == null) continue;
				String[] parts = layout.split(",");
				if (parts[0].equals("bit")) return "bit-field " + c.getSimpleName() + "." + field.getName();
				long offset = Long.parseLong(parts[0]), size = Long.parseLong(parts[1]);
				JNIType type = field.getType();
				String expr;
				long align;
				if (type.isPrimitive()) {
					Kind kind = fieldKind(size, Integer.parseInt(parts[2]), parts[3].equals("1"));
					expr = layout(kind);
					align = size;
				} else if (type.isArray()) {
					long elementSize = Long.parseLong(parts[4]);
					JNIType component = type.getComponentType();
					Kind element = component.isType("float") || component.isType("double") ? (elementSize == 4 ? Kind.F32 : Kind.F64)
						: fieldKind(elementSize, 1, true);
					expr = "MemoryLayout.sequenceLayout(" + size / elementSize + ", " + layout(element) + ")";
					align = elementSize;
				} else {
					StructInfo nested = structs.get(type.getName());
					if (nested == null) return "struct without layout: " + type.getSimpleName();
					List<Object> inner = new ArrayList<>();
					String reason = structLayout(nested, inner);
					if (reason != null) return reason;
					expr = (String) inner.get(0);
					align = (Long) inner.get(1);
				}
				if (offset % align != 0) return "packed field " + c.getSimpleName() + "." + field.getName();
				if (members.put(offset, new Object[] {expr, size, align}) != null) return "overlapping fields (union) in " + c.getSimpleName();
			}
		}
		StringBuilder b = new StringBuilder("MemoryLayout.structLayout(");
		long position = 0, alignment = 1;
		for (Map.Entry<Long, Object[]> member : members.entrySet()) {
			long offset = member.getKey(), size = (Long) member.getValue()[1];
			if (offset < position) return "overlapping fields (union) in " + info.clazz.getSimpleName();
			if (position != 0) b.append(", ");
			if (offset > position) b.append("MemoryLayout.paddingLayout(" + (offset - position) + "), ");
			b.append(member.getValue()[0]);
			position = offset + size;
			alignment = Math.max(alignment, (Long) member.getValue()[2]);
		}
		if (position > info.size || info.size % alignment != 0) return "size " + info.size + " does not fit its fields";
		if (position < info.size) b.append(position != 0 ? ", " : "").append("MemoryLayout.paddingLayout(" + (info.size - position) + ")");
		out.add(b.append(")").toString());
		out.add(alignment);
		return null;
	}

	/** Generates the <code>Structs_FFM</code> class for the structs of one package. */
	public void generateStructs(String packageName, List<StructInfo> infos) {
		generateHeader(packageName);
		outputln("@SuppressWarnings(\"all\")");
		outputln("public final class " + STRUCTS + " {");
		outputln();
		for (StructInfo info : infos) {
			String name = info.clazz.getSimpleName();
			String type = info.clazz.getName();
			outputln("public static final long " + name + "_SIZEOF = " + info.size + "L;");
			if (info.byValue) {
				List<Object> layout = new ArrayList<>();
				if (structLayout(info, layout) != null) throw new IllegalStateException(name);
				outputln("public static final StructLayout " + name + "_LAYOUT = " + layout.get(0) + ";");
			}
			for (JNIField field : info.clazz.getDeclaredFields()) {
				if (ignoreField(field)) continue;
				String layout = info.fields.get(field.getName());
				if (layout == null || layout.startsWith("bit")) continue;
				outputln("public static final long " + name + "_" + field.getName().toUpperCase(java.util.Locale.ROOT) + "_OFFSET = " + layout.split(",")[0] + "L;");
			}
			outputln();
			for (boolean read : new boolean[] {true, false}) {
				outputln("public static void " + name + (read ? "_read" : "_write") + "(MemorySegment s, " + type + " o) {");
				JNIClass superclass = info.clazz.getSuperclass();
				if (!superclass.getName().equals("java.lang.Object")) {
					StructInfo superInfo = structs.get(superclass.getName());
					outputln("\t" + superInfo.helper() + "." + superclass.getSimpleName() + (read ? "_read" : "_write") + "(s, o);");
				}
				for (JNIField field : info.clazz.getDeclaredFields()) {
					if (ignoreField(field)) continue;
					String layout = info.fields.get(field.getName());
					if (layout == null) continue;
					outputln("\t" + fieldStatement(field, layout, read));
				}
				outputln("}");
				outputln();
			}
		}
		outputln("}");
	}

	String fieldStatement(JNIField field, String layout, boolean read) {
		String[] parts = layout.split(",");
		JNIType type = field.getType();
		String javaType = type.getTypeSignature3();
		String target = "o." + field.getName();
		if (parts[0].equals("bit")) {
			String bits = "s, " + parts[1] + "L, " + parts[2];
			if (read && javaType.equals("boolean")) return target + " = (FFM.getBits(" + bits + ") & 1) != 0;";
			if (read) return target + " = " + toJava(Kind.U64, javaType, "FFM.getBits(" + bits + ")") + ";";
			return "FFM.setBits(" + bits + ", " + toC(javaType, Kind.I64, target) + ");";
		}
		long offset = Long.parseLong(parts[0]), size = Long.parseLong(parts[1]);
		if (type.isPrimitive()) {
			Kind kind = fieldKind(size, Integer.parseInt(parts[2]), parts[3].equals("1"));
			String value = "s.get(" + unaligned(kind) + ", " + offset + "L)";
			// JNI SetBooleanField keeps only the lowest bit, unlike native method results
			if (read && javaType.equals("boolean")) return target + " = (" + value + " & 1) != 0;";
			if (read) return target + " = " + toJava(kind, javaType, value) + ";";
			return "s.set(" + unaligned(kind) + ", " + offset + "L, " + toC(javaType, kind, target) + ");";
		}
		if (type.isArray()) {
			JNIType component = type.getComponentType();
			long count = component.isType("byte") ? size : size / javaSize(component);
			String element = unaligned(javaKind(component) == Kind.U16 ? Kind.U16 : javaKind(component));
			if (component.isType("char")) element = "JAVA_CHAR_UNALIGNED";
			if (read) return "MemorySegment.copy(s, " + element + ", " + offset + "L, " + target + ", 0, " + count + ");";
			return "MemorySegment.copy(" + target + ", 0, s, " + element + ", " + offset + "L, " + count + ");";
		}
		StructInfo nested = structs.get(type.getName());
		String helper = nested.helper() + "." + type.getSimpleName();
		return "if (" + target + " != null) " + helper + (read ? "_read" : "_write") + "(s.asSlice(" + offset + "L), " + target + ");";
	}

	public Map<String, String> getUnsupported() {
		return unsupported;
	}

	public Set<String> getSupported() {
		return supported;
	}
}
