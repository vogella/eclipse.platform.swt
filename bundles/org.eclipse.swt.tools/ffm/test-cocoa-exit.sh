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
# Checks that the FFM build of Cocoa SWT exits cleanly when main returns after Display.dispose().
# Native code that calls an upcall stub after the VM is destroyed crashes with SIGBUS.
#
#   test-cocoa-exit.sh [runs]
#
# Needs a JDK 25 on the PATH and the libswt-awt-cocoa library of the fragment.

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
RUNS="${1:-5}"
BUILD_DIR="${BUILD_DIR:-${TMPDIR:-/tmp}/swt-ffm-cocoa-exit}"
export BUILD_DIR

"$HERE/build-cocoa.sh" ffm
mkdir -p "$BUILD_DIR/check" "$BUILD_DIR/natives"
cp "$REPO"/binaries/org.eclipse.swt.cocoa.macosx.aarch64/libswt-awt-cocoa-*.jnilib "$BUILD_DIR/natives/"
javac -nowarn -cp "$BUILD_DIR/swt-ffm/classes" -d "$BUILD_DIR/check" "$HERE/test/org/eclipse/swt/tools/ffm/FFMExitCheck.java"

cd "$BUILD_DIR"
rm -f hs_err_pid*.log
failed=0
for i in $(seq "$RUNS"); do
	# the crash happens while the process exits and java still reports status 0
	if ! java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -Djava.library.path="$BUILD_DIR/natives" \
			-cp "$BUILD_DIR/check:$BUILD_DIR/swt-ffm/classes" org.eclipse.swt.tools.ffm.FFMExitCheck > "$BUILD_DIR/run$i.log" 2>&1; then
		failed=$((failed + 1))
	elif grep -q 'fatal error has been detected' "$BUILD_DIR/run$i.log"; then
		failed=$((failed + 1))
	fi
done
echo "$failed of $RUNS runs failed"
[ "$failed" -eq 0 ]
