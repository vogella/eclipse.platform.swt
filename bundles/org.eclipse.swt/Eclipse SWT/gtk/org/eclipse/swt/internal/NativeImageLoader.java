/*******************************************************************************
 * Copyright (c) 2019, 2026 Red Hat and others. All rights reserved.
 * The contents of this file are made available under the terms
 * of the GNU Lesser General Public License (LGPL) Version 2.1 that
 * accompanies this distribution (lgpl-v21.txt).  The LGPL is also
 * available at http://www.gnu.org/licenses/lgpl.html.  If the version
 * of the LGPL at http://www.gnu.org is different to the version of
 * the LGPL accompanying this distribution and there is any conflict
 * between the two license versions, the terms of the LGPL accompanying
 * this distribution shall govern.
 *
 * Contributors:
 *     Red Hat - initial implementation
 *     Hannes Wellmann - Unify ImageLoader implementations and extract differences into InternalImageLoader
 *******************************************************************************/
package org.eclipse.swt.internal;

import java.io.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.DPIUtil.*;
import org.eclipse.swt.internal.gtk.*;
import org.eclipse.swt.internal.image.*;
import org.eclipse.swt.widgets.*;

public class NativeImageLoader {

	/** If the 29th byte of the PNG file is not zero, then it is interlaced. */
	private static final int PNG_INTERLACE_METHOD_OFFSET = 28;

	// --- loading ---

	public static List<ElementAtZoom<ImageData>> load(ElementAtZoom<InputStream> streamAtZoom, ImageLoader imageLoader, int targetZoom) {
		// 1) Load InputStream into byte array
		byte[] data_buffer;
		InputStream stream = streamAtZoom.element();
		try (stream) {
			data_buffer = stream.readAllBytes();
		} catch (IOException e) {
			SWT.error(SWT.ERROR_IO);
			return null;
		}
		if (data_buffer.length == 0) {
			SWT.error(SWT.ERROR_UNSUPPORTED_FORMAT); // empty stream
		}
		InputStream stream2 = new ByteArrayInputStream(data_buffer);
		if (FileFormat.isDynamicallySizableFormat(stream2)) {
			try {
				stream2.reset();
			} catch (IOException e) {
				SWT.error(SWT.ERROR_IO);
			}
			return FileFormat.load(new ElementAtZoom<>(stream2, streamAtZoom.zoom()), imageLoader, targetZoom);
		}
		if (isIcoFile(data_buffer)) {
			// GdkPixbuf returns only one of the icons of an ICO file, and none (or an empty
			// one) when it holds PNG compressed icons, so use the Java decoder like win32 and
			// cocoa do: it returns every icon, BMP or PNG.
			return FileFormat.load(new ElementAtZoom<>(stream2, streamAtZoom.zoom()), imageLoader, targetZoom);
		}
		List<ImageData> imgDataList = new ArrayList<>();
		long loader = GDK.gdk_pixbuf_loader_new();
		// 2) Copy byte array to C memory, write to GdkPixbufLoader
		long buffer_ptr = OS.g_malloc(data_buffer.length);
		try {
			C.memmove(buffer_ptr, data_buffer, data_buffer.length);
			long[] error = new long[1];
			GDK.gdk_pixbuf_loader_write(loader, buffer_ptr, data_buffer.length, error);
			GDK.gdk_pixbuf_loader_close(loader, null);
			if (error[0] != 0) {
				// Bug 576484
				// It is safe just to assume if this fails it is most likely an IO error
				// since unsupported format is checked before, and invalid image right after.
				// Still, check if it belongs to the G_FILE_ERROR domain and IO error code
				if (OS.g_error_matches(error[0], OS.g_file_error_quark(), OS.G_FILE_ERROR_IO)) {
					SWT.error(SWT.ERROR_IO, null, Display.extractFreeGError(error[0]));
				} else {
					OS.g_error_free(error[0]);
				}
			}

			// 3) Get GdkPixbufAnimation from loader
			long pixbuf_animation = GDK.gdk_pixbuf_loader_get_animation(loader);
			if (pixbuf_animation == 0) {
				SWT.error(SWT.ERROR_INVALID_IMAGE);
			}
			boolean isStatic = GDK.gdk_pixbuf_animation_is_static_image(pixbuf_animation);
			if (isStatic) {
				// Static image, get as single pixbuf and convert it to ImageData
				long pixbuf = GDK.gdk_pixbuf_animation_get_static_image(pixbuf_animation);
				ImageData imgData = pixbufToImageData(pixbuf);
				imgData.type = getImageFormat(loader);
				imgDataList.add(imgData);
			} else {
				// Image with multiple frames, iterate through each frame and convert
				// each frame to ImageData
				long start_time = OS.g_malloc(16); // GTimeVal, two glongs
				OS.g_get_current_time(start_time);
				long animation_iter = GDK.gdk_pixbuf_animation_get_iter(pixbuf_animation, start_time);
				// GdkPixbufAnimation does not tell the number of frames and starts over after
				// the last one, so take it from the file. Other formats get at most 32 frames.
				int gifFrames = countGifFrames(data_buffer);
				int num_frames = gifFrames > 0 ? gifFrames : 32;
				int delay_time = 0;
				for (int i = 0; i < num_frames; i++) {
					if (i > 0) {
						// A negative delay means that the previous frame is shown forever
						if (delay_time < 0) break;
						OS.g_time_val_add(start_time, delay_time * 1000L);
						if (!GDK.gdk_pixbuf_animation_iter_advance(animation_iter, start_time)) break;
					}
					delay_time = GDK.gdk_pixbuf_animation_iter_get_delay_time(animation_iter);
					long curr_pixbuf = GDK.gdk_pixbuf_animation_iter_get_pixbuf(animation_iter);
					long pixbuf_copy = GDK.gdk_pixbuf_copy(curr_pixbuf); // copy because curr_pixbuf might get disposed
																			// on next advance
					ImageData imgData = pixbufToImageData(pixbuf_copy);
					if (imageLoader.logicalScreenHeight == 0 && imageLoader.logicalScreenWidth == 0) {
						imageLoader.logicalScreenHeight = imgData.height;
						imageLoader.logicalScreenWidth = imgData.width;
					}
					OS.g_object_unref(pixbuf_copy);
					imgData.type = getImageFormat(loader);
					// GdkPixbuf counts in milliseconds, ImageData in hundredths of a second
					imgData.delayTime = Math.max(0, delay_time) / 10;
					imgDataList.add(imgData);
				}
				OS.g_object_unref(animation_iter);
				OS.g_free(start_time);
			}
		} finally {
			// The loader keeps no reference to the written buffer, and owns the animation
			OS.g_object_unref(loader);
			OS.g_free(buffer_ptr);
		}
		for (int i = 0; i < imgDataList.size(); i++) {
			// Loading completed, notify listeners
			// listener should only be called when loading interlaced/progressive
			// PNG/JPG/GIF ?
			ImageData data = (ImageData) imgDataList.get(i).clone();
			if (imageLoader.hasListeners()) {
				if (data.type == SWT.IMAGE_PNG && isInterlacedPNG(data_buffer)) {
					imageLoader.notifyListeners(new ImageLoaderEvent(imageLoader, data, i, true));
				} else if (data.type != SWT.IMAGE_PNG) {
					imageLoader.notifyListeners(new ImageLoaderEvent(imageLoader, data, i, true));
				}
			}
		}
		return imgDataList.stream().map(data -> new ElementAtZoom<>(data, streamAtZoom.zoom())).toList();
	}

	private static boolean isIcoFile(byte[] data) {
		return data.length >= 4 && data[0] == 0 && data[1] == 0 && data[2] == 1 && data[3] == 0;
	}

	/**
	 * Answer the number of images in a GIF file, or -1 if the data is not a GIF file
	 * or cannot be parsed.
	 */
	static int countGifFrames(byte[] data) {
		if (data.length < 13 || data[0] != 'G' || data[1] != 'I' || data[2] != 'F') return -1;
		int index = 13;
		if ((data[10] & 0x80) != 0) index += 3 << ((data[10] & 0x7) + 1); // global color table
		int frames = 0;
		while (index < data.length) {
			switch (data[index++]) {
				case 0x21: // extension: label, then data sub-blocks
					index++;
					break;
				case 0x2C: // image descriptor, optional local color table, LZW code size, then data sub-blocks
					if (index + 9 > data.length) return -1;
					int flags = data[index + 8];
					index += 9;
					if ((flags & 0x80) != 0) index += 3 << ((flags & 0x7) + 1);
					index++;
					frames++;
					break;
				case 0x3B: // trailer
					return frames;
				default:
					return frames > 0 ? frames : -1;
			}
			while (index < data.length && data[index] != 0) {
				index += (data[index] & 0xFF) + 1;
			}
			index++;
		}
		return frames > 0 ? frames : -1;
	}

	public static ImageData load(InputStream streamAtZoom, ImageLoader imageLoader, int width, int height) {
		return FileFormat.load(streamAtZoom, imageLoader, width, height);
	}

	/**
	 * Return true if the image is an interlaced PNG file. This is used to check
	 * whether ImageLoaderEvent should be fired when loading images.
	 *
	 * @return true iff 29th byte of PNG files is not zero
	 */
	private static boolean isInterlacedPNG(byte[] imageAsByteArray) {
		return imageAsByteArray.length > PNG_INTERLACE_METHOD_OFFSET
				&& imageAsByteArray[PNG_INTERLACE_METHOD_OFFSET] != 0;
	}

	/**
	 * Return the type of file from which the image was read by inspecting
	 * GdkPixbufFormat from GdkPixbufLoader
	 *
	 * It is expressed as one of the following values:
	 * <dl>
	 * <dt>{@link SWT#IMAGE_BMP}</dt>
	 * <dd>Windows BMP file format, no compression</dd>
	 * <dt>{@link SWT#IMAGE_BMP_RLE}</dt>
	 * <dd>Windows BMP file format, RLE compression if appropriate</dd>
	 * <dt>{@link SWT#IMAGE_GIF}</dt>
	 * <dd>GIF file format</dd>
	 * <dt>{@link SWT#IMAGE_ICO}</dt>
	 * <dd>Windows ICO file format</dd>
	 * <dt>{@link SWT#IMAGE_JPEG}</dt>
	 * <dd>JPEG file format</dd>
	 * <dt>{@link SWT#IMAGE_PNG}</dt>
	 * <dd>PNG file format</dd>
	 * <dt>{@link SWT#IMAGE_TIFF}</dt>
	 * <dd>TIFF file format</dd>
	 * </dl>
	 */
	private static int getImageFormat(long loader) {
		long format = GDK.gdk_pixbuf_loader_get_format(loader);
		long name = GDK.gdk_pixbuf_format_get_name(format);
		String nameStr = Converter.cCharPtrToJavaString(name, false);
		OS.g_free(name);
		return switch (nameStr) {
		case "bmp" -> SWT.IMAGE_BMP;
		case "gif" -> SWT.IMAGE_GIF;
		case "ico" -> SWT.IMAGE_ICO;
		case "jpeg" -> SWT.IMAGE_JPEG;
		case "png" -> SWT.IMAGE_PNG;
		case "tiff" -> SWT.IMAGE_TIFF;
		case "svg" -> SWT.IMAGE_SVG;
		default -> SWT.IMAGE_UNDEFINED;
		};
	}

	/**
	 * Convert GdkPixbuf pointer to Java object ImageData
	 *
	 * @return ImageData with pixbuf data
	 */
	private static ImageData pixbufToImageData(long pixbuf) {
		boolean hasAlpha = GDK.gdk_pixbuf_get_has_alpha(pixbuf);
		int width = GDK.gdk_pixbuf_get_width(pixbuf);
		int height = GDK.gdk_pixbuf_get_height(pixbuf);
		int stride = GDK.gdk_pixbuf_get_rowstride(pixbuf);
		int n_channels = GDK.gdk_pixbuf_get_n_channels(pixbuf); // only 3 or 4 samples per pixel are supported
		int bits_per_sample = GDK.gdk_pixbuf_get_bits_per_sample(pixbuf); // only 8 bit per sample are supported
		long pixels = GDK.gdk_pixbuf_get_pixels(pixbuf);
		/*
		 * From GDK Docs: last row in the pixbuf may not be as wide as the full
		 * rowstride, but rather just as wide as the pixel data needs to be. Compute the
		 * width in bytes of the last row to copy raw pixbuf data.
		 */
		int lastRowWidth = width * ((n_channels * bits_per_sample + 7) / 8);
		// The rowstride is not always 4-byte aligned (e.g. with glycin), but ImageData expects that
		int bytesPerLine = (lastRowWidth + 3) / 4 * 4;
		byte[] srcData = new byte[bytesPerLine * height];
		if (stride == bytesPerLine) {
			C.memmove(srcData, pixels, stride * (height - 1) + lastRowWidth);
		} else {
			byte[] row = new byte[lastRowWidth];
			for (int y = 0; y < height; y++) {
				C.memmove(row, pixels + (long) y * stride, lastRowWidth);
				System.arraycopy(row, 0, srcData, y * bytesPerLine, lastRowWidth);
			}
		}
		/*
		 * Note: GdkPixbuf only supports 3/4 n_channels and 8 bits_per_sample, This
		 * means all images are of depth 24 / depth 32. This means loading images will
		 * result in a direct PaletteData with RGB masks, since there is no way to
		 * determine indexed PaletteData info.
		 *
		 * See https://www.eclipse.org/articles/Article-SWT-images/graphics-resources.html#PaletteData
		 */
		PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
		ImageData imgData = new ImageData(width, height, bits_per_sample * n_channels, palette, 4, srcData);
		if (hasAlpha) {
			byte[] alphaData = imgData.alphaData = new byte[width * height];
			for (int y = 0, offset = 0, alphaOffset = 0; y < height; y++) {
				for (int x = 0; x < width; x++, offset += n_channels) {
					byte r = srcData[offset + 0];
					byte g = srcData[offset + 1];
					byte b = srcData[offset + 2];
					byte a = srcData[offset + 3];
					srcData[offset + 0] = 0;
					alphaData[alphaOffset++] = a;
					if (a != 0) {
						srcData[offset + 1] = r;
						srcData[offset + 2] = g;
						srcData[offset + 3] = b;
					}
				}
			}
		}
		return imgData;
	}

	// --- saving ---

	public static void save(OutputStream stream, int format, ImageLoader imageLoader) {
		if (format == -1) {
			SWT.error(SWT.ERROR_UNSUPPORTED_FORMAT);
		}
		if (imageLoader.data == null) {
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}
		ImageData imgData = imageLoader.data[0];
		int colorspace = GDK.GDK_COLORSPACE_RGB;
		boolean alpha_supported = format == SWT.IMAGE_TIFF || format == SWT.IMAGE_PNG || format == SWT.IMAGE_ICO;
		boolean has_alpha = imgData.alphaData != null && alpha_supported;
		int width = imgData.width;
		int height = imgData.height;
		PaletteData palette = imgData.palette;

		// Native implementation only supports a subset of possible image configurations.
		// Redirect the not supported variants to the Java implementation.
		// See also https://bugs.eclipse.org/bugs/show_bug.cgi?id=558043
		if (!palette.isDirect || (imgData.depth != 24 && imgData.depth != 32)) {
			FileFormat.save(stream, format, imageLoader);
			return;
		}

		/*
		 * Build the pixel buffer for the GdkPixbuf, which stores pixels as RGB or RGBA
		 * bytes. The channels are taken from the pixel values through the palette masks,
		 * because the byte order of ImageData.data depends on the palette and its rows
		 * may be padded to the scanline pad.
		 *
		 * Formats that support an alpha channel are saved as 32 bit RGBA, all others as
		 * 24 bit RGB. The pixbuf must not carry an alpha channel for formats that don't
		 * support it (e.g. JPEG), otherwise the encoder fails and produces an empty
		 * result. When the format supports alpha but the source has none, the alpha
		 * channel is filled with 255.
		 */
		int bytes_per_pixel = alpha_supported ? 4 : 3;
		byte[] srcData = new byte[(width * height * bytes_per_pixel)];
		int redMask = palette.redMask, redShift = palette.redShift;
		int greenMask = palette.greenMask, greenShift = palette.greenShift;
		int blueMask = palette.blueMask, blueShift = palette.blueShift;
		int[] pixels = new int[width];
		for (int y = 0, new_offset = 0, alphaIndex = 0; y < height; y++) {
			imgData.getPixels(0, y, width, pixels, 0);
			for (int x = 0; x < width; x++, new_offset += bytes_per_pixel) {
				int pixel = pixels[x];
				int r = pixel & redMask;
				int g = pixel & greenMask;
				int b = pixel & blueMask;
				srcData[new_offset] = (byte) (redShift < 0 ? r >>> -redShift : r << redShift);
				srcData[new_offset + 1] = (byte) (greenShift < 0 ? g >>> -greenShift : g << greenShift);
				srcData[new_offset + 2] = (byte) (blueShift < 0 ? b >>> -blueShift : b << blueShift);
				if (alpha_supported) {
					srcData[new_offset + 3] = has_alpha ? imgData.alphaData[alphaIndex++] : (byte) 255;
				}
			}
		}

		// Get GdkPixbuf from pixel data buffer
		long buffer_ptr = OS.g_malloc(srcData.length);
		C.memmove(buffer_ptr, srcData, srcData.length);
		int rowstride = srcData.length / height;
		long pixbuf = GDK.gdk_pixbuf_new_from_data(buffer_ptr, colorspace, alpha_supported, 8, width, height, rowstride, 0, 0);
		if (pixbuf == 0) {
			OS.g_free(buffer_ptr);
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		}

		// Write pixbuf to byte array and then to OutputStream
		String typeStr = switch (format) {
		case SWT.IMAGE_BMP_RLE -> "bmp";
		case SWT.IMAGE_BMP -> "bmp";
		case SWT.IMAGE_GIF -> "gif";
		case SWT.IMAGE_ICO -> "ico";
		case SWT.IMAGE_JPEG -> "jpeg";
		case SWT.IMAGE_PNG -> "png";
		case SWT.IMAGE_TIFF -> "tiff";
		case SWT.IMAGE_SVG -> "svg";
		default -> "";
		};
		byte[] type = Converter.wcsToMbcs(typeStr, true);

		long[] buffer = new long[1];
		if (typeStr.isEmpty()) {
			OS.g_object_unref(pixbuf);
			OS.g_free(buffer_ptr);
			SWT.error(SWT.ERROR_UNSUPPORTED_FORMAT);
		}
		long[] len = new long[1];
		boolean saved = GDK.gdk_pixbuf_save_to_bufferv(pixbuf, buffer, len, type, null, null, null);
		if (!saved || buffer[0] == 0) {
			OS.g_object_unref(pixbuf);
			OS.g_free(buffer_ptr);
			SWT.error(SWT.ERROR_INVALID_IMAGE);
		}
		byte[] byteArray = new byte[(int) len[0]];
		C.memmove(byteArray, buffer[0], byteArray.length);
		// Free the buffer allocated by gdk_pixbuf_save_to_bufferv to avoid an off-heap memory leak
		OS.g_free(buffer[0]);
		try {
			stream.write(byteArray);
		} catch (IOException e) {
			OS.g_object_unref(pixbuf);
			OS.g_free(buffer_ptr);
			SWT.error(SWT.ERROR_IO);
		}
		OS.g_object_unref(pixbuf);
		// must free buffer_ptr last otherwise we get half/corrupted image
		OS.g_free(buffer_ptr);
	}

}
