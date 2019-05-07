/*****************************************************************************
 * PagedTracksModel.kt
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

package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.*
import org.videolan.vlc.providers.medialibrary.TracksProvider
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.Settings

@ExperimentalCoroutinesApi
class PagedTracksModel(context: Context, val parent: MediaLibraryItem? = null): MLPagedModel<MediaWrapper>(context),
        Medialibrary.MediaCb,
        Medialibrary.ArtistsCb by EmptyMLCallbacks,
        Medialibrary.AlbumsCb by EmptyMLCallbacks,
        Medialibrary.GenresCb by EmptyMLCallbacks,
        Medialibrary.PlaylistsCb by EmptyMLCallbacks {

    override val provider = TracksProvider(parent, context, this)
    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", parent is Artist)
        if (sort == Medialibrary.SORT_ALPHA) sort = when (parent) {
            is Artist -> Medialibrary.SORT_ALBUM
            is Album -> Medialibrary.SORT_DEFAULT
            else -> Medialibrary.SORT_ALPHA
        }
        if (medialibrary.isStarted) refresh()
        when (parent) {
            is Artist -> medialibrary.addArtistsCb(this@PagedTracksModel)
            is Album -> medialibrary.addAlbumsCb(this@PagedTracksModel)
            is Genre -> medialibrary.addGenreCb(this@PagedTracksModel)
            is Playlist -> medialibrary.addPlaylistCb(this@PagedTracksModel)
            else -> medialibrary.addMediaCb(this@PagedTracksModel)
        }
    }

    override fun onCleared() {
        when (parent) {
            is Artist -> medialibrary.removeArtistsCb(this)
            is Album -> medialibrary.removeAlbumsCb(this)
            is Genre -> medialibrary.removeGenreCb(this)
            is Playlist -> medialibrary.removePlaylistCb(this)
            else -> medialibrary.removeMediaCb(this)
        }
        super.onCleared()
    }

    class Factory(private val context: Context, private val parent: MediaLibraryItem?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedTracksModel(context.applicationContext, parent) as T
        }
    }

    override fun onMediaAdded() {
        refresh()
    }

    override fun onMediaModified() {
        refresh()
    }

    override fun onMediaDeleted() {
        refresh()
    }

    override fun onArtistsModified() {
        refresh()
    }

    override fun onAlbumsModified() {
        refresh()
    }

    override fun onGenresModified() {
        refresh()
    }

    override fun onPlaylistsModified() {
        refresh()
        if ((parent as Playlist).realTracksCount == 0) parent.delete()
    }
}
