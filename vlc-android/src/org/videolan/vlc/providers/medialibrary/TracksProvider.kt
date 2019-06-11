/*****************************************************************************
 * TracksProvider.kt
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.videolan.medialibrary.interfaces.AMedialibrary
import org.videolan.medialibrary.interfaces.media.*
import org.videolan.medialibrary.media.*
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.SortableModel


@ExperimentalCoroutinesApi
class TracksProvider(val parent : MediaLibraryItem?, context: Context, scope: SortableModel) : MedialibraryProvider<AMediaWrapper>(context, scope) {

    override val sortKey = "${super.sortKey}_${parent?.javaClass?.simpleName}"
    override fun canSortByDuration() = true
    override fun canSortByAlbum() = parent !== null
    override fun canSortByLastModified() = true

    init {
        sort = Settings.getInstance(context).getInt(sortKey, AMedialibrary.SORT_DEFAULT)
        desc = Settings.getInstance(context).getBoolean("${sortKey}_desc", parent is AArtist)
        if (sort == AMedialibrary.SORT_DEFAULT) sort = when (parent) {
            is AArtist -> AMedialibrary.SORT_ALBUM
            is AAlbum -> AMedialibrary.SORT_DEFAULT
            else -> AMedialibrary.SORT_ALPHA
        }
    }

    override fun getAll(): Array<AMediaWrapper> = parent?.tracks ?: medialibrary.getAudio(sort, scope.desc)

    override fun getPage(loadSize: Int, startposition: Int) : Array<AMediaWrapper> {
        val list = if (scope.filterQuery == null) when(parent) {
            is AArtist -> parent.getPagedTracks(sort, scope.desc, loadSize, startposition)
            is AAlbum -> parent.getPagedTracks(sort, scope.desc, loadSize, startposition)
            is AGenre -> parent.getPagedTracks(sort, scope.desc, loadSize, startposition)
            is APlaylist -> parent.getPagedTracks(loadSize, startposition)
            else -> medialibrary.getPagedAudio(sort, scope.desc, loadSize, startposition)
        } else when(parent) {
            is AArtist -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            is AAlbum -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            is AGenre -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            is APlaylist -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            else -> medialibrary.searchAudio(scope.filterQuery, sort, scope.desc, loadSize, startposition)
        }
        return list.also { completeHeaders(it, startposition) }
    }

    override fun getTotalCount() = if (scope.filterQuery == null) when (parent) {
        is AAlbum -> parent.realTracksCount
        is APlaylist -> parent.realTracksCount
        is AArtist,
        is AGenre -> parent.tracksCount
        else -> medialibrary.audioCount
    } else when(parent) {
        is AArtist -> parent.searchTracksCount(scope.filterQuery)
        is AAlbum -> parent.searchTracksCount(scope.filterQuery)
        is AGenre -> parent.searchTracksCount(scope.filterQuery)
        is APlaylist -> parent.searchTracksCount(scope.filterQuery)
        else ->medialibrary.getAudioCount(scope.filterQuery)
    }
}