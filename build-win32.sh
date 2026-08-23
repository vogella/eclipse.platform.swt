#!/bin/bash
# Compiles the win32 variant of the org.eclipse.swt bundle with plain javac.
# The win32 Java sources are pure Java, so this works on Linux; only the JNI natives are Windows-only.
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
FRAG="$ROOT/binaries/org.eclipse.swt.win32.win32.x86_64"
OUT="${1:-$ROOT/.build-win32}"
rm -rf "$OUT"; mkdir -p "$OUT"

SRCDIRS=$(awk '/^source\.\. *=/{f=1} f{print; if ($0 !~ /\\$/) exit}' "$FRAG/build.properties" \
  | grep -oE '\.\.[^,\\]*' | sed 's/[[:space:]]*$//')

ARGFILE="$OUT/sources.txt"
: > "$ARGFILE"
while IFS= read -r d; do
  [ -n "$d" ] || continue
  abs="$FRAG/$d"
  [ -d "$abs" ] && find "$abs" -name '*.java' -printf '"%p"\n' >> "$ARGFILE"
done <<< "$SRCDIRS"

echo "$(wc -l < "$ARGFILE") source files"
javac -nowarn -proc:none -d "$OUT/classes" "@$ARGFILE"

# Optional: also compile the win32 test fragment against the classes just built.
# Only the win32 fragment is listed explicitly; javac pulls referenced helpers from the
# common test bundle via -sourcepath. The common bundle as a whole needs org.eclipse.test,
# which is not resolvable outside a Tycho build.
if [ "${WITH_TESTS:-0}" = "1" ]; then
  m2() { find "$HOME/.m2/repository/$1" -name "$2" 2>/dev/null | head -1; }
  CP="$OUT/classes"
  for j in \
    "$(m2 org/junit/jupiter/junit-jupiter-api 'junit-jupiter-api-5.9.2.jar')" \
    "$(m2 org/junit/jupiter/junit-jupiter-params 'junit-jupiter-params-5.9.2.jar')" \
    "$(m2 org/junit/platform 'junit-platform-suite-api-*.jar')" \
    "$(m2 org/opentest4j 'opentest4j-*.jar')" \
    "$(m2 org/apiguardian 'apiguardian-api-*.jar')" \
    "$(m2 junit/junit/4.13.2 'junit-4.13.2.jar')" \
    "$(m2 org/hamcrest 'hamcrest-core-1.3.jar')"; do
    [ -n "$j" ] && CP="$CP:$j"
  done

  SP="$ROOT/tests/org.eclipse.swt.tests/JUnit Tests"
  TARG="$OUT/testsources.txt"; : > "$TARG"
  find "$ROOT/tests/org.eclipse.swt.tests.win32/JUnit Tests" -name '*.java' -printf '"%p"\n' >> "$TARG"
  echo "$(wc -l < "$TARG") win32 test source files"
  javac -nowarn -proc:none -cp "$CP" -sourcepath "$SP" -d "$OUT/testclasses" "@$TARG"
fi

# Optional: re-compile with ecj and report problems in the files this branch changed.
# javac ignores the Eclipse-only problem severities (unused imports and similar) that make
# the Tycho build fail, and the bundle has pre-existing warnings elsewhere, so the report is
# scoped to files that differ from the merge base.
if [ "${WITH_ECJ:-0}" = "1" ]; then
  ECJ=$(find "$HOME/.m2/repository/p2/osgi/bundle/org.eclipse.jdt.core.compiler.batch" -name '*.jar' 2>/dev/null | sort | tail -1)
  if [ -z "$ECJ" ]; then
    echo "ecj jar not found, skipping"
  else
    BASE="${ECJ_BASE:-$(git -C "$ROOT" merge-base HEAD master 2>/dev/null)}"
    CHANGED=$(git -C "$ROOT" diff --name-only "$BASE" 2>/dev/null; git -C "$ROOT" status --porcelain | awk '{print $2}')
    java -jar "$ECJ" -properties "$ROOT/bundles/org.eclipse.swt/.settings/org.eclipse.jdt.core.prefs" \
      -source 21 -target 21 -proceedOnError:Fatal -d "$OUT/ecjclasses" "@$ARGFILE" > "$OUT/ecj.txt" 2>&1
    STATUS=$?
    # Keep only problems whose file is one this branch touched.
    python3 - "$OUT/ecj.txt" <<'PY' $CHANGED
import sys, re, os
report, changed = sys.argv[1], {os.path.basename(p) for p in sys.argv[2:]}
blocks, cur = [], []
for line in open(report, errors="replace"):
    if re.match(r"^\d+\. (ERROR|WARNING) in ", line):
        if cur: blocks.append(cur)
        cur = [line]
    elif cur:
        cur.append(line)
if cur: blocks.append(cur)
hits = [b for b in blocks if os.path.basename(b[0].split(" in ",1)[1].split(" (")[0]) in changed]
for b in hits: print("".join(b).rstrip())
print(f"ecj: {len(hits)} problem(s) in changed files, {len(blocks)} in the bundle overall")
PY
    echo "ecj compile status=$STATUS"
  fi
fi
