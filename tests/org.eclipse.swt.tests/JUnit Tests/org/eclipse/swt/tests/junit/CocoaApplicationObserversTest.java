/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH. All rights reserved.
 *
 * Proprietary and confidential. This file is not licensed under the Eclipse
 * Public License. Use, copying, modification and distribution are permitted
 * only under a written license agreement with vogella GmbH; see
 * about_files/vogella-LICENSE.txt of org.eclipse.swt.
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;

import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.Test;

/**
 * On Cocoa a key-value observer that is registered on NSApp before the Display exists stays registered when the
 * Display gives NSApp its own class, so that the observer can remove itself, as ViewBridge does on activation.
 */
public class CocoaApplicationObserversTest {

	final Class<?> os;
	final Method sel;
	final Method msg0;

	public CocoaApplicationObserversTest() throws ReflectiveOperationException {
		os = SwtTestUtil.isCocoa ? Class.forName("org.eclipse.swt.internal.cocoa.OS") : null;
		sel = os == null ? null : os.getMethod("sel_registerName", String.class);
		msg0 = os == null ? null : os.getMethod("objc_msgSend", long.class, long.class);
	}

	long send(long id, String selector) throws ReflectiveOperationException {
		return (long) msg0.invoke(null, id, (long) sel.invoke(null, selector));
	}

	long send(long id, String selector, long... args) throws ReflectiveOperationException {
		Class<?>[] types = new Class<?>[args.length + 2];
		Object[] values = new Object[args.length + 2];
		types[0] = types[1] = long.class;
		values[0] = id;
		values[1] = sel.invoke(null, selector);
		for (int i = 0; i < args.length; i++) {
			types[i + 2] = long.class;
			values[i + 2] = args[i];
		}
		return (long) os.getMethod("objc_msgSend", types).invoke(null, values);
	}

	long cls(String name) throws ReflectiveOperationException {
		return (long) os.getMethod("objc_getClass", String.class).invoke(null, name);
	}

	String isa(long id) throws ReflectiveOperationException {
		long cls = (long) os.getMethod("object_getClass", long.class).invoke(null, id);
		return (String) os.getMethod("class_getName", long.class).invoke(null, cls);
	}

	long ns(String string) throws ReflectiveOperationException {
		Class<?> nsString = Class.forName("org.eclipse.swt.internal.cocoa.NSString");
		Object s = nsString.getMethod("stringWith", String.class).invoke(null, string);
		return Class.forName("org.eclipse.swt.internal.cocoa.id").getField("id").getLong(s);
	}

	String string(long ns) throws ReflectiveOperationException {
		Class<?> nsString = Class.forName("org.eclipse.swt.internal.cocoa.NSString");
		Object s = nsString.getConstructor(long.class).newInstance(ns);
		return (String) nsString.getMethod("getString").invoke(s);
	}

	@Test
	public void test_observerRegisteredBeforeDisplayCanBeRemoved() throws ReflectiveOperationException {
		assumeTrue(SwtTestUtil.isCocoa, "Cocoa only");
		Display current = Display.getCurrent();
		if (current != null) current.dispose();
		long app = send(cls("NSApplication"), "sharedApplication");
		long observer = send(cls("NSObject"), "new");
		long keyPath = ns("windowsMenu");
		long context = 0x5157;
		send(app, "addObserver:forKeyPath:options:context:", observer, keyPath, 1, context);
		Display display = new Display();
		try {
			assertEquals("NSKVONotifying_SWTApplication", isa(app), "KVO lost on NSApp");
		} finally {
			display.dispose();
		}
		assertEquals("NSKVONotifying_NSApplication", isa(app), "KVO lost on NSApp after dispose");
		send(app, "removeObserver:forKeyPath:context:", observer, keyPath, context);
		long info = send(app, "observationInfo");
		String observances = info == 0 ? "" : string(send(info, "description"));
		assertFalse(observances.contains("Key path: windowsMenu"), "observer not removed: " + observances);
		send(observer, "release");
	}
}
