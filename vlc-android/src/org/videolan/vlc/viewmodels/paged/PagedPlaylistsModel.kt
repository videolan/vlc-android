/*****************************************************************************
 * PagedPlaylistsModel.kt
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Playlist
import org.videolan.vlc.providers.medialibrary.PlaylistsProvider

@ExperimentalCoroutinesApi
class PagedPlaylistsModel(context: Context): MLPagedModel<Playlist>(context), Medialibrary.PlaylistsCb {
    override val provider = PlaylistsProvider(context, this)

    init {
        medialibrary.addPlaylistCb(this)
        if (medialibrary.isStarted) refresh()
    }

    override fun onCleared() {
        medialibrary.removePlaylistCb(this)
        super.onCleared()
    }

    override fun canSortByDuration() = true

    override fun onPlaylistsAdded() {
        refresh()
    }

    override fun onPlaylistsModified() {
        refresh()
    }

    override fun onPlaylistsDeleted() {
        refresh()
    }

    class Factory(private val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedPlaylistsModel(context.applicationContext) as T
        }
    }
}