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
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.AMedialibrary
import org.videolan.medialibrary.interfaces.media.AAlbum
import org.videolan.medialibrary.interfaces.media.AArtist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.viewmodels.MedialibraryViewModel

@ExperimentalCoroutinesApi
class AlbumSongsViewModel(context: Context, val parent: MediaLibraryItem) : MedialibraryViewModel(context),
        AMedialibrary.MediaCb,
        AMedialibrary.ArtistsCb by EmptyMLCallbacks,
        AMedialibrary.AlbumsCb by EmptyMLCallbacks {

    val albumsProvider = AlbumsProvider(parent, context, this)
    val tracksProvider = TracksProvider(parent, context, this)
    override val providers = arrayOf(albumsProvider, tracksProvider)

    init {
        when (parent) {
            is AArtist -> medialibrary.addArtistsCb(this@AlbumSongsViewModel)
            is AAlbum -> medialibrary.addAlbumsCb(this@AlbumSongsViewModel)
            else -> medialibrary.addMediaCb(this@AlbumSongsViewModel)
        }
    }

    override fun onMediaAdded() { refresh() }

    override fun onMediaModified() { refresh() }

    override fun onMediaDeleted() { refresh() }

    override fun onArtistsModified() { refresh() }

    override fun onAlbumsModified() { refresh() }

    override fun onCleared() {
        when (parent) {
            is AArtist -> medialibrary.removeArtistsCb(this)
            is AAlbum -> medialibrary.removeAlbumsCb(this)
            else -> medialibrary.removeMediaCb(this)
        }
        super.onCleared()
    }

    class Factory(val context: Context, val parent: MediaLibraryItem): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AlbumSongsViewModel(context.applicationContext, parent) as T
        }
    }
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal fun AudioAlbumsSongsFragment.getViewModel(item : MediaLibraryItem) = ViewModelProviders.of(requireActivity(), AlbumSongsViewModel.Factory(requireContext(), item)).get(AlbumSongsViewModel::class.java)
