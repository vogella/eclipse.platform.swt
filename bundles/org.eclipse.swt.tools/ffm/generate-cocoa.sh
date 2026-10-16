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
# Generates the Cocoa FFM bindings from the SWT native declarations. Runs on macOS.
#
# Needs clang from the Xcode command line tools, a JDK 25 and an Eclipse installation (ECLIPSE_HOME) providing
# JDT Core for the generator. ARCH=arm64 (default on Apple silicon) or x86_64; the layout probes run, so they
# have to be built for the host. Intermediate files go to BUILD_DIR (default: $TMPDIR/swt-ffm-build-cocoa).

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
SWT="$(cd "$TOOLS/../org.eclipse.swt" && pwd)"
ARCH="${ARCH:-$(uname -m)}"
case "$ARCH" in
	arm64|aarch64) ARCH=arm64; SWT_FRAGMENT=org.eclipse.swt.cocoa.macosx.aarch64 ;;
	x86_64) SWT_FRAGMENT=org.eclipse.swt.cocoa.macosx.x86_64 ;;
	*) echo "Unknown ARCH $ARCH" >&2; exit 1 ;;
esac
BUILD_DIR="${BUILD_DIR:-${TMPDIR:-/tmp}/swt-ffm-build-cocoa}"
OUTPUT="$SWT/Eclipse SWT PI/cocoa-ffm"
REPORT="$TOOLS/ffm/report-cocoa"
LIBRARY="$SWT/Eclipse SWT PI/cocoa/library"
: "${ECLIPSE_HOME:?set ECLIPSE_HOME to an Eclipse installation containing JDT Core}"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home)}"
export PATH="$JAVA_HOME/bin:$PATH"
# same deployment target as build.sh
export MACOSX_DEPLOYMENT_TARGET=11

plugins=""
for dir in "$ECLIPSE_HOME/plugins" "$ECLIPSE_HOME/Contents/Eclipse/plugins" "$ECLIPSE_HOME/Eclipse.app/Contents/Eclipse/plugins"; do
	[ -d "$dir" ] && plugins="$dir" && break
done
[ -n "$plugins" ] || { echo "No plugins folder in $ECLIPSE_HOME" >&2; exit 1; }

mkdir -p "$BUILD_DIR/classes"
cp=""
for b in org.eclipse.jdt.core org.eclipse.jdt.core.compiler.batch org.eclipse.core.runtime org.eclipse.equinox.common \
		org.eclipse.core.resources org.eclipse.core.jobs org.eclipse.osgi org.eclipse.core.contenttype \
		org.eclipse.equinox.preferences org.eclipse.text org.osgi.service.prefs "$SWT_FRAGMENT"; do
	jar=$(ls "$plugins/${b}_"*.jar | grep -v '\.source_' | sort | tail -1)
	cp="$cp:$jar"
done

echo "Compiling generator"
(cd "$TOOLS/JNI Generation" && find . -name '*.java' ! -name JNIGeneratorAppUI.java > "$BUILD_DIR/sources.txt" \
	&& javac -nowarn -d "$BUILD_DIR/classes" -cp "$cp" @"$BUILD_DIR/sources.txt" \
	&& cp org/eclipse/swt/tools/internal/*.properties "$BUILD_DIR/classes/org/eclipse/swt/tools/internal/")
run_generator() {
	(cd "$TOOLS" && java -cp "$BUILD_DIR/classes$cp" org.eclipse.swt.tools.internal.FFMGeneratorApp "$@")
}

# the flags of make_macosx.mak; Objective-C, as the JNI glue is compiled
CFLAGS=(-x objective-c -arch "$ARCH" -DUSE_ASSEMBLER -DCOCOA -DATOMIC -w
	-I "$JAVA_HOME/include" -I "$JAVA_HOME/include/darwin"
	-I /System/Library/Frameworks/Cocoa.framework/Headers -I /System/Library/Frameworks/JavaScriptCore.framework/Headers
	-I "$SWT/Eclipse SWT/common/library")
LFLAGS=(-framework Cocoa -framework Carbon -framework WebKit -framework CoreServices -framework JavaScriptCore -framework Security -framework SecurityInterface)

# main class | C file
UNITS=(
	"org.eclipse.swt.internal.C|Eclipse SWT PI/common/library/c.c"
	"org.eclipse.swt.internal.cocoa.OS|Eclipse SWT PI/cocoa/library/os.c"
)

args=()
for unit in "${UNITS[@]}"; do
	IFS='|' read -r main cfile <<< "$unit"
	name=$(basename "$cfile" .c)
	src="$SWT/$cfile"
	echo "Reading C types, macros and struct layouts of $name"
	# CGDisplayCreateImage is unavailable to the newest SDKs: the AST is complete, the diagnostic does not matter
	clang -fsyntax-only -Xclang -ast-dump -fno-color-diagnostics "${CFLAGS[@]}" -iquote "$(dirname "$src")" "$src" > "$BUILD_DIR/$name.ast" 2> "$BUILD_DIR/$name.ast.log" || true
	[ -s "$BUILD_DIR/$name.ast" ] || { cat "$BUILD_DIR/$name.ast.log" >&2; exit 1; }
	clang -E -dM "${CFLAGS[@]}" -iquote "$(dirname "$src")" "$src" > "$BUILD_DIR/$name.macros" 2>> "$BUILD_DIR/$name.ast.log" || true
	run_generator probe "$main" "$BUILD_DIR/$name.ast" "$BUILD_DIR/${name}_probe.c"
	clang "${CFLAGS[@]}" -iquote "$(dirname "$src")" "$BUILD_DIR/${name}_probe.c" -o "$BUILD_DIR/${name}_probe" "${LFLAGS[@]}"
	"$BUILD_DIR/${name}_probe" > "$BUILD_DIR/${name}_layout.txt"
	args+=("$main" "$BUILD_DIR/$name.ast" "$BUILD_DIR/${name}_layout.txt")
done

# only the generated classes, hand written FFM support classes live in the same folders
mkdir -p "$OUTPUT"
find "$OUTPUT" -name '*_FFM.java' -delete
run_generator generate "$OUTPUT" "$REPORT" --cocoa "${args[@]}"
