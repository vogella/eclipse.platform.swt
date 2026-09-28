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
package org.eclipse.swt.internal.ffm;

import java.lang.foreign.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * The Windows DLLs the generated bindings link against, one lookup per DLL named by the import libraries of the
 * native build or by the <code>name_LIB</code> macro of a dynamic function. Loaded on first use.
 */
public final class FFMLibraries {

	static final Map<String, SymbolLookup> LOOKUPS = new ConcurrentHashMap<>();

	/** Finds nothing, the way the JNI glue skips a dynamic function whose DLL does not load. */
	static final SymbolLookup MISSING = name -> Optional.empty();

	private FFMLibraries() {
	}

	public static SymbolLookup get(String dll) {
		return LOOKUPS.computeIfAbsent(dll.toLowerCase(Locale.ROOT), name -> {
			try {
				return SymbolLookup.libraryLookup(name, Arena.global());
			} catch (IllegalArgumentException e) {
				return MISSING;
			}
		});
	}
}
