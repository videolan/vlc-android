/*****************************************************************************
 * AlbumSongsViewModel.kt
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
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.viewmodels.MedialibraryViewModel

class AlbumSongsViewModel(context: Context, val parent: MediaLibraryItem) : MedialibraryViewModel(context) {

    val albumsProvider = AlbumsProvider(parent, context, this)
    val tracksProvider = TracksProvider(parent, context, this)
    override val providers = arrayOf(albumsProvider, tracksProvider)
    val providersInCard = arrayOf(true, false)
    val displayModeKeys = arrayOf("display_mode_albums_song_albums", "display_mode_albums_song_tracks")

    init {
        when (parent) {
            is Artist -> watchArtists()
            is Album -> watchAlbums()
            else -> watchMedia()
        }
        //Initial state coming from preferences and falling back to [providersInCard] hardcoded values
        for (i in displayModeKeys.indices) {
            providersInCard[i] = settings.getBoolean(displayModeKeys[i], providersInCard[i])
        }
    }

    class Factory(val context: Context, val parent: MediaLibraryItem): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AlbumSongsViewModel(context.applicationContext, parent) as T
        }
    }
}

internal fun AudioAlbumsSongsFragment.getViewModel(item : MediaLibraryItem) = ViewModelProvider(requireActivity(), AlbumSongsViewModel.Factory(requireContext(), item)).get(AlbumSongsViewModel::class.java)
