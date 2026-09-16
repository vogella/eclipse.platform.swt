#!/usr/bin/env bash
# T01 capture strategy spike: rebuilds and reruns everything from a clean state.
#
# Compares three capture strategies (Control.print, GC.copyArea, X11 root grab)
# on Linux/GTK at zoom 100 and zoom 200, on the native backend and the
# SWT.SKIA canvas backend. Writes evidence PNGs to
# docs/visual-oracle/adr/evidence/ and prints a summary on stdout.
#
# Scratch output lives in /tmp/opencode/oracle-t01/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRATCH="${ORACLE_SPIKE_SCRATCH:-/tmp/opencode/oracle-t01}"
EVIDENCE="$ROOT/docs/visual-oracle/adr/evidence"
CLASSES="$SCRATCH/classes"
OUT="$SCRATCH/out"
SUMMARY="$OUT/summary.txt"

die() { printf 'run-spike.sh: %s\n' "$*" >&2; exit 1; }
info() { printf 'run-spike.sh: %s\n' "$*" >&2; }

command -v xvfb-run >/dev/null 2>&1 || die "xvfb-run not found, install xvfb"
command -v import >/dev/null 2>&1 || die "ImageMagick 'import' not found"

rm -rf "$CLASSES" "$OUT"
mkdir -p "$CLASSES" "$OUT" "$EVIDENCE"
find "$EVIDENCE" -name '*.png' -delete

CP_NATIVE="$("$ROOT"/tools/oracle/build.sh native)" || die "building native backend failed"
CP_SKIA="$("$ROOT"/tools/oracle/build.sh skia-canvas)" || die "building skia-canvas backend failed"

info "compiling spike"
javac -nowarn -encoding UTF-8 -cp "$CP_NATIVE" -d "$CLASSES" \
	"$ROOT"/tools/oracle-spike/src/CaptureSpike.java || die "compiling CaptureSpike failed"

NATIVE_LIB="$ROOT/binaries/org.eclipse.swt.gtk.linux.x86_64"

runjava() {
	env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1 \
		xvfb-run -a -s "-screen 0 1600x1200x24" timeout 300 \
		java --enable-native-access=ALL-UNNAMED "$@"
}

: > "$SUMMARY"

# Splits trailing arguments: -D/-X go to the JVM, everything else to the program.
split_args() {
	jvm_args=(); app_args=()
	local a
	for a in "$@"; do
		case "$a" in
			-D*|-X*|--enable-*) jvm_args+=("$a") ;;
			*) app_args+=("$a") ;;
		esac
	done
}

scenario() {
	local name="$1" cp="$2"; shift 2
	split_args "$@"
	info "running scenario $name"
	{
		printf '=== scenario %s ===\n' "$name"
		runjava -Djava.library.path="$NATIVE_LIB" ${jvm_args[@]+"${jvm_args[@]}"} \
			-cp "$CLASSES:$cp" CaptureSpike full "$OUT/$name" "$name" ${app_args[@]+"${app_args[@]}"}
	} | tee -a "$SUMMARY"
}

det_run() {
	local dir="$1" cp="$2"; shift 2
	split_args "$@"
	mkdir -p "$dir"
	for i in 1 2 3 4 5; do
		runjava -Djava.library.path="$NATIVE_LIB" ${jvm_args[@]+"${jvm_args[@]}"} \
			-cp "$CLASSES:$cp" CaptureSpike single "$dir" "r$i" ${app_args[@]+"${app_args[@]}"}
	done
}

det_verdicts() {
	local file="$1"
	local s sha n
	for s in print copyarea xgrab; do
		n="$(awk -v s="strategy=$s" '$2==s {print $3}' "$file" | sort -u | wc -l)"
		sha="$(awk -v s="strategy=$s" '$2==s {print $3}' "$file" | sort -u | head -1)"
		if [ "$n" -eq 1 ]; then
			printf 'DET-CROSS %s=IDENTICAL sha256=%s\n' "$s" "${sha:-none}"
		else
			printf 'DET-CROSS %s=MISMATCH (%s distinct hashes over 5 processes)\n' "$s" "$n"
		fi
	done
}

scenario native-z100 "$CP_NATIVE"
scenario native-z200 "$CP_NATIVE" -Dswt.autoScale=200
scenario skia-z100 "$CP_SKIA" --skia

info "cross-process determinism runs (5 processes each)"
det_run "$OUT/det-native-z100" "$CP_NATIVE" > "$OUT/det-native-z100.txt"
det_run "$OUT/det-skia-z100" "$CP_SKIA" --skia > "$OUT/det-skia-z100.txt"
{
	printf '=== cross-process determinism, native-z100 ===\n'
	det_verdicts "$OUT/det-native-z100.txt"
	printf '=== cross-process determinism, skia-z100 ===\n'
	det_verdicts "$OUT/det-skia-z100.txt"
} | tee -a "$SUMMARY"

results="$(grep -c '^RESULT ' "$SUMMARY" || true)"
[ "$results" -eq 15 ] || die "expected 15 RESULT lines, got $results; see $SUMMARY"

# Evidence: every capture produced by the three scenarios. The native scenarios
# have no Skia canvas, so only skia-z100 contributes skiacanvas images.
for d in native-z100 native-z200 skia-z100; do
	cp "$OUT/$d"/*.png "$EVIDENCE"/
done
evidence_count="$(find "$EVIDENCE" -name '*.png' | wc -l)"
[ "$evidence_count" -le 20 ] || die "evidence set grew to $evidence_count files, cap is 20"

printf '\nSPIKE-OK: %s RESULT lines, %s evidence PNGs in docs/visual-oracle/adr/evidence/\n' "$results" "$evidence_count"
cat "$SUMMARY"
