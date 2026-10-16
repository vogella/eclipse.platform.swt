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
# Compiles the Cocoa SWT bundle with plain javac into BUILD_DIR/swt-<mode>/classes. Runs on macOS.
#
#   jni  stock SWT plus the FFM classes, used to compare both implementations in one process
#   ffm  the natives of Eclipse SWT PI/cocoa, Eclipse SWT WebKit/cocoa, C and Callback delegate to their FFM implementation
#
# Run generate-cocoa.sh first. Needs a JDK 25 on the PATH.

set -euo pipefail

MODE="${1:-ffm}"
TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
FRAGMENT="$REPO/binaries/org.eclipse.swt.cocoa.macosx.aarch64"
BUILD_DIR="${BUILD_DIR:-${TMPDIR:-/tmp}/swt-ffm-build-cocoa}"
OUT="$BUILD_DIR/swt-$MODE"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

roots=()
add_root() {
	[ -d "$1" ] || return 0
	local dir r
	dir="$(cd "$1" && pwd)"
	for r in ${roots[@]+"${roots[@]}"}; do [ "$r" = "$dir" ] && return 0; done
	roots+=("$dir")
}
while IFS= read -r entry; do
	[[ "$entry" == *legal_files* ]] && continue
	add_root "$FRAGMENT/$entry"
done <<< "$(sed -n '/^source\.\. *=/,/^[^[:space:]]/p' "$FRAGMENT/build.properties" | tr -d '\r' | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[[:space:]]*//' | grep -v '^$' | grep -v '=')"
add_root "$SWT/Eclipse SWT PI/common-ffm"
add_root "$SWT/Eclipse SWT PI/cocoa-ffm"

if [ "$MODE" = ffm ]; then
	# rewrite copies: the PI and WebKit sources, and C and Callback, which the other platforms share
	mkdir -p "$OUT/rewrite/pi" "$OUT/rewrite/webkit" "$OUT/rewrite/shared/org/eclipse/swt/internal"
	cp -r "$SWT/Eclipse SWT PI/cocoa/org" "$OUT/rewrite/pi/"
	cp -r "$SWT/Eclipse SWT WebKit/cocoa/org" "$OUT/rewrite/webkit/"
	cp "$SWT/Eclipse SWT PI/common/org/eclipse/swt/internal/C.java" "$SWT/Eclipse SWT/common/org/eclipse/swt/internal/Callback.java" \
		"$OUT/rewrite/shared/org/eclipse/swt/internal/"
	# hand written FFM classes whose public static methods replace natives
	implementations=()
	for file in "$SWT/Eclipse SWT PI/cocoa-ffm/org/eclipse/swt/internal/ffm/FFMCocoa"*.java; do
		[ -f "$file" ] && implementations+=("$file")
	done
	if [ "${#implementations[@]}" -eq 0 ]; then
		echo "build-cocoa.sh: no FFMCocoa*.java in Eclipse SWT PI/cocoa-ffm" >&2
		exit 1
	fi
	for dir in pi webkit shared; do
		java "$TOOLS/ffm/FFMRewriter.java" "$TOOLS/ffm/report-cocoa/supported.txt" "$OUT/rewrite/$dir" "${implementations[@]}"
	done
	mkdir -p "$OUT/overlay"
	cp -r "$OUT/rewrite/pi/." "$OUT/overlay/"
	cp -r "$OUT/rewrite/webkit/." "$OUT/overlay/"
	cp -r "$OUT/rewrite/shared/." "$OUT/overlay/"
fi

: > "$OUT/sources.txt"
for root in ${roots[@]+"${roots[@]}"}; do
	find "$root" -name '*.java' -print0 > "$OUT/files.tmp"
	while IFS= read -r -d '' file; do
		relative="${file#"$root"/}"
		if [ -f "$OUT/overlay/$relative" ]; then file="$OUT/overlay/$relative"; fi
		printf '"%s"\n' "$file" >> "$OUT/sources.txt"
	done < "$OUT/files.tmp"
	# the native sources under library/ are not part of the classes
	(cd "$root" && find . -path ./library -prune -o -type f ! -name '*.java' ! -name '*.html' -print0 | cpio -0pdm --quiet "$OUT/classes")
done

echo "Compiling $(wc -l < "$OUT/sources.txt" | tr -d ' ') sources ($MODE)"
if ! javac --release 25 -nowarn -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1; then
	grep -v '^Note:' "$OUT/javac.log" >&2 || true
	exit 1
fi
grep -v '^Note:' "$OUT/javac.log" || true
test -f "$OUT/classes/org/eclipse/swt/widgets/Display.class"
test -f "$OUT/classes/org/eclipse/swt/internal/cocoa/OS.class"
echo "Classes: $OUT/classes"
