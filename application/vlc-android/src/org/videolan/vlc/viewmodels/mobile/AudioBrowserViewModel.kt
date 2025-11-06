/*****************************************************************************
 * AudioBrowserViewModel.kt
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.resources.GROUP_VIDEOS_FOLDER
import org.videolan.resources.GROUP_VIDEOS_NAME
import org.videolan.resources.GROUP_VIDEOS_NONE
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.KEY_AUDIO_CURRENT_TAB
import org.videolan.tools.KEY_AUDIO_RESUME_CARD
import org.videolan.tools.KEY_GROUP_VIDEOS
import org.videolan.tools.Settings
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.providers.medialibrary.ArtistsProvider
import org.videolan.vlc.providers.medialibrary.GenresProvider
import org.videolan.vlc.providers.medialibrary.PlaylistsProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.viewmodels.MedialibraryViewModel

class AudioBrowserViewModel(context: Context) : MedialibraryViewModel(context) {

    var currentTab = Settings.getInstance(context).getInt(KEY_AUDIO_CURRENT_TAB, 0)
    val artistsProvider = ArtistsProvider(context, this,
            Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false))
    val albumsProvider = AlbumsProvider(null, context, this)
    val tracksProvider = TracksProvider(null, context, this)
    val genresProvider = GenresProvider(context, this)
    private val playlistsProvider = PlaylistsProvider(context, this, Playlist.Type.Audio)
    override val providers = arrayOf(artistsProvider, albumsProvider, tracksProvider, genresProvider, playlistsProvider)
    val providersInCard = arrayOf(true, true, false, false, true)

    var showResumeCard = settings.getBoolean(KEY_AUDIO_RESUME_CARD, true)
    val displayModeKeys = arrayOf("display_mode_audio_browser_artists", "display_mode_audio_browser_albums", "display_mode_audio_browser_track", "display_mode_audio_browser_genres", "display_mode_playlists_AudioOnly")


    init {
        watchAlbums()
        watchArtists()
        watchGenres()
        watchMedia()
        watchPlaylists()
        watchFor(MediaListEntry.ARTISTS)
        watchFor(MediaListEntry.ALBUMS)
        watchFor(MediaListEntry.TRACKS)
        watchFor(MediaListEntry.GENRES)
        watchFor(MediaListEntry.AUDIO_PLAYLISTS)
        //Initial state coming from preferences and falling back to [providersInCard] hardcoded values
        for (i in displayModeKeys.indices) {
            providersInCard[i] = settings.getBoolean(displayModeKeys[i], providersInCard[i])
        }

    }

    override fun refresh() {
        artistsProvider.showAll = settings.getBoolean(KEY_ARTISTS_SHOW_ALL, false)
        viewModelScope.launch {
            if (currentTab < providers.size) providers[currentTab].awaitRefresh()
            for ((index, provider) in providers.withIndex()) {
                if (index != currentTab && provider.loading.hasObservers()) provider.awaitRefresh()
            }
        }
    }

    class Factory(val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AudioBrowserViewModel(context.applicationContext) as T
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                AudioBrowserViewModel(application)
            }
        }
    }
}

internal fun AudioBrowserFragment.getViewModel() = ViewModelProvider(requireActivity(), AudioBrowserViewModel.Factory(requireContext()))[AudioBrowserViewModel::class.java]