/*
 * The org.opensourcephysics.media.quicktime package provides QuickTime
 * services including implementations of the Video and VideoRecorder interfaces.
 *
 * Copyright (c) 2017  Douglas Brown and Wolfgang Christian.
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston MA 02111-1307 USA
 * or view the license online at http://www.gnu.org/copyleft/gpl.html
 *
 * For additional information and documentation on Open Source Physics,
 * please see <http://www.opensourcephysics.org/>.
 */
package org.opensourcephysics.media.quicktime;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.TreeSet;

import org.opensourcephysics.controls.OSPLog;
import org.opensourcephysics.media.core.*;

import quicktime.QTSession;

/**
 * This implements the VideoType interface with QuickTime for Java
 *
 * @author Douglas Brown
 * @version 1.0
 */
public class QTVideoType implements VideoType {
	
  protected static TreeSet<VideoFileFilter> qtFileFilters 
			= new TreeSet<VideoFileFilter>();
  protected static PropertyChangeListener errorListener;
  protected static boolean isQTAvailable = true;
  
  static {
  	errorListener = new PropertyChangeListener() {
    	public void propertyChange(PropertyChangeEvent e) {
    		if (e.getPropertyName().equals("qt_error")) { //$NON-NLS-1$
    			isQTAvailable = false;
    		}
    	}
  	};
    OSPLog.getOSPLog().addPropertyChangeListener(errorListener);
  }

  private VideoFileFilter singleTypeFilter; // null for general type

  /**
   * Constructor queries QTSession to see if QTJava is working.
   * This will throw an error if QTJava is not available.
   */
  public QTVideoType() {
  	if (!isQTAvailable)
			throw new Error("QuickTime unavailable"); //$NON-NLS-1$
  	boolean logConsole = OSPLog.isConsoleMessagesLogged();
    try {
    	OSPLog.setConsoleMessagesLogged(false);
    	QTSession.open();
    	OSPLog.setConsoleMessagesLogged(logConsole);
		} catch (Exception ex) {
    	OSPLog.setConsoleMessagesLogged(logConsole);
			throw new Error("QuickTime unavailable"); //$NON-NLS-1$
		}
	}

  /**
   * Constructor with a file filter for a specific container type.
   * 
   * @param filter the file filter 
   * @throws Exception 
   */
  public QTVideoType(VideoFileFilter filter) {
  	this();
  	if (filter!=null) {
			singleTypeFilter = filter;
			qtFileFilters.add(filter);
  	}
  }

  /**
   * Opens a video as a new QTVideo.
   *
   * @param name the name of the video
   * @return the new video
   */
  public Video getVideo(String name) {
    try {
    	Video video = new QTVideo(name);
      video.setProperty("video_type", this); //$NON-NLS-1$
      return video;
    }
    catch (IOException ex) {
      return null;
    }
  }

  /**
   * Gets a video recorder.
   *
   * @return the video recorder
   */
  public VideoRecorder getRecorder() {
    return new QTVideoRecorder(this);
  }

  /**
   * Reports whether this type can record videos
   *
   * @return true if this can record videos
   */
  public boolean canRecord() {
    return true;
  }

  /**
   * Gets the name and/or description of this type.
   *
   * @return a description
   */
  public String getDescription() {
  	if (singleTypeFilter!=null)
  		return singleTypeFilter.getDescription();
    return MediaRes.getString("QTVideoType.Description"); //$NON-NLS-1$
  }

  /**
   * Gets the name and/or description of this type.
   *
   * @return a description
   */
  public String getDefaultExtension() {
  	if (singleTypeFilter!=null) {
  		return singleTypeFilter.getDefaultExtension();
  	}
    return null;
  }

  /**
   * Gets the file filters for this type.
   *
   * @return an array of file filters
   */
  public VideoFileFilter[] getFileFilters() {
  	if (singleTypeFilter!=null)
  		return new VideoFileFilter[] {singleTypeFilter};
    return qtFileFilters.toArray(new VideoFileFilter[0]);
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
  	if (!video.getClass().equals(QTVideo.class)) return false;
  	if (singleTypeFilter==null) return true;
  	String name = (String)video.getProperty("name"); //$NON-NLS-1$
  	return singleTypeFilter.accept(new File(name));
  }
}


