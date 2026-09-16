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

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cairo.*;
import org.eclipse.swt.internal.ffm.*;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Rough per-call cost of JNI and FFM for typical parameter shapes, measured alternately in one process.
 * Not a JMH benchmark: use it to spot order-of-magnitude differences only.
 */
public class FFMBench {

	interface Call {
		void run();
	}

	static volatile Object sink;

	static double nanosPerCall(Call call, int iterations) {
		long start = System.nanoTime();
		for (int i = 0; i < iterations; i++) call.run();
		return (System.nanoTime() - start) / (double) iterations;
	}

	static void compare(String name, Call jni, Call ffm) {
		int iterations = 2_000_000;
		for (int i = 0; i < 3; i++) {
			nanosPerCall(jni, iterations);
			nanosPerCall(ffm, iterations);
		}
		double bestJni = Double.MAX_VALUE, bestFfm = Double.MAX_VALUE;
		for (int i = 0; i < 5; i++) {
			bestJni = Math.min(bestJni, nanosPerCall(jni, iterations));
			bestFfm = Math.min(bestFfm, nanosPerCall(ffm, iterations));
		}
		System.out.printf("%-55s JNI %7.1f ns  FFM %7.1f ns%n", name, bestJni, bestFfm);
	}

	public static long callbackTarget(long a, long b, long c) {
		return a + b + c;
	}

	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.open();
		long widget = shell.handle;
		long context = GDK.gdk_pango_context_get();
		long layout = OS.pango_layout_new(context);
		OS.pango_layout_set_text(layout, Converter.wcsToMbcs("Benchmark text", true), -1);
		long surface = Cairo.cairo_image_surface_create(Cairo.CAIRO_FORMAT_ARGB32, 64, 64);
		long cairo = Cairo.cairo_create(surface);
		int[] w = new int[1], h = new int[1];
		double[] matrix = new double[6], x = {1}, y = {2};
		Cairo.cairo_matrix_init_identity(matrix);
		GdkRectangle rect = new GdkRectangle();
		byte[] bytes = new byte[64];
		long buffer = C.malloc(64);

		compare("scalar: gtk_widget_get_visible(long)", () -> sink = GTK.gtk_widget_get_visible(widget), () -> sink = GTK_FFM.gtk_widget_get_visible(widget));
		compare("double: cairo_get_tolerance(long)", () -> sink = Cairo.cairo_get_tolerance(cairo), () -> sink = Cairo_FFM.cairo_get_tolerance(cairo));
		compare("critical byte[]: memmove(byte[], long, long)", () -> C.memmove(bytes, buffer, 64), () -> C_FFM.memmove(bytes, buffer, 64));
		compare("copied int[] x2: pango_layout_get_pixel_size", () -> OS.pango_layout_get_pixel_size(layout, w, h), () -> OS_FFM.pango_layout_get_pixel_size(layout, w, h));
		compare("copied double[] x3: cairo_matrix_transform_point", () -> Cairo.cairo_matrix_transform_point(matrix, x, y), () -> Cairo_FFM.cairo_matrix_transform_point(matrix, x, y));
		compare("struct out: gdk_cairo_get_clip_rectangle", () -> GDK.gdk_cairo_get_clip_rectangle(cairo, rect), () -> GDK_FFM.gdk_cairo_get_clip_rectangle(cairo, rect));

		// callbacks: the same Java method reached through a JNI trampoline and through an upcall stub
		Callback jniCallback = new Callback(FFMBench.class, "callbackTarget", 3);
		long jniProc = jniCallback.getAddress();
		long ffmProc = FFMCallback.bind(new Object(), FFMBench.class, "callbackTarget", "(JJJ)J", 3, true, false, 0);
		compare("callback: 3 long arguments through OS.call", () -> sink = OS.call(jniProc, 1, 2, 3, 0), () -> sink = OS.call(ffmProc, 1, 2, 3, 0));

		C.free(buffer);
		Cairo.cairo_destroy(cairo);
		Cairo.cairo_surface_destroy(surface);
		OS.g_object_unref(layout);
		OS.g_object_unref(context);
		display.dispose();
	}
}
