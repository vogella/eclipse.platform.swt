#!/bin/bash
# Compiles the GTK variant of org.eclipse.swt with plain javac.
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
FRAG="$ROOT/binaries/org.eclipse.swt.gtk.linux.x86_64"
OUT="${1:-$ROOT/.build-gtk}"
rm -rf "$OUT"; mkdir -p "$OUT"
SRCDIRS=$(awk '/^source\.\. *=/{f=1} f{print; if ($0 !~ /\\$/) exit}' "$FRAG/build.properties" \
  | grep -oE '\.\.[^,\\]*' | sed 's/[[:space:]]*$//')
ARGFILE="$OUT/sources.txt"; : > "$ARGFILE"
while IFS= read -r d; do
  [ -n "$d" ] || continue
  abs="$FRAG/$d"
  [ -d "$abs" ] && find "$abs" -name '*.java' -printf '"%p"\n' >> "$ARGFILE"
done <<< "$SRCDIRS"
echo "$(wc -l < "$ARGFILE") source files"
javac -nowarn -proc:none -d "$OUT/classes" "@$ARGFILE"
