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
import static org.eclipse.swt.internal.ffm.FFMGtk4.*;

import java.lang.foreign.*;
import java.lang.invoke.*;

import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk4.*;

/**
 * Java port of SwtScaledPaintable of os_custom.c: a GdkPaintable that wraps a GdkTexture but
 * advertises a fixed logical size, so that GtkPicture does not inflate images at HiDPI while the
 * full resolution texture is still drawn.
 */
final class FFMScaledPaintable {

	/* the instance: GObject parent_instance, GdkTexture *texture, int width, int height */
	static final int TEXTURE = (int) Extra_FFM.GOBJECT, WIDTH = TEXTURE + 8, HEIGHT = WIDTH + 4, INSTANCE_SIZEOF = HEIGHT + 4;

	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	/** The name the type registers under, changeable so that a check can register a second one next to the C type. */
	static String typeName = "SwtScaledPaintable";

	static long type, parentClass;

	private FFMScaledPaintable() {
	}

	static long stub(String name, FunctionDescriptor descriptor) {
		return FFMGtk4.stub(LOOKUP, FFMScaledPaintable.class, name, descriptor);
	}

	static synchronized long getType() {
		if (type != 0) return type;
		long newType = registerType(G_TYPE_OBJECT, typeName, Structs_FFM.GObjectClass_SIZEOF,
			stub("classInit", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG)), INSTANCE_SIZEOF);
		addInterface(newType, GTK4_FFM.gdk_paintable_get_type(),
			stub("paintableInit", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG)));
		type = newType;
		return type;
	}

	static void classInit(long klass, long data) {
		try {
			parentClass = OS_FFM.g_type_class_peek_parent(klass);
			putLong(klass, Structs_FFM.GObjectClass_DISPOSE_OFFSET, stub("dispose", FunctionDescriptor.ofVoid(JAVA_LONG)));
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void paintableInit(long iface, long data) {
		try {
			putLong(iface, Extra_FFM.GDKPAINTABLEINTERFACE_SNAPSHOT, stub("snapshot", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE)));
			putLong(iface, Extra_FFM.GDKPAINTABLEINTERFACE_GET_INTRINSIC_WIDTH, stub("intrinsicWidth", FunctionDescriptor.of(JAVA_INT, JAVA_LONG)));
			putLong(iface, Extra_FFM.GDKPAINTABLEINTERFACE_GET_INTRINSIC_HEIGHT, stub("intrinsicHeight", FunctionDescriptor.of(JAVA_INT, JAVA_LONG)));
			putLong(iface, Extra_FFM.GDKPAINTABLEINTERFACE_GET_INTRINSIC_ASPECT_RATIO, stub("intrinsicAspectRatio", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG)));
			putLong(iface, Extra_FFM.GDKPAINTABLEINTERFACE_GET_FLAGS, stub("flags", FunctionDescriptor.of(JAVA_INT, JAVA_LONG)));
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static void snapshot(long paintable, long snapshot, double width, double height) {
		try {
			long texture = getLong(paintable, TEXTURE);
			// draws the full device resolution texture into the logical box
			if (texture != 0) GTK4_FFM.gdk_paintable_snapshot(texture, snapshot, width, height);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static int intrinsicWidth(long paintable) {
		try {
			return getInt(paintable, WIDTH);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0;
		}
	}

	static int intrinsicHeight(long paintable) {
		try {
			return getInt(paintable, HEIGHT);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0;
		}
	}

	static double intrinsicAspectRatio(long paintable) {
		try {
			int height = getInt(paintable, HEIGHT);
			if (height <= 0) return 0.0;
			return (double) getInt(paintable, WIDTH) / (double) height;
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0.0;
		}
	}

	static int flags(long paintable) {
		return GDK_PAINTABLE_STATIC_SIZE | GDK_PAINTABLE_STATIC_CONTENTS;
	}

	static void dispose(long object) {
		try {
			try {
				long texture = getLong(object, TEXTURE);
				putLong(object, TEXTURE, 0);
				if (texture != 0) OS_FFM.g_object_unref(texture);
			} finally {
				long function = getLong(parentClass, Structs_FFM.GObjectClass_DISPOSE_OFFSET);
				FFMSwtFixed4.PARENT_VOID.invokeExact(MemorySegment.ofAddress(function), object);
			}
		} catch (Throwable t) {
			FFM.callbackFailed(t);
		}
	}

	static long create(long texture, int width, int height) {
		try {
			long self = OS_FFM.g_object_new(getType(), 0L);
			putLong(self, TEXTURE, texture != 0 ? OS_FFM.g_object_ref(texture) : 0L);
			putInt(self, WIDTH, width);
			putInt(self, HEIGHT, height);
			return self;
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}
}
