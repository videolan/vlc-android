/*
 * ************************************************************************
 *  MediaListsViewModel.kt
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
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

private const val TAG = "VLC/MediaListsViewModel"
class MediaListsViewModel(app: Application) : AndroidViewModel(app) {


    val audioArtists: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioAlbums: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioTracks: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioGenres: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioPlaylists: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val videos: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    var videosLoaded = false
    var audioArtistLoaded = false
    var audioAlbumLoaded = false
    var audioTrackLoaded = false
    var audioGenreLoaded = false
    var audioPlaylistLoaded = false

    private fun getContext() = getApplication<Application>().applicationContext.getContextWithLocale(AppContextProvider.locale)

    private suspend fun showPermissionItem(list: MutableLiveData<List<MediaLibraryItem>>) = withContext(Dispatchers.Main) {
        list.value = listOf(DummyItem(HEADER_PERMISSION, getContext().getString(org.videolan.vlc.R.string.permission_media), getContext().getString(org.videolan.vlc.R.string.permission_ask_again)))
    }

    fun updateVideos() = viewModelScope.launch {
        if (videosLoaded) return@launch
        videosLoaded = true
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(videos)
            return@launch
        }
        getContext().getFromMl {
            getPagedVideos(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            videos.value = mutableListOf<MediaLibraryItem>().apply {
                addAll(it)
            }
        }
    }

    fun updateAudioTracks() = viewModelScope.launch(Dispatchers.IO) {
        if (audioTrackLoaded) return@launch
        audioTrackLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateAudioTracks")
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(audioTracks)
            return@launch
        }
        getContext().getFromMl {
            getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            withContext(Dispatchers.Main) {
                audioTracks.value = it.toMutableList()
            }
        }
    }

    fun updateAudioArtists() = viewModelScope.launch(Dispatchers.IO) {
        if (audioArtistLoaded) return@launch
        audioArtistLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateAudioArtists")
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(audioArtists)
            return@launch
        }
        getContext().getFromMl {
            getPagedArtists(true, Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            withContext(Dispatchers.Main) {
                audioArtists.value = it.toMutableList()
            }
        }
    }

    fun updateAudioAlbums() = viewModelScope.launch(Dispatchers.IO) {
        if (audioAlbumLoaded) return@launch
        audioAlbumLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateAudioAlbums")
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(audioAlbums)
            return@launch
        }
        getContext().getFromMl {
            getPagedAlbums(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            withContext(Dispatchers.Main) {
                audioAlbums.value = it.toMutableList()
            }
        }
    }

    fun updateAudioPlaylists() = viewModelScope.launch(Dispatchers.IO) {
        if (audioPlaylistLoaded) return@launch
        audioPlaylistLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateAudioPlaylists")
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(audioPlaylists)
            return@launch
        }
        getContext().getFromMl {
            getPagedPlaylists(Playlist.Type.Audio, Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            withContext(Dispatchers.Main) {
                audioPlaylists.value = it.toMutableList()
            }
        }
    }

    fun updateAudioGenres() = viewModelScope.launch(Dispatchers.IO) {
        if (audioGenreLoaded) return@launch
        audioGenreLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateAudioGenres")
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(audioGenres)
            return@launch
        }
        getContext().getFromMl {
            getPagedGenres(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            withContext(Dispatchers.Main) {
                audioGenres.value = it.toMutableList()
            }
        }
    }
}