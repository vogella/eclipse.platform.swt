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

import java.awt.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.file.*;

/**
 * The natives of <code>SWT_AWT</code> on X11, which reach AWT through JAWT and the JNI view of
 * <code>sun.awt.X11.XEmbeddedFrame</code> just like <code>swt_awt.c</code>.
 */
public final class FFMAwt {

	static final int JAWT_VERSION_1_3 = 0x00010003, JAWT_LOCK_ERROR = 0x00000001;

	/* offsets in JAWT, JAWT_DrawingSurface and JAWT_X11DrawingSurfaceInfo */
	static final long JAWT_SIZEOF = 72, GET_DRAWING_SURFACE = 8, FREE_DRAWING_SURFACE = 16;
	static final long DS_LOCK = 16, DS_GET_INFO = 24, DS_FREE_INFO = 32, DS_UNLOCK = 40, DS_SIZEOF = 48;
	static final long X11_DRAWABLE = 0, X11_DISPLAY = 8, X11_SIZEOF = 16;

	static final MemorySegment GET_AWT = jawt().find("JAWT_GetAWT").orElseThrow(() -> new UnsatisfiedLinkError("JAWT_GetAWT"));
	static final MethodHandle X_SYNCHRONIZE = FFM.downcall("XSynchronize", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT))
		.asType(MethodType.methodType(void.class, MemorySegment.class, int.class));

	static final MemorySegment EMBEDDED_FRAME = FFMJni.cstring("sun/awt/X11/XEmbeddedFrame"), CONSTRUCTOR = FFMJni.cstring("<init>"),
		CONSTRUCTOR_SIGNATURE = FFMJni.cstring("(JZ)V"), VALIDATE_WITH_BOUNDS = FFMJni.cstring("validateWithBounds"),
		VALIDATE_WITH_BOUNDS_SIGNATURE = FFMJni.cstring("(IIII)V"), SYNTHESIZE_WINDOW_ACTIVATION = FFMJni.cstring("synthesizeWindowActivation"),
		SYNTHESIZE_WINDOW_ACTIVATION_SIGNATURE = FFMJni.cstring("(Z)V"), REGISTER_LISTENERS = FFMJni.cstring("registerListeners"),
		REGISTER_LISTENERS_SIGNATURE = FFMJni.cstring("()V");

	private FFMAwt() {
	}

	static SymbolLookup jawt() {
		try {
			return SymbolLookup.libraryLookup("libjawt.so", Arena.global());
		} catch (IllegalArgumentException e) {
			return SymbolLookup.libraryLookup(Path.of(System.getProperty("java.home"), "lib", "libjawt.so"), Arena.global());
		}
	}

	static MemorySegment function(MemorySegment struct, long offset) {
		return struct.get(ADDRESS, offset);
	}

	/**
	 * Returns the X11 drawable of <code>component</code>, or 0 if AWT has none, and applies
	 * <code>synchronize</code> (0 or 1, -1 for none) to its display while the surface is locked.
	 */
	static long drawingSurface(Object component, int synchronize) {
		try (Arena arena = Arena.ofConfined(); FFMJni jni = new FFMJni(component)) {
			MemorySegment awt = arena.allocate(JAWT_SIZEOF, 8);
			awt.set(JAVA_INT, 0, JAWT_VERSION_1_3);
			if ((FFMJni.call(GET_AWT, jni.env(), awt.address(), 0, 0) & 0xFF) == 0) return 0;
			MemorySegment getDrawingSurface = function(awt, GET_DRAWING_SURFACE), freeDrawingSurface = function(awt, FREE_DRAWING_SURFACE);
			long target = jni.argument();
			long address = FFMJni.call(getDrawingSurface, jni.env(), target, 0, 0);
			jni.check();
			if (address == 0) return 0;
			try {
				MemorySegment ds = MemorySegment.ofAddress(address).reinterpret(DS_SIZEOF);
				int lock = (int) FFMJni.call(function(ds, DS_LOCK), address, 0, 0, 0);
				if ((lock & JAWT_LOCK_ERROR) != 0) return 0;
				try {
					long dsi = FFMJni.call(function(ds, DS_GET_INFO), address, 0, 0, 0);
					if (dsi == 0) return 0;
					try {
						MemorySegment x11 = MemorySegment.ofAddress(dsi).reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0).reinterpret(X11_SIZEOF);
						if (synchronize != -1) X_SYNCHRONIZE.invokeExact(x11.get(ADDRESS, X11_DISPLAY), synchronize);
						return x11.get(JAVA_LONG, X11_DRAWABLE);
					} finally {
						FFMJni.call(function(ds, DS_FREE_INFO), dsi, 0, 0, 0);
					}
				} finally {
					FFMJni.call(function(ds, DS_UNLOCK), address, 0, 0, 0);
				}
			} finally {
				FFMJni.call(freeDrawingSurface, address, 0, 0, 0);
			}
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static long getAWTHandle(Object canvas) {
		return drawingSurface(canvas, -1);
	}

	public static void setDebug(Frame canvas, boolean debug) {
		drawingSurface(canvas, debug ? 1 : 0);
	}

	public static Object initFrame(long handle, String className) {
		try (FFMJni jni = new FFMJni(null)) {
			long clazz = jni.findClass(EMBEDDED_FRAME);
			long constructor = jni.method(clazz, CONSTRUCTOR, CONSTRUCTOR_SIGNATURE);
			return jni.result(jni.newObject(clazz, constructor, handle, 1, 0, 0));
		}
	}

	static void callEmbeddedFrame(Frame frame, MemorySegment name, MemorySegment signature, long a, long b, long c, long d) {
		try (FFMJni jni = new FFMJni(frame)) {
			long clazz = jni.findClass(EMBEDDED_FRAME);
			long method = jni.method(clazz, name, signature);
			jni.callVoid(jni.argument(), method, a, b, c, d);
		}
	}

	public static void validateWithBounds(Frame frame, int x, int y, int w, int h) {
		callEmbeddedFrame(frame, VALIDATE_WITH_BOUNDS, VALIDATE_WITH_BOUNDS_SIGNATURE, x, y, w, h);
	}

	public static void synthesizeWindowActivation(Frame frame, boolean doActivate) {
		callEmbeddedFrame(frame, SYNTHESIZE_WINDOW_ACTIVATION, SYNTHESIZE_WINDOW_ACTIVATION_SIGNATURE, doActivate ? 1 : 0, 0, 0, 0);
	}

	public static void registerListeners(Frame frame) {
		callEmbeddedFrame(frame, REGISTER_LISTENERS, REGISTER_LISTENERS_SIGNATURE, 0, 0, 0, 0);
	}
}
