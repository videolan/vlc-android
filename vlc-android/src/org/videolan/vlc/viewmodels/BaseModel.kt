/*****************************************************************************
 * FilterableModel.kt
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

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.FilterDelagate

abstract class BaseModel<T : MediaLibraryItem> : ViewModel() {

    var sort = Constants.SORT_DEFAULT
    var desc = false

    private val filter by lazy(LazyThreadSafetyMode.NONE) { FilterDelagate(dataset) }

    val dataset by lazy {
        fetch()
        MutableLiveData<MutableList<T>>()
    }

    @Suppress("UNCHECKED_CAST")
    protected val updateActor = actor<Update>(capacity = Channel.UNLIMITED) {
        for (update in channel) when(update) {
            Refresh -> updateList()
            is Filter -> doFilter(update.query)
            is MediaUpdate -> updateItems(update.mediaList as List<T>)
            is MediaAddition -> addMedia(update.mediaList as List<T>)
            is Remove -> removeMedia(update.media as T)
            is Sort -> {
                desc = if (sort == update.sort) !desc else false
                sort = update.sort
                updateList()
            }
        }
    }

    fun refresh() {
        updateActor.offer(Refresh)
    }

    fun sort(sort: Int) {
        updateActor.offer(Sort(sort))
    }

    fun remove(mw: T) {
        updateActor.offer(Remove(mw))
    }

    fun filter(query: String?) {
        updateActor.offer(Filter(query))
    }

    protected open fun removeMedia(media: T) {
        dataset.value?.let {
            dataset.postValue(it.apply { this.remove(media) })
        }
    }

    protected open fun addMedia(mediaList: List<T>) {
        val list = dataset.value ?: mutableListOf()
        if (list.isEmpty()) dataset.postValue(mediaList.toMutableList())
        else dataset.postValue(list.apply { this.addAll(mediaList) })
    }

    protected open fun updateItems(mediaList: List<T>) {
        val list = dataset.value?.toMutableList() ?: mutableListOf()
        val iterator = list.listIterator()
        for (media in iterator) {
            for (newItem in mediaList) if (media.equals(newItem)) {
                iterator.set(newItem)
                break
            }
        }
        dataset.postValue(list)
    }

    protected open fun updateList() {}

    protected abstract fun fetch()

    private fun doFilter(query: String?) = filter.filter(query)
}

sealed class Update
object Refresh : Update()
data class MediaUpdate(val mediaList: List<MediaLibraryItem>) : Update()
data class MediaAddition(val mediaList: List<MediaLibraryItem>) : Update()
data class Sort(val sort: Int) : Update()
data class Remove(val media: MediaLibraryItem) : Update()
data class Filter(val query: String?) : Update()