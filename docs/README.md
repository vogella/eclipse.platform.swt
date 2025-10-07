# SWT Documentation

This directory contains documentation for Eclipse SWT development, with a focus on GTK/Linux development.

## Documents

### [gtk-dev-guide.md](gtk-dev-guide.md)
**The Comprehensive Guide to SWT Development for Linux/GTK**

This is the main development guide for SWT on Linux/GTK. It covers:
- Setting up your development environment
- Building SWT native libraries
- Understanding the SWT codebase structure
- Adding custom GTK functions to OS.java
- Working with GTK3 and GTK4
- Debugging GTK and SWT code
- Using GtkInspector
- And much more...

**Start here** if you're new to SWT GTK development.

### [gtk4-migration-status.md](gtk4-migration-status.md)
**GTK4 Migration Status - Comprehensive Tracking Document**

A detailed tracking document identifying all areas in the SWT codebase that need updates for GTK4 compatibility. Includes:
- 61 identified TODO items across 30 files
- 8 main categories of issues
- Priority rankings (High/Medium/Low)
- Specific API changes and required actions
- Testing checklist

**Use this** when working on GTK4 migration tasks or to understand the current status.

### [gtk4-quick-reference.md](gtk4-quick-reference.md)
**GTK4 Quick Reference for SWT Developers**

A quick reference guide for common GTK4 migration questions. Includes:
- Top priority issues at a glance
- Common GTK3→GTK4 API migrations
- Version checking patterns
- Development workflow
- Testing checklist
- Common debugging tips
- Known workarounds

**Use this** for quick answers while developing or reviewing GTK4-related code.

## GTK4 Support Status

GTK4 support in SWT is currently **experimental**. Key statistics:
- **61** identified TODO items
- **30** affected source files
- **~15-20** critical issues

The main areas requiring work:
1. Surface and window management
2. Event handling (new event controller model)
3. Widget hierarchy and children access
4. Toolbar implementation (GtkToolbar removed)
5. Accessibility features

## Quick Links

### For New Contributors
1. Start with [gtk-dev-guide.md](gtk-dev-guide.md)
2. Set up your development environment
3. Review [gtk4-quick-reference.md](gtk4-quick-reference.md) to understand GTK4 status
4. Pick an issue from [gtk4-migration-status.md](gtk4-migration-status.md)

### For GTK4 Migration Work
1. Review [gtk4-migration-status.md](gtk4-migration-status.md) for comprehensive status
2. Use [gtk4-quick-reference.md](gtk4-quick-reference.md) for quick patterns
3. Refer to [gtk-dev-guide.md](gtk-dev-guide.md) for development practices

### External Resources
- [GTK4 API Documentation](https://docs.gtk.org/gtk4/)
- [GTK3 to GTK4 Migration Guide](https://docs.gtk.org/gtk4/migrating-3to4.html)
- [GTK3 API Documentation](https://docs.gtk.org/gtk3/)

## Contact

For questions or discussions:
- Mailing list: platform-swt-dev@eclipse.org
- IRC: #swt-gtk, #eclipse-dev (Freenode)

## Contributing

Contributions to these documents are welcome! If you:
- Find outdated information
- Complete a GTK4 migration task
- Discover new issues or solutions
- Have suggestions for improvement

Please update the relevant documentation as part of your contribution.
