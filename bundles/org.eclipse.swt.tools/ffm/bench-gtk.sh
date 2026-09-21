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
# Compares FFMWorkloadBench on an FFM SWT jar and on its JNI twin, alternating fresh JVMs under Xvfb.
#
#   bench-gtk.sh <swt-gtk-fragment.jar> <swt checkout apply-ffm.sh ran in> [runs]
#
# The twin is the same jar with the rewritten PI classes compiled from their committed, unrewritten
# sources, so both sides share every other class and the native libraries. Needs a JDK 25 and Xvfb.

set -euo pipefail

JAR="$(realpath "$1")"; CHECKOUT="$(realpath "$2")"; RUNS="${3:-10}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${BENCH_DIR:-/tmp/swt-ffm-bench}"
rm -rf "$WORK"; mkdir -p "$WORK/src" "$WORK/twin" "$WORK/bench"

(cd "$CHECKOUT" && git diff --name-only -z -- '*.java') | while IFS= read -r -d '' file; do
	(cd "$CHECKOUT" && git show "HEAD:$file") > "$WORK/src/$(basename "$file")"
done
ls "$WORK"/src/*.java >/dev/null
javac --release 25 -nowarn -encoding UTF-8 -cp "$JAR" -d "$WORK/twin" "$WORK"/src/*.java 2>&1 | grep -v '^Note:' || true
cp "$JAR" "$WORK/swt-ffm.jar"; cp "$JAR" "$WORK/swt-jni.jar"
(cd "$WORK/twin" && zip -q -r "$WORK/swt-jni.jar" .)
javac -nowarn -cp "$JAR" -d "$WORK/bench" "$HERE/test/org/eclipse/swt/tools/ffm/FFMWorkloadBench.java"

Xvfb :98 -screen 0 1920x1200x24 -nolisten tcp >/dev/null 2>&1 & XVFB=$!
trap 'kill $XVFB' EXIT
sleep 1
one() {
	env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE DISPLAY=:98 GDK_BACKEND=x11 taskset -c 4-7 \
		java -Diterations=30 --enable-native-access=ALL-UNNAMED -cp "$WORK/bench:$WORK/swt-$1.jar" \
		org.eclipse.swt.tools.ffm.FFMWorkloadBench | sed "s/^/$1 /"
}
for r in $(seq 1 "$RUNS"); do
	if (( r % 2 )); then one jni; one ffm; else one ffm; one jni; fi
done > "$WORK/results.txt"

python3 - "$WORK/results.txt" <<'PY'
import collections, statistics as st, sys
d = collections.defaultdict(list)
for line in open(sys.argv[1]):
    v, p, cold, warm = line.split()
    d[(v, p)].append((float(cold), float(warm)))
order = ['display', 'create', 'open', 'relayout', 'gc', 'dispose']
print(f"{'phase':14} {'cold jni':>9} {'cold ffm':>9} {'diff':>6}   {'warm jni':>9} {'warm ffm':>9} {'diff':>6}")
for p in sorted({k[1] for k in d}, key=lambda s: (s.endswith('-cpu'), order.index(s.replace('-cpu', '')))):
    cj, cf = (st.median(x[0] for x in d[(v, p)]) for v in ('jni', 'ffm'))
    wj, wf = (st.median(x[1] for x in d[(v, p)]) for v in ('jni', 'ffm'))
    print(f"{p:14} {cj:9.1f} {cf:9.1f} {100*(cf/cj-1):+5.0f}%   {wj:9.1f} {wf:9.1f} {100*(wf/wj-1):+5.0f}%")
PY
