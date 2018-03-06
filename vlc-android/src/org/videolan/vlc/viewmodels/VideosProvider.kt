/*****************************************************************************
 * VideosProvider.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.async
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.MediaAddedCb
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.media.MediaGroup
import org.videolan.vlc.util.Util

class VideosProvider(private val group: String?) : MedialibraryModel<MediaWrapper>(), MediaAddedCb {

    override fun canSortByDuration() = true
    override fun canSortByLastModified() = true

    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {
        if (!Util.isArrayEmpty<MediaWrapper>(mediaList)) updateActor.offer(MediaListAddition(mediaList!!.toList()))
    }

    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {
        if (!Util.isArrayEmpty<MediaWrapper>(mediaList)) updateActor.offer(MediaUpdate(mediaList!!.toList()))
    }

    override suspend fun updateList() {
        dataset.value = async {
            val list = medialibrary.getVideos(sort, desc)
            val displayList = mutableListOf<MediaWrapper>()
            if (group !== null) {
                for (item in list) {
                    val title = item.title.substring(if (item.title.toLowerCase().startsWith("the")) 4 else 0)
                    if (title.toLowerCase().startsWith(group.toLowerCase()))
                        displayList.add(item)
                }
            } else {
                //TODO get length value from prefs
                MediaGroup.group(list, 6).mapTo(displayList) { it.media }
            }
            return@async displayList
        }.await()
    }

    override fun onMedialibraryReady() {
        super.onMedialibraryReady()
        medialibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO)
        medialibrary.setMediaAddedCb(this, Medialibrary.FLAG_MEDIA_ADDED_VIDEO)
    }

    override fun onCleared() {
        super.onCleared()
        medialibrary.removeMediaAddedCb()
        medialibrary.removeMediaUpdatedCb()
    }

    class Factory(val group: String?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VideosProvider(group) as T
        }
    }
}
