/*****************************************************************************
 * PlaylistsProvider.kt
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
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.vlc.viewmodels.SortableModel


@ExperimentalCoroutinesApi
class PlaylistsProvider(context: Context, model: SortableModel) : MedialibraryProvider<Playlist>(context, model) {

    override fun getAll() : Array<Playlist> = medialibrary.getPlaylists(sort, desc)

    override fun getPage(loadSize: Int, startposition: Int)  : Array<Playlist> {
        val list = if (model.filterQuery == null) medialibrary.getPagedPlaylists(sort, desc, loadSize, startposition)
        else medialibrary.searchPlaylist(model.filterQuery, sort, desc, loadSize, startposition)
        model.viewModelScope.launch { completeHeaders(list, startposition) }
        return list
    }

    override fun getTotalCount() = if (model.filterQuery == null) medialibrary.playlistsCount else medialibrary.getPlaylistsCount(model.filterQuery)
}