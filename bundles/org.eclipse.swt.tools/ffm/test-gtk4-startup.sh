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
# Starts SWT with SWT_GTK4=1 on the FFM build (no SWT library on the library path) and on the JNI build, headless,
# and creates a Display, a Shell with a Table and a Tree and disposes them. The FFM struct sizes of the cell renderers
# are compared with the JNI ones by test-gtk4-custom.sh.
#
# Needs a JDK 25, clang, gcc with the GTK4 headers and xvfb-run.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
export BUILD_DIR="${BUILD_DIR:-/tmp/swt-ffm-build}"
B="$BUILD_DIR"
FRAGMENT="${SWT_NATIVES:-$(cd "$TOOLS/../.." && pwd)/binaries/org.eclipse.swt.gtk.linux.x86_64}"

"$TOOLS/ffm/build-gtk.sh" jni
"$TOOLS/ffm/build-gtk.sh" ffm

mkdir -p "$B/gtk4-startup"
javac --release 25 -nowarn -d "$B/gtk4-startup" -cp "$B/swt-ffm/classes" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMGtk4StartupCheck.java"

CHECK=org.eclipse.swt.tools.ffm.FFMGtk4StartupCheck
run() {
	SWT_GTK4=1 env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 xvfb-run -a \
		java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED "$@" 2>&1 | tee "$B/gtk4-startup/last.txt" | grep -v "^MESA\|^libEGL\|CRITICAL\|^\$"
	grep -q "^PASSED GTK4" "$B/gtk4-startup/last.txt"
}
echo "== FFM, no SWT libraries"
run -Djava.library.path=/nonexistent -cp "$B/swt-ffm/classes:$B/gtk4-startup" $CHECK
echo "== JNI"
run -Djava.library.path="$FRAGMENT" -cp "$B/swt-jni/classes:$B/gtk4-startup" $CHECK
