/*******************************************************************************
 * Copyright (c) 2021, 2024 Syntevo and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Syntevo - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.gtk3;

import org.eclipse.swt.internal.gtk.*;

/**
 * This class contains native functions that are present in GTK3 only.
 */
public class GTK3 {

	/* Macros */
	public static final boolean GTK_IS_MENU_ITEM(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_MENU_ITEM(obj); }
	/** @method flags=const */
	public static final long GTK_TYPE_MENU() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_MENU(); }

	/**
	 * @param context cast=(GtkIMContext *)
	 * @param event cast=(GdkEventKey *)
	 */
	public static final boolean gtk_im_context_filter_keypress(long context, long event) { return GTK3_FFM.gtk_im_context_filter_keypress(context, event); }

	/* GtkButton */
	/**
	 * @param button cast=(GtkButton *)
	 * @param image cast=(GtkWidget *)
	 */
	public static final void gtk_button_set_image(long button, long image) { GTK3_FFM.gtk_button_set_image(button, image); }

	/* GtkAccelLabel */
	/**
	 * @param label cast=(const gchar *)
	 */
	public static final long gtk_accel_label_new(byte[] label) { return GTK3_FFM.gtk_accel_label_new(label); }
	/**
	 * @param accel_label cast=(GtkAccelLabel *)
	 * @param accel_widget cast=(GtkWidget *)
	 */
	public static final void gtk_accel_label_set_accel_widget(long accel_label, long accel_widget) { GTK3_FFM.gtk_accel_label_set_accel_widget(accel_label, accel_widget); }
	/**
	 * @param accel_label cast=(GtkAccelLabel *)
	 * @param accel_key cast=(guint)
	 * @param accel_mods cast=(GdkModifierType)
	 */
	public static final void gtk_accel_label_set_accel(long accel_label, int accel_key, int accel_mods) { GTK3_FFM.gtk_accel_label_set_accel(accel_label, accel_key, accel_mods); }

	/* GtkBin */
	/** @param bin cast=(GtkBin *) */
	public static final long gtk_bin_get_child(long bin) { return GTK3_FFM.gtk_bin_get_child(bin); }

	/* GtkBox */
	/**
	 * @param box cast=(GtkBox *)
	 * @param child cast=(GtkWidget *)
	 */
	public static final void gtk_box_set_child_packing(long box, long child, boolean expand, boolean fill, int padding, int pack_type) { GTK3_FFM.gtk_box_set_child_packing(box, child, expand, fill, padding, pack_type); }
	/**
	 * @param box cast=(GtkBox *)
	 * @param child cast=(GtkWidget *)
	 * @param position cast=(gint)
	 */
	public static final void gtk_box_reorder_child(long box, long child, int position) { GTK3_FFM.gtk_box_reorder_child(box, child, position); }
	/**
	 * @param box cast=(GtkBox *)
	 * @param widget cast=(GtkWidget *)
	 * @param expand cast=(gboolean)
	 * @param fill cast=(gboolean)
	 * @param padding cast=(guint)
	 */
	public static final void gtk_box_pack_end(long box, long widget, boolean expand, boolean fill, int padding) { GTK3_FFM.gtk_box_pack_end(box, widget, expand, fill, padding); }

	/* GtkCalendar */
	/**
	 * @param calendar cast=(GtkCalendar *)
	 * @param month cast=(guint)
	 * @param year cast=(guint)
	 */
	public static final void gtk_calendar_select_month(long calendar, int month, int year) { GTK3_FFM.gtk_calendar_select_month(calendar, month, year); }
	/**
	 * @param calendar cast=(GtkCalendar *)
	 * @param day cast=(guint)
	 */
	public static final void gtk_calendar_select_day(long calendar, int day) { GTK3_FFM.gtk_calendar_select_day(calendar, day); }
	/**
	 * @param calendar cast=(GtkCalendar *)
	 * @param flags cast=(GtkCalendarDisplayOptions)
	 */
	public static final void gtk_calendar_set_display_options(long calendar, int flags) { GTK3_FFM.gtk_calendar_set_display_options(calendar, flags); }
	/**
	 * @param calendar cast=(GtkCalendar *)
	 * @param year cast=(guint *)
	 * @param month cast=(guint *)
	 * @param day cast=(guint *)
	 */
	public static final void gtk_calendar_get_date(long calendar, int[] year, int[] month, int[] day) { GTK3_FFM.gtk_calendar_get_date(calendar, year, month, day); }

	/* GtkColorChooser Interface */
	/**
	 * @param h cast=(gdouble)
	 * @param s cast=(gdouble)
	 * @param v cast=(gdouble)
	 * @param r cast=(gdouble *)
	 * @param g cast=(gdouble *)
	 * @param b cast=(gdouble *)
	 */
	public static final void gtk_hsv_to_rgb(double h, double s, double v, double[] r, double[] g, double[] b) { GTK3_FFM.gtk_hsv_to_rgb(h, s, v, r, g, b); }
	/**
	 * @param r cast=(gdouble)
	 * @param g cast=(gdouble)
	 * @param b cast=(gdouble)
	 * @param h cast=(gdouble *)
	 * @param s cast=(gdouble *)
	 * @param v cast=(gdouble *)
	 */
	public static final void gtk_rgb_to_hsv(double r, double g, double b, double[] h, double[] s, double[] v) { GTK3_FFM.gtk_rgb_to_hsv(r, g, b, h, s, v); }

	/* GtkContainer */
	/**
	 * @param container cast=(GtkContainer *)
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_container_add(long container, long widget) { GTK3_FFM.gtk_container_add(container, widget); }
	// Do not confuse this function with gtk_container_foreach(..).
	// Make sure you know what you are doing when using this. Please be attentive to swt_fixed_forall(..)
	// found in os_custom.c, which overrides this function for swtFixed container with custom behaviour.
	/**
	 * @param container cast=(GtkContainer *)
	 * @param callback cast=(GtkCallback)
	 * @param callback_data cast=(gpointer)
	 */
	public static final void gtk_container_forall(long container, long callback, long callback_data) { GTK3_FFM.gtk_container_forall(container, callback, callback_data); }
	/**
	 * @param container cast=(GtkContainer *)
	 * @param child cast=(GtkWidget *)
	 * @param cairo cast=(cairo_t *)
	 */
	public static final void gtk_container_propagate_draw(long container, long child, long cairo) { GTK3_FFM.gtk_container_propagate_draw(container, child, cairo); }
	/** @param container cast=(GtkContainer *) */
	public static final int gtk_container_get_border_width(long container) { return GTK3_FFM.gtk_container_get_border_width(container); }
	/** @param container cast=(GtkContainer *) */
	public static final long gtk_container_get_children(long container) { return GTK3_FFM.gtk_container_get_children(container); }
	/**
	 * @param container cast=(GtkContainer *)
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_container_remove(long container, long widget) { GTK3_FFM.gtk_container_remove(container, widget); }
	/**
	 * @param container cast=(GtkContainer *)
	 * @param border_width cast=(guint)
	 */
	public static final void gtk_container_set_border_width(long container, int border_width) { GTK3_FFM.gtk_container_set_border_width(container, border_width); }

	/* GtkDialog */
	/** @param dialog cast=(GtkDialog *) */
	public static final int gtk_dialog_run(long dialog) { return GTK3_FFM.gtk_dialog_run(dialog); }

	/* GTK Initialization */
	/**
	 * @param argc cast=(int *)
	 * @param argv cast=(char ***)
	 */
	public static final boolean gtk_init_check(long [] argc, long [] argv) { return GTK3_FFM.gtk_init_check(argc, argv); }

	/* GtkGrab */
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_grab_add(long widget) { GTK3_FFM.gtk_grab_add(widget); }
	public static final long gtk_grab_get_current() { return GTK3_FFM.gtk_grab_get_current(); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_grab_remove(long widget) { GTK3_FFM.gtk_grab_remove(widget); }

	/* Events */
	public static final long gtk_get_current_event() { return GTK3_FFM.gtk_get_current_event(); }
	/** @param state cast=(GdkModifierType*) */
	public static final boolean gtk_get_current_event_state(int[] state) { return GTK3_FFM.gtk_get_current_event_state(state); }
	/** @param event cast=(GdkEvent *) */
	public static final long gtk_get_event_widget(long event) { return GTK3_FFM.gtk_get_event_widget(event); }
	/** @param event cast=(GdkEvent *) */
	public static final void gtk_main_do_event(long event) { GTK3_FFM.gtk_main_do_event(event); }
	public static final boolean gtk_main_iteration_do(boolean blocking) { return GTK3_FFM.gtk_main_iteration_do(blocking); }
	public static final boolean gtk_events_pending() { return GTK3_FFM.gtk_events_pending(); }

	/* GtkWindow */
	/**
	 * @param window cast=(GtkWindow *)
	 * @param list cast=(GList *)
	 */
	public static final void gtk_window_set_icon_list(long window, long list) { GTK3_FFM.gtk_window_set_icon_list(window, list); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param accel_group cast=(GtkAccelGroup *)
	 */
	public static final void gtk_window_add_accel_group(long window, long accel_group) { GTK3_FFM.gtk_window_add_accel_group(window, accel_group); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param accel_group cast=(GtkAccelGroup *)
	 */
	public static final void gtk_window_remove_accel_group(long window, long accel_group) { GTK3_FFM.gtk_window_remove_accel_group(window, accel_group); }
	/** @param handle cast=(GtkWindow *) */
	public static final void gtk_window_deiconify(long handle) { GTK3_FFM.gtk_window_deiconify(handle); }
	/** @param handle cast=(GtkWindow *) */
	public static final void gtk_window_iconify(long handle) { GTK3_FFM.gtk_window_iconify(handle); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_window_set_default(long window, long widget) { GTK3_FFM.gtk_window_set_default(window, widget); }
	/** @param window cast=(GtkWindow *) */
	public static final boolean gtk_window_activate_default(long window) { return GTK3_FFM.gtk_window_activate_default(window); }
	/** @param window cast=(GtkWindow *) */
	public static final void gtk_window_set_type_hint(long window, int hint) { GTK3_FFM.gtk_window_set_type_hint(window, hint); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param skips_taskbar cast=(gboolean)
	 */
	public static final void gtk_window_set_skip_taskbar_hint(long window, boolean skips_taskbar) { GTK3_FFM.gtk_window_set_skip_taskbar_hint(window, skips_taskbar); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param setting cast=(gboolean)
	 */
	public static final void gtk_window_set_keep_above(long window, boolean setting) { GTK3_FFM.gtk_window_set_keep_above(window, setting); }
	/** @param window cast=(GtkWindow *) */
	public static final long gtk_window_get_icon_list(long window) { return GTK3_FFM.gtk_window_get_icon_list(window); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param attach_widget cast=(GtkWidget *)
	 */
	public static final void gtk_window_set_attached_to(long window, long attach_widget) { GTK3_FFM.gtk_window_set_attached_to(window, attach_widget); }
	/**
	 * @param handle cast=(GtkWindow *)
	 * @param x cast=(gint)
	 * @param y cast=(gint)
	 */
	public static final void gtk_window_move(long handle, int x, int y) { GTK3_FFM.gtk_window_move(handle, x, y); }
	/** @param type cast=(GtkWindowType) */
	public static final long gtk_window_new(int type) { return GTK3_FFM.gtk_window_new(type); }
	/**
	 * @param handle cast=(GtkWindow *)
	 * @param x cast=(gint *)
	 * @param y cast=(gint *)
	 */
	public static final void gtk_window_get_position(long handle, int[] x, int[] y) { GTK3_FFM.gtk_window_get_position(handle, x, y); }
	/** @param window cast=(GtkWindow *) */
	public static final int gtk_window_get_mnemonic_modifier(long window) { return GTK3_FFM.gtk_window_get_mnemonic_modifier(window); }
	/**
	 * @param handle cast=(GtkWindow *)
	 * @param x cast=(gint)
	 * @param y cast=(gint)
	 */
	public static final void gtk_window_resize(long handle, int x, int y) { GTK3_FFM.gtk_window_resize(handle, x, y); }
	/**
	 * @param handle cast=(GtkWindow *)
	 * @param width cast=(gint *)
	 * @param height cast=(gint *)
	 */
	public static final void gtk_window_get_size(long handle, int[] width, int[] height) { GTK3_FFM.gtk_window_get_size(handle, width, height); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param geometry_widget cast=(GtkWidget *)
	 * @param geometry flags=no_out
	 */
	public static final void gtk_window_set_geometry_hints(long window, long geometry_widget, GdkGeometry geometry, int geom_mask) { GTK3_FFM.gtk_window_set_geometry_hints(window, geometry_widget, geometry, geom_mask); }

	/* GtkWidget */
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_accessible(long widget) { return GTK3_FFM.gtk_widget_get_accessible(widget); }
	/**
	 * @method flags=ignore_deprecations
	 * @param widget cast=(GtkWidget *)
	 * @param font cast=(const PangoFontDescription *)
	 */
	/* deprecated as of 3.16 */
	public static final void gtk_widget_override_font(long widget, long font) { GTK3_FFM.gtk_widget_override_font(widget, font); }
	/**
	 * @method flags=ignore_deprecations
	 * @param widget cast=(GtkWidget *)
	 * @param new_parent cast=(GtkWidget *)
	 */
	/* deprecated as of 3.14 */
	public static final void gtk_widget_reparent(long widget, long new_parent) { GTK3_FFM.gtk_widget_reparent(widget, new_parent); }
	/**
	 * @method flags=ignore_deprecations
	 * @param widget cast=(GtkWidget *)
	 * @param double_buffered cast=(gboolean)
	 */
	/* deprecated as of 3.14 */
	public static final void gtk_widget_set_double_buffered(long widget, boolean double_buffered) { GTK3_FFM.gtk_widget_set_double_buffered(widget, double_buffered); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param width cast=(gint)
	 * @param minimum_size cast=(gint *)
	 * @param natural_size cast=(gint *)
	 */
	public static final void gtk_widget_get_preferred_height_for_width(long widget, int width, int[] minimum_size, int[] natural_size) { GTK3_FFM.gtk_widget_get_preferred_height_for_width(widget, width, minimum_size, natural_size); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param minimum_size cast=(gint *)
	 * @param natural_size cast=(gint *)
	 */
	public static final void gtk_widget_get_preferred_height(long widget, int[] minimum_size, int[] natural_size) { GTK3_FFM.gtk_widget_get_preferred_height(widget, minimum_size, natural_size); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param height cast=(gint)
	 * @param minimum_size cast=(gint *)
	 * @param natural_size cast=(gint *)
	 */
	public static final void gtk_widget_get_preferred_width_for_height(long widget, int height, int[] minimum_size, int[] natural_size) { GTK3_FFM.gtk_widget_get_preferred_width_for_height(widget, height, minimum_size, natural_size); }
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_screen(long widget) { return GTK3_FFM.gtk_widget_get_screen(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param has_window cast=(gboolean)
	 */
	public static final void gtk_widget_set_has_window(long widget, boolean has_window) { GTK3_FFM.gtk_widget_set_has_window(widget, has_window); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param accel_signal cast=(const gchar *)
	 * @param accel_group cast=(GtkAccelGroup *)
	 * @param accel_key cast=(guint)
	 * @param accel_mods cast=(GdkModifierType)
	 */
	public static final void gtk_widget_add_accelerator(long widget, byte[] accel_signal, long accel_group, int accel_key, int accel_mods, int accel_flags) { GTK3_FFM.gtk_widget_add_accelerator(widget, accel_signal, accel_group, accel_key, accel_mods, accel_flags); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param accel_group cast=(GtkAccelGroup *)
	 * @param accel_key cast=(guint)
	 * @param accel_mods cast=(GdkModifierType)
	 */
	public static final void gtk_widget_remove_accelerator(long widget, long accel_group, int accel_key, int accel_mods) { GTK3_FFM.gtk_widget_remove_accelerator(widget, accel_group, accel_key, accel_mods); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param events cast=(gint)
	 */
	public static final void gtk_widget_add_events(long widget, int events) { GTK3_FFM.gtk_widget_add_events(widget, events); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_destroy(long widget) { GTK3_FFM.gtk_widget_destroy(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final int gtk_widget_get_events(long widget) { return GTK3_FFM.gtk_widget_get_events(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_window(long widget) { return GTK3_FFM.gtk_widget_get_window(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_toplevel(long widget) { return GTK3_FFM.gtk_widget_get_toplevel(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param redraw cast=(gboolean)
	 */
	public static final void gtk_widget_set_redraw_on_allocate(long widget, boolean redraw) { GTK3_FFM.gtk_widget_set_redraw_on_allocate(widget, redraw); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param event cast=(GdkEvent *)
	 */
	public static final boolean gtk_widget_event(long widget, long event) { return GTK3_FFM.gtk_widget_event(widget, event); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param cr cast=(cairo_t *)
	 */
	public static final void gtk_widget_draw(long widget, long cr) { GTK3_FFM.gtk_widget_draw(widget, cr); }
	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_get_has_window(long widget) { return GTK3_FFM.gtk_widget_get_has_window(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_get_can_default(long widget) { return GTK3_FFM.gtk_widget_get_can_default(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param can_default cast=(gboolean)
	 */
	public static final void gtk_widget_set_can_default(long widget, boolean can_default) { GTK3_FFM.gtk_widget_set_can_default(widget, can_default); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param parent_window cast=(GdkWindow *)
	 */
	public static final void gtk_widget_set_parent_window(long widget, long parent_window) { GTK3_FFM.gtk_widget_set_parent_window(widget, parent_window); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param region cast=(cairo_region_t *)
	 */
	public static final void gtk_widget_shape_combine_region(long widget, long region) { GTK3_FFM.gtk_widget_shape_combine_region(widget, region); }
	/**
	 * @param src_widget cast=(GtkWidget *)
	 * @param dest_widget cast=(GtkWidget *)
	 * @param dest_x cast=(gint *)
	 * @param dest_y cast=(gint *)
	 */
	public static final boolean gtk_widget_translate_coordinates(long src_widget, long dest_widget, int src_x, int src_y, int[] dest_x, int[] dest_y) { return GTK3_FFM.gtk_widget_translate_coordinates(src_widget, dest_widget, src_x, src_y, dest_x, dest_y); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param property_name cast=(const gchar *)
	 * @param terminator cast=(const gchar *),flags=sentinel
	 */
	public static final void gtk_widget_style_get(long widget, byte[] property_name, int[] value, long terminator) { GTK3_FFM.gtk_widget_style_get(widget, property_name, value, terminator); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param property_name cast=(const gchar *)
	 * @param terminator cast=(const gchar *),flags=sentinel
	 */
	public static final void gtk_widget_style_get(long widget, byte[] property_name, long[] value, long terminator) { GTK3_FFM.gtk_widget_style_get(widget, property_name, value, terminator); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param region cast=(cairo_region_t *)
	 */
	public static final void gtk_widget_input_shape_combine_region(long widget, long region) { GTK3_FFM.gtk_widget_input_shape_combine_region(widget, region); }
	/** @param widget cast=(GtkWidget *)*/
	public static final void gtk_widget_set_clip(long widget, GtkAllocation allocation) { GTK3_FFM.gtk_widget_set_clip(widget, allocation); }
	/** @param widget cast=(GtkWidget *)*/
	public static final void gtk_widget_get_clip(long widget, GtkAllocation allocation) { GTK3_FFM.gtk_widget_get_clip(widget, allocation); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param allocation cast=(GtkAllocation *),flags=no_out
	 */
	public static final void gtk_widget_set_allocation(long widget, GtkAllocation allocation) { GTK3_FFM.gtk_widget_set_allocation(widget, allocation); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param allocation cast=(GtkAllocation *),flags=no_out
	 */
	public static final void gtk_widget_size_allocate(long widget, GtkAllocation allocation) { GTK3_FFM.gtk_widget_size_allocate(widget, allocation); }

	/* Drag and Drop API */
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param targets cast=(GtkTargetList *)
	 * @param actions cast=(GdkDragAction)
	 * @param button cast=(gint)
	 * @param event cast=(GdkEvent *)
	 * @param x cast=(gint)
	 * @param y cast=(gint)
	 */
	public static final long gtk_drag_begin_with_coordinates(long widget, long targets, int actions, int button, long event, int x, int y) { return GTK3_FFM.gtk_drag_begin_with_coordinates(widget, targets, actions, button, event, x, y); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param start_x cast=(gint)
	 * @param start_y cast=(gint)
	 * @param current_x cast=(gint)
	 * @param current_y cast=(gint)
	 */
	public static final boolean gtk_drag_check_threshold(long widget, int start_x, int start_y, int current_x, int current_y) { return GTK3_FFM.gtk_drag_check_threshold(widget, start_x, start_y, current_x, current_y); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param flags cast=(GtkDestDefaults)
	 * @param targets cast=(const GtkTargetEntry *)
	 * @param n_targets cast=(gint)
	 * @param actions cast=(GdkDragAction)
	 */
	public static final void gtk_drag_dest_set(long widget, int flags, long targets, int n_targets, int actions) { GTK3_FFM.gtk_drag_dest_set(widget, flags, targets, n_targets, actions); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_drag_dest_unset(long widget) { GTK3_FFM.gtk_drag_dest_unset(widget); }
	/**
	 * @param context cast=(GdkDragContext *)
	 * @param success cast=(gboolean)
	 * @param delete cast=(gboolean)
	 * @param time cast=(guint32)
	 */
	public static final void gtk_drag_finish(long context, boolean success, boolean delete, int time) { GTK3_FFM.gtk_drag_finish(context, success, delete, time); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param context cast=(GdkDragContext *)
	 * @param target cast=(GdkAtom)
	 * @param time cast=(guint32)
	 */
	public static final void gtk_drag_get_data(long widget, long context, long target, int time) { GTK3_FFM.gtk_drag_get_data(widget, context, target, time); }
	/**
	 * @param context cast=(GdkDragContext *)
	 * @param surface cast=(cairo_surface_t *)
	 */
	public static final void gtk_drag_set_icon_surface(long context, long surface) { GTK3_FFM.gtk_drag_set_icon_surface(context, surface); }

	/* GtkFileChooser */
	/** @param chooser cast=(GtkFileChooser *) */
	public static final long gtk_file_chooser_get_filename(long chooser) { return GTK3_FFM.gtk_file_chooser_get_filename(chooser); }
	/** @param chooser cast=(GtkFileChooser *) */
	public static final long gtk_file_chooser_get_filenames(long chooser) { return GTK3_FFM.gtk_file_chooser_get_filenames(chooser); }
	/** @param chooser cast=(GtkFileChooser *) */
	public static final long gtk_file_chooser_get_uri(long chooser) { return GTK3_FFM.gtk_file_chooser_get_uri(chooser); }
	/** @param chooser cast=(GtkFileChooser *) */
	public static final long gtk_file_chooser_get_uris(long chooser) { return GTK3_FFM.gtk_file_chooser_get_uris(chooser); }
	/**
	 * @param chooser cast=(GtkFileChooser *)
	 * @param filename cast=(const gchar *)
	 */
	public static final void gtk_file_chooser_set_current_folder(long chooser, long filename) { GTK3_FFM.gtk_file_chooser_set_current_folder(chooser, filename); }
	/**
	 * @param chooser cast=(GtkFileChooser *)
	 * @param uri cast=(const gchar *)
	 */
	public static final void gtk_file_chooser_set_current_folder_uri(long chooser, byte [] uri) { GTK3_FFM.gtk_file_chooser_set_current_folder_uri(chooser, uri); }
	/**
	 * @param chooser cast=(GtkFileChooser *)
	 * @param local_only cast=(gboolean)
	 */
	public static final void gtk_file_chooser_set_local_only(long chooser, boolean local_only) { GTK3_FFM.gtk_file_chooser_set_local_only(chooser, local_only); }
	/**
	 * @param chooser cast=(GtkFileChooser *)
	 * @param do_overwrite_confirmation cast=(gboolean)
	 */
	public static final void gtk_file_chooser_set_do_overwrite_confirmation(long chooser, boolean do_overwrite_confirmation) { GTK3_FFM.gtk_file_chooser_set_do_overwrite_confirmation(chooser, do_overwrite_confirmation); }
	/**
	 * @param chooser cast=(GtkFileChooser *)
	 * @param name cast=(const gchar *)
	 */
	public static final void gtk_file_chooser_set_filename(long chooser, long name) { GTK3_FFM.gtk_file_chooser_set_filename(chooser, name); }
	/**
	 * @param chooser cast=(GtkFileChooser *)
	 * @param uri cast=(const char *)
	 */
	public static final void gtk_file_chooser_set_uri(long chooser, byte [] uri) { GTK3_FFM.gtk_file_chooser_set_uri(chooser, uri); }
	/**
	 * @param chooser cast=(GtkFileChooser *)
	 * @param extra_widget cast=(GtkWidget *)
	 */
	public static final void gtk_file_chooser_set_extra_widget(long chooser, long extra_widget) { GTK3_FFM.gtk_file_chooser_set_extra_widget(chooser, extra_widget); }

	/* GtkRadioButton */
	/** @param radio_button cast=(GtkRadioButton *) */
	public static final long gtk_radio_button_get_group(long radio_button) { return GTK3_FFM.gtk_radio_button_get_group(radio_button); }
	/** @param group cast=(GSList *) */
	public static final long gtk_radio_button_new(long group) { return GTK3_FFM.gtk_radio_button_new(group); }

	/* GtkNativeDialog */
	/** @param dialog cast=(GtkNativeDialog *) */
	public static final int gtk_native_dialog_run(long dialog) { return GTK3_FFM.gtk_native_dialog_run(dialog); }

	/* GtkScrolledWindow */
	/**
	 * @param hadjustment cast=(GtkAdjustment *)
	 * @param vadjustment cast=(GtkAdjustment *)
	 */
	public static final long gtk_scrolled_window_new(long hadjustment, long vadjustment) { return GTK3_FFM.gtk_scrolled_window_new(hadjustment, vadjustment); }
	/**
	 * @param scrolled_window cast=(GtkScrolledWindow *)
	 * @param type cast=(GtkShadowType)
	 */
	public static final void gtk_scrolled_window_set_shadow_type(long scrolled_window, int type) { GTK3_FFM.gtk_scrolled_window_set_shadow_type(scrolled_window, type); }
	/** @param scrolled_window cast=(GtkScrolledWindow *) */
	public static final int gtk_scrolled_window_get_shadow_type(long scrolled_window) { return GTK3_FFM.gtk_scrolled_window_get_shadow_type(scrolled_window); }

	/* GtkClipboard */
	/** @param clipboard cast=(GtkClipboard *) */
	public static final void gtk_clipboard_clear(long clipboard) { GTK3_FFM.gtk_clipboard_clear(clipboard); }
	/** @param selection cast=(GdkAtom) */
	public static final long gtk_clipboard_get(long selection) { return GTK3_FFM.gtk_clipboard_get(selection); }
	/**
	 * @param clipboard cast=(GtkClipboard *)
	 * @param target cast=(const GtkTargetEntry *)
	 * @param n_targets cast=(guint)
	 * @param get_func cast=(GtkClipboardGetFunc)
	 * @param clear_func cast=(GtkClipboardClearFunc)
	 * @param user_data cast=(GObject *)
	 */
	public static final boolean gtk_clipboard_set_with_owner(long clipboard, long target, int n_targets, long get_func, long clear_func, long user_data) { return GTK3_FFM.gtk_clipboard_set_with_owner(clipboard, target, n_targets, get_func, clear_func, user_data); }
	/**
	 * @param clipboard cast=(GtkClipboard *)
	 * @param targets cast=(const GtkTargetEntry *)
	 * @param n_targets cast=(gint)
	 */
	public static final void gtk_clipboard_set_can_store(long clipboard, long targets, int n_targets) { GTK3_FFM.gtk_clipboard_set_can_store(clipboard, targets, n_targets); }
	/** @param clipboard cast=(GtkClipboard *) */
	public static final void gtk_clipboard_store(long clipboard) { GTK3_FFM.gtk_clipboard_store(clipboard); }
	/**
	 * @param clipboard cast=(GtkClipboard *)
	 * @param target cast=(GdkAtom)
	 */
	public static final long gtk_clipboard_wait_for_contents(long clipboard, long target) { return GTK3_FFM.gtk_clipboard_wait_for_contents(clipboard, target); }

	/* GtkStatusIcon */
	/**
	 * @method flags=ignore_deprecations
	 * @param handle cast=(GtkStatusIcon*)
	 */
	public static final boolean gtk_status_icon_get_visible(long handle) { return GTK3_FFM.gtk_status_icon_get_visible(handle); }
	/** @method flags=ignore_deprecations */
	public static final long gtk_status_icon_new() { return GTK3_FFM.gtk_status_icon_new(); }
	/**
	 * @method flags=ignore_deprecations
	 * @param handle cast=(GtkStatusIcon*)
	 * @param pixbuf cast=(GdkPixbuf*)
	 */
	public static final void gtk_status_icon_set_from_pixbuf(long handle, long pixbuf) { GTK3_FFM.gtk_status_icon_set_from_pixbuf(handle, pixbuf); }
	/**
	 * @method flags=ignore_deprecations
	 * @param handle cast=(GtkStatusIcon*)
	 * @param visible cast=(gboolean)
	 */
	public static final void gtk_status_icon_set_visible(long handle, boolean visible) { GTK3_FFM.gtk_status_icon_set_visible(handle, visible); }
	/**
	 * @method flags=ignore_deprecations
	 * @param handle cast=(GtkStatusIcon *)
	 * @param tip_text cast=(const gchar *)
	 */
	public static final void gtk_status_icon_set_tooltip_text(long handle, byte[] tip_text) { GTK3_FFM.gtk_status_icon_set_tooltip_text(handle, tip_text); }
	/**
	 * @method flags=ignore_deprecations
	 * @param handle cast=(GtkStatusIcon*)
	 * @param screen cast=(GdkScreen**)
	 * @param area cast=(GdkRectangle*)
	 * @param orientation cast=(GtkOrientation*)
	 */
	public static final boolean gtk_status_icon_get_geometry(long handle, long screen, GdkRectangle area, long orientation) { return GTK3_FFM.gtk_status_icon_get_geometry(handle, screen, area, orientation); }

	/* GtkTargetList */
	/**
	 * @param targets cast=(const GtkTargetEntry *)
	 * @param ntargets cast=(guint)
	 */
	public static final long gtk_target_list_new(long targets, int ntargets) { return GTK3_FFM.gtk_target_list_new(targets, ntargets); }
	/** @param list cast=(GtkTargetList *) */
	public static final void gtk_target_list_unref(long list) { GTK3_FFM.gtk_target_list_unref(list); }

	/* GtkSelectionData */
	/** @param selection_data cast=(GtkSelectionData *) */
	public static final void gtk_selection_data_free(long selection_data) { GTK3_FFM.gtk_selection_data_free(selection_data); }
	/** @param selection_data cast=(GtkSelectionData *) */
	public static final long gtk_selection_data_get_data(long selection_data) { return GTK3_FFM.gtk_selection_data_get_data(selection_data); }
	/** @param selection_data cast=(GtkSelectionData *) */
	public static final int gtk_selection_data_get_format(long selection_data) { return GTK3_FFM.gtk_selection_data_get_format(selection_data); }
	/** @param selection_data cast=(GtkSelectionData *) */
	public static final int gtk_selection_data_get_length(long selection_data) { return GTK3_FFM.gtk_selection_data_get_length(selection_data); }
	/** @param selection_data cast=(GtkSelectionData *) */
	public static final long gtk_selection_data_get_target(long selection_data) { return GTK3_FFM.gtk_selection_data_get_target(selection_data); }
	/** @param selection_data cast=(GtkSelectionData *) */
	public static final long gtk_selection_data_get_data_type(long selection_data) { return GTK3_FFM.gtk_selection_data_get_data_type(selection_data); }
	/**
	 * @param selection_data cast=(GtkSelectionData *)
	 * @param type cast=(GdkAtom)
	 * @param format cast=(gint)
	 * @param data cast=(const guchar *)
	 * @param length cast=(gint)
	 */
	public static final void gtk_selection_data_set(long selection_data, long type, int format, long data, int length) { GTK3_FFM.gtk_selection_data_set(selection_data, type, format, data, length); }

	/* GtkMenu */
	public static final long gtk_menu_new() { return GTK3_FFM.gtk_menu_new(); }
	/** @param menu cast=(GtkMenu *) */
	public static final void gtk_menu_popdown(long menu) { GTK3_FFM.gtk_menu_popdown(menu); }
	/**
	 * @param menu cast=(GtkMenu *)
	 * @param trigger_event cast=(const GdkEvent*)
	 */
	public static final void gtk_menu_popup_at_pointer(long menu, long trigger_event) { GTK3_FFM.gtk_menu_popup_at_pointer(menu, trigger_event); }

	/* GtkMenuBar */
	public static final long gtk_menu_bar_new() { return GTK3_FFM.gtk_menu_bar_new(); }

	/* GtkMenuItem */
	/** @param menu_item cast=(GtkMenuItem *) */
	public static final long gtk_menu_item_get_submenu(long menu_item) { return GTK3_FFM.gtk_menu_item_get_submenu(menu_item); }
	public static final long gtk_menu_item_new() { return GTK3_FFM.gtk_menu_item_new(); }
	/**
	 * @param menu_item cast=(GtkMenuItem *)
	 * @param submenu cast=(GtkWidget *)
	 */
	public static final void gtk_menu_item_set_submenu(long menu_item, long submenu) { GTK3_FFM.gtk_menu_item_set_submenu(menu_item, submenu); }
	/** @param check_menu_item cast=(GtkCheckMenuItem *) */
	public static final boolean gtk_check_menu_item_get_active(long check_menu_item) { return GTK3_FFM.gtk_check_menu_item_get_active(check_menu_item); }
	public static final long gtk_check_menu_item_new() { return GTK3_FFM.gtk_check_menu_item_new(); }
	/**
	 * @param wid cast=(GtkCheckMenuItem *)
	 * @param active cast=(gboolean)
	 */
	public static final void gtk_check_menu_item_set_active(long wid, boolean active) { GTK3_FFM.gtk_check_menu_item_set_active(wid, active); }
	/** @param radio_menu_item cast=(GtkRadioMenuItem *) */
	public static final long gtk_radio_menu_item_get_group(long radio_menu_item) { return GTK3_FFM.gtk_radio_menu_item_get_group(radio_menu_item); }
	/** @param group cast=(GSList *) */
	public static final long gtk_radio_menu_item_new(long group) { return GTK3_FFM.gtk_radio_menu_item_new(group); }
	public static final long gtk_separator_menu_item_new() { return GTK3_FFM.gtk_separator_menu_item_new(); }
	/**
	 * @param menu cast=(GtkMenu *)
	 * @param rect_window cast=(GdkWindow *)
	 * @param rect cast=(GdkRectangle *)
	 * @param rect_anchor cast=(GdkGravity)
	 * @param menu_anchor cast=(GdkGravity)
	 * @param trigger_event cast=(const GdkEvent *)
	 */
	public static final void gtk_menu_popup_at_rect(long menu, long rect_window, GdkRectangle rect, int rect_anchor, int menu_anchor, long trigger_event) { GTK3_FFM.gtk_menu_popup_at_rect(menu, rect_window, rect, rect_anchor, menu_anchor, trigger_event); }

	/* GtkMenuShell */
	/** @param menu_shell cast=(GtkMenuShell *) */
	public static final void gtk_menu_shell_deactivate(long menu_shell) { GTK3_FFM.gtk_menu_shell_deactivate(menu_shell); }
	/**
	 * @param menu_shell cast=(GtkMenuShell *)
	 * @param child cast=(GtkWidget *)
	 * @param position cast=(gint)
	 */
	public static final void gtk_menu_shell_insert(long menu_shell, long child, int position) { GTK3_FFM.gtk_menu_shell_insert(menu_shell, child, position); }
	/**
	 * @param menu_shell cast=(GtkMenuShell *)
	 * @param take_focus cast=(gboolean)
	 */
	public static final void gtk_menu_shell_set_take_focus(long menu_shell, boolean take_focus) { GTK3_FFM.gtk_menu_shell_set_take_focus(menu_shell, take_focus); }

	/* GtkToolbar */
	public static final long gtk_toolbar_new() { return GTK3_FFM.gtk_toolbar_new(); }
	/**
	 * @param toolbar cast=(GtkToolbar *)
	 * @param item cast=(GtkToolItem *)
	 */
	public static final void gtk_toolbar_insert(long toolbar, long item, int pos) { GTK3_FFM.gtk_toolbar_insert(toolbar, item, pos); }
	/**
	 * @param toolbar cast=(GtkToolbar *)
	 * @param style cast=(GtkToolbarStyle)
	 */
	public static final void gtk_toolbar_set_style(long toolbar, int style) { GTK3_FFM.gtk_toolbar_set_style(toolbar, style); }
	/** @param toolbar cast=(GtkToolbar *)*/
	public static final void gtk_toolbar_set_icon_size(long toolbar, int size) { GTK3_FFM.gtk_toolbar_set_icon_size(toolbar, size); }

	/* GtkToolItem */
	/**
	 * @param item cast=(GtkToolItem *)
	 * @param menu_id cast=(const gchar *)
	 */
	public static final long gtk_tool_item_get_proxy_menu_item(long item, byte[] menu_id) { return GTK3_FFM.gtk_tool_item_get_proxy_menu_item(item, menu_id); }
	/** @param item cast=(GtkToolItem *) */
	public static final long gtk_tool_item_retrieve_proxy_menu_item(long item) { return GTK3_FFM.gtk_tool_item_retrieve_proxy_menu_item(item); }
	/**
	 * @param item cast=(GtkToolItem *)
	 * @param important cast=(gboolean)
	 */
	public static final void gtk_tool_item_set_is_important(long item, boolean important) { GTK3_FFM.gtk_tool_item_set_is_important(item, important); }
	/**
	 * @param item cast=(GtkToolItem *)
	 * @param homogeneous cast=(gboolean)
	 */
	public static final void gtk_tool_item_set_homogeneous(long item, boolean homogeneous) { GTK3_FFM.gtk_tool_item_set_homogeneous(item, homogeneous); }
	/**
	 * @param item cast=(GtkToolItem *)
	 * @param menu_id cast=(const gchar *)
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_tool_item_set_proxy_menu_item(long item, byte[] menu_id, long widget) { GTK3_FFM.gtk_tool_item_set_proxy_menu_item(item, menu_id, widget); }

	/* GtkSeparatorToolItem */
	public static final long gtk_separator_tool_item_new() { return GTK3_FFM.gtk_separator_tool_item_new(); }
	/**
	 * @param item cast=(GtkSeparatorToolItem *)
	 * @param draw cast=(gboolean)
	 */
	public static final void gtk_separator_tool_item_set_draw(long item, boolean draw) { GTK3_FFM.gtk_separator_tool_item_set_draw(item, draw); }

	/* GtkToolButton */
	/**
	 * @param icon_widget cast=(GtkWidget *)
	 * @param label cast=(const gchar *)
	 */
	public static final long gtk_tool_button_new(long icon_widget, byte[] label) { return GTK3_FFM.gtk_tool_button_new(icon_widget, label); }
	/**
	 * @param button cast=(GtkToolButton *)
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_tool_button_set_icon_widget(long button, long widget) { GTK3_FFM.gtk_tool_button_set_icon_widget(button, widget); }
	/**
	 * @param button cast=(GtkToolButton *)
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_tool_button_set_label_widget(long button,  long widget) { GTK3_FFM.gtk_tool_button_set_label_widget(button, widget); }
	/**
	 * @param item cast=(GtkToolButton *)
	 * @param underline cast=(gboolean)
	 */
	public static final void gtk_tool_button_set_use_underline(long item, boolean underline) { GTK3_FFM.gtk_tool_button_set_use_underline(item, underline); }

	/* GtkToggleToolButton */
	/** @param button cast=(GtkToggleToolButton *) */
	public static final boolean gtk_toggle_tool_button_get_active(long button) { return GTK3_FFM.gtk_toggle_tool_button_get_active(button); }
	public static final long gtk_toggle_tool_button_new() { return GTK3_FFM.gtk_toggle_tool_button_new(); }
	/**
	 * @param item cast=(GtkToggleToolButton *)
	 * @param selected cast=(gboolean)
	 */
	public static final void gtk_toggle_tool_button_set_active(long item, boolean selected) { GTK3_FFM.gtk_toggle_tool_button_set_active(item, selected); }

	/* GtkMenuToolButton */
	/**
	 * @param icon_widget cast=(GtkWidget *)
	 * @param label cast=(const gchar *)
	 */
	public static final long gtk_menu_tool_button_new(long icon_widget, byte[] label) { return GTK3_FFM.gtk_menu_tool_button_new(icon_widget, label); }

	/* GtkIconTheme */
	/**
	 * @param icon_theme cast=(GtkIconTheme *)
	 * @param icon cast=(GIcon *)
	 * @param size cast=(gint)
	 * @param flags cast=(GtkIconLookupFlags)
	 */
	public static final long gtk_icon_theme_lookup_by_gicon(long icon_theme, long icon, int size, int flags) { return GTK3_FFM.gtk_icon_theme_lookup_by_gicon(icon_theme, icon, size, flags); }
	/**
	 * @param icon_theme cast=(GtkIconTheme *)
	 * @param icon_name cast=(const gchar *)
	 * @param size cast=(gint)
	 * @param flags cast=(GtkIconLookupFlags)
	 * @param error cast=(GError **)
	 */
	public static final long gtk_icon_theme_load_icon(long icon_theme, byte[] icon_name, int size, int flags, long error) { return GTK3_FFM.gtk_icon_theme_load_icon(icon_theme, icon_name, size, flags, error); }
	public static final long gtk_icon_theme_get_default() { return GTK3_FFM.gtk_icon_theme_get_default(); }
	/**
	 * @param icon_info cast=(GtkIconInfo *)
	 * @param error cast=(GError **)
	 */
	public static final long gtk_icon_info_load_icon(long icon_info, long error[]) { return GTK3_FFM.gtk_icon_info_load_icon(icon_info, error); }

	/* GtkEditable Interface */
	/** @param editable cast=(GtkEditable *) */
	public static final void gtk_editable_copy_clipboard(long editable) { GTK3_FFM.gtk_editable_copy_clipboard(editable); }
	/** @param editable cast=(GtkEditable *) */
	public static final void gtk_editable_cut_clipboard(long editable) { GTK3_FFM.gtk_editable_cut_clipboard(editable); }
	/** @param editable cast=(GtkEditable *) */
	public static final void gtk_editable_paste_clipboard(long editable) { GTK3_FFM.gtk_editable_paste_clipboard(editable); }

	/* GtkEntry */
	/**
	 * @param self cast=(GtkEntry *)
	 * @param n_chars cast=(gint)
	 */
	public static final void gtk_entry_set_width_chars(long self, int n_chars) { GTK3_FFM.gtk_entry_set_width_chars(self, n_chars); }
	/** @param entry cast=(GtkEntry *) */
	public static final long gtk_entry_get_layout(long entry) { return GTK3_FFM.gtk_entry_get_layout(entry); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param x cast=(gint *)
	 * @param y cast=(gint *)
	 */
	public static final void gtk_entry_get_layout_offsets(long entry, int[] x, int[] y) { GTK3_FFM.gtk_entry_get_layout_offsets(entry, x, y); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param index cast=(gint)
	 */
	public static final int gtk_entry_text_index_to_layout_index(long entry, int index) { return GTK3_FFM.gtk_entry_text_index_to_layout_index(entry, index); }
	/** @param entry cast=(GtkEntry *) */
	public static final long gtk_entry_get_text(long entry) { return GTK3_FFM.gtk_entry_get_text(entry); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param text cast=(const gchar *)
	 */
	public static final void gtk_entry_set_text(long entry, byte[] text) { GTK3_FFM.gtk_entry_set_text(entry, text); }

	/* GtkEventController */
	/**
	 * @param gesture cast=(GtkEventController *)
	 * @param event cast=(const GdkEvent *)
	 */
	public static final void gtk_event_controller_handle_event(long gesture, long event) { GTK3_FFM.gtk_event_controller_handle_event(gesture, event); }

	/* GtkFrame */
	/**
	 * @param frame cast=(GtkFrame *)
	 * @param type cast=(GtkShadowType)
	 */
	public static final void gtk_frame_set_shadow_type(long frame, int type) { GTK3_FFM.gtk_frame_set_shadow_type(frame, type); }

	/* GtkViewport */
	/**
	 * @param viewport cast=(GtkViewport *)
	 * @param type cast=(GtkShadowType)
	 */
	public static final void gtk_viewport_set_shadow_type(long viewport, int type) { GTK3_FFM.gtk_viewport_set_shadow_type(viewport, type); }

	/* GtkAccessible */
	/** @param accessible cast=(GtkAccessible *) */
	public static final long gtk_accessible_get_widget(long accessible) { return GTK3_FFM.gtk_accessible_get_widget(accessible); }

	/* GtkComboBox */
	/**
	 * @param combo_box cast=(GtkComboBox *)
	 * @param width cast=(gint)
	 */
	/* Do not use directly. Instead use Combo.gtk_combo_box_toggle_wrap(..) */
	public static final void gtk_combo_box_set_wrap_width(long combo_box, int width) { GTK3_FFM.gtk_combo_box_set_wrap_width(combo_box, width); }
	/**
	 * @param combo_box cast=(GtkComboBox *)
	 * @return cast=(gint)
	 */
	public static final int gtk_combo_box_get_wrap_width(long combo_box) { return GTK3_FFM.gtk_combo_box_get_wrap_width(combo_box); }

	/* GtkEventBox */
	public static final long gtk_event_box_new() { return GTK3_FFM.gtk_event_box_new(); }

	/* GtkImage */
	/**
	 * @param image cast=(GtkImage *)
	 * @param surface cast=(cairo_surface_t *)
	 */
	public static final void gtk_image_set_from_surface(long image, long surface) { GTK3_FFM.gtk_image_set_from_surface(image, surface); }
	/**
	 * @param icon_name cast=(const gchar *)
	 * @param size cast=(GtkIconSize)
	 */
	public static final long gtk_image_new_from_icon_name(byte[] icon_name, int size) { return GTK3_FFM.gtk_image_new_from_icon_name(icon_name, size); }
	/**
	 * @param image cast=(GtkImage *)
	 * @param icon_name cast=(const gchar *)
	 * @param size cast=(GtkIconSize)
	 */
	public static final void gtk_image_set_from_icon_name(long image, byte[] icon_name, int size) { GTK3_FFM.gtk_image_set_from_icon_name(image, icon_name, size); }
	/** @param surface cast=(cairo_surface_t *) */
	public static final long gtk_image_new_from_surface(long surface) { return GTK3_FFM.gtk_image_new_from_surface(surface); }

	/* GtkCssProvider */
	/**
	 * @param css_provider cast=(GtkCssProvider *)
	 * @param data cast=(const gchar *)
	 * @param length cast=(gssize)
	 * @param error cast=(GError **)
	 */
	public static final boolean gtk_css_provider_load_from_data(long css_provider, byte[] data, long length, long error[]) { return GTK3_FFM.gtk_css_provider_load_from_data(css_provider, data, length, error); }

	/* GtkStyleContext */
	/**
	 * @param screen cast=(GdkScreen *)
	 * @param provider cast=(GtkStyleProvider *)
	 * @param priority cast=(guint)
	 */
	public static final void gtk_style_context_add_provider_for_screen(long screen, long provider, int priority) { GTK3_FFM.gtk_style_context_add_provider_for_screen(screen, provider, priority); }
	/**
	 * @method flags=ignore_deprecations
	 * @param context cast=(GtkStyleContext *)
	 * @param state cast=(GtkStateFlags)
	 */
	/* [GTK3; 3.8 deprecated, replaced] */
	public static final long gtk_style_context_get_font(long context, int state) { return GTK3_FFM.gtk_style_context_get_font(context, state); }
	/** @param context cast=(GtkStyleContext *) */
	public static final long gtk_style_context_get_parent(long context) { return GTK3_FFM.gtk_style_context_get_parent(context); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param state cast=(GtkStateFlags)
	 * @param property cast=(const gchar *),flags=no_out
	 * @param terminator cast=(const gchar *),flags=sentinel
	 */
	public static final void gtk_style_context_get(long context, int state, byte [] property, long [] value, long terminator) { GTK3_FFM.gtk_style_context_get(context, state, property, value, terminator); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param state cast=(GtkStateFlags)
	 * @param padding cast=(GtkBorder *),flags=no_in
	 */
	public static final void gtk_style_context_get_padding(long context, int state, GtkBorder padding) { GTK3_FFM.gtk_style_context_get_padding(context, state, padding); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param state cast=(GtkStateFlags)
	 * @param color cast=(GdkRGBA *)
	 */
	public static final void gtk_style_context_get_color(long context, int state, GdkRGBA color) { GTK3_FFM.gtk_style_context_get_color(context, state, color); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param state cast=(GtkStateFlags)
	 * @param padding cast=(GtkBorder *),flags=no_in
	 */
	public static final void gtk_style_context_get_border(long context, int state, GtkBorder padding) { GTK3_FFM.gtk_style_context_get_border(context, state, padding); }

	/* GtkLabel */
	/**
	 * @param label cast=(GtkLabel *)
	 * @param wrap cast=(gboolean)
	 */
	public static final void gtk_label_set_line_wrap(long label, boolean wrap) { GTK3_FFM.gtk_label_set_line_wrap(label, wrap); }
	/**
	 * @param label cast=(GtkLabel *)
	 * @param wrap_mode cast=(PangoWrapMode)
	 */
	public static final void gtk_label_set_line_wrap_mode(long label, int wrap_mode) { GTK3_FFM.gtk_label_set_line_wrap_mode(label, wrap_mode); }

	/* GtkTextView */
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param win cast=(GtkTextWindowType)
	 */
	public static final long gtk_text_view_get_window(long text_view, int win) { return GTK3_FFM.gtk_text_view_get_window(text_view, win); }

	/* GtkToggleButton */
	/**
	 * @param toggle_button cast=(GtkToggleButton *)
	 * @param setting cast=(gboolean)
	 */
	public static final void gtk_toggle_button_set_inconsistent(long toggle_button, boolean setting) { GTK3_FFM.gtk_toggle_button_set_inconsistent(toggle_button, setting); }

	/* GtkTreeView */
	/** @param tree_view cast=(GtkTreeView *) */
	public static final long gtk_tree_view_get_bin_window(long tree_view) { return GTK3_FFM.gtk_tree_view_get_bin_window(tree_view); }

	/* GtkTreeViewColumn */
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param cell_area cast=(GdkRectangle *),flags=no_in
	 * @param x_offset cast=(gint *)
	 * @param y_offset cast=(gint *)
	 * @param width cast=(gint *)
	 * @param height cast=(gint *)
	 */
	public static final void gtk_tree_view_column_cell_get_size(long tree_column, GdkRectangle cell_area, int[] x_offset, int[] y_offset, int[] width, int[] height) { GTK3_FFM.gtk_tree_view_column_cell_get_size(tree_column, cell_area, x_offset, y_offset, width, height); }

	/* GdkWindow */
	/**
	 * @param parent cast=(GdkWindow *)
	 * @param attributes flags=no_out
	 */
	public static final long gdk_window_new(long parent, GdkWindowAttr attributes, int attributes_mask) { return GTK3_FFM.gdk_window_new(parent, attributes, attributes_mask); }

	/* Memmove */
	/**
	 * @param dest cast=(void *),flags=no_in
	 * @param src cast=(const void *)
	 * @param size cast=(size_t)
	 */
	public static final void memmove(GdkEventButton dest, long src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *),flags=no_in
	 * @param src cast=(const void *)
	 * @param size cast=(size_t)
	 */
	public static final void memmove(GdkEventCrossing dest, long src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *),flags=no_in
	 * @param src cast=(const void *)
	 * @param size cast=(size_t)
	 */
	public static final void memmove(GdkEventFocus dest, long src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *),flags=no_in
	 * @param src cast=(const void *)
	 * @param size cast=(size_t)
	 */
	public static final void memmove(GdkEventKey dest, long src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *),flags=no_in
	 * @param src cast=(const void *)
	 * @param size cast=(size_t)
	 */
	public static final void memmove(GdkEventMotion dest, long src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *),flags=no_in
	 * @param src cast=(const void *)
	 * @param size cast=(size_t)
	 */
	public static final void memmove(GdkEventWindowState dest, long src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *)
	 * @param src cast=(const void *),flags=no_out
	 * @param size cast=(size_t)
	 */
	public static final void memmove(long dest, GdkEventButton src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *)
	 * @param src cast=(const void *),flags=no_out
	 * @param size cast=(size_t)
	 */
	public static final void memmove(long dest, GdkEventKey src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param dest cast=(void *)
	 * @param src cast=(const void *),flags=no_out
	 * @param size cast=(size_t)
	 */
	public static final void memmove(long dest, GtkTargetEntry src, long size) { GTK3_FFM.memmove(dest, src, size); }
	/**
	 * @param widget cast=(GtkWidget *)
	 */
	public static final long gtk_gesture_rotate_new(long widget) { return GTK3_FFM.gtk_gesture_rotate_new(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 */
	public static final long gtk_gesture_zoom_new(long widget) { return GTK3_FFM.gtk_gesture_zoom_new(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 */
	public static final long gtk_gesture_drag_new(long widget) { return GTK3_FFM.gtk_gesture_drag_new(widget); }

	/* Sizeof */
	public static final int GtkTargetEntry_sizeof() { return GTK3_FFM.GtkTargetEntry_sizeof(); }
	public static final int GdkEvent_sizeof() { return GTK3_FFM.GdkEvent_sizeof(); }
	public static final int GdkEventButton_sizeof() { return GTK3_FFM.GdkEventButton_sizeof(); }
	public static final int GdkEventCrossing_sizeof() { return GTK3_FFM.GdkEventCrossing_sizeof(); }
	public static final int GdkEventFocus_sizeof() { return GTK3_FFM.GdkEventFocus_sizeof(); }
	public static final int GdkEventKey_sizeof() { return GTK3_FFM.GdkEventKey_sizeof(); }
	public static final int GdkEventMotion_sizeof() { return GTK3_FFM.GdkEventMotion_sizeof(); }
	public static final int GdkEventWindowState_sizeof() { return GTK3_FFM.GdkEventWindowState_sizeof(); }
	public static final int GdkGeometry_sizeof() { return GTK3_FFM.GdkGeometry_sizeof(); }
	public static final int GdkWindowAttr_sizeof() { return GTK3_FFM.GdkWindowAttr_sizeof(); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_show(long widget) { GTK3_FFM.gtk_widget_show(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_hide(long widget) { GTK3_FFM.gtk_widget_hide(widget); }

}
