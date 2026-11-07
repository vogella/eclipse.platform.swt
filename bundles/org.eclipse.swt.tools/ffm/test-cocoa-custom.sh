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
# Compares the hand written Cocoa ports (FFMCocoaCustom, FFMCocoaCallbacks) with the JNI natives of
# os_custom.c and os.c on macOS:
#
#   1. compiles the source folders of the aarch64 fragment, the common FFM runtime, the two ports and
#      FFMCocoaCustomCheck with plain javac
#   2. runs the check on the first thread against the JNI libraries of the fragment
#
# Needs a JDK 25. JAVA_HOME and SWT_NATIVES (the directory with the libswt-*.jnilib) can be overridden.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
SWT="$REPO/bundles/org.eclipse.swt"
FRAGMENT="$REPO/binaries/org.eclipse.swt.cocoa.macosx.aarch64"
NATIVES="${SWT_NATIVES:-$FRAGMENT}"
BUILD_DIR="${BUILD_DIR:-${TMPDIR:-/tmp}/swt-ffm-cocoa-custom}"
JAVA_HOME="${JAVA_HOME:-/Users/vogella/Library/Java/JavaVirtualMachines/temurin-25-sdkman.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"

: > "$BUILD_DIR/sources.txt"
add_root() {
	find "$1" -name '*.java' | while IFS= read -r file; do printf '"%s"\n' "$file" >> "$BUILD_DIR/sources.txt"; done
}
sed -n '/^source\.\. *=/,/^[^ \t]/p' "$FRAGMENT/build.properties" | sed -e 's/^source\.\. *=//' -e 's/\\$//' -e 's/,$//' -e 's/^[ \t]*//' | grep -v '^$' | grep -v '=' | while IFS= read -r entry; do
	dir="$FRAGMENT/$entry"
	[[ "$entry" == *legal_files* || ! -d "$dir" ]] && continue
	add_root "$(cd "$dir" && pwd)"
done
add_root "$SWT/Eclipse SWT PI/common-ffm"
for name in FFMCocoaCustom FFMCocoaCallbacks; do
	printf '"%s"\n' "$SWT/Eclipse SWT PI/cocoa-ffm/org/eclipse/swt/internal/ffm/$name.java" >> "$BUILD_DIR/sources.txt"
done
printf '"%s"\n' "$TOOLS/ffm/test/org/eclipse/swt/internal/ffm/FFMCocoaCustomCheck.java" >> "$BUILD_DIR/sources.txt"

echo "Compiling $(wc -l < "$BUILD_DIR/sources.txt" | tr -d ' ') sources"
javac --release 25 -nowarn -encoding UTF-8 -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt" 2>&1 | grep -v '^Note:' || true
test -f "$BUILD_DIR/classes/org/eclipse/swt/internal/ffm/FFMCocoaCustomCheck.class"

java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -Djava.library.path="$NATIVES" \
	-Dcheck.os="$SWT/Eclipse SWT PI/cocoa/org/eclipse/swt/internal/cocoa/OS.java" \
	-cp "$BUILD_DIR/classes" org.eclipse.swt.internal.ffm.FFMCocoaCustomCheck "$@"
