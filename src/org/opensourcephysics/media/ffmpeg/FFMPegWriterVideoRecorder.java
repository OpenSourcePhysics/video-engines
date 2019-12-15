package org.opensourcephysics.media.ffmpeg;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.filechooser.FileFilter;

import org.opensourcephysics.controls.XML;
import org.opensourcephysics.media.core.ScratchVideoRecorder;
import org.opensourcephysics.media.core.VideoFileFilter;
import org.opensourcephysics.tools.ResourceLoader;

/**
 * A class to record videos using a FFMPeg IMediaWriter.
 */
public class FFMPegWriterVideoRecorder extends ScratchVideoRecorder {

//	private IRational timebase = IRational.make(1, 9000);	
	private String tempFileBasePath;
	private String tempFileType = "png"; //$NON-NLS-1$

	/**
   * Constructs a FFMPegVideoRecorder object.
	 * @param type the video type
   */
  public FFMPegWriterVideoRecorder(FFMPegVideoType type) {
    super(type);
  }
  
  /**
   * Discards the current video and resets the recorder to a ready state.
   */
	@Override
  public void reset() {
    deleteTempFiles();
    super.reset();
    scratchFile = null;
  }

  /**
   * Called by the garbage collector when this recorder is no longer in use.
   */
	@Override
  protected void finalize() {
  	reset();
  }
  
  /**
   * Appends a frame to the current video by saving the image in a tempFile.
   *
   * @param image the image to append
   * @return true if image successfully appended
   */
	@Override
	protected boolean append(Image image) {
		int w = image.getWidth(null);
		int h = image.getHeight(null);
		if (dim==null || (!hasContent && (dim.width!=w || dim.height!=h))) {
			dim = new Dimension(w, h);
		}
		// resize and/or convert to BufferedImage if needed
		if (dim.width!=w || dim.height!=h || !(image instanceof BufferedImage)) {
			BufferedImage img = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_RGB);			
			int x = (dim.width-w)/2;
			int y = (dim.height-h)/2;
			img.getGraphics().drawImage(image, x, y, null);
			image = img;
		}
		BufferedImage source = (BufferedImage)image;
		String fileName = tempFileBasePath+"_"+tempFiles.size()+".tmp"; //$NON-NLS-1$ //$NON-NLS-2$
    try {
			ImageIO.write(source, tempFileType, new BufferedOutputStream(
			    new FileOutputStream(fileName)));
		} catch (Exception e) {
			return false;
		}
		File imageFile = new File(fileName);
		if (imageFile.exists()) {
			synchronized (tempFiles) {
				tempFiles.add(imageFile);
			}
			imageFile.deleteOnExit();
		}
		return true;
	}

  /**
   * Saves the video to the scratchFile.
   * 
   * @throws IOException
   */
	@Override
	protected void saveScratch() throws IOException {
		FileFilter fileFilter	=	videoType.getDefaultFileFilter();
		if (!hasContent || !(fileFilter instanceof VideoFileFilter))
			return;
		VideoFileFilter videoFilter = (VideoFileFilter)fileFilter;
		
		// set up the container format
//	TODO	IContainerFormat format = IContainerFormat.make();
//		format.setOutputFormat(videoFilter.getContainerType(), null, null);
//		
//    // get an appropriate codec for this format
//    ICodec codec = ICodec.guessEncodingCodec(format, null, null, null, ICodec.Type.CODEC_TYPE_VIDEO);
//    if (codec == null)
//      throw new UnsupportedOperationException("could not guess video codec");
//
//		// set scratch file extension to video type
//		String s = XML.stripExtension(scratchFile.getAbsolutePath())+"."+videoFilter.getDefaultExtension();
//		scratchFile = new File(s);
//		
//		// define frame rate
////		IRational frameRate = IRational.make(1000/frameDuration);
//		IRational frameRate = IRational.make(1000/33);
//		
//		System.out.println("frame rate "+frameRate);
//    // create mediaWriter and add a video stream with id 0, position 0, and fixed frame rate
//		IMediaWriter writer = ToolFactory.makeWriter(scratchFile.getAbsolutePath());
//    writer.addVideoStream(0, 0,	codec, frameRate, dim.width, dim.height);
		
		// open temp images and encode
		long timeStamp = 0;
		int n=0;
		synchronized (tempFiles) {
			for (File imageFile: tempFiles) {
				
				if (!imageFile.exists())
					throw new IOException("temp image file not found"); //$NON-NLS-1$
				
				BufferedImage image = ResourceLoader.getBufferedImage(imageFile.getAbsolutePath(), BufferedImage.TYPE_3BYTE_BGR);
				if (image==null || image.getType()!=BufferedImage.TYPE_3BYTE_BGR) {
					throw new IOException("unable to load temp image file"); //$NON-NLS-1$
				}
				
				System.out.println("adding frame "+n+" at "+timeStamp);
				
//        writer.encodeVideo(0, image, timeStamp, TimeUnit.NANOSECONDS);

				n++;
				timeStamp = Math.round(n*frameDuration*1000000); // frameDuration in ms, timestamp in microsec
			}
		}
//    writer.close();
		deleteTempFiles();
		hasContent = false;
		canRecord = false;
	}

  /**
   * Starts the video recording process.
   *
   * @return true if video recording successfully started
   */
	@Override
	protected boolean startRecording() {
		try {
			tempFileBasePath = XML.stripExtension(scratchFile.getAbsolutePath());
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Given the short name of a container, prints out information about
	 * it, including which codecs ffmpeg can write (mux) into that container.
	 * 
	 * @param name the short name of the format (e.g. "flv")
	 */
//	TODO public static void getSupportedCodecs(String name) {
//	  IContainerFormat format = IContainerFormat.make();
//	  format.setOutputFormat(name, null, null);
//	
//	  List<ID> codecs = format.getOutputCodecsSupported();
//	  if (codecs.isEmpty())
//	    System.out.println("no supported codecs for "+name); //$NON-NLS-1$
//	  else {
//	    System.out.println(name+" ("+format+") supports following codecs:"); //$NON-NLS-1$ //$NON-NLS-2$
//	    for(ID id : codecs) {
//	      if (id != null) {
//	        ICodec codec = ICodec.findEncodingCodec(id);
//	        if (codec != null) {
//	          System.out.println(codec);
//	        }
//	      }
//	    }
//	  }
//	}
	
}
