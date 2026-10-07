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
# Verifies the generated GTK FFM bindings:
#
#   1. FFMCrossCheck compares JNI and FFM struct layouts, struct marshalling and a set of calls in one process,
#      on GTK3 and, with SWT_GTK4=1 against libswt-pi4, on GTK4 (FFMCrossCheck4, with real events injected through XTest).
#   2. The SWT JUnit tests run on the JNI build and on the FFM build, on GTK3 and with SWT_GTK4=1 on GTK4; the outcomes are
#      diffed. The FFM build runs without any SWT native library on the library path.
#
# Run generate-gtk.sh first. Needs a JDK 25, xvfb-run and ECLIPSE_HOME for the JUnit jars.
# Pass test class names to restrict step 2, or "--skip-junit". SWT_NATIVES overrides the directory of the native libraries.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
TESTS="$REPO/tests/org.eclipse.swt.tests"
FRAGMENT="${SWT_NATIVES:-$REPO/binaries/org.eclipse.swt.gtk.linux.x86_64}"
export BUILD_DIR="${BUILD_DIR:-/tmp/swt-ffm-build}"
: "${ECLIPSE_HOME:?set ECLIPSE_HOME to an Eclipse installation containing JUnit 5}"
B="$BUILD_DIR"

"$TOOLS/ffm/build-gtk.sh" jni
"$TOOLS/ffm/build-gtk.sh" ffm

headless() {
	env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 xvfb-run -a -s "-screen 0 1920x1080x24" \
		java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED -Djava.library.path="$FRAGMENT" "$@"
}

headless4() {
	env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 SWT_GTK4=1 xvfb-run -a -s "-screen 0 1920x1080x24" \
		java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED -Djava.library.path="$FRAGMENT" "$@"
}

echo "== Cross check"
mkdir -p "$B/test-classes"
javac --release 25 -nowarn -d "$B/test-classes" -cp "$B/swt-jni/classes" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMCrossCheck.java" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMCrossCheck4.java" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMCrossCheckTypes.java"
headless -cp "$B/swt-jni/classes:$B/test-classes" org.eclipse.swt.tools.ffm.FFMCrossCheck | grep -v '^  \(read\|write\) '

echo "== Cross check on GTK4"
headless4 -Dffm.inject="$TOOLS/ffm/test/xinject.py" -cp "$B/swt-jni/classes:$B/test-classes" org.eclipse.swt.tools.ffm.FFMCrossCheck | grep -v '^  \(read\|write\) '

echo "== GTK version selection without SWT libraries"
javac --release 25 -nowarn -d "$B/test-classes" -cp "$B/swt-ffm/classes" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMGtkVersionCheck.java"
SWT_NATIVES_NONE=/nonexistent
for gtk4 in 0 1; do
	env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 SWT_GTK4=$([ $gtk4 = 1 ] && echo 1 || echo 0) xvfb-run -a -s "-screen 0 1920x1080x24" \
		java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED -Djava.library.path="$SWT_NATIVES_NONE" \
		-cp "$B/swt-ffm/classes:$B/test-classes" org.eclipse.swt.tools.ffm.FFMGtkVersionCheck 2>&1 | grep -v 'DRI3\|MESA\|^Note:'
done

[ "${1:-}" = "--skip-junit" ] && exit 0

echo "== SWT JUnit tests"
plugins="$ECLIPSE_HOME/plugins"
junit=$(ls "$plugins"/junit-jupiter-{api,engine,params}_5.*.jar "$plugins"/junit-platform-{commons,engine,launcher,suite-api,suite-commons,suite-engine}_1.*.jar \
	"$plugins"/org.opentest4j_*.jar "$plugins"/org.apiguardian.api_*.jar "$plugins"/org.hamcrest_*.jar | grep -v '\.source_' | tr '\n' ':')
rm -rf "$B/swt-tests"
mkdir -p "$B/swt-tests"
find "$TESTS/JUnit Tests" "$TESTS/data/clipboard" "$TOOLS/ffm/test-stubs" -name '*.java' ! -path '*/performance/*' -printf '"%p"\n' > "$B/swt-tests-sources.txt"
echo "\"$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMTestRunner.java\"" >> "$B/swt-tests-sources.txt"
javac --release 25 -nowarn -encoding UTF-8 -d "$B/swt-tests" -cp "$B/swt-jni/classes:$junit" @"$B/swt-tests-sources.txt" 2>&1 | grep -v '^Note:' || true
(cd "$TESTS/JUnit Tests" && find . -type f ! -name '*.java' -print0 | cpio -0pdm --quiet "$B/swt-tests")

if [ $# -gt 0 ]; then
	classes=("$@")
else
	mapfile -t classes < <(cd "$B/swt-tests" && ls org/eclipse/swt/tests/junit/Test_org_eclipse_swt_{widgets,graphics,custom,accessibility,dnd,layout,events,program,printing}_*.class 2>/dev/null \
		| grep -v '\$' | grep -v 'Browser\|Clipboard' | sed -e 's/\.class$//' -e 's#/#.#g')
fi

for gtk in 3 4; do
	for mode in jni ffm; do
		natives="$FRAGMENT"
		[ $mode = ffm ] && natives=/nonexistent
		(cd "$TESTS" && env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 SWT_GTK4=$([ $gtk = 4 ] && echo 1 || echo 0) \
			xvfb-run -a -s "-screen 0 1920x1080x24" java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED -Djava.library.path="$natives" \
			-cp "$B/swt-$mode/classes:$B/swt-tests:$junit" org.eclipse.swt.tools.ffm.FFMTestRunner "$B/results-$mode-gtk$gtk.txt" "${classes[@]}" > "$B/log-$mode-gtk$gtk.txt" 2>&1) &
	done
	wait
	echo "== GTK$gtk"
	grep -h "^Results" "$B/log-jni-gtk$gtk.txt" "$B/log-ffm-gtk$gtk.txt"
	echo "== Tests whose outcome differs between JNI and FFM on GTK$gtk"
	diff <(cut -f1,2 "$B/results-jni-gtk$gtk.txt" | sed 's/\t\(\w*\).*/\t\1/') <(cut -f1,2 "$B/results-ffm-gtk$gtk.txt" | sed 's/\t\(\w*\).*/\t\1/') && echo "none"
done
