/*****************************************************************************
 * HistoryModel.kt
 *****************************************************************************
 * Copyright © 2018-2019 VLC authors and VideoLAN
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper

class HistoryModel(context: Context) : MedialibraryModel<AbstractMediaWrapper>(context) {

    override fun canSortByName() = false

    override suspend fun updateList() {
        dataset.value = withContext(Dispatchers.Default) { medialibrary.lastMediaPlayed().toMutableList() }
    }

    fun moveUp(media: AbstractMediaWrapper) {
        dataset.value = dataset.value.apply {
            remove(media)
            add(0, media)
        }
    }

    fun clearHistory() {
        dataset.value = mutableListOf()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HistoryModel(context.applicationContext) as T
        }
    }
}