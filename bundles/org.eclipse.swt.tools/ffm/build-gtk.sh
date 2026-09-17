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
# Compiles the GTK SWT bundle with plain javac into BUILD_DIR/swt-<mode>/classes.
#
#   jni  stock SWT plus the generated FFM classes, used to compare both implementations in one process
#   ffm  every native the generator supports delegates to its FFM implementation, the rest stays JNI
#
# Run generate-gtk.sh first. Needs a JDK 25.

set -euo pipefail

MODE="${1:-ffm}"
TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
FRAGMENT="$REPO/binaries/org.eclipse.swt.gtk.linux.x86_64"
BUILD_DIR="${BUILD_DIR:-/tmp/swt-ffm-build}"
OUT="$BUILD_DIR/swt-$MODE"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

roots=()
while IFS= read -r entry; do
	dir="$FRAGMENT/$entry"
	[[ "$entry" == *legal_files* || ! -d "$dir" ]] && continue
	roots+=("$(cd "$dir" && pwd)")
done < <(sed -n '/^source\.\. *=/,/^[^ \t]/p' "$FRAGMENT/build.properties" | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[ \t]*//' | grep -v '^$' | grep -v '=')
roots+=("$SWT/Eclipse SWT PI/common-ffm" "$SWT/Eclipse SWT PI/gtk-ffm")

if [ "$MODE" = ffm ]; then
	generator_classes="$BUILD_DIR/classes"
	# hand written FFM classes whose public static methods replace natives
	mapfile -t implementations < <(ls "$SWT/Eclipse SWT PI/common-ffm/org/eclipse/swt/internal/ffm/FFMUtf16.java" \
		"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMConstructorProc.java" \
		"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMMacros.java" \
		"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMSwtFixed.java" \
		"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMTypes.java" \
		"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMAccessible.java")
	for root in "${roots[@]}"; do
		(cd "$TOOLS" && java -cp "$generator_classes" org.eclipse.swt.tools.internal.FFMGeneratorApp rewrite \
			"$TOOLS/ffm/report-gtk/supported.txt" "$root" "$OUT/overlay" "${implementations[@]}") | grep -v ' 0 natives' || true
	done
fi

: > "$OUT/sources.txt"
for root in "${roots[@]}"; do
	while IFS= read -r -d '' file; do
		relative="${file#"$root"/}"
		if [ -f "$OUT/overlay/$relative" ]; then file="$OUT/overlay/$relative"; fi
		printf '"%s"\n' "$file" >> "$OUT/sources.txt"
	done < <(find "$root" -name '*.java' -print0)
	(cd "$root" && find . -type f ! -name '*.java' ! -name '*.html' -print0 | cpio -0pdm --quiet "$OUT/classes")
done

echo "Compiling $(wc -l < "$OUT/sources.txt") sources ($MODE)"
javac --release 25 -nowarn -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v '^Note:' || true
test -f "$OUT/classes/org/eclipse/swt/widgets/Display.class"
echo "Classes: $OUT/classes"
