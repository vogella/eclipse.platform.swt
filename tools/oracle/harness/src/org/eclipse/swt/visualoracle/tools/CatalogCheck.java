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
package org.eclipse.swt.visualoracle.tools;

import java.io.PrintStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;

/**
 * Exercises the discovered specimen catalog: presence of every family
 * module, unique well-formed ids, and the determinism requirement, proven by
 * capturing every specimen three times in one process and requiring
 * byte-identical PNGs at the declared preferred size.
 */
public final class CatalogCheck {

	/** The modules this task contributes; extend when families are added. */
	/**
	 * Where the catalog's module sources live, used to cross-check discovery
	 * against what is actually on disk. Deliberately a directory scan and not a
	 * hardcoded class list: widget families are added by several agents in
	 * parallel, and a shared list would make every one of them conflict.
	 */
	private static final String CATALOG_SOURCE_DIR =
			"tools/oracle/harness/src/org/eclipse/swt/visualoracle/catalog";

	private static final String ID_PATTERN = "[a-z0-9]+(\\.[a-z0-9]+)+";

	private CatalogCheck() {
	}

	/** Discovery, uniqueness, id shape, sizes, family coverage. */
	public static void checkDiscovery() {
		SpecimenCatalog catalog = SpecimenCatalog.discover();
		List<Specimen> all = catalog.all();
		require(!all.isEmpty(), "discovered an empty catalog");

		Set<String> seen = new HashSet<>();
		for (Specimen specimen : all) {
			String id = specimen.id();
			require(id != null && id.matches(ID_PATTERN),
					"id '" + id + "' is not lowercase dot-separated");
			require(seen.add(id), "duplicate specimen id '" + id + "'");
			Point size = specimen.preferredSize();
			require(size != null && size.x > 0 && size.y > 0,
					id + " has no positive preferred size: " + size);
			String family = id.substring(0, id.indexOf('.'));
			require(catalog.family(family).contains(specimen),
					id + " not found under its own family '" + family + "'");
			require(catalog.byId(id).isPresent(), id + " lost during discovery");
		}

		for (String simpleName : moduleSourceNames()) {
			SpecimenModule module;
			try {
				Class<?> type = Class.forName(
						"org.eclipse.swt.visualoracle.catalog." + simpleName);
				module = (SpecimenModule) type.getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot instantiate " + simpleName
						+ "; a catalog module needs a public no-argument constructor", e);
			}
			for (Specimen specimen : module.specimens()) {
				if (!catalog.byId(specimen.id()).isPresent()) {
					throw new AssertionError("catalog did not discover "
							+ specimen.id() + " from " + simpleName);
				}
			}
		}
	}

	/**
	 * Every {@code *Module.java} in the catalog source directory. Scanning
	 * sources rather than compiled classes keeps this an independent check on
	 * discovery: a module that exists but is never discovered fails here.
	 */
	private static List<String> moduleSourceNames() {
		File dir = new File(CATALOG_SOURCE_DIR);
		require(dir.isDirectory(), "catalog source directory not found: " + dir.getAbsolutePath()
				+ " (run selftest from the repository root)");
		String[] files = dir.list((d, name) -> name.endsWith("Module.java"));
		require(files != null && files.length > 0, "no *Module.java found in " + CATALOG_SOURCE_DIR);
		List<String> names = new ArrayList<>();
		for (String file : files) {
			names.add(file.substring(0, file.length() - ".java".length()));
		}
		names.sort(null);
		return names;
	}

	/** Triple render per specimen: byte-identical PNGs at preferred size. */
	public static void checkTripleRender(Display display, Backend backend, RenderEnv env,
			PrintStream out) {
		require(display != null && !display.isDisposed(), "no live Display for catalog renders");
		CaptureRuntime capture = new CaptureRuntime();
		int count = 0;
		for (Specimen specimen : SpecimenCatalog.discover().all()) {
			require(backend.supports(specimen),
					"backend '" + backend.id() + "' does not support '" + specimen.id() + "'");
			byte[] firstHash = null;
			for (int round = 0; round < 3; round++) {
				CapturedImage image = capture.capture(specimen, backend, env);
				Point wanted = specimen.preferredSize();
				require(image.width() == wanted.x && image.height() == wanted.y,
						specimen.id() + " captured " + image.width() + "x" + image.height()
								+ ", preferred is " + wanted.x + "x" + wanted.y);
				byte[] hash = sha256(image.pngBytes());
				if (firstHash == null) {
					firstHash = hash;
				} else {
					require(Arrays.equals(firstHash, hash),
							specimen.id() + " rendered differently on capture " + (round + 1)
									+ "; it is not deterministic");
				}
			}
			count++;
			out.println("      catalog " + specimen.id() + ": deterministic ("
					+ specimen.preferredSize().x + "x" + specimen.preferredSize().y + ")");
		}
		out.println("      catalog triple-rendered " + count + " specimens");
	}

	private static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
