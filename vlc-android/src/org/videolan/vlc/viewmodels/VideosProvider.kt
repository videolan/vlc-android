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
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.MediaAddedCb
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.media.MediaGroup
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.Util

class VideosProvider(private val group: String?) : MedialibraryModel<MediaWrapper>(), MediaAddedCb {

    var sort = Constants.SORT_DEFAULT
    var desc = false

    private val updateActor = actor<Update>(capacity = Channel.UNLIMITED) {
        for (update in channel) when(update) {
            Refresh -> updateList()
            is MediaUpdate -> updateItems(update.mediaList)
            is MediaAddition -> addMedia(update.mediaList)
            is Remove -> removeMedia(update.mw)
            is Sort -> {
                if (sort == update.sort) desc = !desc
                sort = update.sort
                updateList()
            }
            is Filter -> filter(update.query)
        }
    }

    override fun refresh() {
        updateActor.offer(Refresh)
    }

    fun sort(sort: Int) {
        updateActor.offer(Sort(sort))
    }

    fun remove(mw:MediaWrapper) {
        updateActor.offer(Remove(mw))
    }

    override fun onMediaAdded(mediaList: Array<out MediaWrapper>?) {
        if (!Util.isArrayEmpty<MediaWrapper>(mediaList)) updateActor.offer(MediaAddition(mediaList!!.toList()))
    }

    override fun onMediaUpdated(mediaList: Array<out MediaWrapper>?) {
        if (!Util.isArrayEmpty<MediaWrapper>(mediaList)) updateActor.offer(MediaUpdate(mediaList!!.toList()))
    }

    private fun updateList() {
        val list = medialibrary.getVideos(sort, desc)
        val displayList = mutableListOf<MediaWrapper>()
        if (group !== null) {
            for (item in list) {
                val title = item.title.substring(if (item.title.toLowerCase().startsWith("the")) 4 else 0)
                if (title.toLowerCase().startsWith(group.toLowerCase()))
                    displayList.add(item)
            }
        } else {
            MediaGroup.group(list).mapTo(displayList) { it.media }
        }
        dataset.postValue(displayList)
    }

    private fun removeMedia(mw: MediaWrapper) {
        dataset.value?.let {
            dataset.postValue(it.apply { this.remove(mw) })
        }
    }

    private fun addMedia(mediaList: List<MediaWrapper>) {
        val list = dataset.value ?: mutableListOf<MediaWrapper>()
        if (list.isEmpty()) dataset.postValue(mediaList.toMutableList())
        else dataset.postValue(list.apply { this.addAll(mediaList) })
    }

    private fun updateItems(mediaList: List<MediaWrapper>) {
        val list = dataset.value?.toMutableList() ?: mutableListOf<MediaWrapper>()
        val iterator = list.listIterator()
        for (media in iterator) {
            for (newItem in mediaList) if (media.equals(newItem)) {
                iterator.set(newItem)
                break
            }
        }
        dataset.postValue(list)
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

sealed class Update
object Refresh : Update()
data class MediaUpdate(val mediaList: List<MediaWrapper>) : Update()
data class MediaAddition(val mediaList: List<MediaWrapper>) : Update()
data class Sort(val sort: Int) : Update()
data class Remove(val mw: MediaWrapper) : Update()
data class Filter(val query: String?) : Update()
