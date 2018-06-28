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

import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.*

private const val TAG = "VLC/BaseModel"
abstract class BaseModel<T : MediaLibraryItem> : ViewModel(), RefreshModel {

    var sort = Medialibrary.SORT_ALPHA
    var desc = false
    private var filtering = false
    protected open val sortKey = this.javaClass.simpleName!!

    open fun canSortByName() = true
    open fun canSortByFileNameName() = false
    open fun canSortByDuration() = false
    open fun canSortByInsertionDate() = false
    open fun canSortByLastModified() = false
    open fun canSortByReleaseDate() = false
    open fun canSortByFileSize() = false
    open fun canSortByArtist() = false
    open fun canSortByAlbum ()= false
    open fun canSortByPlayCount() = false

    private val filter by lazy(LazyThreadSafetyMode.NONE) { FilterDelegate(dataset) }

    val dataset by lazy {
        fetch()
        LiveDataset<T>()
    }

    val categories by lazy(LazyThreadSafetyMode.NONE) {
        MediatorLiveData<Map<String, List<MediaLibraryItem>>>().apply {
            addSource(dataset) {
                uiJob(false) { value = withContext(CommonPool) { ModelsHelper.splitList(sort, it!!.toList()) } }
            }
        }
    }

    val sections by lazy(LazyThreadSafetyMode.NONE) {
        MediatorLiveData<List<MediaLibraryItem>>().apply {
            addSource(dataset) {
                uiJob(false) { value = withContext(CommonPool) { ModelsHelper.generateSections(sort, it!!.toList()) } }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected val updateActor by lazy {
        actor<Update>(UI, capacity = Channel.UNLIMITED) {
            for (update in channel) when (update) {
                Refresh -> updateList()
                is Filter -> filter.filter(update.query)
                is MediaUpdate -> updateItems(update.mediaList as List<T>)
                is MediaAddition -> addMedia(update.media as T)
                is MediaListAddition -> addMedia(update.mediaList as List<T>)
                is Remove -> removeMedia(update.media as T)
            }
        }
    }

    override fun refresh() = updateActor.offer(Refresh)

    open fun sort(sort: Int) {
        if (canSortBy(sort)) {
            desc = when (this.sort) {
                Medialibrary.SORT_DEFAULT -> sort == Medialibrary.SORT_ALPHA
                sort -> !desc
                else -> false
            }
            this.sort = sort
            refresh()
        }
    }

    fun remove(mw: T) = updateActor.offer(Remove(mw))

    fun filter(query: String?) {
        filtering = true
        updateActor.offer(Filter(query))
    }

    fun restore() {
        if (filtering) updateActor.offer(Filter(null))
        filtering = false
    }

    protected open fun removeMedia(media: T) = dataset.remove(media)

    protected open suspend fun addMedia(media: T) = dataset.add(media)

    open suspend fun addMedia(mediaList: List<T>) = dataset.add(mediaList)

    protected open suspend fun updateItems(mediaList: List<T>) {
        dataset.value = withContext(CommonPool) {
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

    fun getKey() : String {
        return sortKey
    }
}

sealed class Update
object Refresh : Update()
class MediaUpdate(val mediaList: List<MediaLibraryItem>) : Update()
class MediaListAddition(val mediaList: List<MediaLibraryItem>) : Update()
class MediaAddition(val media: MediaLibraryItem) : Update()
class Remove(val media: MediaLibraryItem) : Update()
class Filter(val query: String?) : Update()
