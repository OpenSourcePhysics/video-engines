package org.opensourcephysics.media.ffmpeg;

import static org.ffmpeg.avutil.AvutilLibrary.av_freep;
import static org.ffmpeg.avutil.AvutilLibrary.av_image_alloc;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.bridj.IntValuedEnum;
import org.bridj.Pointer;
import org.ffmpeg.avcodec.AVPicture;
import org.ffmpeg.avutil.AvutilLibrary.AVPixelFormat;
import org.ffmpeg.swscale.SwscaleLibrary;
import org.ffmpeg.swscale.SwscaleLibrary.SwsContext;
import org.opensourcephysics.controls.OSPLog;

/**
 * A converter to translate {@link AVPicture}s to and from {@link BufferedImage}
 * s of type {@link BufferedImage#TYPE_3BYTE_BGR}.
 */

public class BgrConverter {
	// band offsets requried by the sample model

	private static final int[] mBandOffsets = { 2, 1, 0 };

	// color space for this converter

	private static final ColorSpace mColorSpace = ColorSpace
			.getInstance(ColorSpace.CS_sRGB);

	private SampleModel sm;
	private ColorModel colorModel;

	// input picture pixel format
	IntValuedEnum<AVPixelFormat> pixfmt;
	// data structure needed for resampling
	Pointer<SwsContext> resampler;
	Pointer<Pointer<Byte>> rpicture;
	Pointer<Integer> rpicture_linesize;
	int rpicture_bufsize;

	public BgrConverter(IntValuedEnum<AVPixelFormat> pixfmt, int w, int h)
			throws IOException {
		sm = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, w, h, 3,
				3 * w, mBandOffsets);
		colorModel = new ComponentColorModel(mColorSpace, false, false,
				ColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
		this.pixfmt = pixfmt;
		if (pixfmt != AVPixelFormat.AV_PIX_FMT_BGR24) {
			resampler = SwscaleLibrary.sws_getContext(w, h, pixfmt, w, h,
					AVPixelFormat.AV_PIX_FMT_BGR24,
					SwscaleLibrary.SWS_BILINEAR, null, null, null);
			if (resampler == null) {
				OSPLog.warning("Could not create color space resampler"); //$NON-NLS-1$
				throw new IOException("Could not create color space resampler.");
			}
			rpicture = Pointer.allocatePointers(Byte.class, 4);
			rpicture_linesize = Pointer.allocateInts(4);
			rpicture_bufsize = av_image_alloc(rpicture, rpicture_linesize, w,
					h, AVPixelFormat.AV_PIX_FMT_BGR24, 1);
			if (rpicture_bufsize < 0) {
				OSPLog.warning("Could not allocate BGR24 picture memory");
				throw new IOException(
						"Could not allocate BGR24 picture memory.");
			}
		} else {
			resampler = null;
			rpicture = null;
			rpicture_linesize = null;
			rpicture_bufsize = 0;
		}
	}

	public BufferedImage toImage(Pointer<Pointer<Byte>> picture,
			Pointer<Integer> picture_linesize, int size) {
		if (resampler != null) {
			if (SwscaleLibrary.sws_scale(resampler, picture, picture_linesize,
					0, sm.getHeight(), rpicture, rpicture_linesize) < 0) {
				OSPLog.warning("Could not encode video as BGR24"); //$NON-NLS-1$
				return null;
			}
		}
		// make a copy of the raw bytes int a DataBufferByte which the
		// writable raster can operate on

		final ByteBuffer byteBuf = ByteBuffer.wrap(rpicture == null ? picture
				.get().getBytes(size) : rpicture.get().getBytes(
				rpicture_bufsize));
		final byte[] bytes = new byte[rpicture == null ? size : rpicture_bufsize];
		byteBuf.get(bytes, 0, bytes.length);

		// create the data buffer from the bytes
		final DataBufferByte db = new DataBufferByte(bytes, bytes.length);

		// create an a sample model which matches the byte layout of the
		// image data and raster which contains the data which now can be
		// properly interpreted
		final WritableRaster wr = Raster.createWritableRaster(sm, db, null);

		// return a new image created from the color model and raster
		return new BufferedImage(colorModel, wr, false, null);
	}

	public void dispose() {
		if (rpicture != null) {
			if(rpicture.getValidElements() > 0)
				av_freep(rpicture);
			rpicture = null;
		}
		rpicture_linesize = null;
		if (resampler != null) {
			SwscaleLibrary.sws_freeContext(resampler);
			resampler = null;
		}		
	}
}
