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
# Compares FFMOsCustom with the JNI natives of os_custom.c on Windows:
#
#   1. builds validators.dll from the Validate_* functions of os_custom.c, once per processor branch
#   2. compiles the win32 SWT sources, FFMOsCustom and FFMOsCustomCheck with javac
#   3. runs the check against swt-win32 from SWT_NATIVES, then once without any SWT library
#
# Runs in Git Bash. Needs a JDK 25, clang (LLVM) and locally built JNI DLLs, since LFS is not smudged.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
FRAGMENT="$REPO/binaries/org.eclipse.swt.win32.win32.x86_64"
NATIVES="$(cygpath -m "${SWT_NATIVES:-$FRAGMENT}")"
BUILD_DIR="${BUILD_DIR:-${TEMP:-/tmp}/swt-ffm-oscustom}"
CLANG="${CLANG:-C:/Program Files/LLVM/bin/clang}"
mkdir -p "$BUILD_DIR"
OUT="$(cygpath -m "$BUILD_DIR")"

extract() { awk '/^BOOL Validate_/{p=1} p{print} p&&/^}/{p=0; print ""}' "$SWT/Eclipse SWT PI/win32/library/os_custom.c"; }
{
	echo '#include <windows.h>'
	extract | sed -e 's/#ifdef _M_X64/#if 1/' -e 's/#elif defined(_M_ARM64)/#elif 0/' -e 's/^BOOL Validate_\(\w*\)/__declspec(dllexport) BOOL x64_\1/'
	extract | sed -e 's/#ifdef _M_X64/#if 0/' -e 's/#elif defined(_M_ARM64)/#elif 1/' -e 's/^BOOL Validate_\(\w*\)/__declspec(dllexport) BOOL arm64_\1/'
} > "$OUT/validators.c"
"$CLANG" -O1 -shared -o "$OUT/validators.dll" "$OUT/validators.c"

roots=()
while IFS= read -r entry; do
	dir="$FRAGMENT/$entry"
	[[ "$entry" == *legal_files* || ! -d "$dir" ]] && continue
	roots+=("$(cd "$dir" && pwd)")
done < <(sed -n '/^source\.\. *=/,/^[^ \t]/p' "$FRAGMENT/build.properties" | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[ \t]*//' | grep -v '^$' | grep -v '=')
roots+=("$SWT/Eclipse SWT PI/win32-ffm")

: > "$OUT/sources.txt"
for root in "${roots[@]}"; do
	find "$root" -name '*.java' -print0 | while IFS= read -r -d '' file; do printf '"%s"\n' "$(cygpath -m "$file")" >> "$OUT/sources.txt"; done
done
printf '"%s"\n' "$(cygpath -m "$SWT/Eclipse SWT PI/common-ffm/org/eclipse/swt/internal/ffm/FFM.java")" >> "$OUT/sources.txt"
printf '"%s"\n' "$(cygpath -m "$TOOLS/ffm/test/org/eclipse/swt/internal/ffm/FFMOsCustomCheck.java")" >> "$OUT/sources.txt"
rm -rf "$OUT/classes"
javac --release 25 -nowarn -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v '^Note:' || true

java --enable-native-access=ALL-UNNAMED -Dswt.library.path="$NATIVES" -cp "$OUT/classes" org.eclipse.swt.internal.ffm.FFMOsCustomCheck "$OUT/validators.dll"
java --enable-native-access=ALL-UNNAMED -cp "$OUT/classes" org.eclipse.swt.internal.ffm.FFMOsCustomCheck ffm-only
