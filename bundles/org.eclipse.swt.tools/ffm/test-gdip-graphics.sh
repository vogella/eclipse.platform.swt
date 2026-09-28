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
# Checks FFMGdipGraphics against the JNI GDI+ C++ wrapper on Windows x64.
#
#  1. GdipGraphicsCrossCheck calls every Graphics native both ways in one process and compares
#     results, out-parameters, clip regions, transforms and pixels.
#  2. GdipScenes renders GC drawing in advanced mode with the stock build and with a build whose
#     Graphics natives go through FFM (GdipGraphicsBridge), and compares the pixels.
#
# SWT_NATIVES must point to a directory with the JNI DLLs built from this checkout
# (binaries/org.eclipse.swt.win32.win32.x86_64: mvn clean antrun:run@build-native-binaries).
# Needs a JDK 25 and Git Bash.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
FRAGMENT="$REPO/binaries/org.eclipse.swt.win32.win32.x86_64"
BUILD_DIR="${BUILD_DIR:-${TMP:-/tmp}/swt-ffm-gdip}"
: "${SWT_NATIVES:?SWT_NATIVES must point to the JNI DLLs}"
TEST="$TOOLS/ffm/test/org/eclipse/swt/tools/ffm"

roots=()
while IFS= read -r entry; do
	dir="$FRAGMENT/$entry"
	[[ "$entry" == *legal_files* || ! -d "$dir" ]] && continue
	roots+=("$(cd "$dir" && pwd)")
done < <(sed -n '/^source\.\. *=/,/^[^ \t]/p' "$FRAGMENT/build.properties" | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[ \t]*//' | grep -v '^$' | grep -v '=')
roots+=("$SWT/Eclipse SWT PI/common-ffm" "$SWT/Eclipse SWT PI/win32-ffm")

build() {
	local mode="$1" out="$BUILD_DIR/$1"
	rm -rf "$out"
	mkdir -p "$out/classes" "$out/overlay"
	local gdip="org/eclipse/swt/internal/gdip/Gdip.java"
	if [ "$mode" = ffm ]; then
		mkdir -p "$out/overlay/$(dirname "$gdip")"
		cp "$SWT/Eclipse SWT PI/win32/$gdip" "$out/overlay/$gdip"
		: > "$out/supported.txt"
		java "$TOOLS/ffm/FFMRewriter.java" "$out/supported.txt" "$out/overlay" "$TEST/GdipGraphicsBridge.java"
	fi
	: > "$out/sources.txt"
	for root in "${roots[@]}"; do
		while IFS= read -r -d '' file; do
			relative="${file#"$root"/}"
			if [ -f "$out/overlay/$relative" ]; then file="$out/overlay/$relative"; fi
			printf '"%s"\n' "$(cygpath -m "$file")" >> "$out/sources.txt"
		done < <(find "$root" -name '*.java' -print0)
		(cd "$root" && tar cf - --exclude='*.java' --exclude='*.html' .) | (cd "$out/classes" && tar xf -)
	done
	for file in GdipGraphicsBridge GdipGraphicsCrossCheck GdipScenes; do
		printf '"%s"\n' "$(cygpath -m "$TEST/$file.java")" >> "$out/sources.txt"
	done
	echo "Compiling $(wc -l < "$out/sources.txt") sources ($mode)"
	javac --release 25 -nowarn -encoding UTF-8 -d "$out/classes" @"$out/sources.txt" 2>&1 | grep -v '^Note:' || true
	test -f "$out/classes/org/eclipse/swt/tools/ffm/GdipScenes.class"
}

run() {
	local mode="$1"; shift
	(cd "$BUILD_DIR" && java --enable-native-access=ALL-UNNAMED -Djava.library.path="$SWT_NATIVES" -cp "$BUILD_DIR/$mode/classes" "$@")
}

if [ -n "${SKIP_BUILD:-}" ]; then
	# only recompile the test classes
	for mode in jni ffm; do
		javac --release 25 -nowarn -encoding UTF-8 -cp "$BUILD_DIR/$mode/classes" -d "$BUILD_DIR/$mode/classes" \
			"$TEST/GdipGraphicsBridge.java" "$TEST/GdipGraphicsCrossCheck.java" "$TEST/GdipScenes.java"
	done
else
	build jni
	build ffm
fi
run jni org.eclipse.swt.tools.ffm.GdipGraphicsCrossCheck
run jni org.eclipse.swt.tools.ffm.GdipScenes render "$BUILD_DIR/out-jni"
run ffm org.eclipse.swt.tools.ffm.GdipScenes render "$BUILD_DIR/out-ffm"
run jni org.eclipse.swt.tools.ffm.GdipScenes compare "$BUILD_DIR/out-jni" "$BUILD_DIR/out-ffm"
