/*****************************************************************************
 * BaseModel.kt
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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.util.FilterDelegate
import org.videolan.vlc.util.ModelsHelper
import org.videolan.vlc.util.map

private const val TAG = "VLC/BaseModel"

abstract class BaseModel<T : MediaLibraryItem>(context: Context, val coroutineContextProvider: CoroutineContextProvider) : SortableModel(context) {

    private val filter by lazy(LazyThreadSafetyMode.NONE) { FilterDelegate(dataset) }

    val dataset = LiveDataset<T>()
    open val loading = MutableLiveData<Boolean>().apply { value = false }

    val categories by lazy(LazyThreadSafetyMode.NONE) {
        viewModelScope.map(dataset) { ModelsHelper.splitList(sort, it!!.toList()) }
    }

    val sections by lazy(LazyThreadSafetyMode.NONE) {
        viewModelScope.map(dataset) { ModelsHelper.generateSections(sort, it!!.toList()) }
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ObsoleteCoroutinesApi::class)
    protected val updateActor by lazy {
        viewModelScope.actor<Update>(capacity = Channel.UNLIMITED) {
            for (update in channel) if (isActive) when (update) {
                Refresh -> updateList()
                is Filter -> filter.filter(update.query)
                is MediaUpdate -> updateItems(update.mediaList as List<T>)
                is MediaAddition -> addMedia(update.media as T)
                is MediaListAddition -> addMedia(update.mediaList as List<T>)
                is Remove -> removeMedia(update.media as T)
            } else channel.close()
        }
    }

    fun isEmpty() = dataset.isEmpty()

    override fun refresh() {
        updateActor.trySend(Refresh)
    }

    fun remove(mw: T) {
        updateActor.trySend(Remove(mw))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun filter(query: String?) {
        if (!updateActor.isClosedForSend) {
            filterQuery = query
            updateActor.trySend(Filter(query))
        }
    }

    override fun restore() {
        if (filterQuery !== null ) filter(null)
    }

    protected open fun removeMedia(media: T) = dataset.remove(media)

    protected open suspend fun addMedia(media: T) = dataset.add(media)

    open suspend fun addMedia(mediaList: List<T>) = dataset.add(mediaList)

    protected open suspend fun updateItems(mediaList: List<T>) {
        dataset.value = withContext(coroutineContextProvider.Default) {
            val list = dataset.value
            val iterator = list.listIterator()
            while (iterator.hasNext()) {
                val media = iterator.next()
                for (newItem in mediaList) if (media.equals(newItem)) {
                    iterator.set(newItem)
                    break
                }
            }
            list
        }
    }

    protected open suspend fun updateList() {}

    override fun onCleared() {
        updateActor.close()
        super.onCleared()
    }
}

sealed class Update
object Refresh : Update()
class MediaUpdate(val mediaList: List<MediaLibraryItem>) : Update()
class MediaListAddition(val mediaList: List<MediaLibraryItem>) : Update()
class MediaAddition(val media: MediaLibraryItem) : Update()
class Remove(val media: MediaLibraryItem) : Update()
class Filter(val query: String?) : Update()
