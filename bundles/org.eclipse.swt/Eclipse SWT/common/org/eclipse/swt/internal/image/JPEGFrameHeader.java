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

final class JPEGFrameHeader extends JPEGVariableSizeSegment {
	int maxVFactor;
	int maxHFactor;
	public int[] componentIdentifiers;
	public int[][] componentParameters;

	public JPEGFrameHeader(byte[] reference) {
		super(reference);
	}

	public JPEGFrameHeader(LEDataInputStream byteStream) {
		super(byteStream);
		initializeComponentParameters();
	}

	public int getNumberOfLines() {
		return (reference[5] & 0xFF) << 8 | (reference[6] & 0xFF);
	}

	public int getSamplesPerLine() {
		return (reference[7] & 0xFF) << 8 | (reference[8] & 0xFF);
	}

	public int getNumberOfImageComponents() {
		return reference[9] & 0xFF;
	}

	public void setSamplePrecision(int precision) {
		reference[4] = (byte)(precision & 0xFF);
	}

	public void setNumberOfLines(int anInteger) {
		reference[5] = (byte)((anInteger & 0xFF00) >> 8);
		reference[6] = (byte)(anInteger & 0xFF);
	}

	public void setSamplesPerLine(int samples) {
		reference[7] = (byte)((samples & 0xFF00) >> 8);
		reference[8] = (byte)(samples & 0xFF);
	}

	public void setNumberOfImageComponents(int anInteger) {
		reference[9] = (byte)(anInteger & 0xFF);
	}

	public int getMaxHFactor() {
		return maxHFactor;
	}

	public int getMaxVFactor() {
		return maxVFactor;
	}

	public void setMaxHFactor(int anInteger) {
		maxHFactor = anInteger;
	}

	public void setMaxVFactor(int anInteger) {
		maxVFactor = anInteger;
	}

	/* Used when decoding. */
	void initializeComponentParameters() {
		int nf = getNumberOfImageComponents();
		componentIdentifiers = new int[nf];
		int[][] compSpecParams = new int[0][];
		int hmax = 1;
		int vmax = 1;
		for (int i = 0; i < nf; i++) {
			int ofs = i * 3 + 10;
			int ci = reference[ofs] & 0xFF;
			componentIdentifiers[i] = ci;
			int hi = (reference[ofs + 1] & 0xFF) >> 4;
			int vi = reference[ofs + 1] & 0xF;
			int tqi = reference[ofs + 2] & 0xFF;
			if (hi > hmax) {
				hmax = hi;
			}
			if (vi > vmax) {
				vmax = vi;
			}
			int[] compParam = new int[5];
			compParam[0] = tqi;
			compParam[1] = hi;
			compParam[2] = vi;
			if (compSpecParams.length <= ci) {
				compSpecParams = Arrays.copyOf(compSpecParams, ci + 1);
			}
			compSpecParams[ci] = compParam;
		}
		int x = getSamplesPerLine();
		int y = getNumberOfLines();
		int[] multiples = new int[] { 8, 16, 24, 32 };
		for (int i = 0; i < nf; i++) {
			int[] compParam = compSpecParams[componentIdentifiers[i]];
			int hi = compParam[1];
			int vi = compParam[2];
			int compWidth = (x * hi + hmax - 1) / hmax;
			int compHeight = (y * vi + vmax - 1) / vmax;
			int dsWidth = roundUpToMultiple(compWidth, multiples[hi - 1]);
			int dsHeight = roundUpToMultiple(compHeight, multiples[vi - 1]);
			compParam[3] = dsWidth;
			compParam[4] = dsHeight;
		}
		setMaxHFactor(hmax);
		setMaxVFactor(vmax);
		componentParameters = compSpecParams;
	}

	/* Used when encoding. */
	public void initializeContents() {
		int nf = getNumberOfImageComponents();
		if (nf == 0 || nf != componentParameters.length) {
			SWT.error(SWT.ERROR_INVALID_IMAGE);
		}
		int hmax = 0;
		int vmax = 0;
		int[][] compSpecParams = componentParameters;
		for (int i = 0; i < nf; i++) {
			int ofs = i * 3 + 10;
			int[] compParam = compSpecParams[componentIdentifiers[i]];
			int hi = compParam[1];
			int vi = compParam[2];
			if (hi * vi > 4) {
				SWT.error(SWT.ERROR_INVALID_IMAGE);
			}
			reference[ofs] = (byte)(i + 1);
			reference[ofs + 1] = (byte)(hi * 16 + vi);
			reference[ofs + 2] = (byte)(compParam[0]);
			if (hi > hmax) hmax = hi;
			if (vi > vmax) vmax = vi;
		}
		int x = getSamplesPerLine();
		int y = getNumberOfLines();
		int[] multiples = new int[] {8, 16, 24, 32};
		for (int i = 0; i < nf; i++) {
			int[] compParam = compSpecParams[componentIdentifiers[i]];
			int hi = compParam[1];
			int vi = compParam[2];
			int compWidth = (x * hi + hmax - 1) / hmax;
			int compHeight = (y * vi + vmax - 1) / vmax;
			int dsWidth = roundUpToMultiple(compWidth, multiples[hi - 1]);
			int dsHeight = roundUpToMultiple(compHeight, multiples[vi - 1]);
			compParam[3] = dsWidth;
			compParam[4] = dsHeight;
		}
		setMaxHFactor(hmax);
		setMaxVFactor(vmax);
	}

	int roundUpToMultiple(int anInteger, int mInteger) {
		int a = anInteger + mInteger - 1;
		return a - (a % mInteger);
	}
}
