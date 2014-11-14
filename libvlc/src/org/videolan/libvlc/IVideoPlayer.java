/*****************************************************************************
 * IVideoPlayer.java
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.libvlc;

import android.view.Surface;

public interface IVideoPlayer {
    /**
     * This method is called by native vout to request a new layout.
     * @param width Frame width
     * @param height Frame height
     * @param visible_width Visible frame width
     * @param visible_height Visible frame height
     * @param sar_num Surface aspect ratio numerator
     * @param sar_den Surface aspect ratio denominator
     */
    void setSurfaceLayout(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den);

    /**
     * This method is only used for Gingerbread and before.
     * It is called by native vout to request a surface size and hal.
     * It is synchronous.
     * @param surface
     * @param width surface width
     * @param height surface height
     * @param hal color format (or PixelFormat)
     * @return -1 if you should'nt not use this call, 1 if surface size is changed, 0 otherwise
     */
    int configureSurface(Surface surface, int width, int height, int hal);


    /**
     * Called in case of hardware acceleration error
     */
    public void eventHardwareAccelerationError();
}
