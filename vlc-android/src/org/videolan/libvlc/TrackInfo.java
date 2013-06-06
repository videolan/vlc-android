/*****************************************************************************
 * TrackInfo.java
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

public class TrackInfo {

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_TEXT = 2;
    public static final int TYPE_META = 3;

    public int Type;
    public int Id;
    public String Codec;
    public String Language;

    /* Video */
    public int Height;
    public int Width;
    public float Framerate;

    /* Audio */
    public int Channels;
    public int Samplerate;

    /* MetaData */
    public long Length;
    public String Title;
    public String Artist;
    public String Album;
    public String Genre;
    public String ArtworkURL;
}
