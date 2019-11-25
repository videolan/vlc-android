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
import androidx.lifecycle.MutableLiveData
import androidx.paging.Config
import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import androidx.paging.toLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.retry
import org.videolan.vlc.providers.HeaderProvider
import org.videolan.vlc.util.MEDIALIBRARY_PAGE_SIZE
import org.videolan.vlc.util.ModelsHelper
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.SortModule
import org.videolan.vlc.viewmodels.SortableModel

abstract class MedialibraryProvider<T : MediaLibraryItem>(val context: Context, val scope: SortableModel) : HeaderProvider(),
        SortModule
{
    private val settings = Settings.getInstance(context)
    protected val medialibrary = AbstractMedialibrary.getInstance()
    private lateinit var dataSource : DataSource<Int, T>
    val loading = MutableLiveData<Boolean>().apply { value = true }
    var isRefreshing = medialibrary.isWorking
        private set(value) {
            loading.postValue(value || medialibrary.isWorking)
            field = value
        }

    protected open val sortKey : String = this.javaClass.simpleName
    var sort = settings.getInt(sortKey, AbstractMedialibrary.SORT_DEFAULT)
    var desc = settings.getBoolean("${sortKey}_desc", false)

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
    open fun isByDisc(): Boolean = false

    override fun sort(sort: Int) {
        if (canSortBy(sort)) {
            desc = when (this.sort) {
                AbstractMedialibrary.SORT_DEFAULT -> sort == AbstractMedialibrary.SORT_ALPHA
                sort -> !desc
                else -> false
            }
            this.sort = sort
            refresh()
            settings.edit()
                    .putInt(sortKey, sort)
                    .putBoolean("${sortKey}_desc", desc)
                    .apply()
        }
    }

    fun refresh(): Boolean {
        if (isRefreshing || !medialibrary.isStarted || !this::dataSource.isInitialized) return false
        privateHeaders.clear()
        if (!dataSource.isInvalid) {
            isRefreshing = true
            dataSource.invalidate()
        }
        return true
    }

    fun isEmpty() = pagedList.value.isNullOrEmpty()

    fun completeHeaders(list: Array<T?>, startposition: Int) {
        for ((position, item) in list.withIndex()) {
            val previous = when {
                position > 0 -> list[position - 1]
                startposition > 0 -> pagedList.value?.getOrNull(startposition + position - 1)
                else -> null
            }
            ModelsHelper.getHeader(context, sort, item, previous, isByDisc())?.let {
                privateHeaders.put(startposition + position, it)
            }
        }
        (liveHeaders as MutableLiveData).postValue(privateHeaders.clone())
    }

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
                isRefreshing = false
            }
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            callback.onResult(getPage(params.loadSize, params.startPosition).toList())
        }
    }

    inner class MLDatasourceFactory : DataSource.Factory<Int, T>() {
        override fun create() = MLDataSource().also { dataSource = it }
    }
}