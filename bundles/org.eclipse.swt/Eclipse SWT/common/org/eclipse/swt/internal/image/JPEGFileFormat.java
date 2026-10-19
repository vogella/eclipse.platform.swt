/*******************************************************************************
 * Copyright (c) 2000, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * This source file is based in part on the work of the Independent JPEG Group (IJG)
 * and is made available under the terms contained in the about_files/IJG_README
 * file accompanying this program.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.image;


import java.io.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.image.FileFormat.*;

public final class JPEGFileFormat extends StaticImageFileFormat {
	JPEGFrameHeader frameHeader;
	int imageWidth, imageHeight;
	int interleavedMcuCols, interleavedMcuRows;
	int maxV, maxH;
	int nComponents;
	int[][] frameComponents;
	int[] componentIds;
	byte[][] imageComponents;
	int[] dataUnit;
	int[] precedingDCs;
	JPEGScanHeader scanHeader;
	byte[] dataBuffer;
	int currentBitCount;
	int bufferCurrentPosition;
	JPEGHuffmanTable[] acHuffmanTables;
	JPEGHuffmanTable[] dcHuffmanTables;
	int[][] quantizationTables;
	int currentByte;
	int encoderQFactor = 75;

	/* JPEGConstants */
	public static final int DCTSIZE = 8;
	/* JPEGFixedPointConstants */
	public static final int FIX_0_899976223 = 7373;
	public static final int FIX_1_961570560 = 16069;
	public static final int FIX_2_053119869 = 16819;
	public static final int FIX_0_298631336 = 2446;
	public static final int FIX_1_847759065 = 15137;
	public static final int FIX_1_175875602 = 9633;
	public static final int FIX_3_072711026 = 25172;
	public static final int FIX_0_765366865 = 6270;
	public static final int FIX_2_562915447 = 20995;
	public static final int FIX_0_541196100 = 4433;
	public static final int FIX_0_390180644 = 3196;
	public static final int FIX_1_501321110 = 12299;
	/* JPEGMarkerCodes */
	public static final int APP0  = 0xFFE0;
	public static final int COM   = 0xFFFE;
	public static final int DHT   = 0xFFC4;
	public static final int DRI   = 0xFFDD;
	public static final int DQT   = 0xFFDB;
	public static final int EOI   = 0xFFD9;
	public static final int SOF0  = 0xFFC0;
	public static final int SOI   = 0xFFD8;
	public static final int SOS   = 0xFFDA;
	/* JPEGFrameComponentParameterConstants */
	public static final int TQI	= 0;
	public static final int HI	= 1;
	public static final int VI	= 2;
	public static final int CW	= 3;
	public static final int CH	= 4;
	/* JPEGScanComponentParameterConstants */
	public static final int DC	= 0;
	public static final int AC	= 1;
	/* JFIF Component Constants */
	public static final int ID_Y		= 1 - 1;
	public static final int ID_CB	= 2 - 1;
	public static final int ID_CR	= 3 - 1;
	public static final int[] ZigZag8x8 = {
		0, 1, 8, 16, 9, 2, 3, 10,
		17, 24, 32, 25, 18, 11, 4, 5,
		12, 19, 26, 33, 40, 48, 41, 34,
		27, 20, 13, 6, 7, 14, 21, 28,
		35, 42, 49, 56, 57, 50, 43, 36,
		29, 22, 15, 23, 30, 37, 44, 51,
		58, 59, 52, 45, 38, 31, 39, 46,
		53, 60, 61, 54, 47, 55, 62, 63
	};

	public static final int[] RYTable, GYTable, BYTable,
		RCbTable, GCbTable, BCbTable, RCrTable, GCrTable, BCrTable, NBitsTable;
	static {
		/* Initialize RGB-YCbCr Tables */
		int [] rYTable = new int[256];
		int [] gYTable = new int[256];
		int [] bYTable = new int[256];
		int [] rCbTable = new int[256];
		int [] gCbTable = new int[256];
		int [] bCbTable = new int[256];
		int [] gCrTable = new int[256];
		int [] bCrTable = new int[256];
		for (int i = 0; i < 256; i++) {
			rYTable[i] = i * 19595;
			gYTable[i] = i * 38470;
			bYTable[i] = i * 7471 + 32768;
			rCbTable[i] = i * -11059;
			gCbTable[i] = i * -21709;
			bCbTable[i] = i * 32768 + 8388608;
			gCrTable[i] = i * -27439;
			bCrTable[i] = i * -5329;
		}
		RYTable = rYTable;
		GYTable = gYTable;
		BYTable = bYTable;
		RCbTable = rCbTable;
		GCbTable = gCbTable;
		BCbTable = bCbTable;
		RCrTable = bCbTable;
		GCrTable = gCrTable;
		BCrTable = bCrTable;

		/* Initialize BitCount Table */
		int nBits = 1;
		int power2 = 2;
		int [] nBitsTable = new int[2048];
		nBitsTable[0] = 0;
		for (int i = 1; i < nBitsTable.length; i++) {
			if (!(i < power2)) {
				nBits++;
				power2 *= 2;
			}
			nBitsTable[i] = nBits;
		}
		NBitsTable = nBitsTable;
	}
void compress(ImageData image, byte[] dataYComp, byte[] dataCbComp, byte[] dataCrComp) {
	int srcWidth = image.width;
	int srcHeight = image.height;
	int vhFactor = maxV * maxH;
	int[] frameComponent;
	imageComponents = new byte[nComponents][];
	for (int i = 0; i < nComponents; i++) {
		frameComponent = frameComponents[componentIds[i]];
		imageComponents[i] = new byte[frameComponent[CW] * frameComponent[CH]];
	}
	frameComponent = frameComponents[componentIds[ID_Y]];
	for (int yPos = 0; yPos < srcHeight; yPos++) {
		int srcOfs = yPos * srcWidth;
		int dstOfs = yPos * frameComponent[CW];
		System.arraycopy(dataYComp, srcOfs, imageComponents[ID_Y], dstOfs, srcWidth);
	}
	frameComponent = frameComponents[componentIds[ID_CB]];
	for (int yPos = 0; yPos < srcHeight / maxV; yPos++) {
		int destRowIndex = yPos * frameComponent[CW];
		for (int xPos = 0; xPos < srcWidth / maxH; xPos++) {
			int sum = 0;
			for (int iv = 0; iv < maxV; iv++) {
				int srcIndex = (yPos * maxV + iv) * srcWidth + (xPos * maxH);
				for (int ih = 0; ih < maxH; ih++) {
					sum += dataCbComp[srcIndex + ih] & 0xFF;
				}
			}
			imageComponents[ID_CB][destRowIndex + xPos] = (byte)(sum / vhFactor);
		}
	}
	frameComponent = frameComponents[componentIds[ID_CR]];
	for (int yPos = 0; yPos < srcHeight / maxV; yPos++) {
		int destRowIndex = yPos * frameComponent[CW];
		for (int xPos = 0; xPos < srcWidth / maxH; xPos++) {
			int sum = 0;
			for (int iv = 0; iv < maxV; iv++) {
				int srcIndex = (yPos * maxV + iv) * srcWidth + (xPos * maxH);
				for (int ih = 0; ih < maxH; ih++) {
					sum += dataCrComp[srcIndex + ih] & 0xFF;
				}
			}
			imageComponents[ID_CR][destRowIndex + xPos] = (byte)(sum / vhFactor);
		}
	}
	for (int iComp = 0; iComp < nComponents; iComp++) {
		byte[] imageComponent = imageComponents[iComp];
		frameComponent = frameComponents[componentIds[iComp]];
		int hFactor = frameComponent[HI];
		int vFactor = frameComponent[VI];
		int componentWidth = frameComponent[CW];
		int componentHeight = frameComponent[CH];
		int compressedWidth = srcWidth / (maxH / hFactor);
		int compressedHeight = srcHeight / (maxV / vFactor);
		if (compressedWidth < componentWidth) {
			int delta = componentWidth - compressedWidth;
			for (int yPos = 0; yPos < compressedHeight; yPos++) {
				int dstOfs = ((yPos + 1) * componentWidth - delta);
				int dataValue = imageComponent[(dstOfs > 0) ? dstOfs - 1 : 0] & 0xFF;
				for (int i = 0; i < delta; i++) {
					imageComponent[dstOfs + i] = (byte)dataValue;
				}
			}
		}
		if (compressedHeight < componentHeight) {
			int srcOfs = (compressedHeight > 0) ? (compressedHeight - 1) * componentWidth : 1;
			for (int yPos = (compressedHeight > 0) ? compressedHeight : 1; yPos <= componentHeight; yPos++) {
				int dstOfs = (yPos - 1) * componentWidth;
				System.arraycopy(imageComponent, srcOfs, imageComponent, dstOfs, componentWidth);
			}
		}
	}
}
void convert4BitRGBToYCbCr(ImageData image) {
	RGB[] rgbs = image.getRGBs();
	int paletteSize = rgbs.length;
	byte[] yComp = new byte[paletteSize];
	byte[] cbComp = new byte[paletteSize];
	byte[] crComp = new byte[paletteSize];
	int srcWidth = image.width;
	int srcHeight = image.height;
	for (int i = 0; i < paletteSize; i++) {
		RGB color = rgbs[i];
		int r = color.red;
		int g = color.green;
		int b = color.blue;
		int n = RYTable[r] + GYTable[g] + BYTable[b];
		yComp[i] = (byte)(n >> 16);
		if ((n < 0) && ((n & 0xFFFF) != 0)) yComp[i]--;
		n = RCbTable[r] + GCbTable[g] + BCbTable[b];
		cbComp[i] = (byte)(n >> 16);
		if ((n < 0) && ((n & 0xFFFF) != 0)) cbComp[i]--;
		n = RCrTable[r] + GCrTable[g] + BCrTable[b];
		crComp[i] = (byte)(n >> 16);
		if ((n < 0) && ((n & 0xFFFF) != 0)) crComp[i]--;
	}
	int bSize = srcWidth * srcHeight;
	byte[] dataYComp = new byte[bSize];
	byte[] dataCbComp = new byte[bSize];
	byte[] dataCrComp = new byte[bSize];
	byte[] origData = image.data;
	int bytesPerLine = image.bytesPerLine;
	int maxScanlineByte = srcWidth >> 1;
	for (int yPos = 0; yPos < srcHeight; yPos++) {
		for (int xPos = 0; xPos < maxScanlineByte; xPos++) {
			int srcIndex = yPos * bytesPerLine + xPos;
			int dstIndex = yPos * srcWidth + (xPos * 2);
			int value2 = origData[srcIndex] & 0xFF;
			int value1 = value2 >> 4;
			value2 &= 0x0F;
			dataYComp[dstIndex] = yComp[value1];
			dataCbComp[dstIndex] = cbComp[value1];
			dataCrComp[dstIndex] = crComp[value1];
			dataYComp[dstIndex + 1] = yComp[value2];
			dataCbComp[dstIndex + 1] = cbComp[value2];
			dataCrComp[dstIndex + 1] = crComp[value2];
		}
	}
	compress(image, dataYComp, dataCbComp, dataCrComp);
}
void convert8BitRGBToYCbCr(ImageData image) {
	RGB[] rgbs = image.getRGBs();
	int paletteSize = rgbs.length;
	byte[] yComp = new byte[paletteSize];
	byte[] cbComp = new byte[paletteSize];
	byte[] crComp = new byte[paletteSize];
	int srcWidth = image.width;
	int srcHeight = image.height;
	for (int i = 0; i < paletteSize; i++) {
		RGB color = rgbs[i];
		int r = color.red;
		int g = color.green;
		int b = color.blue;
		int n = RYTable[r] + GYTable[g] + BYTable[b];
		yComp[i] = (byte)(n >> 16);
		if ((n < 0) && ((n & 0xFFFF) != 0)) yComp[i]--;
		n = RCbTable[r] + GCbTable[g] + BCbTable[b];
		cbComp[i] = (byte)(n >> 16);
		if ((n < 0) && ((n & 0xFFFF) != 0)) cbComp[i]--;
		n = RCrTable[r] + GCrTable[g] + BCrTable[b];
		crComp[i] = (byte)(n >> 16);
		if ((n < 0) && ((n & 0xFFFF) != 0)) crComp[i]--;
	}
	int dstWidth = image.width;
	int dstHeight = srcHeight;
	int stride = ((srcWidth + 3) >> 2) << 2;
	int bSize = dstWidth * dstHeight;
	byte[] dataYComp = new byte[bSize];
	byte[] dataCbComp = new byte[bSize];
	byte[] dataCrComp = new byte[bSize];
	byte[] origData = image.data;
	for (int yPos = 0; yPos < srcHeight; yPos++) {
		int srcRowIndex = yPos * stride;
		int dstRowIndex = yPos * dstWidth;
		for (int xPos = 0; xPos < srcWidth; xPos++) {
			int value = origData[srcRowIndex + xPos] & 0xFF;
			int dstIndex = dstRowIndex + xPos;
			dataYComp[dstIndex] = yComp[value];
			dataCbComp[dstIndex] = cbComp[value];
			dataCrComp[dstIndex] = crComp[value];
		}
	}
	compress(image, dataYComp, dataCbComp, dataCrComp);
}
void convertImageToYCbCr(ImageData image) {
	switch (image.depth) {
		case 4:
			convert4BitRGBToYCbCr(image);
			return;
		case 8:
			convert8BitRGBToYCbCr(image);
			return;
		case 16:
		case 24:
		case 32:
			convertMultiRGBToYCbCr(image);
			return;
		default:
			SWT.error(SWT.ERROR_UNSUPPORTED_DEPTH);
	}
	return;
}
void convertMultiRGBToYCbCr(ImageData image) {
	int srcWidth = image.width;
	int srcHeight = image.height;
	int bSize = srcWidth * srcHeight;
	byte[] dataYComp = new byte[bSize];
	byte[] dataCbComp = new byte[bSize];
	byte[] dataCrComp = new byte[bSize];
	PaletteData palette = image.palette;
	int[] buffer = new int[srcWidth];
	if (palette.isDirect) {
		int redMask = palette.redMask;
		int greenMask = palette.greenMask;
		int blueMask = palette.blueMask;
		int redShift = palette.redShift;
		int greenShift = palette.greenShift;
		int blueShift = palette.blueShift;
		for (int yPos = 0; yPos < srcHeight; yPos++) {
			image.getPixels(0, yPos, srcWidth, buffer, 0);
			int dstRowIndex = yPos * srcWidth;
			for (int xPos = 0; xPos < srcWidth; xPos++) {
				int pixel = buffer[xPos];
				int dstDataIndex = dstRowIndex + xPos;
				int r = pixel & redMask;
				r = (redShift < 0) ? r >>> -redShift : r << redShift;
				int g = pixel & greenMask;
				g = (greenShift < 0) ? g >>> -greenShift : g << greenShift;
				int b = pixel & blueMask;
				b = (blueShift < 0) ? b >>> -blueShift : b << blueShift;
				dataYComp[dstDataIndex] = (byte)((RYTable[r] + GYTable[g] + BYTable[b]) >> 16);
				dataCbComp[dstDataIndex] = (byte)((RCbTable[r] + GCbTable[g] + BCbTable[b]) >> 16);
				dataCrComp[dstDataIndex] = (byte)((RCrTable[r] + GCrTable[g] + BCrTable[b]) >> 16);
			}
		}
	} else {
		for (int yPos = 0; yPos < srcHeight; yPos++) {
			image.getPixels(0, yPos, srcWidth, buffer, 0);
			int dstRowIndex = yPos * srcWidth;
			for (int xPos = 0; xPos < srcWidth; xPos++) {
				int pixel = buffer[xPos];
				int dstDataIndex = dstRowIndex + xPos;
				RGB rgb = palette.getRGB(pixel);
				int r = rgb.red;
				int g = rgb.green;
				int b = rgb.blue;
				dataYComp[dstDataIndex] = (byte)((RYTable[r] + GYTable[g] + BYTable[b]) >> 16);
				dataCbComp[dstDataIndex] = (byte)((RCbTable[r] + GCbTable[g] + BCbTable[b]) >> 16);
				dataCrComp[dstDataIndex] = (byte)((RCrTable[r] + GCrTable[g] + BCrTable[b]) >> 16);
			}
		}
	}
	compress(image, dataYComp, dataCbComp, dataCrComp);
}
void emit(int huffCode, int nBits) {
	if (nBits == 0) {
		SWT.error(SWT.ERROR_INVALID_IMAGE);
	}
	int[] power2m1 = new int[] {
		1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191,
		16383, 32767, 65535, 131125
	};
	int code = (huffCode & power2m1[nBits - 1]) << (24 - nBits - currentBitCount);
	byte[] codeBuffer = new byte[4];
	codeBuffer[0] = (byte)(code & 0xFF);
	codeBuffer[1] = (byte)((code >> 8) & 0xFF);
	codeBuffer[2] = (byte)((code >> 16) & 0xFF);
	codeBuffer[3] = (byte)((code >> 24) & 0xFF);
	int abs = nBits - (8 - currentBitCount);
	if (abs < 0) abs = -abs;
	if ((abs >> 3) > 0) {
		currentByte += codeBuffer[2];
		emitByte((byte)currentByte);
		emitByte(codeBuffer[1]);
		currentByte = codeBuffer[0];
		currentBitCount += nBits - 16;
	} else {
		currentBitCount += nBits;
		if (currentBitCount >= 8) {
			currentByte += codeBuffer[2];
			emitByte((byte)currentByte);
			currentByte = codeBuffer[1];
			currentBitCount -= 8;
		} else {
			currentByte += codeBuffer[2];
		}
	}
}
void emitByte(byte byteValue) {
	if (bufferCurrentPosition >= 512) {
		resetOutputBuffer();
	}
	dataBuffer[bufferCurrentPosition] = byteValue;
	bufferCurrentPosition++;
	if (byteValue == -1) {
		emitByte((byte)0);
	}
}
void encodeACCoefficients(int[] dataUnit, int iComp) {
	int[] sParams = scanHeader.componentParameters[iComp];
	JPEGHuffmanTable acTable = acHuffmanTables[sParams[AC]];
	int[] ehCodes = acTable.ehCodes;
	byte[] ehSizes = acTable.ehCodeLengths;
	int r = 0;
	int k = 1;
	while (k < 64) {
		k++;
		int acValue = dataUnit[ZigZag8x8[k - 1]];
		if (acValue == 0) {
			if (k == 64) {
				emit(ehCodes[0], ehSizes[0] & 0xFF);
			} else {
				r++;
			}
		} else {
			while (r > 15) {
				emit(ehCodes[0xF0], ehSizes[0xF0] & 0xFF);
				r -= 16;
			}
			if (acValue < 0) {
				int absACValue = acValue;
				if (absACValue < 0) absACValue = -absACValue;
				int nBits = NBitsTable[absACValue];
				int rs = r * 16 + nBits;
				emit(ehCodes[rs], ehSizes[rs] & 0xFF);
				emit(0xFFFFFF - absACValue, nBits);
			} else {
				int nBits = NBitsTable[acValue];
				int rs = r * 16 + nBits;
				emit(ehCodes[rs], ehSizes[rs] & 0xFF);
				emit(acValue, nBits);
			}
			r = 0;
		}
	}
}
void encodeDCCoefficients(int[] dataUnit, int iComp) {
	int[] sParams = scanHeader.componentParameters[iComp];
	JPEGHuffmanTable dcTable = dcHuffmanTables[sParams[DC]];
	int lastDC = precedingDCs[iComp];
	int dcValue = dataUnit[0];
	int diff = dcValue - lastDC;
	precedingDCs[iComp] = dcValue;
	if (diff < 0) {
		int absDiff = 0 - diff;
		int nBits = NBitsTable[absDiff];
		emit(dcTable.ehCodes[nBits], dcTable.ehCodeLengths[nBits]);
		emit(0xFFFFFF - absDiff, nBits);
	} else {
		int nBits = NBitsTable[diff];
		emit(dcTable.ehCodes[nBits], dcTable.ehCodeLengths[nBits]);
		if (nBits != 0) {
			emit(diff, nBits);
		}
	}
}
void encodeMCUAtXAndY(int xmcu, int ymcu) {
	int nComponentsInScan = scanHeader.getNumberOfImageComponents();
	dataUnit = new int[64];
	for (int iComp = 0; iComp < nComponentsInScan; iComp++) {
		int[] frameComponent = frameComponents[componentIds[iComp]];
		int hi = frameComponent[HI];
		int vi = frameComponent[VI];
		for (int ivi = 0; ivi < vi; ivi++) {
			for (int ihi = 0; ihi < hi; ihi++) {
				extractData(dataUnit, iComp, xmcu, ymcu, ihi, ivi);
				forwardDCT(dataUnit);
				quantizeData(dataUnit, iComp);
				encodeDCCoefficients(dataUnit, iComp);
				encodeACCoefficients(dataUnit, iComp);
			}
		}
	}
}
void encodeScan() {
	for (int ymcu = 0; ymcu < interleavedMcuRows; ymcu++) {
		for (int xmcu = 0; xmcu < interleavedMcuCols; xmcu++) {
			encodeMCUAtXAndY(xmcu, ymcu);
		}
	}
	if (currentBitCount != 0) {
		emitByte((byte)currentByte);
	}
	resetOutputBuffer();
}
void extractData(int[] dataUnit, int iComp, int xmcu, int ymcu, int ihi, int ivi) {
	byte[] compImage = imageComponents[iComp];
	int[] frameComponent = frameComponents[componentIds[iComp]];
	int hi = frameComponent[HI];
	int vi = frameComponent[VI];
	int compWidth = frameComponent[CW];
	int srcIndex = ((ymcu * vi + ivi) * compWidth * DCTSIZE) + ((xmcu * hi + ihi) * DCTSIZE);
	int destIndex = 0;
	for (int i = 0; i < DCTSIZE; i++) {
		for (int col = 0; col < DCTSIZE; col++) {
			dataUnit[destIndex] = (compImage[srcIndex + col] & 0xFF) - 128;
			destIndex++;
		}
		srcIndex += compWidth;
	}
}
void forwardDCT(int[] dataUnit) {
	for (int row = 0; row < 8; row++) {
		int rIndex = row * DCTSIZE;
		int tmp0 = dataUnit[rIndex] + dataUnit[rIndex + 7];
		int tmp7 = dataUnit[rIndex] - dataUnit[rIndex + 7];
		int tmp1 = dataUnit[rIndex + 1] + dataUnit[rIndex + 6];
		int tmp6 = dataUnit[rIndex + 1] - dataUnit[rIndex + 6];
		int tmp2 = dataUnit[rIndex + 2] + dataUnit[rIndex + 5];
		int tmp5 = dataUnit[rIndex + 2] - dataUnit[rIndex + 5];
		int tmp3 = dataUnit[rIndex + 3] + dataUnit[rIndex + 4];
		int tmp4 = dataUnit[rIndex + 3] - dataUnit[rIndex + 4];

		/**
		 * Even part per LL&M figure 1 --- note that published figure
		 * is faulty; rotator 'sqrt(2)*c1' should be 'sqrt(2)*c6'.
		 */
		int tmp10 = tmp0 + tmp3;
		int tmp13 = tmp0 - tmp3;
		int tmp11 = tmp1 + tmp2;
		int tmp12 = tmp1 - tmp2;

		dataUnit[rIndex] = (tmp10 + tmp11) * 4;
		dataUnit[rIndex + 4]  = (tmp10 - tmp11) * 4;

		int z1 = (tmp12 + tmp13) * FIX_0_541196100;
		int n = z1 + (tmp13 * FIX_0_765366865) + 1024;
		dataUnit[rIndex + 2] = n >> 11;
		if ((n < 0) && ((n & 0x07FF) != 0)) dataUnit[rIndex + 2]--;
		n = z1 + (tmp12 * (0 - FIX_1_847759065)) + 1024;
		dataUnit[rIndex + 6] = n >> 11;
		if ((n < 0) && ((n & 0x07FF) != 0)) dataUnit[rIndex + 6]--;

		/**
		 * Odd part per figure 8 --- note paper omits factor of sqrt(2).
		 * cK represents cos(K*pi/16).
		 * i0..i3 in the paper are tmp4..tmp7 here.
		 */
		z1 = tmp4 + tmp7;
		int z2 = tmp5 + tmp6;
		int z3 = tmp4 + tmp6;
		int z4 = tmp5 + tmp7;
		int z5 = (z3 + z4) * FIX_1_175875602;	// sqrt(2) * c3

		tmp4 *= FIX_0_298631336;	// sqrt(2) * (-c1+c3+c5-c7)
		tmp5 *= FIX_2_053119869;	// sqrt(2) * ( c1+c3-c5+c7)
		tmp6 *= FIX_3_072711026;	// sqrt(2) * ( c1+c3+c5-c7)
		tmp7 *= FIX_1_501321110;	// sqrt(2) * ( c1+c3-c5-c7)
		z1 *= 0 - FIX_0_899976223;	// sqrt(2) * (c7-c3)
		z2 *= 0 - FIX_2_562915447;	// sqrt(2) * (-c1-c3)
		z3 *= 0 - FIX_1_961570560;	// sqrt(2) * (-c3-c5)
		z4 *= 0 - FIX_0_390180644;	// sqrt(2) * (c5-c3)

		z3 += z5;
		z4 += z5;

		n = tmp4 + z1 + z3 + 1024;
		dataUnit[rIndex + 7] = n >> 11;
		if ((n < 0) && ((n & 0x07FF) != 0)) dataUnit[rIndex + 7]--;
		n = tmp5 + z2 + z4 + 1024;
		dataUnit[rIndex + 5] = n >> 11;
		if ((n < 0) && ((n & 0x07FF) != 0)) dataUnit[rIndex + 5]--;
		n = tmp6 + z2 + z3 + 1024;
		dataUnit[rIndex + 3] = n >> 11;
		if ((n < 0) && ((n & 0x07FF) != 0)) dataUnit[rIndex + 3]--;
		n = tmp7 + z1 + z4 + 1024;
		dataUnit[rIndex + 1] = n >> 11;
		if ((n < 0) && ((n & 0x07FF) != 0)) dataUnit[rIndex + 1]--;
	}

	/**
	 * Pass 2: process columns.
	 * Note that we must descale the results by a factor of 8 == 2**3,
	 * and also undo the PASS1_BITS scaling.
	 */
	for (int col = 0; col < 8; col++) {
		int c0 = col;
		int c1 = col + 8;
		int c2 = col + 16;
		int c3 = col + 24;
		int c4 = col + 32;
		int c5 = col + 40;
		int c6 = col + 48;
		int c7 = col + 56;
		int tmp0 = dataUnit[c0] + dataUnit[c7];
		int tmp7 = dataUnit[c0] - dataUnit[c7];
		int tmp1 = dataUnit[c1] + dataUnit[c6];
		int tmp6 = dataUnit[c1] - dataUnit[c6];
		int tmp2 = dataUnit[c2] + dataUnit[c5];
		int tmp5 = dataUnit[c2] - dataUnit[c5];
		int tmp3 = dataUnit[c3] + dataUnit[c4];
		int tmp4 = dataUnit[c3] - dataUnit[c4];

		/**
		 * Even part per LL&M figure 1 --- note that published figure
		 * is faulty; rotator 'sqrt(2)*c1' should be 'sqrt(2)*c6'.
		 */
		int tmp10 = tmp0 + tmp3;
		int tmp13 = tmp0 - tmp3;
		int tmp11 = tmp1 + tmp2;
		int tmp12 = tmp1 - tmp2;

		int n = tmp10 + tmp11 + 16;
		dataUnit[c0] = n >> 5;
		if ((n < 0) && ((n & 0x1F) != 0)) dataUnit[c0]--;
		n = tmp10 - tmp11 + 16;
		dataUnit[c4] = n >> 5;
		if ((n < 0) && ((n & 0x1F) != 0)) dataUnit[c4]--;

		int z1 = (tmp12 + tmp13) * FIX_0_541196100;
		n = z1 + (tmp13 * FIX_0_765366865) + 131072;
		dataUnit[c2] = n >> 18;
		if ((n < 0) && ((n & 0x3FFFF) != 0)) dataUnit[c2]--;
		n = z1 + (tmp12 * (0 - FIX_1_847759065)) + 131072;
		dataUnit[c6] = n >> 18;
		if ((n < 0) && ((n & 0x3FFFF) != 0)) dataUnit[c6]--;

		/**
		 * Odd part per figure 8 --- note paper omits factor of sqrt(2).
		 * cK represents cos(K*pi/16).
		 * i0..i3 in the paper are tmp4..tmp7 here.
		 */
		z1 = tmp4 + tmp7;
		int z2 = tmp5 + tmp6;
		int z3 = tmp4 + tmp6;
		int z4 = tmp5 + tmp7;
		int z5 = (z3 + z4) * FIX_1_175875602;	// sqrt(2) * c3

		tmp4 *= FIX_0_298631336;	// sqrt(2) * (-c1+c3+c5-c7)
		tmp5 *= FIX_2_053119869;	// sqrt(2) * ( c1+c3-c5+c7)
		tmp6 *= FIX_3_072711026;	// sqrt(2) * ( c1+c3+c5-c7)
		tmp7 *= FIX_1_501321110;	// sqrt(2) * ( c1+c3-c5-c7)
		z1 *= 0 - FIX_0_899976223;	// sqrt(2) * (c7-c3)
		z2 *= 0 - FIX_2_562915447;	// sqrt(2) * (-c1-c3)
		z3 *= 0 - FIX_1_961570560;	// sqrt(2) * (-c3-c5)
		z4 *= 0 - FIX_0_390180644;	// sqrt(2) * (c5-c3)

		z3 += z5;
		z4 += z5;

		n = tmp4 + z1 + z3 + 131072;
		dataUnit[c7] = n >> 18;
		if ((n < 0) && ((n & 0x3FFFF) != 0)) dataUnit[c7]--;
		n = tmp5 + z2 + z4 + 131072;
		dataUnit[c5] = n >> 18;
		if ((n < 0) && ((n & 0x3FFFF) != 0)) dataUnit[c5]--;
		n = tmp6 + z2 + z3 + 131072;
		dataUnit[c3] = n >> 18;
		if ((n < 0) && ((n & 0x3FFFF) != 0)) dataUnit[c3]--;
		n = tmp7 + z1 + z4 + 131072;
		dataUnit[c1] = n >> 18;
		if ((n < 0) && ((n & 0x3FFFF) != 0)) dataUnit[c1]--;
	}
}

	@Override
	boolean isFileFormat(LEDataInputStream stream) throws IOException {
		JPEGStartOfImage soi = new JPEGStartOfImage(stream);
		stream.unread(soi.reference);
		return soi.verify(); // we no longer check for appN
	}

@Override
ImageData[] loadFromByteStream() {
	return JPEGDecoder.loadFromByteStream(inputStream, loader);
}
void quantizeData(int[] dataUnit, int iComp) {
	int[] qTable = quantizationTables[frameComponents[componentIds[iComp]][TQI]];
	for (int i = 0; i < dataUnit.length; i++) {
		int zzIndex = ZigZag8x8[i];
		int data = dataUnit[zzIndex];
		int absData = data < 0 ? 0 - data : data;
		int qValue = qTable[i];
		int q2 = qValue >> 1;
		absData += q2;
		if (absData < qValue) {
			dataUnit[zzIndex] = 0;
		} else {
			absData /= qValue;
			if (data >= 0) {
				dataUnit[zzIndex] = absData;
			} else {
				dataUnit[zzIndex] = 0 - absData;
			}
		}
	}
}
void resetOutputBuffer() {
	if (dataBuffer == null) {
		dataBuffer = new byte[512];
	} else {
		try {
			outputStream.write(dataBuffer, 0, bufferCurrentPosition);
		} catch (IOException e) {
			SWT.error(SWT.ERROR_IO, e);
		}
	}
	bufferCurrentPosition = 0;
}
@Override
void unloadIntoByteStream(ImageLoader loader) {
	ImageData image = loader.data[0];
	if (!new JPEGStartOfImage().writeToStream(outputStream)) {
		SWT.error(SWT.ERROR_IO);
	}
	JPEGAppn appn = new JPEGAppn(new byte[] {(byte)0xFF, (byte)0xE0, 0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0});
	if (!appn.writeToStream(outputStream)) {
		SWT.error(SWT.ERROR_IO);
	}
	quantizationTables = new int[4][];
	JPEGQuantizationTable chromDQT = JPEGQuantizationTable.defaultChrominanceTable();
	int encoderQFactor = loader.compression >= 1 && loader.compression <= 100 ? loader.compression : 75;
	chromDQT.scaleBy(encoderQFactor);
	int[] jpegDQTKeys = chromDQT.getQuantizationTablesKeys();
	int[][] jpegDQTValues = chromDQT.getQuantizationTablesValues();
	for (int i = 0; i < jpegDQTKeys.length; i++) {
		quantizationTables[jpegDQTKeys[i]] = jpegDQTValues[i];
	}
	JPEGQuantizationTable lumDQT = JPEGQuantizationTable.defaultLuminanceTable();
	lumDQT.scaleBy(encoderQFactor);
	jpegDQTKeys = lumDQT.getQuantizationTablesKeys();
	jpegDQTValues = lumDQT.getQuantizationTablesValues();
	for (int i = 0; i < jpegDQTKeys.length; i++) {
		quantizationTables[jpegDQTKeys[i]] = jpegDQTValues[i];
	}
	if (!lumDQT.writeToStream(outputStream)) {
		SWT.error(SWT.ERROR_IO);
	}
	if (!chromDQT.writeToStream(outputStream)) {
		SWT.error(SWT.ERROR_IO);
	}
	int frameLength, scanLength, precision;
	int[][] frameParams, scanParams;
	if (image.depth == 1) {
		frameLength = 11;
		frameParams = new int[1][];
		frameParams[0] = new int[] {1, 1, 1, 0, 0};
		scanParams = new int[1][];
		scanParams[0] = new int[] {0, 0};
		scanLength = 8;
		nComponents = 1;
		precision = 1;
	} else {
		frameLength = 17;
		frameParams = new int[3][];
		frameParams[0] = new int[] {0, 2, 2, 0, 0};
		frameParams[1] = new int[] {1, 1, 1, 0, 0};
		frameParams[2] = new int[] {1, 1, 1, 0, 0};
		scanParams = new int[3][];
		scanParams[0] = new int[] {0, 0};
		scanParams[1] = new int[] {1, 1};
		scanParams[2] = new int[] {1, 1};
		scanLength = 12;
		nComponents = 3;
		precision = 8;
	}
	imageWidth = image.width;
	imageHeight = image.height;
	frameHeader = new JPEGFrameHeader(new byte[19]);
	frameHeader.setSegmentMarker(SOF0);
	frameHeader.setSegmentLength(frameLength);
	frameHeader.setSamplePrecision(precision);
	frameHeader.setSamplesPerLine(imageWidth);
	frameHeader.setNumberOfLines(imageHeight);
	frameHeader.setNumberOfImageComponents(nComponents);
	frameHeader.componentParameters = frameParams;
	frameHeader.componentIdentifiers = new int[] {0, 1, 2};
	frameHeader.initializeContents();
	if (!frameHeader.writeToStream(outputStream)) {
		SWT.error(SWT.ERROR_IO);
	}
	frameComponents = frameParams;
	componentIds = frameHeader.componentIdentifiers;
	maxH = frameHeader.getMaxHFactor();
	maxV = frameHeader.getMaxVFactor();
	int mcuWidth = maxH * DCTSIZE;
	int mcuHeight = maxV * DCTSIZE;
	interleavedMcuCols = (imageWidth + mcuWidth - 1) / mcuWidth;
	interleavedMcuRows = (imageHeight + mcuHeight - 1) / mcuHeight;
	acHuffmanTables = new JPEGHuffmanTable[4];
	dcHuffmanTables = new JPEGHuffmanTable[4];
	JPEGHuffmanTable[] dhtTables = new JPEGHuffmanTable[] {
		JPEGHuffmanTable.getDefaultDCLuminanceTable(),
		JPEGHuffmanTable.getDefaultDCChrominanceTable(),
		JPEGHuffmanTable.getDefaultACLuminanceTable(),
		JPEGHuffmanTable.getDefaultACChrominanceTable()
	};
	for (JPEGHuffmanTable dhtTable : dhtTables) {
		if (!dhtTable.writeToStream(outputStream)) {
			SWT.error(SWT.ERROR_IO);
		}
		for (JPEGHuffmanTable huffmanTable : dhtTable.getAllTables()) {
			if (huffmanTable.getTableClass() == 0) {
				dcHuffmanTables[huffmanTable.getTableIdentifier()] = huffmanTable;
			} else {
				acHuffmanTables[huffmanTable.getTableIdentifier()] = huffmanTable;
			}
		}
	}
	precedingDCs = new int[4];
	scanHeader = new JPEGScanHeader(new byte[14]);
	scanHeader.setSegmentMarker(SOS);
	scanHeader.setSegmentLength(scanLength);
	scanHeader.setNumberOfImageComponents(nComponents);
	scanHeader.setStartOfSpectralSelection(0);
	scanHeader.setEndOfSpectralSelection(63);
	scanHeader.componentParameters = scanParams;
	scanHeader.initializeContents();
	if (!scanHeader.writeToStream(outputStream)) {
		SWT.error(SWT.ERROR_IO);
	}
	convertImageToYCbCr(image);
	resetOutputBuffer();
	currentByte = 0;
	currentBitCount = 0;
	encodeScan();
	if (!new JPEGEndOfImage().writeToStream(outputStream)) {
		SWT.error(SWT.ERROR_IO);
	}
}
}
