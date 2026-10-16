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
# Compares FFMWorkloadBench on the JNI and the FFM build of build-cocoa.sh, alternating fresh JVMs.
#
#   bench-cocoa.sh [runs]
#
# Run build-cocoa.sh jni and ffm with the same BUILD_DIR first. The FFM build gets no SWT library
# except libswt-awt on its library path. Needs a JDK 25 on the PATH; the shells open on screen.

set -euo pipefail

RUNS="${1:-10}"
TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$TOOLS/../.." && pwd)"
FRAGMENT="$REPO/binaries/org.eclipse.swt.cocoa.macosx.aarch64"
B="${BUILD_DIR:-${TMPDIR:-/tmp}/swt-ffm-build-cocoa}"
WORK="$B/bench"
rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/awt-only"
cp "$FRAGMENT"/libswt-awt-cocoa-*.jnilib "$WORK/awt-only/"
javac -nowarn -cp "$B/swt-jni/classes" -d "$WORK/classes" "$TOOLS/ffm/test/org/eclipse/swt/tools/ffm/FFMWorkloadBench.java"

one() {
	if [ "$1" = jni ]; then libs="$FRAGMENT"; else libs="$WORK/awt-only"; fi
	java -Diterations="${ITERATIONS:-30}" -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -Djava.library.path="$libs" \
		-cp "$WORK/classes:$B/swt-$1/classes" org.eclipse.swt.tools.ffm.FFMWorkloadBench | sed "s/^/$1 /"
}
for r in $(seq 1 "$RUNS"); do
	if (( r % 2 )); then one jni; one ffm; else one ffm; one jni; fi
done > "$WORK/results.txt"

python3 - "$WORK/results.txt" <<'PY'
import collections, statistics as st, sys
d = collections.defaultdict(list)
order = ['display', 'create', 'open', 'relayout', 'gc', 'dispose']
for line in open(sys.argv[1]):
    f = line.split()
    if len(f) == 4 and f[1].replace('-cpu', '') in order:
        d[(f[0], f[1])].append((float(f[2]), float(f[3])))
print(f"{'phase':14} {'cold jni':>9} {'cold ffm':>9} {'diff':>6}   {'warm jni':>9} {'warm ffm':>9} {'diff':>6}")
for p in sorted({k[1] for k in d}, key=lambda s: (s.endswith('-cpu'), order.index(s.replace('-cpu', '')))):
    cj, cf = (st.median(x[0] for x in d[(v, p)]) for v in ('jni', 'ffm'))
    wj, wf = (st.median(x[1] for x in d[(v, p)]) for v in ('jni', 'ffm'))
    print(f"{p:14} {cj:9.1f} {cf:9.1f} {100*(cf/cj-1):+5.0f}%   {wj:9.1f} {wf:9.1f} {100*(wf/wj-1):+5.0f}%")
PY
