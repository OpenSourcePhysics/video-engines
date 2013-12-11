package org.opensourcephysics.media.xuggle;

import org.opensourcephysics.controls.OSPLog;
import org.opensourcephysics.media.core.VideoFileFilter;
import org.opensourcephysics.media.core.VideoIO;
import org.opensourcephysics.tools.ResourceLoader;

/**
 * This registers Xuggle with VideoIO so it can be used to open and record videos.
 *
 * @author Wolfgang Christian, Douglas Brown
 * @version 1.0
 */
public class XuggleIO {
	
	/**
   * Registers Xuggle video types with VideoIO class.
   */
  static public void registerWithVideoIO(){ // add Xuggle video types, if available
    String xugglehome = System.getenv("XUGGLE_HOME"); //$NON-NLS-1$
    if (xugglehome!=null) {
      try {
    	  VideoIO.addVideoEngine(new XuggleVideoType());
    	  // determine if using version 5.4
	  		boolean isXuggle54 = VideoIO.guessXuggleVersion()==5.4; // xuggle 5.4

        // add common video types shared with QuickTime
      	for (String ext: VideoIO.VIDEO_EXTENSIONS) { // {"mov", "avi", "mp4"}
        	VideoFileFilter filter = new VideoFileFilter(ext, new String[] {ext});
        	XuggleVideoType xuggleType = new XuggleVideoType(filter);
        	// avi not recordable with xuggle 3.4 or 5.4
          if (ext.equals("avi")) { //$NON-NLS-1$
          	xuggleType.setRecordable(false);
          }
        	// mov and mp4 not recordable with Xuggle 5.4
          if (isXuggle54 && (ext.equals("mov") || ext.equals("mp4"))) { //$NON-NLS-1$ //$NON-NLS-2$
          	xuggleType.setRecordable(false);
          }
          VideoIO.addVideoType(xuggleType);
          ResourceLoader.addExtractExtension(ext);
      	} 
      	// add additional xuggle types
      	// FLV
        VideoFileFilter filter = new VideoFileFilter("flv", new String[] {"flv"}); //$NON-NLS-1$ //$NON-NLS-2$
        VideoIO.addVideoType(new XuggleVideoType(filter));
        ResourceLoader.addExtractExtension("flv"); //$NON-NLS-1$
      	// WMV
      	filter = new VideoFileFilter("asf", new String[] {"wmv"}); //$NON-NLS-1$ //$NON-NLS-2$
        VideoIO.addVideoType(new XuggleVideoType(filter));
        ResourceLoader.addExtractExtension("wmv"); //$NON-NLS-1$
      	// DV
      	filter = new VideoFileFilter("dv", new String[] {"dv"}); //$NON-NLS-1$ //$NON-NLS-2$
      	XuggleVideoType vidType = new XuggleVideoType(filter);
      	vidType.setRecordable(false);
      	VideoIO.addVideoType(vidType);
        ResourceLoader.addExtractExtension("dv"); //$NON-NLS-1$
      	// MTS
      	filter = new VideoFileFilter("mts", new String[] {"mts"}); //$NON-NLS-1$ //$NON-NLS-2$
      	vidType = new XuggleVideoType(filter);
      	vidType.setRecordable(false);
      	VideoIO.addVideoType(vidType);
        ResourceLoader.addExtractExtension("mts"); //$NON-NLS-1$
      	// M2TS
      	filter = new VideoFileFilter("m2ts", new String[] {"m2ts"}); //$NON-NLS-1$ //$NON-NLS-2$
      	vidType = new XuggleVideoType(filter);
      	vidType.setRecordable(false);
      	VideoIO.addVideoType(vidType);
        ResourceLoader.addExtractExtension("m2ts"); //$NON-NLS-1$
      	// MPG
      	filter = new VideoFileFilter("mpg", new String[] {"mpg"}); //$NON-NLS-1$ //$NON-NLS-2$
      	vidType = new XuggleVideoType(filter);
      	vidType.setRecordable(false);
      	VideoIO.addVideoType(vidType);
        ResourceLoader.addExtractExtension("mpg"); //$NON-NLS-1$
      	// MOD
      	filter = new VideoFileFilter("mod", new String[] {"mod"}); //$NON-NLS-1$ //$NON-NLS-2$
      	vidType = new XuggleVideoType(filter);
      	vidType.setRecordable(false);
      	VideoIO.addVideoType(vidType);
        ResourceLoader.addExtractExtension("mod"); //$NON-NLS-1$
      	// OGG
      	filter = new VideoFileFilter("ogg", new String[] {"ogg"}); //$NON-NLS-1$ //$NON-NLS-2$
      	vidType = new XuggleVideoType(filter);
      	vidType.setRecordable(false);
      	VideoIO.addVideoType(vidType);
        ResourceLoader.addExtractExtension("mod"); //$NON-NLS-1$
//      	// WEBM --problematic with Xuggle 5.4 and won't play with Xuggle 3.4...
//      	filter = new VideoFileFilter("webm", new String[] {"webm"}); //$NON-NLS-1$ //$NON-NLS-2$
//      	vidType = new XuggleVideoType(filter);
//      	vidType.setRecordable(false);
//      	VideoIO.addVideoType(vidType);
//        ResourceLoader.addExtractExtension("webm"); //$NON-NLS-1$
      }
      catch (Exception ex) { // Xuggle not working
      	OSPLog.config("Xuggle exception: "+ex.toString()); //$NON-NLS-1$
      }    	
      catch (Error er) { // Xuggle not working
      	OSPLog.config("Xuggle error: "+er.toString()); //$NON-NLS-1$
      }    	
    }
    else {
    	OSPLog.config("Xuggle not installed? (XUGGLE_HOME not found)"); //$NON-NLS-1$
    }
  }
  
}
