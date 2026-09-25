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
# The committed sources keep their native declarations, so that this branch never
# collides with a change to OS.java and friends; a product build runs this first.
# Needs a JDK 21+ for the single file source launch and leaves a dirty checkout.

set -euo pipefail

TOOLS="$(cd "$(dirname "$0")/.." && pwd)"
SWT="$(cd "$TOOLS/../org.eclipse.swt" && pwd)"
REWRITER="$TOOLS/ffm/FFMRewriter.java"
SUPPORTED="$TOOLS/ffm/report-gtk/supported.txt"

implementations=(
	"$SWT/Eclipse SWT PI/common-ffm/org/eclipse/swt/internal/ffm/FFMUtf16.java"
	"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMConstructorProc.java"
	"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMMacros.java"
	"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMSwtFixed.java"
	"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMTypes.java"
	"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMAccessible.java"
	"$SWT/Eclipse SWT PI/gtk-ffm/org/eclipse/swt/internal/ffm/FFMRuntime.java"
)

for root in "Eclipse SWT PI/gtk" "Eclipse SWT PI/cairo" "Eclipse SWT OpenGL/glx" "Eclipse SWT WebKit/gtk" "Eclipse SWT AWT/gtk"; do
	java "$REWRITER" "$SUPPORTED" "$SWT/$root" "${implementations[@]}"
done

# C.java and Callback.java sit in folders the win32 and cocoa fragments compile too, which have no
# FFM implementation. Move them out: a rewritten copy for the GTK fragments, the original for the others.
GTK_SHARED="Eclipse SWT PI/gtk-ffm-shared"
JNI_SHARED="Eclipse SWT PI/jni-shared"
for file in "Eclipse SWT PI/common/org/eclipse/swt/internal/C.java" "Eclipse SWT/common/org/eclipse/swt/internal/Callback.java"; do
	name="$(basename "$file")"
	mkdir -p "$SWT/$GTK_SHARED/org/eclipse/swt/internal" "$SWT/$JNI_SHARED/org/eclipse/swt/internal"
	cp "$SWT/$file" "$SWT/$GTK_SHARED/org/eclipse/swt/internal/$name"
	mv "$SWT/$file" "$SWT/$JNI_SHARED/org/eclipse/swt/internal/$name"
done
java "$REWRITER" "$SUPPORTED" "$SWT/$GTK_SHARED" "${implementations[@]}"
for fragment in "$SWT"/../../binaries/org.eclipse.swt.*.*.*/build.properties; do
	case "$fragment" in
		*/org.eclipse.swt.gtk.*) folder="$GTK_SHARED" ;;
		*) folder="$JNI_SHARED" ;;
	esac
	awk -v folder="$folder" '{ print }
		!done && /\/Eclipse SWT PI\/common,\\$/ {
			match($0, /^[ \t]*/); print substr($0, 1, RLENGTH) "../../bundles/org.eclipse.swt/" folder ",\\"; done = 1
		}' "$fragment" > "$fragment.tmp" && mv "$fragment.tmp" "$fragment"
	grep -q "$folder," "$fragment" || { echo "apply-ffm.sh: could not add $folder to $fragment" >&2; exit 1; }
done
