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
#   1. FFMCrossCheck compares JNI and FFM struct layouts, struct marshalling and a set of calls in one process.
#   2. The SWT JUnit tests run on the JNI build and on the FFM build; the outcomes are diffed.
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

echo "== Cross check"
mkdir -p "$B/test-classes"
javac --release 25 -nowarn -d "$B/test-classes" -cp "$B/swt-jni/classes" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMCrossCheck.java"
headless -cp "$B/swt-jni/classes:$B/test-classes" org.eclipse.swt.tools.ffm.FFMCrossCheck | grep -v '^  \(read\|write\) '

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

for mode in jni ffm; do
	(cd "$TESTS" && headless -cp "$B/swt-$mode/classes:$B/swt-tests:$junit" org.eclipse.swt.tools.ffm.FFMTestRunner "$B/results-$mode.txt" "${classes[@]}" > "$B/log-$mode.txt" 2>&1) &
done
wait

grep -h "^Results" "$B/log-jni.txt" "$B/log-ffm.txt"
echo "== Tests whose outcome differs between JNI and FFM"
diff <(cut -f1,2 "$B/results-jni.txt" | sed 's/\t\(\w*\).*/\t\1/') <(cut -f1,2 "$B/results-ffm.txt" | sed 's/\t\(\w*\).*/\t\1/') && echo "none"
