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

import android.app.Activity
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.FORCE_PLAY_ALL
import org.videolan.tools.Settings
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

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VideosViewModel(context: Context, type: VideoGroupingType, val folder: Folder?, val group: VideoGroup?) : MedialibraryViewModel(context) {

    var groupingType = type
        private set
    var provider = loadProvider()
        private set

    private fun loadProvider(): MedialibraryProvider<out MediaLibraryItem> =  when (groupingType) {
        VideoGroupingType.NONE -> VideosProvider(folder, group, context, this)
        VideoGroupingType.FOLDER -> FoldersProvider(context, this, Folder.TYPE_FOLDER_VIDEO)
        VideoGroupingType.NAME -> VideoGroupsProvider(context, this)
    }

    override val providers: Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(provider)

    internal fun changeGroupingType(type: VideoGroupingType) {
        if (groupingType == type) return
        groupingType = type
        provider = loadProvider()
        providers[0] = provider
        refresh()
    }

    init {
        watchMedia()
    }

    class Factory(val context: Context, private val groupingType: VideoGroupingType, val folder: Folder? = null, val group: VideoGroup? = null) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VideosViewModel(context.applicationContext, groupingType, folder, group) as T
        }
    }

    // Folders & Groups
    internal suspend fun play(position: Int) {
        val item = provider.pagedList.value?.get(position) ?: return
        withContext(Dispatchers.IO) {
            when (item) {
                is Folder -> item.getAll()
                is VideoGroup -> item.getAll()
                else -> null
            }
        }?.let { MediaUtils.openList(context, it, 0) }
    }

    internal suspend fun append(position: Int) {
        val item = provider.pagedList.value?.get(position) ?: return
        withContext(Dispatchers.IO) {
            when (item) {
                is Folder -> item.getAll()
                is VideoGroup -> item.getAll()
                else -> null
            }
        }?.let { MediaUtils.appendMedia(context, it) }
    }

    internal fun playFoldersSelection(selection: List<Folder>) = viewModelScope.launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.openList(context, list, 0)
    }

    internal fun addToPlaylist(activity: FragmentActivity, position: Int) = viewModelScope.launch {
        val item = provider.pagedList.value?.get(position) ?: return@launch
        withContext(Dispatchers.IO) {
            when (item) {
                is Folder -> item.getAll()
                is VideoGroup -> item.getAll()
                else -> null
            }
        }?.let {if (activity.isStarted()) UiTools.addToPlaylist(activity, it) }
    }

    internal fun appendFoldersSelection(selection: List<Folder>) = viewModelScope.launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }

    internal fun playVideo(context: Activity?, mw: MediaWrapper, position: Int, fromStart: Boolean = false) {
        if (context === null) return
        mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        val settings = Settings.getInstance(context)
        if (settings.getBoolean(FORCE_PLAY_ALL, false)) {
            MediaUtils.playAll(context, provider as VideosProvider, position, false)
        } else {
            if (fromStart) mw.addFlags(MediaWrapper.MEDIA_FROM_START)
            MediaUtils.openMedia(context, mw)
        }
    }

    internal fun playAll(activity: FragmentActivity?, position: Int = 0) {
        if (activity?.isStarted() == true) when (groupingType) {
            VideoGroupingType.NONE -> MediaUtils.playAll(activity, provider as VideosProvider, position, false)
            VideoGroupingType.FOLDER -> MediaUtils.playAllTracks(activity, (provider as FoldersProvider), position, false)
            VideoGroupingType.NAME -> MediaUtils.playAllTracks(activity, (provider as VideoGroupsProvider), position, false)
        }
    }

    internal fun playAudio(activity: FragmentActivity?, media: MediaWrapper) {
        if (activity == null) return
        media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        MediaUtils.openMedia(activity, media)
    }
}

enum class VideoGroupingType {
    NONE, FOLDER, NAME
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal fun VideoGridFragment.getViewModel(type: VideoGroupingType = VideoGroupingType.NONE, folder: Folder?, group: VideoGroup?) = ViewModelProviders.of(requireActivity(), VideosViewModel.Factory(requireContext(), type, folder, group)).get(VideosViewModel::class.java)

