/*****************************************************************************
 * PagedAlbumsModel.kt
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
import org.videolan.medialibrary.media.Album
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.medialibrary.AlbumsProvider
import org.videolan.vlc.util.EmptyMLCallbacks
import org.videolan.vlc.util.Settings


@ExperimentalCoroutinesApi
class PagedAlbumsModel(context: Context, val parent: MediaLibraryItem? = null) : MLPagedModel<Album>(context), Medialibrary.AlbumsCb by EmptyMLCallbacks {

    override val provider = AlbumsProvider(parent, context, this)
    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"

    init {
        sort = Settings.getInstance(context).getInt(sortKey, Medialibrary.SORT_ALPHA)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
        if (sort == Medialibrary.SORT_ALPHA && parent is Artist) sort = Medialibrary.SORT_RELEASEDATE
        medialibrary.addAlbumsCb(this)
        if (medialibrary.isStarted) refresh()
    }

    override fun onAlbumsAdded() {
        refresh()
    }

    override fun onCleared() {
        medialibrary.removeAlbumsCb(this)
        super.onCleared()
    }

    class Factory(private val context: Context, val parent: MediaLibraryItem?) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedAlbumsModel(context.applicationContext, parent) as T
        }
    }
}