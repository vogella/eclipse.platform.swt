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
# Switches the checked out SWT sources from JNI to FFM, in place, by turning every
# supported native declaration into a call of its FFM implementation.
#
#   apply-ffm.sh [gtk] [win32]    the platforms to switch, gtk if none is given
#
# The committed sources keep their native declarations, so that this branch never
# collides with a change to OS.java and friends; a product build runs this first.
# Needs a JDK 21+ for the single file source launch and leaves a dirty checkout.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
SWT="$(cd "$TOOLS/../org.eclipse.swt" && pwd)"
REWRITER="$TOOLS/ffm/FFMRewriter.java"
PLATFORMS=("$@")
[ ${#PLATFORMS[@]} -eq 0 ] && PLATFORMS=(gtk)

native_path() {
	if command -v cygpath > /dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

rewrite() {
	local supported="$1" root="$2"
	shift 2
	java "$(native_path "$REWRITER")" "$(native_path "$supported")" "$(native_path "$root")" "$@"
}

gtk_implementations=()
for name in "common-ffm/org/eclipse/swt/internal/ffm/FFMUtf16" "gtk-ffm/org/eclipse/swt/internal/ffm/FFMConstructorProc" \
	"gtk-ffm/org/eclipse/swt/internal/ffm/FFMMacros" "gtk-ffm/org/eclipse/swt/internal/ffm/FFMSwtFixed" \
	"gtk-ffm/org/eclipse/swt/internal/ffm/FFMTypes" "gtk-ffm/org/eclipse/swt/internal/ffm/FFMAccessible" \
	"gtk-ffm/org/eclipse/swt/internal/ffm/FFMRuntime"; do
	gtk_implementations+=("$(native_path "$SWT/Eclipse SWT PI/$name.java")")
done
win32_implementations=()
for name in FFMCom FFMComCustom FFMOsCustom FFMGdipGraphics FFMGdipObjects FFMWin32Macros FFMResources; do
	win32_implementations+=("$(native_path "$SWT/Eclipse SWT PI/win32-ffm/org/eclipse/swt/internal/ffm/$name.java")")
done

for platform in "${PLATFORMS[@]}"; do
	case "$platform" in
		gtk)
			for root in "Eclipse SWT PI/gtk" "Eclipse SWT PI/cairo" "Eclipse SWT OpenGL/glx" "Eclipse SWT WebKit/gtk" "Eclipse SWT AWT/gtk"; do
				rewrite "$TOOLS/ffm/report-gtk/supported.txt" "$SWT/$root" "${gtk_implementations[@]}"
			done ;;
		win32)
			# the OpenGL (swt-wgl) and AWT (swt-awt) natives stay on JNI
			rewrite "$TOOLS/ffm/report-win32/supported.txt" "$SWT/Eclipse SWT PI/win32" "${win32_implementations[@]}" ;;
		*) echo "apply-ffm.sh: unknown platform $platform" >&2; exit 2 ;;
	esac
done

# C.java and Callback.java sit in folders every fragment compiles. Move them out: a rewritten copy per
# switched platform, since C_FFM differs between them, and the original for the other fragments.
JNI_SHARED="Eclipse SWT PI/jni-shared"
for file in "Eclipse SWT PI/common/org/eclipse/swt/internal/C.java" "Eclipse SWT/common/org/eclipse/swt/internal/Callback.java"; do
	name="$(basename "$file")"
	for platform in "${PLATFORMS[@]}"; do
		mkdir -p "$SWT/Eclipse SWT PI/$platform-ffm-shared/org/eclipse/swt/internal"
		cp "$SWT/$file" "$SWT/Eclipse SWT PI/$platform-ffm-shared/org/eclipse/swt/internal/$name"
	done
	mkdir -p "$SWT/$JNI_SHARED/org/eclipse/swt/internal"
	mv "$SWT/$file" "$SWT/$JNI_SHARED/org/eclipse/swt/internal/$name"
done
for platform in "${PLATFORMS[@]}"; do
	if [ "$platform" = gtk ]; then
		rewrite "$TOOLS/ffm/report-gtk/supported.txt" "$SWT/Eclipse SWT PI/gtk-ffm-shared" "${gtk_implementations[@]}"
	else
		rewrite "$TOOLS/ffm/report-win32/supported.txt" "$SWT/Eclipse SWT PI/win32-ffm-shared" "${win32_implementations[@]}"
	fi
done
for fragment in "$SWT"/../../binaries/org.eclipse.swt.*.*.*/build.properties; do
	folder="$JNI_SHARED"
	for platform in "${PLATFORMS[@]}"; do
		case "$fragment" in */org.eclipse.swt.$platform.*) folder="Eclipse SWT PI/$platform-ffm-shared" ;; esac
	done
	awk -v folder="$folder" '{ print }
		!done && /\/Eclipse SWT PI\/common,\\\r?$/ {
			match($0, /^[ \t]*/); print substr($0, 1, RLENGTH) "../../bundles/org.eclipse.swt/" folder ",\\"; done = 1
		}' "$fragment" > "$fragment.tmp" && mv "$fragment.tmp" "$fragment"
	grep -q "$folder," "$fragment" || { echo "apply-ffm.sh: could not add $folder to $fragment" >&2; exit 1; }
done
