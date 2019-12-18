/*****************************************************************************
 * VideosProvider.kt
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
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup
import org.videolan.vlc.media.getAll
import org.videolan.vlc.viewmodels.SortableModel


@ExperimentalCoroutinesApi
class VideosProvider(val folder : AbstractFolder?, val group: AbstractVideoGroup?, context: Context, model: SortableModel) : MedialibraryProvider<MediaWrapper>(context, model){

    override fun canSortByFileNameName() = true
    override fun canSortByDuration() = true
    override fun canSortByLastModified() = folder == null

    override fun getTotalCount() = if (model.filterQuery == null) when {
        folder !== null -> folder.mediaCount(AbstractFolder.TYPE_FOLDER_VIDEO)
        group !== null -> group.mediaCount()
        else -> medialibrary.videoCount
    } else when {
        folder !== null -> folder.searchTracksCount(model.filterQuery, AbstractFolder.TYPE_FOLDER_VIDEO)
        group !== null -> group.searchTracksCount(model.filterQuery)
        else -> medialibrary.getVideoCount(model.filterQuery)
    }

    override fun getPage(loadSize: Int, startposition: Int): Array<MediaWrapper> {
        val list = if (model.filterQuery == null) when {
            folder !== null -> folder.media(AbstractFolder.TYPE_FOLDER_VIDEO, sort, desc, loadSize, startposition)
            group !== null -> group.media(sort, desc, loadSize, startposition)
            else -> medialibrary.getPagedVideos(sort, desc, loadSize, startposition)
        } else when {
            folder !== null -> folder.searchTracks(model.filterQuery, AbstractFolder.TYPE_FOLDER_VIDEO, sort, desc, loadSize, startposition)
            group !== null -> group.searchTracks(model.filterQuery, sort, desc, loadSize, startposition)
            else -> medialibrary.searchVideo(model.filterQuery, sort, desc, loadSize, startposition)
        }
        return list.also { completeHeaders(it, startposition) }
    }

    override fun getAll(): Array<MediaWrapper> = when {
        folder !== null -> folder.getAll(AbstractFolder.TYPE_FOLDER_VIDEO, sort, desc).toTypedArray()
        group !== null -> group.getAll(sort, desc).toTypedArray()
        else -> medialibrary.getVideos(sort, desc)
    }
}
