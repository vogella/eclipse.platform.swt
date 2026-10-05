#!/usr/bin/env bash
# Compile-checks the harness for one platform fragment on any machine.
#
# Usage: compile-check.sh <fragment>     for example gtk.linux.x86_64, win32.win32.x86_64
#
# Compiles the SWT sources that fragment's build.properties lists (no natives
# needed), then harness/src plus the matching harness/src-<gtk|cocoa|win32>
# folder against them. Nothing is run. Exits non-zero on a compile error.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=platform.sh
. "$SCRIPT_DIR/platform.sh"

die() { printf 'compile-check.sh: %s\n' "$*" >&2; exit 1; }

[ $# -eq 1 ] || die "usage: compile-check.sh <fragment>, for example gtk.linux.x86_64"
fragment="$1"
ws="${fragment%%.*}"
case "$ws" in gtk|cocoa|win32) ;; *) die "unknown fragment '$fragment'" ;; esac
platform_src="$SCRIPT_DIR/harness/src-$ws"
[ -d "$platform_src" ] || die "missing $platform_src"

swt_classes="$("$SCRIPT_DIR/build.sh" fragment-classes "$fragment")" || die "compiling SWT for $fragment failed"

out_dir="$(mktemp -d "${TMPDIR:-/tmp}/oracle-compile-check-XXXXXX")"
trap 'rm -rf "$out_dir"' EXIT
args_file="$out_dir/javac.args"
find "$SCRIPT_DIR/harness/src" "$platform_src" -name '*.java' | LC_ALL=C sort \
	| while IFS= read -r f; do printf '"%s"\n' "$f"; done > "$args_file"

printf 'compile-check.sh: compiling harness (%s) against %s\n' "$ws" "$fragment" >&2
javac -nowarn -encoding UTF-8 -cp "$swt_classes" -d "$out_dir/classes" @"$args_file" 1>&2 \
	|| die "harness does not compile against $fragment"
printf 'compile-check.sh: OK, harness compiles against %s\n' "$fragment" >&2
