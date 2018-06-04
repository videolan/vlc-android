/*****************************************************************************
 * AlbumModel.kt
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

package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.Genre
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.VLCIO


class AlbumModel(val parent: MediaLibraryItem? = null): AudioModel(), Medialibrary.AlbumsAddedCb {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByReleaseDate() = true

    init {
        sort = VLCApplication.getSettings().getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = VLCApplication.getSettings().getBoolean("${sortKey}_desc", false)
        if (sort == Medialibrary.SORT_ALPHA && parent is Artist) sort = Medialibrary.SORT_RELEASEDATE
    }

    override fun onAlbumsAdded() {
        refresh()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(VLCIO) { when (parent) {
                is Artist -> parent.getAlbums(sort, desc)
                is Genre -> parent.getAlbums(sort, desc)
                else -> medialibrary.getAlbums(sort, desc)
            }.toMutableList() as MutableList<MediaLibraryItem>
        }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.setAlbumsAddedCb(this)
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.setAlbumsAddedCb(null)
    }

    class Factory(val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AlbumModel(parent) as T
        }
    }
}