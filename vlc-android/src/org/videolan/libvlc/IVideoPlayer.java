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

public interface IVideoPlayer {
    /**
     * This method is called by native vout to request a surface resize when frame size doesn't match surface size.
     * @param width Frame width
     * @param height Frame height
     * @param visible_width Visible frame width
     * @param visible_height Visible frame height
     * @param sar_num Surface aspect ratio numerator
     * @param sar_den Surface aspect ratio denominator
     */
    void setSurfaceSize(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den);
}
