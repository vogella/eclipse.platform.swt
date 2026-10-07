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
# Runs the JNI and the FFM build through every combination of installed GTK versions (both, GTK3 only,
# GTK4 only, neither) and SWT_GTK4 (unset, 0, 1) and fails if their outcomes differ. A missing GTK library is
# simulated by a stub of that name ahead on LD_LIBRARY_PATH whose own dependency is missing, so dlopen fails.
#
# Needs a JDK 25 and gcc. SWT_NATIVES overrides the directory of the native libraries.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
FRAGMENT="${SWT_NATIVES:-$REPO/binaries/org.eclipse.swt.gtk.linux.x86_64}"
export BUILD_DIR="${BUILD_DIR:-/tmp/swt-ffm-build}"
B="$BUILD_DIR"

"$TOOLS/ffm/build-gtk.sh" jni
"$TOOLS/ffm/build-gtk.sh" ffm

mkdir -p "$B/select" "$B/stubs/missing"
javac --release 25 -nowarn -d "$B/select" -cp "$B/swt-jni/classes" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMGtkSelectCheck.java"
echo 'int x;' > "$B/stubs/stub.c"
gcc -shared -o "$B/stubs/missing/libswtmissing.so" "$B/stubs/stub.c"
for stub in "no3 libgtk-3.so.0" "no4 libgtk-4.so.1"; do
	set -- $stub
	mkdir -p "$B/stubs/$1"
	gcc -shared -o "$B/stubs/$1/$2" "$B/stubs/stub.c" -Wl,-soname,"$2" -Wl,--no-as-needed -L"$B/stubs/missing" -lswtmissing
done
rm "$B/stubs/missing/libswtmissing.so"

run() { # mode stubpath gtk4
	local natives="$FRAGMENT" env=()
	[ "$1" = ffm ] && natives=/nonexistent
	[ "$3" != unset ] && env=(SWT_GTK4="$3")
	env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE -u SWT_GTK4 GDK_BACKEND=x11 "${env[@]}" LD_LIBRARY_PATH="$2" \
		java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED -Djava.library.path="$natives" \
		-cp "$B/swt-$1/classes:$B/select" org.eclipse.swt.tools.ffm.FFMGtkSelectCheck 2>&1 \
		| grep '^OUTCOME\|^MESSAGE\|^SWT OS.java Error' | sed -e 's/^MESSAGE.*/MESSAGE/' | tr '\n' '|'
}

failures=0
printf '%-10s %-6s %-60s %s\n' installed SWT_GTK4 JNI FFM
for installed in both gtk3 gtk4 neither; do
	case $installed in
		both) stubs="";;
		gtk3) stubs="$B/stubs/no4";;
		gtk4) stubs="$B/stubs/no3";;
		neither) stubs="$B/stubs/no3:$B/stubs/no4";;
	esac
	for gtk4 in unset 0 1; do
		jni=$(run jni "$stubs" $gtk4)
		ffm=$(run ffm "$stubs" $gtk4)
		status=""
		[ "$jni" = "$ffm" ] || { status="DIFFERENT"; failures=$((failures + 1)); }
		printf '%-10s %-6s %-60s %s %s\n' $installed $gtk4 "$jni" "$ffm" "$status"
	done
done
echo "GTK selection: $failures differences"
[ $failures = 0 ]
