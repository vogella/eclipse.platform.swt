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
# Verifies the generated Win32 FFM bindings with FFMCrossCheckWin32, which compares JNI and FFM symbols, struct
# layouts, struct marshalling and a set of calls in one process. Runs in Git Bash.
#
# Run generate-win32.sh first. Needs a JDK 25 and the JNI DLLs of this checkout: SWT_NATIVES points to their
# directory (default: the win32 x86_64 fragment, which holds Git LFS pointers unless LFS is set up).

set -euo pipefail
export MSYS2_ARG_CONV_EXCL='*'

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
NATIVES="${SWT_NATIVES:-$REPO/binaries/org.eclipse.swt.win32.win32.x86_64}"
export BUILD_DIR="${BUILD_DIR:-${TEMP:-/tmp}/swt-ffm-build-win32}"
B="$(cygpath -u "$BUILD_DIR")"

"$TOOLS/ffm/build-win32.sh"

echo "== Cross check"
mkdir -p "$B/test-classes"
javac --release 25 -nowarn -d "$(cygpath -m "$B/test-classes")" -cp "$(cygpath -m "$B/swt-jni/classes")" \
	"$(cygpath -m "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMCrossCheckWin32.java")"
java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED -Djava.library.path="$(cygpath -m "$NATIVES")" \
	-cp "$(cygpath -m "$B/swt-jni/classes");$(cygpath -m "$B/test-classes")" org.eclipse.swt.tools.ffm.FFMCrossCheckWin32 \
	"$(cygpath -m "$TOOLS/ffm/report-win32/symbols.txt")"
