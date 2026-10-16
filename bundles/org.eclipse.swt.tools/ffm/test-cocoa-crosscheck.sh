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
# Compiles the SWT sources of the Cocoa fragment with plain javac, stock plus the generated FFM classes, and runs
# FFMCrossCheckCocoa, which compares JNI and FFM symbols, struct layouts, struct marshalling and a set of calls in
# one process against the JNI libraries of the fragment. Runs on macOS.
#
# Run generate-cocoa.sh first. Needs a JDK 25. SWT_NATIVES points to the directory with the libswt-*.jnilib files
# (default: the aarch64 fragment, or x86_64 on Intel), BUILD_DIR to the intermediate files.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
case "$(uname -m)" in
	arm64) FRAGMENT="$REPO/binaries/org.eclipse.swt.cocoa.macosx.aarch64" ;;
	*) FRAGMENT="$REPO/binaries/org.eclipse.swt.cocoa.macosx.x86_64" ;;
esac
NATIVES="${SWT_NATIVES:-$FRAGMENT}"
BUILD_DIR="${BUILD_DIR:-${TMPDIR:-/tmp}/swt-ffm-build-cocoa}"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home)}"
export PATH="$JAVA_HOME/bin:$PATH"
OUT="$BUILD_DIR/swt-jni"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/test-classes"

# the source folders of the fragment, plus the generated FFM classes
roots=()
while IFS= read -r entry; do
	dir="$FRAGMENT/$entry"
	[[ "$entry" == *legal_files* || ! -d "$dir" ]] && continue
	roots+=("$(cd "$dir" && pwd)")
done < <(sed -n '/^source\.\. *=/,/^[^ \t]/p' "$FRAGMENT/build.properties" | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[ \t]*//' | grep -v '^$' | grep -v '=')
roots+=("$SWT/Eclipse SWT PI/common-ffm" "$SWT/Eclipse SWT PI/cocoa-ffm")

: > "$OUT/sources.txt"
for root in "${roots[@]}"; do
	find "$root" -name '*.java' -print | sed 's/.*/"&"/' >> "$OUT/sources.txt"
done
echo "Compiling $(wc -l < "$OUT/sources.txt") sources"
javac --release 25 -nowarn -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v '^Note:' || true
test -f "$OUT/classes/org/eclipse/swt/internal/cocoa/OS_FFM.class"

javac --release 25 -nowarn -d "$OUT/test-classes" -cp "$OUT/classes" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMCrossCheckCocoa.java"
echo "== Cross check"
java -XstartOnFirstThread -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED -Djava.library.path="$NATIVES" \
	-cp "$OUT/classes:$OUT/test-classes" org.eclipse.swt.tools.ffm.FFMCrossCheckCocoa \
	"$TOOLS/ffm/report-cocoa/symbols.txt" | grep -v '^  \(read\|write\) '
