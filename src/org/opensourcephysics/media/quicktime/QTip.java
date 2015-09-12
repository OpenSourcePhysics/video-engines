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

import quicktime.*;
import quicktime.std.movies.*;
import quicktime.app.view.*;
import quicktime.qd.*;

/**
 * This is a utility class used by Quicktime objects needing
 * cleanup services when exiting or being garbage collected.
 *
 * @author Douglas Brown
 * @version 1.0
 */
public class QTip {

  static final int REMOVE_DRAWING_COMPLETE_PROC = 0;

  private static int nextID = 0;

  private int id;
  private int action;
  private Object[] needsCleaning;

  /**
   * Constructs a QTip object.
   *
   * @param _action type of cleanup service required
   * @param toBeCleaned array of objects to be cleaned up
   */
  public QTip(int _action, Object[] toBeCleaned) {
    action = _action;
    needsCleaning = toBeCleaned;
    id = nextID++;
  }

  /**
   * Returns the unique id assigned to this object.
   *
   * @return the unique id
   */
  public int id() {return id;}

  /**
   * Performs cleanup services--typically removing Quicktime callbacks
   * and releasing Quicktime resources.
   *
   * @throws QTException
   */
  protected void cleanup() throws QTException {
    switch(action) {
      case REMOVE_DRAWING_COMPLETE_PROC:
        MoviePlayer player = (MoviePlayer)needsCleaning[0];
        if (player == null) return;
        Movie movie = player.getMovie();
        movie.removeDrawingCompleteProc();
        player.setGWorld(QDGraphics.scratch);
        needsCleaning[0] = null;
    }
  }

}
