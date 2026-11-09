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

import static java.lang.foreign.ValueLayout.*;

import java.io.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;

import org.eclipse.swt.internal.win32.*;

/**
 * Serves the resources of swt.rc when no SWT library is loaded, so <code>GetLibraryHandle</code> is 0:
 * the manifest that binds comctl32 version 6 (<code>CreateActCtx</code>) and the icons 101 to 104 of
 * the search and cancel buttons of <code>Text</code> (<code>LoadImage</code>). Both come from class
 * path resources next to this class; everything else goes to the generated binding.
 */
public final class FFMResources {

	static final String IMPLEMENTS = "org.eclipse.swt.internal.win32.OS";

	static final int ACTCTX_FLAG_RESOURCE_NAME_VALID = 0x8;
	static final int ACTCTX_FLAG_HMODULE_VALID = 0x80;
	static final int MANIFEST_RESOURCE_ID = 2;
	static final int IMAGE_ICON = 1;
	static final int LR_LOADFROMFILE = 0x10;
	static final String[] ICONS = {"search.ico", "cancel.ico", "search_darktheme.ico", "cancel_darktheme.ico"};

	private static final class MH_LookupIconIdFromDirectoryEx {
		static final MethodHandle MH = FFM.downcall(FFMLibraries.get("user32.dll"), "LookupIconIdFromDirectoryEx",
			FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}

	private static final class MH_CreateIconFromResourceEx {
		static final MethodHandle MH = FFM.downcall(FFMLibraries.get("user32.dll"), "CreateIconFromResourceEx",
			FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
	}

	private FFMResources() {
	}

	/** Activates the SWT manifest from a temporary file when there is no module to read it from. */
	public static long CreateActCtx(ACTCTX pActCtx) {
		if (pActCtx == null || pActCtx.hModule != 0 || (pActCtx.dwFlags & ACTCTX_FLAG_HMODULE_VALID) == 0
				|| (pActCtx.dwFlags & ACTCTX_FLAG_RESOURCE_NAME_VALID) == 0 || pActCtx.lpResourceName != MANIFEST_RESOURCE_ID) {
			return OS_FFM.CreateActCtx(pActCtx);
		}
		Path manifest = null;
		try (Arena arena = Arena.ofConfined()) {
			manifest = Files.createTempFile("swt", ".manifest");
			try (InputStream in = resource("swt.manifest")) {
				Files.copy(in, manifest, StandardCopyOption.REPLACE_EXISTING);
			}
			ACTCTX fromFile = new ACTCTX();
			fromFile.cbSize = pActCtx.cbSize;
			fromFile.dwFlags = pActCtx.dwFlags & ~(ACTCTX_FLAG_HMODULE_VALID | ACTCTX_FLAG_RESOURCE_NAME_VALID);
			fromFile.lpSource = arena.allocateFrom(manifest.toString(), StandardCharsets.UTF_16LE).address();
			return OS_FFM.CreateActCtx(fromFile);
		} catch (IOException e) {
			return -1; // INVALID_HANDLE_VALUE, as a failed CreateActCtx
		} finally {
			if (manifest != null) {
				try {
					Files.deleteIfExists(manifest);
				} catch (IOException e) {
					manifest.toFile().deleteOnExit();
				}
			}
		}
	}

	/** Loads the icons of swt.rc from the .ico files, choosing the size the way a resource load does. */
	public static long LoadImage(long hinst, long lpszName, int uType, int cxDesired, int cyDesired, int fuLoad) {
		if (hinst != 0 || uType != IMAGE_ICON || (fuLoad & LR_LOADFROMFILE) != 0 || lpszName < 101 || lpszName > 104) {
			return OS_FFM.LoadImage(hinst, lpszName, uType, cxDesired, cyDesired, fuLoad);
		}
		byte[] file;
		try (InputStream in = resource(ICONS[(int) lpszName - 101])) {
			file = in.readAllBytes();
		} catch (IOException e) {
			return 0;
		}
		try (Arena arena = Arena.ofConfined()) {
			// ICONDIR entries are 16 bytes ending in the image offset, GRPICONDIR ones 14 ending in an id
			ByteBuffer ico = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
			int count = Short.toUnsignedInt(ico.getShort(4));
			MemorySegment directory = arena.allocate(6 + 14L * count, 2);
			MemorySegment.copy(file, 0, directory, JAVA_BYTE, 0, 6);
			for (int i = 0; i < count; i++) {
				MemorySegment.copy(file, 6 + 16 * i, directory, JAVA_BYTE, 6 + 14L * i, 12);
				directory.set(JAVA_SHORT_UNALIGNED, 6 + 14L * i + 12, (short) (i + 1));
			}
			int id = (int) MH_LookupIconIdFromDirectoryEx.MH.invokeExact(directory, 1, cxDesired, cyDesired, fuLoad);
			if (id < 1 || id > count) return 0;
			int size = ico.getInt(6 + 16 * (id - 1) + 8), offset = ico.getInt(6 + 16 * (id - 1) + 12);
			MemorySegment bits = arena.allocate(size, 8);
			MemorySegment.copy(file, offset, bits, JAVA_BYTE, 0, size);
			return (long) MH_CreateIconFromResourceEx.MH.invokeExact(bits, size, 1, 0x00030000, cxDesired, cyDesired, fuLoad);
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	static InputStream resource(String name) throws IOException {
		InputStream in = FFMResources.class.getResourceAsStream(name);
		if (in == null) throw new FileNotFoundException(name);
		return in;
	}
}
