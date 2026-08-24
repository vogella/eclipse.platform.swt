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
package org.eclipse.swt.visualoracle.result;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.swt.visualoracle.spi.CapturedImage;

/**
 * One entry of the {@code captures} array in a result document.
 *
 * @param specimen specimen id
 * @param backend backend id
 * @param status outcome of the capture attempt
 * @param width captured image width, only for CAPTURED
 * @param height captured image height, only for CAPTURED
 * @param image path of the PNG file relative to the result document,
 *     only for CAPTURED
 * @param message failure or skip reason, only when status is not CAPTURED
 */
public record CaptureEntry(String specimen, String backend, CaptureStatus status,
		Integer width, Integer height, String image, String message) {

	public CaptureEntry {
		if (status == CaptureStatus.CAPTURED && (width == null || height == null || image == null))
			throw new IllegalArgumentException("CAPTURED entries need width, height and image");
		if (status != CaptureStatus.CAPTURED && (width != null || height != null || image != null))
			throw new IllegalArgumentException("non-CAPTURED entries must omit width, height and image");
	}

	/** Factory for a successful capture. */
	public static CaptureEntry captured(String specimen, String backend, CapturedImage image, String path) {
		return new CaptureEntry(specimen, backend, CaptureStatus.CAPTURED,
				Integer.valueOf(image.width()), Integer.valueOf(image.height()), path, null);
	}

	/** Factory for an unsupported or failed capture. */
	public static CaptureEntry skipped(String specimen, String backend, CaptureStatus status, String message) {
		return new CaptureEntry(specimen, backend, status, null, null, null, message);
	}

	Map<String, Object> toJson() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("specimen", specimen);
		map.put("backend", backend);
		map.put("status", status.name());
		if (status == CaptureStatus.CAPTURED) {
			map.put("width", width);
			map.put("height", height);
			map.put("image", image);
		} else {
			map.put("message", message == null ? "" : message);
		}
		return map;
	}
}
