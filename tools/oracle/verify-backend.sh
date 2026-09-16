#!/usr/bin/env bash
# Verify that a visual-oracle backend is genuinely active, not silently
# falling back to native rendering.
#
# Usage: verify-backend.sh <backend-id>
#
# Builds the backend via build.sh, compiles its probe program, and runs it
# headless under Xvfb (Wayland variables unset, GDK pinned to X11, software
# GL). Exits zero only when the backend's activation marker was observed:
#
#   native        NATIVE-ACTIVE=true            (natives load, paint events fire)
#   skia-canvas   "External canvas activated."  (ExternalCanvasHandler log line,
#                 enabled by -Dorg.eclipse.swt.external.canvas:logActivation)
#   skija-proto   SKIJA-PROTO-ACTIVE=true       (Drawing hands out a real SkijaGC)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CACHE_ROOT="${ORACLE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/swt-visual-oracle}"
BUILD_TMP="$CACHE_ROOT/verify-probes"

die() { printf 'verify-backend.sh: %s\n' "$*" >&2; exit 1; }

[ $# -eq 1 ] || die "usage: verify-backend.sh <backend-id> (native | skia-canvas | skija-proto)"
backend="$1"
case "$backend" in
	native|skia-canvas|skija-proto) ;;
	*) die "unknown backend '$backend', expected native, skia-canvas or skija-proto" ;;
esac

command -v xvfb-run >/dev/null 2>&1 || die "xvfb-run not found, install xvfb"

classpath="$("$SCRIPT_DIR/build.sh" "$backend")" \
	|| die "building backend '$backend' failed"

mkdir -p "$BUILD_TMP/$backend"

case "$backend" in
	native) probe_src="$SCRIPT_DIR/probes/native/NativeProbe.java"; probe_class=NativeProbe ;;
	skia-canvas) probe_src="$SCRIPT_DIR/probes/skia-canvas/SkiaCanvasProbe.java"; probe_class=SkiaCanvasProbe ;;
	skija-proto) probe_src="$SCRIPT_DIR/probes/skija-proto/SkijaProtoProbe.java"; probe_class=SkijaProtoProbe ;;
esac

javac -nowarn -encoding UTF-8 -cp "$classpath" -d "$BUILD_TMP/$backend" "$probe_src" 1>&2 \
	|| die "compiling $probe_class failed"

java_cmd=(java --enable-native-access=ALL-UNNAMED)

if [ "$backend" = "skija-proto" ]; then
	fork_binaries="$CACHE_ROOT/checkouts/prototype-skija/binaries/org.eclipse.swt.gtk.linux.x86_64"
	[ -d "$fork_binaries" ] || die "fork binaries dir missing at $fork_binaries"
	java_library_path="$fork_binaries"
else
	worktree_root="$(cd "$SCRIPT_DIR/../.." && pwd)"
	java_library_path="$worktree_root/binaries/org.eclipse.swt.gtk.linux.x86_64"
fi

run_flags=(-Djava.library.path="$java_library_path")
if [ "$backend" = "skia-canvas" ]; then
	run_flags+=(-Dorg.eclipse.swt.external.canvas:logActivation=true)
fi

log_file="$BUILD_TMP/$backend/run.log"
env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1 \
	xvfb-run -a -s "-screen 0 1024x768x24" \
	timeout 60 "${java_cmd[@]}" "${run_flags[@]}" \
	-cp "$BUILD_TMP/$backend:$classpath" "$probe_class" > "$log_file" 2>&1 \
	|| { cat "$log_file" >&2; die "probe for '$backend' crashed or timed out"; }

echo "--- probe output ($backend) ---"
cat "$log_file"

case "$backend" in
	native)
		grep -q '^NATIVE-ACTIVE=true$' "$log_file" \
			|| die "native backend did not report NATIVE-ACTIVE=true"
		;;
	skia-canvas)
		if grep -q '^No external canvas factory found' "$log_file"; then
			die "skia canvas factory NOT found; backend silently falls back to native rendering (ServiceLoader resources missing?)"
		fi
		grep -q '^External canvas activated\.$' "$log_file" \
			|| die "expected 'External canvas activated.' marker not found"
		grep -q '^PAINTS=[1-9]' "$log_file" \
			|| die "no paint events observed on the SWT.SKIA canvas"
		;;
	skija-proto)
		grep -q '^PROBE-GC=org.eclipse.swt.graphics.SkijaGC$' "$log_file" \
			|| die "expected SkijaGC probe result not found"
		grep -q '^SKIJA-PROTO-ACTIVE=true$' "$log_file" \
			|| die "skija-proto did not report SKIJA-PROTO-ACTIVE=true"
		grep -q '^PAINTS=[1-9]' "$log_file" \
			|| die "no paint events observed on the skija-proto shell"
		;;
esac

echo "VERIFY-OK: backend '$backend' is genuinely active"
