/*
 * ************************************************************************
 *  DummyMediaWrapperProvider.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.vlc.util

import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper

object DummyMediaWrapperProvider {
    fun getDummyMediaWrapper(id: Long): MediaWrapper {
        if (id >= 0) throw IllegalArgumentException("Dummy MediaWrapper id must be < 0")
        return MLServiceLocator.getAbstractMediaWrapper(id, "dummy://Mrl", 0L, 18820L, MediaWrapper.TYPE_VIDEO,
                "", "", "", "",
                "", "", 416, 304, "", 0, -2,
                0, 0, 1509466228L, 0L, true, 1970)
    }
}