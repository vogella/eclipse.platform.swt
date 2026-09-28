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
# Verifies the Win32 FFM bindings. Runs in Git Bash.
#
#   1. FFMCrossCheckWin32 compares JNI and FFM symbols, struct layouts, struct marshalling and a set of calls in one process.
#   2. The SWT JUnit tests run on the JNI build and on the FFM build, one after the other since both share the
#      desktop; the outcomes are diffed. The FFM build runs without any SWT library on the library path.
#
# Run generate-win32.sh first. Needs a JDK 25, JUnit 5 and JSVG jars (JUNIT_REPO, default ~/.m2/repository) and the JNI DLLs
# of this checkout: SWT_NATIVES points to their directory (default: the win32 x86_64 fragment, which holds Git LFS
# pointers unless LFS is set up). Pass test class names to restrict step 2, or "--skip-junit".

set -euo pipefail
export MSYS2_ARG_CONV_EXCL='*'

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
NATIVES="${SWT_NATIVES:-$REPO/binaries/org.eclipse.swt.win32.win32.x86_64}"
export BUILD_DIR="${BUILD_DIR:-${TEMP:-/tmp}/swt-ffm-build-win32}"
B="$(cygpath -u "$BUILD_DIR")"
M2="${JUNIT_REPO:-$HOME/.m2/repository}"
w() { cygpath -m "$1"; }

"$TOOLS/ffm/build-win32.sh" jni
"$TOOLS/ffm/build-win32.sh" ffm

run() {
	java -XX:-CreateCoredumpOnCrash --enable-native-access=ALL-UNNAMED "$@"
}

echo "== Cross check"
mkdir -p "$B/test-classes"
javac --release 25 -nowarn -d "$(w "$B/test-classes")" -cp "$(w "$B/swt-jni/classes")" "$(w "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMCrossCheckWin32.java")"
run -Djava.library.path="$(w "$NATIVES")" -cp "$(w "$B/swt-jni/classes");$(w "$B/test-classes")" org.eclipse.swt.tools.ffm.FFMCrossCheckWin32 \
	"$(w "$TOOLS/ffm/report-win32/symbols.txt")" | grep -v '^  \(read\|write\) '

[ "${1:-}" = "--skip-junit" ] && exit 0

echo "== SWT JUnit tests"
find_jar() { ls "$M2"/$1/*/*.jar | grep -v 'source' | grep "/$2/" | tail -1; }
junit=""
for j in "org/junit/jupiter/junit-jupiter-api 5.14.4" "org/junit/jupiter/junit-jupiter-engine 5.14.4" "org/junit/jupiter/junit-jupiter-params 5.14.4" \
	"org/junit/platform/junit-platform-commons 1.14.4" "org/junit/platform/junit-platform-engine 1.14.4" "org/junit/platform/junit-platform-launcher 1.14.4" \
	"org/junit/platform/junit-platform-suite-api 1.14.4" "org/junit/platform/junit-platform-suite-commons 1.14.4" "org/junit/platform/junit-platform-suite-engine 1.14.4" \
	"org/opentest4j/opentest4j 1.3.0" "org/apiguardian/apiguardian-api 1.1.2" "org/hamcrest/hamcrest 3.0"; do
	junit+="$(w "$(find_jar $j)");"
done
TESTS="$REPO/tests/org.eclipse.swt.tests"
TESTS_WIN32="$REPO/tests/org.eclipse.swt.tests.win32"
rm -rf "$B/swt-tests"
mkdir -p "$B/swt-tests"
find "$TESTS/JUnit Tests" "$TESTS/data/clipboard" "$TESTS_WIN32/JUnit Tests" "$TOOLS/ffm/test-stubs" -name '*.java' ! -path '*/performance/*' \
	| cygpath -m -f - | sed 's/.*/"&"/' > "$B/swt-tests-sources.txt"
echo "\"$(w "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMTestRunner.java")\"" >> "$B/swt-tests-sources.txt"
javac --release 25 -nowarn -encoding UTF-8 -d "$(w "$B/swt-tests")" -cp "$(w "$B/swt-jni/classes");$junit" @"$(w "$B/swt-tests-sources.txt")" 2>&1 | grep -v '^Note:' || true
(cd "$TESTS/JUnit Tests" && find . -type f ! -name '*.java' -exec cp --parents {} "$B/swt-tests" \;)

# the SVG fragment, found through ServiceLoader as in an OSGi runtime
SVG="$REPO/bundles/org.eclipse.swt.svg"
jsvg="$(w "$(find_jar com/github/weisj/jsvg 2.2.0)")"
rm -rf "$B/swt-svg"
mkdir -p "$B/swt-svg"
javac --release 25 -nowarn -encoding UTF-8 -d "$(w "$B/swt-svg")" -cp "$(w "$B/swt-jni/classes");$jsvg" \
	$(find "$SVG/src" -name '*.java' | cygpath -m -f -)
cp -r "$SVG/resources/." "$B/swt-svg/"
svg="$(w "$B/swt-svg");$jsvg"

if [ $# -gt 0 ]; then
	classes=("$@")
else
	mapfile -t classes < <(cd "$B/swt-tests" && ls org/eclipse/swt/tests/junit/Test_org_eclipse_swt_{widgets,graphics,custom,browser,accessibility,dnd,layout,events,program,printing}_*.class \
		org/eclipse/swt/tests/win32/*.class org/eclipse/swt/tests/win32/widgets/*.class org/eclipse/swt/graphics/*Win32Tests.class 2>/dev/null \
		| grep -v '\$' | grep -v 'Clipboard\|AllWin32Tests\|SwtWin32TestUtil' | sed -e 's/\.class$//' -e 's#/#.#g')
fi

# the FFM build gets no SWT library, only the WebView2 loader that Edge loads itself
rm -rf "$B/ffm-natives"
mkdir -p "$B/ffm-natives"
cp "$NATIVES/WebView2Loader.dll" "$B/ffm-natives/" 2>/dev/null || true
for mode in jni ffm; do
	if [ $mode = jni ]; then libs="$(w "$NATIVES")"; else libs="$(w "$B/ffm-natives")"; fi
	echo "Running ${#classes[@]} test classes on the $mode build"
	(cd "$TESTS" && run -Djava.library.path="$libs" -cp "$(w "$B/swt-$mode/classes");$svg;$(w "$B/swt-tests");$junit" \
		org.eclipse.swt.tools.ffm.FFMTestRunner "$(w "$B/results-$mode.txt")" "${classes[@]}" > "$B/log-$mode.txt" 2>&1) || true
done

grep -h "^Results" "$B/log-jni.txt" "$B/log-ffm.txt" || true
echo "== Tests whose outcome differs between JNI and FFM"
diff <(cut -f1,2 "$B/results-jni.txt" | sed 's/\t\(\w*\).*/\t\1/') <(cut -f1,2 "$B/results-ffm.txt" | sed 's/\t\(\w*\).*/\t\1/') && echo "none"
