
package org.opensourcephysics.media.ffmpeg;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBuffer;
import java.awt.image.ColorModel;
import java.awt.color.ColorSpace;
import java.awt.image.ComponentColorModel;
import java.awt.image.SampleModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.WritableRaster;
import java.awt.image.Raster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicReference;

import org.bridj.Pointer;
import org.ffmpeg.avutil.AvutilLibrary.AVPixelFormat;

/** A converter to translate {@link AVPicture}s to and from
 * {@link BufferedImage}s of type {@link BufferedImage#TYPE_3BYTE_BGR}. */

public class BgrConverter
{
  // band offsets requried by the sample model
    
  private static final int[] mBandOffsets = {2, 1, 0};

  // color space for this converter
  
  private static final ColorSpace mColorSpace = 
    ColorSpace.getInstance(ColorSpace.CS_sRGB);

  private SampleModel sm;
  private ColorModel colorModel;
  
  public BgrConverter(int w, int h) {
	  sm = new PixelInterleavedSampleModel(
		      DataBuffer.TYPE_BYTE, w, h, 3, 3 * w, mBandOffsets);
	  colorModel = new ComponentColorModel(
		      mColorSpace, false, false, ColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
  }
  
  public BufferedImage toImage(Pointer<Pointer<Byte>> picture, int size)
  {
    // make a copy of the raw bytes int a DataBufferByte which the
    // writable raster can operate on

    final ByteBuffer byteBuf = ByteBuffer.wrap(picture.get().getBytes(size));
    final byte[] bytes = new byte[size];
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

}
