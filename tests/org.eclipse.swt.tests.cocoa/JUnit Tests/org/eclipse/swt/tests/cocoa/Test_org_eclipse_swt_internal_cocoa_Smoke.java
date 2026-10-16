/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.cocoa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.swt.internal.cocoa.OS;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the Cocoa JNI/binding layer {@code org.eclipse.swt.internal.cocoa}.
 * <p>
 * Loads and initializes every class of the package and touches {@link OS}, so
 * that a missing class, native library or Objective-C class/selector/protocol
 * lookup fails fast (for example after the PI constants in {@code OS.<clinit>}
 * have been pruned or regenerated). It does not need a Display.
 * </p>
 */
public class Test_org_eclipse_swt_internal_cocoa_Smoke {

	private static final String PACKAGE = "org.eclipse.swt.internal.cocoa";

	/** Constants that legitimately resolve to 0 on some systems. */
	private static final Set<String> OPTIONAL_CONSTANTS = Set.of("class_JRSAppKitAWT");

	@Test
	public void test_OS_classInitialization() throws Exception {
		Class<?> os = Class.forName(PACKAGE + ".OS", true, OS.class.getClassLoader());
		assertEquals(OS.class, os);
		assertTrue(OS.VERSION > 0, "macOS version was not determined");
	}

	@Test
	public void test_allClassesLoadAndInitialize() throws Exception {
		TreeSet<String> names = listClassNames();
		// The package has hundreds of binding classes; guard against listing nothing.
		assertTrue(names.size() > 100, "Found only " + names.size() + " classes in " + PACKAGE);
		List<String> failures = new ArrayList<>();
		for (String name : names) {
			try {
				Class.forName(name, true, OS.class.getClassLoader());
			} catch (Throwable t) { // includes LinkageError / ExceptionInInitializerError
				failures.add(name + ": " + t);
			}
		}
		assertTrue(failures.isEmpty(), "Classes failing to load/initialize:\n" + String.join("\n", failures));
	}

	/**
	 * Every {@code OS.class_*} and {@code OS.sel_*} constant must have been
	 * resolved by the Objective-C runtime (non-zero).
	 * <p>
	 * {@code OS.protocol_*} constants are not checked: informal protocols (for
	 * example {@code NSMenuValidation}) and removed Web protocols legitimately
	 * resolve to 0. {@code class_JRSAppKitAWT} only exists inside a JDK that
	 * ships it.
	 * </p>
	 */
	@Test
	public void test_OS_objcConstantsResolved() throws Exception {
		List<String> unresolved = new ArrayList<>();
		int checked = 0;
		for (Field f : OS.class.getDeclaredFields()) {
			String n = f.getName();
			if (!Modifier.isStatic(f.getModifiers()) || f.getType() != long.class) {
				continue;
			}
			if (!(n.startsWith("class_") || n.startsWith("sel_"))) {
				continue;
			}
			f.setAccessible(true);
			checked++;
			if (f.getLong(null) == 0 && !OPTIONAL_CONSTANTS.contains(n)) {
				unresolved.add(n);
			}
		}
		assertTrue(checked > 100, "No class_/protocol_/sel_ constants found in OS");
		assertTrue(unresolved.isEmpty(), "Unresolved Objective-C constants in OS: " + unresolved);
	}

	@Test
	public void test_OS_basicRuntimeLookup() {
		assertNotEquals(0, OS.objc_getClass("NSObject"));
		assertNotEquals(0, OS.class_NSString);
		assertNotEquals(0, OS.sel_UTF8String);
	}

	private static TreeSet<String> listClassNames() throws IOException, URISyntaxException {
		String path = PACKAGE.replace('.', '/');
		TreeSet<String> names = new TreeSet<>();
		ClassLoader loader = OS.class.getClassLoader();
		Enumeration<URL> urls = loader.getResources(path);
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			if ("file".equals(url.getProtocol())) {
				File[] files = new File(url.toURI()).listFiles();
				if (files != null) {
					for (File file : files) {
						addClassName(names, file.getName());
					}
				}
			} else if ("jar".equals(url.getProtocol())) {
				JarURLConnection connection = (JarURLConnection) url.openConnection();
				connection.setUseCaches(false);
				try (JarFile jar = connection.getJarFile()) {
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						String entry = entries.nextElement().getName();
						if (entry.startsWith(path + "/") && entry.indexOf('/', path.length() + 1) < 0) {
							addClassName(names, entry.substring(path.length() + 1));
						}
					}
				}
			}
		}
		if (names.isEmpty()) {
			// Fall back to the code source of OS (host bundle jar or classes directory)
			URL location = OS.class.getProtectionDomain().getCodeSource().getLocation();
			File file = new File(location.toURI());
			if (file.isFile()) {
				try (JarFile jar = new JarFile(file)) {
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						String entry = entries.nextElement().getName();
						if (entry.startsWith(path + "/") && entry.indexOf('/', path.length() + 1) < 0) {
							addClassName(names, entry.substring(path.length() + 1));
						}
					}
				}
			} else {
				File[] files = new File(file, path).listFiles();
				if (files != null) {
					for (File f : files) {
						addClassName(names, f.getName());
					}
				}
			}
		}
		return names;
	}

	private static void addClassName(TreeSet<String> names, String fileName) {
		if (fileName.endsWith(".class") && !fileName.equals("package-info.class")) {
			names.add(PACKAGE + "." + fileName.substring(0, fileName.length() - ".class".length()));
		}
	}
}
