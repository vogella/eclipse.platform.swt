/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
/*
 * Prints where the GDI+ C++ wrapper objects keep their flat API pointer, for GdipGraphicsBridge,
 * and the layouts FFMGdipGraphics uses. In a VS x64 developer prompt:
 *   cl /nologo /EHsc gdip-objects-probe.cpp gdiplus.lib && gdip-objects-probe
 */
#define private public
#define protected public
#include <windows.h>
#include <gdiplus.h>
#include <stddef.h>
#include <stdio.h>
using namespace Gdiplus;

int main() {
	printf("Graphics %zu (sizeof %zu)\n", offsetof(Graphics, nativeGraphics), sizeof(Graphics));
	printf("Pen %zu\n", offsetof(Pen, nativePen));
	printf("Brush %zu\n", offsetof(Brush, nativeBrush));
	printf("Image %zu\n", offsetof(Image, nativeImage));
	printf("Font %zu\n", offsetof(Font, nativeFont));
	printf("Matrix %zu\n", offsetof(Matrix, nativeMatrix));
	printf("Region %zu\n", offsetof(Region, nativeRegion));
	printf("GraphicsPath %zu\n", offsetof(GraphicsPath, nativePath));
	printf("StringFormat %zu\n", offsetof(StringFormat, nativeFormat));
	printf("ImageAttributes %zu\n", offsetof(ImageAttributes, nativeImageAttr));
	printf("GdiplusStartupInput sizeof %zu: %zu %zu %zu %zu\n", sizeof(GdiplusStartupInput), offsetof(GdiplusStartupInput, GdiplusVersion),
		offsetof(GdiplusStartupInput, DebugEventCallback), offsetof(GdiplusStartupInput, SuppressBackgroundThread),
		offsetof(GdiplusStartupInput, SuppressExternalCodecs));
	printf("PointF %zu Rect %zu RectF %zu\n", sizeof(PointF), sizeof(Rect), sizeof(RectF));
	return 0;
}
