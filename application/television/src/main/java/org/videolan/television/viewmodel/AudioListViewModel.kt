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

class AudioListViewModel(app: Application) : AndroidViewModel(app) {


    val audioArtists: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioAlbums: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioTracks: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioGenres: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val audioPlaylists: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    var audioArtistLoaded = false
    var audioAlbumLoaded = false
    var audioTrackLoaded = false
    var audioGenreLoaded = false
    var audioPlaylistLoaded = false


    val context = getApplication<Application>().getContextWithLocale(AppContextProvider.locale)
    val audioTabs = listOf(
        R.string.artists,
        R.string.albums,
        R.string.tracks,
        R.string.genres,
        R.string.playlists,
    )


    fun updateAudioTracks() = viewModelScope.launch {
        if (audioTrackLoaded) return@launch
        audioTrackLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updateAudioTracks")
        if (!Permissions.canReadStorage(context)) {
            (audioTracks as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            (audioTracks as MutableLiveData).value = mutableListOf<MediaWrapper>().apply {
                addAll(it)
            }
        }
    }
    fun updateAudioArtists() = viewModelScope.launch {
        if (audioArtistLoaded) return@launch
        audioArtistLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updateAudioArtists")
        if (!Permissions.canReadStorage(context)) {
            (audioArtists as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            getPagedArtists(true, Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            (audioArtists as MutableLiveData).value = mutableListOf<Artist>().apply {
                addAll(it)
            }
        }
    }
    fun updateAudioAlbums() = viewModelScope.launch {
        if (audioAlbumLoaded) return@launch
        audioAlbumLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updateAudioAlbums")
        if (!Permissions.canReadStorage(context)) {
            (audioAlbums as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            getPagedAlbums( Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            (audioAlbums as MutableLiveData).value = mutableListOf<Album>().apply {
                addAll(it)
            }
        }
    }

    fun updateAudioPlaylists() = viewModelScope.launch {
        if (audioPlaylistLoaded) return@launch
        audioPlaylistLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updateAudioPlaylists")
        if (!Permissions.canReadStorage(context)) {
            (audioPlaylists as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            getPagedPlaylists(Playlist.Type.Audio,  Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            (audioPlaylists as MutableLiveData).value = mutableListOf<Playlist>().apply {
                addAll(it)
            }
        }
    }

    fun updateAudioGenres() = viewModelScope.launch {
        if (audioGenreLoaded) return@launch
        audioGenreLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updateAudioGenres")
        if (!Permissions.canReadStorage(context)) {
            (audioGenres as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            getPagedGenres( Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, 0)
        }.let {
            (audioGenres as MutableLiveData).value = mutableListOf<Genre>().apply {
                addAll(it)
            }
        }
    }
}