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
# Verifies the hand written GTK4 ports (SwtFixed, SwtScaledPaintable, content providers, macros) against
# the JNI natives of libswt-pi4 under SWT_GTK4=1, headless:
#
#   1. probe-gtk4-custom.c prints the struct layouts that the generated constants of the Java code have to match
#   2. FFMGtk4CustomCheck compares both implementations in one process and records what the JNI side observed
#   3. the same check replays that record on the FFM build without any SWT library on the library path
#
# Needs a JDK 25, gcc with the GTK4 headers and xvfb-run. SWT_NATIVES overrides the directory of the native libraries.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
FRAGMENT="${SWT_NATIVES:-$REPO/binaries/org.eclipse.swt.gtk.linux.x86_64}"
export BUILD_DIR="${BUILD_DIR:-/tmp/swt-ffm-build}"
B="$BUILD_DIR"

"$TOOLS/ffm/build-gtk.sh" jni
"$TOOLS/ffm/build-gtk.sh" ffm

mkdir -p "$B/gtk4-custom"
gcc "$TOOLS/ffm/test/probe-gtk4-custom.c" -o "$B/gtk4-custom/probe" $(pkg-config --cflags --libs gtk4)
"$B/gtk4-custom/probe" > "$B/gtk4-custom/probe.txt"
javac --release 25 -nowarn -d "$B/gtk4-custom" -cp "$B/swt-jni/classes" "$TOOLS/ffm/test/org/eclipse/swt/internal/ffm/FFMGtk4CustomCheck.java"

headless() {
	SWT_GTK4=1 env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 xvfb-run -a \
		java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED "$@" 2>&1 | grep -v "^MESA\|^Note: you\|^libEGL\|CRITICAL\|^\$"
}

CHECK=org.eclipse.swt.internal.ffm.FFMGtk4CustomCheck
echo "== JNI and FFM side by side"
headless -Djava.library.path="$FRAGMENT" -cp "$B/swt-jni/classes:$B/gtk4-custom" $CHECK "$B/gtk4-custom/probe.txt" jni "$B/gtk4-custom/trace.txt"
echo "== FFM replay without SWT libraries"
headless -Djava.library.path=/nonexistent -cp "$B/swt-ffm/classes:$B/gtk4-custom" $CHECK "$B/gtk4-custom/probe.txt" ffm-only "$B/gtk4-custom/trace.txt"
