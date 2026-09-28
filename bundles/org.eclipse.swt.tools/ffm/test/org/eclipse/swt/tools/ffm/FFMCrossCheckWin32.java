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
package org.eclipse.swt.tools.ffm;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.ole.win32.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.internal.win32.version.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Runs the JNI and the generated FFM implementation of the Win32 natives in one process and compares the results.
 * Run against the build of build-win32.sh with the JNI DLLs on <code>java.library.path</code>.
 * The optional argument is <code>report-win32/symbols.txt</code>, whose symbols are resolved from their DLLs.
 */
public class FFMCrossCheckWin32 {

	static final String[] NATIVES = {
		"org.eclipse.swt.internal.C", "org.eclipse.swt.internal.win32.OS", "org.eclipse.swt.internal.ole.win32.COM",
		"org.eclipse.swt.internal.win32.version.OsVersion",
	};
	static final String[] STRUCT_PACKAGES = {
		"org.eclipse.swt.internal.win32", "org.eclipse.swt.internal.ole.win32", "org.eclipse.swt.internal.win32.version",
	};

	/** DLLs a dynamic function may legitimately miss, as the JNI glue tolerates. */
	static final Set<String> OPTIONAL_LIBRARIES = Set.of("WebView2Loader.dll");

	/** Differences that are not a JNI versus FFM difference. */
	static final Map<String, String> KNOWN = Map.of(
		"sizeof NOTIFYICONDATA", "Java NOTIFYICONDATA.sizeof is NOTIFYICONDATA_V2_SIZE, not the C sizeof");

	static int checks, failures, known;

	static void check(String what, Object jni, Object ffm) {
		checks++;
		if (deepEquals(jni, ffm)) return;
		String reason = KNOWN.get(what);
		if (reason != null) {
			known++;
			System.out.println("KNOWN    " + what + " (JNI " + describe(jni) + ", FFM " + describe(ffm) + "): " + reason);
			return;
		}
		failures++;
		System.out.println("MISMATCH " + what + "\n   JNI: " + describe(jni) + "\n   FFM: " + describe(ffm));
	}

	public static void main(String[] args) throws Exception {
		if (args.length > 0) checkSymbols(args[0]);
		checkLinking();
		checkSizes();
		checkStructs();
		Display display = new Display();
		try {
			checkFunctions(display);
			checkHandwritten(display);
		} finally {
			display.dispose();
		}
		System.out.println(checks + " checks, " + failures + " mismatches, " + known + " known differences");
		System.exit(failures == 0 ? 0 : 1);
	}

	/* ------------------------------------------------------------------ symbols and linking */

	static void checkSymbols(String file) throws Exception {
		int count = 0;
		for (String line : Files.readAllLines(Paths.get(file))) {
			String[] parts = line.split("\t");
			if (parts.length != 2) continue;
			boolean found = parts[1].equals("-") ? Linker.nativeLinker().defaultLookup().find(parts[0]).isPresent()
				: FFMLibraries.get(parts[1]).find(parts[0]).isPresent();
			count++;
			checks++;
			if (!found && !OPTIONAL_LIBRARIES.contains(parts[1])) {
				failures++;
				System.out.println("MISSING  " + parts[0] + " in " + parts[1]);
			}
		}
		System.out.println("Resolved " + count + " symbols");
	}

	/** Initializes every generated holder, which links its downcall handle with the generated descriptor. */
	static void checkLinking() throws Exception {
		int count = 0;
		for (String natives : NATIVES) {
			Class<?> ffm = Class.forName(natives + "_FFM");
			for (Class<?> holder : ffm.getDeclaredClasses()) {
				if (!holder.getSimpleName().startsWith("MH_")) continue;
				checks++;
				count++;
				try {
					Field field = holder.getDeclaredField("MH");
					field.setAccessible(true);
					MethodHandle handle = (MethodHandle) field.get(null);
					if (handle == null) System.out.println("OPTIONAL " + holder.getName() + " not present");
				} catch (Throwable e) {
					failures++;
					System.out.println("LINK     " + holder.getName() + ": " + (e.getCause() != null ? e.getCause() : e));
				}
			}
		}
		System.out.println("Linked " + count + " downcall handles");
	}

	/* ------------------------------------------------------------------ sizes */

	static void checkSizes() throws Exception {
		int count = 0;
		for (String pkg : STRUCT_PACKAGES) {
			Class<?> structs = Class.forName(pkg + ".Structs_FFM");
			for (Field field : structs.getFields()) {
				if (!field.getName().endsWith("_SIZEOF")) continue;
				String name = field.getName().substring(0, field.getName().length() - "_SIZEOF".length());
				Integer jni = jniSizeof(pkg, name);
				if (jni == null) continue;
				check("sizeof " + name, (long) jni, field.getLong(null));
				count++;
			}
		}
		System.out.println("Compared " + count + " struct sizes");
	}

	static Integer jniSizeof(String pkg, String name) throws Exception {
		for (String natives : NATIVES) {
			try {
				Method method = Class.forName(natives).getMethod(name + "_sizeof");
				if (Modifier.isNative(method.getModifiers())) return (Integer) method.invoke(null);
			} catch (NoSuchMethodException e) {
				// declared in another natives class
			}
		}
		try {
			return Class.forName(pkg + "." + name).getField("sizeof").getInt(null);
		} catch (NoSuchFieldException | ClassNotFoundException e) {
			return null;
		}
	}

	/* ------------------------------------------------------------------ struct marshalling */

	/** Reads and writes random data through every JNI <code>MoveMemory</code> of a struct, the generated one and the struct helpers. */
	static void checkStructs() throws Exception {
		int reads = 0, writes = 0;
		for (String natives : NATIVES) {
			Class<?> jniClass = Class.forName(natives);
			Class<?> ffmClass = Class.forName(natives + "_FFM");
			for (Method jni : jniClass.getDeclaredMethods()) {
				if (!jni.getName().equals("MoveMemory") || !Modifier.isNative(jni.getModifiers())) continue;
				Class<?>[] types = jni.getParameterTypes();
				if (types.length != 3) continue;
				boolean read = !types[0].isPrimitive() && !types[0].isArray() && types[1] == long.class;
				boolean write = types[0] == long.class && !types[1].isPrimitive() && !types[1].isArray();
				if (!read && !write) continue;
				Method generated;
				try {
					generated = ffmClass.getMethod(jni.getName(), types);
				} catch (NoSuchMethodException e) {
					continue;
				}
				Class<?> struct = read ? types[0] : types[1];
				Class<?> structs = Class.forName(struct.getPackageName() + ".Structs_FFM");
				long ffmSize = structs.getField(struct.getSimpleName() + "_SIZEOF").getLong(null);
				Integer jniSize = jniSizeof(struct.getPackageName(), struct.getSimpleName());
				int size = (int) (jniSize == null ? ffmSize : Math.min(ffmSize, jniSize));
				Method ffmRead = structs.getMethod(struct.getSimpleName() + "_read", MemorySegment.class, struct);
				Random random = new Random(struct.getName().hashCode());
				for (int round = 0; round < 20; round++) {
					long buffer = C.malloc(ffmSize), other = C.malloc(ffmSize);
					try {
						if (read) {
							byte[] bytes = new byte[(int) ffmSize];
							random.nextBytes(bytes);
							C.memmove(buffer, bytes, ffmSize);
							Object viaJni = struct.getConstructor().newInstance();
							jni.invoke(null, viaJni, buffer, size);
							Object viaFfm = struct.getConstructor().newInstance();
							generated.invoke(null, viaFfm, buffer, size);
							check("read " + struct.getSimpleName() + " round " + round, viaJni, viaFfm);
							Object viaHelper = struct.getConstructor().newInstance();
							ffmRead.invoke(null, FFM.segment(buffer, size), viaHelper);
							check("read helper " + struct.getSimpleName() + " round " + round, viaJni, viaHelper);
							reads++;
						} else {
							Object value = randomStruct(struct, random);
							C.memset(buffer, 0, ffmSize);
							C.memset(other, 0, ffmSize);
							jni.invoke(null, buffer, value, size);
							generated.invoke(null, other, value, size);
							// compared as read back, since JNI copies the padding of an uninitialized C struct
							Object fromJni = struct.getConstructor().newInstance();
							Object fromFfm = struct.getConstructor().newInstance();
							ffmRead.invoke(null, FFM.segment(buffer, size), fromJni);
							ffmRead.invoke(null, FFM.segment(other, size), fromFfm);
							check("write " + struct.getSimpleName() + " round " + round, fromJni, fromFfm);
							writes++;
						}
					} finally {
						C.free(buffer);
						C.free(other);
					}
				}
			}
		}
		System.out.println("Compared " + reads + " struct reads and " + writes + " struct writes");
	}

	static Object randomStruct(Class<?> type, Random random) throws Exception {
		Object value = type.getConstructor().newInstance();
		for (Field field : publicFields(type)) {
			Class<?> t = field.getType();
			if (t == int.class) field.setInt(value, random.nextInt());
			else if (t == long.class) field.setLong(value, random.nextLong());
			else if (t == short.class) field.setShort(value, (short) random.nextInt());
			else if (t == byte.class) field.setByte(value, (byte) random.nextInt());
			else if (t == char.class) field.setChar(value, (char) random.nextInt());
			else if (t == boolean.class) field.setBoolean(value, random.nextBoolean());
			else if (t == double.class) field.setDouble(value, random.nextDouble() * 1e6 - 5e5);
			else if (t == float.class) field.setFloat(value, random.nextFloat() * 1000 - 500);
			else if (t.isArray()) {
				Object array = field.get(value);
				if (array == null) continue;
				for (int i = 0; i < Array.getLength(array); i++) {
					if (t == byte[].class) Array.setByte(array, i, (byte) random.nextInt());
					else if (t == int[].class) Array.setInt(array, i, random.nextInt());
					else if (t == long[].class) Array.setLong(array, i, random.nextLong());
					else if (t == short[].class) Array.setShort(array, i, (short) random.nextInt());
					else if (t == char[].class) Array.setChar(array, i, (char) random.nextInt());
					else if (t == double[].class) Array.setDouble(array, i, random.nextDouble());
					else if (t == float[].class) Array.setFloat(array, i, random.nextFloat());
				}
			} else {
				field.set(value, randomStruct(t, random));
			}
		}
		return value;
	}

	static List<Field> publicFields(Class<?> type) {
		List<Field> fields = new ArrayList<>();
		for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				int mods = field.getModifiers();
				if (Modifier.isPublic(mods) && !Modifier.isStatic(mods) && !Modifier.isFinal(mods)) fields.add(field);
			}
		}
		return fields;
	}

	static boolean deepEquals(Object a, Object b) {
		if (a == null || b == null) return a == b;
		if (a instanceof float[] x && b instanceof float[] y) return Arrays.equals(x, y);
		if (a.getClass().isArray()) return Objects.deepEquals(a, b);
		if (a instanceof Number || a instanceof Boolean || a instanceof Character || a instanceof String) return a.equals(b);
		if (a.getClass() != b.getClass()) return false;
		try {
			for (Field field : publicFields(a.getClass())) {
				if (!deepEquals(field.get(a), field.get(b))) return false;
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return true;
	}

	static String describe(Object value) {
		if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) return String.valueOf(value);
		if (value.getClass().isArray()) return Arrays.deepToString(new Object[] {value});
		StringBuilder b = new StringBuilder(value.getClass().getSimpleName()).append(" {");
		try {
			for (Field field : publicFields(value.getClass())) b.append(' ').append(field.getName()).append('=').append(describe(field.get(value)));
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		return b.append(" }").toString();
	}

	/* ------------------------------------------------------------------ functions */

	static POINT point(int x, int y) {
		POINT p = new POINT();
		p.x = x;
		p.y = y;
		return p;
	}

	static void checkFunctions(Display display) {
		Shell shell = new Shell(display);
		shell.setText("FFM cross check äöü €");
		shell.setBounds(100, 100, 300, 200);
		shell.open();
		while (display.readAndDispatch()) {
			// flush pending events
		}
		int start = checks;
		long handle = shell.handle;

		// scalars
		check("GetSystemMetrics", OS.GetSystemMetrics(OS.SM_CXSCREEN), OS_FFM.GetSystemMetrics(OS.SM_CXSCREEN));
		check("GetCurrentProcessId", OS.GetCurrentProcessId(), OS_FFM.GetCurrentProcessId());
		check("GetCurrentThreadId", OS.GetCurrentThreadId(), OS_FFM.GetCurrentThreadId());
		check("IsWindowVisible", OS.IsWindowVisible(handle), OS_FFM.IsWindowVisible(handle));

		// struct out parameters, char[] in and out
		RECT jniRect = new RECT(), ffmRect = new RECT();
		check("GetWindowRect", OS.GetWindowRect(handle, jniRect), OS_FFM.GetWindowRect(handle, ffmRect));
		check("GetWindowRect rect", jniRect, ffmRect);
		check("GetClientRect", OS.GetClientRect(handle, jniRect), OS_FFM.GetClientRect(handle, ffmRect));
		check("GetClientRect rect", jniRect, ffmRect);
		char[] jniName = new char[256], ffmName = new char[256];
		check("GetClassName", OS.GetClassName(handle, jniName, 256), OS_FFM.GetClassName(handle, ffmName, 256));
		check("GetClassName chars", jniName, ffmName);
		check("GetWindowTextLength", OS.GetWindowTextLength(handle), OS_FFM.GetWindowTextLength(handle));
		char[] jniText = new char[64], ffmText = new char[64];
		check("GetWindowText", OS.GetWindowText(handle, jniText, 64), OS_FFM.GetWindowText(handle, ffmText, 64));
		check("GetWindowText chars", jniText, ffmText);
		byte[] utf8 = "Grüße € 😀".getBytes(StandardCharsets.UTF_8);
		char[] jniWide = new char[32], ffmWide = new char[32];
		check("MultiByteToWideChar", OS.MultiByteToWideChar(OS.CP_UTF8, 0, utf8, utf8.length, jniWide, 32),
			OS_FFM.MultiByteToWideChar(OS.CP_UTF8, 0, utf8, utf8.length, ffmWide, 32));
		check("MultiByteToWideChar chars", jniWide, ffmWide);
		check("GetModuleHandle", OS.GetModuleHandle("user32.dll\0".toCharArray()), OS_FFM.GetModuleHandle("user32.dll\0".toCharArray()));

		// struct pointer as LPARAM
		int WM_GETTEXTLENGTH = 0x000E, EM_GETRECT = 0x00B2;
		check("SendMessage WM_GETTEXTLENGTH", OS.SendMessage(handle, WM_GETTEXTLENGTH, 0, 0), OS_FFM.SendMessage(handle, WM_GETTEXTLENGTH, 0, 0));
		Text edit = new Text(shell, SWT.MULTI | SWT.BORDER);
		edit.setBounds(10, 10, 120, 60);
		RECT jniItem = new RECT(), ffmItem = new RECT();
		check("SendMessage(RECT) EM_GETRECT", OS.SendMessage(edit.handle, EM_GETRECT, 0, jniItem), OS_FFM.SendMessage(edit.handle, EM_GETRECT, 0, ffmItem));
		check("SendMessage(RECT) EM_GETRECT rect", jniItem, ffmItem);
		check("SendMessage(RECT) EM_GETRECT set", true, ffmItem.right > 0);

		// struct by value
		POINT center = point((jniRect.left + jniRect.right) / 2, (jniRect.top + jniRect.bottom) / 2);
		OS.MapWindowPoints(handle, 0, center, 1);
		check("WindowFromPoint", OS.WindowFromPoint(center), OS_FFM.WindowFromPoint(center));
		check("ChildWindowFromPointEx", OS.ChildWindowFromPointEx(handle, point(5, 5), 0), OS_FFM.ChildWindowFromPointEx(handle, point(5, 5), 0));
		POINT jniMapped = point(10, 20), ffmMapped = point(10, 20);
		check("MapWindowPoints", OS.MapWindowPoints(handle, 0, jniMapped, 1), OS_FFM.MapWindowPoints(handle, 0, ffmMapped, 1));
		check("MapWindowPoints point", jniMapped, ffmMapped);
		checkAlphaBlend();
		checkScriptStringOut();

		// last error captured right after the call, even with the JVM running in between
		long badMenu = 0x1234;
		int jniCount = OS.GetMenuItemCount(badMenu);
		int jniError = OS.GetLastError();
		int ffmCount = OS_FFM.GetMenuItemCount(badMenu);
		System.gc();
		int ffmError = OS_FFM.GetLastError();
		check("GetMenuItemCount(invalid)", jniCount, ffmCount);
		check("GetLastError after GetMenuItemCount(invalid) is ERROR_INVALID_MENU_HANDLE", 1401, ffmError);
		System.out.println("INFO     JNI GetLastError after GetMenuItemCount(invalid): " + jniError);
		long[] jniLayouts = new long[16], ffmLayouts = new long[16];
		check("GetKeyboardLayoutList", OS.GetKeyboardLayoutList(16, jniLayouts), OS_FFM.GetKeyboardLayoutList(16, ffmLayouts));
		check("GetKeyboardLayoutList list", jniLayouts, ffmLayouts);

		// dynamic functions
		check("GetDpiForWindow", OS.GetDpiForWindow(handle), OS_FFM.GetDpiForWindow(handle));
		long monitor = OS.MonitorFromWindow(handle, OS.MONITOR_DEFAULTTONEAREST);
		int[] jx = new int[1], jy = new int[1], fx = new int[1], fy = new int[1];
		check("GetDpiForMonitor", OS.GetDpiForMonitor(monitor, 0, jx, jy), OS_FFM.GetDpiForMonitor(monitor, 0, fx, fy));
		check("GetDpiForMonitor dpi", new int[] {jx[0], jy[0]}, new int[] {fx[0], fy[0]});
		OSVERSIONINFOEX jniVersion = new OSVERSIONINFOEX(), ffmVersion = new OSVERSIONINFOEX();
		jniVersion.dwOSVersionInfoSize = ffmVersion.dwOSVersionInfoSize = OSVERSIONINFOEX.sizeof;
		check("RtlGetVersion", OsVersion.RtlGetVersion(jniVersion), OsVersion_FFM.RtlGetVersion(ffmVersion));
		check("RtlGetVersion info", jniVersion, ffmVersion);

		// sizes of C types without a Java struct class
		check("SCRIPT_STRING_ANALYSIS_sizeof", OS.SCRIPT_STRING_ANALYSIS_sizeof(), OS_FFM.SCRIPT_STRING_ANALYSIS_sizeof());
		check("PROPVARIANT_sizeof", OS.PROPVARIANT_sizeof(), OS_FFM.PROPVARIANT_sizeof());
		check("LOGPEN_sizeof", OS.LOGPEN_sizeof(), OS_FFM.LOGPEN_sizeof());
		check("ELEMDESC_sizeof", COM.ELEMDESC_sizeof(), COM_FFM.ELEMDESC_sizeof());
		check("TYPEDESC_sizeof", COM.TYPEDESC_sizeof(), COM_FFM.TYPEDESC_sizeof());

		// COM and the C runtime
		char[] iid = "{00000000-0000-0000-C000-000000000046}\0".toCharArray();
		GUID jniGuid = new GUID(), ffmGuid = new GUID();
		check("CLSIDFromString", COM.CLSIDFromString(iid, jniGuid), COM_FFM.CLSIDFromString(iid, ffmGuid));
		check("CLSIDFromString guid", jniGuid, ffmGuid);
		long jniBstr = COM.SysAllocString("hello\0".toCharArray()), ffmBstr = COM_FFM.SysAllocString("hello\0".toCharArray());
		check("SysStringLen", COM.SysStringLen(jniBstr), COM_FFM.SysStringLen(ffmBstr));
		check("SysStringByteLen", COM.SysStringByteLen(ffmBstr), COM_FFM.SysStringByteLen(jniBstr));
		COM_FFM.SysFreeString(jniBstr);
		COM.SysFreeString(ffmBstr);
		long memory = C_FFM.malloc(16);
		C_FFM.memmove(memory, "abcdef\0".getBytes(StandardCharsets.US_ASCII), 7);
		check("strlen", C.strlen(memory), C_FFM.strlen(memory));
		C_FFM.free(memory);

		System.out.println("Compared " + (checks - start) + " call results");
		shell.dispose();
	}

	/** The macros of FFMWin32Macros and the swt.rc resources FFMResources serves without swt-win32. */
	static void checkHandwritten(Display display) {
		int start = checks;
		check("NOTIFYICONDATA_V2_SIZE", OS.NOTIFYICONDATA_V2_SIZE(), FFMWin32Macros.NOTIFYICONDATA_V2_SIZE());
		check("PTR_sizeof", C.PTR_sizeof(), FFMWin32Macros.PTR_sizeof());
		for (long arg : new long[] {0, 1, 32767, 32768, 65535, 65536, -1}) {
			check("GID_ROTATE_ANGLE_FROM_ARGUMENT(" + arg + ")", OS.GID_ROTATE_ANGLE_FROM_ARGUMENT(arg), FFMWin32Macros.GID_ROTATE_ANGLE_FROM_ARGUMENT(arg));
		}
		GUID a = new GUID(), b = new GUID();
		COM.CLSIDFromString("{00000000-0000-0000-C000-000000000046}\0".toCharArray(), a);
		COM.CLSIDFromString("{00000000-0000-0000-C000-000000000046}\0".toCharArray(), b);
		check("IsEqualGUID equal", COM.IsEqualGUID(a, b), FFMWin32Macros.IsEqualGUID(a, b));
		for (int i = 0; i < 4; i++) {
			GUID c = new GUID();
			COM.CLSIDFromString("{00000000-0000-0000-C000-000000000046}\0".toCharArray(), c);
			switch (i) {
				case 0 -> c.Data1++;
				case 1 -> c.Data2++;
				case 2 -> c.Data3++;
				default -> c.Data4[7]++;
			}
			check("IsEqualGUID differs in part " + i, COM.IsEqualGUID(a, c), FFMWin32Macros.IsEqualGUID(a, c));
		}

		Shell shell = new Shell(display);
		org.eclipse.swt.widgets.Tree tree = new org.eclipse.swt.widgets.Tree(shell, SWT.BORDER);
		tree.setBounds(0, 0, 200, 200);
		org.eclipse.swt.widgets.TreeItem item = new org.eclipse.swt.widgets.TreeItem(tree, 0);
		item.setText("item");
		new org.eclipse.swt.widgets.TreeItem(item, 0).setText("child");
		item.setExpanded(true);
		shell.open();
		long hItem = OS.SendMessage(tree.handle, OS.TVM_GETNEXTITEM, OS.TVGN_ROOT, 0);
		for (boolean code : new boolean[] {true, false}) {
			for (long h : new long[] {hItem, OS.SendMessage(tree.handle, OS.TVM_GETNEXTITEM, OS.TVGN_CHILD, hItem), 0x1234}) {
				RECT jni = new RECT(), ffm = new RECT();
				check("TreeView_GetItemRect(" + code + ")", OS.TreeView_GetItemRect(tree.handle, h, jni, code), FFMWin32Macros.TreeView_GetItemRect(tree.handle, h, ffm, code));
				check("TreeView_GetItemRect(" + code + ") rect", jni, ffm);
			}
		}
		shell.dispose();

		// the icons of swt.rc, from the swt-win32 module and from the .ico files
		for (int id = 101; id <= 104; id++) {
			for (int size : new int[] {16, 20, 24, 32, 40, 48, 64}) {
				long jni = OS.LoadImage(OS.GetLibraryHandle(), id, OS.IMAGE_ICON, size, size, 0);
				long ffm = FFMResources.LoadImage(0, id, OS.IMAGE_ICON, size, size, 0);
				check("LoadImage icon " + id + " size " + size + " loaded", true, jni != 0 && ffm != 0);
				if (jni == 0 || ffm == 0) continue;
				org.eclipse.swt.graphics.Image jniImage = org.eclipse.swt.graphics.Image.win32_new(display, SWT.ICON, jni, 100);
				org.eclipse.swt.graphics.Image ffmImage = org.eclipse.swt.graphics.Image.win32_new(display, SWT.ICON, ffm, 100);
				org.eclipse.swt.graphics.ImageData jniData = jniImage.getImageData(), ffmData = ffmImage.getImageData();
				check("LoadImage icon " + id + " size " + size + " pixels", jniData.data, ffmData.data);
				check("LoadImage icon " + id + " size " + size + " alpha", jniData.alphaData, ffmData.alphaData);
				check("LoadImage icon " + id + " size " + size + " mask", jniData.maskData, ffmData.maskData);
				jniImage.dispose();
				ffmImage.dispose();
			}
		}
		check("LoadImage OEM icon passes through", OS.LoadImage(0, 32512, OS.IMAGE_ICON, 0, 0, OS.LR_SHARED) != 0,
			FFMResources.LoadImage(0, 32512, OS.IMAGE_ICON, 0, 0, OS.LR_SHARED) != 0);

		// the manifest, from a temporary file when there is no module
		ACTCTX context = new ACTCTX();
		context.cbSize = ACTCTX.sizeof;
		context.dwFlags = OS.ACTCTX_FLAG_RESOURCE_NAME_VALID | OS.ACTCTX_FLAG_HMODULE_VALID;
		context.lpResourceName = OS.MANIFEST_RESOURCE_ID;
		long hActCtx = FFMResources.CreateActCtx(context);
		check("CreateActCtx from the manifest file", true, hActCtx != 0 && hActCtx != -1);
		System.out.println("Compared " + (checks - start) + " macro and resource results");
	}

	/** BLENDFUNCTION by value: the same blend into two bitmaps, compared pixel by pixel. */
	static void checkAlphaBlend() {
		long screen = OS.GetDC(0);
		long[] dcs = new long[3], bitmaps = new long[3], old = new long[3];
		for (int i = 0; i < 3; i++) {
			dcs[i] = OS.CreateCompatibleDC(screen);
			bitmaps[i] = OS.CreateCompatibleBitmap(screen, 16, 16);
			old[i] = OS.SelectObject(dcs[i], bitmaps[i]);
			OS.PatBlt(dcs[i], 0, 0, 16, 16, OS.WHITENESS);
		}
		long brush = OS.CreateSolidBrush(0x3080C0);
		RECT rect = new RECT();
		OS.SetRect(rect, 0, 0, 16, 16);
		OS.FillRect(dcs[0], rect, brush);
		BLENDFUNCTION blend = new BLENDFUNCTION();
		blend.BlendOp = OS.AC_SRC_OVER;
		blend.SourceConstantAlpha = (byte) 0x60;
		check("AlphaBlend", OS.AlphaBlend(dcs[1], 0, 0, 16, 16, dcs[0], 0, 0, 16, 16, blend),
			OS_FFM.AlphaBlend(dcs[2], 0, 0, 16, 16, dcs[0], 0, 0, 16, 16, blend));
		check("AlphaBlend pixels", pixels(dcs[1], 16, 16), pixels(dcs[2], 16, 16));
		check("AlphaBlend blended", true, OS.GetPixel(dcs[2], 3, 3) != 0xFFFFFF);
		for (int i = 0; i < 3; i++) {
			OS.SelectObject(dcs[i], old[i]);
			OS.DeleteObject(bitmaps[i]);
			OS.DeleteDC(dcs[i]);
		}
		OS.DeleteObject(brush);
		OS.ReleaseDC(0, screen);
	}

	/** SCRIPT_STRING_ANALYSIS dereferenced from a pointer: the same string drawn into two bitmaps. */
	static void checkScriptStringOut() {
		long screen = OS.GetDC(0);
		char[] text = "FFM äöü".toCharArray();
		long chars = C.malloc(text.length * 2L);
		OS.MoveMemory(chars, text, text.length * 2);
		int[] results = new int[2];
		int[][] pixels = new int[2][];
		for (int i = 0; i < 2; i++) {
			long dc = OS.CreateCompatibleDC(screen), bitmap = OS.CreateCompatibleBitmap(screen, 80, 20);
			long old = OS.SelectObject(dc, bitmap);
			OS.PatBlt(dc, 0, 0, 80, 20, OS.WHITENESS);
			long ssa = C.malloc(OS.SCRIPT_STRING_ANALYSIS_sizeof());
			C.memset(ssa, 0, OS.SCRIPT_STRING_ANALYSIS_sizeof());
			int analysed = OS.ScriptStringAnalyse(dc, chars, text.length, text.length * 3 / 2 + 16, -1, OS.SSA_GLYPHS, 0, null, null, 0, 0, 0, ssa);
			check("ScriptStringAnalyse", OS.S_OK, analysed);
			results[i] = i == 0 ? OS.ScriptStringOut(ssa, 2, 2, 0, null, 0, 0, false) : OS_FFM.ScriptStringOut(ssa, 2, 2, 0, null, 0, 0, false);
			pixels[i] = pixels(dc, 80, 20);
			OS.ScriptStringFree(ssa);
			C.free(ssa);
			OS.SelectObject(dc, old);
			OS.DeleteObject(bitmap);
			OS.DeleteDC(dc);
		}
		C.free(chars);
		OS.ReleaseDC(0, screen);
		check("ScriptStringOut", results[0], results[1]);
		check("ScriptStringOut pixels", pixels[0], pixels[1]);
		check("ScriptStringOut drew", true, Arrays.stream(pixels[1]).anyMatch(p -> p != 0xFFFFFF));
	}

	static int[] pixels(long dc, int width, int height) {
		int[] result = new int[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) result[y * width + x] = OS.GetPixel(dc, x, y);
		}
		return result;
	}
}
