/*****************************************************************************
 * HistoryModel.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.viewmodels

import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.util.VLCIO

class HistoryModel: BaseModel<MediaWrapper>() {

    override fun canSortByName() = false

    override fun fetch() {
        refresh()
    }

    override suspend fun updateList() {
        dataset.value = withContext(VLCIO) { Medialibrary.getInstance().lastMediaPlayed().toMutableList() }
    }

    fun moveUp(media: MediaWrapper) {
        dataset.value = dataset.value.apply {
            remove(media)
            add(0, media)
        }
    }

    fun clear() {
        dataset.value = mutableListOf()
    }
}