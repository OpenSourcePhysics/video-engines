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

import java.io.*;

import java.awt.*;

import javax.imageio.ImageIO;
import java.awt.image.*;

import org.opensourcephysics.controls.*;
import org.opensourcephysics.media.core.*;
import quicktime.*;
import quicktime.io.*;
import quicktime.std.*;
import quicktime.std.image.*;
import quicktime.std.movies.*;
import quicktime.std.movies.media.*;
import quicktime.util.*;

/**
 * This is a quicktime video recorder that uses scratch files.
 *
 * @author Douglas Brown
 */
public class QTVideoRecorder extends ScratchVideoRecorder
    implements StdQTConstants {

  private Movie movie;
  private quicktime.std.movies.Track videoTrack;
  private VideoMedia videoMedia;
  private boolean editing;

  /**
   * Constructs a QTVideoRecorder object.
   * @param type the video type to record
   */
  public QTVideoRecorder(QTVideoType type) {
    super(type);
    QT.start();
  }

//________________________________ protected methods _________________________________

  /**
   * Saves the video to the current scratchFile.
   *
   * @throws IOException
   */
  protected void saveScratch() throws IOException {
    if (movie != null && editing) try {
      // end edits and save the scratch
      videoMedia.endEdits();
      int trackStart	= 0;
      int mediaTime 	= 0;
      int mediaRate	= 1;
      videoTrack.insertMedia(trackStart,
                             mediaTime,
                             videoMedia.getDuration(),
                             mediaRate);
      editing = false;
      OpenMovieFile outStream = OpenMovieFile.asWrite(new QTFile(scratchFile));
      movie.addResource(outStream, movieInDataForkResID, scratchFile.getName());
      outStream.close();
      movie = null;
      OSPLog.finest("saved " + frameCount + " frames in " + scratchFile); //$NON-NLS-1$ //$NON-NLS-2$
    }
    catch (QTException ex) {
      throw new IOException("caught in saveScratch: " + ex.toString()); //$NON-NLS-1$
    }
  }

  /**
   * Starts the video recording process.
   *
   * @return true if video recording successfully started
   */
  protected boolean startRecording() {
    if (dim == null) {
      if (frameImage != null) {
        dim = new Dimension(frameImage.getWidth(null),
                            frameImage.getHeight(null));
      }
      else return false;
    }
    try {
      createMovie();
      return true;
    }
    catch (IOException ex) {
      return false;
    }
  }

  /**
   * Appends a frame to the current video.
   *
   * @param image the image to append
   * @return true if image successfully appended
   */
  protected boolean append(Image image) {
  	BufferedImage bi;
  	if (image instanceof BufferedImage) {
  		bi = (BufferedImage)image;
  	}
  	else {
      bi = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = bi.createGraphics();
      g.drawImage(image, 0, 0, null);
  	}
  	ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
	    if (!editing) {
	    	videoMedia.beginEdits();
        editing = true;
	    }
			ImageIO.write(bi, "png", out); //$NON-NLS-1$
			QTHandle handle = new QTHandle(out.toByteArray());
			DataRef dataRef = new DataRef(handle, kDataRefFileExtensionTag, "png"); //$NON-NLS-1$
			GraphicsImporter importer = new GraphicsImporter(dataRef);
			ImageDescription description = importer.getImageDescription();
      int duration = (int)(frameDuration * 0.6);
      videoMedia.addSample(handle,
              0, 	// data offset
              handle.getSize(),
              duration,
              description,
              1, 	// number of samples
              0); // key frame??
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
    return true;
  }

  /**
   * Creates an empty movie to which frames can be added.
   *
   * @throws IOException
   */
  private void createMovie() throws IOException {
    try {
      // create the scratch movie
      movie = Movie.createMovieFile(new QTFile(scratchFile),
                                    kMoviePlayer,
                                    createMovieFileDeleteCurFile |
                                    createMovieFileDontCreateResFile);

      // create the video track and media
      int timeScale = 600;
      int noVolume = 0;
      videoTrack = movie.newTrack(dim.width, dim.height, noVolume);
      videoMedia = new VideoMedia(videoTrack, timeScale);
      editing = false;
    } catch (QTException ex) {
      throw new IOException("caught in createMovie: " + ex.toString()); //$NON-NLS-1$
    }
  }
  
}
