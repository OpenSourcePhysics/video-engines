/*
 * The org.opensourcephysics.media.quicktime package provides QuickTime
 * services including implementations of the Video and VideoRecorder interfaces.
 *
 * Copyright (c) 2004  Douglas Brown and Wolfgang Christian.
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

import java.beans.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;

import javax.swing.*;
import javax.swing.event.*;

import org.opensourcephysics.controls.*;
import org.opensourcephysics.display.*;
import org.opensourcephysics.media.core.*;
import org.opensourcephysics.tools.*;

import quicktime.*;
import quicktime.app.view.*;
import quicktime.io.*;
import quicktime.qd.*;
import quicktime.std.*;
import quicktime.std.movies.*;
import quicktime.std.movies.media.*;

/**
 * This loads, draws and plays a video using QuickTime for Java.
 *
 * @author Douglas Brown
 * @version 1.0
 */
public class QTVideo implements Video, MovieDrawingComplete {

  // static fields
  protected static JFileChooser chooser;

  // instance fields
  private Movie             movie;
  private QTFile            qtFile;
  private Image             videoImage;
  private Dimension         size;		// video image dimension
  private BufferedImage     bufferedImage;
  private BufferedImage     filteredImage;
  private Graphics          gbi;
  private QTImageProducer   qtImageProducer;
  private MoviePlayer       moviePlayer;
  private QTip              qTip;
  private int               frameCount;
  private int[]             frameTimes;
  private int               frameNumber;
  private int               startFrameNumber;
  private int               endFrameNumber;
  private javax.swing.Timer timer;
  private javax.swing.Timer oneShot;
  private double            rate                  = 1;
  private boolean           playing               = false;
  private boolean           looping               = false;
  private double            minX, maxX, minY, maxY;
  private boolean           mouseEnabled          = false;
  private boolean           visible               = true;
  private boolean           isMeasured            = false;
  private boolean           isValidMeasure        = false;
  private boolean           widthDominates        = true;
  private boolean           isValidImage          = false;
  private boolean           isValidFilteredImage  = false;
  private boolean           firstTimeDrawn        = true;
  private ImageCoordSystem  coords;
  private DoubleArray       aspects;
  private PropertyChangeSupport support;
  private HashMap<String, Object> properties      = new HashMap<String, Object>();
  private FilterStack       filterStack           = new FilterStack();

  /**
   * Creates a QTVideo and loads a movie from a standard open dialog
   *
   * @throws IOException
   */
  public QTVideo() throws IOException {
    initialize();
    loadFromDialog();
  }

  /**
   * Creates a QTVideo and loads a movie specified by name
   *
   * @param movieName the name of the movie to load
   * @throws IOException
   */
  public QTVideo(String movieName) throws IOException {
    initialize();
    load(movieName);
  }

  /**
   * Draws the video image on the panel.
   *
   * @param panel the drawing panel requesting the drawing
   * @param g the graphics context on which to draw
   */
  public void draw(DrawingPanel panel, Graphics g) {
    if (!visible || videoImage == null) return;
    if (firstTimeDrawn) {
      // this is necessary so panel will respond to QTImageProducer
      // and repaint whenever the video image changes
      Shape gclip = g.getClip();
      g.setClip(new Rectangle());
      g.drawImage(videoImage, 0, 0, panel);
      g.setClip(gclip);
      gbi.drawImage(videoImage, 0, 0, panel);
      firstTimeDrawn = false;
      isValidImage = false;
      isValidFilteredImage = false;
    }
    Graphics2D g2 = (Graphics2D)g;
    if ((panel instanceof VideoPanel &&
        ((VideoPanel)panel).isDrawingInImageSpace()) ||
        isMeasured) {
      AffineTransform gat = g2.getTransform(); // save graphics transform
      g2.transform(panel.getPixelTransform()); // world to screen
      if (panel instanceof VideoPanel) {
        VideoPanel vidPanel = (VideoPanel)panel;
        if (!vidPanel.isDrawingInImageSpace()) {
          // use video panel's coords for vid to world transform
          ImageCoordSystem theCoords = vidPanel.getCoords();
          g2.transform(theCoords.getToWorldTransform(frameNumber));
        }
      } else {
        // use this video's coords for vid to world transform
        g2.transform(coords.getToWorldTransform(frameNumber));
      }
      // draw the video or filtered image
      if (filterStack.isEmpty() || !filterStack.isEnabled())
        g2.drawImage(videoImage, 0, 0, panel);
      else {
        g2.drawImage(getImage(), 0, 0, panel);
      }
      g2.setTransform(gat);                    // restore transform
    } else {  // center image in panel
      double centerX = (panel.getXMax() + panel.getXMin()) / 2;
      double centerY = (panel.getYMax() + panel.getYMin()) / 2;
      int xoffset = panel.xToPix(centerX) - size.width / 2;
      int yoffset = panel.yToPix(centerY) - size.height / 2;
      // draw the video or filtered image
      if (filterStack.isEmpty() || !filterStack.isEnabled()) {
        g2.drawImage(videoImage, xoffset, yoffset, panel);
      } else {
        g2.drawImage(getImage(), xoffset, yoffset, panel);
      }
    }
  }

  /**
   * Shows or hides the video.
   *
   * @param visible <code>true</code> to show the video
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
    firePropertyChange("videoVisible", null, new Boolean(visible)); //$NON-NLS-1$
  }

  /**
   * Gets the visibility of the video.
   *
   * @return <code>true</code> if the video is visible
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * Gets the minimum x needed to draw this object.
   *
   * @return minimum x
   */
  public double getXMin() {
    if (!isValidMeasure) findMinMaxValues();
    return minX;
  }

  /**
   * Gets the maximum x needed to draw this object.
   *
   * @return maximum x
   */
  public double getXMax() {
    if (!isValidMeasure) findMinMaxValues();
    return maxX;
  }

  /**
   * Gets the minimum y needed to draw this object.
   *
   * @return minimum y
   */
  public double getYMin() {
    if (!isValidMeasure) findMinMaxValues();
    return minY;
  }

  /**
   * Gets the maximum y needed to draw this object.
   *
   * @return maximum y
   */
  public double getYMax() {
    if (!isValidMeasure) findMinMaxValues();
    return maxY;
  }

  /**
   * Reports whether information is available to set min/max values.
   *
   * @return <code>true</code> if min/max values are valid
   */
  public boolean isMeasured() {
    return isMeasured;
  }

  /**
   * Gets the current video image.
   *
   * @return the current video image
   */
  public BufferedImage getImage() {
    if (videoImage == null) return null;
    if (!isValidImage) {
      isValidImage = true;
      gbi.drawImage(videoImage, 0, 0, null);
    }
    if (filterStack.isEmpty() || !filterStack.isEnabled())
      return bufferedImage;
    else if (!isValidFilteredImage) {
      isValidFilteredImage = true;
      filteredImage = filterStack.getFilteredImage(bufferedImage);
    }
    return filteredImage;
  }

  /**
   * Returns this video if enabled.
   *
   * @param panel the drawing panel
   * @param xpix the x coordinate in pixels
   * @param ypix the y coordinate in pixels
   * @return this if enabled, otherwise null
   */
  public Interactive findInteractive(
                             DrawingPanel panel, int xpix, int ypix) {
    if (!mouseEnabled) return null;
    return this;
  }

  /**
   * Sets whether this responds to mouse hits.
   *
   * @param enabled <code>true</code> if this responds to mouse hits.
   */
  public void setEnabled(boolean enabled) {
    mouseEnabled = enabled;
  }

  /**
   * Gets whether this responds to mouse hits.
   *
   * @return <code>true</code> if this responds to mouse hits.
   */
  public boolean isEnabled() {
    return mouseEnabled;
  }

  /**
   * Sets x position of upper left corner of the specified video frame
   * in world units.
   *
   * @param n the video frame number
   * @param x the world x position
   */
  public void setFrameX(int n, double x) {
    setFrameXY(n, x, coords.imageToWorldY(n, 0, 0));
  }

  /**
   * Sets x position of upper left corner of all video frames
   * in world units.
   *
   * @param x the world x position
   */
  public void setX(double x) {
    for (int n = 0; n < frameCount; n++)
      setFrameX(n, x);
  }

  /**
   * Sets y position of upper left corner of the specified video frame
   * in world units.
   *
   * @param n the video frame number
   * @param y the world y position
   */
  public void setFrameY(int n, double y) {
    setFrameXY(n, coords.imageToWorldX(n, 0, 0), y);
  }

  /**
   * Sets y position of upper left corner of all video frames
   * in world units.
   *
   * @param y the world y position
   */
  public void setY(double y) {
    for (int n = 0; n < frameCount; n++)
      setFrameY(n, y);
  }

  /**
   * Gets x position of upper left corner of the current video frame
   * in world units.
   *
   * @return the world x position
   */
  public double getX() {
    return coords.imageToWorldX(frameNumber, 0, 0);
  }

  /**
   * Gets y position of upper left corner of the current video frame
   * in world units.
   *
   * @return the world y position
   */
  public double getY() {
    return coords.imageToWorldY(frameNumber, 0, 0);
  }

  /**
   * Sets the x and y position of the UL corner of the specified video
   * frame in world units.
   *
   * @param n the video frame number
   * @param x the world x position
   * @param y the world y position
   */
  public void setFrameXY(int n, double x, double y) {
    double sin = coords.getSine(n);
    double cos = coords.getCosine(n);
    double tx = coords.getScaleX(n) * (y*sin - x*cos);
    double ty = coords.getScaleY(n) * (y*cos + x*sin);
    coords.setOriginXY(n, tx, ty);
  }

  /**
   * Sets the x and y position of the UL corner of all video frames
   * in world units.
   *
   * @param x the world x position
   * @param y the world y position
   */
  public void setXY(double x, double y) {
    for (int n = 0; n < frameCount; n++)
      setFrameXY(n, x, y);
  }

  /**
   * Sets the relative aspect of the specified video frame. Relative
   * aspect is the ratio of the world aspect to the pixel aspect of
   * the image. The pixel aspect is the ratio of image width to height
   * in pixels, and world aspect is the ratio of world width to height
   * in world units. For example, a 320 x 240 pixel movie has a pixel
   * aspect of 1.33. If relative aspect is 2, then the world aspect
   * will be 2.67. So if the video's width is 16 wu, its height will
   * be 6 wu. Or if its height is 10 wu, its width will be 26.67 wu.
   *
   * @param n the video frame number
   * @param relativeAspect the desired relative aspect
   */
  public void setFrameRelativeAspect(int n, double relativeAspect) {
    if (relativeAspect < 0.001 || relativeAspect > 1000) return;
    aspects.set(n, Math.abs(relativeAspect));
    if (isMeasured) {
      if (widthDominates) setFrameWidth(n, size.width/coords.getScaleX(n));
      else setFrameHeight(n, size.height/coords.getScaleY(n));
    }
  }

  /**
   * Sets the relative aspect of all video frames. Relative
   * aspect is the ratio of the world aspect to the pixel aspect of
   * the image. The pixel aspect is the ratio of image width to height
   * in pixels, and world aspect is the ratio of world width to height
   * in world units. For example, a 320 x 240 pixel movie has a pixel
   * aspect of 1.33. If relative aspect is 2, then the world aspect
   * will be 2.67. So if the video's width is 16 wu, its height will
   * be 6 wu. Or if its height is 10 wu, its width will be 26.67 wu.
   *
   * @param relativeAspect the desired relative aspect
   */
  public void setRelativeAspect(double relativeAspect) {
    for (int n = 0; n < frameCount; n++)
      setFrameRelativeAspect(n, relativeAspect);
  }

  /**
   * Gets the relative aspect of the current video frame.
   *
   * @return the relative aspect of the current image.
   */
  public double getRelativeAspect() {
    return aspects.get(frameNumber);
  }

  /**
   * Sets the width of the specified video frame in world units. Also sets
   * the height using the relative aspect.
   *
   * @param n the video frame number
   * @param width the width in world units
   * @see #setRelativeAspect
   */
  public void setFrameWidth(int n, double width) {
    if (width == 0) return;
    width = Math.abs(width);
    // save x and y since setting width invalidates them
    double x = coords.imageToWorldX(n, 0, 0);
    double y = coords.imageToWorldY(n, 0, 0);
    double scaleX = size.width / width;
    coords.setScaleX(n, scaleX);
    coords.setScaleY(n, scaleX * aspects.get(n));
    widthDominates = true;
    // restore x and y to their correct values
    setFrameXY(n, x, y);
  }

  /**
   * Sets the width of all video frames in world units. Also sets
   * the heights using the relative aspect.
   *
   * @param width the width in world units
   * @see #setRelativeAspect
   */
  public void setWidth(double width) {
    for (int n = 0; n < frameCount; n++)
      setFrameWidth(n, width);
  }

  /**
   * Gets the current width of the video frame.
   *
   * @return the width of the video image
   */
  public double getWidth() {
    return size.width / coords.getScaleX(frameNumber);
  }

  /**
   * Sets the height of the specified video frame in world units. Also sets
   * the width using the relative aspect.
   *
   * @param n the video frame number
   * @param height the height in world units
   * @see #setRelativeAspect
   */
  public void setFrameHeight(int n, double height) {
    if (height == 0) return;
    height = Math.abs(height);
    // save x and y since setting width invalidates them
    double x = coords.imageToWorldX(n, 0, 0);
    double y = coords.imageToWorldY(n, 0, 0);
    double scaleY = size.height / height;
    coords.setScaleY(n, scaleY);
    coords.setScaleX(n, scaleY / aspects.get(n));
    widthDominates = false;
    // restore x and y to their correct values
    setFrameXY(n, x, y);
  }

  /**
   * Sets the height of all video frames in world units. Also sets
   * the widths using the relative aspect.
   *
   * @param height the height in world units
   * @see #setRelativeAspect
   */
  public void setHeight(double height) {
    for (int n = 0; n < frameCount; n++)
      setFrameHeight(n, height);
  }

  /**
   * Gets the current height of the video frame.
   *
   * @return the height of the video image
   */
  public double getHeight() {
    return size.height / coords.getScaleY(frameNumber);
  }

  /**
   * Sets the angle in radians of the specified video frame measured ccw
   * from the world x-axis. This results in a rotation only.
   *
   * @param n the video frame number
   * @param theta the angle in radians
   */
  public void setFrameAngle(int n, double theta) {
    // save x and y since setting angle invalidates them
    double x = coords.imageToWorldX(n, 0, 0);
    double y = coords.imageToWorldY(n, 0, 0);
    double cos = Math.cos(theta);
    double sin = Math.sin(theta);
    coords.setCosineSine(n, cos, -sin);
    setFrameXY(n, x, y);	// restore x and y to their correct values
  }

  /**
   * Sets the angle in radians of all video frames measured ccw
   * from the world x-axis. This results in a rotation only.
   *
   * @param theta the angle in radians
   */
  public void setAngle(double theta) {
    for (int n = 0; n < frameCount; n++)
      setFrameAngle(n, theta);
  }

  /**
   * Gets the angle in radians of the curent video frame measured ccw
   * from the world x-axis.
   *
   * @return the angle in radians
   */
  public double getAngle() {
    return -coords.getAngle(frameNumber);
  }

  /**
   * Steps the video forward one frame.
   */
  public void step() {
    stop();
    setFrameNumber(frameNumber + 1);
  }

  /**
   * Steps the video back one frame.
   */
  public void back() {
    stop();
    setFrameNumber(frameNumber - 1);
  }

  /**
   * Gets the total number of video frames.
   *
   * @return the number of video frames
   */
  public int getFrameCount() {
    return frameCount;
  }

  /**
   * Gets the current video frame number.
   *
   * @return the current frame number
   */
  public int getFrameNumber() {
    return frameNumber;
  }

  /**
   * Sets the video frame number.
   *
   * @param n the desired frame number
   */
  public void setFrameNumber(int n) {
    if (n == frameNumber) return;
    n = Math.min(n, endFrameNumber);
    n = Math.max(n, startFrameNumber);
    firePropertyChange("nextframe", null, n); //$NON-NLS-1$
    try {
    	int extraPush = movie.getTimeScale()/500;
      moviePlayer.setTime(frameTimes[n]+extraPush);
      frameNumber = n;
    } catch(QTException ex) {
    	ex.printStackTrace();
    }
  }
  
  /**
   * Gets the start frame number.
   *
   * @return the start frame number
   * @see #getEndFrameNumber
   */
  public int getStartFrameNumber() {
    return startFrameNumber;
  }

  /**
   * Sets the start frame number.
   *
   * @param n the desired start frame number
   * @see #setEndFrameNumber
   */
  public void setStartFrameNumber(int n) {
    if (n == startFrameNumber) return;
    n = Math.max(0, n);
    startFrameNumber = Math.min(endFrameNumber, n);
    try {
      int startTime = frameTimes[startFrameNumber];
      int duration = movie.getDuration() - startTime;
      if  (endFrameNumber < frameCount - 1)
        duration = frameTimes[endFrameNumber + 1] - startTime;
      movie.setActiveSegment(new TimeInfo(startTime, duration));
    } catch(StdQTException ex) {ex.printStackTrace();}
    if (frameNumber < startFrameNumber) setFrameNumber(startFrameNumber);
    if (looping && playing) {
       timer.stop();
       timer.setDelay((int)((getEndTime() - getStartTime()) / rate));
       timer.setInitialDelay((int)((getEndTime() - getTime()) / rate));
       timer.start();
    }
    firePropertyChange("startframe", null, new Integer(startFrameNumber)); //$NON-NLS-1$
  }

  /**
   * Gets the end frame number.
   *
   * @return the end frame number
   * @see #getStartFrameNumber
   */
  public int getEndFrameNumber() {
    return endFrameNumber;
  }

  /**
   * Sets the end frame number.
   *
   * @param n the desired end frame number,
   * @see #setStartFrameNumber
   */
  public void setEndFrameNumber(int n) {
    if (n == endFrameNumber) return;
    n = Math.min(frameCount - 1, n);
    endFrameNumber = Math.max(startFrameNumber, n);
    try {
      int startTime = frameTimes[startFrameNumber];
      int duration = movie.getDuration() - startTime;
      if  (endFrameNumber < frameCount - 1)
        duration = frameTimes[endFrameNumber + 1] - startTime;
      movie.setActiveSegment(new TimeInfo(startTime, duration));
    } catch(StdQTException ex) {ex.printStackTrace();}
    if (frameNumber > endFrameNumber) setFrameNumber(endFrameNumber);
    if (looping && playing) {
       timer.stop();
       timer.setDelay((int)((getEndTime() - getStartTime()) / rate));
       timer.setInitialDelay((int)((getEndTime() - getTime()) / rate));
       timer.start();
    }
    firePropertyChange("endframe", null, new Integer(endFrameNumber)); //$NON-NLS-1$
  }

  /**
   * Gets the start time of the specified frame in milliseconds.
   *
   * @param n the frame number
   * @return the start time of the frame in milliseconds, or -1 if not known
   */
  public double getFrameTime(int n) {
    try {
      return 1000.0 * frameTimes[n] / movie.getTimeScale();
    } catch(Exception ex) {ex.printStackTrace();}
    return -1;
  }

  /**
   * Gets the duration of the specified frame in milliseconds.
   *
   * @param n the frame number
   * @return the duration of the frame in milliseconds
   */
  public double getFrameDuration(int n) {
    if(frameCount == 1) return getDuration();
    if(n == frameCount - 1)
      return getDuration() - getFrameTime(n);
    return getFrameTime(n + 1) - getFrameTime(n);
  }

  /**
   * Plays the video at the current rate.
   */
  public void play() {
    if (frameCount == 1) return;  // don't play a still image
    playing = true;
    try {
      if (moviePlayer.getRate() != rate) {
        if(frameNumber >= endFrameNumber ||
          frameNumber < startFrameNumber)
          setFrameNumber(startFrameNumber);
        // use timer for looping
        if (looping) {
          timer.stop();
          timer.setDelay( (int) ( (getEndTime() - getStartTime()) / rate));
          timer.setInitialDelay( (int) ( (getEndTime() - getTime()) / rate));
          timer.start();
        }
        moviePlayer.setRate( (float) rate);
        firePropertyChange("playing", null, new Boolean(true)); //$NON-NLS-1$
      }
     } catch(QTException ex) {ex.printStackTrace();}
  }

  /**
   * Stops the video.
   */
  public void stop() {
    playing = false;
    try {
      if (moviePlayer.getRate() != 0) {
         moviePlayer.setRate(0);
         timer.stop();
         oneShot.stop();
         firePropertyChange("playing", null, new Boolean(false)); //$NON-NLS-1$
       }
     } catch(QTException ex) {ex.printStackTrace();}
  }

  /**
   * Stops the video and resets it to the start time.
   */
  public void reset() {
    stop();
    setFrameNumber(startFrameNumber);
  }

  /**
   * Gets the current video time in milliseconds.
   *
   * @return the current time in milliseconds, or -1 if not known
   */
  public double getTime() {
    try {
      return 1000.0 * movie.getTime() / movie.getTimeScale();
    } catch(QTException ex) {ex.printStackTrace();}
    return -1;
  }

  /**
   * Sets the video time in milliseconds.
   *
   * @param millis the desired time in milliseconds
   */
  public void setTime(double millis) {
    try {
      int t = (int)(millis * movie.getTimeScale() / 1000);
      movie.setTimeValue(t);
    } catch(QTException ex) {ex.printStackTrace();}
  }

  /**
   * Gets the start time in milliseconds.
   *
   * @return the start time in milliseconds, or -1 if not known
   */
  public double getStartTime() {
    try {
      return 1000.0 * frameTimes[startFrameNumber] / movie.getTimeScale();
    } catch(QTException ex) {ex.printStackTrace();}
    return -1;
  }

  /**
   * Sets the start time in milliseconds. NOTE: the actual start time
   * is always set to the beginning of a frame.
   *
   * @param millis the desired start time in milliseconds
   */
  public void setStartTime(double millis) {
    try {
      int n;
      for (n = frameCount - 1; n >= 0; n--) {
        double frameMillis = 1000.0 * frameTimes[n] / movie.getTimeScale();
        if (frameMillis <= millis) break;
      }
      setStartFrameNumber(n);
    } catch(QTException ex) {ex.printStackTrace();}
  }

  /**
   * Gets the end time in milliseconds.
   *
   * @return the end time in milliseconds, or -1 if not known
   */
  public double getEndTime() {
    try {
      if (endFrameNumber == frameCount - 1) return getDuration();
      return 1000.0 * frameTimes[endFrameNumber + 1] / movie.getTimeScale();
    } catch(QTException ex) {ex.printStackTrace();}
    return -1;
  }

  /**
   * Sets the end time in milliseconds. NOTE: the actual end time
   * is always set to the end of a frame.
   *
   * @param millis the desired end time in milliseconds
   */
  public void setEndTime(double millis) {
    try {
      int n;
      for (n = frameCount - 1; n >= 0; n--) {
        double frameMillis = 1000.0 * frameTimes[n] / movie.getTimeScale();
        if (frameMillis <= millis) break;
      }
      setEndFrameNumber(n);
    } catch(QTException ex) {ex.printStackTrace();}
  }

  /**
   * Gets the duration of the video.
   *
   * @return the duration of the video in milliseconds, or -1 if not known
   */
  public double getDuration() {
    try {
      return 1000.0 * movie.getDuration() / movie.getTimeScale();
    } catch(QTException ex) {ex.printStackTrace();}
    return -1;
  }

  /**
   * Sets the frame number to the start frame.
   */
  public void goToStart() {
    setFrameNumber(startFrameNumber);
  }

  /**
   * Sets the frame number to the end frame.
   */
  public void goToEnd() {
    setFrameNumber(endFrameNumber);
  }

  /**
   * Starts and stops the video.
   *
   * @param playing <code>true</code> starts the video, and
   * <code>false</code> stops it
   */
  public void setPlaying(boolean playing) {
    if (playing) play();
    else stop();
  }

  /**
   * Gets the playing state of this video.
   *
   * @return <code>true</code> if the video is playing
   */
  public boolean isPlaying() {
    return playing;
  }

  /**
   * Sets the looping state of this video.
   * If true, the video restarts when reaching the end.
   *
   * @param loops <code>true</code> if the video loops
   */
  public void setLooping(boolean loops) {
    if (looping == loops) return;
    looping = loops;
    if (playing) {
      if (looping) {
         timer.setDelay((int)((getEndTime() - getStartTime()) / rate));
         timer.setInitialDelay((int)((getEndTime() - getTime()) / rate));
         timer.start();
      }
       else {
         timer.stop();
       }
     }
    firePropertyChange("looping", null, new Boolean(looping)); //$NON-NLS-1$
  }

  /**
   * Gets the looping state of the video.
   * If true, the video restarts when reaching the end.
   *
   * @return <code>true</code> if the video loops
   */
  public boolean isLooping() {
    return looping;
  }

  /**
   * Sets the relative play rate. Relative play rate is the ratio
   * of a video's play rate to its preferred ("normal") play rate.
   *
   * @param rate the relative play rate.
   */
  public void setRate(double rate) {
    rate = Math.abs(rate);
    if (rate == this.rate || rate == 0) return;
    this.rate = rate;
    try {
      if (movie.getRate() != 0) play();
    } catch(QTException ex) {ex.printStackTrace();}
    firePropertyChange("rate", null, new Double(rate)); //$NON-NLS-1$
  }

  /**
   * Gets the relative play rate. Relative play rate is the ratio
   * of a video's play rate to its preferred ("normal") play rate.
   *
   * @return the relative play rate.
   */
  public double getRate() {
    return rate;
  }

  /**
   * Sets the image coordinate system used to convert from
   * imagespace to worldspace.
   *
   * @param coords the image coordinate system
   */
  public void setCoords(ImageCoordSystem coords) {
    if (coords == this.coords) return;
    if (this.coords != null)
      this.coords.removePropertyChangeListener(this);
    coords.addPropertyChangeListener(this);
    this.coords = coords;
    isMeasured = true;
    isValidMeasure = false;
    firePropertyChange("coords", null, coords); //$NON-NLS-1$
  }

  /**
   * Gets the image coordinate system.
   *
   * @return the image coordinate system
   */
  public ImageCoordSystem getCoords() {
    return coords;
  }

  /**
   * Sets the filter stack.
   *
   * @param stack the new filter stack
   */
  public void setFilterStack(FilterStack stack) {
    filterStack.removePropertyChangeListener(this);
    filterStack = stack;
    filterStack.addPropertyChangeListener(this);
  }

  /**
   * Gets the filter stack.
   *
   * @return the filter stack
   */
  public FilterStack getFilterStack() {
    return filterStack;
  }

  /**
   * Sets a user property of the video.
   *
   * @param name the name of the property
   * @param value the value of the property
   */
  public void setProperty(String name, Object value) {
  	if (name.equals("measure")) { //$NON-NLS-1$
  		isValidMeasure = false;
  	}
  	else properties.put(name, value);
  }

  /**
   * Gets a user property of the video. May return null.
   *
   * @param name the name of the property
   * @return the value of the property
   */
  public Object getProperty(String name) {
    return properties.get(name);
  }

  /**
   * Gets a collection of user property names for the video.
   *
   * @return a collection of property names
   */
  public Collection<String> getPropertyNames() {
    return properties.keySet();
  }

  /**
   * Adds a PropertyChangeListener to this video.
   *
   * @param listener the object requesting property change notification
   */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    support.addPropertyChangeListener(listener);
  }

  /**
   * Adds a PropertyChangeListener to this video.
   *
   * @param property the name of the property of interest to the listener
   * @param listener the object requesting property change notification
   */
  public void addPropertyChangeListener(String property,
                                        PropertyChangeListener listener) {
    support.addPropertyChangeListener(property, listener);
  }

  /**
   * Removes a PropertyChangeListener from this video.
   *
   * @param listener the listener requesting removal
   */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    support.removePropertyChangeListener(listener);
  }

  /**
   * Removes a PropertyChangeListener for a specified property.
   *
   * @param property the name of the property
   * @param listener the listener to remove
   */
  public void removePropertyChangeListener(
    String property, PropertyChangeListener listener) {
    support.removePropertyChangeListener(property, listener);
  }

  /**
   * Disposes of this object. Users should call this method to explicitly
   * dispose of this object when it is no longer needed. 
   */
  public void dispose() {
    getFilterStack().setInspectorsVisible(false);
    if (coords != null) coords.removePropertyChangeListener(this);
    stop();
    if (qTip != null) {
	    QT.remove(qTip);
	    qTip = null;
    }
  }

  /**
   * Responds to property change events. QTVideo receives the following
   * events: "transform" from ImageCoordSystem and "image" from
   * FilterStack.
   *
   * @param e the property change event
   */
  public void propertyChange(PropertyChangeEvent e) {
    if (e.getSource() == coords) {
      isMeasured = true;
      isValidMeasure = false;
    }
    else if (e.getSource() == filterStack) {
      isValidFilteredImage = false;
      support.firePropertyChange(e); // to video panel
    }
  }

  /**
   * This is called by the Quicktime movie toolbox when it finishes
   * drawing a movie frame.
   *
   * @param movie the movie being drawn
   * @return a QuickTime error code or zero if no error
   */
  public int execute(Movie movie) {
    try {
      qtImageProducer.updateConsumers(null); // triggers repainting
      isValidImage = false;
      isValidFilteredImage = false;
      // determine frame number and fire property change
      int t = movie.getTime();
      int n;
      for (n = 0; n < frameTimes.length; n++)
        if (t < frameTimes[n]) break;
      frameNumber = Math.min(n - 1, endFrameNumber);
      firePropertyChange("framenumber", null, new Integer(frameNumber)); //$NON-NLS-1$
      oneShot.stop();
      if (playing && !looping) {
        oneShot.setInitialDelay( (int) ( (getEndTime() - getTime()) / rate));
        oneShot.start();
      }
    } catch(QTException e) {
    	e.printStackTrace();
    	return e.errorCode();
    }
    return 0;
  }

  /**
   * Overrides Object toString method.
   *
   * @return the name of this video
   */
  public String toString() {
    return (String)getProperty("name"); //$NON-NLS-1$
  }

  //____________________________ protected methods ____________________________

  /**
   * Sends a PropertyChangeEvent to registered listeners. No event is sent
   * if oldVal and newVal are equal, unless they are both null.
   *
   * @param property the name of the property that has changed
   * @param oldVal the value of the property before the change (may be null)
   * @param newVal the value of the property after the change (may be null)
   */
  protected void firePropertyChange(String property, Object oldVal, Object newVal) {
    support.firePropertyChange(property, oldVal, newVal);
  }

  @Override
  protected void finalize() {
  	OSPLog.finer(getClass().getSimpleName()+" reclaimed by garbage collector"); //$NON-NLS-1$
  }

  //_______________________________ private methods ___________________________

  /**
   * Initialize this video.
   */
  private void initialize() {
    QT.start();
    support = new SwingPropertyChangeSupport(this);
    filterStack.addPropertyChangeListener(this);
    // create timers
    timer = new javax.swing.Timer(1000, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setFrameNumber(startFrameNumber);
      }
    });
    oneShot = new javax.swing.Timer(1000, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        stop();
        setFrameNumber(endFrameNumber);
      }
    });
    oneShot.setRepeats(false);
  }

  /**
   * Loads a movie specified by name.
   *
   * @param movieName name of the movie to be loaded
   * @throws IOException
   */
  public void load(String movieName) throws IOException {
    if (!QTSession.isInitialized()) throw new IOException("QT not available"); //$NON-NLS-1$
    if (movieName == null || movieName.equals("")) return; //$NON-NLS-1$
    OSPLog.fine("loading " + movieName); //$NON-NLS-1$
    Resource res = ResourceLoader.getResource(movieName);
    if (res == null) throw new IOException(movieName + " resource not found"); //$NON-NLS-1$
    setProperty("name", movieName); //$NON-NLS-1$
    setProperty("absolutePath", res.getAbsolutePath()); //$NON-NLS-1$
    // set relative path
    String path = XML.forwardSlash(movieName);
    // if name is relative, path is name
    if (path.indexOf(":") == -1 && !path.startsWith("/")) //$NON-NLS-1$ //$NON-NLS-2$
      setProperty("path", path); //$NON-NLS-1$
    // else path is relative to user directory
    else
      setProperty("path", XML.getRelativePath(path)); //$NON-NLS-1$
    if (res.getFile() != null) {
      load(res.getFile());
      return;
    }
    else if (res.getURL() != null) {
      load(res.getURL());
    }
  }

  /**
   * Loads a movie specified by file.
   *
   * @param file the movie file to be loaded
   * @throws IOException
   */
  public void load(File file) throws IOException {
    try {
      qtFile = new QTFile(file);
      OpenMovieFile openMovieFile = OpenMovieFile.asRead(qtFile);
      setMovie(Movie.fromFile(openMovieFile));
    } catch(StdQTException ex) {
      throw new IOException("File type not supported by QuickTime"); //$NON-NLS-1$
    } catch(QTException ex) {
      throw new IOException("File not found"); //$NON-NLS-1$
    }
  }

  /**
   * Loads a movie specified by URL.
   *
   * @param url the url
   * @throws IOException
   */
  public void load(URL url) throws IOException {
    try {
      DataRef movieURL = new DataRef(url.toString());
      Movie newMovie = Movie.fromDataRef(movieURL, StdQTConstants.newMovieActive);
      setMovie(newMovie);
    } catch(StdQTException ex) {
      throw new IOException("URL file type not supported by QuickTime"); //$NON-NLS-1$
    } catch(QTException ex) {
      throw new IOException("URL not found"); //$NON-NLS-1$
    }
  }

//  /**
//   * Extracts a URL from a compressed (ZIP or JAR) file. Not yet functional.
//   * @param url the URL
//   */
//  private void loadFromJar(URL url) { // this doesn't work
//    try {
//      InputStream in = url.openStream();
//      int bytesRead;
//      byte[] buffer = new byte[1048576];
//      ByteArrayOutputStream output = new ByteArrayOutputStream();
//      BufferedOutputStream out = new java.io.BufferedOutputStream(output);
//      if ( (bytesRead = in.read(buffer)) != -1) 
//      	out.write(buffer, 0, bytesRead);
//			QTHandle handle = new QTHandle(output.toByteArray());
//			DataRef dataRef = new DataRef(handle, StdQTConstants.kDataRefFileExtensionTag, "mov"); //$NON-NLS-1$
//      Movie newMovie = Movie.fromDataRef(dataRef, StdQTConstants.newMovieActive);
//      setMovie(newMovie);
//      out.close();
//      in.close();
//    } catch (Exception ex) {}
//  }
//
  /**
   * Shows a standard open dialog and loads the selected file.
   *
   * @throws IOException
   */
  private void loadFromDialog() throws IOException {
    if (chooser == null)
      chooser = new JFileChooser(new File( org.opensourcephysics.display.OSPRuntime.chooserDir));
    int result = chooser.showOpenDialog(null);
    if(result == JFileChooser.APPROVE_OPTION) {
      load(chooser.getSelectedFile().getAbsolutePath());
    }
  }

  /**
   * Sets the QuickTime movie for this video.
   *
   * @param theMovie the QuickTime movie
   * @throws QTException
   * @throws IOException
   */
  private void setMovie(Movie theMovie) throws QTException, IOException {
    movie = theMovie;
    Movie.taskAll(0); // required for execute() to work correctly in applets?!
    // create a movie player
    moviePlayer = new MoviePlayer(movie);
    moviePlayer.setRate(0);
    QDRect rect = moviePlayer.getDisplayBounds();
    size = new Dimension(rect.getWidth(), rect.getHeight());
    // check that size is not zero
    if (size.width == 0) {
      throw new IOException("Movie contains no video content"); //$NON-NLS-1$
    }
    // create the video image
    qtImageProducer = new QTImageProducer(moviePlayer, size);
    videoImage = Toolkit.getDefaultToolkit().createImage(qtImageProducer);
    // set QuickTime callback on the movie
    movie.setDrawingCompleteProc(StdQTConstants.movieDrawingCallWhenChanged,
                                 this);
    // create and register QTip object
    qTip = new QTip(QTip.REMOVE_DRAWING_COMPLETE_PROC, new Object[] {moviePlayer});
    QT.add(qTip);
    // create the buffered image
    bufferedImage = new BufferedImage(size.width, size.height,
                                      BufferedImage.TYPE_INT_RGB);
    gbi = bufferedImage.createGraphics();
    // get frame times
    int t = 0;
    Collection<Integer> times = new ArrayList<Integer>();
    movie.goToBeginning();
    int nextT = movie.getTime();
    while(nextT > -1) {
      t = nextT;
      times.add(new Integer(t));
      nextT = movie.getNextInterestingTime(StdQTConstants.nextTimeStep,
                    new int[]{StdQTConstants.visualMediaCharacteristic},
                    t, 1.0F).time;
    }
    // get frame count
    frameCount = times.size();
    // set start and end frame numbers
    startFrameNumber = 0;
    endFrameNumber = frameCount - 1;
    // put times into frameTimes array
    Object[] timesArray = times.toArray();
    frameTimes = new int[frameCount];
    for(int j = 0; j < frameCount; j++)
      frameTimes[j] = ((Integer)timesArray[j]).intValue();
    // adjust coordinate system and relativeAspects
    coords = new ImageCoordSystem(frameCount);
    coords.addPropertyChangeListener(this);
    aspects = new DoubleArray(frameCount, 1);
  }

  /**
   * Finds the min and max values of x and y.
   */
  private void findMinMaxValues() {
  	VideoClip clip = (VideoClip)getProperty("videoclip"); //$NON-NLS-1$
    // check all four corner positions of every frame
    Point2D corner = new Point2D.Double(0, 0);	// top left
    int start = 0;
    if (clip != null) start = clip.getStartFrameNumber();
    AffineTransform at = coords.getToWorldTransform(start);
    at.transform(corner, corner);
    maxX = minX = corner.getX();
    maxY = minY = corner.getY();
    int stepCount = frameCount;
    if (clip != null) stepCount = clip.getStepCount();
    for (int n = 0; n < stepCount; n++) {
      if (clip == null) at = coords.getToWorldTransform(n);
      else at = coords.getToWorldTransform(clip.stepToFrame(n));
      for (int i = 0; i < 4; i++) {
        switch(i) {
          case 0: corner.setLocation(0, 0); break;
          case 1: corner.setLocation(size.width, 0); break;
          case 2: corner.setLocation(0, size.height); break;
          case 3: corner.setLocation(size.width, size.height);
        }
        at.transform(corner, corner);
        minX = Math.min(corner.getX(), minX);
        maxX = Math.max(corner.getX(), maxX);
        minY = Math.min(corner.getY(), minY);
        maxY = Math.max(corner.getY(), maxY);
      }
    }
    isValidMeasure = true;
  }

  /**
   * Returns an XML.ObjectLoader to save and load QTVideo data.
   *
   * @return the object loader
   */
  public static XML.ObjectLoader getLoader() {
    return new Loader();
  }

  /**
   * A class to save and load QTVideo data.
   */
  static class Loader implements XML.ObjectLoader {

    /**
     * Saves QTVideo data to an XMLControl.
     *
     * @param control the control to save to
     * @param obj the QTVideo to save
     */
    public void saveObject(XMLControl control, Object obj) {
      QTVideo video = (QTVideo)obj;
      String base = (String)video.getProperty("base"); //$NON-NLS-1$
      String absPath = (String)video.getProperty("absolutePath"); //$NON-NLS-1$
      control.setValue("path", XML.getPathRelativeTo(absPath, base)); //$NON-NLS-1$
      if (!video.getFilterStack().isEmpty()) {
        control.setValue("filters", video.getFilterStack().getFilters()); //$NON-NLS-1$
      }
    }

    /**
     * Creates a new QTVideo.
     *
     * @param control the control
     * @return the new QTVideo
     */
    public Object createObject(XMLControl control) {
      try {
      	String path = control.getString("path"); //$NON-NLS-1$
      	Video video = new QTVideo(path);
        VideoType qtType = VideoIO.getVideoType(VideoIO.ENGINE_QUICKTIME, null);
        if (qtType!=null)
        	video.setProperty("video_type", qtType); //$NON-NLS-1$
        return video;
      }
      catch (IOException ex) {
        return null;
      }
    }

    /**
     * Loads a QTVideo with data from an XMLControl.
     *
     * @param control the control
     * @param obj the QTVideo
     * @return the loaded object
     */
    public Object loadObject(XMLControl control, Object obj) {
      QTVideo video = (QTVideo)obj;
      Collection<?> filters = Collection.class.cast(control.getObject("filters")); //$NON-NLS-1$
      if (filters != null) {
        video.getFilterStack().clear();
        Iterator<?> it = filters.iterator();
        while (it.hasNext()) {
          Filter filter = (Filter)it.next();
          video.getFilterStack().addFilter(filter);
        }
      }
      return obj;
    }
  }
}
