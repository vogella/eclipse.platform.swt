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

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.ole.win32.*;
import org.eclipse.swt.internal.win32.*;

/**
 * Compares the Win32 FFM runtime with JNI in one process: callbacks behind window procs, hooks and
 * native threads, every <code>COM.VtblCall</code> overload, a real COM object and the captured last error.
 * Run against stock win32 SWT (JNI) plus common-ffm and win32-ffm.
 */
public class FFMWin32RuntimeCheck {

	static final Linker LINKER = Linker.nativeLinker();
	static int checks, failures;

	static void check(String what, Object jni, Object ffm) {
		checks++;
		if (Objects.deepEquals(jni, ffm)) return;
		failures++;
		System.out.println("MISMATCH " + what + "\n   JNI: " + jni + "\n   FFM: " + ffm);
	}

	static void expect(String what, boolean ok) {
		checks++;
		if (ok) return;
		failures++;
		System.out.println("FAILED   " + what);
	}

	public static void main(String[] args) throws Throwable {
		checkLookup();
		checkCallbacks();
		checkVtblCalls();
		checkComObject();
		checkLastError();
		System.out.println(checks + " checks, " + failures + " mismatches");
		System.exit(failures == 0 ? 0 : 1);
	}

	/* ---------------------------------------------------------------- symbol lookup */

	static final MethodHandle GetModuleHandleW = FFM.downcall("GetModuleHandleW", FunctionDescriptor.of(JAVA_LONG, ADDRESS));

	/** The file of the module containing <code>address</code>. */
	static String modulePath(long address) throws Throwable {
		MethodHandle fromAddress = FFM.downcall("GetModuleHandleExW", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS));
		MethodHandle fileName = FFM.downcall("GetModuleFileNameW", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT));
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment module = arena.allocate(JAVA_LONG);
			int ok = (int) fromAddress.invokeExact(0x4 | 0x2, address, module); // FROM_ADDRESS | UNCHANGED_REFCOUNT
			MemorySegment buffer = arena.allocate(2 * 1024);
			int length = (int) fileName.invokeExact(module.get(JAVA_LONG, 0), buffer, 1024);
			return new String(buffer.asSlice(0, 2L * length).toArray(JAVA_CHAR));
		}
	}

	static void checkLookup() throws Throwable {
		// only kernel32 is loaded for this, comctl32 must wait for the SWT manifest
		MethodHandle tid = FFM.downcall("GetCurrentThreadId", FunctionDescriptor.of(JAVA_INT));
		int ffmTid = (int) tid.invokeExact();
		check("GetCurrentThreadId", OS.GetCurrentThreadId(), ffmTid);
		expect("TaskDialogIndirect found, comctl32 version 6", FFM.downcallOptional("TaskDialogIndirect", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG)) != null);
		String path = modulePath(FFM.address("ImageList_Create"));
		System.out.println("ImageList_Create resolves to " + path);
		expect("comctl32 from WinSxS, version 6", path.toLowerCase().contains("winsxs") && path.contains("_6."));
		expect("missing symbol links lazily", FFM.downcallOptional("NoSuchFunctionInAnyDll", FunctionDescriptor.ofVoid()) == null);
		for (String name : new String[] {"GetSystemMetrics", "CreateSolidBrush", "CoCreateInstance", "SysAllocString", "SHGetFolderPathW",
			"ImmGetContext", "ScriptItemize", "OpenThemeData", "AlphaBlend", "DwmExtendFrameIntoClientArea", "PathIsDirectoryW",
			"RegOpenKeyExW", "AccessibleObjectFromWindow", "DocumentPropertiesW", "CreateURLMonikerEx", "InternetSetOptionW",
			"PSPropertyKeyFromString", "GdipCreateBitmapFromHBITMAP", "ImageList_Create", "ChooseColorW", "malloc"}) {
			expect("symbol " + name, FFM.downcallOptional(name, FunctionDescriptor.ofVoid()) != null);
		}
	}

	/* ---------------------------------------------------------------- callbacks */

	static final int WM_USER = 0x400, WM_TIMER = 0x113, PM_REMOVE = 1, WH_GETMESSAGE = 3;
	static final long HWND_MESSAGE = -3;

	/** The Win32 calls of the callback scenario, through JNI or through FFM. */
	interface Api {
		long bind(Object object, String method, String signature, int argCount, boolean isStatic, boolean arrayBased, long errorResult);
		void unbind(Object object);
		int entryCount();
		void setEnabled(boolean enabled);
		long hInstance();
		void registerClass(String name, long proc);
		void unregisterClass(String name);
		long createWindow(String className);
		long SendMessage(long hwnd, int msg, long wParam, long lParam);
		boolean PostMessage(long hwnd, int msg, long wParam, long lParam);
		/** Removes and dispatches pending messages, returns their ids. */
		List<Integer> pump();
		boolean DestroyWindow(long hwnd);
		long DefWindowProc(long hwnd, int msg, long wParam, long lParam);
		long SetWindowsHookEx(int id, long proc);
		boolean UnhookWindowsHookEx(long hook);
		long CallNextHookEx(long hook, int code, long wParam, long lParam);
		long SetTimer(long hwnd, long id, int elapse, long proc);
		boolean KillTimer(long hwnd, long id);
	}

	static class JniApi implements Api {
		final Map<Object, Callback> callbacks = new IdentityHashMap<>();
		final Map<String, Long> classNames = new HashMap<>();

		@Override
		public long bind(Object object, String method, String signature, int argCount, boolean isStatic, boolean arrayBased, long errorResult) {
			Callback callback = new Callback(object, method, argCount, arrayBased, errorResult);
			callbacks.put(object, callback);
			return callback.getAddress();
		}

		@Override
		public void unbind(Object object) {
			callbacks.remove(object).dispose();
		}

		@Override
		public int entryCount() {
			return Callback.getEntryCount();
		}

		@Override
		public void setEnabled(boolean enabled) {
			Callback.setEnabled(enabled);
		}

		@Override
		public long hInstance() {
			return OS.GetModuleHandle(null);
		}

		@Override
		public void registerClass(String name, long proc) {
			char[] chars = (name + "\0").toCharArray();
			long heap = OS.GetProcessHeap();
			long lpszClassName = OS.HeapAlloc(heap, OS.HEAP_ZERO_MEMORY, chars.length * 2);
			OS.MoveMemory(lpszClassName, chars, chars.length * 2);
			classNames.put(name, lpszClassName);
			WNDCLASS wc = new WNDCLASS();
			wc.lpfnWndProc = proc;
			wc.hInstance = hInstance();
			wc.lpszClassName = lpszClassName;
			expect("JNI RegisterClass", OS.RegisterClass(wc) != 0);
		}

		@Override
		public void unregisterClass(String name) {
			OS.UnregisterClass((name + "\0").toCharArray(), hInstance());
			OS.HeapFree(OS.GetProcessHeap(), 0, classNames.remove(name));
		}

		@Override
		public long createWindow(String className) {
			return OS.CreateWindowEx(0, (className + "\0").toCharArray(), null, 0, 0, 0, 10, 10, HWND_MESSAGE, 0, hInstance(), null);
		}

		@Override
		public long SendMessage(long hwnd, int msg, long wParam, long lParam) {
			return OS.SendMessage(hwnd, msg, wParam, lParam);
		}

		@Override
		public boolean PostMessage(long hwnd, int msg, long wParam, long lParam) {
			return OS.PostMessage(hwnd, msg, wParam, lParam);
		}

		@Override
		public List<Integer> pump() {
			List<Integer> ids = new ArrayList<>();
			MSG msg = new MSG();
			while (OS.PeekMessage(msg, 0, 0, 0, PM_REMOVE)) {
				ids.add(msg.message);
				OS.TranslateMessage(msg);
				OS.DispatchMessage(msg);
			}
			return ids;
		}

		@Override
		public boolean DestroyWindow(long hwnd) {
			return OS.DestroyWindow(hwnd);
		}

		@Override
		public long DefWindowProc(long hwnd, int msg, long wParam, long lParam) {
			return OS.DefWindowProc(hwnd, msg, wParam, lParam);
		}

		@Override
		public long SetWindowsHookEx(int id, long proc) {
			return OS.SetWindowsHookEx(id, proc, 0, OS.GetCurrentThreadId());
		}

		@Override
		public boolean UnhookWindowsHookEx(long hook) {
			return OS.UnhookWindowsHookEx(hook);
		}

		@Override
		public long CallNextHookEx(long hook, int code, long wParam, long lParam) {
			return OS.CallNextHookEx(hook, code, wParam, lParam);
		}

		@Override
		public long SetTimer(long hwnd, long id, int elapse, long proc) {
			return OS.SetTimer(hwnd, id, elapse, proc);
		}

		@Override
		public boolean KillTimer(long hwnd, long id) {
			return OS.KillTimer(hwnd, id);
		}
	}

	/** Hand written downcalls, as the generated OS_FFM will make them, and FFMCallback. */
	static class FfmApi implements Api {
		static final MethodHandle RegisterClassW = FFM.downcall("RegisterClassW", FunctionDescriptor.of(JAVA_SHORT, ADDRESS));
		static final MethodHandle UnregisterClassW = FFM.downcall("UnregisterClassW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
		static final MethodHandle CreateWindowExW = FFM.downcall("CreateWindowExW", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
		static final MethodHandle SendMessageW = FFM.downcall("SendMessageW", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
		static final MethodHandle PostMessageW = FFM.downcall("PostMessageW", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
		static final MethodHandle PeekMessageW = FFM.downcall("PeekMessageW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT));
		static final MethodHandle TranslateMessage = FFM.downcall("TranslateMessage", FunctionDescriptor.of(JAVA_INT, ADDRESS));
		static final MethodHandle DispatchMessageW = FFM.downcall("DispatchMessageW", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
		static final MethodHandle DestroyWindow = FFM.downcall("DestroyWindow", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
		static final MethodHandle DefWindowProcW = FFM.downcall("DefWindowProcW", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
		static final MethodHandle SetWindowsHookExW = FFM.downcall("SetWindowsHookExW", FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT));
		static final MethodHandle UnhookWindowsHookEx = FFM.downcall("UnhookWindowsHookEx", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
		static final MethodHandle CallNextHookEx = FFM.downcall("CallNextHookEx", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
		static final MethodHandle SetTimer = FFM.downcall("SetTimer", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG));
		static final MethodHandle KillTimer = FFM.downcall("KillTimer", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
		static final MethodHandle GetCurrentThreadId = FFM.downcall("GetCurrentThreadId", FunctionDescriptor.of(JAVA_INT));

		final Map<String, Arena> classNames = new HashMap<>();

		@Override
		public long bind(Object object, String method, String signature, int argCount, boolean isStatic, boolean arrayBased, long errorResult) {
			return FFMCallback.bind(object, object, method, signature, argCount, isStatic, arrayBased, errorResult);
		}

		@Override
		public void unbind(Object object) {
			FFMCallback.unbind(object);
		}

		@Override
		public int entryCount() {
			return FFMCallback.getEntryCount();
		}

		@Override
		public void setEnabled(boolean enabled) {
			FFMCallback.setEnabled(enabled);
		}

		@Override
		public long hInstance() {
			try {
				return (long) GetModuleHandleW.invokeExact(MemorySegment.NULL);
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public void registerClass(String name, long proc) {
			Arena arena = Arena.ofShared();
			classNames.put(name, arena);
			MemorySegment wc = arena.allocate(72, 8); // WNDCLASSW
			wc.set(JAVA_LONG, 8, proc);
			wc.set(JAVA_LONG, 24, hInstance());
			wc.set(ADDRESS, 64, utf16(arena, name));
			try {
				expect("FFM RegisterClassW", (short) RegisterClassW.invokeExact(wc) != 0);
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public void unregisterClass(String name) {
			try (Arena arena = Arena.ofConfined()) {
				int ignored = (int) UnregisterClassW.invokeExact(utf16(arena, name), hInstance());
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
			classNames.remove(name).close();
		}

		@Override
		public long createWindow(String className) {
			try (Arena arena = Arena.ofConfined()) {
				long hwnd = (long) CreateWindowExW.invokeExact(0, utf16(arena, className), MemorySegment.NULL, 0, 0, 0, 10, 10, HWND_MESSAGE, 0L, hInstance(), 0L);
				FFM.checkCallbackException();
				return hwnd;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public long SendMessage(long hwnd, int msg, long wParam, long lParam) {
			try {
				long rc = (long) SendMessageW.invokeExact(hwnd, msg, wParam, lParam);
				FFM.checkCallbackException();
				return rc;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public boolean PostMessage(long hwnd, int msg, long wParam, long lParam) {
			try {
				return (int) PostMessageW.invokeExact(hwnd, msg, wParam, lParam) != 0;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public List<Integer> pump() {
			List<Integer> ids = new ArrayList<>();
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment msg = arena.allocate(48, 8);
				while ((int) PeekMessageW.invokeExact(msg, 0L, 0, 0, PM_REMOVE) != 0) {
					ids.add(msg.get(JAVA_INT, 8));
					int translated = (int) TranslateMessage.invokeExact(msg);
					long rc = (long) DispatchMessageW.invokeExact(msg);
					FFM.checkCallbackException();
				}
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
			return ids;
		}

		@Override
		public boolean DestroyWindow(long hwnd) {
			try {
				boolean ok = (int) DestroyWindow.invokeExact(hwnd) != 0;
				FFM.checkCallbackException();
				return ok;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public long DefWindowProc(long hwnd, int msg, long wParam, long lParam) {
			try {
				return (long) DefWindowProcW.invokeExact(hwnd, msg, wParam, lParam);
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public long SetWindowsHookEx(int id, long proc) {
			try {
				return (long) SetWindowsHookExW.invokeExact(id, proc, 0L, (int) GetCurrentThreadId.invokeExact());
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public boolean UnhookWindowsHookEx(long hook) {
			try {
				return (int) UnhookWindowsHookEx.invokeExact(hook) != 0;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public long CallNextHookEx(long hook, int code, long wParam, long lParam) {
			try {
				return (long) CallNextHookEx.invokeExact(hook, code, wParam, lParam);
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public long SetTimer(long hwnd, long id, int elapse, long proc) {
			try {
				return (long) SetTimer.invokeExact(hwnd, id, elapse, proc);
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}

		@Override
		public boolean KillTimer(long hwnd, long id) {
			try {
				return (int) KillTimer.invokeExact(hwnd, id) != 0;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}
	}

	/** The window proc, hook and timer proc of the scenario, logging what reaches Java. */
	static class Target {
		final Api api;
		final List<String> log = new ArrayList<>();
		long hwnd, hook;

		Target(Api api) {
			this.api = api;
		}

		long windowProc(long hwnd, long msg, long wParam, long lParam) {
			int m = (int) msg;
			switch (m) {
				case WM_USER + 1:
					log.add("user1 " + wParam + " " + lParam + " entries=" + api.entryCount());
					return wParam + lParam;
				case WM_USER + 2:
					log.add("posted " + wParam + " " + lParam);
					return 0;
				case WM_USER + 3:
					log.add("throwing");
					throw new IllegalStateException("thrown by the window proc");
				case WM_USER + 4:
					log.add("outer entries=" + api.entryCount());
					long inner = api.SendMessage(hwnd, WM_USER + 5, 0, 0);
					log.add("inner returned " + inner + " entries=" + api.entryCount());
					return inner + 1;
				case WM_USER + 5:
					log.add("inner entries=" + api.entryCount());
					return 41;
				case WM_USER + 6:
					// a nested callback throws, the outer one continues and its caller sees the exception
					try {
						api.SendMessage(hwnd, WM_USER + 3, 0, 0);
						log.add("nested exception not seen");
					} catch (IllegalStateException e) {
						log.add("nested exception seen: " + e.getMessage());
					}
					return 6;
				case WM_USER + 7:
					log.add("hooked message dispatched");
					return 0;
				case WM_TIMER:
					log.add("WM_TIMER " + wParam);
					return 0;
				default:
					log.add("msg 0x" + Integer.toHexString(m));
					return api.DefWindowProc(hwnd, m, wParam, lParam);
			}
		}

		long getMsgProc(long code, long wParam, long lParam) {
			int message = MemorySegment.ofAddress(lParam).reinterpret(48).get(JAVA_INT, 8);
			if (message == WM_USER + 7) log.add("hook saw WM_USER+7");
			return api.CallNextHookEx(hook, (int) code, wParam, lParam);
		}

		long timerProc(long hwnd, long msg, long id, long time) {
			log.add("timerProc " + id);
			return 0;
		}

		/** A thread start routine run on a thread the JVM did not create. */
		long threadProc(long parameter) {
			if (parameter == 13) throw new IllegalStateException("thrown on a native thread");
			return parameter * 2;
		}

		static long arrayProc(long[] args) {
			return args[0] * 100 + args[1] * 10 + args[2];
		}

		void intProc(int a, int b, int c, int d) {
			log.add("intProc " + (a + b + c + d));
		}
	}

	static final MethodHandle CreateThread = FFM.downcall("CreateThread", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG));
	static final MethodHandle WaitForSingleObject = FFM.downcall("WaitForSingleObject", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
	static final MethodHandle GetExitCodeThread = FFM.downcall("GetExitCodeThread", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
	static final MethodHandle CloseHandle = FFM.downcall("CloseHandle", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));

	static int runOnNativeThread(long start, long parameter) throws Throwable {
		long thread = (long) CreateThread.invokeExact(0L, 0L, start, parameter, 0, 0L);
		int wait = (int) WaitForSingleObject.invokeExact(thread, 10_000);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment code = arena.allocate(JAVA_INT);
			int ok = (int) GetExitCodeThread.invokeExact(thread, code);
			ok = (int) CloseHandle.invokeExact(thread);
			return wait == 0 ? code.get(JAVA_INT, 0) : -1;
		}
	}

	static List<String> scenario(Api api, String className) throws Throwable {
		Target t = new Target(api);
		List<String> log = t.log;
		long proc = api.bind(t, "windowProc", "(JJJJ)J", 4, false, false, 0);
		api.registerClass(className, proc);
		t.hwnd = api.createWindow(className);
		log.add("created " + (t.hwnd != 0) + " entries=" + api.entryCount());

		log.add("send returned " + api.SendMessage(t.hwnd, WM_USER + 1, 11, 22));
		api.PostMessage(t.hwnd, WM_USER + 2, 3, 4);
		log.add("pumped " + api.pump().contains(WM_USER + 2));
		log.add("nested returned " + api.SendMessage(t.hwnd, WM_USER + 4, 0, 0));

		try {
			long rc = api.SendMessage(t.hwnd, WM_USER + 3, 0, 0);
			log.add("exception not thrown, returned " + rc);
		} catch (IllegalStateException e) {
			log.add("exception after SendMessage: " + e.getMessage());
		}
		log.add("send after exception returned " + api.SendMessage(t.hwnd, WM_USER + 1, 1, 2));
		try {
			log.add("nested thrower returned " + api.SendMessage(t.hwnd, WM_USER + 6, 0, 0));
		} catch (IllegalStateException e) {
			log.add("outer caller saw: " + e.getMessage());
		}

		api.setEnabled(false);
		log.add("disabled send returned " + api.SendMessage(t.hwnd, WM_USER + 1, 5, 6) + " entries=" + api.entryCount());
		api.setEnabled(true);

		long hookProc = bindMethod(api, t, "getMsgProc", "(JJJ)J", 3, 0);
		t.hook = api.SetWindowsHookEx(WH_GETMESSAGE, hookProc);
		log.add("hook installed " + (t.hook != 0));
		api.PostMessage(t.hwnd, WM_USER + 7, 0, 0);
		api.pump();
		log.add("unhooked " + api.UnhookWindowsHookEx(t.hook));

		long timerProc = bindMethod(api, t, "timerProc", "(JJJJ)J", 4, 0);
		api.SetTimer(t.hwnd, 7, 1, 0);
		api.SetTimer(t.hwnd, 8, 1, timerProc);
		for (int i = 0; i < 200 && !(log.contains("WM_TIMER 7") && log.contains("timerProc 8")); i++) {
			Thread.sleep(5);
			api.pump();
		}
		api.KillTimer(t.hwnd, 7);
		api.KillTimer(t.hwnd, 8);
		log.removeIf(s -> s.equals("WM_TIMER 7") || s.equals("timerProc 8"));
		log.add("timers fired");

		long threadProc = bindMethod(api, t, "threadProc", "(J)J", 1, 99);
		log.add("native thread exit " + runOnNativeThread(threadProc, 21));
		// the error result of the callback, and nothing thrown into native code
		log.add("native thread failing exit " + runOnNativeThread(threadProc, 13));

		long arrayProc = api.bind(Target.class, "arrayProc", "([J)J", 3, true, true, 0x80004005L);
		MethodHandle three = LINKER.downcallHandle(MemorySegment.ofAddress(arrayProc), FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
		log.add("array based returned " + (long) three.invokeExact(1L, 2L, 3L));

		long intProc = bindMethod(api, t, "intProc", "(IIII)V", 4, 0);
		MethodHandle ints = LINKER.downcallHandle(MemorySegment.ofAddress(intProc), FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
		ints.invokeExact(1, 2, 3, 4);

		log.add("destroyed " + api.DestroyWindow(t.hwnd) + " entries=" + api.entryCount());
		api.unregisterClass(className);
		log.add("final entries=" + api.entryCount());
		return log;
	}

	static final Map<String, Object> KEYS = new HashMap<>();

	/** Binds a method of <code>t</code>; the JNI Callback cannot take a signature, so the key object carries it. */
	static long bindMethod(Api api, Target t, String method, String signature, int argCount, long errorResult) {
		if (api instanceof JniApi) {
			if (signature.contains("I")) {
				Callback c = new Callback(t, method, void.class, new java.lang.reflect.Type[] {int.class, int.class, int.class, int.class});
				KEYS.put("jni " + method, c);
				return c.getAddress();
			}
			Callback c = new Callback(t, method, argCount, false, errorResult);
			KEYS.put("jni " + method, c);
			return c.getAddress();
		}
		Object key = new Object();
		KEYS.put("ffm " + method, key);
		return FFMCallback.bind(key, t, method, signature, argCount, false, false, errorResult);
	}

	static void checkCallbacks() throws Throwable {
		List<String> jni = scenario(new JniApi(), "SwtJniCheck");
		List<String> ffm = scenario(new FfmApi(), "SwtFfmCheck");
		check("callback scenario (" + jni.size() + " events)", String.join("\n", jni), String.join("\n", ffm));
		if (Boolean.getBoolean("verbose")) ffm.forEach(line -> System.out.println("   " + line));
		expect("scenario saw the native thread exit code", ffm.contains("native thread exit 42"));
		expect("error result of a failing callback", ffm.contains("native thread failing exit 99"));
		expect("FFM: no exception pending on the UI thread", FFM.takePending() == null);
	}

	/* ---------------------------------------------------------------- VtblCall, every overload against a recording object */

	static final Random RANDOM = new Random(4711);
	static final StringBuilder RECORD = new StringBuilder();
	static long[] pointeeSizes;
	static boolean[][] pointeePadding;
	static long pattern;

	/** The padding bytes of a struct, from the layout FFMCom uses; a misplaced field still shows up as a mismatch. */
	static boolean[] padding(Class<?> struct) throws Exception {
		Field f = FFMCom.class.getDeclaredField(struct.getSimpleName() + "_LAYOUT");
		f.setAccessible(true);
		StructLayout layout = (StructLayout) f.get(null);
		boolean[] padding = new boolean[(int) layout.byteSize()];
		long offset = 0;
		for (MemoryLayout member : layout.memberLayouts()) {
			if (member instanceof PaddingLayout) Arrays.fill(padding, (int) offset, (int) (offset + member.byteSize()), true);
			offset += member.byteSize();
		}
		return padding;
	}

	/** The vtable slot of the fake object: records its arguments and writes a pattern through every pointer. */
	static int record(Object[] args) {
		RECORD.setLength(0);
		Random fill = new Random(pattern);
		for (int i = 0; i < args.length; i++) {
			Object a = args[i];
			if (a instanceof MemorySegment s) {
				long size = pointeeSizes[i];
				if (s.address() == 0) {
					RECORD.append(" null");
					continue;
				}
				MemorySegment m = s.byteSize() == 0 ? s.reinterpret(size) : s;
				byte[] bytes = m.toArray(JAVA_BYTE);
				// padding is stack garbage in the JNI glue and zero in FFM
				if (pointeePadding[i] != null) {
					for (int b = 0; b < bytes.length; b++) if (pointeePadding[i][b]) bytes[b] = 0;
				}
				RECORD.append(" ").append(HexFormat.of().formatHex(bytes));
				for (long b = 0; b < size; b++) m.set(JAVA_BYTE, b, (byte) fill.nextInt());
			} else {
				RECORD.append(" ").append(a);
			}
		}
		return 0x1234 + args.length;
	}

	static boolean isByValue(Method m) {
		Class<?>[] p = m.getParameterTypes();
		return m.getName().equals("VtblCall_put_Bounds") || (p.length == 5 && p[2] == RECT.class && p[3] == long.class && p[4] == long.class);
	}

	static final StructLayout RECT_BY_VALUE = MemoryLayout.structLayout(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);

	static void checkVtblCalls() throws Throwable {
		checkLayouts();
		MethodHandle record = MethodHandles.lookup().findStatic(FFMWin32RuntimeCheck.class, "record", MethodType.methodType(int.class, Object[].class));
		Arena arena = Arena.global();
		int count = 0;
		for (Method jni : COM.class.getDeclaredMethods()) {
			if (!Modifier.isNative(jni.getModifiers()) || !jni.getName().startsWith("VtblCall")) continue;
			Method ffm = FFMCom.class.getMethod(jni.getName(), jni.getParameterTypes());
			Class<?>[] types = jni.getParameterTypes();
			boolean byValue = isByValue(jni);
			MemoryLayout[] layouts = new MemoryLayout[types.length - 1];
			layouts[0] = JAVA_LONG;
			for (int i = 2; i < types.length; i++) {
				Class<?> t = types[i];
				layouts[i - 1] = t == int.class ? JAVA_INT : t == long.class ? JAVA_LONG : t == double.class ? JAVA_DOUBLE : byValue && i == 2 ? RECT_BY_VALUE : ADDRESS;
			}
			FunctionDescriptor descriptor = FunctionDescriptor.of(JAVA_INT, layouts);
			MethodHandle target = record.asCollector(Object[].class, layouts.length).asType(descriptor.toMethodType());
			MemorySegment stub = LINKER.upcallStub(target, descriptor, arena);
			int index = 3 + RANDOM.nextInt(20);
			MemorySegment vtbl = arena.allocate(8L * 32, 8);
			vtbl.set(JAVA_LONG, 8L * index, stub.address());
			MemorySegment object = arena.allocate(8, 8);
			object.set(JAVA_LONG, 0, vtbl.address());

			for (boolean nulls : new boolean[] {false, true}) {
				long seed = RANDOM.nextLong();
				if (nulls && byValue) {
					// JNI dereferences NULL and crashes, FFM throws
					Object[] ffmArgs = arguments(types, index, object.address(), new Random(seed), true);
					try {
						ffm.invoke(null, ffmArgs);
						expect(jni.getName() + " null struct by value throws", false);
					} catch (InvocationTargetException e) {
						expect(jni.getName() + " null struct by value throws", e.getCause() instanceof NullPointerException);
					}
					continue;
				}
				Object[] jniArgs = arguments(types, index, object.address(), new Random(seed), nulls);
				Object[] ffmArgs = arguments(types, index, object.address(), new Random(seed), nulls);
				pointeeSizes = sizes(types, jniArgs, byValue);
				pattern = seed;
				RECORD.setLength(0);
				Object jniResult = jni.invoke(null, jniArgs);
				String jniRecord = RECORD.toString();
				RECORD.setLength(0);
				Object ffmResult = ffm.invoke(null, ffmArgs);
				String ffmRecord = RECORD.toString();
				String what = jni.getName() + Arrays.toString(types) + (nulls ? " with nulls" : "");
				check(what + " result", jniResult, ffmResult);
				check(what + " native side", jniRecord, ffmRecord);
				check(what + " copied back", describe(jniArgs), describe(ffmArgs));
				count++;
			}
		}
		System.out.println("VtblCall: " + count + " calls compared");
	}

	static void checkLayouts() throws Throwable {
		for (Field f : FFMCom.class.getDeclaredFields()) {
			if (!f.getName().endsWith("_LAYOUT")) continue;
			f.setAccessible(true);
			String struct = f.getName().substring(0, f.getName().length() - "_LAYOUT".length());
			Class<?> c;
			try {
				c = Class.forName("org.eclipse.swt.internal.win32." + struct);
			} catch (ClassNotFoundException e) {
				c = Class.forName("org.eclipse.swt.internal.ole.win32." + struct);
			}
			long sizeof = c.getField("sizeof").getInt(null);
			check("sizeof " + struct, sizeof, ((MemoryLayout) f.get(null)).byteSize());
		}
	}

	static Object[] arguments(Class<?>[] types, int index, long object, Random random, boolean nulls) throws Exception {
		Object[] args = new Object[types.length];
		args[0] = index;
		args[1] = object;
		for (int i = 2; i < types.length; i++) {
			Class<?> t = types[i];
			if (t == int.class) args[i] = random.nextInt();
			else if (t == long.class) args[i] = random.nextLong();
			else if (t == double.class) args[i] = random.nextDouble();
			else if (nulls) args[i] = null;
			else if (t.isArray()) args[i] = randomArray(t.getComponentType(), 1 + random.nextInt(6), random);
			else args[i] = randomStruct(t, random);
		}
		return args;
	}

	static Object randomArray(Class<?> component, int length, Random random) {
		Object array = Array.newInstance(component, length);
		for (int i = 0; i < length; i++) {
			if (component == char.class) Array.setChar(array, i, (char) random.nextInt());
			else if (component == int.class) Array.setInt(array, i, random.nextInt());
			else if (component == long.class) Array.setLong(array, i, random.nextLong());
			else throw new IllegalArgumentException(component.toString());
		}
		return array;
	}

	static Object randomStruct(Class<?> type, Random random) throws Exception {
		Object o = type.getConstructor().newInstance();
		for (Field f : type.getFields()) {
			if (Modifier.isStatic(f.getModifiers())) continue;
			Class<?> t = f.getType();
			if (t == int.class) f.setInt(o, random.nextInt());
			else if (t == long.class) f.setLong(o, random.nextLong());
			else if (t == short.class) f.setShort(o, (short) random.nextInt());
			else if (t == boolean.class) f.setBoolean(o, random.nextBoolean());
			else if (t == byte[].class) random.nextBytes((byte[]) f.get(o));
			else f.set(o, randomStruct(t, random));
		}
		return o;
	}

	static long[] sizes(Class<?>[] types, Object[] args, boolean byValue) throws Exception {
		// indexed like the upcall arguments: 0 is the object
		long[] sizes = new long[types.length - 1];
		pointeePadding = new boolean[types.length - 1][];
		for (int i = 2; i < types.length; i++) {
			Class<?> t = types[i];
			if (t.isPrimitive() || args[i] == null) continue;
			if (t.isArray()) {
				int element = t == char[].class ? 2 : t == int[].class ? 4 : 8;
				sizes[i - 1] = (long) Array.getLength(args[i]) * element;
			} else {
				sizes[i - 1] = t.getField("sizeof").getInt(null);
				pointeePadding[i - 1] = padding(t);
			}
		}
		return sizes;
	}

	static String describe(Object o) throws Exception {
		if (o == null) return "null";
		if (o.getClass().isArray()) {
			if (o instanceof Object[] a) {
				StringBuilder b = new StringBuilder("[");
				for (int i = 2; i < a.length; i++) b.append(describe(a[i])).append(", ");
				return b.append("]").toString();
			}
			StringBuilder b = new StringBuilder("[");
			for (int i = 0; i < Array.getLength(o); i++) b.append(Array.get(o, i)).append(' ');
			return b.append("]").toString();
		}
		if (o instanceof Number || o instanceof Boolean) return o.toString();
		StringBuilder b = new StringBuilder(o.getClass().getSimpleName()).append('{');
		for (Field f : o.getClass().getFields()) {
			if (Modifier.isStatic(f.getModifiers())) continue;
			b.append(f.getName()).append('=').append(describe(f.get(o))).append(' ');
		}
		return b.append('}').toString();
	}

	/* ---------------------------------------------------------------- a real COM object */

	static final int CLSCTX_INPROC_SERVER = 1;

	static GUID guid(String s) {
		GUID g = new GUID();
		COM.IIDFromString((s + "\0").toCharArray(), g);
		return g;
	}

	static String text(char[] buffer) {
		int end = 0;
		while (end < buffer.length && buffer[end] != 0) end++;
		return new String(buffer, 0, end);
	}

	/** Slots of a COMObject style Java object, bound array based with E_FAIL as error result. */
	static long slot0(long[] args) {
		MemorySegment.ofAddress(args[2]).reinterpret(8).set(JAVA_LONG, 0, 0x4242);
		return COM.S_OK;
	}

	static long slot1(long[] args) {
		return 7;
	}

	static long slot2(long[] args) {
		throw new IllegalStateException("thrown by a COM method");
	}

	static List<String> javaComObject(boolean ffm) throws Throwable {
		List<String> log = new ArrayList<>();
		int[] argCounts = {2, 0, 0};
		MemorySegment vtbl = Arena.global().allocate(8L * argCounts.length, 8);
		for (int i = 0; i < argCounts.length; i++) {
			String method = "slot" + i;
			int argCount = argCounts[i] + 1;
			long address = ffm
				? FFMCallback.bind(new Object(), FFMWin32RuntimeCheck.class, method, "([J)J", argCount, true, true, COM.E_FAIL)
				: new Callback(FFMWin32RuntimeCheck.class, method, argCount, true, COM.E_FAIL).getAddress();
			vtbl.set(JAVA_LONG, 8L * i, address);
		}
		MemorySegment object = Arena.global().allocate(8, 8);
		object.set(JAVA_LONG, 0, vtbl.address());
		long[] out = new long[1];
		log.add("QueryInterface " + (ffm ? FFMCom.VtblCall(0, object.address(), new GUID(), out) : COM.VtblCall(0, object.address(), new GUID(), out)) + " " + out[0]);
		log.add("AddRef " + (ffm ? FFMCom.VtblCall(1, object.address()) : COM.VtblCall(1, object.address())));
		try {
			log.add("returned " + (ffm ? FFMCom.VtblCall(2, object.address()) : COM.VtblCall(2, object.address())));
		} catch (IllegalStateException e) {
			log.add("exception after VtblCall: " + e.getMessage());
		}
		return log;
	}

	static void checkComObject() throws Throwable {
		check("Java COM object", javaComObject(false), javaComObject(true));
		OS.OleInitialize(0);
		GUID clsid = guid("{00021401-0000-0000-C000-000000000046}"); // CLSID_ShellLink
		GUID iidShellLink = guid("{000214F9-0000-0000-C000-000000000046}"); // IShellLinkW
		GUID iidPersistFile = guid("{0000010b-0000-0000-C000-000000000046}");
		long[] ppv = new long[1];
		expect("CoCreateInstance ShellLink", COM.CoCreateInstance(clsid, 0, CLSCTX_INPROC_SERVER, iidShellLink, ppv) == COM.S_OK);
		long link = ppv[0];

		// IShellLinkW: 6 GetDescription, 7 SetDescription, 14 GetShowCmd, 15 SetShowCmd
		check("SetDescription", COM.S_OK, FFMCom.VtblCall(7, link, "written through FFM\0".toCharArray()));
		char[] jniBuffer = new char[64], ffmBuffer = new char[64];
		COM.VtblCall(6, link, jniBuffer, jniBuffer.length);
		FFMCom.VtblCall(6, link, ffmBuffer, ffmBuffer.length);
		check("GetDescription after FFM set", "written through FFM", text(jniBuffer));
		check("GetDescription through FFM", text(jniBuffer), text(ffmBuffer));
		COM.VtblCall(7, link, "written through JNI\0".toCharArray());
		FFMCom.VtblCall(6, link, ffmBuffer, ffmBuffer.length);
		check("GetDescription after JNI set", "written through JNI", text(ffmBuffer));

		check("SetShowCmd", COM.S_OK, FFMCom.VtblCall(15, link, 3));
		int[] jniShow = new int[1], ffmShow = new int[1];
		COM.VtblCall(14, link, jniShow);
		FFMCom.VtblCall(14, link, ffmShow);
		check("GetShowCmd", jniShow[0], ffmShow[0]);
		check("GetShowCmd value", 3, ffmShow[0]);

		long[] jniFile = new long[1], ffmFile = new long[1];
		check("QueryInterface IPersistFile", COM.VtblCall(0, link, iidPersistFile, jniFile), FFMCom.VtblCall(0, link, iidPersistFile, ffmFile));
		check("QueryInterface pointer", jniFile[0], ffmFile[0]);
		GUID jniClass = new GUID(), ffmClass = new GUID();
		check("IPersistFile.GetClassID", COM.VtblCall(3, jniFile[0], jniClass), FFMCom.VtblCall(3, ffmFile[0], ffmClass));
		check("class id", clsid.toString(), ffmClass.toString());
		check("class id through both", jniClass.toString(), ffmClass.toString());
		check("IPersistFile.IsDirty", COM.VtblCall(4, jniFile[0]), FFMCom.VtblCall(4, ffmFile[0]));

		int jniRef = COM.VtblCall(1, link); // AddRef
		int ffmRef = FFMCom.VtblCall(1, link);
		check("AddRef counts", jniRef + 1, ffmRef);
		check("Release counts", COM.VtblCall(2, link) - 1, FFMCom.VtblCall(2, link));
		COM.VtblCall(2, jniFile[0]);
		FFMCom.VtblCall(2, ffmFile[0]);
		check("final Release", 0, FFMCom.VtblCall(2, link));
		OS.OleUninitialize();
	}

	/* ---------------------------------------------------------------- GetLastError */

	static void checkLastError() throws Throwable {
		int ERROR_INVALID_MENU_HANDLE = 1401, ERROR_INVALID_HANDLE = 6;
		int jniCount = OS.GetMenuItemCount(0x1234);
		int jniError = OS.GetLastError();

		MethodHandle getMenuItemCount = FFMLastError.downcall("GetMenuItemCount", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
		MethodHandle setLastError = FFM.downcall("SetLastError", FunctionDescriptor.ofVoid(JAVA_INT));
		int ffmCount = (int) getMenuItemCount.invokeExact(FFMLastError.state(), 0x1234L);
		// clobber the real last error, then let the JVM run
		setLastError.invokeExact(0);
		byte[][] garbage = new byte[1000][];
		for (int i = 0; i < garbage.length; i++) garbage[i] = new byte[1000];
		System.gc();
		check("GetMenuItemCount", jniCount, ffmCount);
		// JNI usually loses the error to the VM between the two natives, which is why FFM captures it
		System.out.println("GetLastError after GetMenuItemCount: JNI " + jniError + ", FFM " + FFMLastError.get());
		check("GetLastError value", ERROR_INVALID_MENU_HANDLE, FFMLastError.get());

		int[] other = new int[1];
		Thread thread = new Thread(() -> {
			try {
				MethodHandle closeHandle = FFMLastError.downcall("CloseHandle", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
				int ok = (int) closeHandle.invokeExact(FFMLastError.state(), 0x1234L);
				other[0] = FFMLastError.get();
			} catch (Throwable t) {
				other[0] = -1;
			}
		});
		thread.start();
		thread.join();
		check("GetLastError of another thread", ERROR_INVALID_HANDLE, other[0]);
		check("GetLastError kept per thread", ERROR_INVALID_MENU_HANDLE, FFMLastError.get());

		MethodHandle missing = FFMLastError.downcall("NoSuchFunctionInAnyDll", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
		try {
			int ignored = (int) missing.invokeExact(FFMLastError.state(), 0L);
			expect("missing capturing function throws", false);
		} catch (UnsatisfiedLinkError e) {
			expect("missing capturing function throws", true);
		}
	}

	static MemorySegment utf16(Arena arena, String s) {
		return arena.allocateFrom(s, java.nio.charset.StandardCharsets.UTF_16LE);
	}
}
