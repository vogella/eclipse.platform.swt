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

# Only sources that no other platform compiles: C.java and Callback.java sit in folders the
# win32 and cocoa fragments share, which have no FFM implementation, so they stay on JNI here.
for root in "Eclipse SWT PI/gtk" "Eclipse SWT PI/cairo"; do
	java "$REWRITER" "$SUPPORTED" "$SWT/$root" "${implementations[@]}"
done
