/*
 * ************************************************************************
 *  MainActivityViewModel.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.BuildConfig
import org.videolan.resources.HEADER_PERMISSION
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.util.getFromMl
import org.videolan.television.R
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.util.Permissions

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {


    val playlists: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    var playlistsLoaded = false


    val context = getApplication<Application>().getContextWithLocale(AppContextProvider.locale)


    fun updatePlaylists() = viewModelScope.launch {
        if (playlistsLoaded) return@launch
        playlistsLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updatePlaylists")
        if (!Permissions.canReadStorage(context)) {
            (playlists as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            getPagedPlaylists(Playlist.Type.All,Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            (playlists as MutableLiveData).value = mutableListOf<Playlist>().apply {
                addAll(it)
            }
        }
    }
    
}