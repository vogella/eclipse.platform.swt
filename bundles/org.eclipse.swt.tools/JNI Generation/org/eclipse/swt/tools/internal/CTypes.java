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

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * C function prototypes and typedefs read from a clang AST dump
 * (<code>clang -fsyntax-only -Xclang -ast-dump</code>) of a generated JNI C file.
 */
public class CTypes {

	/** ABI relevant classification of a C type. */
	public enum Kind {
		VOID, I8, U8, I16, U16, I32, U32, I64, U64, F32, F64, PTR, STRUCT, UNKNOWN;

		public boolean isInteger() {
			return ordinal() >= I8.ordinal() && ordinal() <= U64.ordinal();
		}

		public boolean isSigned() {
			return this == I8 || this == I16 || this == I32 || this == I64;
		}
	}

	public static class Function {
		public String name;
		public String returnType;
		public List<String> params = new ArrayList<>();
		public boolean variadic;
		public boolean inline;
	}

	static final Pattern DECL = Pattern.compile("^[|`]-(FunctionDecl|TypedefDecl) 0x\\S+ .*?(\\w+) '([^']*)'(?::'([^']*)')?(.*)$");

	static final Pattern RECORD = Pattern.compile("^[|`]-RecordDecl 0x\\S+ .* ((?:struct|union) \\w+) definition$");
	static final Pattern FIELD = Pattern.compile("^[| ] [|`]-FieldDecl 0x\\S+ .*?(\\w+) '[^']*'(?::'[^']*')?$");
	static final Pattern BIT_WIDTH = Pattern.compile("^[| ] [| ] [|`]-ConstantExpr .*");

	final Map<String, Function> functions = new HashMap<>();
	final Map<String, String> typedefs = new HashMap<>();
	final Set<String> bitfields = new HashSet<>();

	public CTypes(String astFile) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(astFile), StandardCharsets.UTF_8)) {
			String line, record = null, field = null;
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("|-") && !line.startsWith("`-")) {
					if (record == null) continue;
					Matcher f = FIELD.matcher(line);
					if (f.matches()) {
						field = f.group(1);
					} else if (field != null && BIT_WIDTH.matcher(line).matches()) {
						bitfields.add(record + "." + field);
					}
					continue;
				}
				Matcher r = RECORD.matcher(line);
				record = r.matches() ? r.group(1) : null;
				field = null;
				if (record != null) continue;
				Matcher m = DECL.matcher(line);
				if (!m.matches()) continue;
				String name = m.group(2), sugared = m.group(3), rest = m.group(5);
				if (m.group(1).equals("TypedefDecl")) {
					typedefs.put(name, sugared.startsWith("enum ") ? "enum " + name : sugared);
					continue;
				}
				int close = sugared.lastIndexOf(')');
				int open = matchingOpen(sugared, close);
				if (open == -1) continue;
				Function function = new Function();
				function.name = name;
				Function previous = functions.get(name);
				function.inline = rest.contains("inline") || rest.contains("static") || (previous != null && previous.inline);
				String prefix = sugared.substring(0, open).trim();
				String params = sugared.substring(open + 1, close);
				if (prefix.endsWith(")")) {
					// function returning a function pointer: RET (*(PARAMS))(POINTER_PARAMS)
					String inner = prefix.substring(matchingOpen(prefix, prefix.length() - 1) + 1, prefix.length() - 1).trim();
					if (!inner.startsWith("*")) continue;
					inner = inner.substring(1).trim();
					function.returnType = "void *";
					params = inner.substring(1, inner.length() - 1);
				} else {
					function.returnType = prefix;
				}
				for (String param : splitParams(params)) {
					if (param.equals("...")) function.variadic = true;
					else if (!param.equals("void")) function.params.add(param);
				}
				functions.put(name, function);
			}
		}
	}

	static List<String> splitParams(String list) {
		List<String> result = new ArrayList<>();
		int depth = 0, start = 0;
		for (int i = 0; i < list.length(); i++) {
			char c = list.charAt(i);
			if (c == '(' || c == '[') depth++;
			else if (c == ')' || c == ']') depth--;
			else if (c == ',' && depth == 0) {
				result.add(list.substring(start, i).trim());
				start = i + 1;
			}
		}
		if (!list.isBlank()) result.add(list.substring(start).trim());
		return result;
	}

	static int matchingOpen(String type, int close) {
		if (close == -1) return -1;
		int depth = 0;
		for (int i = close; i >= 0; i--) {
			char c = type.charAt(i);
			if (c == ')') depth++;
			else if (c == '(' && --depth == 0) return i;
		}
		return -1;
	}

	/** Whether <code>field</code> of the struct named by a typedef or <code>struct</code> tag is a bit-field. */
	public boolean isBitfield(String structType, String field) {
		String type = structType;
		for (int i = 0; i < 20 && !type.startsWith("struct ") && !type.startsWith("union "); i++) {
			type = typedefs.get(type);
			if (type == null) return false;
		}
		return bitfields.contains(type + "." + field);
	}

	public Function getFunction(String name) {
		return functions.get(name);
	}

	public Kind classify(String type) {
		return classify(type, 0);
	}

	Kind classify(String type, int depth) {
		if (type == null || depth > 20) return Kind.UNKNOWN;
		String t = type.replaceAll("\\b(const|volatile|restrict|__restrict|register)\\b", "").replaceAll("\\s+", " ").trim();
		while (t.startsWith("(") && t.endsWith(")")) t = t.substring(1, t.length() - 1).trim();
		if (t.contains("*") || t.contains("[")) return Kind.PTR;
		if (t.startsWith("enum ")) return Kind.I32;
		if (t.startsWith("struct ") || t.startsWith("union ")) return Kind.STRUCT;
		switch (t) {
			case "void": return Kind.VOID;
			case "char": case "signed char": return Kind.I8;
			case "unsigned char": case "_Bool": case "jboolean": return Kind.U8;
			case "short": case "short int": case "signed short": return Kind.I16;
			case "unsigned short": case "unsigned short int": return Kind.U16;
			case "int": case "signed": case "signed int": case "jint": return Kind.I32;
			case "unsigned": case "unsigned int": return Kind.U32;
			case "long": case "long int": case "long long": case "long long int": case "jlong": return Kind.I64;
			case "unsigned long": case "unsigned long int": case "unsigned long long": case "unsigned long long int": return Kind.U64;
			case "float": case "jfloat": return Kind.F32;
			case "double": case "jdouble": return Kind.F64;
			default:
		}
		String target = typedefs.get(t);
		if (target == null) return Kind.UNKNOWN;
		if (target.equals("enum " + t)) return Kind.I32;
		return classify(target, depth + 1);
	}
}
