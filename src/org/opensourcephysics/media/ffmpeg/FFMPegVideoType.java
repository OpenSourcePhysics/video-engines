package org.opensourcephysics.media.ffmpeg;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.TreeSet;

import org.opensourcephysics.controls.OSPLog;
import org.opensourcephysics.media.core.MediaRes;
import org.opensourcephysics.media.core.Video;
import org.opensourcephysics.media.core.VideoFileFilter;
import org.opensourcephysics.media.core.VideoRecorder;
import org.opensourcephysics.media.core.VideoType;

/**
 * This implements the VideoType interface with a ffmpeg type.
 * 
 * @author Frank Schütte
 * @version 1.0
 */
public class FFMPegVideoType implements VideoType {

	protected static TreeSet<VideoFileFilter> ffmpegFileFilters = new TreeSet<VideoFileFilter>();
	protected static String ffmpegClass = "org.ffmpeg.FFMPeg"; //$NON-NLS-1$
	protected static PropertyChangeListener errorListener;
	protected static boolean isffmpegAvailable = true;
	protected boolean recordable = true;

	static {
		errorListener = new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent e) {
				if (e.getPropertyName().equals("ffmpeg_error")) { //$NON-NLS-1$
					isffmpegAvailable = false;
				}
			}
		};
		OSPLog.getOSPLog().addPropertyChangeListener(errorListener);
		FFMPegThumbnailTool.start();
	}

	private VideoFileFilter singleTypeFilter; // null for general type

	/**
	 * Constructor attempts to load a ffmpeg class the first time used. This
	 * will throw an error if ffmpeg is not available.
	 */
	public FFMPegVideoType() {
		if (!isffmpegAvailable)
			throw new Error("ffmpeg unavailable"); //$NON-NLS-1$
		boolean logConsole = OSPLog.isConsoleMessagesLogged();
		try {
			OSPLog.setConsoleMessagesLogged(false);
			Class.forName(ffmpegClass);
			OSPLog.setConsoleMessagesLogged(logConsole);
		} catch (Exception ex) {
			OSPLog.setConsoleMessagesLogged(logConsole);
			throw new Error("ffmpeg unavailable"); //$NON-NLS-1$
		}
	}

	/**
	 * Constructor with a file filter for a specific container type.
	 * 
	 * @param filter
	 *            the file filter
	 */
	public FFMPegVideoType(VideoFileFilter filter) {
		this();
		if (filter != null) {
			singleTypeFilter = filter;
			ffmpegFileFilters.add(filter);
		}
	}

	  /**
	   * Opens a named video as a FFMPegVideo.
	   *
	   * @param name the name of the video
	   * @return a new FFMPeg video
	   */
	  public Video getVideo(String name) {
	    try {
	    	Video video = new FFMPegVideo(name);
	      video.setProperty("video_type", this); //$NON-NLS-1$
	      return video;
	    } catch(IOException ex) {
	    	OSPLog.fine(this.getDescription()+": "+ex.getMessage()); //$NON-NLS-1$
	      return null;
	    }
	  }

	  /**
	   * Reports whether this ffmpeg type can record videos
	   *
	   * @return true by default (set recordable to change)
	   */
	  public boolean canRecord() {
	    return recordable;
	  }

	  /**
	   * Sets the recordable property
	   *
	   * @param record true if recordable
	   */
	  public void setRecordable(boolean record) {
	    recordable = record;
	  }

	  /**
	   * Gets a ffmpeg video recorder.
	   *
	   * @return the video recorder
	   */
	  public VideoRecorder getRecorder() {
		  return new FFMPegVideoRecorder(this);  	
	  }

	  /**
	   * Gets the file filters for this type.
	   *
	   * @return an array of file filters
	   */
	  public VideoFileFilter[] getFileFilters() {
	  	if (singleTypeFilter!=null)
	  		return new VideoFileFilter[] {singleTypeFilter};
	    return ffmpegFileFilters.toArray(new VideoFileFilter[0]);
	  }

	  /**
	   * Gets the default file filter for this type. May return null.
	   *
	   * @return the default file filter
	   */
	  public VideoFileFilter getDefaultFileFilter() {
	  	if (singleTypeFilter!=null)
	  		return singleTypeFilter;
	  	return null;
	  }
	  
	  /**
	   * Return true if the specified video is this type.
	   *
	   * @param video the video
	   * @return true if the video is this type
	   */
	  public boolean isType(Video video) {
	  	if (!video.getClass().equals(FFMPegVideo.class)) return false;
	  	if (singleTypeFilter==null) return true;
	  	String name = (String)video.getProperty("name"); //$NON-NLS-1$
	  	return singleTypeFilter.accept(new File(name));
	  }

	  /**
	   * Gets the name and/or description of this type.
	   *
	   * @return a description
	   */
	  public String getDescription() {
	  	if (singleTypeFilter!=null)
	  		return singleTypeFilter.getDescription();
	    return MediaRes.getString("FFMPegVideoType.Description"); //$NON-NLS-1$
	  }

	  /**
	   * Gets the default extension for this type.
	   *
	   * @return an extension
	   */
	  public String getDefaultExtension() {
	  	if (singleTypeFilter!=null) {
	  		return singleTypeFilter.getDefaultExtension();
	  	}
	    return null;
	  }

}
