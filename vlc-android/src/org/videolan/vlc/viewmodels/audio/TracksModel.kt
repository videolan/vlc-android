/*****************************************************************************
 * TracksModel.kt
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
import org.videolan.medialibrary.media.*
import org.videolan.vlc.util.Settings

class TracksModel(context: Context, val parent: MediaLibraryItem? = null): AudioModel(context), Medialibrary.MediaCb {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByAlbum() = parent !== null

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
        if (sort == Medialibrary.SORT_ALPHA) sort = when (parent) {
            is Artist -> Medialibrary.SORT_ALBUM
            is Album -> Medialibrary.SORT_DEFAULT
            else -> Medialibrary.SORT_ALPHA
        }
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.addMediaCb(this)
    }

    override fun onCleared() {
        medialibrary.removeMediaCb(this)
        super.onCleared()
    }

    override fun onMediaAdded() {
        refresh()
    }

    override fun onMediaModified() {
        refresh()
    }

    override fun onMediaDeleted() {
        refresh()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(Dispatchers.IO) {
            when (parent) {
                is Artist -> parent.getTracks(sort, desc)
                is Album -> parent.getTracks(sort, desc)
                is Genre -> parent.getTracks(sort, desc)
                is Playlist -> parent.getTracks()
                else -> medialibrary.getAudio(sort, desc)
            }.toMutableList() as MutableList<MediaLibraryItem>
        }
    }

    class Factory(private val context: Context, private val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TracksModel(context.applicationContext, parent) as T
        }
    }
}
