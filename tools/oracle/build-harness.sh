#!/usr/bin/env bash
# T03 build recipe for the visual oracle harness.
#
# Compiles the plain-Java harness sources in harness/src against a real
# backend classpath obtained from build.sh (the merged T02 recipe, which is
# not modified here). Prints the harness classes directory on stdout;
# diagnostics go to stderr.
#
# Always recompiles: the harness is a few dozen files, a full javac run takes
# about a second, and skipping fingerprints avoids staleness bugs.
#
# Outputs live under the same cache root build.sh uses:
#   ${ORACLE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/swt-visual-oracle}
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SRC_DIR="$SCRIPT_DIR/harness/src"

CACHE_ROOT="${ORACLE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/swt-visual-oracle}"
WORKTREE_ID="$(printf '%s' "$(realpath "$REPO_ROOT")" | sha256sum | cut -c1-12)"
OUT_DIR="$CACHE_ROOT/harness/$WORKTREE_ID/classes"

die() { printf 'build-harness.sh: %s\n' "$*" >&2; exit 1; }

command -v java >/dev/null 2>&1 || die "java not found in PATH"
command -v javac >/dev/null 2>&1 || die "javac not found in PATH, install a JDK (21 or newer)"
[ -d "$SRC_DIR/org/eclipse/swt/visualoracle" ] || die "missing $SRC_DIR"

major="$(java -version 2>&1 | head -1 | sed 's/^[^"]*"\([0-9]*\).*/\1/')"
[ "$major" -ge 21 ] 2>/dev/null || die "JDK 21 or newer required, found: $(java -version 2>&1 | head -1)"

backend_cp="$("$SCRIPT_DIR/build.sh" native)" || die "building 'native' backend classpath failed"

# Remove stale classes before compiling. A deleted or renamed source otherwise
# leaves a ghost .class behind, and SpecimenCatalog discovers catalog modules by
# scanning compiled classes, so a ghost module reappears in the catalog and
# collides with the real one.
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
args_file="$(mktemp /tmp/opencode/oracle-t03/harness-javac-XXXXXX.args)"
trap 'rm -f "$args_file"' EXIT
mkdir -p "$(dirname "$args_file")"
find "$SRC_DIR" -name '*.java' | LC_ALL=C sort > "$args_file"
[ -s "$args_file" ] || die "no java sources found below $SRC_DIR"

printf 'compiling harness against native backend classpath\n' >&2
javac -nowarn -encoding UTF-8 -cp "$backend_cp" -d "$OUT_DIR" @"$args_file" 1>&2 \
	|| die "harness compilation failed"

printf '%s\n' "$OUT_DIR"
