/*
 * ************************************************************************
 *  VlcMigrationHelper.java
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc;

import org.videolan.libvlc.interfaces.IMedia;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class VlcMigrationHelper {
    public static List<IMedia.Track> getMediaTracks(IMedia media) {
        IMedia.Track[] tracks = media.getTracks();
        if (tracks == null) {
            return new ArrayList<IMedia.Track>();
        }
        return Arrays.asList(tracks);
    }
}
