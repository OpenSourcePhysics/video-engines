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

import javax.swing.*;

import org.opensourcephysics.media.core.MediaRes;

import quicktime.QTException;
import quicktime.app.view.*;
import quicktime.io.*;
import quicktime.std.movies.*;

/** A frame with a QuickTime movie and player */
public class QTPlayer extends JFrame {

  private Thread shutdownHook;

  /** Constructor */
  public QTPlayer() {
    super(MediaRes.getString("QTPlayer.Title")); //$NON-NLS-1$
    setName("QTPlayerTool"); //$NON-NLS-1$
    setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    // create shutdown hook
    shutdownHook = new Thread() {
      public void run() {
        QT.exit();
      }
    };
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  /**
   * Loads a movie file at a specified path.
   * 
   * @param path the path
   * @throws QTException
   */
  public void load(String path) throws QTException {
    QTFile qtFile = new QTFile(path);
    OpenMovieFile openMovieFile = OpenMovieFile.asRead(qtFile);
    Movie mov = Movie.fromFile(openMovieFile);
    MovieController controller = new MovieController(mov);
    QTComponent qtComp = QTFactory.makeQTComponent(controller);
    setTitle(MediaRes.getString("QTPlayer.Title") + ": "+path); //$NON-NLS-1$ //$NON-NLS-2$
    getContentPane().removeAll();
    getContentPane().add(qtComp.asComponent());
    pack();
  }
}
