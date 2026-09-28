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

import java.io.*;
import java.util.*;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.browser.*;
import org.eclipse.swt.internal.ole.win32.*;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Exercises the objects of com_custom.cpp, or their FFM port: the WebView2 options object through
 * its vtable, PathToPIDL, and an Edge browser with a BrowserFunction, which goes through the callback
 * and host objects. Run it on the JNI and on the FFM build and compare the output.
 */
public class FFMWebView2Check {

	static int failures;

	static void check(String what, Object actual, Object expected) {
		boolean ok = Objects.equals(actual, expected);
		if (!ok) failures++;
		System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + actual + (ok ? "" : " expected " + expected));
	}

	public static void main(String[] args) throws Exception {
		options();
		pathToPIDL();
		for (int i = 0; i < 2; i++) browser();
		System.out.println(failures == 0 ? "PASSED" : "FAILED " + failures);
		System.exit(failures == 0 ? 0 : 1);
	}

	static String getString(long pOpts, int slot) {
		long[] ppsz = new long[1];
		int hr = COM.VtblCall(slot, pOpts, ppsz);
		if (hr != COM.S_OK) return "hr=" + Integer.toHexString(hr);
		if (ppsz[0] == 0) return null;
		int length = OS.wcslen(ppsz[0]);
		char[] chars = new char[length];
		OS.MoveMemory(chars, ppsz[0], length * 2);
		COM.CoTaskMemFree(ppsz[0]);
		return new String(chars);
	}

	static void options() {
		long pOpts = COM.CreateSwtWebView2Options();
		check("options created", pOpts != 0, true);
		ICoreWebView2EnvironmentOptions options = new ICoreWebView2EnvironmentOptions(pOpts);
		check("args initially", getString(pOpts, 3), null);
		options.put_AdditionalBrowserArguments("--foo --bar\0".toCharArray());
		options.put_Language("de-DE\0".toCharArray());
		options.put_TargetCompatibleBrowserVersion("99.0.1\0".toCharArray());
		check("args", getString(pOpts, 3), "--foo --bar");
		check("language", getString(pOpts, 5), "de-DE");
		check("version", getString(pOpts, 7), "99.0.1");
		options.put_Language("en\0".toCharArray());
		check("language replaced", getString(pOpts, 5), "en");
		COM.VtblCall(10, pOpts, 0);
		int[] allow = {-1};
		COM.VtblCall(9, pOpts, allow);
		check("allowSSO off", allow[0], 0);
		COM.VtblCall(10, pOpts, 1);
		COM.VtblCall(9, pOpts, allow);
		check("allowSSO on", allow[0], 1);

		GUID iid = COMObject.IIDFromString("{2FDE08A8-1E9A-4766-8C05-95A9CEB9D1C5}");
		long[] ppv = new long[1];
		check("QI options", options.QueryInterface(iid, ppv), COM.S_OK);
		check("QI same pointer", ppv[0], pOpts);
		check("QI IUnknown", options.QueryInterface(COM.IIDIUnknown, ppv), COM.S_OK);
		check("QI IDispatch", options.QueryInterface(COM.IIDIDispatch, ppv), COM.E_NOINTERFACE);
		check("QI IDispatch clears", ppv[0], 0L);
		check("AddRef", options.AddRef(), 4);
		check("Release", options.Release(), 3);
		options.Release();
		options.Release();
		check("last Release", options.Release(), 0);
	}

	static void pathToPIDL() throws IOException {
		File existing = File.createTempFile("ffm-pidl", ".txt");
		existing.deleteOnExit();
		File missing = new File(existing.getParentFile(), "ffm-pidl-does-not-exist-" + System.nanoTime() + "\\file.txt");
		for (File file : new File[] {existing, missing}) {
			long[] ppidl = new long[1];
			int hr = COM.PathToPIDL((file.getAbsolutePath() + "\0").toCharArray(), ppidl);
			check("PathToPIDL " + (file == existing ? "existing" : "missing"), hr, COM.S_OK);
			check("PIDL set", ppidl[0] != 0, true);
			check("PIDL size", OS.ILGetSize(ppidl[0]) > 2, true);
			COM.CoTaskMemFree(ppidl[0]);
		}
		check("PathToPIDL null out", COM.PathToPIDL("C:\\\0".toCharArray(), null), COM.E_FAIL);
	}

	static void browser() {
		Display display = Display.getDefault();
		Shell shell = new Shell(display);
		shell.setLayout(new FillLayout());
		Browser browser = new Browser(shell, SWT.EDGE);
		shell.setSize(400, 300);
		shell.open();
		boolean[] done = {false};
		browser.addProgressListener(ProgressListener.completedAdapter(e -> done[0] = true));
		java.util.List<String> calls = new ArrayList<>();
		new BrowserFunction(browser, "javaAdd") {
			@Override
			public Object function(Object[] arguments) {
				calls.add(Arrays.toString(arguments));
				return ((Number) arguments[0]).doubleValue() + ((Number) arguments[1]).doubleValue();
			}
		};
		new BrowserFunction(browser, "javaFail") {
			@Override
			public Object function(Object[] arguments) {
				throw new IllegalStateException("from Java");
			}
		};
		browser.setText("<html><body><h1 id='h'>WebView2</h1></body></html>");
		waitFor(display, () -> done[0], 20000);
		check("page loaded", done[0], true);
		check("evaluate", browser.evaluate("return 1 + 2;"), 3.0);
		check("evaluate string", browser.evaluate("return document.getElementById('h').textContent;"), "WebView2");
		check("function", browser.evaluate("return javaAdd(40, 2);"), 42.0);
		check("function arguments", calls, java.util.List.of("[40.0, 2.0]"));
		Object failed;
		try {
			failed = browser.evaluate("try { javaFail(); return 'no error'; } catch (e) { return 'error'; }");
		} catch (SWTException e) {
			failed = "SWTException " + e.getMessage();
		}
		check("function throws", failed, "error");
		check("execute", browser.execute("document.title = 'done';"), true);
		shell.dispose();
	}

	static void waitFor(Display display, BooleanSupplier condition, long timeout) {
		long end = System.currentTimeMillis() + timeout;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
			if (!display.readAndDispatch()) display.timerExec(50, () -> {});
			if (!condition.getAsBoolean()) display.sleep();
		}
	}
}
