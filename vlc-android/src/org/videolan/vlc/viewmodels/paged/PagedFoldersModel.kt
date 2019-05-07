/*****************************************************************************
 * PagedFoldersModel.kt
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

package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import org.videolan.medialibrary.media.Folder
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.getAll
import org.videolan.vlc.providers.medialibrary.FoldersProvider

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PagedFoldersModel(context: Context, val type: Int) : MLPagedModel<Folder>(context) {
    override val provider = FoldersProvider(context, this, type)

    init {
        if (medialibrary.isStarted) refresh()
    }

    suspend fun play(position: Int) {
        val list = withContext(Dispatchers.IO) { pagedList.value?.get(position)?.getAll()}
        list?.let { MediaUtils.openList(context, it, 0) }
    }

    suspend fun append(position: Int) {
        val list = withContext(Dispatchers.IO) { pagedList.value?.get(position)?.getAll()}
        list?.let { MediaUtils.appendMedia(context, it) }
    }

    fun playSelection(selection: List<Folder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.openList(context, list, 0)
    }

    fun appendSelection(selection: List<Folder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }

    class Factory(private val context: Context, val type: Int): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PagedFoldersModel(context.applicationContext, type) as T
        }
    }
}