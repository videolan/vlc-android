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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.Genre
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.Settings


class AlbumModel(context: Context, val parent: MediaLibraryItem? = null): AudioModel(context), Medialibrary.AlbumsCb by EmptyMLCallbacks {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByReleaseDate() = true

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
        if (sort == Medialibrary.SORT_ALPHA && parent is Artist) sort = Medialibrary.SORT_RELEASEDATE
    }

    override fun onAlbumsAdded() {
        refresh()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(Dispatchers.IO) { when (parent) {
                is Artist -> parent.getAlbums(sort, desc)
                is Genre -> parent.getAlbums(sort, desc)
                else -> medialibrary.getAlbums(sort, desc)
            }.toMutableList() as MutableList<MediaLibraryItem>
        }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.addAlbumsCb(this)
    }

    override fun onCleared() {
        medialibrary.removeAlbumsCb(this)
        super.onCleared()
    }

    class Factory(private val context: Context, val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AlbumModel(context.applicationContext, parent) as T
        }
    }
}