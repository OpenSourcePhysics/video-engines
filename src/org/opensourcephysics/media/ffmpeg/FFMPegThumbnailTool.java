package org.opensourcephysics.media.ffmpeg;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import org.opensourcephysics.media.core.VideoIO;
import org.opensourcephysics.tools.ResourceLoader;

  /**
   * A class to create thumbnail images of videos.
   */
public class FFMPegThumbnailTool {
	
	private static final FFMPegThumbnailTool THUMBNAIL_TOOL = new FFMPegThumbnailTool();
	private static final int TARGET_FRAME_NUMBER = 15;
	
	private BufferedImage thumbnail;
	private Graphics2D g;
	private boolean finished;
	private int frameNumber;
	private BufferedImage overlay;
	private Dimension dim;
  
  /**
   * "Starts" this tool--called by FFMPegVideoType so minijar will include it
   */
  public static void start() {}
  
  /**
   * Attempts to create a new thumbnail image.
   * @param dim the dimension of the image
   * @param pathToVideo the path to the source video file
   * @return the thumbnail image, or null if failed
   */
  public static synchronized BufferedImage createThumbnailImage(Dimension dim, String pathToVideo) {
  	THUMBNAIL_TOOL.initialize(dim);
  	String path = pathToVideo.startsWith("http:")? ResourceLoader.getURIPath(pathToVideo): pathToVideo; //$NON-NLS-1$
  	THUMBNAIL_TOOL.finished = false;
  	FFMPegAnalyzer analyzer = null;
  	try {
  		analyzer = new FFMPegAnalyzer(path, true, TARGET_FRAME_NUMBER);
  	  	THUMBNAIL_TOOL.thumbnailFromPicture(analyzer.getThumbnail());
  	} catch (IOException e) { }
    return THUMBNAIL_TOOL.thumbnail;
  }
  
  /**
   * Attempts to create a new thumbnail file.
   * @param dim the dimension of the image
   * @param pathToVideo the path to the source video file 
   * @param pathToThumbnail the path to the desired thumbnail file
   * @return the thumbnail file, or null if failed
   */
  public static synchronized File createThumbnailFile(Dimension dim, String pathToVideo, String pathToThumbnail) {
  	BufferedImage thumb = createThumbnailImage(dim, pathToVideo);
    return VideoIO.writeImageFile(thumb, pathToThumbnail);
  }
  
  /**
   * Creates a thumbnail image from the video image passed in from a file. 
   * @param BufferedImage from the file
   */
  private void thumbnailFromPicture(BufferedImage image) {
  	if (!isFinished()) {
      
      double widthFactor = dim.getWidth()/image.getWidth();
      double heightFactor = dim.getHeight()/image.getHeight();
      double factor = Math.min(widthFactor, heightFactor);
      
      // determine actual dimensions of thumbnail
      int w = (int)(image.getWidth()*factor);
      int h = (int)(image.getHeight()*factor);
      
  		thumbnail = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
  		g = thumbnail.createGraphics();
      AffineTransform transform = AffineTransform.getScaleInstance(factor, factor);
      g.setTransform(transform); // shrink video image
      g.drawImage(image, 0, 0, null);
      
      if (overlay!=null) {
	    g.scale(1/factor, 1/factor); // draw overlay at full scale
	      
        // determine the inset and translate the image
        Rectangle2D bounds = new Rectangle2D.Float(0, 0, overlay.getWidth(), overlay.getHeight());
        double ht = bounds.getHeight();
        g.translate(0.5*ht, thumbnail.getHeight()-1.5*ht);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        g.drawImage(overlay, 0, 0, null);

      }
      frameNumber++;
      finished = frameNumber>=TARGET_FRAME_NUMBER;
  	}
  }
    
  private void initialize(Dimension dimension) {
  	dim = dimension;
		finished = false;
		frameNumber = 0;
    try {
    	URL imageURL = FFMPegThumbnailTool.class.getResource("../../resources/media/images/tracker_icon.png");
    	overlay = ImageIO.read(imageURL);
	  } 
	  catch (IllegalArgumentException e) { } 
	  catch (IOException e) {
	      e.printStackTrace();
	      throw new RuntimeException("Could not open file");
	  }

	}
		
  private boolean isFinished() {
		return finished;
	}
		
  public static void main(String[] args) {
	  if(args == null || args.length != 2) {
		  System.err.println("usage: FFMPegThumbnailTool <Videofile> <Thumbnailfile>");
	  }
	  createThumbnailFile(new Dimension(640,480),args[0], args[1]);
  }
}
