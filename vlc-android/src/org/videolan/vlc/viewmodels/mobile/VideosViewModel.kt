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
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.viewmodels.MedialibraryViewModel

@ExperimentalCoroutinesApi
class VideosViewModel(context: Context, val groupingType: VideoGroupingType, val folder: AbstractFolder?, val group: AbstractVideoGroup?) : MedialibraryViewModel(context) {

    val provider = when (groupingType) {
        VideoGroupingType.NONE -> VideosProvider(folder, group, context, this)
        VideoGroupingType.FOLDER -> FoldersProvider(context, this, AbstractFolder.TYPE_FOLDER_VIDEO)
    }
    override val providers: Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(provider)

    init {
        watchMedia()
    }

    class Factory(val context: Context, val groupingType: VideoGroupingType, val folder: AbstractFolder?, val group: AbstractVideoGroup?) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VideosViewModel(context.applicationContext, groupingType, folder, group) as T
        }
    }

    //Folders
    suspend fun play(position: Int) {
        val list = withContext(Dispatchers.IO) { (provider as FoldersProvider).pagedList.value?.get(position)?.getAll() }
        list?.let { MediaUtils.openList(context, it, 0) }
    }

    suspend fun append(position: Int) {
        val list = withContext(Dispatchers.IO) { (provider as FoldersProvider).pagedList.value?.get(position)?.getAll() }
        list?.let { MediaUtils.appendMedia(context, it) }
    }

    fun playSelection(selection: List<AbstractFolder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.openList(context, list, 0)
    }

    fun addToPlaylist(activity: FragmentActivity, position: Int) = launch {
        (provider as FoldersProvider).pagedList.value?.get(position)?.let {
            val list = withContext(Dispatchers.IO) { it.getAll() }
            if (activity.isStarted()) UiTools.addToPlaylist(activity, list)
        }
    }

    fun appendSelection(selection: List<AbstractFolder>) = launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }
}

enum class VideoGroupingType {
    NONE, FOLDER
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal fun VideoGridFragment.getViewModel(folder: AbstractFolder?, group: AbstractVideoGroup?) = ViewModelProviders.of(requireActivity(), VideosViewModel.Factory(requireContext(), VideoGroupingType.NONE, folder, group)).get(VideosViewModel::class.java)

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal fun VideoGridFragment.getFolderViewModel() = ViewModelProviders.of(requireActivity(), VideosViewModel.Factory(requireContext(), VideoGroupingType.FOLDER, null, null)).get(VideosViewModel::class.java)
