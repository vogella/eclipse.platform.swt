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

import java.lang.foreign.*;
import java.lang.invoke.*;

/**
 * Java port of the GObject constructor overrides of os_custom.c. Each one calls the constructor of
 * the super class and then does what SWT needs, which for most of them is nothing at all.
 */
public final class FFMConstructorProc {

	/** GObject constructor: GType, guint n_construct_properties, GObjectConstructParam *. */
	static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG);

	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	static MethodHandle imContextSuper, pangoLayoutSuper, pangoFontFamilySuper, pangoFontFaceSuper, printerOptionWidgetSuper;
	static long lastIMContext;

	private FFMConstructorProc() {
	}

	public static long imContextNewProc_CALLBACK(long superProc) {
		imContextSuper = downcall(superProc);
		return stub("imContext");
	}

	public static long imContextLast() {
		return lastIMContext;
	}

	public static long pangoLayoutNewProc_CALLBACK(long superProc) {
		pangoLayoutSuper = downcall(superProc);
		return stub("pangoLayout");
	}

	public static long pangoFontFamilyNewProc_CALLBACK(long superProc) {
		pangoFontFamilySuper = downcall(superProc);
		return stub("pangoFontFamily");
	}

	public static long pangoFontFaceNewProc_CALLBACK(long superProc) {
		pangoFontFaceSuper = downcall(superProc);
		return stub("pangoFontFace");
	}

	public static long printerOptionWidgetNewProc_CALLBACK(long superProc) {
		printerOptionWidgetSuper = downcall(superProc);
		return stub("printerOptionWidget");
	}

	/* ---------------------------------------------------------------- the overrides */

	static long imContext(long type, int count, long properties) {
		lastIMContext = call(imContextSuper, type, count, properties);
		return lastIMContext;
	}

	static long pangoLayout(long type, int count, long properties) {
		long layout = call(pangoLayoutSuper, type, count, properties);
		if (layout != 0) {
			try {
				SET_AUTO_DIR.invokeExact(layout, 0);
			} catch (Throwable t) {
				FFM.callbackFailed(t);
			}
		}
		return layout;
	}

	static long pangoFontFamily(long type, int count, long properties) {
		return call(pangoFontFamilySuper, type, count, properties);
	}

	static long pangoFontFace(long type, int count, long properties) {
		return call(pangoFontFaceSuper, type, count, properties);
	}

	static long printerOptionWidget(long type, int count, long properties) {
		return call(printerOptionWidgetSuper, type, count, properties);
	}

	static final MethodHandle SET_AUTO_DIR = FFM.downcall("pango_layout_set_auto_dir", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT));

	/* ---------------------------------------------------------------- plumbing */

	static MethodHandle downcall(long proc) {
		return FFM.LINKER.downcallHandle(MemorySegment.ofAddress(proc), DESCRIPTOR);
	}

	/** Runs inside an upcall, so a failure is left pending for the calling downcall instead of thrown into GTK. */
	static long call(MethodHandle superProc, long type, int count, long properties) {
		try {
			return (long) superProc.invokeExact(type, count, properties);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0;
		}
	}

	static long stub(String name) {
		try {
			MethodHandle handle = LOOKUP.findStatic(FFMConstructorProc.class, name, DESCRIPTOR.toMethodType());
			return FFM.LINKER.upcallStub(handle, DESCRIPTOR, Arena.global()).address();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
