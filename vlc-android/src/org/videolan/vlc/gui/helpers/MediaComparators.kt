/*****************************************************************************
 * MediaComparators.java
 *
 * Copyright © 2013 VLC authors and VideoLAN
 *
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
 */
package org.videolan.vlc.gui.helpers

import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import java.util.*

object MediaComparators {

    val BY_TRACK_NUMBER: Comparator<AbstractMediaWrapper> = Comparator { m1, m2 ->
        if (m1.discNumber < m2.discNumber) return@Comparator -1
        if (m1.discNumber > m2.discNumber) return@Comparator 1
        if (m1.trackNumber < m2.trackNumber) return@Comparator -1
        if (m1.trackNumber > m2.trackNumber)
            1
        else
            0
    }

}
