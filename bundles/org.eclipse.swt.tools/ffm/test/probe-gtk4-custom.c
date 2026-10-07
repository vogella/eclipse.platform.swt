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
/* Prints the GTK4 struct layouts that FFMGtk4 hard codes, for FFMGtk4CustomCheck to validate. */
#include <gtk/gtk.h>
#include <stddef.h>
#define S(t) printf(#t "_SIZEOF=%zu\n", sizeof(t))
#define O(t, f) printf(#t "_" #f "_OFFSET=%zu\n", offsetof(t, f))
int main(void) {
	S(GObject); S(GObjectClass); S(GtkWidget); S(GtkWidgetClass); S(GTypeInfo); S(GInterfaceInfo);
	S(GdkPaintableInterface); S(GValue); S(GPollFD); S(GtkTextIter); S(GtkTreeIter);
	S(GtkCellRendererText); S(GtkCellRendererTextClass); S(GtkAllocation); S(GtkRequisition);
	O(GObjectClass, constructor); O(GObjectClass, set_property); O(GObjectClass, get_property); O(GObjectClass, dispose); O(GObjectClass, finalize);
	O(GtkWidgetClass, map); O(GtkWidgetClass, size_allocate); O(GtkWidgetClass, measure);
	O(GdkPaintableInterface, snapshot); O(GdkPaintableInterface, get_current_image); O(GdkPaintableInterface, get_flags);
	O(GdkPaintableInterface, get_intrinsic_width); O(GdkPaintableInterface, get_intrinsic_height); O(GdkPaintableInterface, get_intrinsic_aspect_ratio);
	printf("GDK_PAINTABLE_STATIC_SIZE=%d\nGDK_PAINTABLE_STATIC_CONTENTS=%d\n", GDK_PAINTABLE_STATIC_SIZE, GDK_PAINTABLE_STATIC_CONTENTS);
	return 0;
}
