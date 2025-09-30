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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.BuildConfig
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.util.getFromMl
import org.videolan.vlc.util.Permissions

private const val TAG = "VLC/MediaListsViewModel"
class MediaListsViewModel(app: Application) : TvMediaViewModel(app) {


    val audioArtists: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioAlbums: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioTracks: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioGenres: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioPlaylists: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val allPlaylists: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val videos: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()

    val audioArtistsLoading = MutableLiveData(false)
    val audioAlbumsLoading = MutableLiveData(false)
    val audioTracksLoading = MutableLiveData(false)
    val audioGenresLoading = MutableLiveData(false)
    val audioPlaylistsLoading = MutableLiveData(false)
    val allPlaylistsLoading = MutableLiveData(false)
    val videoLoading = MutableLiveData(false)

    var audioArtistLoaded = false
    var audioAlbumLoaded = false
    var audioTrackLoaded = false
    var audioGenreLoaded = false
    var audioPlaylistLoaded = false
    var allPlaylistLoaded = false
    var videosLoaded = false

    fun updateVideos() = viewModelScope.launch {
        if (videosLoaded) return@launch
        videosLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateVideos -> true")
        setLoading(videoLoading, true)
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
        setLoading(videoLoading, false)
        if (BuildConfig.DEBUG) Log.d(TAG, "updateVideos -> false")
    }

    fun updateAudioTracks() = viewModelScope.launch(Dispatchers.IO) {
        if (audioTrackLoaded) return@launch
        audioTrackLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateAudioTracks")
        setLoading(audioTracksLoading, true)
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
        setLoading(audioTracksLoading, false)
    }

    fun updateAudioArtists() = viewModelScope.launch(Dispatchers.IO) {
        if (audioArtistLoaded) return@launch
        audioArtistLoaded = true
        setLoading(audioArtistsLoading, true)
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
        setLoading(audioArtistsLoading, false)
    }

    fun updateAudioAlbums() = viewModelScope.launch(Dispatchers.IO) {
        if (audioAlbumLoaded) return@launch
        audioAlbumLoaded = true
        setLoading(audioAlbumsLoading, true)
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
        setLoading(audioAlbumsLoading, false)
    }

    fun updateAudioPlaylists() = viewModelScope.launch(Dispatchers.IO) {
        if (audioPlaylistLoaded) return@launch
        audioPlaylistLoaded = true
        setLoading(audioPlaylistsLoading, true)
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
        setLoading(audioPlaylistsLoading, false)
    }

    fun updateAudioGenres() = viewModelScope.launch(Dispatchers.IO) {
        if (audioGenreLoaded) return@launch
        audioGenreLoaded = true
        setLoading(audioGenresLoading, true)
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
        setLoading(audioGenresLoading, false)
    }

    fun updateAllPlaylists() = viewModelScope.launch(Dispatchers.IO) {
        if (allPlaylistLoaded) return@launch
        allPlaylistLoaded = true
        setLoading(allPlaylistsLoading, true)
        if (BuildConfig.DEBUG) Log.d(TAG, "updateAudioPlaylists")
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(audioPlaylists)
            return@launch
        }
        getContext().getFromMl {
            getPagedPlaylists(Playlist.Type.All,Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            withContext(Dispatchers.Main) {
                allPlaylists.value = it.toMutableList()
            }
        }
        setLoading(allPlaylistsLoading, false)
    }
}