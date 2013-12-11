package org.opensourcephysics.media.xuggle;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

import org.opensourcephysics.media.core.VideoIO;
import org.opensourcephysics.tools.ResourceLoader;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IVideoPictureEvent;

  /**
   * A class to create thumbnail images of videos.
   */
public class XuggleThumbnailTool extends MediaToolAdapter {
	
	private static final XuggleThumbnailTool THUMBNAIL_TOOL = new XuggleThumbnailTool();
	private static final int TARGET_FRAME_NUMBER = 15;
	
	private BufferedImage thumbnail;
	private Graphics2D g;
	private boolean finished;
	private int frameNumber;
	private BufferedImage overlay;
	private Dimension dim;
  
  /**
   * "Starts" this tool--called by XuggleVideoType so minijar will include it
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
    IMediaReader mediaReader = ToolFactory.makeReader(path);
    mediaReader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
    mediaReader.addListener(THUMBNAIL_TOOL);
    while (!THUMBNAIL_TOOL.isFinished() && mediaReader.readPacket()==null); // reads video until a thumbnail is created
    mediaReader.close();
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
   * Creates a thumbnail image from the video image passed in by an IMediaReader. 
   * @param event the IVideoPictureEvent from the mediaReader
   */
  @Override
  public void onVideoPicture(IVideoPictureEvent event) {
  	if (!isFinished()) {
      BufferedImage image = event.getImage();
      
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
      
    // call parent which will pass the video onto next tool in chain
    super.onVideoPicture(event);
      
  }
    
  private void initialize(Dimension dimension) {
  	dim = dimension;
		finished = false;
		frameNumber = 0;
//    try {
//    	String imageFile = "C:/Program Files (x86)/Tracker/tracker_icon.png";
//    	overlay = ImageIO.read(new File(imageFile));
//	  } 
//	  catch (IOException e) {
//	      e.printStackTrace();
//	      throw new RuntimeException("Could not open file");
//	  }

	}
		
  private boolean isFinished() {
		return finished;
	}
		
}
