/*****************************************************************************
 * AlbumsProvider.kt
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

package org.videolan.vlc.providers.medialibrary

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.Settings
import org.videolan.vlc.viewmodels.SortableModel


@ExperimentalCoroutinesApi
class AlbumsProvider(val parent : MediaLibraryItem?, context: Context, model: SortableModel) : MedialibraryProvider<Album>(context, model) {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByReleaseDate() = true

    init {
        sort = Settings.getInstance(context).getInt(sortKey, if (parent is Artist) Medialibrary.SORT_RELEASEDATE else Medialibrary.SORT_DEFAULT)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", false)
    }

    override fun getAll() : Array<Album> = when (parent) {
        is Artist -> parent.getAlbums(sort, desc)
        is Genre -> parent.getAlbums(sort, desc)
        else -> medialibrary.getAlbums(sort, desc)
    }

    override fun getPage(loadSize: Int, startposition: Int) : Array<Album> {
        val list = if (model.filterQuery == null) when(parent) {
            is Artist -> parent.getPagedAlbums(sort, desc, loadSize, startposition)
            is Genre -> parent.getPagedAlbums(sort, desc, loadSize, startposition)
            else -> medialibrary.getPagedAlbums(sort, desc, loadSize, startposition)
        } else when(parent) {
            is Artist -> parent.searchAlbums(model.filterQuery, sort, desc, loadSize, startposition)
            is Genre -> parent.searchAlbums(model.filterQuery, sort, desc, loadSize, startposition)
            else -> medialibrary.searchAlbum(model.filterQuery, sort, desc, loadSize, startposition)
        }
        model.viewModelScope.launch { completeHeaders(list, startposition) }
        return list
    }

    override fun getTotalCount() = if (model.filterQuery == null) when(parent) {
        is Artist -> parent.albumsCount
        is Genre -> parent.albumsCount
        else -> medialibrary.albumsCount
    } else when (parent) {
        is Artist -> parent.searchAlbumsCount(model.filterQuery)
        is Genre -> parent.searchAlbumsCount(model.filterQuery)
        else -> medialibrary.getAlbumsCount(model.filterQuery)
    }
}