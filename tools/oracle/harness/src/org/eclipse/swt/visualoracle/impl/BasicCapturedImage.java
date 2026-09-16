/*******************************************************************************
 * Copyright (c) 2026 SWT Visual Oracle contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.visualoracle.impl;

import java.io.ByteArrayOutputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.visualoracle.spi.CapturedImage;

/**
 * Plain-data CapturedImage holding an ImageData copy plus its lazily encoded,
 * cached PNG form.
 */
public class BasicCapturedImage implements CapturedImage {

	private final ImageData data;
	private byte[] png;

	public BasicCapturedImage(ImageData data) {
		this.data = (ImageData) data.clone();
	}

	@Override
	public int width() {
		return data.width;
	}

	@Override
	public int height() {
		return data.height;
	}

	@Override
	public ImageData imageData() {
		return (ImageData) data.clone();
	}

	@Override
	public byte[] pngBytes() {
		if (png == null) {
			ImageLoader loader = new ImageLoader();
			loader.data = new ImageData[] { (ImageData) data.clone() };
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			loader.save(out, SWT.IMAGE_PNG);
			png = out.toByteArray();
		}
		return png.clone();
	}
}
