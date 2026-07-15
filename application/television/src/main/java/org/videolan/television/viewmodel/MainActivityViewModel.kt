/*
 * ************************************************************************
 *  MainActivityViewModel.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.viewmodel

import android.app.Activity
import android.app.Application
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import android.content.Intent
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.ACTION_DISCOVER_DEVICE
import org.videolan.resources.EXTRA_PATH
import org.videolan.resources.util.launchForeground
import org.videolan.television.R
import org.videolan.television.ui.compose.MainDestination
import org.videolan.television.ui.compose.composable.components.BrowserItemCtxFlags
import org.videolan.tools.retrieveParent
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.ScanProgress
import org.videolan.vlc.gui.dialogs.ContextSheet
import org.videolan.vlc.gui.dialogs.CtxMenuItem
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.providers.NetworkProvider
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_ADD_FOLDER_AND_SUB_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_ADD_FOLDER_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_ADD_GROUP
import org.videolan.vlc.util.ContextOption.CTX_ADD_SCANNED
import org.videolan.vlc.util.ContextOption.CTX_ADD_SHORTCUT
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_COPY
import org.videolan.vlc.util.ContextOption.CTX_DELETE
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_ALBUM_ARTIST
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_GROUP_SIMILAR
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_PLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_UNPLAYED
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.ContextOption.CTX_PLAY_AS_AUDIO
import org.videolan.vlc.util.ContextOption.CTX_PLAY_FROM_START
import org.videolan.vlc.util.ContextOption.CTX_PLAY_SHUFFLE
import org.videolan.vlc.util.ContextOption.CTX_REMOVE_GROUP
import org.videolan.vlc.util.ContextOption.CTX_RENAME
import org.videolan.vlc.util.ContextOption.Companion.createCtxAudioFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxFolderFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxHistoryFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxPlaylistAlbumFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxTrackFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxVideoFlags
import org.videolan.vlc.util.ContextOption.Companion.createCtxVideoGroupFlags
import org.videolan.vlc.util.FlagSet
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.isSchemeHttpOrHttps
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {

    val tabs = listOf(
//        Pair(R.string.search, R.drawable.ic_search),
        Pair(R.string.video, R.drawable.ic_video),
        Pair(R.string.audio, R.drawable.ic_menu_audio),
        Pair(R.string.browse, R.drawable.ic_folder),
        Pair(R.string.playlists, R.drawable.ic_playlist),
        Pair(R.string.more, R.drawable.ic_nav_more),
    )

    val audioTabs = listOf(
        R.string.artists,
        R.string.albums,
        R.string.tracks,
        R.string.genres,
        R.string.playlists,
    )

    val videoTabs = listOf(
        R.string.video,
        R.string.playlists,
    )


    private val _currentMediaListEntry: MutableStateFlow<MediaListEntry?> = MutableStateFlow(null)
    val currentMediaListEntry: StateFlow<MediaListEntry?> = _currentMediaListEntry.asStateFlow()

    private val _snackBarFlow: MutableStateFlow<SnackbarContent?> = MutableStateFlow(null)
    val snackBarFlow: StateFlow<SnackbarContent?> = _snackBarFlow.asStateFlow()

    private val _currentDisplaySettingsChange: MutableStateFlow<DisplaySettingsChange?> = MutableStateFlow(null)
    val currentDisplaySettingsChange: StateFlow<DisplaySettingsChange?> = _currentDisplaySettingsChange.asStateFlow()

    private val _invalidateMediaListEntry: MutableStateFlow<MediaListEntry?> = MutableStateFlow(null)
    val invalidateMediaListEntry: StateFlow<MediaListEntry?> = _invalidateMediaListEntry.asStateFlow()

    private val _showTabs: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val showTabs: StateFlow<Boolean> = _showTabs.asStateFlow()

    private val _navigationFlow = MutableSharedFlow<MainDestination>(extraBufferCapacity = 1)
    val navigationFlow: SharedFlow<MainDestination> = _navigationFlow.asSharedFlow()

    fun navigateTo(destination: MainDestination) = viewModelScope.launch {
        _navigationFlow.emit(destination)
    }

    fun setShowTabs(show: Boolean) = viewModelScope.launch {
        _showTabs.emit(show)
    }

    private val _newStorageDetected = MutableStateFlow<String?>(null)
    val newStorageDetected = _newStorageDetected.asStateFlow()

    private val ctxClickListeners = mutableMapOf<MediaListEntry, (MediaLibraryItem, Int, CtxMenuItem) -> Unit>()

    fun addCtxClickListener(mediaListEntry: MediaListEntry, listener: (MediaLibraryItem, Int, CtxMenuItem) -> Unit) {
        ctxClickListeners[mediaListEntry] = listener
    }

    fun changeCurrentMediaListEntry(entry: MediaListEntry?) = viewModelScope.launch {
        _currentMediaListEntry.emit(entry)
    }

    private val tabsInfo = mutableMapOf<String, TabInfo>()


    val editAudioQueue = MutableStateFlow(false)
    fun toggleEditAudioQueue() {
        editAudioQueue.value = !editAudioQueue.value
    }

    val progress = MutableLiveData<ScanProgress?>(null)
    private var progressJob: Job = viewModelScope.launch {
        MediaParsingService.progress.asFlow().collect {
            if (Medialibrary.getState().value == false) {
                progress.value = null
                return@collect
            }
            progress.value = it
        }
    }

    private var workingJob: Job = viewModelScope.launch {
        Medialibrary.getState().asFlow().collect {
            if (!it) progress.value = null
        }
    }

    private var storageJob: Job = viewModelScope.launch {
        MediaParsingService.newStorages.asFlow().collect { devices ->
            if (devices.isNullOrEmpty()) return@collect
            _newStorageDetected.value = devices.first()
            MediaParsingService.newStorages.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob.cancel()
        workingJob.cancel()
        storageJob.cancel()
    }

    fun getTabInfo(key: String): TabInfo? {
        return tabsInfo[key]
    }

    fun setTabInfo(key: String, info: TabInfo) {
        tabsInfo[key] = info
    }

    /**
     * Open the display settings UI for the given media list entry
     *
     * @param mediaListEntry The media list entry to open the settings for
     */
    fun openDisplaySettings(mediaListEntry: MediaListEntry) = viewModelScope.launch {
        _currentMediaListEntry.emit(mediaListEntry)
    }

    /**
     * Change display settings. Used for display settings not affecting the viewmodel
     *
     * @param current The current media list entry
     */
    fun changeDisplaySettings(current: MediaListEntry) = viewModelScope.launch {
        val newIndex = if (_currentDisplaySettingsChange.value?.entry == current) _currentDisplaySettingsChange.value!!.value + 1 else 0
        _currentDisplaySettingsChange.emit(DisplaySettingsChange(current, newIndex))
    }

    fun hideDisplaySettings() = viewModelScope.launch {
        _currentMediaListEntry.emit(null)
    }

    fun showSnackbar(content: SnackbarContent?) = viewModelScope.launch {
        _snackBarFlow.emit(content)
    }

    fun acceptStorage(path: String) {
        val context = getApplication<Application>()
        val intent = Intent(ACTION_DISCOVER_DEVICE, null, context, MediaParsingService::class.java)
                .putExtra(EXTRA_PATH, path)
        context.launchForeground(intent)
        _newStorageDetected.value = null
    }

    fun declineStorage() {
        _newStorageDetected.value = null
    }

    fun onCtxClick(entry: MediaListEntry, item: MediaLibraryItem, position: Int, it: CtxMenuItem) {
        ctxClickListeners[entry]?.invoke(item, position, it)
    }

    fun getFlags(activity: Activity, entry: MediaListEntry, item: MediaLibraryItem): List<CtxMenuItem>? {
        val flags: FlagSet<ContextOption> = if (entry == MediaListEntry.HISTORY) {
            createCtxHistoryFlags()
        } else when (item.itemType) {
            MediaLibraryItem.TYPE_MEDIA -> {
                when (item) {
                    is MediaWrapper if item.type == MediaWrapper.TYPE_DIR -> {
                        FlagSet(ContextOption::class.java).apply {
                            if (item.hasFlag(BrowserItemCtxFlags.isFolderEmpty)) add(CTX_PLAY)
                            val isFileBrowser = entry.providerClass != NetworkProvider::class.java && item.uri.scheme == "file"
                            if (!entry.isRoot && isFileBrowser) add(ContextOption.CTX_BAN_FOLDER)
                            if (isFileBrowser && !entry.isRoot && !MedialibraryUtils.isScanned(item.uri.toString())) {
                                add(CTX_ADD_SCANNED)
                            }
                            if (isFileBrowser) {
                                add(CTX_APPEND)
                                if (item.hasFlag(BrowserItemCtxFlags.hasMedias)) add(CTX_ADD_FOLDER_PLAYLIST)
                                if (item.hasFlag(BrowserItemCtxFlags.hasSubfolders)) add(CTX_ADD_FOLDER_AND_SUB_PLAYLIST)
                            }
                        }
                    }

                    is Folder -> {
                        createCtxFolderFlags().apply {
                            if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                        }
                    }

                    is VideoGroup -> {
                        if (item.presentCount == 0) {
                            showSnackbar(SnackbarContent(activity.resources.getString(R.string.missing_media_snack)))
                            return null
                        } else {
                            createCtxVideoGroupFlags().apply {
                                if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                            }
                        }
                    }

                    is MediaWrapper if item.type == MediaWrapper.TYPE_VIDEO -> {
                        createCtxVideoFlags().apply {
                            if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                            if (item.seen > 0) add(CTX_MARK_AS_UNPLAYED) else add(CTX_MARK_AS_PLAYED)
                            if (item.time != 0L) add(CTX_PLAY_FROM_START)
                            if (entry == MediaListEntry.VIDEO_GROUPS || entry.isGroup) {
                                if (entry.isGroup) add(CTX_REMOVE_GROUP) else addAll(CTX_ADD_GROUP, CTX_GROUP_SIMILAR)
                            }
                            //go to folder
                            if (item.uri.retrieveParent() != null) add(CTX_GO_TO_FOLDER)
                            // no sharing on TV
                            remove(ContextOption.CTX_SHARE)
                            if (entry == MediaListEntry.BROWSER) remove(CTX_GO_TO_FOLDER)
                        }
                    }

                    is MediaWrapper if isSchemeHttpOrHttps(item.uri.scheme) -> {
                        FlagSet(ContextOption::class.java).apply {
                            addAll(CTX_ADD_SHORTCUT, CTX_ADD_TO_PLAYLIST, CTX_APPEND, CTX_COPY, CTX_DELETE, CTX_RENAME)
                        }
                    }

                    else -> createCtxTrackFlags().apply {
                        if ((item as? MediaWrapper)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                        if ((item as? MediaWrapper)?.artistId != (item as? MediaWrapper)?.albumArtistId) add(CTX_GO_TO_ALBUM_ARTIST)
                    }
                }
            }

            MediaLibraryItem.TYPE_ARTIST -> {
                createCtxAudioFlags().apply {
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if ((item as? Artist)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            MediaLibraryItem.TYPE_ALBUM -> {
                createCtxPlaylistAlbumFlags().apply {
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if ((item as? Album)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            MediaLibraryItem.TYPE_GENRE -> {
                createCtxAudioFlags().apply {
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if ((item as? Genre)?.isFavorite == true) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            MediaLibraryItem.TYPE_PLAYLIST -> {
                createCtxPlaylistAlbumFlags().apply {
                    add(CTX_PLAY_AS_AUDIO)
                    if (item.tracksCount > 2) add(CTX_PLAY_SHUFFLE)
                    if (item.isFavorite) add(CTX_FAV_REMOVE) else add(CTX_FAV_ADD)
                }
            }

            else -> FlagSet(ContextOption::class.java)
        }
        return if (flags.isNotEmpty()) ContextSheet.populateMenuItems(activity, flags) else null
    }

    fun invalidateList(entry: MediaListEntry) = viewModelScope.launch {
        _invalidateMediaListEntry.emit(entry)
    }

    fun invalidationDone() = viewModelScope.launch {
        _invalidateMediaListEntry.emit(null)
    }
}

data class DisplaySettingsChange(val entry: MediaListEntry, val value: Int)

data class SnackbarContent(val message:String, val duration:SnackbarDuration = SnackbarDuration.Short)

data class TabInfo(val x: Int, val width: Int, val height: Int)