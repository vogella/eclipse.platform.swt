/*******************************************************************************
 * Copyright (c) 2026 SWT Visual Oracle contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.visualoracle.spi;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers every {@link SpecimenModule} on the classpath and exposes the
 * specimens they contribute.
 *
 * Discovery scans the compiled {@code org.eclipse.swt.visualoracle.catalog}
 * package for classes whose name ends in {@code Module}. This deliberately
 * avoids a shared registration file: several agents add widget families in
 * parallel, and a central list would make every one of them conflict with the
 * others.
 * <p>
 * Part of the frozen visual oracle SPI, see {@code docs/visual-oracle/SPI.md}.
 */
public final class SpecimenCatalog {

	private static final String CATALOG_PACKAGE = "org.eclipse.swt.visualoracle.catalog";

	private final Map<String, Specimen> byId;

	private SpecimenCatalog(Map<String, Specimen> byId) {
		this.byId = byId;
	}

	/** Discovers all modules and validates the combined catalog. */
	public static SpecimenCatalog discover() {
		List<SpecimenModule> modules = loadModules();
		modules.sort(Comparator.comparing(SpecimenModule::family));

		Map<String, Specimen> specimens = new LinkedHashMap<>();
		Map<String, String> owner = new HashMap<>();
		for (SpecimenModule module : modules) {
			for (Specimen specimen : module.specimens()) {
				String id = specimen.id();
				if (id == null || id.isBlank()) {
					throw new IllegalStateException(module.family() + " contributed a specimen with no id");
				}
				String previous = owner.put(id, module.family());
				if (previous != null) {
					throw new IllegalStateException(
							"duplicate specimen id " + id + ", contributed by " + previous + " and " + module.family());
				}
				specimens.put(id, specimen);
			}
		}
		return new SpecimenCatalog(specimens);
	}

	/** Every specimen, ordered by contributing family then contribution order. */
	public List<Specimen> all() {
		return List.copyOf(byId.values());
	}

	public Optional<Specimen> byId(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	/** Specimens whose id starts with {@code family + "."}. */
	public List<Specimen> family(String family) {
		String prefix = family + ".";
		List<Specimen> result = new ArrayList<>();
		for (Specimen specimen : byId.values()) {
			if (specimen.id().startsWith(prefix)) {
				result.add(specimen);
			}
		}
		return result;
	}

	private static List<SpecimenModule> loadModules() {
		List<SpecimenModule> modules = new ArrayList<>();
		for (File classFile : catalogClassFiles()) {
			String name = classFile.getName();
			if (!name.endsWith("Module.class") || name.contains("$")) {
				continue;
			}
			String className = CATALOG_PACKAGE + "." + name.substring(0, name.length() - ".class".length());
			try {
				Class<?> type = Class.forName(className, false, SpecimenCatalog.class.getClassLoader());
				if (!SpecimenModule.class.isAssignableFrom(type) || type.isInterface()) {
					continue;
				}
				modules.add((SpecimenModule) type.getDeclaredConstructor().newInstance());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("cannot instantiate specimen module " + className
						+ "; it needs a public no-argument constructor", e);
			}
		}
		return modules;
	}

	private static List<File> catalogClassFiles() {
		URL url = SpecimenCatalog.class.getClassLoader().getResource(CATALOG_PACKAGE.replace('.', '/'));
		if (url == null || !"file".equals(url.getProtocol())) {
			return List.of();
		}
		File[] files = new File(url.getPath()).listFiles();
		if (files == null) {
			return List.of();
		}
		List<File> result = new ArrayList<>(List.of(files));
		result.sort(Comparator.comparing(File::getName));
		return result;
	}
}
