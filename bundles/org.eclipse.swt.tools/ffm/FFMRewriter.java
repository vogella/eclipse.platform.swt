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
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Turns the native declarations of SWT into calls of their FFM implementation, in place.
 *
 * It runs as a source file, so a build can apply the switch without compiling the generator:
 *
 * <pre>
 * java FFMRewriter.java &lt;supported.txt&gt; &lt;sourceRoot&gt; &lt;implementation.java&gt;...
 * </pre>
 *
 * Keeping this out of the committed sources means the branch never edits OS.java and friends,
 * which every other change to them would otherwise conflict with.
 */
public class FFMRewriter {

	static final String MODIFIERS = "(?:public|protected|private|static|final|synchronized|strictfp)";
	static final Pattern NATIVE = Pattern.compile("((?:" + MODIFIERS + "\\s+)*)native\\s+((?:" + MODIFIERS + "\\s+)*)([\\w\\[\\]]+)\\s*(?:/\\*[^*]*\\*/\\s*)?(\\w+)\\s*\\(([^)]*)\\)\\s*;");
	static final Pattern PARAM = Pattern.compile("(.+?)\\s*\\b(\\w+)\\s*((?:\\[\\s*\\])*)", Pattern.DOTALL);
	static final Pattern IMPLEMENTATION = Pattern.compile("^\\tpublic static (?:synchronized |final )*[\\w\\[\\]]+ (\\w+)\\(", Pattern.MULTILINE);

	/** Classes whose natives are implemented by a hand written FFM class instead of generated code. */
	static final Map<String, String> HANDWRITTEN = Map.of(
		"org.eclipse.swt.internal.Callback", "org.eclipse.swt.internal.ffm.FFMCallback",
		"org.eclipse.swt.awt.SWT_AWT", "org.eclipse.swt.internal.ffm.FFMAwt");
	static final Pattern LOAD_LIBRARY = Pattern.compile("Library\\.loadLibrary\\s*\\(\\s*\"swt[\\w-]*\"\\s*\\)\\s*;");

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.err.println("usage: FFMRewriter <supported.txt> <sourceRoot> <implementation.java>...");
			System.exit(2);
		}
		Set<String> supported = new HashSet<>(Files.readAllLines(Paths.get(args[0])));
		Map<String, String> implementations = implementations(Arrays.copyOfRange(args, 2, args.length));
		Path root = Paths.get(args[1]);
		int count = 0, files = 0;
		List<String> remaining = new ArrayList<>();
		try (Stream<Path> tree = Files.walk(root)) {
			for (Path file : tree.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
				String source = Files.readString(file);
				if (!source.contains(" native ")) continue;
				String relative = root.relativize(file).toString();
				String className = relative.substring(0, relative.length() - ".java".length()).replace(File.separatorChar, '.');
				String rewritten = rewrite(source, className, supported, implementations);
				if (rewritten == null) continue;
				Files.writeString(file, rewritten);
				count += countOf(source) - countOf(rewritten);
				files++;
				Matcher left = NATIVE.matcher(rewritten);
				while (left.find()) remaining.add(className.substring(className.lastIndexOf('.') + 1) + "." + left.group(4));
			}
		}
		System.out.println("FFMRewriter: " + count + " natives in " + files + " files below " + root);
		if (!remaining.isEmpty()) {
			System.out.println("FFMRewriter: " + remaining.size() + " natives stay on JNI, GTK4 ones and any added since report-gtk was generated");
			List<String> unknown = remaining.stream().filter(n -> !n.matches(".*\\.(gdk_(surface|event|popup|texture|clipboard|cursor_new_from_texture|display_get_monitor_at_surface|x11_surface|scroll_event|key_event|button_event|crossing_event|focus_event)\\w*|swt_fixed_(add|remove)|swt_scaled_paintable_new|content_providers_\\w+)")).collect(Collectors.toList());
			if (!unknown.isEmpty()) System.out.println("FFMRewriter: not yet generated, running on JNI: " + String.join(", ", unknown));
		}
	}

	static int countOf(String source) {
		Matcher m = NATIVE.matcher(source);
		int count = 0;
		while (m.find()) count++;
		return count;
	}

	static String rewrite(String source, String className, Set<String> supported, Map<String, String> implementations) {
		String handwritten = HANDWRITTEN.get(className);
		Matcher m = NATIVE.matcher(source);
		StringBuilder result = new StringBuilder();
		boolean changed = false;
		while (m.find()) {
			String returnType = m.group(3), name = m.group(4), parameters = m.group(5);
			List<String> types = new ArrayList<>(), names = new ArrayList<>();
			for (String parameter : parameters.split(",")) {
				Matcher pm = PARAM.matcher(parameter.trim());
				if (!pm.matches()) continue;
				types.add(pm.group(1) + pm.group(3).replaceAll("\\s", ""));
				names.add(pm.group(2));
			}
			String target;
			String implementation = implementations.get(name);
			if (handwritten != null) {
				target = handwritten;
			} else if (implementation != null) {
				target = implementation;
			} else if (supported.contains(key(className, name, types))) {
				target = className.substring(className.lastIndexOf('.') + 1) + "_FFM";
			} else {
				continue;
			}
			String call = target + "." + name + "(" + String.join(", ", names) + ")";
			String body = returnType.equals("void") ? call + ";" : "return " + call + ";";
			String modifiers = (m.group(1) + m.group(2)).replaceAll("\\s+", " ").trim();
			m.appendReplacement(result, Matcher.quoteReplacement(modifiers + " " + returnType + " " + name + "(" + parameters + ") { " + body + " }"));
			changed = true;
		}
		if (!changed) return null;
		m.appendTail(result);
		// A class keeps loading its JNI library while it has natives left, for example one a merged
		// pull request adds that this list does not know yet, so that it links instead of failing.
		if (NATIVE.matcher(result).find()) return result.toString();
		return LOAD_LIBRARY.matcher(result).replaceAll("/* FFM: no JNI library needed */");
	}

	static String key(String className, String methodName, List<String> parameterTypes) {
		StringBuilder b = new StringBuilder(className).append('#').append(methodName).append('(');
		for (int i = 0; i < parameterTypes.size(); i++) {
			if (i > 0) b.append(',');
			String type = parameterTypes.get(i).replace(" ", "");
			b.append(type.substring(type.lastIndexOf('.') + 1));
		}
		return b.append(')').toString();
	}

	/** The natives a hand written FFM class implements, read from its public static methods. */
	static Map<String, String> implementations(String[] files) throws IOException {
		Map<String, String> result = new HashMap<>();
		for (String file : files) {
			String source = Files.readString(Paths.get(file));
			String name = Paths.get(file).getFileName().toString().replace(".java", "");
			String packageName = source.replaceAll("(?s).*?package\\s+([\\w.]+);.*", "$1");
			Matcher m = IMPLEMENTATION.matcher(source);
			while (m.find()) result.put(m.group(1), packageName + "." + name);
		}
		return result;
	}
}
