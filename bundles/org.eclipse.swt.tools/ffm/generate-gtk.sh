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
# Generates the GTK3 FFM bindings from the SWT native declarations.
#
# Needs clang, gcc, pkg-config with the GTK3 development headers, a JDK 21+ and
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
INCLUDES=(-I"$SWT/Eclipse SWT/common/library" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux")
CFLAGS=(-DLINUX -DGTK -std=gnu17 -w "${INCLUDES[@]}")

# main class | C file | flags
UNITS=(
	"org.eclipse.swt.internal.C|Eclipse SWT PI/common/library/c.c|"
	"org.eclipse.swt.internal.gtk.OS|Eclipse SWT PI/gtk/library/os.c|$GTK_FLAGS"
	"org.eclipse.swt.internal.gtk3.GTK3|Eclipse SWT PI/gtk/library/gtk3.c|$GTK_FLAGS"
	"org.eclipse.swt.internal.cairo.Cairo|Eclipse SWT PI/cairo/library/cairo.c|$CAIRO_FLAGS"
	"org.eclipse.swt.internal.accessibility.gtk.ATK|Eclipse SWT PI/gtk/library/atk.c|$ATK_FLAGS"
)

args=()
for unit in "${UNITS[@]}"; do
	IFS='|' read -r main cfile flags <<< "$unit"
	name=$(basename "$cfile" .c)
	echo "Reading C types and struct layouts of $name"
	# shellcheck disable=SC2086
	clang -fsyntax-only -Xclang -ast-dump -fno-color-diagnostics "${CFLAGS[@]}" -iquote "$(dirname "$SWT/$cfile")" $flags "$SWT/$cfile" > "$BUILD_DIR/$name.ast"
	run_generator probe "$main" "$BUILD_DIR/$name.ast" "$BUILD_DIR/${name}_probe.c"
	# shellcheck disable=SC2086
	gcc "${CFLAGS[@]}" -iquote "$(dirname "$SWT/$cfile")" -iquote "$SWT/Eclipse SWT PI/gtk/library" $flags "$BUILD_DIR/${name}_probe.c" -o "$BUILD_DIR/${name}_probe"
	"$BUILD_DIR/${name}_probe" > "$BUILD_DIR/${name}_layout.txt"
	args+=("$main" "$BUILD_DIR/$name.ast" "$BUILD_DIR/${name}_layout.txt")
done

# only the generated classes, hand written FFM support classes live in the same folders
mkdir -p "$OUTPUT"
find "$OUTPUT" -name '*_FFM.java' -delete
run_generator generate "$OUTPUT" "$REPORT" "${args[@]}"
