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
# Generates the Win32 FFM bindings from the SWT native declarations. Runs in Git Bash.
#
# Needs LLVM (clang-cl, llvm-nm; default "C:/Program Files/LLVM/bin"), the MSVC and Windows SDK headers and
# libraries that clang-cl finds by itself or through INCLUDE/LIB of vcvarsall, and a JDK 25. JDT Core for the
# generator comes from ECLIPSE_HOME/plugins or else from the p2 cache of the local Maven repository.
# ARCH=x64 (default) or arm64; the layout probes run, so they have to be built for the host.
# Intermediate files go to BUILD_DIR (default: $TEMP/swt-ffm-build-win32).

set -euo pipefail
# keep clang-cl options and Windows paths as they are
export MSYS2_ARG_CONV_EXCL='*'

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
SWT="$(cd "$TOOLS/../org.eclipse.swt" && pwd)"
ARCH="${ARCH:-x64}"
case "$ARCH" in
	x64) TARGET=x86_64-pc-windows-msvc ;;
	arm64) TARGET=aarch64-pc-windows-msvc ;;
	*) echo "Unknown ARCH $ARCH" >&2; exit 1 ;;
esac
BUILD_DIR="${BUILD_DIR:-${TEMP:-/tmp}/swt-ffm-build-win32}"
BUILD_DIR="$(cygpath -u "$BUILD_DIR")"
OUTPUT="$SWT/Eclipse SWT PI/win32-ffm"
REPORT="$TOOLS/ffm/report-win32"
LIBRARY="$SWT/Eclipse SWT PI/win32/library"
MAK="$LIBRARY/make_win32.mak"
PATH="$PATH:/c/Program Files/LLVM/bin"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
win() { cygpath -m "$1"; }

mkdir -p "$BUILD_DIR/classes"

# newest jar of a bundle from ECLIPSE_HOME or the p2 cache
bundle_jar() {
	if [ -n "${ECLIPSE_HOME:-}" ]; then
		ls "$ECLIPSE_HOME/plugins/$1_"*.jar 2>/dev/null | grep -v '\.source_' | sort -V | tail -1
	else
		ls "${P2_BUNDLES:-$HOME/.m2/repository/p2/osgi/bundle}/$1/"*/"$1-"*.jar 2>/dev/null | sort -V | tail -1
	fi
}
cp=""
for b in org.eclipse.jdt.core org.eclipse.jdt.core.compiler.batch org.eclipse.core.runtime org.eclipse.equinox.common \
		org.eclipse.core.resources org.eclipse.core.jobs org.eclipse.osgi org.eclipse.core.contenttype \
		org.eclipse.equinox.preferences org.eclipse.text org.osgi.service.prefs org.eclipse.swt.win32.win32.x86_64; do
	jar=$(bundle_jar "$b")
	[ -n "$jar" ] || { echo "Bundle $b not found, set ECLIPSE_HOME or P2_BUNDLES" >&2; exit 1; }
	cp="$cp;$(win "$jar")"
done

echo "Compiling generator"
(cd "$TOOLS/JNI Generation" && find . -name '*.java' ! -name JNIGeneratorAppUI.java > "$BUILD_DIR/sources.txt" \
	&& javac -nowarn -encoding UTF-8 -d "$(win "$BUILD_DIR/classes")" -cp "$cp" @"$(win "$BUILD_DIR/sources.txt")" \
	&& cp org/eclipse/swt/tools/internal/*.properties "$BUILD_DIR/classes/org/eclipse/swt/tools/internal/")
run_generator() {
	(cd "$TOOLS" && java -cp "$(win "$BUILD_DIR/classes")$cp" org.eclipse.swt.tools.internal.FFMGeneratorApp "$@")
}

# same defines and include paths as make_win32.mak and build.bat
CFLAGS=(--target="$TARGET" -w -DJNI64 -DNDEBUG -DUNICODE -D_UNICODE -DUSE_ASSEMBLER
	-I"$(win "$JAVA_HOME/include")" -I"$(win "$JAVA_HOME/include/win32")"
	-I"$(win "$LIBRARY")" -I"$(win "$SWT/Eclipse SWT/common/library")")

# symbols of the import libraries make_win32.mak links, first library wins as with the linker
makevar() {
	awk -v name="$1" '
		!line && $0 ~ "^" name "[ \t]*=" { line = $0; sub("^" name "[ \t]*=", "", line); cont = sub(/\\[ \t\r]*$/, "", line); if (!cont) { print line; exit } next }
		line { l = $0; cont = sub(/\\[ \t\r]*$/, "", l); line = line " " l; if (!cont) { print line; exit } }
	' "$MAK" | tr -d '\r'
}
libs="$(makevar olelibsmt) $(makevar SWT_LIBS)"
libs="${libs//\$(guilibsmt)/$(makevar guilibsmt)}"
sdk_lib="${WindowsSdkDir:-C:/Program Files (x86)/Windows Kits/10}/Lib"
sdk_lib="$(cygpath -u "$sdk_lib")"
sdk_version="${WindowsSDKLibVersion:-$(ls "$sdk_lib" | sort -V | tail -1)}"
sdk_version="${sdk_version%\\}"
echo "Reading import libraries of $sdk_lib/$sdk_version/um/$ARCH"
: > "$BUILD_DIR/imports.txt"
for lib in $libs; do
	file="$sdk_lib/$sdk_version/um/$ARCH/$lib"
	[ -f "$file" ] || { echo "  no import library $lib"; continue; }
	llvm-nm "$(win "$file")" 2>/dev/null | awk '
		/^[^ ].*:$/ { dll = tolower(substr($0, 1, length($0) - 1)); next }
		$2 == "T" && $3 !~ /^__imp_/ && dll ~ /\.(dll|drv)$/ { print $3, dll }
	' >> "$BUILD_DIR/imports.txt"
done

# main class | C file ("-": not generated)
UNITS=(
	"org.eclipse.swt.internal.C|Eclipse SWT PI/common/library/c.c"
	"org.eclipse.swt.internal.win32.OS|Eclipse SWT PI/win32/library/os.c"
	"org.eclipse.swt.internal.ole.win32.COM|Eclipse SWT PI/win32/library/com.c"
	"org.eclipse.swt.internal.win32.version.OsVersion|Eclipse SWT PI/win32/library/osversion.c"
	"org.eclipse.swt.internal.gdip.Gdip|-"
)

args=()
for unit in "${UNITS[@]}"; do
	IFS='|' read -r main cfile <<< "$unit"
	if [ "$cfile" = - ]; then
		args+=("$main" - -)
		continue
	fi
	name=$(basename "$cfile" .c)
	src="$(win "$SWT/$cfile")"
	echo "Reading C types, macros and struct layouts of $name"
	clang-cl "${CFLAGS[@]}" -Zs -Xclang -ast-dump -fno-color-diagnostics "$src" > "$BUILD_DIR/$name.ast"
	clang-cl "${CFLAGS[@]}" -E -clang:-dM "$src" > "$BUILD_DIR/$name.macros"
	run_generator probe "$main" "$(win "$BUILD_DIR/$name.ast")" "$(win "$BUILD_DIR/${name}_probe.c")"
	clang-cl "${CFLAGS[@]}" -I"$(win "$(dirname "$SWT/$cfile")")" "$(win "$BUILD_DIR/${name}_probe.c")" \
		-Fe"$(win "$BUILD_DIR/${name}_probe.exe")" -Fo"$(win "$BUILD_DIR/${name}_probe.obj")" > /dev/null
	"$BUILD_DIR/${name}_probe.exe" | tr -d '\r' > "$BUILD_DIR/${name}_layout.txt"
	args+=("$main" "$(win "$BUILD_DIR/$name.ast")" "$(win "$BUILD_DIR/${name}_layout.txt")")
done

# only the generated classes, hand written FFM support classes live in the same folders
mkdir -p "$OUTPUT"
find "$OUTPUT" -name '*_FFM.java' -delete
run_generator generate "$(win "$OUTPUT")" "$(win "$REPORT")" --imports "$(win "$BUILD_DIR/imports.txt")" "${args[@]}"
