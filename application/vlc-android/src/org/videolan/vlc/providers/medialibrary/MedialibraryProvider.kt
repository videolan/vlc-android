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
import kotlinx.coroutines.CompletableDeferred
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.util.HeaderProvider
import org.videolan.tools.Settings
import org.videolan.vlc.util.ModelsHelper
import org.videolan.vlc.util.SortModule
import org.videolan.vlc.viewmodels.SortableModel

abstract class MedialibraryProvider<T : MediaLibraryItem>(val context: Context, val model: SortableModel) : HeaderProvider(),
        SortModule
{
    private val settings = Settings.getInstance(context)
    protected val medialibrary = Medialibrary.getInstance()
    private lateinit var dataSource : DataSource<Int, T>
    val loading = MutableLiveData<Boolean>().apply { value = true }
    private var refreshDeferred : CompletableDeferred<Unit>? = null
    var isRefreshing = true
        private set(value) {
            refreshDeferred = if (value) CompletableDeferred()
            else {
                refreshDeferred?.complete(Unit)
                null
            }
            loading.postValue(value || medialibrary.isWorking)
            field = value
        }

    protected open val sortKey : String = this.javaClass.simpleName
    var sort = settings.getInt(sortKey, Medialibrary.SORT_DEFAULT)
    var desc = settings.getBoolean("${sortKey}_desc", false)

    private val pagingConfig = Config(
            pageSize = MEDIALIBRARY_PAGE_SIZE,
            prefetchDistance = MEDIALIBRARY_PAGE_SIZE / 5,
            enablePlaceholders = true,
            initialLoadSizeHint = MEDIALIBRARY_PAGE_SIZE,
            maxSize = MEDIALIBRARY_PAGE_SIZE *2
    )

    val pagedList by lazy(LazyThreadSafetyMode.NONE) { MLDatasourceFactory().toLiveData(pagingConfig) }

    abstract fun getTotalCount(): Int
    abstract fun getPage(loadSize: Int, startposition: Int): Array<T>
    abstract fun getAll(): Array<out T>

    override fun sort(sort: Int) {
        if (canSortBy(sort)) {
            desc = when (this.sort) {
                Medialibrary.SORT_DEFAULT -> sort == Medialibrary.SORT_ALPHA
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

    suspend fun awaitRefresh() {
        refresh()
        refreshDeferred?.await()
    }

    fun refresh(): Boolean {
        if (medialibrary.isWorking || !medialibrary.isStarted || !this::dataSource.isInitialized) return false
        privateHeaders.clear()
        if (!dataSource.isInvalid) {
            isRefreshing = true
            dataSource.invalidate()
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
            ModelsHelper.getHeader(context, sort, item, previous)?.let {
                privateHeaders.put(startposition + position, it)
            }
        }
        (liveHeaders as MutableLiveData).postValue(privateHeaders.clone())
    }

    inner class MLDataSource : PositionalDataSource<T>() {

        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
            val page = getPage(params.requestedLoadSize, params.requestedStartPosition)
            val count = if (page.size < params.requestedLoadSize) page.size else getTotalCount()
            try {
                callback.onResult(page.toList(), params.requestedStartPosition, count)
            } catch (e: IllegalArgumentException) {}
            isRefreshing = !medialibrary.isStarted
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            val result = getPage(params.loadSize, params.startPosition).toList()
            callback.onResult(result)
        }
    }

    inner class MLDatasourceFactory : DataSource.Factory<Int, T>() {
        override fun create() = MLDataSource().also { dataSource = it }
    }
}