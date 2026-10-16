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
# Verifies the Cocoa FFM bindings on macOS. Builds both modes with build-cocoa.sh, then runs the SWT JUnit tests on
# the JNI build and on the FFM build, one after the other since both share the desktop; the outcomes are diffed.
# The FFM build runs without any SWT native library on the library path unless SWT_NATIVES is set.
#
# Run generate-cocoa.sh first. Needs a JDK 25 on the PATH and JUnit 5 in ECLIPSE_HOME (default
# ~/dev/eclipse-SDK-Vogella-20261007-1208-macosx-cocoa-aarch64). Pass test class names to restrict the run, or "--skip-junit".
# SWT_NATIVES points to the directory of the JNI libraries (default: the cocoa aarch64 fragment).

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
TESTS="$REPO/tests/org.eclipse.swt.tests"
TESTS_COCOA="$REPO/tests/org.eclipse.swt.tests.cocoa"
FRAGMENT="$REPO/binaries/org.eclipse.swt.cocoa.macosx.aarch64"
export BUILD_DIR="${BUILD_DIR:-${TMPDIR:-/tmp}/swt-ffm-build-cocoa}"
B="$BUILD_DIR"
ECLIPSE_HOME="${ECLIPSE_HOME:-$HOME/dev/eclipse-SDK-Vogella-20261007-1208-macosx-cocoa-aarch64}"

"$TOOLS/ffm/build-cocoa.sh" jni
"$TOOLS/ffm/build-cocoa.sh" ffm

[ "${1:-}" = "--skip-junit" ] && exit 0

echo "== SWT JUnit tests"
plugins="$ECLIPSE_HOME/plugins"
[ -d "$plugins" ] || plugins="$ECLIPSE_HOME/Eclipse.app/Contents/Eclipse/plugins"
junit=$(ls "$plugins"/junit-jupiter-{api,engine,params}_5.*.jar "$plugins"/junit-platform-{commons,engine,launcher,suite-api,suite-commons,suite-engine}_1.*.jar \
	"$plugins"/org.opentest4j_*.jar "$plugins"/org.apiguardian.api_*.jar "$plugins"/org.hamcrest_*.jar | grep -v '\.source_' | tr '\n' ':')
rm -rf "$B/swt-tests"
mkdir -p "$B/swt-tests"

test_dirs=("$TESTS/JUnit Tests" "$TESTS/data/clipboard" "$TOOLS/ffm/test-stubs")
[ -d "$TESTS_COCOA/JUnit Tests" ] && test_dirs+=("$TESTS_COCOA/JUnit Tests")
# AllCocoaTests is a JUnit 4 suite, the runner picks the classes itself
find "${test_dirs[@]}" -name '*.java' ! -path '*/performance/*' ! -name AllCocoaTests.java | sed 's/.*/"&"/' > "$B/swt-tests-sources.txt"
echo "\"$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMTestRunner.java\"" >> "$B/swt-tests-sources.txt"
javac --release 25 -nowarn -encoding UTF-8 -d "$B/swt-tests" -cp "$B/swt-jni/classes:$junit" @"$B/swt-tests-sources.txt" > "$B/javac-tests.log" 2>&1 \
	|| { grep -v '^Note:' "$B/javac-tests.log"; echo "test-cocoa.sh: compiling the tests failed" >&2; exit 1; }
for dir in "$TESTS/JUnit Tests" "$TESTS_COCOA/JUnit Tests"; do
	[ -d "$dir" ] && (cd "$dir" && find . -type f ! -name '*.java' -print0 | cpio -0pdm --quiet "$B/swt-tests")
done

if [ $# -gt 0 ]; then
	classes=("$@")
else
	classes=()
	while IFS= read -r class; do classes+=("$class"); done < <(cd "$B/swt-tests" && ls org/eclipse/swt/tests/junit/Test_org_eclipse_swt_{widgets,graphics,custom,browser,accessibility,dnd,layout,events,program,printing}_*.class \
		org/eclipse/swt/tests/cocoa/*.class 2>/dev/null | grep -v '\$' | grep -v 'Clipboard' | sed -e 's/\.class$//' -e 's#/#.#g')
fi

for mode in jni ffm; do
	# the JNI build loads the libraries of this checkout, the FFM build none
	if [ $mode = jni ]; then libs="${SWT_NATIVES:-$FRAGMENT}"; else libs="${SWT_NATIVES:-/nonexistent}"; fi
	echo "Running ${#classes[@]} test classes on the $mode build"
	(cd "$TESTS" && java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -Djava.library.path="$libs" \
		-cp "$B/swt-$mode/classes:$B/swt-tests:$junit" org.eclipse.swt.tools.ffm.FFMTestRunner "$B/results-$mode.txt" "${classes[@]}" \
		> "$B/log-$mode.txt" 2>&1) || true
done

for mode in jni ffm; do
	[ -s "$B/results-$mode.txt" ] || { echo "test-cocoa.sh: no results from the $mode build, see $B/log-$mode.txt" >&2; exit 1; }
done
grep -h "^Results" "$B/log-jni.txt" "$B/log-ffm.txt" || true
echo "== Tests whose outcome differs between JNI and FFM"
# compare the outcome only, not the message
outcomes() { awk -F'\t' '{ split($2, o, " "); print $1 "\t" o[1] }' "$1" | sort; }
diff <(outcomes "$B/results-jni.txt") <(outcomes "$B/results-ffm.txt") && echo "none"
