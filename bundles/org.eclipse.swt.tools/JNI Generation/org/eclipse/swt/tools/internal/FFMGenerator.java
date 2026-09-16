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

		String helper() {
			return packageName + "." + STRUCTS;
		}
	}

	/** Natives whose C code needs to be entered from a JNI native method, with the reason. */
	static final Map<String, String> JNI_ONLY = Map.of(
		"swt_fixed_accessible_register_accessible", "JNI caller context: caches the SWT class for JNI FindClass");

	final CTypes ctypes;
	final Map<String, StructInfo> structs;
	final Map<String, String> unsupported = new TreeMap<>();
	final Set<String> supported = new TreeSet<>();

	public FFMGenerator(CTypes ctypes, Map<String, StructInfo> structs) {
		this.ctypes = ctypes;
		this.structs = structs;
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
	}

	class Plan {
		JNIMethod method;
		String cName;
		String special;
		StructInfo struct;
		Kind returnKind;
		List<Param> params = new ArrayList<>();
		int firstVariadic = -1;
		boolean dynamic, critical, arena;
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
			for (StructInfo info : structs.values()) {
				if (info.clazz.getSimpleName().equals(structName)) {
					plan.special = "sizeof";
					plan.struct = info;
					return null;
				}
			}
		}

		plan.dynamic = method.getFlag(FLAG_DYNAMIC);
		Function function = null;
		if (plan.dynamic) {
			plan.returnKind = javaKind(returnType);
		} else {
			function = ctypes.getFunction(plan.cName);
			if (function == null) return "no C function declaration (macro or custom C): " + plan.cName;
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
			} else if (p.type.isPrimitive()) {
				p.mode = "value";
				if (!(p.kind.isInteger() || p.kind == Kind.PTR || p.kind == Kind.F32 || p.kind == Kind.F64)) {
					return "C parameter type: " + (function != null && !variadic ? function.params.get(i) : cast);
				}
			} else {
				if (param.getFlag(FLAG_STRUCT)) return "struct by value: " + p.type.getSimpleName();
				if (p.kind != Kind.PTR) return "C parameter is not a pointer: " + p.type.getSimpleName();
				if (p.type.isArray()) {
					JNIType component = p.type.getComponentType();
					if (!component.isPrimitive() || component.isType("boolean")) return "array type: " + p.type.getTypeSignature3();
					p.mode = isCritical(param) ? "critical" : "array";
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
		for (int i = 0; i < plan.params.size(); i++) {
			if (i != 0) output(", ");
			Param p = plan.params.get(i);
			switch (p.mode) {
				case "sentinel": output("0L"); break;
				case "value": output(toC(p.type.getName(), p.kind, "arg" + i)); break;
				case "critical": output("FFM.heap(arg" + i + ")"); break;
				default: output("lparg" + i);
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
			} else if (p.mode.equals("struct")) {
				outputln("\t\tif (arg" + i + " != null) " + p.struct.helper() + "." + p.struct.clazz.getSimpleName() + "_read(lparg" + i + ", arg" + i + ");");
			}
		}
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
		output("(\"");
		output(plan.cName);
		output("\", ");
		StringBuilder layouts = new StringBuilder();
		for (Param p : plan.params) {
			if (layouts.length() != 0) layouts.append(", ");
			boolean segment = p.mode.equals("array") || p.mode.equals("string") || p.mode.equals("struct") || p.mode.equals("critical");
			layouts.append(segment ? "ADDRESS" : layout(p.kind));
		}
		if (plan.returnKind == Kind.VOID) {
			output("FunctionDescriptor.ofVoid(" + layouts + ")");
		} else {
			output("FunctionDescriptor.of(" + layout(plan.returnKind) + (layouts.length() != 0 ? ", " : "") + layouts + ")");
		}
		if (plan.firstVariadic != -1) output(", Linker.Option.firstVariadicArg(" + plan.firstVariadic + ")");
		if (plan.critical) output(", Linker.Option.critical(true)");
		outputln(");");
		outputln("}");
	}

	void generateSpecial(Plan plan) {
		String struct = plan.struct != null ? plan.struct.helper() + "." + plan.struct.clazz.getSimpleName() : null;
		switch (plan.special) {
			case "address":
				outputln("\treturn FFM.address(\"" + plan.cName + "\");");
				break;
			case "sizeof":
				outputln("\treturn (int) " + struct + "_SIZEOF;");
				break;
			case "memmove_write":
				outputln("\tif (arg1 != null) " + struct + "_write(FFM.segment(arg0, " + struct + "_SIZEOF), arg1);");
				break;
			case "memmove_read":
				outputln("\tif (arg0 != null) " + struct + "_read(FFM.segment(arg1, " + struct + "_SIZEOF), arg0);");
				break;
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
