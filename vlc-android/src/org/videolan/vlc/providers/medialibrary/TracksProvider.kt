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
import org.videolan.medialibrary.media.*
import org.videolan.vlc.viewmodels.SortableModel


@ExperimentalCoroutinesApi
class TracksProvider(val parent : MediaLibraryItem?, context: Context, scope: SortableModel) : MedialibraryProvider<MediaWrapper>(context, scope) {

    override fun canSortByDuration() = true
    override fun canSortByAlbum() = parent !== null
    override fun canSortByLastModified() = true

    override fun getAll(): Array<MediaWrapper> = parent?.tracks ?: medialibrary.getAudio(sort, scope.desc)

    override fun getPage(loadSize: Int, startposition: Int) : Array<MediaWrapper> {
        val list = if (scope.filterQuery == null) when(parent) {
            is Artist -> parent.getPagedTracks(sort, scope.desc, loadSize, startposition)
            is Album -> parent.getPagedTracks(sort, scope.desc, loadSize, startposition)
            is Genre -> parent.getPagedTracks(sort, scope.desc, loadSize, startposition)
            is Playlist -> parent.getPagedTracks(loadSize, startposition)
            else -> medialibrary.getPagedAudio(sort, scope.desc, loadSize, startposition)
        } else when(parent) {
            is Artist -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            is Album -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            is Genre -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            is Playlist -> parent.searchTracks(scope.filterQuery, sort, scope.desc, loadSize, startposition)
            else -> medialibrary.searchAudio(scope.filterQuery, sort, scope.desc, loadSize, startposition)
        }
        return list.also { completeHeaders(it, startposition) }
    }

    override fun getTotalCount() = if (scope.filterQuery == null) when (parent) {
        is Album -> parent.realTracksCount
        is Playlist -> parent.realTracksCount
        is Artist,
        is Genre -> parent.tracksCount
        else -> medialibrary.audioCount
    } else when(parent) {
        is Artist -> parent.searchTracksCount(scope.filterQuery)
        is Album -> parent.searchTracksCount(scope.filterQuery)
        is Genre -> parent.searchTracksCount(scope.filterQuery)
        is Playlist -> parent.searchTracksCount(scope.filterQuery)
        else ->medialibrary.getAudioCount(scope.filterQuery)
    }
}