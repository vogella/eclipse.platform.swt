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
package org.eclipse.swt.tools.internal;

/**
 * Generates a C program that prints size, offset, type class and signedness of every struct field
 * the JNI glue accesses, compiled with the same headers and flags as the <code>_structs.c</code> file.
 */
public class FFMProbeGenerator extends JNIGenerator {

	final CTypes ctypes;

	public FFMProbeGenerator(CTypes ctypes) {
		this.ctypes = ctypes;
	}

	@Override
	public void generateIncludes() {
		String outputName = getOutputName();
		outputln("#include \"swt.h\"");
		outputln("#include \"" + outputName + "_structs.h\"");
		if (outputName.equals("gtk3") || outputName.equals("gtk4")) outputln("#include \"os_structs.h\"");
		outputln("#include <stdio.h>");
		outputln("#include <string.h>");
		outputln();
		outputln("#define SWT_SIGNED(e) ((__typeof__(e))-1 < (__typeof__(e))0)");
		outputln();
		outputln("int main(void) {");
	}

	/** C types whose size the FFM code needs although no Java struct class describes them, per unit. */
	static final String[][] EXTRA_SIZES = {
		{"os", "GtkContainer"}, {"os", "GtkContainerClass"},
		{"os", "GtkTextIter"}, {"os", "GtkTreeIter"}, {"os", "GPollFD"}, {"os", "GValue"},
		{"os", "GtkCellRendererText"}, {"os", "GtkCellRendererTextClass"},
		{"os", "GtkCellRendererPixbuf"}, {"os", "GtkCellRendererPixbufClass"},
		{"os", "GtkCellRendererToggle"}, {"os", "GtkCellRendererToggleClass"},
		{"os", "GtkContainerAccessible"}, {"os", "GtkContainerAccessibleClass"},
	};

	/** Struct fields the FFM code needs the offset of, per unit. */
	static final String[][] EXTRA_OFFSETS = {
		{"os", "GtkWidgetClass", "realize"}, {"os", "GtkWidgetClass", "map"},
		{"os", "GtkWidgetClass", "get_preferred_width"}, {"os", "GtkWidgetClass", "get_preferred_height"},
		{"os", "GtkWidgetClass", "size_allocate"}, {"os", "GtkWidgetClass", "get_accessible"},
		{"os", "GtkContainerClass", "add"}, {"os", "GtkContainerClass", "remove"}, {"os", "GtkContainerClass", "forall"},
	};

	@Override
	public void generate() {
		super.generate();
		String unit = getOutputName();
		for (String[] extra : EXTRA_SIZES) {
			if (!extra[0].equals(unit)) continue;
			outputln("\tprintf(\"EXTRA." + extra[1] + "=%zu\\n\", sizeof(" + extra[1] + "));");
		}
		for (String[] extra : EXTRA_OFFSETS) {
			if (!extra[0].equals(unit)) continue;
			outputln("\tprintf(\"EXTRA." + extra[1] + "." + extra[2] + "=%zu\\n\", (size_t)__builtin_offsetof(" + extra[1] + ", " + extra[2] + "));");
		}
		outputln("\treturn 0;");
		outputln("}");
	}

	@Override
	public void generate(JNIClass clazz) {
		String name = clazz.getSimpleName();
		String type = (clazz.getFlag(FLAG_STRUCT) ? "struct " : "") + name;
		String exclude = clazz.getExclude();
		if (exclude.length() != 0) outputln(exclude);
		outputln("\tprintf(\"" + name + "=%zu\\n\", sizeof(" + type + "));");
		for (JNIField field : clazz.getDeclaredFields()) {
			if (FFMGenerator.ignoreField(field)) continue;
			String accessor = field.getAccessor();
			if (accessor == null || accessor.length() == 0) accessor = field.getName();
			String key = name + "." + field.getName();
			String expr = "((" + type + " *)0)->" + accessor;
			String offset = "(size_t)__builtin_offsetof(" + type + ", " + accessor + ")";
			String fieldExclude = field.getExclude();
			if (fieldExclude.length() != 0) outputln(fieldExclude);
			JNIType fieldType = field.getType();
			if (accessor.matches("\\w+") && ctypes.isBitfield(type, accessor)) {
				outputln("\t{");
				outputln("\t\t" + type + " s; unsigned char *p = (unsigned char *)&s; int lo = -1, width = 0; size_t i;");
				outputln("\t\tmemset(&s, 0, sizeof(s)); s." + accessor + " = 1;");
				outputln("\t\tfor (i = 0; i < sizeof(s) * 8; i++) if (p[i / 8] & (1 << (i % 8))) { lo = (int)i; break; }");
				outputln("\t\tmemset(&s, 0, sizeof(s)); s." + accessor + " = ~0;");
				outputln("\t\tfor (i = 0; i < sizeof(s) * 8; i++) if (p[i / 8] & (1 << (i % 8))) width++;");
				outputln("\t\tprintf(\"" + key + "=bit,%d,%d\\n\", lo, width);");
				outputln("\t}");
			} else if (fieldType.isPrimitive()) {
				outputln("\tprintf(\"" + key + "=%zu,%zu,%d,%d,0\\n\", " + offset + ", sizeof(" + expr + "), __builtin_classify_type(" + expr + "), (int)SWT_SIGNED(" + expr + "));");
			} else if (fieldType.isArray()) {
				outputln("\tprintf(\"" + key + "=%zu,%zu,0,0,%zu\\n\", " + offset + ", sizeof(" + expr + "), sizeof(" + expr + "[0]));");
			} else {
				outputln("\tprintf(\"" + key + "=%zu,%zu,0,0,0\\n\", " + offset + ", sizeof(" + expr + "));");
			}
			if (fieldExclude.length() != 0) outputln("#endif");
		}
		if (exclude.length() != 0) outputln("#endif");
	}
}
