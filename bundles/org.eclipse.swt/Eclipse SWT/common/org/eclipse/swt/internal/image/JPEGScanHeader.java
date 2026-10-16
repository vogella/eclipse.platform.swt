/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.image;


import java.util.Arrays;

import org.eclipse.swt.*;

final class JPEGScanHeader extends JPEGVariableSizeSegment {
	public int[][] componentParameters;

public JPEGScanHeader(byte[] reference) {
	super(reference);
}

public JPEGScanHeader(LEDataInputStream byteStream) {
	super(byteStream);
	initializeComponentParameters();
}

public int getNumberOfImageComponents() {
	return reference[4];
}

/* Used when decoding. */
void initializeComponentParameters() {
	int compCount = getNumberOfImageComponents();
	componentParameters = new int[0][];
	for (int i = 0; i < compCount; i++) {
		int ofs = 5 + i * 2;
		int cid = reference[ofs] & 0xFF;
		int dc = (reference[ofs + 1] & 0xFF) >> 4;
		int ac = reference[ofs + 1] & 0xF;
		if (componentParameters.length <= cid) {
			componentParameters = Arrays.copyOf(componentParameters, cid + 1);
		}
		componentParameters[cid] = new int[] { dc, ac };
	}
}

/* Used when encoding. */
public void initializeContents() {
	int compCount = getNumberOfImageComponents();
	int[][] compSpecParams = componentParameters;
	if (compCount == 0 || compCount != compSpecParams.length) {
		SWT.error(SWT.ERROR_INVALID_IMAGE);
	}
	for (int i = 0; i < compCount; i++) {
		int ofs = i * 2 + 5;
		int[] compParams = compSpecParams[i];
		reference[ofs] = (byte)(i + 1);
		reference[ofs + 1] = (byte)(compParams[0] * 16 + compParams[1]);
	}
}

public void setEndOfSpectralSelection(int anInteger) {
	reference[(2 * getNumberOfImageComponents()) + 6] = (byte)anInteger;
}

public void setNumberOfImageComponents(int anInteger) {
	reference[4] = (byte)(anInteger & 0xFF);
}

public void setStartOfSpectralSelection(int anInteger) {
	reference[(2 * getNumberOfImageComponents()) + 5] = (byte)anInteger;
}

@Override
public int signature() {
	return JPEGFileFormat.SOS;
}
}
