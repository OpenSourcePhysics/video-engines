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

import java.util.*;

import javax.swing.*;

import org.opensourcephysics.media.core.*;

import quicktime.*;

/**
 * This contains static methods for opening and closing Quicktime sessions.
 *
 * @author Douglas Brown
 * @version 1.0
 */
public class QT {

  // static constants
  static final int PRINT_EXCEPTION_ON_FAIL = 0;
  static final int SHOW_DIALOG_ON_FAIL = 1;
  static final int DO_NOTHING_ON_FAIL = 2;

  // static fields
  private static int failMode = SHOW_DIALOG_ON_FAIL;
  private static ArrayList<QTip> qTips = new ArrayList<QTip>();
  private static Thread shutdownHook;

  // private constructor
  private QT() {/** empty block */}

  /**
   * Start using Quicktime services. This method must be called
   * prior to using Quicktime, and is generally the first method
   * invoked by objects using any Quicktime for Java code.
   */
  public static void start() {
    synchronized(QTSession.class) {
      // if not present, add QT video type to VideoIO
      VideoType[] types = VideoIO.getVideoTypes();
      boolean qtInstalled = false;
      Class<QTVideoType> qtType = QTVideoType.class;
      for (int i = 0; i < types.length; i++) {
        if (types[i].getClass().equals(qtType)) {
          qtInstalled = true;
        }
      }
      if (!qtInstalled) VideoIO.addVideoType(new QTVideoType());
      // open QT session and add shutdown hook to exit
      if (shutdownHook == null) try {
        org.opensourcephysics.controls.OSPLog.finest("QT opening"); //$NON-NLS-1$
        QTSession.open();
        shutdownHook = new Thread() {
          public void run() {
            exit();
          }
        };
        Runtime.getRuntime().addShutdownHook(shutdownHook);
      } catch(QTException ex) {
        switch(failMode) {
          default:
            case PRINT_EXCEPTION_ON_FAIL:
            	ex.printStackTrace();
              break;
            case SHOW_DIALOG_ON_FAIL:
              JOptionPane.showMessageDialog(null,
                  MediaRes.getString("QT.Dialog.NotFound.Message"), //$NON-NLS-1$
                  MediaRes.getString("QT.Dialog.NotFound.Title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
              break;
            case DO_NOTHING_ON_FAIL:
              break;
        } // end switch
      } // end catch
    }
  }

  /**
   * Adds a QTip requiring cleanup services.
   *
   * @param qTip the QTip to be added
   */
  public static synchronized void add(QTip qTip) {
    if (qTip == null) return;
    qTips.add(qTip);
  }

  /**
   * Cleans up and removes a QTip.
   *
   * @param qTip the QTip to be removed
   */
  public static synchronized void remove(QTip qTip) {
    if (qTip == null) return;
    if (qTips.remove(qTip)) {
      try {
        qTip.cleanup();
      } catch(QTException ex) {ex.printStackTrace();}
    }
  }

  /**
   * Called when QuickTime resources are no longer needed--typically when
   * an application is closed or an applet is destroyed
   */
  public static synchronized void exit() {
    org.opensourcephysics.controls.OSPLog.finest("QT exiting"); //$NON-NLS-1$
    // clean up all the registered QTips
    for (QTip qTip: qTips) {
      try {
        qTip.cleanup();
      } catch(QTException ex) {
      }
    }
    qTips.clear();
    synchronized(QTSession.class) {
      QTSession.exitMovies();
    }
    shutdownHook = null;
  }

  /**
   * FailMode specifies the actions taken if QuickTime is not available
   *
   * @param _failMode one of the fail mode constants defined in this class
   */
  public static void setFailMode(int _failMode) {
    failMode = _failMode;
  }
}
