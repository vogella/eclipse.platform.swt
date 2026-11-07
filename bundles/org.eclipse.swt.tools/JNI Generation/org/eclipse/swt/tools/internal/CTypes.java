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
	/** A global variable, such as the string constants of Cocoa, with the storage class in the rest of the line. */
	static final Pattern VARIABLE = Pattern.compile("^[|`]-VarDecl 0x\\S+ .*?(\\w+) '([^']*)'(?::'([^']*)')?(.*)$");
	/** An enum with a fixed underlying type, <code>NS_ENUM(NSInteger, ...)</code>, which decides how it is passed. */
	static final Pattern ENUM = Pattern.compile("^[|`]-EnumDecl 0x\\S+ .*?(\\w+) '([^']*)'(?::'([^']*)')?$");

	/** A named or, as in <code>typedef struct {...} NAME</code>, anonymous struct definition. */
	static final Pattern RECORD = Pattern.compile("^[|`]-RecordDecl 0x\\S+ .* ((?:struct|union)(?: \\w+)?) definition(?: [\\w-]+)*$");
	static final Pattern FIELD = Pattern.compile("^[| ] [|`]-FieldDecl 0x\\S+ .*?(\\w+) '[^']*'(?::'[^']*')?$");
	static final Pattern BIT_WIDTH = Pattern.compile("^[| ] [| ] [|`]-ConstantExpr .*");

	/** Calling convention and other attributes that clang-cl prints into function types. */
	static final Pattern ATTRIBUTE = Pattern.compile("\\s*__attribute__\\(\\((?:[^()]|\\([^()]*\\))*\\)\\)");
	/** <code>#define A B</code> */
	static final Pattern MACRO_ALIAS = Pattern.compile("^#define (\\w+) (\\w+)$");
	/** <code>#define A(x, y) B((x), (y))</code>, forwarding its arguments unchanged. */
	static final Pattern MACRO_FORWARD = Pattern.compile("^#define (\\w+)\\(([\\w, ]*)\\) (\\w+)\\((.*)\\)$");
	/** <code>#define name_LIB "library"</code>, the library the JNI glue loads a dynamic function from. */
	static final Pattern MACRO_LIBRARY = Pattern.compile("^#define (\\w+)_LIB \"([^\"]+)\"$");

	final Map<String, Function> functions = new HashMap<>();
	final Map<String, String> typedefs = new HashMap<>();
	final Set<String> bitfields = new HashSet<>();
	final Map<String, String> aliases = new HashMap<>();
	final Map<String, String> libraries = new HashMap<>();
	/** Global variables by name: the desugared type and whether they are <code>extern</code>, so that they have a symbol. */
	final Map<String, String[]> variables = new HashMap<>();
	final Map<String, String> enums = new HashMap<>();
	/** Objective-C: <code>id</code>, <code>SEL</code>, <code>Class</code> and <code>BOOL</code> are known types. */
	boolean objc;
	/** C <code>long</code> is 32 bits (Windows), derived from <code>size_t</code>. */
	final boolean llp64;

	public CTypes(String astFile) throws IOException {
		this(astFile, null);
	}

	/**
	 * @param macroFile optional <code>clang -E -dM</code> output of the same unit, used to resolve
	 * 	macros that rename a function, such as the <code>A</code>/<code>W</code> variants of Windows
	 */
	public CTypes(String astFile, String macroFile) throws IOException {
		parseAst(astFile);
		if (macroFile != null) parseMacros(macroFile);
		String size = "size_t";
		for (int i = 0; i < 20 && typedefs.containsKey(size); i++) size = typedefs.get(size);
		llp64 = size.equals("unsigned long long");
	}

	void parseMacros(String macroFile) throws IOException {
		// string macros of the SDK headers are not always valid UTF-8
		for (String line : new String(Files.readAllBytes(Paths.get(macroFile)), StandardCharsets.UTF_8).split("\\R")) {
			line = line.trim();
			Matcher m = MACRO_LIBRARY.matcher(line);
			if (m.matches()) {
				libraries.put(m.group(1), m.group(2));
				continue;
			}
			m = MACRO_ALIAS.matcher(line);
			if (m.matches()) {
				aliases.put(m.group(1), m.group(2));
				continue;
			}
			m = MACRO_FORWARD.matcher(line);
			if (m.matches() && forwards(m.group(2), m.group(4))) aliases.put(m.group(1), m.group(3));
		}
	}

	/** Whether the arguments of a macro body are the macro parameters in order, each at most in parentheses. */
	static boolean forwards(String params, String args) {
		List<String> p = splitParams(params), a = splitParams(args);
		if (p.size() != a.size()) return false;
		for (int i = 0; i < p.size(); i++) {
			String arg = a.get(i);
			while (arg.startsWith("(") && arg.endsWith(")")) arg = arg.substring(1, arg.length() - 1).trim();
			if (!arg.equals(p.get(i))) return false;
		}
		return true;
	}

	void parseAst(String astFile) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(astFile), StandardCharsets.UTF_8)) {
			String line, record = null, field = null;
			// bit-fields of an anonymous struct, named by the typedef that follows it
			List<String> anonymousBits = new ArrayList<>();
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("|-") && !line.startsWith("`-")) {
					if (record == null) continue;
					Matcher f = FIELD.matcher(line);
					if (f.matches()) {
						field = f.group(1);
					} else if (field != null && BIT_WIDTH.matcher(line).matches()) {
						if (record.indexOf(' ') == -1) anonymousBits.add(field);
						else bitfields.add(record + "." + field);
					}
					continue;
				}
				List<String> pendingBits = new ArrayList<>(anonymousBits);
				anonymousBits.clear();
				Matcher r = RECORD.matcher(line);
				record = r.matches() ? r.group(1) : null;
				field = null;
				if (record != null) continue;
				Matcher m = DECL.matcher(line);
				if (!m.matches()) {
					Matcher v = VARIABLE.matcher(line);
					if (v.matches()) variables.put(v.group(1), new String[] {v.group(3) != null ? v.group(3) : v.group(2), v.group(4).contains(" extern") ? "extern" : ""});
					Matcher e = ENUM.matcher(line);
					if (e.matches()) enums.put(e.group(1), e.group(3) != null ? e.group(3) : e.group(2));
					continue;
				}
				String name = m.group(2), sugared = ATTRIBUTE.matcher(m.group(3)).replaceAll(""), rest = m.group(5);
				if (m.group(1).equals("TypedefDecl")) {
					typedefs.put(name, sugared.startsWith("enum ") ? "enum " + name : sugared);
					if (sugared.startsWith("struct ") || sugared.startsWith("union ")) {
						for (String bit : pendingBits) bitfields.add(sugared + "." + bit);
					}
					continue;
				}
				// an implicitly declared function has no real prototype, so binding it would guess
				if (rest.contains("implicit")) continue;
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

	/** Whether the AST has an <code>extern</code> variable of that name. */
	public boolean hasSymbolVariable(String name) {
		String[] variable = variables.get(name);
		return variable != null && variable[1].equals("extern");
	}

	/** The type of the global variable, or <code>null</code>. */
	public String getVariableType(String name) {
		String[] variable = variables.get(name);
		return variable != null ? variable[0] : null;
	}

	public void setObjc(boolean objc) {
		this.objc = objc;
	}

	public Function getFunction(String name) {
		return functions.get(name);
	}

	/** The function a call of <code>name</code> reaches after macro expansion, or <code>null</code>. */
	public String resolve(String name) {
		String current = name;
		for (int i = 0; i < 10 && current != null; i++) {
			if (functions.containsKey(current)) return current;
			current = aliases.get(current);
		}
		return null;
	}

	/** The function a call of <code>name</code> reaches when the macros are expanded first, as the compiler does, or <code>null</code>. */
	public String resolveMacros(String name) {
		String current = name;
		for (int i = 0; i < 10 && aliases.containsKey(current); i++) current = aliases.get(current);
		return functions.containsKey(current) ? current : null;
	}

	/** The library a <code>flags=dynamic</code> function is loaded from (<code>name_LIB</code>), or <code>null</code>. */
	public String getLibrary(String name) {
		return libraries.get(name);
	}

	public boolean isLLP64() {
		return llp64;
	}

	public Kind classify(String type) {
		return classify(type, 0);
	}

	Kind classify(String type, int depth) {
		if (type == null || depth > 20) return Kind.UNKNOWN;
		String t = type.replaceAll("\\b(const|volatile|restrict|__restrict|register)\\b", "").replaceAll("\\s+", " ").trim();
		// nullability and the attribute macros of the SDK headers, such as CF_RELEASES_ARGUMENT, are printed into the type
		if (objc) t = t.replaceAll("\\b(?:_Nullable|_Nonnull|_Null_unspecified|__nullable|__nonnull|(?:CF|OBJC|API|NS)_[A-Z0-9_]+)\\b", "").replaceAll("\\s+", " ").trim();
		while (t.startsWith("(") && t.endsWith(")")) t = t.substring(1, t.length() - 1).trim();
		if (t.contains("*") || t.contains("[")) return Kind.PTR;
		if (t.startsWith("enum ")) return enums.containsKey(t.substring(5)) ? classify(enums.get(t.substring(5)), depth + 1) : Kind.I32;
		if (objc) {
			// the blocks and the types that clang knows without a declaration
			if (t.contains("^")) return Kind.PTR;
			switch (t) {
				case "id": case "SEL": case "Class": return Kind.PTR;
				case "bool": return Kind.U8;
				default:
			}
		}
		if (t.startsWith("struct ") || t.startsWith("union ")) return Kind.STRUCT;
		switch (t) {
			case "void": return Kind.VOID;
			case "char": case "signed char": return Kind.I8;
			case "unsigned char": case "_Bool": case "jboolean": return Kind.U8;
			case "short": case "short int": case "signed short": return Kind.I16;
			case "unsigned short": case "unsigned short int": return Kind.U16;
			case "int": case "signed": case "signed int": case "jint": return Kind.I32;
			case "unsigned": case "unsigned int": return Kind.U32;
			case "long": case "long int": return llp64 ? Kind.I32 : Kind.I64;
			case "unsigned long": case "unsigned long int": return llp64 ? Kind.U32 : Kind.U64;
			case "long long": case "long long int": case "jlong": return Kind.I64;
			case "unsigned long long": case "unsigned long long int": return Kind.U64;
			// predefined sugar of recent clang, pointer sized on every target SWT supports
			case "__size_t": return Kind.U64;
			case "__signed_size_t": case "__ptrdiff_t": return Kind.I64;
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
