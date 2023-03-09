/*****************************************************************************
 * PlaylistViewModel.kt
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

package org.videolan.vlc.viewmodels.mobile

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.HeaderMediaListActivity
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.viewmodels.MedialibraryViewModel
import java.util.ArrayList

class PlaylistViewModel(context: Context, private val initialPlaylist: MediaLibraryItem) : MedialibraryViewModel(context) {

    val tracksProvider = TracksProvider(initialPlaylist, context, this)
    override val providers : Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(tracksProvider)
    var playlistLiveData: MutableLiveData<MediaLibraryItem> = MutableLiveData()

    val playlist:MediaLibraryItem?
        get() = playlistLiveData.value

    init {
        when (initialPlaylist) {
            is Playlist -> {
                watchPlaylists()
                watchMedia()
            }
            is Album -> {
                watchAlbums()
                watchMedia()
            }
            else -> watchMedia()
        }
        viewModelScope.registerCallBacks { refresh() }
        viewModelScope.launch {
            refreshPlaylistItem()
        }
    }

    override fun refresh() {
        viewModelScope.launch {
            refreshPlaylistItem()
            super.refresh()
        }
    }

    private suspend fun refreshPlaylistItem() {
        withContext(Dispatchers.IO) {
            when (initialPlaylist) {
                is Album -> playlistLiveData.postValue(medialibrary.getAlbum(initialPlaylist.id))
                is Playlist -> playlistLiveData.postValue(medialibrary.getPlaylist(initialPlaylist.id, true, false))
            }
        }
    }

    class Factory(val context: Context, val playlist: MediaLibraryItem): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaylistViewModel(context.applicationContext, playlist) as T
        }
    }

    suspend fun rename(media: MediaWrapper, name:String) {
        withContext(Dispatchers.IO) { (media as? MediaWrapper)?.rename(name) }
        refresh()
    }

    suspend fun toggleFavorite() = withContext(Dispatchers.IO) {
        playlist?.let { it.setFavorite(!it.isFavorite) }
    }
}

internal fun HeaderMediaListActivity.getViewModel(playlist: MediaLibraryItem) = ViewModelProvider(this, PlaylistViewModel.Factory(this, playlist)).get(PlaylistViewModel::class.java)