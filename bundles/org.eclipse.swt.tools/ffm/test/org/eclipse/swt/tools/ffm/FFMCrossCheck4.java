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

import static org.eclipse.swt.tools.ffm.FFMCrossCheck.check;

import java.lang.reflect.*;
import java.util.*;

import org.eclipse.swt.internal.*;
import org.eclipse.swt.internal.cairo.*;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.gtk4.*;

/**
 * The call part of {@link FFMCrossCheck} on GTK4: the natives only GTK4 has, compared through JNI and FFM on a
 * realized window and on real events injected with XTest (<code>xinject.py</code>, whose path is the system property
 * <code>ffm.inject</code>).
 */
public class FFMCrossCheck4 {

	static int events;
	static final Map<String, Integer> eventKinds = new TreeMap<>();

	public void motion(long controller, double x, double y, long data) {
		event("motion", GTK4.gtk_event_controller_get_current_event(controller));
	}

	public void pressed(long gesture, int nPress, double x, double y, long data) {
		event("button", GTK4.gtk_event_controller_get_current_event(gesture));
	}

	public boolean scrolled(long controller, double dx, double dy, long data) {
		event("scroll", GTK4.gtk_event_controller_get_current_event(controller));
		return false;
	}

	public boolean keyPressed(long controller, int keyval, int keycode, int state, long data) {
		event("key", GTK4.gtk_event_controller_get_current_event(controller));
		return false;
	}

	/** Reads the same event through JNI and through FFM. */
	static void event(String kind, long event) {
		if (event == 0) return;
		events++;
		eventKinds.merge(kind, 1, Integer::sum);
		String at = kind + " #" + events;
		double[] jx = new double[1], jy = new double[1], fx = new double[1], fy = new double[1];
		check(at + " gdk_event_get_position", GDK.gdk_event_get_position(event, jx, jy), GDK_FFM.gdk_event_get_position(event, fx, fy));
		check(at + " gdk_event_get_position values", new double[] {jx[0], jy[0]}, new double[] {fx[0], fy[0]});
		check(at + " gdk_event_get_surface", GDK.gdk_event_get_surface(event), GDK_FFM.gdk_event_get_surface(event));
		check(at + " gdk_event_get_seat", GDK.gdk_event_get_seat(event), GDK_FFM.gdk_event_get_seat(event));
		check(at + " gdk_event_get_event_type", GDK.gdk_event_get_event_type(event), GDK_FFM.gdk_event_get_event_type(event));
		check(at + " gdk_event_get_time", GDK.gdk_event_get_time(event), GDK_FFM.gdk_event_get_time(event));
		check(at + " gdk_event_get_modifier_state", GDK.gdk_event_get_modifier_state(event), GDK_FFM.gdk_event_get_modifier_state(event));
		check(at + " gdk_event_ref", GDK.gdk_event_ref(event), GDK_FFM.gdk_event_ref(event));
		GDK.gdk_event_unref(event);
		GDK_FFM.gdk_event_unref(event);
		switch (kind) {
			case "button":
				check(at + " gdk_button_event_get_button", GDK.gdk_button_event_get_button(event), GDK_FFM.gdk_button_event_get_button(event));
				break;
			case "scroll":
				double[] jdx = new double[1], jdy = new double[1], fdx = new double[1], fdy = new double[1];
				GDK.gdk_scroll_event_get_deltas(event, jdx, jdy);
				GDK_FFM.gdk_scroll_event_get_deltas(event, fdx, fdy);
				check(at + " gdk_scroll_event_get_deltas", new double[] {jdx[0], jdy[0]}, new double[] {fdx[0], fdy[0]});
				check(at + " gdk_scroll_event_get_direction", GDK.gdk_scroll_event_get_direction(event), GDK_FFM.gdk_scroll_event_get_direction(event));
				break;
			case "key":
				check(at + " gdk_key_event_get_keyval", GDK.gdk_key_event_get_keyval(event), GDK_FFM.gdk_key_event_get_keyval(event));
				check(at + " gdk_key_event_get_keycode", GDK.gdk_key_event_get_keycode(event), GDK_FFM.gdk_key_event_get_keycode(event));
				check(at + " gdk_key_event_get_layout", GDK.gdk_key_event_get_layout(event), GDK_FFM.gdk_key_event_get_layout(event));
				break;
			default:
		}
	}

	static byte[] ascii(String s) {
		return Converter.wcsToMbcs(s, true);
	}

	static void iterate(int rounds) {
		for (int i = 0; i < rounds && OS.g_main_context_iteration(0, false); i++) {
			// dispatch what is pending
		}
	}

	static void expectUnsatisfied(String what, Runnable jni, Runnable ffm) {
		check(what, thrown(jni), thrown(ffm));
	}

	static String thrown(Runnable run) {
		try {
			run.run();
			return "returned";
		} catch (UnsatisfiedLinkError e) {
			return "UnsatisfiedLinkError";
		}
	}

	static void checkFunctions() throws Exception {
		int start = FFMCrossCheck.checks;
		check("gtk_init_check", GTK4.gtk_init_check(), true);
		check("gtk_get_major_version", GTK.gtk_get_major_version(), GTK_FFM.gtk_get_major_version());
		check("gtk_get_minor_version", GTK.gtk_get_minor_version(), GTK_FFM.gtk_get_minor_version());
		check("GTK is version 4", GTK_FFM.gtk_get_major_version(), 4);
		long display = GDK.gdk_display_get_default();
		check("gdk_display_get_default", display, GDK_FFM.gdk_display_get_default());

		// scalars and float out parameters
		float[][] hsv = {{0, 0, 0}, {0.1f, 0.5f, 0.75f}, {0.5f, 1, 1}, {0.99f, 0.25f, 0.125f}};
		for (float[] c : hsv) {
			float[] jr = new float[1], jg = new float[1], jb = new float[1], fr = new float[1], fg = new float[1], fb = new float[1];
			GTK4.gtk_hsv_to_rgb(c[0], c[1], c[2], jr, jg, jb);
			GTK4_FFM.gtk_hsv_to_rgb(c[0], c[1], c[2], fr, fg, fb);
			check("gtk_hsv_to_rgb " + Arrays.toString(c), new float[] {jr[0], jg[0], jb[0]}, new float[] {fr[0], fg[0], fb[0]});
			GTK4.gtk_rgb_to_hsv(c[0], c[1], c[2], jr, jg, jb);
			GTK4_FFM.gtk_rgb_to_hsv(c[0], c[1], c[2], fr, fg, fb);
			check("gtk_rgb_to_hsv " + Arrays.toString(c), new float[] {jr[0], jg[0], jb[0]}, new float[] {fr[0], fg[0], fb[0]});
		}
		check("gdk_keyval_name", utf8(GDK.gdk_keyval_name(0x61)), utf8(GDK_FFM.gdk_keyval_name(0x61)));
		check("gdk_keyval_name Return", utf8(GDK.gdk_keyval_name(0xff0d)), utf8(GDK_FFM.gdk_keyval_name(0xff0d)));

		// a realized window with a child
		long window = GTK4.gtk_window_new();
		long label = GTK.gtk_label_new(ascii("FFM cross check on GTK4"));
		GTK4.gtk_window_set_child(window, label);
		GTK.gtk_widget_set_size_request(window, 300, 200);
		GTK4_FFM.gtk_window_set_icon_name(window, 0);
		check("gtk_window_get_icon_name", GTK4.gtk_window_get_icon_name(window), GTK4_FFM.gtk_window_get_icon_name(window));
		check("gtk_widget_get_first_child", GTK4.gtk_widget_get_first_child(window), GTK4_FFM.gtk_widget_get_first_child(window));
		check("gtk_widget_get_last_child", GTK4.gtk_widget_get_last_child(window), GTK4_FFM.gtk_widget_get_last_child(window));
		check("gtk_widget_get_next_sibling", GTK4.gtk_widget_get_next_sibling(label), GTK4_FFM.gtk_widget_get_next_sibling(label));
		check("gtk_widget_get_root", GTK4.gtk_widget_get_root(label), GTK4_FFM.gtk_widget_get_root(label));
		check("gtk_widget_get_native", GTK4.gtk_widget_get_native(label), GTK4_FFM.gtk_widget_get_native(label));
		check("gtk_window_is_maximized", GTK4.gtk_window_is_maximized(window), GTK4_FFM.gtk_window_is_maximized(window));
		int[][] jm = new int[4][1], fm = new int[4][1];
		for (int orientation = 0; orientation < 2; orientation++) {
			GTK4.gtk_widget_measure(label, orientation, -1, jm[0], jm[1], jm[2], jm[3]);
			GTK4_FFM.gtk_widget_measure(label, orientation, -1, fm[0], fm[1], fm[2], fm[3]);
			check("gtk_widget_measure " + orientation, jm, fm);
		}
		int[] jw = new int[1], jh = new int[1], fw = new int[1], fh = new int[1];
		GTK4.gtk_widget_get_size_request(window, jw, jh);
		GTK4_FFM.gtk_widget_get_size_request(window, fw, fh);
		check("gtk_widget_get_size_request", new int[] {jw[0], jh[0]}, new int[] {fw[0], fh[0]});
		check("gtk_widget_get_size_request values", fw[0] == 300 && fh[0] == 200, true);

		long motion = GTK4.gtk_event_controller_motion_new();
		long click = GTK4.gtk_gesture_click_new();
		long scroll = GTK4.gtk_event_controller_scroll_new(3);
		long key = GTK4.gtk_event_controller_key_new();
		long ffmMotion = GTK4_FFM.gtk_event_controller_motion_new();
		check("gtk_event_controller_motion_new", motion != 0, ffmMotion != 0);
		check("gtk_event_controller_key_new type", OS.G_OBJECT_TYPE_NAME(key), OS.G_OBJECT_TYPE_NAME(GTK4_FFM.gtk_event_controller_key_new()));
		OS.g_object_unref(ffmMotion);
		FFMCrossCheck4 handler = new FFMCrossCheck4();
		Callback motionCallback = new Callback(handler, "motion", void.class, new Type[] {long.class, double.class, double.class, long.class});
		Callback pressedCallback = new Callback(handler, "pressed", void.class, new Type[] {long.class, int.class, double.class, double.class, long.class});
		Callback scrollCallback = new Callback(handler, "scrolled", boolean.class, new Type[] {long.class, double.class, double.class, long.class});
		Callback keyCallback = new Callback(handler, "keyPressed", boolean.class, new Type[] {long.class, int.class, int.class, int.class, long.class});
		OS.g_signal_connect(motion, ascii("motion"), motionCallback.getAddress(), 0);
		OS.g_signal_connect(click, ascii("pressed"), pressedCallback.getAddress(), 0);
		OS.g_signal_connect(scroll, ascii("scroll"), scrollCallback.getAddress(), 0);
		OS.g_signal_connect(key, ascii("key-pressed"), keyCallback.getAddress(), 0);
		GTK4.gtk_widget_add_controller(window, motion);
		GTK4.gtk_widget_add_controller(window, click);
		GTK4.gtk_widget_add_controller(window, scroll);
		GTK4.gtk_widget_add_controller(window, key);
		GTK4.gtk_widget_set_focusable(window, true);
		GTK4.gtk_window_present(window);

		Process injector = null;
		String inject = System.getProperty("ffm.inject");
		if (inject != null) injector = new ProcessBuilder("python3", "-I", inject, "1.0").inheritIO().start();
		long end = System.currentTimeMillis() + 15000;
		long idle = 0;
		while (System.currentTimeMillis() < end) {
			boolean busy = OS.g_main_context_iteration(0, false);
			if (!busy) Thread.sleep(10);
			if (injector != null && !injector.isAlive()) {
				if (idle == 0) idle = System.currentTimeMillis() + 500;
				if (System.currentTimeMillis() > idle) break;
			}
		}
		System.out.println("Events seen: " + eventKinds);

		long surface = GTK4.gtk_native_get_surface(window);
		check("gtk_native_get_surface", surface != 0, true);
		check("gtk_widget_get_width", GTK4.gtk_widget_get_width(window), GTK4_FFM.gtk_widget_get_width(window));
		check("gtk_widget_get_height", GTK4.gtk_widget_get_height(window), GTK4_FFM.gtk_widget_get_height(window));
		check("gdk_surface_get_width", GDK.gdk_surface_get_width(surface), GDK_FFM.gdk_surface_get_width(surface));
		check("gdk_surface_get_height", GDK.gdk_surface_get_height(surface), GDK_FFM.gdk_surface_get_height(surface));
		check("gdk_surface_get_display", GDK.gdk_surface_get_display(surface), GDK_FFM.gdk_surface_get_display(surface));
		check("gdk_x11_surface_get_xid", GDK.gdk_x11_surface_get_xid(surface), GDK_FFM.gdk_x11_surface_get_xid(surface));
		check("gdk_x11_surface_get_xid is set", GDK_FFM.gdk_x11_surface_get_xid(surface) != 0, true);
		check("gtk_native_get_for_surface", GTK4.gtk_native_get_for_surface(surface), GTK4_FFM.gtk_native_get_for_surface(surface));
		check("gdk_display_get_monitor_at_surface", GDK.gdk_display_get_monitor_at_surface(display, surface), GDK_FFM.gdk_display_get_monitor_at_surface(display, surface));
		long seat = GDK.gdk_display_get_default_seat(display);
		long pointer = GDK.gdk_seat_get_pointer(seat);
		double[] jdx = new double[1], jdy = new double[1], fdx = new double[1], fdy = new double[1];
		int[] jmask = new int[1], fmask = new int[1];
		GDK.gdk_surface_get_device_position(surface, pointer, jdx, jdy, jmask);
		GDK_FFM.gdk_surface_get_device_position(surface, pointer, fdx, fdy, fmask);
		check("gdk_surface_get_device_position", new double[] {jdx[0], jdy[0], jmask[0]}, new double[] {fdx[0], fdy[0], fmask[0]});
		double[] jtx = new double[1], jty = new double[1], ftx = new double[1], fty = new double[1];
		check("gtk_widget_translate_coordinates", GTK4.gtk_widget_translate_coordinates(label, window, 3.5, 4.25, jtx, jty),
			GTK4_FFM.gtk_widget_translate_coordinates(label, window, 3.5, 4.25, ftx, fty));
		check("gtk_widget_translate_coordinates values", new double[] {jtx[0], jty[0]}, new double[] {ftx[0], fty[0]});
		check("gtk_widget_pick", GTK4.gtk_widget_pick(window, 5.5, 6.5, 0), GTK4_FFM.gtk_widget_pick(window, 5.5, 6.5, 0));

		// struct parameters
		GdkRGBA jrgba = new GdkRGBA(), frgba = new GdkRGBA();
		check("gdk_rgba_parse", GDK.gdk_rgba_parse(jrgba, ascii("rgba(255, 128, 0, 0.25)")) != 0, GDK_FFM.gdk_rgba_parse(frgba, ascii("rgba(255, 128, 0, 0.25)")) != 0);
		check("gdk_rgba_parse struct", jrgba, frgba);
		check("gdk_rgba_parse struct values", frgba.green > 0.49 && frgba.green < 0.51 && frgba.alpha == 0.25, true);
		long jstring = GDK.gdk_rgba_to_string(jrgba), fstring = GDK_FFM.gdk_rgba_to_string(frgba);
		check("gdk_rgba_to_string", utf8(jstring), utf8(fstring));
		OS.g_free(jstring);
		OS.g_free(fstring);
		long context = GTK.gtk_widget_get_style_context(label);
		GdkRGBA jcolor = new GdkRGBA(), fcolor = new GdkRGBA();
		GTK4.gtk_style_context_get_color(context, jcolor);
		GTK4_FFM.gtk_style_context_get_color(context, fcolor);
		check("gtk_style_context_get_color", jcolor, fcolor);
		GtkBorder jborder = new GtkBorder(), fborder = new GtkBorder();
		GTK4.gtk_style_context_get_padding(context, jborder);
		GTK4_FFM.gtk_style_context_get_padding(context, fborder);
		check("gtk_style_context_get_padding", jborder, fborder);
		GdkRectangle anchor = new GdkRectangle();
		anchor.x = 3;
		anchor.y = 4;
		anchor.width = 50;
		anchor.height = 60;
		long jlayout = GDK.gdk_popup_layout_new(anchor, 1, 2), flayout = GDK_FFM.gdk_popup_layout_new(anchor, 1, 2);
		check("gdk_popup_layout_new", jlayout != 0, flayout != 0);
		GdkRectangle jmonitor = new GdkRectangle(), fmonitor = new GdkRectangle();
		long monitor = GDK.gdk_display_get_monitor_at_surface(display, surface);
		GDK.gdk_monitor_get_geometry(monitor, jmonitor);
		GDK_FFM.gdk_monitor_get_geometry(monitor, fmonitor);
		check("gdk_monitor_get_geometry", jmonitor, fmonitor);

		// graphene: functions of a library no GTK3 header declares
		long jrect = Graphene.graphene_rect_alloc(), frect = Graphene_FFM.graphene_rect_alloc();
		check("graphene_rect_alloc", jrect != 0, frect != 0);
		check("graphene_rect_init returns its argument", Graphene.graphene_rect_init(jrect, 1.5f, -2.25f, 30.5f, 40.75f) == jrect, Graphene_FFM.graphene_rect_init(frect, 1.5f, -2.25f, 30.5f, 40.75f) == frect);
		byte[] jbytes = new byte[16], fbytes = new byte[16];
		C.memmove(jbytes, jrect, 16);
		C.memmove(fbytes, frect, 16);
		check("graphene_rect memory", jbytes, fbytes);
		Graphene.graphene_rect_free(jrect);
		Graphene_FFM.graphene_rect_free(frect);

		// pointer and string results, a NULL terminated string array
		long pixbuf = GDK.gdk_pixbuf_new(0, true, 8, 4, 3);
		long jtexture = GDK.gdk_texture_new_for_pixbuf(pixbuf), ftexture = GDK_FFM.gdk_texture_new_for_pixbuf(pixbuf);
		check("gdk_texture_new_for_pixbuf", OS.G_OBJECT_TYPE_NAME(jtexture), OS.G_OBJECT_TYPE_NAME(ftexture));
		OS.g_object_unref(jtexture);
		OS.g_object_unref(ftexture);
		OS.g_object_unref(pixbuf);
		long clipboard = GTK4.gtk_widget_get_clipboard(window);
		check("gdk_clipboard_is_local", GTK4.gdk_clipboard_is_local(clipboard), GTK4_FFM.gdk_clipboard_is_local(clipboard));
		GTK4_FFM.gdk_clipboard_set_text(clipboard, ascii("ffm clipboard text"));
		check("gdk_clipboard_is_local after set_text", GTK4.gdk_clipboard_is_local(clipboard), true);
		long formats = GTK4.gdk_clipboard_get_formats(clipboard);
		long jmime = GTK4.gdk_content_formats_to_string(formats), fmime = GTK4_FFM.gdk_content_formats_to_string(formats);
		check("gdk_content_formats_to_string", utf8(jmime), utf8(fmime));
		long builder = GTK4_FFM.gdk_content_formats_builder_new();
		GTK4_FFM.gdk_content_formats_builder_add_mime_type(builder, ascii("text/plain;charset=utf-8"));
		GTK4_FFM.gdk_content_formats_builder_add_mime_type(builder, ascii("image/png"));
		long built = GTK4_FFM.gdk_content_formats_builder_free_to_formats(builder);
		check("formats built through FFM", utf8(GTK4.gdk_content_formats_to_string(built)).contains("image/png"), true);
		long[] jn = new long[1], fn = new long[1];
		long jtypes = GTK4.gdk_content_formats_get_mime_types(built, jn), ftypes = GTK4_FFM.gdk_content_formats_get_mime_types(built, fn);
		check("gdk_content_formats_get_mime_types", jtypes, ftypes);
		check("gdk_content_formats_get_mime_types count", jn[0], fn[0]);
		GTK4.gdk_content_formats_unref(built);

		FFMCrossCheckTypes.checkTypes(label);
		FFMCrossCheckTypes.checkGtk4();

		// dynamic natives of GTK3 are absent from GTK4: no call, result 0, with both implementations
		check("gtk_accel_group_new is missing", GTK.gtk_accel_group_new(), GTK_FFM.gtk_accel_group_new());
		check("gtk_accel_group_new result", GTK_FFM.gtk_accel_group_new(), 0L);
		// GTK3 only natives are not linkable in a GTK4 process
		expectUnsatisfied("GTK3 only native gdk_window_get_width", () -> GDK.gdk_window_get_width(0), () -> GDK_FFM.gdk_window_get_width(0));

		GTK4.gtk_window_destroy(window);
		iterate(100);
		System.out.println("Compared " + (FFMCrossCheck.checks - start) + " function and event results");
		check("events delivered by XTest", events > 0 || System.getProperty("ffm.inject") == null, true);
		for (String kind : new String[] {"motion", "button", "scroll"}) {
			check("event kind " + kind + " seen", System.getProperty("ffm.inject") == null || eventKinds.containsKey(kind), true);
		}
	}

	static String utf8(long pointer) {
		if (pointer == 0) return null;
		int length = C.strlen(pointer);
		byte[] bytes = new byte[length];
		C.memmove(bytes, pointer, length);
		return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
	}
}
