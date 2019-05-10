/*****************************************************************************
 * MedialibraryProvider.kt
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
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.Config
import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import androidx.paging.toLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.util.MEDIALIBRARY_PAGE_SIZE
import org.videolan.vlc.util.ModelsHelper
import org.videolan.vlc.util.retry
import org.videolan.vlc.viewmodels.SortableModel
import org.videolan.vlc.viewmodels.paged.HeadersIndex


abstract class MedialibraryProvider<T : MediaLibraryItem>(val context: Context, val scope: SortableModel) {
    protected val medialibrary = Medialibrary.getInstance()
    val loading = MutableLiveData<Boolean>().apply { value = false }
    private val headers = HeadersIndex()
    val liveHeaders : LiveData<HeadersIndex> = MutableLiveData<HeadersIndex>()

    private val pagingConfig = Config(
            pageSize = MEDIALIBRARY_PAGE_SIZE,
            prefetchDistance = MEDIALIBRARY_PAGE_SIZE / 5,
            enablePlaceholders = true,
            initialLoadSizeHint = MEDIALIBRARY_PAGE_SIZE*3,
            maxSize = MEDIALIBRARY_PAGE_SIZE*3
    )

    val pagedList = MLDatasourceFactory().toLiveData(pagingConfig)

    abstract fun getTotalCount(): Int
    abstract fun getPage(loadSize: Int, startposition: Int): Array<T>
    abstract fun getAll(): Array<T>

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

    fun refresh(): Boolean {
        headers.clear()
        if (pagedList.value?.dataSource?.isInvalid == false) {
            loading.postValue(true)
            pagedList.value?.dataSource?.invalidate()
        }
        return true
    }

    fun isEmpty() = pagedList.value.isNullOrEmpty()

    fun completeHeaders(list: Array<T>, startposition: Int) {
        for ((position, item) in list.withIndex()) {
            val previous = when {
                position > 0 -> list[position - 1]
                startposition > 0 -> pagedList.value?.getOrNull(startposition + position - 1)
                else -> null
            }
            ModelsHelper.getHeader(context, scope.sort, item, previous)?.let {
                scope.launch {
                    headers.put(startposition + position, it)
                    (liveHeaders as MutableLiveData<HeadersIndex>).value = headers
                }
            }
        }
    }

    @MainThread
    fun getSectionforPosition(position: Int): String {
        for (pos in headers.size()-1 downTo 0) if (position >= headers.keyAt(pos)) return headers.valueAt(pos)
        return ""
    }


    @MainThread
    fun isFirstInSection(position: Int): Boolean {
        return headers.containsKey(position)
    }

    @MainThread
    fun getPositionForSection(position: Int): Int {
        for (pos in headers.size()-1 downTo 0) if (position >= headers.keyAt(pos)) return headers.keyAt(pos)
        return 0
    }

    @MainThread
    fun getPositionForSectionByName(header: String): Int {
        for (pos in headers.size() - 1 downTo 0) if (headers.valueAt(pos) == header) return headers.keyAt(pos)
        return 0
    }

    @MainThread
    fun getHeaderForPostion(position: Int) = headers.get(position)

    inner class MLDataSource : PositionalDataSource<T>() {

        @ExperimentalCoroutinesApi
        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
            scope.launch(Dispatchers.Unconfined) {
                retry(1) {
                    val page = getPage(params.requestedLoadSize, params.requestedStartPosition)
                    val count = if (page.size < params.requestedLoadSize) page.size else getTotalCount()
                    try {
                        callback.onResult(page.toList(), params.requestedStartPosition, count)
                        true
                    } catch (e: IllegalArgumentException) {
                        false
                    }
                }
                loading.postValue(false)
            }
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            callback.onResult(getPage(params.loadSize, params.startPosition).toList())
        }
    }

    inner class MLDatasourceFactory : DataSource.Factory<Int, T>() {
        override fun create() = MLDataSource()
    }
}