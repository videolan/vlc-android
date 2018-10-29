/*****************************************************************************
 * PlaylistsModel.kt
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
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.EmptyMLCallbacks


class PlaylistsModel(context: Context): AudioModel(context), Medialibrary.PlaylistsCb by EmptyMLCallbacks {

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.addPlaylistCb(this)
    }

    override fun onCleared() {
        medialibrary.removePlaylistCb(this)
        super.onCleared()
    }

    override fun canSortByDuration() = true

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateList() {
        dataset.value = withContext(Dispatchers.IO) { medialibrary.getPlaylists(sort, desc).toMutableList() as MutableList<MediaLibraryItem> }
    }

    class Factory(private val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaylistsModel(context.applicationContext) as T
        }
    }

    override fun onPlaylistsAdded() {
        refresh()
    }

    override fun onPlaylistsDeleted() {
        refresh()
    }
}