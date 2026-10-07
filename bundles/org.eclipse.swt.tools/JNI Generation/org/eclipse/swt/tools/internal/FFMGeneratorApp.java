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
import java.util.stream.*;

import org.eclipse.swt.tools.internal.FFMGenerator.*;

/**
 * Command line driver for the FFM backend of the JNI generator. Run from <code>bundles/org.eclipse.swt.tools</code>.
 *
 * <pre>
 * probe    &lt;mainClass&gt; &lt;ast&gt; &lt;out.c&gt;
 * generate &lt;outputRoot&gt; &lt;reportDir&gt; [--imports &lt;imports.txt&gt;] (&lt;mainClass&gt; &lt;ast&gt; &lt;layout&gt;)... [--gtk4 (&lt;mainClass&gt; &lt;ast&gt; &lt;layout&gt;)...]
 * rewrite  &lt;supported.txt&gt; &lt;sourceRoot&gt; &lt;outputRoot&gt;
 * </pre>
 *
 * A <code>clang -E -dM</code> dump next to an AST, with the extension <code>.macros</code>, resolves function renaming
 * macros. <code>--imports</code> lists <code>function dll</code> pairs from the Windows import libraries, and an AST
 * and layout of <code>-</code> stand for a natives class that is not generated at all. The units after
 * <code>--gtk4</code> are the GTK4 compile of the units before it: a natives class that has none is generated from
 * the GTK3 compile, one that only has a GTK4 compile (<code>GTK4</code>) from that, and one with both serves
 * GTK3 and GTK4 and gets a run time choice wherever the C ABI or a struct layout differs.
 */
public class FFMGeneratorApp {

	static JNIGeneratorApp load(String mainClass) {
		JNIGeneratorApp app = new JNIGeneratorApp();
		app.setMainClassName(mainClass);
		return app;
	}

	static String render(JNIGenerator generator, Runnable body) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream stream = new PrintStream(out, true, StandardCharsets.UTF_8);
		generator.setOutput(stream);
		generator.setDelimiter("\n");
		body.run();
		stream.flush();
		return out.toString(StandardCharsets.UTF_8);
	}

	static void probe(String mainClass, String ast, String outFile) throws IOException {
		JNIGeneratorApp app = load(mainClass);
		FFMProbeGenerator generator = new FFMProbeGenerator(new CTypes(ast));
		generator.setMainClass(app.getMainClass());
		generator.setClasses(app.getStructureClasses(app.getClasses()));
		generator.setMetaData(app.getMetaData());
		Files.writeString(Paths.get(outFile), render(generator, generator::generate));
	}

	static Map<String, String> readLayout(String file) throws IOException {
		Map<String, String> layout = new HashMap<>();
		if (file.equals("-")) return layout;
		for (String line : Files.readAllLines(Paths.get(file))) {
			int eq = line.indexOf('=');
			if (eq > 0) layout.put(line.substring(0, eq), line.substring(eq + 1));
		}
		return layout;
	}

	static CTypes readCTypes(String ast) throws IOException {
		if (ast.equals("-")) return null;
		Path macros = Paths.get(ast.replaceAll("\\.ast$", "") + ".macros");
		return new CTypes(ast, Files.exists(macros) ? macros.toString() : null);
	}

	/** Reads <code>function dll</code> lines, the first library naming a function wins as with the linker. */
	static Map<String, String> readImports(String file) throws IOException {
		Map<String, String> imports = new HashMap<>();
		for (String line : Files.readAllLines(Paths.get(file))) {
			String[] parts = line.trim().split("\\s+");
			if (parts.length == 2) imports.putIfAbsent(parts[0], parts[1]);
		}
		return imports;
	}

	static void generate(String outputRoot, String reportDir, String importsFile, List<String[]> units, List<String[]> units4) throws IOException {
		Map<String, String[]> alternatives = new HashMap<>();
		for (String[] unit : units4) alternatives.put(unit[0], unit);
		Set<String> known = new HashSet<>();
		for (String[] unit : units) known.add(unit[0]);
		// a natives class that only the GTK4 build compiles is generated from that compile alone
		Set<String> only4 = new HashSet<>();
		for (String[] unit : units4) {
			if (known.add(unit[0])) {
				units.add(unit);
				only4.add(unit[0]);
			}
		}
		Map<String, String> layoutDifferences = new TreeMap<>();
		Map<String, String> imports = importsFile != null ? readImports(importsFile) : null;
		Map<String, String> symbols = new TreeMap<>();
		Map<String, StructInfo> structs = new HashMap<>();
		Map<String, List<StructInfo>> byPackage = new TreeMap<>();
		List<JNIGeneratorApp> apps = new ArrayList<>();
		for (String[] unit : units) {
			JNIGeneratorApp app = load(unit[0]);
			apps.add(app);
			Map<String, String> layout = readLayout(unit[2]);
			for (JNIClass clazz : app.getStructureClasses(app.getClasses())) {
				String size = layout.get(clazz.getSimpleName());
				if (size == null) continue;
				StructInfo info = new StructInfo();
				info.clazz = clazz;
				info.packageName = FFMGenerator.packageOf(clazz.getName());
				info.size = Long.parseLong(size);
				String prefix = clazz.getSimpleName() + ".";
				layout.forEach((k, v) -> {
					if (k.startsWith(prefix)) info.fields.put(k.substring(prefix.length()), v);
				});
				structs.put(clazz.getName(), info);
				byPackage.computeIfAbsent(info.packageName, k -> new ArrayList<>()).add(info);
			}
		}
		for (String[] unit : units4) {
			JNIGeneratorApp app = load(unit[0]);
			Map<String, String> layout = readLayout(unit[2]);
			for (JNIClass clazz : app.getStructureClasses(app.getClasses())) {
				String size = layout.get(clazz.getSimpleName());
				StructInfo info = structs.get(clazz.getName());
				if (size == null || info == null) continue;
				info.size4 = Long.parseLong(size);
				info.fields4 = new HashMap<>();
				String prefix = clazz.getSimpleName() + ".";
				layout.forEach((k, v) -> {
					if (k.startsWith(prefix)) info.fields4.put(k.substring(prefix.length()), v);
				});
				if (info.differs()) {
					Set<String> names = new TreeSet<>(info.fields.keySet());
					names.addAll(info.fields4.keySet());
					names.removeIf(n -> Objects.equals(info.fields.get(n), info.fields4.get(n)));
					layoutDifferences.put(clazz.getName(), "size " + info.size + "/" + info.size4 + ", fields " + names);
				}
			}
		}
		Map<String, String> unsupported = new TreeMap<>();
		Set<String> supported = new TreeSet<>();
		Map<String, String> differences = new TreeMap<>();
		int[] sameAbi = new int[1];
		StringBuilder summary = new StringBuilder();
		for (int u = 0; u < units.size(); u++) {
			JNIGeneratorApp app = apps.get(u);
			FFMGenerator generator = new FFMGenerator(readCTypes(units.get(u)[1]), structs);
			String[] unit4 = alternatives.get(units.get(u)[0]);
			if (unit4 != null) generator.setGtk4(only4.contains(units.get(u)[0]) ? null : readCTypes(unit4[1]), only4.contains(units.get(u)[0]));
			generator.setImports(imports);
			Map<String, String> extras = new HashMap<>();
			readLayout(units.get(u)[2]).forEach((k, v) -> {
				if (k.startsWith("EXTRA.")) extras.put(k.substring("EXTRA.".length()), v);
			});
			generator.setExtras(extras);
			generator.setMetaData(app.getMetaData());
			generator.setMainClass(app.getMainClass());
			for (JNIClass clazz : app.getNativesClasses(app.getClasses())) {
				generator.setClasses(new JNIClass[] {clazz});
				int before = generator.getSupported().size(), beforeUnsupported = generator.getUnsupported().size();
				String source = render(generator, () -> generator.generate(clazz));
				int count = generator.getSupported().size() - before;
				int left = generator.getUnsupported().size() - beforeUnsupported;
				summary.append(String.format("%-55s FFM %5d  JNI %5d%n", clazz.getName(), count, left));
				if (count > 0) write(outputRoot, clazz.getName() + FFMGenerator.SUFFIX, source);
			}
			unsupported.putAll(generator.getUnsupported());
			supported.addAll(generator.getSupported());
			generator.getDifferences().forEach((k, v) -> differences.put(k, v));
			sameAbi[0] += generator.getSameAbi();
			symbols.putAll(generator.getSymbols());
		}
		for (int u = 0; u < units.size(); u++) {
			Map<String, String> layout = readLayout(units.get(u)[2]);
			Map<String, String> extras = new TreeMap<>();
			layout.forEach((k, v) -> {
				if (k.startsWith("EXTRA.")) extras.put(k.substring("EXTRA.".length()), v);
			});
			if (extras.isEmpty()) continue;
			String[] unit4 = alternatives.get(units.get(u)[0]);
			Map<String, String> extras4 = new TreeMap<>();
			if (unit4 != null && !only4.contains(unit4[0])) {
				readLayout(unit4[2]).forEach((k, v) -> {
					if (k.startsWith("EXTRA.")) extras4.put(k.substring("EXTRA.".length()), v);
				});
				extras4.forEach((k, v) -> {
					if (extras.containsKey(k) && !extras.get(k).equals(v)) layoutDifferences.put("EXTRA." + k, extras.get(k) + "/" + v);
				});
			}
			String packageName = FFMGenerator.packageOf(apps.get(u).getMainClass().getName());
			StringBuilder source = new StringBuilder();
			source.append("package ").append(packageName).append(";\n\n");
			source.append("/* Note: This file was auto-generated by ").append(FFMGenerator.class.getName()).append(" */\n");
			source.append("/* DO NOT EDIT - your changes will be lost. */\n\n");
			if (!extras4.isEmpty()) source.append("import org.eclipse.swt.internal.ffm.*;\n\n");
			source.append("/** Sizes and field offsets of C types that have no Java struct class. */\n");
			source.append("public final class Extra").append(FFMGenerator.SUFFIX).append(" {\n\n");
			Map<String, String> all = new TreeMap<>(extras);
			// -1 where the type does not exist in GTK3
			extras4.forEach((k, v) -> all.putIfAbsent(k, null));
			all.forEach((k, v) -> {
				String v4 = extras4.get(k);
				String name = k.replace('.', '_').toUpperCase(Locale.ROOT);
				int dot = k.indexOf('.');
				boolean only3 = v != null && v4 == null && !extras4.isEmpty()
					&& FFMProbeGenerator.gtk3Only(dot < 0 ? k : k.substring(0, dot), dot < 0 ? "" : k.substring(dot + 1));
				String value = v == null ? "FFM.GTK4 ? " + v4 + "L : -1L" : only3 ? "FFM.GTK4 ? -1L : " + v + "L"
					: v4 != null && !v4.equals(v) ? "FFM.GTK4 ? " + v4 + "L : " + v + "L" : v + "L";
				source.append("\tpublic static final long ").append(name).append(" = ").append(value).append(";\n");
			});
			source.append("}\n");
			write(outputRoot, packageName + ".Extra" + FFMGenerator.SUFFIX, source.toString());
		}
		FFMGenerator structGenerator = new FFMGenerator(null, structs);
		for (Map.Entry<String, List<StructInfo>> entry : byPackage.entrySet()) {
			List<StructInfo> infos = entry.getValue();
			infos.sort(Comparator.comparing(i -> i.clazz.getSimpleName()));
			write(outputRoot, entry.getKey() + "." + FFMGenerator.STRUCTS, render(structGenerator, () -> structGenerator.generateStructs(entry.getKey(), infos)));
		}
		Files.createDirectories(Paths.get(reportDir));
		Files.write(Paths.get(reportDir, "supported.txt"), supported);
		Files.write(Paths.get(reportDir, "unsupported.txt"), unsupported.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(Collectors.toList()));
		if (!units4.isEmpty()) {
			List<String> lines = new ArrayList<>();
			lines.add("Struct layouts and extras that differ between GTK3 and GTK4 (chosen at run time):");
			layoutDifferences.forEach((k, v) -> lines.add("  " + k + "\t" + v));
			lines.add("");
			lines.add("Natives by what the GTK3 and GTK4 compile declare:");
			differences.forEach((k, v) -> lines.add("  " + k + "\t" + v));
			Files.write(Paths.get(reportDir, "gtk4-differences.txt"), lines);
		}
		if (imports != null) {
			// "-": not in an import library, resolved through the default lookup (C runtime)
			Files.write(Paths.get(reportDir, "symbols.txt"), symbols.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(Collectors.toList()));
		}
		Map<String, Long> reasons = unsupported.values().stream()
			.collect(Collectors.groupingBy(r -> r.substring(0, r.indexOf(':')), TreeMap::new, Collectors.counting()));
		summary.append(String.format("%nTotal FFM %d, JNI %d%n%nKept on JNI by reason:%n", supported.size(), unsupported.size()));
		reasons.forEach((r, c) -> summary.append(String.format("%6d  %s%n", c, r)));
		if (!units4.isEmpty()) {
			Map<String, Long> kinds = differences.values().stream()
				.collect(Collectors.groupingBy(v -> v.startsWith("GTK3 only") ? "GTK3 only (no declaration in the GTK4 headers)" : v.startsWith("GTK4 only") ? "GTK4 only" : "C ABI differs, run time choice", TreeMap::new, Collectors.counting()));
			summary.append(String.format("%nGTK3 and GTK4 (gtk4-differences.txt), %d struct layouts differ:%n", layoutDifferences.size()));
			kinds.forEach((r, c) -> summary.append(String.format("%6d  %s%n", c, r)));
			summary.append(String.format("%6d  same C ABI in both%n", sameAbi[0]));
		}
		Files.writeString(Paths.get(reportDir, "summary.txt"), summary.toString());
		System.out.print(summary);
	}

	static void write(String outputRoot, String qualifiedName, String source) throws IOException {
		Path path = Paths.get(outputRoot, qualifiedName.replace('.', '/') + ".java");
		Files.createDirectories(path.getParent());
		Files.writeString(path, source);
	}

	static final Pattern PARAM = Pattern.compile("(.+?)\\s*\\b(\\w+)\\s*((?:\\[\\s*\\])*)", Pattern.DOTALL);
	static final String MODIFIERS = "(?:public|protected|private|static|final|synchronized|strictfp)";
	static final Pattern NATIVE = Pattern.compile("((?:" + MODIFIERS + "\\s+)*)native\\s+((?:" + MODIFIERS + "\\s+)*)([\\w\\[\\]]+)\\s*(?:/\\*[^*]*\\*/\\s*)?(\\w+)\\s*\\(([^)]*)\\)\\s*;");

	/** Classes whose natives are implemented by a hand written FFM class instead of generated code. */
	static final Pattern LOAD_LIBRARY = Pattern.compile("Library\\.loadLibrary\\s*\\(\\s*\"(swt[\\w-]*)\"\\s*\\)\\s*;");

	/** OS asks FFM instead of Library for the GTK libraries, so that a missing one fails it as a missing swt-pi3 or swt-pi4 does. */
	static String loadReplacement(String library) {
		return switch (library) {
			case "swt-pi3" -> "org.eclipse.swt.internal.ffm.FFM.loadGtk(false);";
			case "swt-pi4" -> "org.eclipse.swt.internal.ffm.FFM.loadGtk(true);";
			default -> "/* FFM: no JNI library needed */";
		};
	}

	static final Map<String, String> HANDWRITTEN = Map.of(
		"org.eclipse.swt.internal.Callback", "org.eclipse.swt.internal.ffm.FFMCallback",
		"org.eclipse.swt.awt.SWT_AWT", "org.eclipse.swt.internal.ffm.FFMAwt");

	static final Pattern IMPLEMENTATION = Pattern.compile("^\\tpublic static (?:synchronized |final )*[\\w\\[\\]]+ (\\w+)\\(", Pattern.MULTILINE);
	static final Pattern IMPLEMENTS = Pattern.compile("static final String IMPLEMENTS = \"([\\w.]+)\";");

	/**
	 * Maps a native name to the hand written FFM class implementing it, read from the public static
	 * methods of the given Java sources, so that adding an implementation needs no list to be updated.
	 */
	static Map<String, String> implementations(String[] files) throws IOException {
		Map<String, String> result = new HashMap<>();
		for (String file : files) {
			String source = Files.readString(Paths.get(file));
			String name = Paths.get(file).getFileName().toString().replace(".java", "");
			String packageName = source.replaceAll("(?s).*?package\\s+([\\w.]+);.*", "$1");
			// a class naming the natives class it implements replaces only the natives of that class
			Matcher target = IMPLEMENTS.matcher(source);
			String prefix = target.find() ? target.group(1) + "#" : "";
			Matcher m = IMPLEMENTATION.matcher(source);
			while (m.find()) result.put(prefix + m.group(1), packageName + "." + name);
		}
		return result;
	}

	/** Copies the Java sources below <code>sourceRoot</code>, delegating every supported native to its FFM implementation. */
	static void rewrite(String supportedFile, String sourceRoot, String outputRoot, String[] implementationFiles) throws IOException {
		Set<String> supported = new HashSet<>(Files.readAllLines(Paths.get(supportedFile)));
		Map<String, String> implementations = implementations(implementationFiles);
		Path root = Paths.get(sourceRoot);
		int[] count = new int[1];
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
				String source = Files.readString(file);
				if (!source.contains(" native ")) continue;
				String relative = root.relativize(file).toString();
				String className = relative.substring(0, relative.length() - ".java".length()).replace(File.separatorChar, '.');
				String handwritten = HANDWRITTEN.get(className);
				Matcher m = NATIVE.matcher(source);
				StringBuilder result = new StringBuilder();
				boolean changed = false;
				while (m.find()) {
					String returnType = m.group(3), name = m.group(4), parameters = m.group(5);
					List<String> types = new ArrayList<>(), names = new ArrayList<>();
					for (String param : parameters.split(",")) {
						Matcher pm = PARAM.matcher(param.trim());
						if (!pm.matches()) continue;
						types.add(pm.group(1) + pm.group(3).replaceAll("\\s", ""));
						names.add(pm.group(2));
					}
					String target;
					String handwrittenMethod = implementations.getOrDefault(className + "#" + name, implementations.get(name));
					if (handwritten != null) {
						target = handwritten;
					} else if (handwrittenMethod != null) {
						target = handwrittenMethod;
					} else if (supported.contains(FFMGenerator.key(className, name, types))) {
						target = FFMGenerator.simpleName(className) + FFMGenerator.SUFFIX;
					} else {
						continue;
					}
					String call = target + "." + name + "(" + String.join(", ", names) + ")";
					String body = returnType.equals("void") ? call + ";" : "return " + call + ";";
					String modifiers = (m.group(1) + m.group(2)).replaceAll("\\s+", " ").trim();
					m.appendReplacement(result, Matcher.quoteReplacement(modifiers + " " + returnType + " " + name + "(" + parameters + ") { " + body + " }"));
					changed = true;
					count[0]++;
				}
				if (!changed) continue;
				m.appendTail(result);
				// nothing in this class reaches the JNI library any more
				String rewritten = LOAD_LIBRARY.matcher(result.toString()).replaceAll(load -> loadReplacement(load.group(1)));
				result = new StringBuilder(rewritten);
				Path out = Paths.get(outputRoot).resolve(relative);
				Files.createDirectories(out.getParent());
				Files.writeString(out, result.toString());
			}
		}
		System.out.println("Delegated " + count[0] + " natives below " + sourceRoot);
	}

	public static void main(String[] args) throws IOException {
		JNIGeneratorApp.USE_AST = true;
		switch (args.length > 0 ? args[0] : "") {
			case "probe":
				probe(args[1], args[2], args[3]);
				break;
			case "generate":
				List<String[]> units = new ArrayList<>(), units4 = new ArrayList<>();
				int first = 3;
				String imports = null;
				if (args.length > 4 && args[3].equals("--imports")) {
					imports = args[4];
					first = 5;
				}
				List<String[]> target = units;
				for (int i = first; i < args.length;) {
					if (args[i].equals("--gtk4")) {
						target = units4;
						i++;
					} else {
						target.add(new String[] {args[i], args[i + 1], args[i + 2]});
						i += 3;
					}
				}
				generate(args[1], args[2], imports, units, units4);
				break;
			case "rewrite":
				rewrite(args[1], args[2], args[3], Arrays.copyOfRange(args, 4, args.length));
				break;
			default:
				System.err.println("Usage: probe|generate|rewrite, see Javadoc of " + FFMGeneratorApp.class.getName());
				System.exit(1);
		}
	}
}
