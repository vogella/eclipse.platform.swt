#!/bin/bash
###############################################################################
# Copyright (c) 2026 vogella GmbH and others.
#
# This program and the accompanying materials
# are made available under the terms of the Eclipse Public License 2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
###############################################################################
# Generates the GTK FFM bindings from the SWT native declarations, reading both the GTK3 and the GTK4 compile of the
# natives classes that serve both versions (OS, GDK, GTK, ATK) and the GTK4 compile of GTK4.
#
# Needs clang, gcc, pkg-config with the GTK3, GTK4 and GL development headers, a JDK 25 and
# an Eclipse installation (ECLIPSE_HOME) providing JDT Core for the generator.
# Intermediate files go to BUILD_DIR (default: /tmp/swt-ffm-build).

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
SWT="$(cd "$TOOLS/../org.eclipse.swt" && pwd)"
BUILD_DIR="${BUILD_DIR:-/tmp/swt-ffm-build}"
OUTPUT="$SWT/Eclipse SWT PI/gtk-ffm"
REPORT="$TOOLS/ffm/report-gtk"
: "${ECLIPSE_HOME:?set ECLIPSE_HOME to an Eclipse installation containing JDT Core}"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"

mkdir -p "$BUILD_DIR/classes"

plugins="$ECLIPSE_HOME/plugins"
cp=""
for b in org.eclipse.jdt.core org.eclipse.jdt.core.compiler.batch org.eclipse.core.runtime org.eclipse.equinox.common \
		org.eclipse.core.resources org.eclipse.core.jobs org.eclipse.osgi org.eclipse.core.contenttype \
		org.eclipse.equinox.preferences org.eclipse.text org.osgi.service.prefs org.eclipse.swt.gtk.linux.x86_64; do
	jar=$(ls "$plugins/${b}_"*.jar | grep -v '\.source_' | head -1)
	cp="$cp:$jar"
done

echo "Compiling generator"
(cd "$TOOLS/JNI Generation" && find . -name '*.java' ! -name JNIGeneratorAppUI.java > "$BUILD_DIR/sources.txt" \
	&& javac -nowarn -d "$BUILD_DIR/classes" -cp "$cp" @"$BUILD_DIR/sources.txt" \
	&& cp org/eclipse/swt/tools/internal/*.properties "$BUILD_DIR/classes/org/eclipse/swt/tools/internal/")
run_generator() {
	(cd "$TOOLS" && java -cp "$BUILD_DIR/classes$cp" org.eclipse.swt.tools.internal.FFMGeneratorApp "$@")
}

GTK_FLAGS="$(pkg-config --cflags gtk+-3.0 gtk+-unix-print-3.0)"
CAIRO_FLAGS="$(pkg-config --cflags cairo)"
ATK_FLAGS="$(pkg-config --cflags atk gtk+-3.0 gtk+-unix-print-3.0)"
GLX_FLAGS="$(pkg-config --cflags gl x11)"
WEBKIT_FLAGS="$(pkg-config --cflags gio-2.0 gtk+-3.0)"
# the flags of the GTK4 build in make_linux.mak
GTK4_FLAGS="$(pkg-config --cflags gtk4 gtk4-x11 gtk4-unix-print)"
ATK4_FLAGS="$(pkg-config --cflags atk gtk4 gtk4-unix-print)"
INCLUDES=(-I"$SWT/Eclipse SWT/common/library" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux")
CFLAGS=(-DLINUX -DGTK -std=gnu17 -w "${INCLUDES[@]}")

# main class | C file | flags
UNITS=(
	"org.eclipse.swt.internal.C|Eclipse SWT PI/common/library/c.c|"
	"org.eclipse.swt.internal.gtk.OS|Eclipse SWT PI/gtk/library/os.c|$GTK_FLAGS"
	"org.eclipse.swt.internal.gtk3.GTK3|Eclipse SWT PI/gtk/library/gtk3.c|$GTK_FLAGS"
	"org.eclipse.swt.internal.cairo.Cairo|Eclipse SWT PI/cairo/library/cairo.c|$CAIRO_FLAGS"
	"org.eclipse.swt.internal.accessibility.gtk.ATK|Eclipse SWT PI/gtk/library/atk.c|$ATK_FLAGS"
	"org.eclipse.swt.internal.opengl.glx.GLX|Eclipse SWT OpenGL/glx/library/glx.c|$GLX_FLAGS"
	"org.eclipse.swt.internal.webkit.WebKitGTK|Eclipse SWT WebKit/gtk/library/webkitgtk.c|$WEBKIT_FLAGS"
)

# the same natives classes compiled for GTK4; c.c, cairo.c, glx.c and webkitgtk.c do not depend on the GTK version
UNITS4=(
	"org.eclipse.swt.internal.gtk.OS|Eclipse SWT PI/gtk/library/os.c|$GTK4_FLAGS"
	"org.eclipse.swt.internal.gtk4.GTK4|Eclipse SWT PI/gtk/library/gtk4.c|$GTK4_FLAGS"
	"org.eclipse.swt.internal.accessibility.gtk.ATK|Eclipse SWT PI/gtk/library/atk.c|$ATK4_FLAGS"
)

# read_unit <directory> <unit>: AST, layout probe and layout of one C file, appends to args
read_unit() {
	local dir="$1" main cfile flags name
	IFS='|' read -r main cfile flags <<< "$2"
	name=$(basename "$cfile" .c)
	mkdir -p "$BUILD_DIR/$dir"
	echo "Reading C types and struct layouts of $name ($dir)"
	# shellcheck disable=SC2086
	clang -fsyntax-only -Xclang -ast-dump -fno-color-diagnostics "${CFLAGS[@]}" -iquote "$(dirname "$SWT/$cfile")" $flags "$SWT/$cfile" > "$BUILD_DIR/$dir/$name.ast"
	run_generator probe "$main" "$BUILD_DIR/$dir/$name.ast" "$BUILD_DIR/$dir/${name}_probe.c"
	# shellcheck disable=SC2086
	gcc "${CFLAGS[@]}" -iquote "$(dirname "$SWT/$cfile")" -iquote "$SWT/Eclipse SWT PI/gtk/library" $flags "$BUILD_DIR/$dir/${name}_probe.c" -o "$BUILD_DIR/$dir/${name}_probe"
	"$BUILD_DIR/$dir/${name}_probe" > "$BUILD_DIR/$dir/${name}_layout.txt"
	args+=("$main" "$BUILD_DIR/$dir/$name.ast" "$BUILD_DIR/$dir/${name}_layout.txt")
}

args=()
for unit in "${UNITS[@]}"; do
	read_unit gtk3 "$unit"
done
args+=(--gtk4)
for unit in "${UNITS4[@]}"; do
	read_unit gtk4 "$unit"
done

# only the generated classes, hand written FFM support classes live in the same folders
mkdir -p "$OUTPUT"
find "$OUTPUT" -name '*_FFM.java' -delete
run_generator generate "$OUTPUT" "$REPORT" "${args[@]}"
