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
# Compiles the Win32 SWT bundle with plain javac into BUILD_DIR/swt-<mode>/classes. Runs in Git Bash.
#
#   jni  stock SWT plus the FFM classes, used to compare both implementations in one process
#   ffm  the natives of Eclipse SWT PI/win32, C and Callback delegate to their FFM implementation
#
# Run generate-win32.sh first. Needs a JDK 25.

set -euo pipefail
export MSYS2_ARG_CONV_EXCL='*'

MODE="${1:-ffm}"
TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
FRAGMENT="$REPO/binaries/org.eclipse.swt.win32.win32.x86_64"
BUILD_DIR="${BUILD_DIR:-${TEMP:-/tmp}/swt-ffm-build-win32}"
BUILD_DIR="$(cygpath -u "$BUILD_DIR")"
OUT="$BUILD_DIR/swt-$MODE"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

roots=()
while IFS= read -r entry; do
	dir="$FRAGMENT/$entry"
	[[ "$entry" == *legal_files* || ! -d "$dir" ]] && continue
	roots+=("$(cd "$dir" && pwd)")
done < <(sed -n '/^source\.\. *=/,/^[^ \t]/p' "$FRAGMENT/build.properties" | tr -d '\r' | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[ \t]*//' | grep -v '^$' | grep -v '=')
for extra in "$SWT/Eclipse SWT PI/common-ffm" "$SWT/Eclipse SWT PI/win32-ffm"; do
	[[ " ${roots[*]} " == *" $extra "* ]] || roots+=("$extra")
done

if [ "$MODE" = ffm ]; then
	# rewrite copies: the PI sources, and C and Callback, which the other platforms share
	mkdir -p "$OUT/rewrite/pi" "$OUT/rewrite/shared/org/eclipse/swt/internal"
	cp -r "$SWT/Eclipse SWT PI/win32/org" "$OUT/rewrite/pi/"
	cp "$SWT/Eclipse SWT PI/common/org/eclipse/swt/internal/C.java" "$SWT/Eclipse SWT/common/org/eclipse/swt/internal/Callback.java" \
		"$OUT/rewrite/shared/org/eclipse/swt/internal/"
	# hand written FFM classes whose public static methods replace natives
	implementations=()
	for name in FFMCom FFMComCustom FFMOsCustom FFMGdipGraphics FFMGdipObjects FFMWin32Macros FFMResources; do
		implementations+=("$(cygpath -m "$SWT/Eclipse SWT PI/win32-ffm/org/eclipse/swt/internal/ffm/$name.java")")
	done
	for dir in pi shared; do
		java "$(cygpath -m "$TOOLS/ffm/FFMRewriter.java")" "$(cygpath -m "$TOOLS/ffm/report-win32/supported.txt")" \
			"$(cygpath -m "$OUT/rewrite/$dir")" "${implementations[@]}"
	done
	mkdir -p "$OUT/overlay"
	cp -r "$OUT/rewrite/pi/." "$OUT/overlay/"
	cp -r "$OUT/rewrite/shared/." "$OUT/overlay/"
fi

: > "$OUT/sources.txt"
for root in "${roots[@]}"; do
	while IFS= read -r -d '' file; do
		relative="${file#"$root"/}"
		if [ -f "$OUT/overlay/$relative" ]; then file="$OUT/overlay/$relative"; fi
		printf '"%s"\n' "$(cygpath -m "$file")" >> "$OUT/sources.txt"
	done < <(find "$root" -name '*.java' -print0)
	(cd "$root" && find . -type f ! -name '*.java' ! -name '*.html' -exec cp --parents {} "$OUT/classes" \;)
done

echo "Compiling $(wc -l < "$OUT/sources.txt") sources ($MODE)"
javac --release 25 -nowarn -encoding UTF-8 -d "$(cygpath -m "$OUT/classes")" @"$(cygpath -m "$OUT/sources.txt")" 2>&1 | grep -v '^Note:' || true
test -f "$OUT/classes/org/eclipse/swt/widgets/Display.class"
test -f "$OUT/classes/org/eclipse/swt/internal/win32/OS_FFM.class"
echo "Classes: $OUT/classes"
