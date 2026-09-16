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
 * generate &lt;outputRoot&gt; &lt;reportDir&gt; (&lt;mainClass&gt; &lt;ast&gt; &lt;layout&gt;)...
 * rewrite  &lt;supported.txt&gt; &lt;sourceRoot&gt; &lt;outputRoot&gt;
 * </pre>
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
		for (String line : Files.readAllLines(Paths.get(file))) {
			int eq = line.indexOf('=');
			if (eq > 0) layout.put(line.substring(0, eq), line.substring(eq + 1));
		}
		return layout;
	}

	static void generate(String outputRoot, String reportDir, List<String[]> units) throws IOException {
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
		Map<String, String> unsupported = new TreeMap<>();
		Set<String> supported = new TreeSet<>();
		StringBuilder summary = new StringBuilder();
		for (int u = 0; u < units.size(); u++) {
			JNIGeneratorApp app = apps.get(u);
			FFMGenerator generator = new FFMGenerator(new CTypes(units.get(u)[1]), structs);
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
		Map<String, Long> reasons = unsupported.values().stream()
			.collect(Collectors.groupingBy(r -> r.substring(0, r.indexOf(':')), TreeMap::new, Collectors.counting()));
		summary.append(String.format("%nTotal FFM %d, JNI %d%n%nKept on JNI by reason:%n", supported.size(), unsupported.size()));
		reasons.forEach((r, c) -> summary.append(String.format("%6d  %s%n", c, r)));
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
	static final Map<String, String> HANDWRITTEN = Map.of(
		"org.eclipse.swt.internal.Callback", "org.eclipse.swt.internal.ffm.FFMCallback");

	/** Single natives implemented in Java instead of calling their C counterpart, as class#method. */
	static final Map<String, String> HANDWRITTEN_METHODS = Map.ofEntries(
		Map.entry("org.eclipse.swt.internal.gtk.OS#g_utf16_strlen", "org.eclipse.swt.internal.ffm.FFMUtf16"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#g_utf16_pointer_to_offset", "org.eclipse.swt.internal.ffm.FFMUtf16"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#g_utf16_offset_to_pointer", "org.eclipse.swt.internal.ffm.FFMUtf16"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#g_utf16_offset_to_utf8_offset", "org.eclipse.swt.internal.ffm.FFMUtf16"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#g_utf8_offset_to_utf16_offset", "org.eclipse.swt.internal.ffm.FFMUtf16"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#imContextNewProc_CALLBACK", "org.eclipse.swt.internal.ffm.FFMConstructorProc"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#imContextLast", "org.eclipse.swt.internal.ffm.FFMConstructorProc"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#pangoLayoutNewProc_CALLBACK", "org.eclipse.swt.internal.ffm.FFMConstructorProc"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#pangoFontFamilyNewProc_CALLBACK", "org.eclipse.swt.internal.ffm.FFMConstructorProc"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#pangoFontFaceNewProc_CALLBACK", "org.eclipse.swt.internal.ffm.FFMConstructorProc"),
		Map.entry("org.eclipse.swt.internal.gtk.OS#printerOptionWidgetNewProc_CALLBACK", "org.eclipse.swt.internal.ffm.FFMConstructorProc"));

	/** Copies the Java sources below <code>sourceRoot</code>, delegating every supported native to its FFM implementation. */
	static void rewrite(String supportedFile, String sourceRoot, String outputRoot) throws IOException {
		Set<String> supported = new HashSet<>(Files.readAllLines(Paths.get(supportedFile)));
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
					String handwrittenMethod = HANDWRITTEN_METHODS.get(className + "#" + name);
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
				List<String[]> units = new ArrayList<>();
				for (int i = 3; i + 2 < args.length; i += 3) units.add(new String[] {args[i], args[i + 1], args[i + 2]});
				generate(args[1], args[2], units);
				break;
			case "rewrite":
				rewrite(args[1], args[2], args[3]);
				break;
			default:
				System.err.println("Usage: probe|generate|rewrite, see Javadoc of " + FFMGeneratorApp.class.getName());
				System.exit(1);
		}
	}
}
