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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.Settings

class HistoryModel(context: Context, coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) : MedialibraryModel<MediaWrapper>(context, coroutineContextProvider) {

    override fun canSortByName() = false

    override suspend fun updateList() {
        if (!Settings.getInstance(context).getBoolean(PLAYBACK_HISTORY, true)) return
        dataset.value = withContext(coroutineContextProvider.Default) { medialibrary.history(
            Medialibrary.HISTORY_TYPE_LOCAL).toMutableList() }
    }

    fun moveUp(media: MediaWrapper) = dataset.move(media, 0)

    fun clearHistory() = dataset.clear()
    fun removeFromHistory(media: MediaWrapper) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                media.removeFromHistory()
            }
            refresh()
        }

    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HistoryModel(context.applicationContext) as T
        }
    }
}