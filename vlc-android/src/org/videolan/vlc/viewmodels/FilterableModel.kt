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
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.videolan.medialibrary.media.MediaLibraryItem

abstract class FilterableModel<T : MediaLibraryItem> : ViewModel() {

    val dataset by lazy {
        fetch()
        MutableLiveData<MutableList<T>>()
    }
    private var originalData : MutableList<T>? = null

    protected abstract fun fetch()

    private fun initData() : MutableList<T>? {
        if (originalData === null) originalData = (dataset.value)
        return originalData
    }

    fun filter(charSequence: CharSequence?) = launch(UI, CoroutineStart.UNDISPATCHED) {
        publish(filteringJob(charSequence).await())
    }


    private fun filteringJob(charSequence: CharSequence?) = async {
        if (charSequence !== null) initData()?.let {
            val list = mutableListOf<T>()
            val queryStrings = charSequence.trim().toString().split(" ").filter { it.length > 2 }
            for (item in it) {
                for (query in queryStrings)
                    if (item.title.contains(query, true)) {
                        list.add(item)
                        break
                    }
            }
            return@async list
        }
        return@async mutableListOf<T>()
    }

    private fun publish(list: MutableList<T>?) {
        originalData?.let {
            if (list?.isEmpty() == false)
                dataset.value = list
            else {
                dataset.value = it
                originalData = null
            }
        }
    }
}