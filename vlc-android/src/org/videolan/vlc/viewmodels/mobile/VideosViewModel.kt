/*****************************************************************************
 * VideosViewModel.kt
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

package org.videolan.vlc.viewmodels.mobile

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.isStarted
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.getAll
import org.videolan.vlc.providers.medialibrary.FoldersProvider
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.VideoGroupsProvider
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.viewmodels.MedialibraryViewModel

@ExperimentalCoroutinesApi
class VideosViewModel(context: Context, type: VideoGroupingType, val folder: AbstractFolder?, val group: AbstractVideoGroup?) : MedialibraryViewModel(context) {

    var groupingType = type
        private set
    var provider = loadProvider()
        private set

    private fun loadProvider(): MedialibraryProvider<out MediaLibraryItem> =  when (groupingType) {
        VideoGroupingType.NONE -> VideosProvider(folder, group, context, this)
        VideoGroupingType.FOLDER -> FoldersProvider(context, this, AbstractFolder.TYPE_FOLDER_VIDEO)
        VideoGroupingType.NAME -> VideoGroupsProvider(context, this)
    }

    override val providers: Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(provider)

    fun changeGroupingType(type: VideoGroupingType) {
        if (groupingType == type) return
        groupingType = type
        provider = loadProvider()
        providers[0] = provider
        refresh()
    }

    init {
        watchMedia()
    }

    class Factory(val context: Context, private val groupingType: VideoGroupingType, val folder: AbstractFolder? = null, val group: AbstractVideoGroup? = null) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VideosViewModel(context.applicationContext, groupingType, folder, group) as T
        }
    }

    // Folders & Groups
    suspend fun play(position: Int) {
        val item = provider.pagedList.value?.get(position) ?: return
        withContext(Dispatchers.IO) {
            when (item) {
                is AbstractFolder -> item.getAll()
                is AbstractVideoGroup -> item.getAll()
                else -> null
            }
        }?.let { MediaUtils.openList(context, it, 0) }
    }

    suspend fun append(position: Int) {
        val item = provider.pagedList.value?.get(position) ?: return
        withContext(Dispatchers.IO) {
            when (item) {
                is AbstractFolder -> item.getAll()
                is AbstractVideoGroup -> item.getAll()
                else -> null
            }
        }?.let { MediaUtils.appendMedia(context, it) }
    }

    fun playFoldersSelection(selection: List<AbstractFolder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.openList(context, list, 0)
    }

    fun playGroupsSelection(selection: List<AbstractVideoGroup>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.openList(context, list, 0)
    }

    fun addToPlaylist(activity: FragmentActivity, position: Int) = launch {
        val item = provider.pagedList.value?.get(position) ?: return@launch
        withContext(Dispatchers.IO) {
            when (item) {
                is AbstractFolder -> item.getAll()
                is AbstractVideoGroup -> item.getAll()
                else -> null
            }
        }?.let {if (activity.isStarted()) UiTools.addToPlaylist(activity, it) }
    }

    fun appendFoldersSelection(selection: List<AbstractFolder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }

    fun appendGroupsSelection(selection: List<AbstractVideoGroup>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }
}

enum class VideoGroupingType {
    NONE, FOLDER, NAME
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal fun VideoGridFragment.getViewModel(type: VideoGroupingType = VideoGroupingType.NONE , folder: AbstractFolder?, group: AbstractVideoGroup?) = ViewModelProviders.of(requireActivity(), VideosViewModel.Factory(requireContext(), type, folder, group)).get(VideosViewModel::class.java)

