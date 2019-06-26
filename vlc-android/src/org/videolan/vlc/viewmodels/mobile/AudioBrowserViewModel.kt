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
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.providers.medialibrary.ArtistsProvider
import org.videolan.vlc.providers.medialibrary.GenresProvider
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.KEY_ARTISTS_SHOW_ALL
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.MedialibraryViewModel


@ExperimentalCoroutinesApi
class AudioBrowserViewModel(context: Context) : MedialibraryViewModel(context),
        AbstractMedialibrary.MediaCb,
        AbstractMedialibrary.ArtistsCb by EmptyMLCallbacks,
        AbstractMedialibrary.AlbumsCb by EmptyMLCallbacks,
        AbstractMedialibrary.GenresCb by EmptyMLCallbacks {

    val artistsProvider = ArtistsProvider(context, this, true)
    val albumsProvider = AlbumsProvider(null, context, this)
    val tracksProvider = TracksProvider(null, context, this)
    val genresProvider = GenresProvider(context, this)
    override val providers = arrayOf(artistsProvider, albumsProvider, tracksProvider, genresProvider)
    val providersInCard = arrayOf(true, true, false, false)

    var showResumeCard = Settings.getInstance(context).getBoolean("audio_resume_card", true)

    init {
        medialibrary.addArtistsCb(this)
        medialibrary.addAlbumsCb(this)
        medialibrary.addGenreCb(this)
        medialibrary.addMediaCb(this)
    }

    override fun onCleared() {
        medialibrary.removeArtistsCb(this)
        medialibrary.removeAlbumsCb(this)
        medialibrary.removeGenreCb(this)
        medialibrary.removeMediaCb(this)
        super.onCleared()
    }

    override fun refresh() {
        artistsProvider.showAll = Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false)
        super.refresh()
    }

    override fun onMediaAdded() { refresh() }
    override fun onMediaModified() { refresh() }
    override fun onMediaDeleted() { refresh() }

    override fun onArtistsAdded() { refresh() }
    override fun onArtistsDeleted() { refresh() }
    override fun onArtistsModified() { refresh() }

    override fun onAlbumsAdded() { refresh() }
    override fun onAlbumsDeleted() { refresh() }
    override fun onAlbumsModified() { refresh() }

    override fun onGenresAdded() { refresh() }
    override fun onGenresModified() { refresh() }
    override fun onGenresDeleted() { refresh() }

    class Factory(val context: Context): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AudioBrowserViewModel(context.applicationContext) as T
        }
    }
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal fun AudioBrowserFragment.getViewModel() = ViewModelProviders.of(requireActivity(), AudioBrowserViewModel.Factory(requireContext())).get(AudioBrowserViewModel::class.java)