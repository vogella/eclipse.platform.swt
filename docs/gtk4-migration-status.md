# GTK4 Migration Status

This document tracks areas in the Eclipse SWT codebase that still need updates to work correctly with GTK4. GTK4 support in SWT is currently experimental and under active development.

## Overview

GTK4 introduces significant API changes and deprecations compared to GTK3. This document catalogs all identified areas requiring updates, organized by category and priority.

**Summary Statistics:**
- Total GTK4 TODO comments: 61
- Affected source files: 30
- Main categories: 8

## Table of Contents

1. [Surface and Window Management](#1-surface-and-window-management)
2. [Event Handling](#2-event-handling)
3. [Widget Hierarchy and Children Access](#3-widget-hierarchy-and-children-access)
4. [Removed/Deprecated Functions](#4-removeddeprecated-functions)
5. [Missing Implementations](#5-missing-implementations)
6. [Accessibility](#6-accessibility)
7. [Graphics and Drawing](#7-graphics-and-drawing)
8. [Drag and Drop](#8-drag-and-drop)

---

## 1. Surface and Window Management

GTK4 changed the coordinate system to be surface-relative and removed several window management functions.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| Shell | Shell.java | 1275, 3370 | GtkWindow no longer has the ability to get position | High |
| Shell | Shell.java | 3376 | Coordinate system is now surface relative, cannot obtain absolute position | High |
| Shell | Shell.java | 1749 | gdk_window_move/gdk_window_resize no longer exist, need gdk_toplevel_begin_move/resize | High |
| Shell | Shell.java | 1942, 1944, 1972 | Cannot specify window management hints (decorations, functions) | Medium |
| Display | Display.java | 4183 | No longer able to retrieve surface/window origin | High |
| Menu | Menu.java | 1348 | gdk_surface_resize/move no longer exist, need GdkToplevel alternatives | Medium |
| AccessibleObject | AccessibleObject.java | 4533, 4551 | No gdk_surface_get_origin | Medium |
| DropTarget | DropTarget.java | 790 | No gdk_surface_get_origin | Medium |

### GTK4 API Changes:
- `gdk_window_*` → `gdk_surface_*` (rename)
- `gdk_surface_get_origin()` → removed (no direct replacement)
- `gdk_window_move()` → `gdk_toplevel_begin_move()`
- `gdk_window_resize()` → `gdk_toplevel_begin_resize()`
- Window management hints → No longer supported

### Required Actions:
1. Implement surface position tracking mechanism
2. Migrate to GdkToplevel API for window operations
3. Find alternatives for window management hints
4. Update coordinate transformation logic

---

## 2. Event Handling

GTK4 introduces a new event controller model, deprecating many GTK3 event signals.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| Widget | Widget.java | 1692 | No access to key event string | High |
| Widget | Widget.java | 2176 | gdk_keymap_translate_keyboard_state no longer exists | High |
| Text | Text.java | 1857 | No access to key event string | High |
| Combo | Combo.java | 929 | event-after signal handling | Medium |
| Control | Control.java | 420 | event-after signal handling | Medium |
| DateTime | DateTime.java | 987 | focus-in event handling | Medium |
| ScrollBar | ScrollBar.java | 584 | change-value moved to gtk_scroll_child, event-after | Medium |
| TableColumn | TableColumn.java | 441 | event-after signal handling | Medium |
| TreeColumn | TreeColumn.java | 435 | event-after, size-allocate signals | Medium |
| Shell | Shell.java | 964 | Need to verify required signals, may need legacy event controller | Medium |
| Table | Table.java | 2124 | Need to replicate gtk_button_press_event functions | Medium |
| Bug543984 | Bug543984_GTK4EventTypeConstants.java | 58 | GDK_2BUTTON_PRESS removed, no signal for double click | High |

### GTK4 API Changes:
- `event-after` signal → Removed
- `GDK_2BUTTON_PRESS` → Removed (use click count in GtkGestureClick)
- `gdk_keymap_translate_keyboard_state()` → `gdk_display_map_keycode()`
- Key event string access → Changed API
- Legacy signals → Event controllers (GtkEventController*)

### Required Actions:
1. Migrate to GtkEventController API
2. Implement GtkGestureClick for multi-click detection
3. Update key event handling with new GDK API
4. Assess which signals need legacy event controller
5. Update focus event handling

---

## 3. Widget Hierarchy and Children Access

GTK4 changed how widget parent-child relationships are managed and accessed.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| Composite | Composite.java | 676 | Parent does not hold list of children (parent-child relationship changed) | High |
| Composite | Composite.java | 1489 | gtk_container_propagate_draw removed, possibly not required | Medium |
| Combo | Combo.java | 1943 | No access to children of the surface | Medium |
| Combo | Combo.java | 546 | popupHandle not mapped in GTK4 | Medium |
| Spinner | Spinner.java | 869 | No access to children of surface, class hierarchy may change | Medium |
| Control | Control.java | 4726 | No ability to invalidate surfaces, may need tracking | Medium |
| Scrollable | Scrollable.java | 546 | No ability to invalidate surfaces, may need tracking | Medium |
| Table | Table.java | 3706 | No gtk_widget_set_parent_surface | Low |
| Tree | Tree.java | 3874 | No gtk_widget_set_parent_surface | Low |

### GTK4 API Changes:
- `GtkContainer` → Removed (widgets directly manage children)
- `gtk_container_get_children()` → Various widget-specific accessors
- `gtk_container_propagate_draw()` → Removed (automatic propagation)
- `gtk_widget_set_parent_surface()` → Removed
- Surface invalidation → Automatic, no manual control

### Required Actions:
1. Update child widget access methods
2. Remove or replace gtk_container_propagate_draw calls
3. Implement alternative for surface invalidation tracking
4. Test widget hierarchy management
5. Update parent-child relationship handling

---

## 4. Removed/Deprecated Functions

Functions completely removed or deprecated in GTK4.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| GDK | GDK.java | 428, 438, 559 | Multiple GDK functions removed | High |
| List | List.java | 1105 | Function removed in GTK4 | Medium |
| Text | Text.java | 2092 | Function removed in GTK4 (bug 561444) | Medium |
| Table | Table.java | 2533 | Function removed in GTK4 | Medium |
| Tree | Tree.java | 2804 | Function removed in GTK4 | Medium |
| Display | Display.java | 5300 | gdk_device_warp removed | Medium |
| MenuItem | MenuItem.java | 1031 | Menu images with text no longer supported | Low |
| Combo | Combo.java | N/A | Function removed in GTK4 commit fdc0c642 (2016-11-11) | Medium |

### Specific Function Removals:
- `gdk_device_warp()` → No direct replacement
- Various container functions → Widget-specific replacors
- Menu icon/image API changes
- GdkScreen functions → GdkDisplay/GdkMonitor

### Required Actions:
1. Document each removed function
2. Research GTK4 replacements
3. Implement alternatives where available
4. Mark unsupported features appropriately

---

## 5. Missing Implementations

Features that require custom implementations in GTK4.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| ToolBar | ToolBar.java | 550 | No more GtkToolbar, must use generic GtkBox | High |
| ToolBar | ToolBar.java | 617 | Must implement custom toolbar functionality | High |
| ToolItem | ToolItem.java | 674, 1666 | Must implement custom overflow menu | Medium |
| ToolTip | ToolTip.java | 299 | Must implement ToolTips for GTK4 | Medium |
| Tracker | Tracker.java | 908 | Tracker implementation for GTK4 | High |
| Shell | Shell.java | 754 | Need to handle GTK_WINDOW_POPUP type | High |
| Shell | Shell.java | 769 | Need case for SWT.MIN | Medium |
| Display | Display.java | 6144 | May need for minimum size, signal remains connected | Low |
| FileDialog | FileDialog.java | 633 | Missing property for file chooser | Low |
| Clipboard | Clipboard.java | 387 | Other clipboard cases | Medium |

### GTK4 API Changes:
- `GtkToolbar` → Removed (use GtkBox with custom implementation)
- `GtkTooltip` → API changed
- `GDK_WINDOW_POPUP` → Different window types system
- Overflow menus → Must implement manually

### Required Actions:
1. Implement custom toolbar using GtkBox
2. Implement custom overflow menu system
3. Port tooltip implementation to GTK4 API
4. Implement tracker functionality
5. Handle new window type system
6. Complete clipboard implementation

---

## 6. Accessibility

Accessibility features needing GTK4 updates.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| AccessibleObject | AccessibleObject.java | Various | Multiple accessibility features disabled for GTK4 | High |
| AccessibleObject | AccessibleObject.java | Multiple | "TODO investigate proper way for GTK 4.x" (6 occurrences) | High |
| AccessibleObject | AccessibleObject.java | Multiple | "TODO reenable for GTK 4.x" (1 occurrence) | High |
| Accessible | Accessible.java | N/A | GTK4 conditional checks present | Medium |

### ATK/Accessibility Changes:
- Several ATK functions need investigation for GTK4
- Surface origin issues affect accessibility coordinate calculations
- Some accessibility features currently disabled in GTK4 mode

### Required Actions:
1. Investigate proper GTK4 accessibility implementation
2. Re-enable disabled accessibility features
3. Test accessibility with GTK4
4. Update coordinate calculations for accessibility

---

## 7. Graphics and Drawing

Graphics context and drawing issues in GTK4.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| GC | GC.java | 580, 604, 618, 632 | No ability to invalidate surfaces, may need tracking (4 occurrences) | Medium |
| Control | Control.java | 191 | Check for bug 547466 once Eclipse runs on GTK4 | Low |

### GTK4 API Changes:
- Surface invalidation is automatic
- Cairo integration changes
- Snapshot rendering model

### Required Actions:
1. Implement surface invalidation tracking if needed
2. Test drawing operations on GTK4
3. Verify bug 547466 status on GTK4
4. Update to snapshot rendering model where necessary

---

## 8. Drag and Drop

DnD functionality updates needed for GTK4.

### Issues:

| Component | File | Line | Issue | Priority |
|-----------|------|------|-------|----------|
| Clipboard | Clipboard.java | 387 | Other clipboard cases for GTK4 | Medium |
| DragSource | DragSource.java | 452 | Ungrab keyboard seat if different from pointer's seat | Medium |
| DropTarget | DropTarget.java | 790 | No gdk_surface_get_origin | Medium |

### GTK4 API Changes:
- DnD API significantly redesigned
- GdkDrag and GdkDrop objects
- Seat-based input handling

### Required Actions:
1. Complete clipboard implementation
2. Handle keyboard/pointer seat management
3. Update coordinate calculations for DnD
4. Test DnD operations on GTK4

---

## Priority Definitions

- **High**: Blocking issues that prevent core functionality from working
- **Medium**: Issues that affect important features but have workarounds
- **Low**: Minor issues or features with limited impact

---

## Testing and Validation

### Test Coverage Needed:
1. Widget creation and hierarchy
2. Event handling (keyboard, mouse, focus)
3. Window management and positioning
4. Drawing and graphics operations
5. Accessibility features
6. Drag and drop operations
7. Menus and toolbars
8. Clipboard operations

### Known Test Issues:
- Bug543984_GTK4EventTypeConstants.java: Documents double-click detection issue

---

## Related Documentation

- [GTK3 to GTK4 Migration Guide](https://docs.gtk.org/gtk4/migrating-3to4.html)
- [GTK4 API Documentation](https://docs.gtk.org/gtk4/)
- [SWT GTK Development Guide](gtk-dev-guide.md)

---

## How to Contribute

When working on GTK4 migration:

1. Review this document to understand the scope of work
2. Choose an area to work on (start with High priority items)
3. Check both GTK3 and GTK4 documentation for API changes
4. Implement changes with appropriate GTK version checks
5. Test on both GTK3 and GTK4 (if possible)
6. Update or remove TODO comments in the code
7. Update this document to reflect completed work

### Code Patterns:

```java
// Version check pattern
if (GTK.GTK4) {
    // GTK4 implementation
} else {
    // GTK3 implementation
}

// Dynamic function pattern in OS.java
/** @method flags=dynamic */
public static final native void gtk_function_name();
```

---

## Recent Changes

- **Initial Document**: Created comprehensive tracking of GTK4 migration status

---

## Contributors

This document is maintained by the Eclipse SWT team. Contributions and updates are welcome.

For questions or discussions, contact: platform-swt-dev@eclipse.org
