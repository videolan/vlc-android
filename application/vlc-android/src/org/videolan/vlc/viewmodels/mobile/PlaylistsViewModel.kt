/*****************************************************************************
 * PlaylistsViewModel.kt
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
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.PlaylistFragment
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.PlaylistsProvider
import org.videolan.vlc.viewmodels.MedialibraryViewModel

class PlaylistsViewModel(context: Context, type: Playlist.Type) : MedialibraryViewModel(context) {
    val displayModeKey: String = "display_mode_playlists_$type"
    val provider = PlaylistsProvider(context, this, type)
    var providerInCard = true
    override val providers : Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(provider)

    init {
        watchPlaylists()
        providerInCard = settings.getBoolean(displayModeKey, providerInCard)
    }

    class Factory(val context: Context, val type: Playlist.Type): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaylistsViewModel(context.applicationContext, type) as T
        }
    }
}

internal fun PlaylistFragment.getViewModel(type: Playlist.Type) =ViewModelProvider(this, PlaylistsViewModel.Factory(requireContext(), type)).get(PlaylistsViewModel::class.java)