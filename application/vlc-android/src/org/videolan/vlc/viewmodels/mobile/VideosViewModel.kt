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
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.GROUP_VIDEOS_FOLDER
import org.videolan.resources.GROUP_VIDEOS_NAME
import org.videolan.resources.GROUP_VIDEOS_NONE
import org.videolan.tools.KEY_GROUP_VIDEOS
import org.videolan.tools.Settings
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.getAll
import org.videolan.vlc.providers.medialibrary.FoldersProvider
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.providers.medialibrary.VideoGroupsProvider
import org.videolan.vlc.providers.medialibrary.VideosProvider
import org.videolan.vlc.viewmodels.MedialibraryViewModel

class VideosViewModel(context: Context, type: VideoGroupingType, val folder: Folder?, val group: VideoGroup?) : MedialibraryViewModel(context) {

    var groupingType = type
        private set
    var provider = loadProvider()
        private set

    private fun loadProvider(): MedialibraryProvider<out MediaLibraryItem> = when (groupingType) {
        VideoGroupingType.NONE -> VideosProvider(folder, group, context, this)
        VideoGroupingType.FOLDER -> FoldersProvider(context, this, Folder.TYPE_FOLDER_VIDEO)
        VideoGroupingType.NAME -> VideoGroupsProvider(context, this)
    }

    override val providers: Array<MedialibraryProvider<out MediaLibraryItem>> = arrayOf(provider)

    fun changeGroupingType(type: VideoGroupingType) {
        if (groupingType == type) return
        if (BuildConfig.DEBUG) Log.d("Grouping", "Changed grouping type to $type")
        groupingType = type
        provider = loadProvider()
        providers[0] = provider
        refresh()
    }

    init {
        watchMedia()
        watchMediaGroups()
        watchFolders()
    }

    class Factory(val context: Context, private val groupingType: VideoGroupingType, val folder: Folder? = null, val group: VideoGroup? = null) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VideosViewModel(context.applicationContext, groupingType, folder, group) as T
        }
    }
    companion object {


        // Define a custom key for your dependency
        val PARENT_GROUP_KEY = object : CreationExtras.Key<VideoGroup?> {}
        val PARENT_FOLDER_KEY = object : CreationExtras.Key<Folder?> {}

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Get the dependency in your factory
                val application = checkNotNull(this[APPLICATION_KEY])
                val parentGroup = this[PARENT_GROUP_KEY]
                val parentFolder = this[PARENT_FOLDER_KEY]

                val grouping = if (parentGroup != null || parentFolder != null) VideoGroupingType.NONE else when (Settings.getInstance(application).getString(KEY_GROUP_VIDEOS, null) ?: GROUP_VIDEOS_NAME) {
                    GROUP_VIDEOS_NONE -> VideoGroupingType.NONE
                    GROUP_VIDEOS_FOLDER -> VideoGroupingType.FOLDER
                    else -> VideoGroupingType.NAME
                }

                VideosViewModel(
                    application,
                    grouping,
                    parentFolder,
                    parentGroup
//                    savedStateHandle
                )
            }
        }
    }

    // Folders & Groups
    internal fun play(position: Int) = viewModelScope.launch {
        val item = provider.pagedList.value?.get(position) ?: return@launch
        withContext(Dispatchers.IO) {
            when (item) {
                is Folder -> item.getAll()
                is VideoGroup -> item.getAll()
                is MediaWrapper -> listOf(item)
                else -> null
            }
        }?.let { MediaUtils.openList(context, it, 0) }
    }

    internal fun append(position: Int) = viewModelScope.launch {
        val item = provider.pagedList.value?.get(position) ?: return@launch
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

    internal fun addItemToPlaylist(activity: FragmentActivity, position: Int) = viewModelScope.launch {
        val item = provider.pagedList.value?.get(position) ?: return@launch
        withContext(Dispatchers.IO) {
            when (item) {
                is Folder -> item.getAll()
                is VideoGroup -> item.getAll()
                else -> null
            }
        }?.let { if (activity.isStarted()) activity.addToPlaylist(it) }
    }

    internal fun appendFoldersSelection(selection: List<Folder>) = viewModelScope.launch {
        val list = selection.flatMap { it.getAll() }
        MediaUtils.appendMedia(context, list)
    }

    internal fun playVideo(context: FragmentActivity?, mw: MediaWrapper, position: Int, fromStart: Boolean = false, forceAll:Boolean = false, forceAudio: Boolean = false) {
        if (context === null) return
        if (!mw.isPresent) {
            UiTools.snackerMissing(context)
            return
        }
        if (forceAudio) {
            mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            PlaylistManager.playingAsAudio = true
        } else {
            mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            PlaylistManager.playingAsAudio = false
        }
        if (forceAll) {
            when(val prov = provider) {
                is VideosProvider -> MediaUtils.playAll(context, prov, position, false, forceAudio)
                is FoldersProvider -> MediaUtils.playAllTracks(context, prov, position, false, forceAudio)
                is VideoGroupsProvider -> MediaUtils.playAllTracks(context, prov, mw, false, forceAudio)
            }
        } else {
            if (fromStart) mw.addFlags(MediaWrapper.MEDIA_FROM_START)
            MediaUtils.openMedia(context, mw)
        }
    }

    internal fun playAll(activity: FragmentActivity?, position: Int = 0) {
        if (activity?.isStarted() == true) when (groupingType) {
            VideoGroupingType.NONE -> MediaUtils.playAll(activity, provider as VideosProvider, position, false)
            VideoGroupingType.FOLDER -> MediaUtils.playAllTracks(activity, (provider as FoldersProvider), position, false)
            VideoGroupingType.NAME -> MediaUtils.playAllTracks(activity, (provider as VideoGroupsProvider), null, false)
        }
    }

    internal fun playAudio(activity: FragmentActivity?, media: MediaWrapper) {
        if (activity == null) return
        media.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        PlaylistManager.playingAsAudio = true
        MediaUtils.openMedia(activity, media)
    }

    fun renameGroup(videoGroup: VideoGroup, newName: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            videoGroup.rename(newName)
        }
    }

    fun removeFromGroup(medias: List<MediaWrapper>) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            medias.forEach { media ->
                group?.remove(media.id)
            }
        }
    }

    fun removeFromGroup(media: MediaWrapper) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            group?.remove(media.id)
        }
    }

    fun ungroup(groups: List<MediaLibraryItem>) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            groups.forEach { group ->
                if (group is VideoGroup) group.destroy()
            }
        }
    }

    fun ungroup(group: VideoGroup) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            group.destroy()
        }
    }

    suspend fun createGroup(medias: List<MediaWrapper>): VideoGroup? {
        if (medias.size < 2) return null
        return withContext(Dispatchers.IO) {
            val newGroup = medialibrary.createVideoGroup(medias.map { it.id }.toLongArray())
            if (newGroup.title.isNullOrBlank()) {
                newGroup.rename(medias[0].title)
                newGroup.title = medias[0].title
            }
            newGroup
        }
    }

    suspend fun groupSimilar(media: MediaWrapper) = withContext(Dispatchers.IO) {
        medialibrary.regroup(media.id)
    }

    suspend fun markAsPlayed(media: MediaLibraryItem) = withContext(Dispatchers.IO) {
        when (media) {
            is VideoGroup -> media.getAll().forEach {
                if (it.seen == 0L) it.playCount = 1L
            }
            is Folder -> media.getAll().forEach {
                if (it.seen == 0L) it.playCount = 1L
            }
            is MediaWrapper -> if (media.seen == 0L) media.playCount = 1L
            else -> {}
        }
    }
    suspend fun markAsUnplayed(media: MediaLibraryItem) = withContext(Dispatchers.IO) {
        when (media) {
            is VideoGroup -> media.getAll().forEach {
                it.playCount = 0L
            }
            is Folder -> media.getAll().forEach {
                it.playCount = 0L
            }
            is MediaWrapper -> media.setPlayCount(0L)
            else -> {}
        }
    }
}

enum class VideoGroupingType(val settingsKey: String) {
    NONE(GROUP_VIDEOS_NONE), FOLDER(GROUP_VIDEOS_FOLDER), NAME(GROUP_VIDEOS_NAME)
}

internal fun VideoGridFragment.getViewModel(type: VideoGroupingType = VideoGroupingType.NONE, folder: Folder?, group: VideoGroup?) = ViewModelProvider(requireActivity(), VideosViewModel.Factory(requireContext(), type, folder, group))[VideosViewModel::class.java]

