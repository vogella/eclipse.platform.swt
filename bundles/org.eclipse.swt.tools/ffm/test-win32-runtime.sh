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
# Compiles stock win32 SWT plus the FFM runtime (common-ffm, win32-ffm) and runs
# FFMWin32RuntimeCheck, which compares callbacks, VtblCall and GetLastError with JNI.
#
#   SWT_NATIVES  directory holding a locally built swt-win32-<version>.dll (required,
#                the committed binaries are Git LFS pointers in a clone without LFS)
#
# Runs in Git Bash on Windows x64 with a JDK 25.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
FRAGMENT="$REPO/binaries/org.eclipse.swt.win32.win32.x86_64"
BUILD_DIR="${BUILD_DIR:-${TEMP:-/tmp}/swt-ffm-win32}"
: "${SWT_NATIVES:?set SWT_NATIVES to a directory with swt-win32-*.dll}"

win() { cygpath -m "$1"; }

rm -rf "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/classes"
: > "$BUILD_DIR/sources.txt"
roots=()
while IFS= read -r entry; do
	[[ "$entry" == *legal_files* ]] && continue
	roots+=("$FRAGMENT/$entry")
done < <(sed -n '/^source\.\. *=/,/^[^ \t]/p' "$FRAGMENT/build.properties" | tr -d '\r' | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[ \t]*//' | grep -v '^$' | grep -v '=')
roots+=("$SWT/Eclipse SWT PI/common-ffm" "$SWT/Eclipse SWT PI/win32-ffm")
for root in "${roots[@]}"; do
	find "$root" -name '*.java' | cygpath -m -f - | sed 's/.*/"&"/' >> "$BUILD_DIR/sources.txt"
done
win "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMWin32RuntimeCheck.java" | sed 's/.*/"&"/' >> "$BUILD_DIR/sources.txt"

echo "Compiling $(wc -l < "$BUILD_DIR/sources.txt") sources"
javac --release 25 -nowarn -encoding UTF-8 -d "$(win "$BUILD_DIR/classes")" @"$(win "$BUILD_DIR/sources.txt")" 2>&1 | grep -v '^Note:' || true
test -f "$BUILD_DIR/classes/org/eclipse/swt/tools/ffm/FFMWin32RuntimeCheck.class"

java ${JAVA_OPTS:-} --enable-native-access=ALL-UNNAMED -Djava.library.path="$(win "$SWT_NATIVES")" \
	-cp "$(win "$BUILD_DIR/classes")" org.eclipse.swt.tools.ffm.FFMWin32RuntimeCheck
