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
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.FilterDelegate
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.ModelsHelper

private const val TAG = "VLC/BaseModel"
abstract class BaseModel<T : MediaLibraryItem>(context: Context) : SortableModel(context) {

    private val filter by lazy(LazyThreadSafetyMode.NONE) { FilterDelegate(dataset) }

    val dataset by lazy {
        fetch()
        LiveDataset<T>()
    }

    val categories by lazy(LazyThreadSafetyMode.NONE) {
        MediatorLiveData<Map<String, List<MediaLibraryItem>>>().apply {
            addSource(dataset) {
                launch { value = withContext(Dispatchers.Default) { ModelsHelper.splitList(sort, it!!.toList()) } }
            }
        }
    }

    val sections by lazy(LazyThreadSafetyMode.NONE) {
        MediatorLiveData<List<MediaLibraryItem>>().apply {
            addSource(dataset) {
                launch { value = withContext(Dispatchers.Default) { ModelsHelper.generateSections(sort, it!!.toList()) } }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected val updateActor by lazy {
        actor<Update>(capacity = Channel.UNLIMITED) {
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

    override fun refresh() : Boolean {
        if (!updateActor.isClosedForSend) updateActor.offer(Refresh)
        return true
    }

    fun remove(mw: T) {
        if (!updateActor.isClosedForSend) updateActor.offer(Remove(mw))
    }

    override fun filter(query: String?) {
        if (!updateActor.isClosedForSend) {
            filterQuery = query
            updateActor.offer(Filter(query))
        }
    }

    override fun restore() {
        if (filterQuery !== null ) filter(null)
    }

    protected open fun removeMedia(media: T) = dataset.remove(media)

    protected open suspend fun addMedia(media: T) = dataset.add(media)

    open suspend fun addMedia(mediaList: List<T>) = dataset.add(mediaList)

    protected open suspend fun updateItems(mediaList: List<T>) {
        dataset.value = withContext(Dispatchers.Default) {
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

    protected abstract fun fetch()

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
