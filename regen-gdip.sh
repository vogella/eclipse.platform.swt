#!/bin/bash
# Regenerates the JNI bindings for org.eclipse.swt.internal.gdip.Gdip from Gdip.java,
# using the repo's own JNI generator. Runs on Linux; no Windows toolchain needed.
#
# The generator does not emit the EPL copyright header, so each existing file's first
# 13 lines are preserved and the generated body is spliced underneath.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
LIB="$ROOT/bundles/org.eclipse.swt/Eclipse SWT PI/win32/library"
SRCROOT="$ROOT/bundles/org.eclipse.swt/Eclipse SWT PI/win32/"
BUILD="$ROOT/.build-win32"
TMP="$BUILD/gdipgen"

[ -d "$BUILD/classes" ] || { echo "run ./build-win32.sh first"; exit 1; }

# 1. Compile the JNI generator and the small headless driver.
mkdir -p "$BUILD/toolsclasses" "$BUILD/gen"
cat > "$BUILD/gen/GenJni.java" <<'EOF'
import org.eclipse.swt.tools.internal.JNIGeneratorApp;
public class GenJni {
	public static void main(String[] args) {
		JNIGeneratorApp app = new JNIGeneratorApp();
		app.setMainClassName(args[0], args[1], args[2]);
		app.generate();
	}
}
EOF

CP="$BUILD/toolsclasses:$BUILD/classes"
for n in org.eclipse.jdt.core org.eclipse.jdt.core.compiler.batch org.eclipse.core.runtime \
         org.eclipse.equinox.common org.eclipse.core.resources org.eclipse.osgi org.eclipse.text \
         org.eclipse.core.jobs org.eclipse.core.contenttype org.eclipse.equinox.preferences \
         org.eclipse.equinox.registry org.eclipse.core.filesystem org.eclipse.core.expressions; do
  J=$(find "$HOME/.m2/repository/p2/osgi/bundle/$n" -name '*.jar' 2>/dev/null | sort | tail -1)
  [ -n "$J" ] && CP="$CP:$J"
done

find "$ROOT/bundles/org.eclipse.swt.tools/JNI Generation" -name '*.java' -printf '"%p"\n' > "$BUILD/toolsrc.txt"
javac -nowarn -proc:none -cp "$CP" -d "$BUILD/toolsclasses" "@$BUILD/toolsrc.txt"
javac -nowarn -proc:none -cp "$CP" -d "$BUILD/toolsclasses" "$BUILD/gen/GenJni.java"

# 2. Generate into a scratch copy of the library directory.
rm -rf "$TMP"; cp -r "$LIB" "$TMP"
java -cp "$CP" GenJni org.eclipse.swt.internal.gdip.Gdip "$TMP/" "$SRCROOT"

# 3. Splice generated bodies under the preserved copyright headers.
for f in gdip.cpp gdip_stats.cpp gdip_stats.h gdip_structs.cpp gdip_structs.h; do
  head -n 13 "$LIB/$f" > "$TMP/$f.spliced"
  cat "$TMP/$f" >> "$TMP/$f.spliced"
  mv "$TMP/$f.spliced" "$LIB/$f"
done
echo "regenerated: gdip.cpp gdip_stats.cpp gdip_stats.h gdip_structs.cpp gdip_structs.h"
