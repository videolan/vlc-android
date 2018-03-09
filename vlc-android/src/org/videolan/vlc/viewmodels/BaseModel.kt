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
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.FilterDelegate

abstract class BaseModel<T : MediaLibraryItem> : ViewModel() {

    var sort = Medialibrary.SORT_ALPHA
    var desc = false

    open fun canSortByName() = true
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
        launch(UI) { fetch() }
        MutableLiveData<MutableList<T>>()
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
                is Sort -> {
                    if (canSortBy(update.sort)) {
                        desc = when (sort) {
                            Medialibrary.SORT_DEFAULT -> update.sort == Medialibrary.SORT_ALPHA
                            update.sort -> !desc
                            else -> false
                        }
                        sort = update.sort
                        updateList()
                    }
                }
            }
        }
    }

    open fun refresh() = updateActor.offer(Refresh)

    open fun sort(sort: Int) = updateActor.offer(Sort(sort))

    fun remove(mw: T) = updateActor.offer(Remove(mw))

    fun filter(query: String?) = updateActor.offer(Filter(query))

    protected open fun removeMedia(media: T) {
        dataset.value?.let {
            dataset.value = it.apply { this.remove(media) }
        }
    }

    protected open suspend fun addMedia(media: T) {
        dataset.value.let {
            dataset.value = if (it === null) mutableListOf(media) else it.apply { add(media) }
        }
    }

    open suspend fun addMedia(mediaList: List<T>) {
        dataset.value.let {
            dataset.value = if (it === null) mediaList.toMutableList() else it.apply { addAll(mediaList) }
        }
    }

    protected open suspend fun updateItems(mediaList: List<T>) {
        dataset.value = withContext(CommonPool) {
            val list = dataset.value ?: mutableListOf()
            val iterator = list.listIterator()
            for (media in iterator) {
                for (newItem in mediaList) if (media.equals(newItem)) {
                    iterator.set(newItem)
                    break
                }
            }
            list
        }
    }

    fun canSortBy(sort: Int) = when(sort) {
        Medialibrary.SORT_DEFAULT -> true
        Medialibrary.SORT_ALPHA -> canSortByName()
        Medialibrary.SORT_DURATION -> canSortByDuration()
        Medialibrary.SORT_INSERTIONDATE -> canSortByInsertionDate()
        Medialibrary.SORT_LASTMODIFICATIONDATE -> canSortByLastModified()
        Medialibrary.SORT_RELEASEDATE -> canSortByReleaseDate()
        Medialibrary.SORT_FILESIZE -> canSortByFileSize()
        Medialibrary.SORT_ARTIST -> canSortByArtist()
        Medialibrary.SORT_ALBUM -> canSortByAlbum()
        Medialibrary.SORT_PLAYCOUNT -> canSortByPlayCount()
        else -> false
    }

    protected open suspend fun updateList() {}

    protected abstract fun fetch()
}

sealed class Update
object Refresh : Update()
data class MediaUpdate(val mediaList: List<MediaLibraryItem>) : Update()
data class MediaListAddition(val mediaList: List<MediaLibraryItem>) : Update()
data class MediaAddition(val media: MediaLibraryItem) : Update()
data class Sort(val sort: Int) : Update()
data class Remove(val media: MediaLibraryItem) : Update()
data class Filter(val query: String?) : Update()
