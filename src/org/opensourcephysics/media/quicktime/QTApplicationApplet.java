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

import javax.swing.*;

 /**
  * This is an ApplicationApplet that exits QT when destroyed.
  *
  * @author Douglas Brown
  * @version 1.0
  */
 public class QTApplicationApplet extends JApplet {

   /**
    *  Initializes the applet
    */
   public void init() {
     getParent().enableInputMethods(true); // bug workaround recommended by Apple
     super.init();
   }

   /**
    *  Destroy method
    */
   public void destroy() {
     getParent().enableInputMethods(false); // bug workaround recommended by Apple
     QT.exit();
     super.destroy();
   }

   /**
    *  Bug workaround recommended by Apple
    *
    * @return null
    */
   public java.awt.im.InputContext getInputContext() {
     return null;
   }
 }