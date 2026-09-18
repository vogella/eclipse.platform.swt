/*******************************************************************************
 * Copyright (c) 2018, 20224 Red Hat Inc. and others. All rights reserved.
 * The contents of this file are made available under the terms
 * of the GNU Lesser General Public License (LGPL) Version 2.1 that
 * accompanies this distribution (lgpl-v21.txt).  The LGPL is also
 * available at http://www.gnu.org/licenses/lgpl.html.  If the version
 * of the LGPL at http://www.gnu.org is different to the version of
 * the LGPL accompanying this distribution and there is any conflict
 * between the two license versions, the terms of the LGPL accompanying
 * this distribution shall govern.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.gtk;

/**
 * This class contains GTK specific native functions.
 *
 * In contrast to OS.java, dynamic functions are automatically linked, no need to add os_custom.h entries.
 */
public class GTK extends OS {

	public static final int GTK_VERSION = OS.VERSION(GTK.gtk_get_major_version(), GTK.gtk_get_minor_version(), GTK.gtk_get_micro_version());
	public static final boolean GTK4 = GTK_VERSION >= OS.VERSION(4, 0, 0);

	/** Constants */
	public static final int GTK_ACCEL_VISIBLE = 0x1;
	public static final int GTK_ALIGN_FILL = 0x0; //Gtk3 GtkAlign Enum
	public static final int GTK_ALIGN_START = 0x1;
	public static final int GTK_ALIGN_END = 0x2;
	public static final int GTK_ALIGN_CENTER = 0x3;
	public static final int GTK_ALIGN_BASELINE = 0x4;
	public static final int GTK_CALENDAR_SHOW_HEADING = 1 << 0;
	public static final int GTK_CALENDAR_SHOW_DAY_NAMES = 1 << 1;
	public static final int GTK_CALENDAR_NO_MONTH_CHANGE = 1 << 2;
	public static final int GTK_CALENDAR_SHOW_WEEK_NUMBERS = 1 << 3;
	public static final int GTK_CALENDAR_WEEK_START_MONDAY = 1 << 4;
	public static final int GTK_CELL_RENDERER_MODE_ACTIVATABLE = 1;
	public static final int GTK_CELL_RENDERER_SELECTED = 1 << 0;
	public static final int GTK_CELL_RENDERER_FOCUSED = 1 << 4;
	public static final int GTK_DIALOG_DESTROY_WITH_PARENT = 1 << 1;
	public static final int GTK_DIALOG_MODAL = 1 << 0;
	public static final int GTK_DIR_TAB_FORWARD = 0;
	public static final int GTK_DIR_TAB_BACKWARD = 1;
	public static final int GTK_DIR_UP = 2;
	public static final int GTK_DIR_LEFT = 4;
	public static final int GTK_DIR_RIGHT = 5;
	public static final int GTK_ENTRY_ICON_PRIMARY = 0;
	public static final int GTK_ENTRY_ICON_SECONDARY = 1;
	public static final int GTK_FILE_CHOOSER_ACTION_OPEN = 0;
	public static final int GTK_FILE_CHOOSER_ACTION_SAVE = 1;
	public static final int GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER = 2;
	public static final int GTK_ICON_SIZE_MENU = 1;
	public static final int GTK_ICON_SIZE_SMALL_TOOLBAR = 2;
	public static final int GTK_ICON_SIZE_DIALOG = 6;
	public static final int GTK_ICON_LOOKUP_FORCE_SIZE = 4;
	public static final int GTK_ICON_LOOKUP_FORCE_REGULAR = 32;
	public static final int GTK_JUSTIFY_CENTER = 0x2;
	public static final int GTK_JUSTIFY_LEFT = 0x0;
	public static final int GTK_JUSTIFY_RIGHT = 0x1;
	public static final int GTK_MESSAGE_INFO = 0;
	public static final int GTK_MESSAGE_WARNING = 1;
	public static final int GTK_MESSAGE_QUESTION = 2;
	public static final int GTK_MESSAGE_ERROR = 3;
	public static final int GTK_MOVEMENT_VISUAL_POSITIONS = 1;
	public static final int GTK_ORIENTATION_HORIZONTAL = 0x0;
	public static final int GTK_ORIENTATION_VERTICAL = 0x1;
	public static final int GTK_PACK_END = 1;
	public static final int GTK_PACK_START = 0;
	public static final int GTK_PAGE_ORIENTATION_PORTRAIT = 0;
	public static final int GTK_PAGE_ORIENTATION_LANDSCAPE = 1;
	public static final int GTK_POLICY_ALWAYS = 0x0;
	public static final int GTK_POLICY_AUTOMATIC = 0x1;
	public static final int GTK_POLICY_NEVER = 0x2;
	public static final int GTK_POLICY_EXTERNAL = 0x3;
	public static final int GTK_POS_TOP = 0x2;
	public static final int GTK_POS_BOTTOM = 0x3;
	public static final int GTK_PRINT_CAPABILITY_PAGE_SET     = 1 << 0;
	public static final int GTK_PRINT_CAPABILITY_COPIES       = 1 << 1;
	public static final int GTK_PRINT_CAPABILITY_COLLATE      = 1 << 2;
	public static final int GTK_PRINT_PAGES_ALL = 0;
	public static final int GTK_PRINT_PAGES_CURRENT = 1;
	public static final int GTK_PRINT_PAGES_RANGES = 2;
	public static final int GTK_PRINT_PAGES_SELECTION = 3;
	public static final int GTK_PRINT_DUPLEX_SIMPLEX = 0;
	public static final int GTK_PRINT_DUPLEX_HORIZONTAL = 1;
	public static final int GTK_PRINT_DUPLEX_VERTICAL = 2;
	public static final int GTK_EVENT_CONTROLLER_SCROLL_BOTH_AXES = 5;
	public static final int GTK_PHASE_CAPTURE = 1;
	public static final int GTK_PHASE_BUBBLE = 2;
	public static final int GTK_PHASE_TARGET = 3;
	public static final int GTK_PROGRESS_LEFT_TO_RIGHT = 0x0;
	public static final int GTK_PROGRESS_BOTTOM_TO_TOP = 0x2;
	public static final int GTK_RESPONSE_CANCEL = 0xfffffffa;
	public static final int GTK_RESPONSE_OK = 0xfffffffb;
	public static final int GTK_RESPONSE_ACCEPT = -3;
	public static final int GTK_SCROLL_NONE = 0;
	public static final int GTK_SCROLL_JUMP = 1;
	public static final int GTK_SCROLL_STEP_BACKWARD = 2;
	public static final int GTK_SCROLL_STEP_FORWARD = 3;
	public static final int GTK_SCROLL_PAGE_BACKWARD = 4;
	public static final int GTK_SCROLL_PAGE_FORWARD = 5;
	public static final int GTK_SCROLL_STEP_UP = 6;
	public static final int GTK_SCROLL_STEP_DOWN = 7;
	public static final int GTK_SCROLL_PAGE_UP = 8;
	public static final int GTK_SCROLL_PAGE_DOWN = 9;
	public static final int GTK_SCROLL_STEP_LEFT = 10;
	public static final int GTK_SCROLL_STEP_RIGHT = 11;
	public static final int GTK_SCROLL_PAGE_LEFT = 12;
	public static final int GTK_SCROLL_PAGE_RIGHT = 13;
	public static final int GTK_SCROLL_START = 14;
	public static final int GTK_SCROLL_END = 15;
	public static final int GTK_SELECTION_BROWSE = 0x2;
	public static final int GTK_SELECTION_MULTIPLE = 0x3;
	public static final int GTK_SHADOW_ETCHED_IN = 0x3;
	public static final int GTK_SHADOW_ETCHED_OUT = 0x4;
	public static final int GTK_SHADOW_IN = 0x1;
	public static final int GTK_SHADOW_NONE = 0x0;
	public static final int GTK_SHADOW_OUT = 0x2;
	public static final int GTK_STATE_FLAG_NORMAL = 0;
	public static final int GTK_STATE_FLAG_ACTIVE = 1 << 0;
	public static final int GTK_STATE_FLAG_PRELIGHT = 1 << 1;
	public static final int GTK_STATE_FLAG_SELECTED = 1 << 2;
	public static final int GTK_STATE_FLAG_INSENSITIVE = 1 << 3;
	public static final int GTK_STATE_FLAG_INCONSISTENT = 1 << 4;
	public static final int GTK_STATE_FLAG_FOCUSED = 1 << 5;
	public static final int GTK_STATE_FLAG_BACKDROP  = 1 << 6;
	public static final int GTK_STATE_FLAG_LINK = 1 << 9;
	public static final int GTK_TEXT_DIR_NONE = 0;
	public static final int GTK_TEXT_DIR_LTR = 1;
	public static final int GTK_TEXT_DIR_RTL = 2;
	public static final int GTK_TEXT_WINDOW_TEXT = 2;
	public static final int GTK_TOOLBAR_ICONS = 0;
	public static final int GTK_TOOLBAR_TEXT = 1;
	public static final int GTK_TOOLBAR_BOTH = 2;
	public static final int GTK_TOOLBAR_BOTH_HORIZ = 3;
	public static final int GTK_TREE_VIEW_COLUMN_GROW_ONLY = 0;
	public static final int GTK_TREE_VIEW_COLUMN_AUTOSIZE = 1;
	public static final int GTK_TREE_VIEW_COLUMN_FIXED = 2;
	public static final int GTK_TREE_VIEW_DROP_BEFORE = 0;
	public static final int GTK_TREE_VIEW_DROP_AFTER = 1;
	public static final int GTK_TREE_VIEW_DROP_INTO_OR_BEFORE = 2;
	public static final int GTK_TREE_VIEW_DROP_INTO_OR_AFTER = 3;
	public static final int GTK_TREE_VIEW_GRID_LINES_NONE = 0;
	public static final int GTK_TREE_VIEW_GRID_LINES_HORIZONTAL = 1;
	public static final int GTK_TREE_VIEW_GRID_LINES_VERTICAL = 2;
	public static final int GTK_TREE_VIEW_GRID_LINES_BOTH = 3;
	public static final int GTK_STYLE_PROVIDER_PRIORITY_APPLICATION = 600;
	public static final int GTK_STYLE_PROVIDER_PRIORITY_USER = 800;
	public static final int GTK_UNIT_PIXEL = 0;
	public static final int GTK_UNIT_POINTS = 1;
	public static final int GTK_UNIT_INCH = 2;
	public static final int GTK_UNIT_MM = 3;
	public static final int GTK_WINDOW_POPUP = 0x1;
	public static final int GTK_WINDOW_TOPLEVEL = 0x0;
	public static final int GTK_WRAP_NONE = 0;
	public static final int GTK_WRAP_WORD = 2;
	public static final int GTK_WRAP_WORD_CHAR = 3;
	public static final int GTK_SHORTCUT_SCOPE_GLOBAL = 2;
	public static final int GTK_INPUT_HINT_NO_EMOJI = 1024;

	/** Classes */
	public static final byte[] GTK_STYLE_CLASS_VIEW = OS.ascii("view");
	public static final byte[] GTK_STYLE_CLASS_CELL = OS.ascii("cell");
	public static final byte[] GTK_STYLE_CLASS_PANE_SEPARATOR = OS.ascii("pane-separator");
	public static final byte[] GTK_STYLE_CLASS_SUGGESTED_ACTION = OS.ascii("suggested-action");
	public static final byte[] GTK_STYLE_CLASS_FRAME = OS.ascii("frame");

	/** Properties */
	public static final byte[] gtk_alternative_button_order = OS.ascii("gtk-alternative-button-order");
	public static final byte[] gtk_cursor_blink = OS.ascii("gtk-cursor-blink");
	public static final byte[] gtk_cursor_blink_time = OS.ascii("gtk-cursor-blink-time");
	public static final byte[] gtk_double_click_time = OS.ascii("gtk-double-click-time");
	public static final byte[] gtk_entry_select_on_focus = OS.ascii("gtk-entry-select-on-focus");
	public static final byte[] gtk_style_property_font = GTK.GTK4 ? OS.ascii("gtk-font-name") : OS.ascii("font");
	public static final byte[] gtk_menu_bar_accel = OS.ascii("gtk-menu-bar-accel");
	public static final byte[] gtk_theme_name = OS.ascii("gtk-theme-name");
	public static final byte[] gtk_im_module = OS.ascii("gtk-im-module");

	/** Misc **/
	public static final byte[] GTK_PRINT_SETTINGS_OUTPUT_URI = OS.ascii("output-uri");

	/**
	 * Needed to tell GTK 3 to prefer a dark or light theme in the UI.
	 * Improves the look of the Eclipse Dark theme in GTK 3 systems.
	 */
	public static final byte[] gtk_application_prefer_dark_theme = OS.ascii("gtk-application-prefer-dark-theme");

	/** Named icons.
	 * See https://docs.google.com/spreadsheet/pub?key=0AsPAM3pPwxagdGF4THNMMUpjUW5xMXZfdUNzMXhEa2c&amp;output=html
	 * See http://standards.freedesktop.org/icon-naming-spec/icon-naming-spec-latest.html#names
	 * Icon preview tool: gtk3-icon-browser
	 * Snippets often demonstrate usage of these. E.x 309, 258.
	 * */
	public static final byte[] GTK_NAMED_ICON_GO_UP = OS.ascii ("go-up-symbolic");
	public static final byte[] GTK_NAMED_ICON_GO_DOWN = OS.ascii ("go-down-symbolic");
	public static final byte[] GTK_NAMED_ICON_GO_NEXT = OS.ascii ("go-next-symbolic");
	public static final byte[] GTK_NAMED_ICON_GO_PREVIOUS = OS.ascii ("go-previous-symbolic");
	public static final byte[] GTK_NAMED_ICON_PAN_DOWN = OS.ascii ("pan-down-symbolic");
	public static final byte[] GTK_NAMED_LABEL_OK = OS.ascii("_OK");
	public static final byte[] GTK_NAMED_LABEL_CANCEL = OS.ascii("_Cancel");

	/** SWT Tools translates TYPE_sizeof() into sizeof(TYPE) at native level. os.c will have a binding to functions auto-generated in os_structs.h */
	public static final int GtkAllocation_sizeof() { return GTK_FFM.GtkAllocation_sizeof(); }
	public static final int GtkBorder_sizeof() { return GTK_FFM.GtkBorder_sizeof(); }
	public static final int GtkRequisition_sizeof() { return GTK_FFM.GtkRequisition_sizeof(); }
	public static final int GtkTextIter_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkTextIter_sizeof(); }
	public static final int GtkCellRendererText_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkCellRendererText_sizeof(); }
	public static final int GtkCellRendererTextClass_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkCellRendererTextClass_sizeof(); }
	public static final int GtkTreeIter_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkTreeIter_sizeof(); }

	/** GTK3 sizeof() [if-def'd in os.h] */
	public static final int GtkCellRendererPixbuf_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkCellRendererPixbuf_sizeof(); }
	public static final int GtkCellRendererPixbufClass_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkCellRendererPixbufClass_sizeof(); }
	public static final int GtkCellRendererToggle_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkCellRendererToggle_sizeof(); }
	public static final int GtkCellRendererToggleClass_sizeof() { return org.eclipse.swt.internal.ffm.FFMTypes.GtkCellRendererToggleClass_sizeof(); }


	/**
	 * Macros.
	 *
	 * Some of these are not found in dev documentation, only in the sources.
	 */
	/** @param widget cast=(GtkWidget *) */
	public static final long GTK_WIDGET_GET_CLASS(long widget) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_WIDGET_GET_CLASS(widget); }
	/** @method flags=const */
	public static final long GTK_TYPE_TEXT_VIEW_ACCESSIBLE() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_TEXT_VIEW_ACCESSIBLE(); }
	public static final boolean GTK_IS_BOX(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_BOX(obj); }
	public static final boolean GTK_IS_BUTTON(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_BUTTON(obj); }
	public static final boolean GTK_IS_LABEL(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_LABEL(obj); }
	public static final boolean GTK_IS_IM_CONTEXT(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_IM_CONTEXT(obj); }
	public static final boolean GTK_IS_SCROLLED_WINDOW(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_SCROLLED_WINDOW(obj); }
	public static final boolean GTK_IS_WINDOW(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_WINDOW(obj); }
	public static final boolean GTK_IS_CELL_RENDERER_PIXBUF(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_CELL_RENDERER_PIXBUF(obj); }
	public static final boolean GTK_IS_CELL_RENDERER_TEXT(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_CELL_RENDERER_TEXT(obj); }
	public static final boolean GTK_IS_CELL_RENDERER_TOGGLE(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_CELL_RENDERER_TOGGLE(obj); }
	public static final boolean GTK_IS_PLUG(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_PLUG(obj); }
	/** @method flags=const */
	public static final long GTK_TYPE_CELL_RENDERER_TEXT() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_CELL_RENDERER_TEXT(); }
	/** @method flags=const */
	public static final long GTK_TYPE_CELL_RENDERER_PIXBUF() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_CELL_RENDERER_PIXBUF(); }
	/** @method flags=const */
	public static final long GTK_TYPE_CELL_RENDERER_TOGGLE() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_CELL_RENDERER_TOGGLE(); }
	/** @method flags=const */
	public static final long GTK_TYPE_IM_MULTICONTEXT() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_IM_MULTICONTEXT(); }
	/** @method flags=const */
	public static final long GTK_TYPE_WIDGET() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_WIDGET(); }
	/** @method flags=const */
	public static final long GTK_TYPE_WINDOW() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_WINDOW(); }
	/** @method flags=const */
	public static final long GTK_TYPE_FILE_FILTER() { return org.eclipse.swt.internal.ffm.FFMTypes.GTK_TYPE_FILE_FILTER(); }

	/** GTK3 Macros [if-def'd in os.h] */
	public static final boolean GTK_IS_ACCEL_LABEL(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_ACCEL_LABEL(obj); }
	public static final boolean GTK_IS_CONTAINER(long obj) { return org.eclipse.swt.internal.ffm.FFMMacros.GTK_IS_CONTAINER(obj); }

	// See os_custom.h
	// Dynamically get's the function pointer to gtk_false(). Gtk3.
	public static final long GET_FUNCTION_POINTER_gtk_false() { return org.eclipse.swt.internal.ffm.FFMMacros.GET_FUNCTION_POINTER_gtk_false(); }


	/* GtkButton */
	public static final long gtk_button_get_type() { return GTK_FFM.gtk_button_get_type(); }
	public static final long gtk_button_new() { return GTK_FFM.gtk_button_new(); }
	/**
	 * @method flags=dynamic
	 * @param button cast=(GtkButton *)
	 * @param label cast=(const char *)
	 */
	public static final void gtk_button_set_label(long button, byte[] label) { GTK_FFM.gtk_button_set_label(button, label); }
	/** @param button cast=(GtkButton *) */
	public static final void gtk_button_set_use_underline(long button, boolean use_underline) { GTK_FFM.gtk_button_set_use_underline(button, use_underline); }

	/* Keyboard Accelerators */
	public static final int gtk_accelerator_get_default_mod_mask() { return GTK_FFM.gtk_accelerator_get_default_mod_mask(); }
	/**
	 * @param accelerator cast=(const gchar *)
	 * @param accelerator_key cast=(guint *)
	 * @param accelerator_mods cast=(GdkModifierType *)
	 */
	public static final void gtk_accelerator_parse(long accelerator, int [] accelerator_key, int [] accelerator_mods) { GTK_FFM.gtk_accelerator_parse(accelerator, accelerator_key, accelerator_mods); }
	/**
	 * @param accelerator_key cast=(guint)
	 * @param accelerator_mods cast=(GdkModifierType)
	 */
	public static final long gtk_accelerator_name(int accelerator_key, int accelerator_mods) { return GTK_FFM.gtk_accelerator_name(accelerator_key, accelerator_mods); }
	/**
	 * @param accelerator cast=(const gchar *)
	 * @param accelerator_key cast=(guint *)
	 * @param accelerator_mods cast=(GdkModifierType *)
	 */
	public static final void gtk_accelerator_parse(byte[] accelerator, int[] accelerator_key, int[] accelerator_mods) { GTK_FFM.gtk_accelerator_parse(accelerator, accelerator_key, accelerator_mods); }
	/** @method flags=dynamic */
	public static final long gtk_accel_group_new() { return GTK_FFM.gtk_accel_group_new(); }

	/* GtkAdjustment */
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final void gtk_adjustment_configure(long adjustment, double value, double lower, double upper, double step_increment, double page_increment, double page_size) { GTK_FFM.gtk_adjustment_configure(adjustment, value, lower, upper, step_increment, page_increment, page_size); }
	/**
	 * @param value cast=(gdouble)
	 * @param lower cast=(gdouble)
	 * @param upper cast=(gdouble)
	 * @param step_increment cast=(gdouble)
	 * @param page_increment cast=(gdouble)
	 * @param page_size cast=(gdouble)
	 */
	public static final long gtk_adjustment_new(double value, double lower, double upper, double step_increment, double page_increment, double page_size) { return GTK_FFM.gtk_adjustment_new(value, lower, upper, step_increment, page_increment, page_size); }
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final double gtk_adjustment_get_lower(long adjustment) { return GTK_FFM.gtk_adjustment_get_lower(adjustment); }
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final double gtk_adjustment_get_page_increment(long adjustment) { return GTK_FFM.gtk_adjustment_get_page_increment(adjustment); }
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final double gtk_adjustment_get_page_size(long adjustment) { return GTK_FFM.gtk_adjustment_get_page_size(adjustment); }
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final double gtk_adjustment_get_step_increment(long adjustment) { return GTK_FFM.gtk_adjustment_get_step_increment(adjustment); }
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final double gtk_adjustment_get_upper(long adjustment) { return GTK_FFM.gtk_adjustment_get_upper(adjustment); }
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final double gtk_adjustment_get_value(long adjustment) { return GTK_FFM.gtk_adjustment_get_value(adjustment); }
	/**
	 * @param adjustment cast=(GtkAdjustment *)
	 * @param value cast=(gdouble)
	 */
	public static final void gtk_adjustment_set_value(long adjustment, double value) { GTK_FFM.gtk_adjustment_set_value(adjustment, value); }
	/**
	 * @param adjustment cast=(GtkAdjustment *)
	 * @param value cast=(gdouble)
	 */
	public static final void gtk_adjustment_set_step_increment(long adjustment, double value) { GTK_FFM.gtk_adjustment_set_step_increment(adjustment, value); }
	/**
	 * @param adjustment cast=(GtkAdjustment *)
	 * @param value cast=(gdouble)
	 */
	public static final void gtk_adjustment_set_page_increment(long adjustment, double value) { GTK_FFM.gtk_adjustment_set_page_increment(adjustment, value); }

	/* GtkBorder */
	/** @param border cast=(GtkBorder *) */
	public static final void gtk_border_free(long border) { GTK_FFM.gtk_border_free(border); }

	/* GtkBox */
	/** @param box cast=(GtkBox *) */
	public static final void gtk_box_set_spacing(long box, int spacing) { GTK_FFM.gtk_box_set_spacing(box, spacing); }
	/**
	 * @param orientation cast=(GtkOrientation)
	 * @param spacing cast=(gint)
	 */
	public static final long gtk_box_new(int orientation, int spacing) { return GTK_FFM.gtk_box_new(orientation, spacing); }
	/**
	 * @param box cast=(GtkBox *)
	 * @param homogeneous cast=(gboolean)
	 */
	public static final void gtk_box_set_homogeneous(long box, boolean homogeneous) { GTK_FFM.gtk_box_set_homogeneous(box, homogeneous); }

	/* GtkCalendar */
	public static final long gtk_calendar_new() { return GTK_FFM.gtk_calendar_new(); }
	/**
	 * @param calendar cast=(GtkCalendar *)
	 * @param day cast=(guint)
	 */
	public static final void gtk_calendar_mark_day(long calendar, int day) { GTK_FFM.gtk_calendar_mark_day(calendar, day); }
	/**
	 * @param calendar cast=(GtkCalendar *)
	 */
	public static final void gtk_calendar_clear_marks(long calendar) { GTK_FFM.gtk_calendar_clear_marks(calendar); }


	/** @param cell_layout cast=(GtkCellLayout *) */
	public static final void gtk_cell_layout_clear(long cell_layout) { GTK_FFM.gtk_cell_layout_clear(cell_layout); }
	/** @param cell_layout cast=(GtkCellLayout *) */
	public static final long gtk_cell_layout_get_cells(long cell_layout) { return GTK_FFM.gtk_cell_layout_get_cells(cell_layout); }
	/**
	 * @param cell_layout cast=(GtkCellLayout *)
	 * @param cell cast=(GtkCellRenderer *)
	 * @param sentinel cast=(const gchar *),flags=sentinel
	 */
	public static final void gtk_cell_layout_set_attributes(long cell_layout, long cell, byte[] attribute, int column, long sentinel) { GTK_FFM.gtk_cell_layout_set_attributes(cell_layout, cell, attribute, column, sentinel); }
	/**
	 * @param cell_layout cast=(GtkCellLayout *)
	 * @param cell cast=(GtkCellRenderer *)
	 */
	public static final void gtk_cell_layout_pack_start(long cell_layout, long cell, boolean expand) { GTK_FFM.gtk_cell_layout_pack_start(cell_layout, cell, expand); }
	/**
	 * @param cell cast=(GtkCellRenderer *)
	 * @param widget cast=(GtkWidget *)
	 * @param minimum_size cast=(GtkRequisition *)
	 * @param natural_size cast=(GtkRequisition *)
	 */
	public static final void gtk_cell_renderer_get_preferred_size(long cell, long widget, GtkRequisition minimum_size, GtkRequisition natural_size) { GTK_FFM.gtk_cell_renderer_get_preferred_size(cell, widget, minimum_size, natural_size); }
	/**
	 * @param cell cast=(GtkCellRenderer *)
	 * @param xpad cast=(gint *)
	 * @param ypad cast=(gint *)
	 */
	public static final void gtk_cell_renderer_get_padding(long cell, int [] xpad, int [] ypad) { GTK_FFM.gtk_cell_renderer_get_padding(cell, xpad, ypad); }
	/**
	 * @param cell cast=(GtkCellRenderer *)
	 * @param widget cast=(GtkWidget *)
	 * @param width cast=(gint)
	 * @param minimum_height cast=(gint *)
	 * @param natural_height cast=(gint *)
	 */
	public static final void gtk_cell_renderer_get_preferred_height_for_width(long cell, long widget, int width, int[] minimum_height, int[] natural_height) { GTK_FFM.gtk_cell_renderer_get_preferred_height_for_width(cell, widget, width, minimum_height, natural_height); }
	/**
	 * @param cell cast=(GtkCellRenderer *)
	 * @param width cast=(gint)
	 * @param height cast=(gint)
	 */
	public static final void gtk_cell_renderer_set_fixed_size(long cell, int width, int height) { GTK_FFM.gtk_cell_renderer_set_fixed_size(cell, width, height); }
	/**
	 * @param cell cast=(GtkCellRenderer *)
	 * @param width cast=(gint *)
	 * @param height cast=(gint *)
	 */
	public static final void gtk_cell_renderer_get_fixed_size(long cell, int[] width, int[] height) { GTK_FFM.gtk_cell_renderer_get_fixed_size(cell, width, height); }
	public static final long gtk_cell_renderer_pixbuf_new() { return GTK_FFM.gtk_cell_renderer_pixbuf_new(); }
	public static final long gtk_cell_renderer_text_new() { return GTK_FFM.gtk_cell_renderer_text_new(); }
	public static final long gtk_cell_renderer_toggle_new() { return GTK_FFM.gtk_cell_renderer_toggle_new(); }
	/**
	 * @param cell_view cast=(GtkCellView *)
	 * @param fit_model cast=(gboolean)
	 */
	public static final void gtk_cell_view_set_fit_model(long cell_view, boolean fit_model) { GTK_FFM.gtk_cell_view_set_fit_model(cell_view, fit_model); }


	/* GtkCheckButton */
	public static final long gtk_check_button_new() { return GTK_FFM.gtk_check_button_new(); }

	/* General Gtk Functions */
	public static final long gtk_check_version(int required_major, int required_minor, int required_micro) { return GTK_FFM.gtk_check_version(required_major, required_minor, required_micro); }
	public static final long gtk_get_default_language() { return GTK_FFM.gtk_get_default_language(); }
	public static final int gtk_get_major_version() { return GTK_FFM.gtk_get_major_version(); }
	public static final int gtk_get_minor_version() { return GTK_FFM.gtk_get_minor_version(); }
	public static final int gtk_get_micro_version() { return GTK_FFM.gtk_get_micro_version(); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param cr cast=(cairo_t *)
	 * @param x cast=(gdouble)
	 * @param y cast=(gdouble)
	 * @param width cast=(gdouble)
	 * @param height cast=(gdouble)
	 */
	public static final void gtk_render_frame(long context, long cr, double x , double y, double width, double height) { GTK_FFM.gtk_render_frame(context, cr, x, y, width, height); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param cr cast=(cairo_t *)
	 * @param x cast=(gdouble)
	 * @param y cast=(gdouble)
	 * @param width cast=(gdouble)
	 * @param height cast=(gdouble)
	 */
	public static final void gtk_render_background(long context, long cr, double x , double y, double width, double height) { GTK_FFM.gtk_render_background(context, cr, x, y, width, height); }
	/**
	* @param context cast=(GtkStyleContext *)
	* @param cr cast=(cairo_t *)
	* @param x cast=(gdouble)
	* @param y cast=(gdouble)
	* @param width cast=(gdouble)
	* @param height cast=(gdouble)
	*/
	public static final void gtk_render_focus(long context, long cr,  double x , double y, double width, double height) { GTK_FFM.gtk_render_focus(context, cr, x, y, width, height); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param cr cast=(cairo_t *)
	 * @param x cast=(gdouble)
	 * @param y cast=(gdouble)
	 * @param width cast=(gdouble)
	 * @param height cast=(gdouble)
	 */
	public static final void gtk_render_handle(long context, long cr, double x , double y, double width, double height) { GTK_FFM.gtk_render_handle(context, cr, x, y, width, height); }
	/**
	 * @param func cast=(GtkPrinterFunc)
	 * @param data cast=(gpointer)
	 * @param destroy cast=(GDestroyNotify)
	 * @param wait cast=(gboolean)
	 */
	public static final void gtk_enumerate_printers(long func, long data, long destroy, boolean wait) { GTK_FFM.gtk_enumerate_printers(func, data, destroy, wait); }

	/* GtkColorChooser Interface */
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkColorChooser *)
	 * @param orientation cast=(GtkOrientation)
	 * @param colors_per_line cast=(gint)
	 * @param n_colors cast=(gint)
	 * @param colors cast=(GdkRGBA *)
	 */
	public static final void gtk_color_chooser_add_palette(long chooser, int orientation, int colors_per_line, int n_colors, long colors) { GTK_FFM.gtk_color_chooser_add_palette(chooser, orientation, colors_per_line, n_colors, colors); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkColorChooser *)
	 * @param use_alpha cast=(gboolean)
	 */
	public static final void gtk_color_chooser_set_use_alpha(long chooser, boolean use_alpha) { GTK_FFM.gtk_color_chooser_set_use_alpha(chooser, use_alpha); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkColorChooser *)
	 */
	public static final boolean gtk_color_chooser_get_use_alpha(long chooser) { return GTK_FFM.gtk_color_chooser_get_use_alpha(chooser); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkColorChooser *)
	 * @param color cast=(GdkRGBA *)
	 */
	public static final void gtk_color_chooser_set_rgba(long chooser, GdkRGBA color) { GTK_FFM.gtk_color_chooser_set_rgba(chooser, color); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkColorChooser *)
	 * @param color cast=(GdkRGBA *)
	 */
	public static final void gtk_color_chooser_get_rgba(long chooser, GdkRGBA color) { GTK_FFM.gtk_color_chooser_get_rgba(chooser, color); }
	/**
	 * @method flags=ignore_deprecations
	 * @param title cast=(const gchar *)
	 * @param parent cast=(GtkWindow *)
	 */
	public static final long gtk_color_chooser_dialog_new(byte[] title, long parent) { return GTK_FFM.gtk_color_chooser_dialog_new(title, parent); }

	/* GtkComboBox */
	public static final long gtk_combo_box_text_new() { return GTK_FFM.gtk_combo_box_text_new(); }
	public static final long gtk_combo_box_text_new_with_entry() { return GTK_FFM.gtk_combo_box_text_new_with_entry(); }
	/**
	 * @param combo_box cast=(GtkComboBoxText *)
	 * @param position cast=(gint)
	 * @param id cast=(const gchar *)
	 * @param text cast=(const gchar *)
	 */
	/* Do not call directly, instead use Combo.gtk_combo_box_insert(..) */
	public static final void gtk_combo_box_text_insert(long combo_box, int position, byte[] id, byte[] text) { GTK_FFM.gtk_combo_box_text_insert(combo_box, position, id, text); }
	/** @param combo_box cast=(GtkComboBoxText *) */
	public static final void gtk_combo_box_text_remove(long combo_box, int position) { GTK_FFM.gtk_combo_box_text_remove(combo_box, position); }
	/**
	 * @param combo_box cast=(GtkComboBoxText *)
	 */
	/* Do not call directly. Call Combo.gtk_combo_box_text_remove_all(..) instead). */
	public static final void gtk_combo_box_text_remove_all(long combo_box) { GTK_FFM.gtk_combo_box_text_remove_all(combo_box); }
	/**
	* @param combo_box cast=(GtkComboBox *)
	*/
	public static final int gtk_combo_box_get_active(long combo_box) { return GTK_FFM.gtk_combo_box_get_active(combo_box); }
	/**
	* @param combo_box cast=(GtkComboBox *)
	*/
	public static final long gtk_combo_box_get_model(long combo_box) { return GTK_FFM.gtk_combo_box_get_model(combo_box); }
	/**
	* @param combo_box cast=(GtkComboBox *)
	* @param index cast=(gint)
	*/
	public static final void gtk_combo_box_set_active(long combo_box, int index) { GTK_FFM.gtk_combo_box_set_active(combo_box, index); }

	/**
	* @param combo_box cast=(GtkComboBox *)
	*/
	public static final void gtk_combo_box_popup(long combo_box) { GTK_FFM.gtk_combo_box_popup(combo_box); }
	/**
	* @param combo_box cast=(GtkComboBox *)
	*/
	public static final void gtk_combo_box_popdown(long combo_box) { GTK_FFM.gtk_combo_box_popdown(combo_box); }

	/* GtkDialog */
	/**
	 * @param dialog cast=(GtkDialog *)
	 * @param button_text cast=(const gchar *)
	 * @param response_id cast=(gint)
	 */
	public static final long gtk_dialog_add_button(long dialog, byte[] button_text, int response_id) { return GTK_FFM.gtk_dialog_add_button(dialog, button_text, response_id); }

	/* GtkEditable Interface */
	/**
	 * @param editable cast=(GtkEditable *)
	 * @param start cast=(gint)
	 * @param end cast=(gint)
	 */
	public static final void gtk_editable_select_region(long editable, int start, int end) { GTK_FFM.gtk_editable_select_region(editable, start, end); }
	/** @param editable cast=(GtkEditable *) */
	public static final void gtk_editable_delete_selection(long editable) { GTK_FFM.gtk_editable_delete_selection(editable); }
	/**
	 * @param editable cast=(GtkEditable *)
	 * @param start_pos cast=(gint)
	 * @param end_pos cast=(gint)
	 */
	public static final void gtk_editable_delete_text(long editable, int start_pos, int end_pos) { GTK_FFM.gtk_editable_delete_text(editable, start_pos, end_pos); }
	/**
	 * @param entry cast=(GtkEditable *)
	 * @param editable cast=(gboolean)
	 */
	public static final void gtk_editable_set_editable(long entry, boolean editable) { GTK_FFM.gtk_editable_set_editable(entry, editable); }
	/** @param editable cast=(GtkEditable *) */
	public static final boolean gtk_editable_get_editable(long editable) { return GTK_FFM.gtk_editable_get_editable(editable); }
	/**
	 * @param editable cast=(GtkEditable *)
	 * @param position cast=(gint)
	 */
	public static final void gtk_editable_set_position(long editable, int position) { GTK_FFM.gtk_editable_set_position(editable, position); }
	/** @param editable cast=(GtkEditable *) */
	public static final int gtk_editable_get_position(long editable) { return GTK_FFM.gtk_editable_get_position(editable); }
	/**
	 * @param editable cast=(GtkEditable *)
	 * @param start cast=(gint *)
	 * @param end cast=(gint *)
	 */
	public static final boolean gtk_editable_get_selection_bounds(long editable, int[] start, int[] end) { return GTK_FFM.gtk_editable_get_selection_bounds(editable, start, end); }
	/**
	 * @param editable cast=(GtkEditable *)
	 * @param new_text cast=(gchar *)
	 * @param new_text_length cast=(gint)
	 * @param position cast=(gint *)
	 */
	public static final void gtk_editable_insert_text(long editable, byte[] new_text, int new_text_length, int[] position) { GTK_FFM.gtk_editable_insert_text(editable, new_text, new_text_length, position); }

	/* GtkEntry */
	public static final long gtk_entry_new() { return GTK_FFM.gtk_entry_new(); }
	/** @param entry cast=(GtkEntry *) */
	public static final char gtk_entry_get_invisible_char(long entry) { return GTK_FFM.gtk_entry_get_invisible_char(entry); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param ch cast=(gint)
	 */
	public static final void gtk_entry_set_invisible_char(long entry, char ch) { GTK_FFM.gtk_entry_set_invisible_char(entry, ch); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param icon_pos cast=(gint)
	 * @param icon_area cast=(GdkRectangle *),flags=no_in
	 */
	public static final void gtk_entry_get_icon_area(long entry, int icon_pos, GdkRectangle icon_area) { GTK_FFM.gtk_entry_get_icon_area(entry, icon_pos, icon_area); }
	/** @param entry cast=(GtkEntry *) */
	public static final int gtk_entry_get_max_length(long entry) { return GTK_FFM.gtk_entry_get_max_length(entry); }
	/** @param entry cast=(GtkEntry *) */
	public static final boolean gtk_entry_get_visibility(long entry) { return GTK_FFM.gtk_entry_get_visibility(entry); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param visible cast=(gboolean)
	 */
	public static final void gtk_entry_set_visibility(long entry, boolean visible) { GTK_FFM.gtk_entry_set_visibility(entry, visible); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param xalign cast=(gfloat)
	 */
	public static final void gtk_entry_set_alignment(long entry, float xalign) { GTK_FFM.gtk_entry_set_alignment(entry, xalign); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param setting cast=(gboolean)
	 */
	public static final void gtk_entry_set_has_frame(long entry, boolean setting) { GTK_FFM.gtk_entry_set_has_frame(entry, setting); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param iconPos cast=(gint)
	 * @param stock cast=(const gchar *)
	 */
	public static final void gtk_entry_set_icon_from_icon_name(long entry, int iconPos, byte[] stock) { GTK_FFM.gtk_entry_set_icon_from_icon_name(entry, iconPos, stock); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param icon_pos cast=(GtkEntryIconPosition)
	 * @param activatable cast=(gboolean)
	 */
	public static final void gtk_entry_set_icon_activatable(long entry, int icon_pos, boolean activatable) { GTK_FFM.gtk_entry_set_icon_activatable(entry, icon_pos, activatable); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param icon_pos cast=(GtkEntryIconPosition)
	 * @param sensitive cast=(gboolean)
	 */
	public static final void gtk_entry_set_icon_sensitive(long entry, int icon_pos, boolean sensitive) { GTK_FFM.gtk_entry_set_icon_sensitive(entry, icon_pos, sensitive); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param text cast=(const gchar *)
	 */
	public static final void gtk_entry_set_placeholder_text(long entry, byte[] text) { GTK_FFM.gtk_entry_set_placeholder_text(entry, text); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param max cast=(gint)
	 */
	public static final void gtk_entry_set_max_length(long entry, int max) { GTK_FFM.gtk_entry_set_max_length(entry, max); }
	/**
	 * @param entry cast=(GtkEntry *)
	 * @param tabs cast=(PangoTabArray *)
	 */
	public static final void gtk_entry_set_tabs(long entry, long tabs) { GTK_FFM.gtk_entry_set_tabs(entry, tabs); }

	/**
	 * @param entry cast=(GtkEntry *)
	 * @param hint cast=(GtkInputHints)
	 */
	public static final void gtk_entry_set_input_hints(long entry, int hint) { GTK_FFM.gtk_entry_set_input_hints(entry, hint); }

	/* GtkEntryBuffer */
	/**
	 * @param buffer cast=(GtkEntryBuffer *)
	 * @param position cast=(guint)
	 */
	public static final int gtk_entry_buffer_delete_text(long buffer, int position, int n_chars) { return GTK_FFM.gtk_entry_buffer_delete_text(buffer, position, n_chars); }
	/**
	 * @param buffer cast=(GtkEntryBuffer *)
	 * @param chars cast=(const char *)
	 */
	public static final void gtk_entry_buffer_set_text(long buffer, byte[] chars, int n_chars) { GTK_FFM.gtk_entry_buffer_set_text(buffer, chars, n_chars); }
	/** @param buffer cast=(GtkEntryBuffer *) */
	public static final long gtk_entry_buffer_get_text(long buffer) { return GTK_FFM.gtk_entry_buffer_get_text(buffer); }

	/* GtkExpander */
	/** @param label cast=(const gchar *) */
	public static final long gtk_expander_new(byte[] label) { return GTK_FFM.gtk_expander_new(label); }
	/** @param expander cast=(GtkExpander *) */
	public static final boolean gtk_expander_get_expanded(long expander) { return GTK_FFM.gtk_expander_get_expanded(expander); }
	/** @param expander cast=(GtkExpander *) */
	public static final void gtk_expander_set_expanded(long expander, boolean expanded) { GTK_FFM.gtk_expander_set_expanded(expander, expanded); }
	/**
	 * @param expander cast=(GtkExpander *)
	 * @param label_widget cast=(GtkWidget *)
	 */
	public static final void gtk_expander_set_label_widget(long expander, long label_widget) { GTK_FFM.gtk_expander_set_label_widget(expander, label_widget); }
	/** @param expander cast=(GtkExpander *) */
	public static final long gtk_expander_get_label_widget(long expander) { return GTK_FFM.gtk_expander_get_label_widget(expander); }

	/* GtkFileChooser */
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkFileChooser *)
	 * @param filter cast=(GtkFileFilter *)
	 */
	public static final void gtk_file_chooser_add_filter(long chooser, long filter) { GTK_FFM.gtk_file_chooser_add_filter(chooser, filter); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkFileChooser *)
	 */
	public static final long gtk_file_chooser_get_filter(long chooser) { return GTK_FFM.gtk_file_chooser_get_filter(chooser); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkFileChooser *)
	 * @param name cast=(const gchar *)
	 */
	public static final void gtk_file_chooser_set_current_name(long chooser, byte[] name) { GTK_FFM.gtk_file_chooser_set_current_name(chooser, name); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkFileChooser *)
	 * @param filter cast=(GtkFileFilter *)
	 */
	public static final void gtk_file_chooser_set_filter(long chooser, long filter) { GTK_FFM.gtk_file_chooser_set_filter(chooser, filter); }
	/**
	 * @method flags=ignore_deprecations
	 * @param chooser cast=(GtkFileChooser *)
	 * @param select_multiple cast=(gboolean)
	 */
	public static final void gtk_file_chooser_set_select_multiple(long chooser, boolean select_multiple) { GTK_FFM.gtk_file_chooser_set_select_multiple(chooser, select_multiple); }

	/* GtkEventController */
	/**
	 * @param controller cast=(GtkEventController *)
	 * @param phase cast=(GtkPropagationPhase)
	 */
	public static final void gtk_event_controller_set_propagation_phase(long controller, int phase) { GTK_FFM.gtk_event_controller_set_propagation_phase(controller, phase); }
	/** @param controller cast=(GtkEventController *) */
	public static final long gtk_event_controller_get_widget(long controller) { return GTK_FFM.gtk_event_controller_get_widget(controller); }

	/* GtkGestureSingle */
	/**
	 * @param gesture cast=(GtkGestureSingle *)
	 * @param button cast=(guint)
	 */
	public static final void gtk_gesture_single_set_button(long gesture, int button) { GTK_FFM.gtk_gesture_single_set_button(gesture, button); }
	/** @param gesture cast=(GtkGestureSingle *) */
	public static final int gtk_gesture_single_get_current_button(long gesture) { return GTK_FFM.gtk_gesture_single_get_current_button(gesture); }

	/* GtkFileChooserNative */
	/**
	 * @method flags=dynamic
	 * @param title cast=(const gchar *),flags=no_out
	 * @param parent cast=(GtkWindow *)
	 * @param accept_label cast=(const gchar *),flags=no_out
	 * @param cancel_label cast=(const gchar *),flags=no_out
	 */
	public static final long gtk_file_chooser_native_new(byte[] title, long parent, int action, byte[] accept_label, byte[] cancel_label) { return GTK_FFM.gtk_file_chooser_native_new(title, parent, action, accept_label, cancel_label); }

	/* GtkFileFilter */
	public static final long gtk_file_filter_new() { return GTK_FFM.gtk_file_filter_new(); }
	/**
	 * @param filter cast=(GtkFileFilter *)
	 * @param pattern cast=(const gchar *)
	 */
	public static final void gtk_file_filter_add_pattern(long filter, byte[] pattern) { GTK_FFM.gtk_file_filter_add_pattern(filter, pattern); }
	/** @param filter cast=(GtkFileFilter *) */
	public static final long gtk_file_filter_get_name(long filter) { return GTK_FFM.gtk_file_filter_get_name(filter); }
	/**
	 * @param filter cast=(GtkFileFilter *)
	 * @param name cast=(const gchar *)
	 */
	public static final void gtk_file_filter_set_name(long filter, byte[] name) { GTK_FFM.gtk_file_filter_set_name(filter, name); }

	/**
	 * @param gesture cast=(GtkGestureDrag *)
	 * @param x cast=(gdouble *)
	 * @param y cast=(gdouble *)
	 */
	public static final boolean gtk_gesture_drag_get_start_point(long gesture, double[] x, double [] y) { return GTK_FFM.gtk_gesture_drag_get_start_point(gesture, x, y); }
	/**
	 * @param gesture cast=(GtkGesture *)
	 */
	public static final boolean gtk_gesture_is_recognized(long gesture) { return GTK_FFM.gtk_gesture_is_recognized(gesture); }
	/**
	 * @param gesture cast=(GtkGesture *)
	 */
	public static final long gtk_gesture_get_last_updated_sequence(long gesture) { return GTK_FFM.gtk_gesture_get_last_updated_sequence(gesture); }
	/**
	 * @param gesture cast=(GtkGesture *)
	 * @param sequence cast=(GdkEventSequence *)
	 * @param x cast=(gdouble *)
	 * @param y cast=(gdouble *)
	 */
	public static final boolean gtk_gesture_get_point(long gesture, long sequence, double[] x, double [] y) { return GTK_FFM.gtk_gesture_get_point(gesture, sequence, x, y); }
	/**
	 * @param gesture cast=(GtkGestureSwipe *)
	 * @param velocity_x cast=(gdouble *)
	 * @param velocity_y cast=(gdouble *)
	 */
	public static final boolean gtk_gesture_swipe_get_velocity(long gesture, double [] velocity_x, double[] velocity_y) { return GTK_FFM.gtk_gesture_swipe_get_velocity(gesture, velocity_x, velocity_y); }
	/**
	 * @param gesture cast=(GtkGestureDrag *)
	 * @param x cast=(gdouble *)
	 * @param y cast=(gdouble *)
	 */
	public static final void gtk_gesture_drag_get_offset(long gesture, double[] x, double[] y) { GTK_FFM.gtk_gesture_drag_get_offset(gesture, x, y); }
	/**
	 * @param gesture cast=(GtkGestureRotate *)
	 */
	public static final double gtk_gesture_rotate_get_angle_delta(long gesture) { return GTK_FFM.gtk_gesture_rotate_get_angle_delta(gesture); }
	/**
	 * @param gesture cast=(GtkGestureZoom *)
	 */
	public static final double gtk_gesture_zoom_get_scale_delta(long gesture) { return GTK_FFM.gtk_gesture_zoom_get_scale_delta(gesture); }

	/* GtkFontChooserDialog */
	/**
	 * @method flags=ignore_deprecations
	 * @param title cast=(const gchar *)
	 * @param parent cast=(GtkWindow *)
	 */
	public static final long gtk_font_chooser_dialog_new(byte[] title, long parent) { return GTK_FFM.gtk_font_chooser_dialog_new(title, parent); }

	/* GtkFontChooser Interface */
	/**
	 * @method flags=ignore_deprecations
	 * @param fontchooser cast=(GtkFontChooser *)
	 */
	public static final long gtk_font_chooser_get_font(long fontchooser) { return GTK_FFM.gtk_font_chooser_get_font(fontchooser); }
	/**
	 * @method flags=ignore_deprecations
	 * @param fsd cast=(GtkFontChooser *)
	 * @param fontname cast=(const gchar *)
	 */
	public static final void gtk_font_chooser_set_font(long fsd, byte[] fontname) { GTK_FFM.gtk_font_chooser_set_font(fsd, fontname); }

	/* GtkFrame */
	/** @param label cast=(const gchar *) */
	public static final long gtk_frame_new(byte[] label) { return GTK_FFM.gtk_frame_new(label); }
	/** @param frame cast=(GtkFrame *) */
	public static final long gtk_frame_get_label_widget(long frame) { return GTK_FFM.gtk_frame_get_label_widget(frame); }
	/**
	 * @param frame cast=(GtkFrame *)
	 * @param label_widget cast=(GtkWidget *)
	 */
	public static final void gtk_frame_set_label_widget(long frame, long label_widget) { GTK_FFM.gtk_frame_set_label_widget(frame, label_widget); }

	/* GtkScale */
	/**
	 *  @param orientation cast=(GtkOrientation)
	 *  @param adjustment cast=(GtkAdjustment *)
	 */
	public static final long gtk_scale_new(int orientation, long adjustment) { return GTK_FFM.gtk_scale_new(orientation, adjustment); }
	/**
	 * @param scale cast=(GtkScale *)
	 * @param digits cast=(gint)
	 */
	public static final void gtk_scale_set_digits(long scale, int digits) { GTK_FFM.gtk_scale_set_digits(scale, digits); }
	/**
	 * @param scale cast=(GtkScale *)
	 * @param draw_value cast=(gboolean)
	 */
	public static final void gtk_scale_set_draw_value(long scale, boolean draw_value) { GTK_FFM.gtk_scale_set_draw_value(scale, draw_value); }

	/* GtkScrollbar */
	/**
	 * @param orientation cast=(GtkOrientation)
	 * @param adjustment cast=(GtkAdjustment *)
	 */
	public static final long gtk_scrollbar_new(int orientation, long adjustment) { return GTK_FFM.gtk_scrollbar_new(orientation, adjustment); }

	/* GtkSearchEntry */
	public static final long gtk_search_entry_new() { return GTK_FFM.gtk_search_entry_new(); }

	/* GtkSeparator */
	/** @param orientation cast=(GtkOrientation) */
	public static final long gtk_separator_new(int orientation) { return GTK_FFM.gtk_separator_new(orientation); }

	/* GtkIMContext */
	/** @param context cast=(GtkIMContext *) */
	public static final void gtk_im_context_focus_in(long context) { GTK_FFM.gtk_im_context_focus_in(context); }
	/** @param context cast=(GtkIMContext *) */
	public static final void gtk_im_context_focus_out(long context) { GTK_FFM.gtk_im_context_focus_out(context); }
	/**
	 * @param context cast=(GtkIMContext *)
	 * @param str cast=(gchar **)
	 * @param attrs cast=(PangoAttrList **)
	 * @param cursor_pos cast=(gint *)
	 */
	public static final void gtk_im_context_get_preedit_string(long context, long [] str, long [] attrs, int[] cursor_pos) { GTK_FFM.gtk_im_context_get_preedit_string(context, str, attrs, cursor_pos); }
	public static final long gtk_im_context_get_type() { return GTK_FFM.gtk_im_context_get_type(); }
	/** @param context cast=(GtkIMContext *) */
	public static final void gtk_im_context_reset(long context) { GTK_FFM.gtk_im_context_reset(context); }
	/**
	 * @param context cast=(GtkIMContext *)
	 * @param window cast=(GdkWindow *)
	 */
	public static final void gtk_im_context_set_client_window(long context, long window) { GTK_FFM.gtk_im_context_set_client_window(context, window); }
	/**
	 * @param context cast=(GtkIMContext *)
	 * @param area cast=(GdkRectangle *),flags=no_out
	 */
	public static final void gtk_im_context_set_cursor_location(long context, GdkRectangle area) { GTK_FFM.gtk_im_context_set_cursor_location(context, area); }

	/* GtkIMMulticontext */
	public static final long gtk_im_multicontext_new() { return GTK_FFM.gtk_im_multicontext_new(); }

	/* GtkImage */
	public static final long gtk_image_new() { return GTK_FFM.gtk_image_new(); }
	/**
	 * @param image cast=(GtkImage *)
	 * @param pixel_size cast=(gint)
	 */
	public static final void gtk_image_set_pixel_size(long image, int pixel_size) { GTK_FFM.gtk_image_set_pixel_size(image, pixel_size); }

	/* GtkLabel */
	public static final long gtk_label_get_type() { return GTK_FFM.gtk_label_get_type(); }
	/** @param label cast=(const gchar *) */
	public static final long gtk_label_new(byte[] label) { return GTK_FFM.gtk_label_new(label); }
	/** @param str cast=(const gchar *) */
	public static final long gtk_label_new_with_mnemonic(byte[] str) { return GTK_FFM.gtk_label_new_with_mnemonic(str); }
	/** @param label cast=(GtkLabel *) */
	public static final long gtk_label_get_layout(long label) { return GTK_FFM.gtk_label_get_layout(label); }
	/** @param label cast=(GtkLabel *) */
	public static final int gtk_label_get_mnemonic_keyval(long label) { return GTK_FFM.gtk_label_get_mnemonic_keyval(label); }
	/**
	 * @param label cast=(GtkLabel *)
	 * @param attrs cast=(PangoAttrList *)
	 */
	public static final void gtk_label_set_attributes(long label, long attrs) { GTK_FFM.gtk_label_set_attributes(label, attrs); }
	/**
	 * @param label cast=(GtkLabel *)
	 * @param jtype cast=(GtkJustification)
	 */
	public static final void gtk_label_set_justify(long label, int jtype) { GTK_FFM.gtk_label_set_justify(label, jtype); }
	/**
	 * @param label cast=(GtkLabel *)
	 * @param str cast=(const gchar *)
	 */
	public static final void gtk_label_set_text(long label, long str) { GTK_FFM.gtk_label_set_text(label, str); }
	/**
	 * @param label cast=(GtkLabel *)
	 * @param str cast=(const gchar *)
	 */
	public static final void gtk_label_set_text(long label, byte[] str) { GTK_FFM.gtk_label_set_text(label, str); }
	/**
	 * @param label cast=(GtkLabel *)
	 * @param str cast=(const gchar *)
	 */
	public static final void gtk_label_set_text_with_mnemonic(long label, byte[] str) { GTK_FFM.gtk_label_set_text_with_mnemonic(label, str); }
	/**
	 * @param label cast=(GtkLabel *)
	 * @param xalign cast=(gfloat)
	 */
	public static final void gtk_label_set_xalign(long label, float xalign) { GTK_FFM.gtk_label_set_xalign(label, xalign); }
	/**
	* @param label cast=(GtkLabel *)
	* @param yalign cast=(gfloat)
	*/
	public static final void gtk_label_set_yalign(long label, float yalign) { GTK_FFM.gtk_label_set_yalign(label, yalign); }

	/* GtkListStore */
	/**
	 * @param list_store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_list_store_append(long list_store, long iter) { GTK_FFM.gtk_list_store_append(list_store, iter); }
	/** @param store cast=(GtkListStore *) */
	public static final void gtk_list_store_clear(long store) { GTK_FFM.gtk_list_store_clear(store); }
	/**
	 * @param list_store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param position cast=(gint)
	 */
	public static final void gtk_list_store_insert(long list_store, long iter, int position) { GTK_FFM.gtk_list_store_insert(list_store, iter, position); }
	/**
	 * @param numColumns cast=(gint)
	 * @param types cast=(GType *)
	 */
	public static final long gtk_list_store_newv(int numColumns, long [] types) { return GTK_FFM.gtk_list_store_newv(numColumns, types); }
	/**
	 * @param list_store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_list_store_remove(long list_store, long iter) { GTK_FFM.gtk_list_store_remove(list_store, iter); }
	/**
	 * @param store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_list_store_set(long store, long iter, int column, byte[] value, int terminator) { GTK_FFM.gtk_list_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_list_store_set(long store, long iter, int column, int value, int terminator) { GTK_FFM.gtk_list_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_list_store_set(long store, long iter, int column, long value, int terminator) { GTK_FFM.gtk_list_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param value flags=no_out
	 */
	public static final void gtk_list_store_set(long store, long iter, int column, GdkRGBA value, int terminator) { GTK_FFM.gtk_list_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_list_store_set(long store, long iter, int column, boolean value, int terminator) { GTK_FFM.gtk_list_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkListStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param value cast=(GValue *)
	 */
	public static final void gtk_list_store_set_value(long store, long iter, int column, long value) { GTK_FFM.gtk_list_store_set_value(store, iter, column, value); }

	/* GtkCssProvider */
	public static final long gtk_css_provider_new() { return GTK_FFM.gtk_css_provider_new(); }
	/** @param provider cast=(GtkCssProvider *) */
	public static final long gtk_css_provider_to_string(long provider) { return GTK_FFM.gtk_css_provider_to_string(provider); }

	/* GtkStyleContext */
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param provider cast=(GtkStyleProvider *)
	 * @param priority cast=(guint)
	 */
	public static final void gtk_style_context_add_provider(long context, long provider, int priority) { GTK_FFM.gtk_style_context_add_provider(context, provider, priority); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param class_name cast=(const gchar *)
	 */
	public static final void gtk_style_context_add_class(long context, byte[] class_name) { GTK_FFM.gtk_style_context_add_class(context, class_name); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param class_name cast=(const gchar *)
	 */
	public static final void gtk_style_context_remove_class(long context, byte[] class_name) { GTK_FFM.gtk_style_context_remove_class(context, class_name); }
	/** @param self cast=(GtkStyleContext *) */
	public static final void gtk_style_context_save(long self) { GTK_FFM.gtk_style_context_save(self); }
	/** @param self cast=(GtkStyleContext *) */
	public static final void gtk_style_context_restore(long self) { GTK_FFM.gtk_style_context_restore(self); }
	/**
	 * @param context cast=(GtkStyleContext *)
	 * @param flags cast=(GtkStateFlags)
	 */
	public static final void gtk_style_context_set_state(long context, long flags) { GTK_FFM.gtk_style_context_set_state(context, flags); }

	/* GtkPopover */
	/** @param popover cast=(GtkPopover *) */
	public static final void gtk_popover_popdown(long popover) { GTK_FFM.gtk_popover_popdown(popover); }
	/** @param popover cast=(GtkPopover *) */
	public static final void gtk_popover_popup(long popover) { GTK_FFM.gtk_popover_popup(popover); }
	/**
	 * @param popover cast=(GtkPopover *)
	 * @param position cast=(GtkPositionType)
	 */
	public static final void gtk_popover_set_position(long popover, int position) { GTK_FFM.gtk_popover_set_position(popover, position); }

	/**
	 * @param popover cast=(GtkPopover *)
	 * @param rect cast=(const GdkRectangle *)
	 */
	public static final void gtk_popover_set_pointing_to(long popover, GdkRectangle rect) { GTK_FFM.gtk_popover_set_pointing_to(popover, rect); }

	/* GtkMenuButton */
	public static final long gtk_menu_button_new() { return GTK_FFM.gtk_menu_button_new(); }

	/* GtkMessageDialog */
	/**
	 * @param parent cast=(GtkWindow *)
	 * @param flags cast=(GtkDialogFlags)
	 * @param type cast=(GtkMessageType)
	 * @param buttons cast=(GtkButtonsType)
	 * @param message_format cast=(const gchar *)
	 * @param arg cast=(const gchar *)
	 */
	public static final long gtk_message_dialog_new(long parent, int flags, int type, int buttons, byte[] message_format, byte[] arg) { return GTK_FFM.gtk_message_dialog_new(parent, flags, type, buttons, message_format, arg); }
	/**
	 * @param message_dialog cast=(GtkMessageDialog *)
	 * @param message_format cast=(const gchar *)
	 * @param arg cast=(const gchar *)
	 */
	public static final void gtk_message_dialog_format_secondary_text(long message_dialog, byte[] message_format, byte[] arg) { GTK_FFM.gtk_message_dialog_format_secondary_text(message_dialog, message_format, arg); }

	/* GtkNativeDialog */
	/** @param dialog cast=(GtkNativeDialog *) */
	public static final void gtk_native_dialog_show(long dialog) { GTK_FFM.gtk_native_dialog_show(dialog); }

	/* GtkNotebook */
	public static final long gtk_notebook_new() { return GTK_FFM.gtk_notebook_new(); }
	/** @param notebook cast=(GtkNotebook *) */
	public static final int gtk_notebook_get_n_pages(long notebook) { return GTK_FFM.gtk_notebook_get_n_pages(notebook); }
	/** @param notebook cast=(GtkNotebook *) */
	public static final int gtk_notebook_get_current_page(long notebook) { return GTK_FFM.gtk_notebook_get_current_page(notebook); }
	/**
	 * @param notebook cast=(GtkNotebook *)
	 * @param page_num cast=(gint)
	 */
	public static final void gtk_notebook_set_current_page(long notebook, int page_num) { GTK_FFM.gtk_notebook_set_current_page(notebook, page_num); }
	/** @param notebook cast=(GtkNotebook *) */
	public static final boolean gtk_notebook_get_scrollable(long notebook) { return GTK_FFM.gtk_notebook_get_scrollable(notebook); }
	/**
	 * @param notebook cast=(GtkNotebook *)
	 * @param scrollable cast=(gboolean)
	 */
	public static final void gtk_notebook_set_scrollable(long notebook, boolean scrollable) { GTK_FFM.gtk_notebook_set_scrollable(notebook, scrollable); }
	/**
	 * @param notebook cast=(GtkNotebook *)
	 * @param child cast=(GtkWidget *)
	 * @param tab_label cast=(GtkWidget *)
	 * @param position cast=(gint)
	 */
	public static final void gtk_notebook_insert_page(long notebook, long child, long tab_label, int position) { GTK_FFM.gtk_notebook_insert_page(notebook, child, tab_label, position); }
	/**
	 * @param notebook cast=(GtkNotebook *)
	 * @param page_num cast=(gint)
	 */
	public static final void gtk_notebook_remove_page(long notebook, int page_num) { GTK_FFM.gtk_notebook_remove_page(notebook, page_num); }
	/** @param notebook cast=(GtkNotebook *) */
	public static final void gtk_notebook_next_page(long notebook) { GTK_FFM.gtk_notebook_next_page(notebook); }
	/** @param notebook cast=(GtkNotebook *) */
	public static final void gtk_notebook_prev_page(long notebook) { GTK_FFM.gtk_notebook_prev_page(notebook); }
	/**
	 * @param notebook cast=(GtkNotebook *)
	 * @param show_tabs cast=(gboolean)
	 */
	public static final void gtk_notebook_set_show_tabs(long notebook, boolean show_tabs) { GTK_FFM.gtk_notebook_set_show_tabs(notebook, show_tabs); }
	/**
	 * @param notebook cast=(GtkNotebook *)
	 * @param pos cast=(GtkPositionType)
	 */
	public static final void gtk_notebook_set_tab_pos(long notebook, int pos) { GTK_FFM.gtk_notebook_set_tab_pos(notebook, pos); }

	/* GtkOrientable Interface */
	/**
	 * @param orientable cast=(GtkOrientable *)
	 * @param orientation cast=(GtkOrientation)
	 */
	public static final void gtk_orientable_set_orientation(long orientable, int orientation) { GTK_FFM.gtk_orientable_set_orientation(orientable, orientation); }

	/* GtkPageSetup */
	public static final long gtk_page_setup_new() { return GTK_FFM.gtk_page_setup_new(); }
	/** @param setup cast=(GtkPageSetup *) */
	public static final int gtk_page_setup_get_orientation(long setup) { return GTK_FFM.gtk_page_setup_get_orientation(setup); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param orientation cast=(GtkPageOrientation)
	 */
	public static final void gtk_page_setup_set_orientation(long setup, int orientation) { GTK_FFM.gtk_page_setup_set_orientation(setup, orientation); }
	/** @param setup cast=(GtkPageSetup *) */
	public static final long gtk_page_setup_get_paper_size(long setup) { return GTK_FFM.gtk_page_setup_get_paper_size(setup); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param size cast=(GtkPaperSize *)
	 */
	public static final void gtk_page_setup_set_paper_size(long setup, long size) { GTK_FFM.gtk_page_setup_set_paper_size(setup, size); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_top_margin(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_top_margin(setup, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param margin cast=(gdouble)
	 * @param unit cast=(GtkUnit)
	 */
	public static final void gtk_page_setup_set_top_margin(long setup, double margin, int unit) { GTK_FFM.gtk_page_setup_set_top_margin(setup, margin, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_bottom_margin(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_bottom_margin(setup, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param margin cast=(gdouble)
	 * @param unit cast=(GtkUnit)
	 */
	public static final void gtk_page_setup_set_bottom_margin(long setup, double margin, int unit) { GTK_FFM.gtk_page_setup_set_bottom_margin(setup, margin, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_left_margin(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_left_margin(setup, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param margin cast=(gdouble)
	 * @param unit cast=(GtkUnit)
	 */
	public static final void gtk_page_setup_set_left_margin(long setup, double margin, int unit) { GTK_FFM.gtk_page_setup_set_left_margin(setup, margin, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_right_margin(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_right_margin(setup, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param margin cast=(gdouble)
	 * @param unit cast=(GtkUnit)
	 */
	public static final void gtk_page_setup_set_right_margin(long setup, double margin, int unit) { GTK_FFM.gtk_page_setup_set_right_margin(setup, margin, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_paper_width(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_paper_width(setup, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_paper_height(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_paper_height(setup, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_page_width(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_page_width(setup, unit); }
	/**
	 * @param setup cast=(GtkPageSetup *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_page_setup_get_page_height(long setup, int unit) { return GTK_FFM.gtk_page_setup_get_page_height(setup, unit); }

	/* GtkPaperSize */
	/** @param size cast=(GtkPaperSize *) */
	public static final void gtk_paper_size_free(long size) { GTK_FFM.gtk_paper_size_free(size); }
	/** @param name cast=(const gchar *) */
	public static final long gtk_paper_size_new(byte [] name) { return GTK_FFM.gtk_paper_size_new(name); }
	/**
	 * @param ppd_name cast=(const gchar *)
	 * @param ppd_display_name cast=(const gchar *)
	 * @param width cast=(gdouble)
	 * @param height cast=(gdouble)
	 */
	public static final long gtk_paper_size_new_from_ppd(byte [] ppd_name, byte [] ppd_display_name, double width, double height) { return GTK_FFM.gtk_paper_size_new_from_ppd(ppd_name, ppd_display_name, width, height); }
	/**
	 * @param name cast=(const gchar *)
	 * @param display_name cast=(const gchar *)
	 * @param width cast=(gdouble)
	 * @param height cast=(gdouble)
	 * @param unit cast=(GtkUnit)
	 */
	public static final long gtk_paper_size_new_custom(byte [] name, byte [] display_name, double width, double height, int unit) { return GTK_FFM.gtk_paper_size_new_custom(name, display_name, width, height, unit); }
	/** @param size cast=(GtkPaperSize *) */
	public static final long gtk_paper_size_get_name(long size) { return GTK_FFM.gtk_paper_size_get_name(size); }
	/** @param size cast=(GtkPaperSize *) */
	public static final long gtk_paper_size_get_display_name(long size) { return GTK_FFM.gtk_paper_size_get_display_name(size); }
	/** @param size cast=(GtkPaperSize *) */
	public static final long gtk_paper_size_get_ppd_name(long size) { return GTK_FFM.gtk_paper_size_get_ppd_name(size); }
	/**
	 * @param size cast=(GtkPaperSize *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_paper_size_get_width(long size, int unit) { return GTK_FFM.gtk_paper_size_get_width(size, unit); }
	/**
	 * @param size cast=(GtkPaperSize *)
	 * @param unit cast=(GtkUnit)
	 */
	public static final double gtk_paper_size_get_height(long size, int unit) { return GTK_FFM.gtk_paper_size_get_height(size, unit); }
	/** @param size cast=(GtkPaperSize *) */
	public static final boolean gtk_paper_size_is_custom(long size) { return GTK_FFM.gtk_paper_size_is_custom(size); }

	/* GtkPrinter */
	/** @param printer cast=(GtkPrinter *) */
	public static final long gtk_printer_get_backend(long printer) { return GTK_FFM.gtk_printer_get_backend(printer); }
	/** @param printer cast=(GtkPrinter *) */
	public static final long gtk_printer_get_name(long printer) { return GTK_FFM.gtk_printer_get_name(printer); }
	/** @param printer cast=(GtkPrinter *) */
	public static final boolean gtk_printer_is_default(long printer) { return GTK_FFM.gtk_printer_is_default(printer); }

	/* GtkPrintJob */
	/**
	 * @param title cast=(const gchar *)
	 * @param printer cast=(GtkPrinter *)
	 * @param settings cast=(GtkPrintSettings *)
	 * @param page_setup cast=(GtkPageSetup *)
	 */
	public static final long gtk_print_job_new(byte[] title, long printer, long settings, long page_setup) { return GTK_FFM.gtk_print_job_new(title, printer, settings, page_setup); }
	/**
	 * @param job cast=(GtkPrintJob *)
	 * @param error cast=(GError **)
	 */
	public static final long gtk_print_job_get_surface(long job, long error[]) { return GTK_FFM.gtk_print_job_get_surface(job, error); }
	/**
	 * @param job cast=(GtkPrintJob *)
	 * @param callback cast=(GtkPrintJobCompleteFunc)
	 * @param user_data cast=(gpointer)
	 * @param dnotify cast=(GDestroyNotify)
	 */
	public static final void gtk_print_job_send(long job, long callback, long user_data, long dnotify) { GTK_FFM.gtk_print_job_send(job, callback, user_data, dnotify); }

	/* GtkPrintSettings */
	public static final long gtk_print_settings_new() { return GTK_FFM.gtk_print_settings_new(); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param func cast=(GtkPrintSettingsFunc)
	 * @param data cast=(gpointer)
	 */
	public static final void gtk_print_settings_foreach(long settings, long func, long data) { GTK_FFM.gtk_print_settings_foreach(settings, func, data); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param key cast=(const gchar *)
	 */
	public static final long gtk_print_settings_get(long settings, byte [] key) { return GTK_FFM.gtk_print_settings_get(settings, key); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param key cast=(const gchar *)
	 * @param value cast=(const gchar *)
	 */
	public static final void gtk_print_settings_set(long settings, byte [] key, byte [] value) { GTK_FFM.gtk_print_settings_set(settings, key, value); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param printer cast=(const gchar *)
	 */
	public static final void gtk_print_settings_set_printer(long settings, byte[] printer) { GTK_FFM.gtk_print_settings_set_printer(settings, printer); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param orientation cast=(GtkPageOrientation)
	 */
	public static final void gtk_print_settings_set_orientation(long settings, int orientation) { GTK_FFM.gtk_print_settings_set_orientation(settings, orientation); }
	/** @param settings cast=(GtkPrintSettings *) */
	public static final boolean gtk_print_settings_get_collate(long settings) { return GTK_FFM.gtk_print_settings_get_collate(settings); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param collate cast=(gboolean)
	 */
	public static final void gtk_print_settings_set_collate(long settings, boolean collate) { GTK_FFM.gtk_print_settings_set_collate(settings, collate); }
	/** @param settings cast=(GtkPrintSettings *) */
	public static final int gtk_print_settings_get_duplex(long settings) { return GTK_FFM.gtk_print_settings_get_duplex(settings); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param duplex cast=(GtkPrintDuplex)
	 */
	public static final void gtk_print_settings_set_duplex(long settings, int duplex) { GTK_FFM.gtk_print_settings_set_duplex(settings, duplex); }
	/** @param settings cast=(GtkPrintSettings *) */
	public static final int gtk_print_settings_get_n_copies(long settings) { return GTK_FFM.gtk_print_settings_get_n_copies(settings); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param num_copies cast=(gint)
	 */
	public static final void gtk_print_settings_set_n_copies(long settings, int num_copies) { GTK_FFM.gtk_print_settings_set_n_copies(settings, num_copies); }
	/** @param settings cast=(GtkPrintSettings *) */
	public static final int gtk_print_settings_get_print_pages(long settings) { return GTK_FFM.gtk_print_settings_get_print_pages(settings); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param pages cast=(GtkPrintPages)
	 */
	public static final void gtk_print_settings_set_print_pages(long settings, int pages) { GTK_FFM.gtk_print_settings_set_print_pages(settings, pages); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param num_ranges cast=(gint *)
	 */
	public static final long gtk_print_settings_get_page_ranges(long settings, int[] num_ranges) { return GTK_FFM.gtk_print_settings_get_page_ranges(settings, num_ranges); }
	/**
	 * @param settings cast=(GtkPrintSettings *)
	 * @param page_ranges cast=(GtkPageRange *)
	 * @param num_ranges cast=(gint)
	 */
	public static final void gtk_print_settings_set_page_ranges(long settings, int[] page_ranges, int num_ranges) { GTK_FFM.gtk_print_settings_set_page_ranges(settings, page_ranges, num_ranges); }
	/** @param settings cast=(GtkPrintSettings *) */
	public static final int gtk_print_settings_get_resolution(long settings) { return GTK_FFM.gtk_print_settings_get_resolution(settings); }

	/* GtkPrintUnixDialog */
	/**
	 * @param title cast=(const gchar *)
	 * @param parent cast=(GtkWindow *)
	 */
	public static final long gtk_print_unix_dialog_new(byte[] title, long parent) { return GTK_FFM.gtk_print_unix_dialog_new(title, parent); }
	/**
	 * @param dialog cast=(GtkPrintUnixDialog *)
	 * @param embed cast=(gboolean)
	 */
	public static final void gtk_print_unix_dialog_set_embed_page_setup(long dialog, boolean embed) { GTK_FFM.gtk_print_unix_dialog_set_embed_page_setup(dialog, embed); }
	/**
	 * @param dialog cast=(GtkPrintUnixDialog *)
	 * @param page_setup cast=(GtkPageSetup *)
	 */
	public static final void gtk_print_unix_dialog_set_page_setup(long dialog, long page_setup) { GTK_FFM.gtk_print_unix_dialog_set_page_setup(dialog, page_setup); }
	/** @param dialog cast=(GtkPrintUnixDialog *) */
	public static final long gtk_print_unix_dialog_get_page_setup(long dialog) { return GTK_FFM.gtk_print_unix_dialog_get_page_setup(dialog); }
	/**
	 * @param dialog cast=(GtkPrintUnixDialog *)
	 * @param current_page cast=(gint)
	 */
	public static final void gtk_print_unix_dialog_set_current_page(long dialog, int current_page) { GTK_FFM.gtk_print_unix_dialog_set_current_page(dialog, current_page); }
	/** @param dialog cast=(GtkPrintUnixDialog *) */
	public static final int gtk_print_unix_dialog_get_current_page(long dialog) { return GTK_FFM.gtk_print_unix_dialog_get_current_page(dialog); }
	/**
	 * @param dialog cast=(GtkPrintUnixDialog *)
	 * @param settings cast=(GtkPrintSettings *)
	 */
	public static final void gtk_print_unix_dialog_set_settings(long dialog, long settings) { GTK_FFM.gtk_print_unix_dialog_set_settings(dialog, settings); }
	/** @param dialog cast=(GtkPrintUnixDialog *) */
	public static final long gtk_print_unix_dialog_get_settings(long dialog) { return GTK_FFM.gtk_print_unix_dialog_get_settings(dialog); }
	/** @param dialog cast=(GtkPrintUnixDialog *) */
	public static final long gtk_print_unix_dialog_get_selected_printer(long dialog) { return GTK_FFM.gtk_print_unix_dialog_get_selected_printer(dialog); }
	/**
	 * @param dialog cast=(GtkPrintUnixDialog *)
	 * @param capabilities cast=(GtkPrintCapabilities)
	 */
	public static final void gtk_print_unix_dialog_set_manual_capabilities(long dialog, long capabilities) { GTK_FFM.gtk_print_unix_dialog_set_manual_capabilities(dialog, capabilities); }
	/** @param dialog cast=(GtkPrintUnixDialog *) */
	public static final void gtk_print_unix_dialog_set_support_selection(long dialog, boolean support_selection) { GTK_FFM.gtk_print_unix_dialog_set_support_selection(dialog, support_selection); }
	/** @param dialog cast=(GtkPrintUnixDialog *) */
	public static final void gtk_print_unix_dialog_set_has_selection(long dialog, boolean has_selection) { GTK_FFM.gtk_print_unix_dialog_set_has_selection(dialog, has_selection); }

	/* GtkProgressBar */
	public static final long gtk_progress_bar_new() { return GTK_FFM.gtk_progress_bar_new(); }
	/** @param pbar cast=(GtkProgressBar *) */
	public static final void gtk_progress_bar_pulse(long pbar) { GTK_FFM.gtk_progress_bar_pulse(pbar); }
	/**
	 * @param pbar cast=(GtkProgressBar *)
	 * @param fraction cast=(gdouble)
	 */
	public static final void gtk_progress_bar_set_fraction(long pbar, double fraction) { GTK_FFM.gtk_progress_bar_set_fraction(pbar, fraction); }
	/**
	 * @param pbar cast=(GtkProgressBar *)
	 * @param inverted cast=(gboolean)
	 */
	public static final void gtk_progress_bar_set_inverted(long pbar, boolean inverted) { GTK_FFM.gtk_progress_bar_set_inverted(pbar, inverted); }

	/* GtkRange */
	/** @param range cast=(GtkRange *) */
	public static final long gtk_range_get_adjustment(long range) { return GTK_FFM.gtk_range_get_adjustment(range); }
	/** @param range cast=(GtkRange *) */
	public static final void gtk_range_set_increments(long range, double step, double page) { GTK_FFM.gtk_range_set_increments(range, step, page); }
	/** @param range cast=(GtkRange *) */
	public static final void gtk_range_set_inverted(long range, boolean setting) { GTK_FFM.gtk_range_set_inverted(range, setting); }
	/** @param range cast=(GtkRange *) */
	public static final void gtk_range_set_range(long range, double min, double max) { GTK_FFM.gtk_range_set_range(range, min, max); }
	/** @param range cast=(GtkRange *) */
	public static final double gtk_range_get_value(long range) { return GTK_FFM.gtk_range_get_value(range); }
	/** @param range cast=(GtkRange *) */
	public static final void gtk_range_set_value(long range, double value) { GTK_FFM.gtk_range_set_value(range, value); }
	/**
	 *  @param range cast=(GtkRange *)
	 *  @param slider_start cast=(gint *)
	 *  @param slider_end cast=(gint *)
	 */
	public static final void gtk_range_get_slider_range(long range, int[] slider_start, int[] slider_end) { GTK_FFM.gtk_range_get_slider_range(range, slider_start, slider_end); }

	/* GtkScrollable */
	/** @param scrollable cast=(GtkScrollable *) */
	public static final long gtk_scrollable_get_vadjustment(long scrollable) { return GTK_FFM.gtk_scrollable_get_vadjustment(scrollable); }

	/* GtkScrolledWindow */
	/** @param scrolled_window cast=(GtkScrolledWindow *) */
	public static final long gtk_scrolled_window_get_hadjustment(long scrolled_window) { return GTK_FFM.gtk_scrolled_window_get_hadjustment(scrolled_window); }
	/** @param scrolled_window cast=(GtkScrolledWindow *) */
	public static final long gtk_scrolled_window_get_hscrollbar(long scrolled_window) { return GTK_FFM.gtk_scrolled_window_get_hscrollbar(scrolled_window); }
	/**
	 * @param scrolled_window cast=(GtkScrolledWindow *)
	 * @param hscrollbar_policy cast=(GtkPolicyType *)
	 * @param vscrollbar_policy cast=(GtkPolicyType *)
	 */
	public static final void gtk_scrolled_window_get_policy(long scrolled_window, int[] hscrollbar_policy, int[] vscrollbar_policy) { GTK_FFM.gtk_scrolled_window_get_policy(scrolled_window, hscrollbar_policy, vscrollbar_policy); }
	/** @param scrolled_window cast=(GtkScrolledWindow *) */
	public static final long gtk_scrolled_window_get_vadjustment(long scrolled_window) { return GTK_FFM.gtk_scrolled_window_get_vadjustment(scrolled_window); }
	/** @param scrolled_window cast=(GtkScrolledWindow *) */
	public static final long gtk_scrolled_window_get_vscrollbar(long scrolled_window) { return GTK_FFM.gtk_scrolled_window_get_vscrollbar(scrolled_window); }
	/**
	 * @param scrolled_window cast=(GtkScrolledWindow *)
	 * @param hscrollbar_policy cast=(GtkPolicyType)
	 * @param vscrollbar_policy cast=(GtkPolicyType)
	 */
	public static final void gtk_scrolled_window_set_policy(long scrolled_window, int hscrollbar_policy, int vscrollbar_policy) { GTK_FFM.gtk_scrolled_window_set_policy(scrolled_window, hscrollbar_policy, vscrollbar_policy); }
	/** @param scrolled_window cast=(GtkScrolledWindow *) */
	public static final boolean gtk_scrolled_window_get_overlay_scrolling(long scrolled_window) { return GTK_FFM.gtk_scrolled_window_get_overlay_scrolling(scrolled_window); }
	/** @param scrolled_window cast=(GtkScrolledWindow *) */
	public static final void gtk_scrolled_window_set_overlay_scrolling(long scrolled_window, boolean overlay_scrolling) { GTK_FFM.gtk_scrolled_window_set_overlay_scrolling(scrolled_window, overlay_scrolling); }
	/**
	 * @param scrolled_window cast=(GtkScrolledWindow *)
	 * @param adjustment cast=(GtkAdjustment *)
	 *  */
	public static final void gtk_scrolled_window_set_vadjustment(long scrolled_window, long adjustment) { GTK_FFM.gtk_scrolled_window_set_vadjustment(scrolled_window, adjustment); }
	/**
	 * @param scrolled_window cast=(GtkScrolledWindow *)
	 * @param adjustment cast=(GtkAdjustment *)
	 *  */
	public static final void gtk_scrolled_window_set_hadjustment(long scrolled_window, long adjustment) { GTK_FFM.gtk_scrolled_window_set_hadjustment(scrolled_window, adjustment); }

	/* GtkSettings */
	public static final long gtk_settings_get_default() { return GTK_FFM.gtk_settings_get_default(); }

	/* GtkSpinButton */
	/** @param adjustment cast=(GtkAdjustment *) */
	public static final long gtk_spin_button_new(long adjustment, double climb_rate, int digits) { return GTK_FFM.gtk_spin_button_new(adjustment, climb_rate, digits); }
	/**
	 * @param spin_button cast=(GtkSpinButton*)
	 * @param numeric cast=(gboolean)
	 **/
	public static final void gtk_spin_button_set_numeric(long spin_button, boolean numeric) { GTK_FFM.gtk_spin_button_set_numeric(spin_button, numeric); }
	/**
	 * @param spin_button cast=(GtkSpinButton*)
	 * @param adjustment cast=(GtkAdjustment *)
	 **/
	public static final void gtk_spin_button_configure(long spin_button, long adjustment, double climb_rate, int digits) { GTK_FFM.gtk_spin_button_configure(spin_button, adjustment, climb_rate, digits); }
	/** @param spin_button cast=(GtkSpinButton*) */
	public static final long gtk_spin_button_get_adjustment(long spin_button) { return GTK_FFM.gtk_spin_button_get_adjustment(spin_button); }
	/** @param spin_button cast=(GtkSpinButton*) */
	public static final int gtk_spin_button_get_digits(long spin_button) { return GTK_FFM.gtk_spin_button_get_digits(spin_button); }
	/** @param spin_button cast=(GtkSpinButton*) */
	public static final void gtk_spin_button_set_increments(long spin_button, double step, double page) { GTK_FFM.gtk_spin_button_set_increments(spin_button, step, page); }
	/** @param spin_button cast=(GtkSpinButton*) */
	public static final void gtk_spin_button_set_range(long spin_button, double max, double min) { GTK_FFM.gtk_spin_button_set_range(spin_button, max, min); }
	/** @param spin_button cast=(GtkSpinButton*) */
	public static final void gtk_spin_button_set_value(long spin_button, double value) { GTK_FFM.gtk_spin_button_set_value(spin_button, value); }
	/** @param spin_button cast=(GtkSpinButton*) */
	public static final void gtk_spin_button_set_wrap(long spin_button, boolean wrap) { GTK_FFM.gtk_spin_button_set_wrap(spin_button, wrap); }
	/** @param spin_button cast=(GtkSpinButton*) */
	public static final void gtk_spin_button_update(long spin_button) { GTK_FFM.gtk_spin_button_update(spin_button); }

	/* GtkTextBuffer */
	/**
	 * @method flags=dynamic
	 * @param buffer cast=(GtkTextBuffer *)
	 */
	/* [GTK3/GTK4, GTK3 uses GtkClipboard but GTK4 uses GdkClipboard -- method signature otherwise identical] */
	public static final void gtk_text_buffer_copy_clipboard(long buffer, long clipboard) { GTK_FFM.gtk_text_buffer_copy_clipboard(buffer, clipboard); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param mark_name cast=(const gchar *)
	 * @param where cast=(GtkTextIter *)
	 * @param left_gravity cast=(gboolean)
	 */
	public static final long gtk_text_buffer_create_mark(long buffer, byte [] mark_name, byte [] where, boolean left_gravity) { return GTK_FFM.gtk_text_buffer_create_mark(buffer, mark_name, where, left_gravity); }
	/**
	 * @method flags=dynamic
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param default_editable cast=(gboolean)
	 */
	/* [GTK3/GTK4, GTK3 uses GtkClipboard but GTK4 uses GdkClipboard -- method signature otherwise identical] */
	public static final void gtk_text_buffer_cut_clipboard(long buffer, long clipboard, boolean default_editable) { GTK_FFM.gtk_text_buffer_cut_clipboard(buffer, clipboard, default_editable); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param start cast=(GtkTextIter *)
	 * @param end cast=(GtkTextIter *)
	 */
	public static final void gtk_text_buffer_delete(long buffer, byte[] start, byte[] end) { GTK_FFM.gtk_text_buffer_delete(buffer, start, end); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param start cast=(GtkTextIter *)
	 * @param end cast=(GtkTextIter *)
	 */
	public static final void gtk_text_buffer_get_bounds(long buffer, byte[] start, byte[] end) { GTK_FFM.gtk_text_buffer_get_bounds(buffer, start, end); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param iter cast=(GtkTextIter *)
	 */
	public static final void gtk_text_buffer_get_end_iter(long buffer, byte[] iter) { GTK_FFM.gtk_text_buffer_get_end_iter(buffer, iter); }
	/** @param buffer cast=(GtkTextBuffer *) */
	public static final long gtk_text_buffer_get_insert(long buffer) { return GTK_FFM.gtk_text_buffer_get_insert(buffer); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param iter cast=(GtkTextIter *)
	 * @param line_number cast=(gint)
	 */
	public static final void gtk_text_buffer_get_iter_at_line(long buffer, byte[] iter, int line_number) { GTK_FFM.gtk_text_buffer_get_iter_at_line(buffer, iter, line_number); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param iter cast=(GtkTextIter *)
	 * @param mark cast=(GtkTextMark *)
	 */
	public static final void gtk_text_buffer_get_iter_at_mark(long buffer, byte[] iter, long mark) { GTK_FFM.gtk_text_buffer_get_iter_at_mark(buffer, iter, mark); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param iter cast=(GtkTextIter *)
	 * @param char_offset cast=(gint)
	 */
	public static final void gtk_text_buffer_get_iter_at_offset(long buffer, byte[] iter, int char_offset) { GTK_FFM.gtk_text_buffer_get_iter_at_offset(buffer, iter, char_offset); }
	/** @param buffer cast=(GtkTextBuffer *) */
	public static final int gtk_text_buffer_get_line_count(long buffer) { return GTK_FFM.gtk_text_buffer_get_line_count(buffer); }
	/** @param buffer cast=(GtkTextBuffer *) */
	public static final long gtk_text_buffer_get_selection_bound(long buffer) { return GTK_FFM.gtk_text_buffer_get_selection_bound(buffer); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param start cast=(GtkTextIter *)
	 * @param end cast=(GtkTextIter *)
	 */
	public static final boolean gtk_text_buffer_get_selection_bounds(long buffer, byte[] start, byte[] end) { return GTK_FFM.gtk_text_buffer_get_selection_bounds(buffer, start, end); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param start cast=(GtkTextIter *)
	 * @param end cast=(GtkTextIter *)
	 * @param include_hidden_chars cast=(gboolean)
	 */
	public static final long gtk_text_buffer_get_text(long buffer, byte[] start, byte[] end, boolean include_hidden_chars) { return GTK_FFM.gtk_text_buffer_get_text(buffer, start, end, include_hidden_chars); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param iter cast=(GtkTextIter *)
	 * @param text cast=(const gchar *)
	 * @param len cast=(gint)
	 */
	public static final void gtk_text_buffer_insert(long buffer, byte[] iter, byte[] text, int len) { GTK_FFM.gtk_text_buffer_insert(buffer, iter, text, len); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param iter cast=(GtkTextIter *)
	 * @param text cast=(const gchar *)
	 * @param len cast=(gint)
	 */
	public static final void gtk_text_buffer_insert(long buffer, long iter, byte[] text, int len) { GTK_FFM.gtk_text_buffer_insert(buffer, iter, text, len); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param ins cast=(const GtkTextIter *)
	 * @param bound cast=(const GtkTextIter *)
	 */
	public static final void gtk_text_buffer_select_range(long buffer, byte[] ins, byte[] bound) { GTK_FFM.gtk_text_buffer_select_range(buffer, ins, bound); }
	/**
	 * @method flags=dynamic
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param override_location cast=(GtkTextIter *)
	 * @param default_editable cast=(gboolean)
	 */
	/* [GTK3/GTK4, GTK3 uses GtkClipboard but GTK4 uses GdkClipboard -- method signature otherwise identical] */
	public static final void gtk_text_buffer_paste_clipboard(long buffer, long clipboard, byte[] override_location, boolean default_editable) { GTK_FFM.gtk_text_buffer_paste_clipboard(buffer, clipboard, override_location, default_editable); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param where cast=(const GtkTextIter *)
	 */
	public static final void gtk_text_buffer_place_cursor(long buffer, byte[] where) { GTK_FFM.gtk_text_buffer_place_cursor(buffer, where); }
	/**
	 * @param buffer cast=(GtkTextBuffer *)
	 * @param text cast=(const gchar *)
	 * @param len cast=(gint)
	 */
	public static final void gtk_text_buffer_set_text(long buffer, byte[] text, int len) { GTK_FFM.gtk_text_buffer_set_text(buffer, text, len); }

	/* GtkTextIter */
	/** @param iter cast=(const GtkTextIter *) */
	public static final int gtk_text_iter_get_line(byte[] iter) { return GTK_FFM.gtk_text_iter_get_line(iter); }
	/** @param iter cast=(const GtkTextIter *) */
	public static final int gtk_text_iter_get_offset(byte[] iter) { return GTK_FFM.gtk_text_iter_get_offset(iter); }

	/* GtkTextView */
	public static final long gtk_text_view_new() { return GTK_FFM.gtk_text_view_new(); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param win cast=(GtkTextWindowType)
	 * @param buffer_x cast=(gint)
	 * @param buffer_y cast=(gint)
	 * @param window_x cast=(gint *)
	 * @param window_y cast=(gint *)
	 */
	public static final void gtk_text_view_buffer_to_window_coords(long text_view, int win, int buffer_x, int buffer_y, int[] window_x, int[] window_y) { GTK_FFM.gtk_text_view_buffer_to_window_coords(text_view, win, buffer_x, buffer_y, window_x, window_y); }
	/** @param text_view cast=(GtkTextView *) */
	public static final long gtk_text_view_get_buffer(long text_view) { return GTK_FFM.gtk_text_view_get_buffer(text_view); }
	/** @param text_view cast=(GtkTextView *) */
	public static final boolean gtk_text_view_get_editable(long text_view) { return GTK_FFM.gtk_text_view_get_editable(text_view); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param iter cast=(GtkTextIter *)
	 * @param x cast=(gint)
	 * @param y cast=(gint)
	 */
	public static final void gtk_text_view_get_iter_at_location(long text_view, byte[] iter, int x, int y) { GTK_FFM.gtk_text_view_get_iter_at_location(text_view, iter, x, y); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param iter cast=(const GtkTextIter *)
	 * @param location cast=(GdkRectangle *),flags=no_in
	 */
	public static final void gtk_text_view_get_iter_location(long text_view, byte[] iter, GdkRectangle location) { GTK_FFM.gtk_text_view_get_iter_location(text_view, iter, location); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param target_iter cast=(GtkTextIter *)
	 * @param y cast=(gint)
	 * @param line_top cast=(gint *)
	 */
	public static final void gtk_text_view_get_line_at_y(long text_view, byte[] target_iter, int y, int[] line_top) { GTK_FFM.gtk_text_view_get_line_at_y(text_view, target_iter, y, line_top); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param target_iter cast=(GtkTextIter *)
	 * @param y cast=(gint *)
	 * @param height cast=(gint *)
	 */
	public static final void gtk_text_view_get_line_yrange(long text_view, byte[] target_iter, int[] y, int[] height) { GTK_FFM.gtk_text_view_get_line_yrange(text_view, target_iter, y, height); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param visible_rect cast=(GdkRectangle *),flags=no_in
	 */
	public static final void gtk_text_view_get_visible_rect(long text_view, GdkRectangle visible_rect) { GTK_FFM.gtk_text_view_get_visible_rect(text_view, visible_rect); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param mark cast=(GtkTextMark *)
	 * @param within_margin cast=(gdouble)
	 * @param use_align cast=(gboolean)
	 * @param xalign cast=(gdouble)
	 * @param yalign cast=(gdouble)
	 */
	public static final void gtk_text_view_scroll_to_mark(long text_view, long mark, double within_margin, boolean use_align, double xalign, double yalign) { GTK_FFM.gtk_text_view_scroll_to_mark(text_view, mark, within_margin, use_align, xalign, yalign); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param iter cast=(GtkTextIter *)
	 * @param within_margin cast=(gdouble)
	 * @param use_align cast=(gboolean)
	 * @param xalign cast=(gdouble)
	 * @param yalign cast=(gdouble)
	 */
	public static final boolean gtk_text_view_scroll_to_iter(long text_view, byte[] iter, double within_margin, boolean use_align, double xalign, double yalign) { return GTK_FFM.gtk_text_view_scroll_to_iter(text_view, iter, within_margin, use_align, xalign, yalign); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param setting cast=(gboolean)
	 */
	public static final void gtk_text_view_set_editable(long text_view, boolean setting) { GTK_FFM.gtk_text_view_set_editable(text_view, setting); }
	/** @param text_view cast=(GtkTextView *) */
	public static final void gtk_text_view_set_justification(long text_view, int justification) { GTK_FFM.gtk_text_view_set_justification(text_view, justification); }
	/**
	 * @param text_view cast=(GtkTextView *)
	 * @param tabs cast=(PangoTabArray *)
	 */
	public static final void gtk_text_view_set_tabs(long text_view, long tabs) { GTK_FFM.gtk_text_view_set_tabs(text_view, tabs); }
	/** @param text_view cast=(GtkTextView *) */
	public static final void gtk_text_view_set_wrap_mode(long text_view, int wrap_mode) { GTK_FFM.gtk_text_view_set_wrap_mode(text_view, wrap_mode); }

	/* GtkToggleButton */
	public static final long gtk_toggle_button_new() { return GTK_FFM.gtk_toggle_button_new(); }
	/** @param toggle_button cast=(GtkToggleButton *) */
	public static final boolean gtk_toggle_button_get_active(long toggle_button) { return GTK_FFM.gtk_toggle_button_get_active(toggle_button); }
	/**
	 * @param toggle_button cast=(GtkToggleButton *)
	 * @param is_active cast=(gboolean)
	 */
	public static final void gtk_toggle_button_set_active(long toggle_button, boolean is_active) { GTK_FFM.gtk_toggle_button_set_active(toggle_button, is_active); }

	/* GtkToolTip */
	public static final long gtk_tooltip_get_type() { return GTK_FFM.gtk_tooltip_get_type(); }
	/**
	 * @param tooltip cast=(GtkTooltip *)
	 * @param custom_widget cast=(GtkWidget *)
	 */
	public static final void gtk_tooltip_set_custom(long tooltip, long custom_widget) { GTK_FFM.gtk_tooltip_set_custom(tooltip, custom_widget); }

	/* GtkTreeModel */
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_model_get(long tree_model, long iter, int column, long[] value, int terminator) { GTK_FFM.gtk_tree_model_get(tree_model, iter, column, value, terminator); }
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_model_get(long tree_model, long iter, int column, int[] value, int terminator) { GTK_FFM.gtk_tree_model_get(tree_model, iter, column, value, terminator); }
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param path cast=(GtkTreePath *)
	 */
	public static final boolean gtk_tree_model_get_iter(long tree_model, long iter, long path) { return GTK_FFM.gtk_tree_model_get_iter(tree_model, iter, path); }
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final boolean gtk_tree_model_get_iter_first(long tree_model, long iter) { return GTK_FFM.gtk_tree_model_get_iter_first(tree_model, iter); }
	/** @param tree_model cast=(GtkTreeModel *) */
	public static final int gtk_tree_model_get_n_columns(long tree_model) { return GTK_FFM.gtk_tree_model_get_n_columns(tree_model); }
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final long gtk_tree_model_get_path(long tree_model, long iter) { return GTK_FFM.gtk_tree_model_get_path(tree_model, iter); }
	public static final long gtk_tree_model_get_type() { return GTK_FFM.gtk_tree_model_get_type(); }
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param value cast=(GValue *)
	 */
	public static final void gtk_tree_model_get_value(long tree_model, long iter, int column, long value) { GTK_FFM.gtk_tree_model_get_value(tree_model, iter, column, value); }

	/**
	 * @param model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param parent cast=(GtkTreeIter *)
	 */
	public static final boolean gtk_tree_model_iter_children(long model, long iter, long parent) { return GTK_FFM.gtk_tree_model_iter_children(model, iter, parent); }
	/**
	 * @param model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final int gtk_tree_model_iter_n_children(long model, long iter) { return GTK_FFM.gtk_tree_model_iter_n_children(model, iter); }
	/**
	 * @param model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final boolean gtk_tree_model_iter_next(long model, long iter) { return GTK_FFM.gtk_tree_model_iter_next(model, iter); }
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param parent cast=(GtkTreeIter *)
	 */
	public static final boolean gtk_tree_model_iter_nth_child(long tree_model, long iter, long parent, int n) { return GTK_FFM.gtk_tree_model_iter_nth_child(tree_model, iter, parent, n); }
	/**
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param child cast=(GtkTreeIter *)
	 */
	public static final boolean gtk_tree_model_iter_parent(long tree_model, long iter, long child) { return GTK_FFM.gtk_tree_model_iter_parent(tree_model, iter, child); }

	/* GtkTreePath */
	/** @param path cast=(GtkTreePath *) */
	public static final void gtk_tree_path_append_index(long path, int index) { GTK_FFM.gtk_tree_path_append_index(path, index); }
	/**
	 * @param a cast=(const GtkTreePath *)
	 * @param b cast=(const GtkTreePath *)
	 */
	public static final long gtk_tree_path_compare(long a, long b) { return GTK_FFM.gtk_tree_path_compare(a, b); }
	/** @param path cast=(GtkTreePath *) */
	public static final void gtk_tree_path_free(long path) { GTK_FFM.gtk_tree_path_free(path); }
	/** @param path cast=(GtkTreePath *) */
	public static final int gtk_tree_path_get_depth(long path) { return GTK_FFM.gtk_tree_path_get_depth(path); }
	/** @param path cast=(GtkTreePath *) */
	public static final long gtk_tree_path_get_indices(long path) { return GTK_FFM.gtk_tree_path_get_indices(path); }
	public static final long gtk_tree_path_new() { return GTK_FFM.gtk_tree_path_new(); }
	/** @param path cast=(const gchar *) */
	public static final long gtk_tree_path_new_from_string(byte[] path) { return GTK_FFM.gtk_tree_path_new_from_string(path); }
	/** @param path cast=(const gchar *) */
	public static final long gtk_tree_path_new_from_string(long path) { return GTK_FFM.gtk_tree_path_new_from_string(path); }
	/** @param path cast=(GtkTreePath *) */
	public static final void gtk_tree_path_next(long path) { GTK_FFM.gtk_tree_path_next(path); }
	/** @param path cast=(GtkTreePath *) */
	public static final boolean gtk_tree_path_prev(long path) { return GTK_FFM.gtk_tree_path_prev(path); }
	/** @param path cast=(GtkTreePath *) */
	public static final boolean gtk_tree_path_up(long path) { return GTK_FFM.gtk_tree_path_up(path); }

	/* GtkTreeSelection */
	/** @param selection cast=(GtkTreeSelection *) */
	public static final int gtk_tree_selection_count_selected_rows(long selection) { return GTK_FFM.gtk_tree_selection_count_selected_rows(selection); }
	/**
	 * @param selection cast=(GtkTreeSelection *)
	 * @param model cast=(GtkTreeModel **)
	 */
	public static final long gtk_tree_selection_get_selected_rows(long selection, long [] model) { return GTK_FFM.gtk_tree_selection_get_selected_rows(selection, model); }
	/**
	 * @param selection cast=(GtkTreeSelection *)
	 * @param path cast=(GtkTreePath *)
	 */
	public static final boolean gtk_tree_selection_path_is_selected(long selection, long path) { return GTK_FFM.gtk_tree_selection_path_is_selected(selection, path); }
	/** @param selection cast=(GtkTreeSelection *) */
	public static final void gtk_tree_selection_select_all(long selection) { GTK_FFM.gtk_tree_selection_select_all(selection); }
	/**
	 * @param selection cast=(GtkTreeSelection *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_selection_select_iter(long selection, long iter) { GTK_FFM.gtk_tree_selection_select_iter(selection, iter); }
	/**
	 * @param selection cast=(GtkTreeSelection *)
	 * @param func cast=(GtkTreeSelectionFunc)
	 * @param data cast=(gpointer)
	 * @param destroy cast=(GDestroyNotify)
	 */
	public static final void gtk_tree_selection_set_select_function(long selection, long func, long data, long destroy) { GTK_FFM.gtk_tree_selection_set_select_function(selection, func, data, destroy); }
	/**
	 * @param selection cast=(GtkTreeSelection *)
	 * @param mode cast=(GtkSelectionMode)
	 */
	public static final void gtk_tree_selection_set_mode(long selection, int mode) { GTK_FFM.gtk_tree_selection_set_mode(selection, mode); }
	/**
	 * @param selection cast=(GtkTreeSelection *)
	 * @param path cast=(GtkTreePath *)
	 */
	public static final void gtk_tree_selection_unselect_path(long selection, long path) { GTK_FFM.gtk_tree_selection_unselect_path(selection, path); }
	/** @param selection cast=(GtkTreeSelection *) */
	public static final void gtk_tree_selection_unselect_all(long selection) { GTK_FFM.gtk_tree_selection_unselect_all(selection); }
	/**
	 * @param selection cast=(GtkTreeSelection *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_selection_unselect_iter(long selection, long iter) { GTK_FFM.gtk_tree_selection_unselect_iter(selection, iter); }

	/* GtkTreeStore */
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param parent cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_append(long store, long iter, long parent) { GTK_FFM.gtk_tree_store_append(store, iter, parent); }
	/** @param store cast=(GtkTreeStore *) */
	public static final void gtk_tree_store_clear(long store) { GTK_FFM.gtk_tree_store_clear(store); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param parent cast=(GtkTreeIter *)
	 * @param position cast=(gint)
	 */
	public static final void gtk_tree_store_insert(long store, long iter, long parent, int position) { GTK_FFM.gtk_tree_store_insert(store, iter, parent, position); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param parent cast=(GtkTreeIter *)
	 * @param sibling cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_insert_after(long store, long iter, long parent, long sibling) { GTK_FFM.gtk_tree_store_insert_after(store, iter, parent, sibling); }
	/** @param types cast=(GType *) */
	public static final long gtk_tree_store_newv(int numColumns, long [] types) { return GTK_FFM.gtk_tree_store_newv(numColumns, types); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param parent cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_prepend(long store, long iter, long parent) { GTK_FFM.gtk_tree_store_prepend(store, iter, parent); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_remove(long store, long iter) { GTK_FFM.gtk_tree_store_remove(store, iter); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_set(long store, long iter, int column, byte[] value, int terminator) { GTK_FFM.gtk_tree_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_set(long store, long iter, int column, int value, int terminator) { GTK_FFM.gtk_tree_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_set(long store, long iter, int column, long value, int terminator) { GTK_FFM.gtk_tree_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param value flags=no_out
	 */
	public static final void gtk_tree_store_set(long store, long iter, int column, GdkRGBA value, int terminator) { GTK_FFM.gtk_tree_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_store_set(long store, long iter, int column, boolean value, int terminator) { GTK_FFM.gtk_tree_store_set(store, iter, column, value, terminator); }
	/**
	 * @param store cast=(GtkTreeStore *)
	 * @param iter cast=(GtkTreeIter *)
	 * @param value cast=(GValue *)
	 */
	public static final void gtk_tree_store_set_value(long store, long iter, int column, long value) { GTK_FFM.gtk_tree_store_set_value(store, iter, column, value); }

	/* GtkTreeViewColumn */
	/**
	 * @param treeColumn cast=(GtkTreeViewColumn *)
	 * @param cellRenderer cast=(GtkCellRenderer *)
	 * @param attribute cast=(const gchar *)
	 * @param column cast=(gint)
	 */
	public static final void gtk_tree_view_column_add_attribute(long treeColumn, long cellRenderer, byte[] attribute, int column) { GTK_FFM.gtk_tree_view_column_add_attribute(treeColumn, cellRenderer, attribute, column); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param cell_renderer cast=(GtkCellRenderer *)
	 * @param start_pos cast=(gint *)
	 * @param width cast=(gint *)
	 */
	public static final boolean gtk_tree_view_column_cell_get_position(long tree_column, long cell_renderer, int[] start_pos, int[] width) { return GTK_FFM.gtk_tree_view_column_cell_get_position(tree_column, cell_renderer, start_pos, width); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param tree_model cast=(GtkTreeModel *)
	 * @param iter cast=(GtkTreeIter *)
	 */
	public static final void gtk_tree_view_column_cell_set_cell_data(long tree_column, long tree_model, long iter, boolean is_expander, boolean is_expanded) { GTK_FFM.gtk_tree_view_column_cell_set_cell_data(tree_column, tree_model, iter, is_expander, is_expanded); }
	/** @param tree_column cast=(GtkTreeViewColumn *) */
	public static final void gtk_tree_view_column_clear(long tree_column) { GTK_FFM.gtk_tree_view_column_clear(tree_column); }
	/** @param column cast=(GtkTreeViewColumn *) */
	public static final long gtk_tree_view_column_get_button(long column) { return GTK_FFM.gtk_tree_view_column_get_button(column); }
	/** @param column cast=(GtkTreeViewColumn *) */
	public static final int gtk_tree_view_column_get_fixed_width(long column) { return GTK_FFM.gtk_tree_view_column_get_fixed_width(column); }
	/** @param column cast=(GtkTreeViewColumn *) */
	public static final boolean gtk_tree_view_column_get_reorderable(long column) { return GTK_FFM.gtk_tree_view_column_get_reorderable(column); }
	/** @param column cast=(GtkTreeViewColumn *) */
	public static final boolean gtk_tree_view_column_get_resizable(long column) { return GTK_FFM.gtk_tree_view_column_get_resizable(column); }
	/** @param column cast=(GtkTreeViewColumn *) */
	public static final boolean gtk_tree_view_column_get_visible(long column) { return GTK_FFM.gtk_tree_view_column_get_visible(column); }
	/** @param column cast=(GtkTreeViewColumn *) */
	public static final int gtk_tree_view_column_get_width(long column) { return GTK_FFM.gtk_tree_view_column_get_width(column); }
	public static final long gtk_tree_view_column_new() { return GTK_FFM.gtk_tree_view_column_new(); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param cell_renderer cast=(GtkCellRenderer *)
	 * @param expand cast=(gboolean)
	 */
	public static final void gtk_tree_view_column_pack_start(long tree_column, long cell_renderer, boolean expand) { GTK_FFM.gtk_tree_view_column_pack_start(tree_column, cell_renderer, expand); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param cell_renderer cast=(GtkCellRenderer *)
	 * @param expand cast=(gboolean)
	 */
	public static final void gtk_tree_view_column_pack_end(long tree_column, long cell_renderer, boolean expand) { GTK_FFM.gtk_tree_view_column_pack_end(tree_column, cell_renderer, expand); }
	/** @param tree_column cast=(GtkTreeViewColumn *) */
	public static final void gtk_tree_view_column_set_alignment(long tree_column, float xalign) { GTK_FFM.gtk_tree_view_column_set_alignment(tree_column, xalign); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param cell_renderer cast=(GtkCellRenderer *)
	 * @param func cast=(GtkTreeCellDataFunc)
	 * @param func_data cast=(gpointer)
	 * @param destroy cast=(GDestroyNotify)
	 */
	public static final void gtk_tree_view_column_set_cell_data_func(long tree_column, long cell_renderer, long func, long func_data, long destroy) { GTK_FFM.gtk_tree_view_column_set_cell_data_func(tree_column, cell_renderer, func, func_data, destroy); }
	/**
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param clickable cast=(gboolean)
	 */
	public static final void gtk_tree_view_column_set_clickable(long column, boolean clickable) { GTK_FFM.gtk_tree_view_column_set_clickable(column, clickable); }
	/**
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param fixed_width cast=(gint)
	 */
	public static final void gtk_tree_view_column_set_fixed_width(long column, int fixed_width) { GTK_FFM.gtk_tree_view_column_set_fixed_width(column, fixed_width); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param min_width cast=(gint)
	 */
	public static final void gtk_tree_view_column_set_min_width(long tree_column, int min_width) { GTK_FFM.gtk_tree_view_column_set_min_width(tree_column, min_width); }
	/**
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param reorderable cast=(gboolean)
	 */
	public static final void gtk_tree_view_column_set_reorderable(long column, boolean reorderable) { GTK_FFM.gtk_tree_view_column_set_reorderable(column, reorderable); }
	/**
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param resizable cast=(gboolean)
	 */
	public static final void gtk_tree_view_column_set_resizable(long column, boolean resizable) { GTK_FFM.gtk_tree_view_column_set_resizable(column, resizable); }
	/**
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param type cast=(GtkTreeViewColumnSizing)
	 */
	public static final void gtk_tree_view_column_set_sizing(long column, int type) { GTK_FFM.gtk_tree_view_column_set_sizing(column, type); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param setting cast=(gboolean)
	 */
	public static final void gtk_tree_view_column_set_sort_indicator(long tree_column, boolean setting) { GTK_FFM.gtk_tree_view_column_set_sort_indicator(tree_column, setting); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param order cast=(GtkSortType)
	 */
	public static final void gtk_tree_view_column_set_sort_order(long tree_column, int order) { GTK_FFM.gtk_tree_view_column_set_sort_order(tree_column, order); }
	/** @param tree_column cast=(GtkTreeViewColumn *) */
	public static final void gtk_tree_view_column_set_visible(long tree_column, boolean visible) { GTK_FFM.gtk_tree_view_column_set_visible(tree_column, visible); }
	/**
	 * @param tree_column cast=(GtkTreeViewColumn *)
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_tree_view_column_set_widget(long tree_column, long widget) { GTK_FFM.gtk_tree_view_column_set_widget(tree_column, widget); }

	/* GtkTreeView */
	/**
	 * @param view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 */
	public static final long gtk_tree_view_create_row_drag_icon(long view, long path) { return GTK_FFM.gtk_tree_view_create_row_drag_icon(view, path); }
	/**
	 * @param view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 */
	public static final boolean gtk_tree_view_collapse_row(long view, long path) { return GTK_FFM.gtk_tree_view_collapse_row(view, path); }
	/**
	 * @param view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 */
	public static final void gtk_tree_view_set_drag_dest_row(long view, long path, int pos) { GTK_FFM.gtk_tree_view_set_drag_dest_row(view, path, pos); }
	/**
	 * @param view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 * @param open_all cast=(gboolean)
	 */
	public static final boolean gtk_tree_view_expand_row(long view, long path, boolean open_all) { return GTK_FFM.gtk_tree_view_expand_row(view, path, open_all); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param rect cast=(GdkRectangle *)
	 */
	public static final void gtk_tree_view_get_background_area(long tree_view, long path, long column, GdkRectangle rect) { GTK_FFM.gtk_tree_view_get_background_area(tree_view, path, column, rect); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param rect cast=(GdkRectangle *),flags=no_in
	 */
	public static final void gtk_tree_view_get_cell_area(long tree_view, long path, long column, GdkRectangle rect) { GTK_FFM.gtk_tree_view_get_cell_area(tree_view, path, column, rect); }
	/** @param tree_view cast=(GtkTreeView *) */
	public static final long gtk_tree_view_get_expander_column(long tree_view) { return GTK_FFM.gtk_tree_view_get_expander_column(tree_view); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param n cast=(gint)
	 */
	public static final long gtk_tree_view_get_column(long tree_view, int n) { return GTK_FFM.gtk_tree_view_get_column(tree_view, n); }
	/** @param tree_view cast=(GtkTreeView *) */
	public static final long gtk_tree_view_get_columns(long tree_view) { return GTK_FFM.gtk_tree_view_get_columns(tree_view); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath **)
	 * @param focus_column cast=(GtkTreeViewColumn **)
	 */
	public static final void gtk_tree_view_get_cursor(long tree_view, long [] path, long [] focus_column) { GTK_FFM.gtk_tree_view_get_cursor(tree_view, path, focus_column); }
	/** @param tree_view cast=(GtkTreeView *) */
	public static final boolean gtk_tree_view_get_headers_visible(long tree_view) { return GTK_FFM.gtk_tree_view_get_headers_visible(tree_view); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param x cast=(gint)
	 * @param y cast=(gint)
	 * @param path cast=(GtkTreePath **)
	 * @param column cast=(GtkTreeViewColumn **)
	 * @param cell_x cast=(gint *)
	 * @param cell_y cast=(gint *)
	 */
	public static final boolean gtk_tree_view_get_path_at_pos(long tree_view, int x, int y, long [] path, long [] column, int[] cell_x, int[] cell_y) { return GTK_FFM.gtk_tree_view_get_path_at_pos(tree_view, x, y, path, column, cell_x, cell_y); }
	/** @param tree_view cast=(GtkTreeView *) */
	public static final long gtk_tree_view_get_selection(long tree_view) { return GTK_FFM.gtk_tree_view_get_selection(tree_view); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param visible_rect flags=no_in
	 */
	public static final void gtk_tree_view_get_visible_rect(long tree_view, GdkRectangle visible_rect) { GTK_FFM.gtk_tree_view_get_visible_rect(tree_view, visible_rect); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param position cast=(gint)
	 */
	public static final int gtk_tree_view_insert_column(long tree_view, long column, int position) { return GTK_FFM.gtk_tree_view_insert_column(tree_view, column, position); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param base_column cast=(GtkTreeViewColumn *)
	 */
	public static final void gtk_tree_view_move_column_after(long tree_view, long column, long base_column) { GTK_FFM.gtk_tree_view_move_column_after(tree_view, column, base_column); }
	/** @param model cast=(GtkTreeModel *) */
	public static final long gtk_tree_view_new_with_model(long model) { return GTK_FFM.gtk_tree_view_new_with_model(model); }
	/** @param tree_view cast=(GtkTreeView *) */
	public static final void gtk_tree_view_columns_autosize(long tree_view) { GTK_FFM.gtk_tree_view_columns_autosize(tree_view); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param column cast=(GtkTreeViewColumn *)
	 */
	public static final void gtk_tree_view_remove_column(long tree_view, long column) { GTK_FFM.gtk_tree_view_remove_column(tree_view, column); }
	/**
	 * @param view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 */
	public static final boolean gtk_tree_view_row_expanded(long view, long path) { return GTK_FFM.gtk_tree_view_row_expanded(view, path); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 * @param column cast=(GtkTreeViewColumn *)
	 * @param use_align cast=(gboolean)
	 * @param row_aligh cast=(gfloat)
	 * @param column_align cast=(gfloat)
	 */
	public static final void gtk_tree_view_scroll_to_cell(long tree_view, long path, long column, boolean use_align, float row_aligh, float column_align) { GTK_FFM.gtk_tree_view_scroll_to_cell(tree_view, path, column, use_align, row_aligh, column_align); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param tree_x cast=(gint)
	 * @param tree_y cast=(gint)
	 */
	public static final void gtk_tree_view_scroll_to_point(long tree_view, int tree_x, int tree_y) { GTK_FFM.gtk_tree_view_scroll_to_point(tree_view, tree_x, tree_y); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param path cast=(GtkTreePath *)
	 * @param focus_column cast=(GtkTreeViewColumn *)
	 */
	public static final void gtk_tree_view_set_cursor(long tree_view, long path, long focus_column, boolean start_editing) { GTK_FFM.gtk_tree_view_set_cursor(tree_view, path, focus_column, start_editing); }
	/**
	 * @param tree_view cast=(GtkTreeView*)
	 * @param grid_lines cast=(GtkTreeViewGridLines)
	 */
	public static final void gtk_tree_view_set_grid_lines(long tree_view, int grid_lines) { GTK_FFM.gtk_tree_view_set_grid_lines(tree_view, grid_lines); }
	/** @param tree_view cast=(GtkTreeView*) */
	public static final int gtk_tree_view_get_grid_lines(long tree_view) { return GTK_FFM.gtk_tree_view_get_grid_lines(tree_view); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param visible cast=(gboolean)
	 */
	public static final void gtk_tree_view_set_headers_visible(long tree_view, boolean visible) { GTK_FFM.gtk_tree_view_set_headers_visible(tree_view, visible); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param model cast=(GtkTreeModel *)
	 */
	public static final void gtk_tree_view_set_model(long tree_view, long model) { GTK_FFM.gtk_tree_view_set_model(tree_view, model); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param column cast=(gint)
	 */
	public static final void gtk_tree_view_set_search_column(long tree_view, int column) { GTK_FFM.gtk_tree_view_set_search_column(tree_view, column); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param bx cast=(gint)
	 * @param by cast=(gint)
	 * @param tx cast=(gint *)
	 * @param ty cast=(gint *)
	 */
	public static final void gtk_tree_view_convert_bin_window_to_tree_coords(long tree_view, int bx, int by, int[] tx, int[] ty) { GTK_FFM.gtk_tree_view_convert_bin_window_to_tree_coords(tree_view, bx, by, tx, ty); }
	/**
	 * @param tree_view cast=(GtkTreeView *)
	 * @param wx cast=(int *)
	 * @param wy cast=(int *)
	 */
	public static final void gtk_tree_view_convert_bin_window_to_widget_coords(long tree_view, int bx, int by, int[]wx, int[] wy) { GTK_FFM.gtk_tree_view_convert_bin_window_to_widget_coords(tree_view, bx, by, wx, wy); }

	/* GtkWidget */
	/** @param widget cast=(GtkWidget *) */
	public static final int gtk_widget_get_scale_factor(long widget) { return GTK_FFM.gtk_widget_get_scale_factor(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_name(long widget) { return GTK_FFM.gtk_widget_get_name(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget_class cast=(GtkWidgetClass *)
	 */
	public static final long gtk_widget_class_get_css_name(long widget_class) { return GTK_FFM.gtk_widget_class_get_css_name(widget_class); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param minimum_size cast=(GtkRequisition *)
	 * @param natural_size cast=(GtkRequisition *)
	 */
	public static final void gtk_widget_get_preferred_size(long widget, GtkRequisition minimum_size, GtkRequisition natural_size) { GTK_FFM.gtk_widget_get_preferred_size(widget, minimum_size, natural_size); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_unparent(long widget) { GTK_FFM.gtk_widget_unparent(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 * @param parent cast=(GtkWidget *)
	 */
	public static final void gtk_widget_set_parent(long widget, long parent) { GTK_FFM.gtk_widget_set_parent(widget, parent); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param expand cast=(gboolean)
	 */
	public static final void gtk_widget_set_hexpand(long widget, boolean expand) { GTK_FFM.gtk_widget_set_hexpand(widget, expand); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param expand cast=(gboolean)
	 */
	public static final void gtk_widget_set_vexpand(long widget, boolean expand) { GTK_FFM.gtk_widget_set_vexpand(widget, expand); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param gtk_align cast=(GtkAlign)
	 */
	public static final void gtk_widget_set_halign(long widget, int gtk_align) { GTK_FFM.gtk_widget_set_halign(widget, gtk_align); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param gtk_align cast=(GtkAlign)
	 */
	public static final void gtk_widget_set_valign(long widget, int gtk_align) { GTK_FFM.gtk_widget_set_valign(widget, gtk_align); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 * @param margin cast=(gint)
	 */
	public static final void gtk_widget_set_margin_start(long widget, int margin) { GTK_FFM.gtk_widget_set_margin_start(widget, margin); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 * @param margin cast=(gint)
	 */
	public static final void gtk_widget_set_margin_end(long widget, int margin) { GTK_FFM.gtk_widget_set_margin_end(widget, margin); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 * @param margin cast=(gint)
	 */
	public static final void gtk_widget_set_margin_top(long widget, int margin) { GTK_FFM.gtk_widget_set_margin_top(widget, margin); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 * @param margin cast=(gint)
	 */
	public static final void gtk_widget_set_margin_bottom(long widget, int margin) { GTK_FFM.gtk_widget_set_margin_bottom(widget, margin); }
	/** @param self cast=(GtkWidget *) */
	public static final int gtk_widget_get_state_flags(long self) { return GTK_FFM.gtk_widget_get_state_flags(self); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param flags cast=(GtkStateFlags)
	 */
	public static final void gtk_widget_unset_state_flags(long widget, int flags) { GTK_FFM.gtk_widget_unset_state_flags(widget, flags); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param flags cast=(GtkStateFlags)
	 */
	public static final void gtk_widget_set_state_flags(long widget, int flags, boolean clear) { GTK_FFM.gtk_widget_set_state_flags(widget, flags, clear); }
	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_has_default(long widget) { return GTK_FFM.gtk_widget_has_default(widget); }

	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_get_sensitive(long widget) { return GTK_FFM.gtk_widget_get_sensitive(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 * @param css_class cast=(const char *)
	 * */
	public static final void gtk_widget_add_css_class(long widget, byte[] css_class) { GTK_FFM.gtk_widget_add_css_class(widget, css_class); }
	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_child_focus(long widget, int direction) { return GTK_FFM.gtk_widget_child_focus(widget, direction); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param text cast=(const gchar *)
	 */
	public static final long gtk_widget_create_pango_layout(long widget, byte[] text) { return GTK_FFM.gtk_widget_create_pango_layout(widget, text); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param text cast=(const gchar *)
	 */
	public static final long gtk_widget_create_pango_layout(long widget, long text) { return GTK_FFM.gtk_widget_create_pango_layout(widget, text); }
	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_get_visible(long widget) { return GTK_FFM.gtk_widget_get_visible(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_get_realized(long widget) { return GTK_FFM.gtk_widget_get_realized(widget); }

	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_get_child_visible(long widget) { return GTK_FFM.gtk_widget_get_child_visible(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final int gtk_widget_get_margin_start(long widget) { return GTK_FFM.gtk_widget_get_margin_start(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final int gtk_widget_get_margin_end(long widget) { return GTK_FFM.gtk_widget_get_margin_end(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final int gtk_widget_get_margin_top(long widget) { return GTK_FFM.gtk_widget_get_margin_top(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final int gtk_widget_get_margin_bottom(long widget) { return GTK_FFM.gtk_widget_get_margin_bottom(widget); }
	/** @param widget cast=(GtkWidget *)  */
	public static final boolean gtk_widget_get_mapped(long widget) { return GTK_FFM.gtk_widget_get_mapped(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_pango_context(long widget) { return GTK_FFM.gtk_widget_get_pango_context(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_parent(long widget) { return GTK_FFM.gtk_widget_get_parent(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final long gtk_widget_get_parent_window(long widget) { return GTK_FFM.gtk_widget_get_parent_window(widget); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final long gtk_widget_get_parent_surface(long widget) { return GTK_FFM.gtk_widget_get_parent_surface(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param allocation cast=(GtkAllocation *),flags=no_in
	 * */
	public static final void gtk_widget_get_allocation(long widget, GtkAllocation allocation) { GTK_FFM.gtk_widget_get_allocation(widget, allocation); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param group_cycling cast=(gboolean)
	 */
	public static final boolean gtk_widget_mnemonic_activate(long widget, boolean group_cycling) { return GTK_FFM.gtk_widget_mnemonic_activate(widget, group_cycling); }
	/**
	 * @param widget cast=(GtkWidget *)
	 */
	public static final long gtk_widget_get_style_context(long widget) { return GTK_FFM.gtk_widget_get_style_context(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param width cast=(gint *)
	 * @param height cast=(gint *)
	 */
	public static final void gtk_widget_get_size_request(long widget, int [] width, int [] height) { GTK_FFM.gtk_widget_get_size_request(widget, width, height); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_grab_focus(long widget) { GTK_FFM.gtk_widget_grab_focus(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_has_focus(long widget) { return GTK_FFM.gtk_widget_has_focus(widget); }

	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_is_focus(long widget) { return GTK_FFM.gtk_widget_is_focus(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_queue_resize(long widget) { GTK_FFM.gtk_widget_queue_resize(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_realize(long widget) { GTK_FFM.gtk_widget_realize(widget); }
	/** @param dir cast=(GtkTextDirection) */
	public static final void gtk_widget_set_default_direction(int dir) { GTK_FFM.gtk_widget_set_default_direction(dir); }
	/** @param widget cast=(GtkWidget *) */
	public static final void gtk_widget_queue_draw(long widget) { GTK_FFM.gtk_widget_queue_draw(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param can_focus cast=(gboolean)
	 */
	public static final void gtk_widget_set_can_focus(long widget, boolean can_focus) { GTK_FFM.gtk_widget_set_can_focus(widget, can_focus); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param visible cast=(gboolean)
	 */
	public static final void gtk_widget_set_visible(long widget, boolean visible) { GTK_FFM.gtk_widget_set_visible(widget, visible); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param dir cast=(GtkTextDirection)
	 */
	public static final void gtk_widget_set_direction(long widget, int dir) { GTK_FFM.gtk_widget_set_direction(widget, dir); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param receives_default cast=(gboolean)
	 */
	public static final void gtk_widget_set_receives_default(long widget, boolean receives_default) { GTK_FFM.gtk_widget_set_receives_default(widget, receives_default); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 * @param val cast=(gboolean)
	 */
	public static final void gtk_widget_set_focus_on_click(long widget, boolean val) { GTK_FFM.gtk_widget_set_focus_on_click(widget, val); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final void gtk_widget_set_opacity(long widget, double opacity) { GTK_FFM.gtk_widget_set_opacity(widget, opacity); }
	/**
	 * @method flags=dynamic
	 * @param widget cast=(GtkWidget *)
	 */
	public static final double gtk_widget_get_opacity(long widget) { return GTK_FFM.gtk_widget_get_opacity(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param sensitive cast=(gboolean)
	 */
	public static final void gtk_widget_set_sensitive(long widget, boolean sensitive) { GTK_FFM.gtk_widget_set_sensitive(widget, sensitive); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param width cast=(gint)
	 * @param height cast=(gint)
	 */
	public static final void gtk_widget_set_size_request(long widget, int width, int height) { GTK_FFM.gtk_widget_set_size_request(widget, width, height); }

	/** @param widget cast=(GtkWidget *) */
	public static final boolean gtk_widget_activate(long widget) { return GTK_FFM.gtk_widget_activate(widget); }
	/** @param widget cast=(GtkWidget *) */
	public static final long gtk_widget_get_tooltip_text(long widget) { return GTK_FFM.gtk_widget_get_tooltip_text(widget); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param tip_text cast=(const gchar *)
	 */
	public static final void gtk_widget_set_tooltip_text(long widget, byte[] tip_text) { GTK_FFM.gtk_widget_set_tooltip_text(widget, tip_text); }
	/**
	 * @param widget cast=(GtkWidget *)
	 * @param name cast=(const char *)
	 * @param group cast=(GActionGroup *)
	 */
	public static final void gtk_widget_insert_action_group(long widget, byte[] name, long group) { GTK_FFM.gtk_widget_insert_action_group(widget, name, group); }

	/* GtkWindow */
	/** @param window cast=(GtkWindow *) */
	public static final long gtk_window_get_focus(long window) { return GTK_FFM.gtk_window_get_focus(window); }
	/**
	 * @param window cast=(GtkWindow *)
	 */
	public static final long gtk_window_get_group(long window) { return GTK_FFM.gtk_window_get_group(window); }
	/** @param window cast=(GtkWindow *) */
	public static final boolean gtk_window_get_modal(long window) { return GTK_FFM.gtk_window_get_modal(window); }
	/**
	 * @param group cast=(GtkWindowGroup*)
	 * @param window cast=(GtkWindow*)
	 */
	public static final void gtk_window_group_add_window(long group, long window) { GTK_FFM.gtk_window_group_add_window(group, window); }
	/**
	 * @param group cast=(GtkWindowGroup*)
	 * @param window cast=(GtkWindow*)
	 */
	public static final void gtk_window_group_remove_window(long group, long window) { GTK_FFM.gtk_window_group_remove_window(group, window); }
	public static final long gtk_window_group_new() { return GTK_FFM.gtk_window_group_new(); }
	/** @param handle cast=(GtkWindow *) */
	public static final boolean gtk_window_is_active(long handle) { return GTK_FFM.gtk_window_is_active(handle); }
	public static final long gtk_window_list_toplevels() { return GTK_FFM.gtk_window_list_toplevels(); }
	/** @param handle cast=(GtkWindow *) */
	public static final void gtk_window_maximize(long handle) { GTK_FFM.gtk_window_maximize(handle); }
	/** @param handle cast=(GtkWindow *) */
	public static final void gtk_window_fullscreen(long handle) { GTK_FFM.gtk_window_fullscreen(handle); }
	/** @param handle cast=(GtkWindow *) */
	public static final void gtk_window_unfullscreen(long handle) { GTK_FFM.gtk_window_unfullscreen(handle); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param decorated cast=(gboolean)
	 */
	public static final void gtk_window_set_decorated(long window, boolean decorated) { GTK_FFM.gtk_window_set_decorated(window, decorated); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param setting cast=(gboolean)
	 */
	public static final void gtk_window_set_destroy_with_parent(long window, boolean setting) { GTK_FFM.gtk_window_set_destroy_with_parent(window, setting); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param modal cast=(gboolean)
	 */
	public static final void gtk_window_set_modal(long window, boolean modal) { GTK_FFM.gtk_window_set_modal(window, modal); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param resizable cast=(gboolean)
	 */
	public static final void gtk_window_set_resizable(long window, boolean resizable) { GTK_FFM.gtk_window_set_resizable(window, resizable); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param title cast=(const gchar *)
	 */
	public static final void gtk_window_set_title(long window, byte[] title) { GTK_FFM.gtk_window_set_title(window, title); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param parent cast=(GtkWindow *)
	 */
	public static final void gtk_window_set_transient_for(long window, long parent) { GTK_FFM.gtk_window_set_transient_for(window, parent); }
	/** @param handle cast=(GtkWindow *) */
	public static final void gtk_window_unmaximize(long handle) { GTK_FFM.gtk_window_unmaximize(handle); }
	/** @param window cast=(GtkWindow *) */
	public static final long gtk_window_get_default_widget(long window) { return GTK_FFM.gtk_window_get_default_widget(window); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param width cast=(gint)
	 * @param height cast=(gint)
	 */
	public static final void gtk_window_set_default_size(long window, int width, int height) { GTK_FFM.gtk_window_set_default_size(window, width, height); }
	/**
	 * @param window cast=(GtkWindow *)
	 * @param width cast=(gint *)
	 * @param height cast=(gint *)
	 */
	public static final void gtk_window_get_default_size(long window, int[] width, int[] height) { GTK_FFM.gtk_window_get_default_size(window, width, height); }

	/* GtkPlug */
	public static final long gtk_plug_new(long socket_id) { return GTK_FFM.gtk_plug_new(socket_id); }

	/* GtkPrinterOption */
	/** @method flags=dynamic */
	public static final long gtk_printer_option_widget_get_type() { return GTK_FFM.gtk_printer_option_widget_get_type(); }

	/* GtkSocket */
	public static final long gtk_socket_new() { return GTK_FFM.gtk_socket_new(); }
	/** @param socket cast=(GtkSocket *) */
	public static final long gtk_socket_get_id(long socket) { return GTK_FFM.gtk_socket_get_id(socket); }
}
