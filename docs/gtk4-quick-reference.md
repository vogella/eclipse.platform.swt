# GTK4 Quick Reference for SWT Developers

This document provides quick answers to common GTK4 migration questions and references to detailed information.

## Quick Stats

- **Total GTK4 TODOs**: 61
- **Affected Files**: 30
- **Critical Issues**: ~15-20
- **Status**: GTK4 support is experimental

## Top Priority Issues

### 1. Surface and Window Management (Critical)
- **Issue**: Cannot get window position
- **Files**: Shell.java, Display.java
- **GTK4 Change**: GtkWindow position APIs removed, coordinate system changed
- **Impact**: Window positioning and dragging broken

### 2. Event Handling (Critical)
- **Issue**: Event model changed completely
- **Files**: Widget.java, Text.java, various widgets
- **GTK4 Change**: Signals replaced with event controllers
- **Impact**: Keyboard/mouse events may not work correctly

### 3. Widget Hierarchy (Critical)
- **Issue**: Cannot access child widgets
- **Files**: Composite.java, Combo.java, Spinner.java
- **GTK4 Change**: GtkContainer removed
- **Impact**: Widget management broken

### 4. Toolbar Implementation (Critical)
- **Issue**: GtkToolbar removed
- **Files**: ToolBar.java, ToolItem.java
- **GTK4 Change**: Must implement custom toolbar using GtkBox
- **Impact**: Toolbars non-functional

### 5. Accessibility (High)
- **Issue**: Multiple features disabled
- **Files**: AccessibleObject.java
- **GTK4 Change**: ATK API changes
- **Impact**: Accessibility features disabled

## Common GTK4 API Migrations

### Window/Surface Management
```
GTK3                              GTK4
----                              ----
gdk_window_*                  →   gdk_surface_*
gdk_surface_get_origin()      →   [REMOVED - no replacement]
gdk_window_move()             →   gdk_toplevel_begin_move()
gdk_window_resize()           →   gdk_toplevel_begin_resize()
gtk_window_get_position()     →   [REMOVED]
```

### Event Handling
```
GTK3                              GTK4
----                              ----
event-after signal            →   [REMOVED - use event controllers]
GDK_2BUTTON_PRESS            →   [REMOVED - use GtkGestureClick]
gdk_keymap_translate_*()      →   gdk_display_map_keycode()
button-press-event            →   GtkGestureClick
key-press-event               →   GtkEventControllerKey
```

### Widget Management
```
GTK3                              GTK4
----                              ----
GtkContainer                  →   [REMOVED - direct widget management]
gtk_container_get_children()  →   gtk_widget_get_first_child() +
                                   gtk_widget_get_next_sibling()
gtk_container_propagate_draw()→   [REMOVED - automatic]
gtk_widget_set_parent_surface()→  [REMOVED]
```

### Other Widgets
```
GTK3                              GTK4
----                              ----
GtkToolbar                    →   [REMOVED - use GtkBox]
gtk_tooltip_*()               →   [API changed]
gdk_device_warp()             →   [REMOVED]
GDK_WINDOW_POPUP              →   [Different window types]
```

## Checking GTK Version in Code

### Java (SWT)
```java
// Check if running on GTK4
if (GTK.GTK4) {
    // GTK4-specific code
} else {
    // GTK3 code
}

// Check GTK version constant
public static final boolean GTK4 = GTK_VERSION >= VERSION(4, 0, 0);
```

### C (Native)
```c
// In os.h
#if GTK_CHECK_VERSION(4,0,0)
#define GTK4 1
// GTK4-specific code
#else
// GTK3 code
#endif
```

### Environment Variable
```bash
# Force GTK4 mode
export SWT_GTK4=1

# Force GTK3 mode (default)
unset SWT_GTK4
```

## Files by Category

### Core Framework
- `OS.java` - Native function bindings
- `GTK.java` - Common GTK functions
- `GTK4.java` - GTK4-only functions
- `GTK3.java` - GTK3-only functions

### Shell/Window Management
- `Shell.java` (11 TODOs)
- `Display.java` (3 TODOs)
- `Decorations.java`

### Widgets
- `Composite.java` (2 TODOs)
- `Control.java` (3 TODOs)
- `Widget.java` (2 TODOs)
- `Combo.java` (3 TODOs)
- `Text.java` (2 TODOs)
- `List.java` (1 TODO)
- `Table.java` (3 TODOs)
- `Tree.java` (2 TODOs)
- `DateTime.java` (1 TODO)
- `Spinner.java` (1 TODO)

### Toolbars and Menus
- `ToolBar.java` (2 TODOs)
- `ToolItem.java` (2 TODOs)
- `ToolTip.java` (1 TODO)
- `Menu.java` (1 TODO)
- `MenuItem.java` (1 TODO)

### Accessibility
- `AccessibleObject.java` (9 TODOs)
- `Accessible.java`

### Graphics
- `GC.java` (4 TODOs)

### Drag and Drop
- `DragSource.java` (1 TODO)
- `DropTarget.java` (1 TODO)
- `Clipboard.java` (1 TODO)

## Development Workflow

### 1. Before Starting Work
```bash
# Check current GTK4 status
grep -r "TODO.*GTK4" --include="*.java" bundles/
```

### 2. Making Changes
1. Review [gtk4-migration-status.md](gtk4-migration-status.md) for context
2. Check GTK4 documentation: https://docs.gtk.org/gtk4/
3. Check GTK3→GTK4 migration guide: https://docs.gtk.org/gtk4/migrating-3to4.html
4. Implement with version checks
5. Test on both GTK3 and GTK4 (if available)

### 3. After Changes
1. Remove or update TODO comments
2. Update gtk4-migration-status.md
3. Update this file if needed
4. Test thoroughly

## Building for GTK4

### Build SO files
```bash
cd bundles/org.eclipse.swt/bin/library
./build.sh -gtk4 install
```

### Run with GTK4
```bash
# Set environment variable
export SWT_GTK4=1

# Run Eclipse or SWT application
./eclipse
```

## Testing Checklist

When testing GTK4 changes:

- [ ] Widget creates successfully
- [ ] Widget displays correctly
- [ ] Mouse events work
- [ ] Keyboard events work
- [ ] Focus handling works
- [ ] Window positioning works (if applicable)
- [ ] Child widget access works (if applicable)
- [ ] Drawing/graphics work correctly
- [ ] No console errors or warnings
- [ ] Works on GTK3 (regression test)

## Common Debugging Tips

### Enable GTK Debug Output
```bash
GTK_DEBUG=interactive ./eclipse
# Or
GTK_DEBUG=all ./eclipse
```

### Check GTK Version at Runtime
```bash
pkg-config --modversion gtk+-4.0
pkg-config --modversion gtk+-3.0
```

### View Widget Hierarchy
Press `Ctrl+Shift+I` to open GTK Inspector (requires enabling):
```bash
gsettings set org.gtk.Settings.Debug enable-inspector-keybinding true
```

## When You Need Help

1. **Documentation**: See [gtk-dev-guide.md](gtk-dev-guide.md)
2. **Detailed Status**: See [gtk4-migration-status.md](gtk4-migration-status.md)
3. **Mailing List**: platform-swt-dev@eclipse.org
4. **GTK Documentation**: https://docs.gtk.org/gtk4/
5. **Migration Guide**: https://docs.gtk.org/gtk4/migrating-3to4.html

## Key Resources

- **GTK4 API**: https://docs.gtk.org/gtk4/
- **GTK3 API**: https://docs.gtk.org/gtk3/
- **Migration Guide**: https://docs.gtk.org/gtk4/migrating-3to4.html
- **SWT Dev Guide**: [gtk-dev-guide.md](gtk-dev-guide.md)
- **Migration Status**: [gtk4-migration-status.md](gtk4-migration-status.md)

## Known Workarounds

### Window Position (No Fix Yet)
Currently no workaround - API removed from GTK4

### Event Handling (Partial)
Use legacy event controller for some events:
```java
if (GTK.GTK4) {
    // May need GtkEventControllerLegacy for some events
}
```

### Child Widget Access (Partial)
Use first_child/next_sibling pattern:
```c
GtkWidget *child = gtk_widget_get_first_child(parent);
while (child) {
    // Process child
    child = gtk_widget_get_next_sibling(child);
}
```

---

Last Updated: [Current Date]

For detailed information, see [gtk4-migration-status.md](gtk4-migration-status.md)
