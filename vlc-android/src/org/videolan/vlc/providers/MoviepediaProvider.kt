/*
 * ************************************************************************
 *  MoviepediaProvider.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.providers

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.PagedList
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.database.models.DisplayableMediaMetadata
import org.videolan.vlc.util.ModelsHelper
import org.videolan.vlc.util.Settings

abstract class MoviepediaProvider<T : DisplayableMediaMetadata>(private val context: Context) : HeaderProvider() {

    abstract var pagedList: LiveData<PagedList<T>>
    val loading = MutableLiveData<Boolean>().apply { value = true }
    private val settings = Settings.getInstance(context)
    protected open val sortKey: String = this.javaClass.simpleName
    var sort = settings.getInt(sortKey, AbstractMedialibrary.SORT_DEFAULT)
    var desc = settings.getBoolean("${sortKey}_desc", false)
    var sortQuery = MutableLiveData<Pair<Int, Boolean>>().apply { value = Pair(sort, desc) }
    var isRefreshing = false
        private set(value) {
            loading.postValue(value)
            field = value
        }

    fun sort(sort: Int) {
        desc = when (this.sort) {
            AbstractMedialibrary.SORT_DEFAULT -> sort == AbstractMedialibrary.SORT_ALPHA
            sort -> !desc
            else -> false
        }
        this.sort = sort
        sortQuery.value = Pair(sort, desc)
        settings.edit()
                .putInt(sortKey, sort)
                .putBoolean("${sortKey}_desc", desc)
                .apply()
    }

    fun refresh(): Boolean {
        if (isRefreshing || pagedList.value?.dataSource == null) return false
        privateHeaders.clear()
        if (!pagedList.value?.dataSource?.isInvalid!!) {
            isRefreshing = true
            pagedList.value?.dataSource?.invalidate()
        }
        return true
    }

    fun completeHeaders(list: Array<T?>, startposition: Int) {
        for ((position, item) in list.withIndex()) {
            val previous = when {
                position > 0 -> list[position - 1]
                startposition > 0 -> pagedList.value?.getOrNull(startposition + position - 1)
                else -> null
            }
            ModelsHelper.getHeaderMoviepedia(context, sort, item, previous)?.let {
                privateHeaders.put(startposition + position, it)
            }
        }
        (liveHeaders as MutableLiveData).postValue(privateHeaders.clone())
    }
}