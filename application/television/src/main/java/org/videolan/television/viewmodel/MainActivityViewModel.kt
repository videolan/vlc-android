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
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.compose.MainDestination
import org.videolan.vlc.gui.dialogs.CtxMenuItem
import org.videolan.vlc.util.MediaListEntry
import javax.inject.Inject

/**
 * Main ViewModel for the Television module activity.
 * It orchestrates UI state, navigation, and background services by delegating
 * specialized tasks to [ContextMenuDelegate] and [MedialibraryDelegate].
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    app: Application,
    private val contextMenuDelegate: ContextMenuDelegate,
    private val medialibraryDelegate: MedialibraryDelegate
) : AndroidViewModel(app) {

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

    /**
     * Navigates to a new top-level destination.
     *
     * @param destination The [MainDestination] to navigate to.
     */
    fun navigateTo(destination: MainDestination) = viewModelScope.launch {
        _navigationFlow.emit(destination)
    }

    /**
     * Updates the visibility state of the navigation tabs.
     *
     * @param show True to show the tabs, false to hide them.
     */
    fun setShowTabs(show: Boolean) = viewModelScope.launch {
        _showTabs.emit(show)
    }

    /**
     * Exposes the flow of newly detected storage devices from the medialibrary delegate.
     */
    val newStorageDetected = medialibraryDelegate.newStorageDetected

    /**
     * Registers a context menu click listener by delegating to [contextMenuDelegate].
     */
    fun addCtxClickListener(mediaListEntry: MediaListEntry, listener: (MediaLibraryItem, Int, CtxMenuItem) -> Unit) {
        contextMenuDelegate.addCtxClickListener(mediaListEntry, listener)
    }

    /**
     * Updates the current media list entry to trigger display settings UI.
     *
     * @param entry The [MediaListEntry] to focus on, or null to close the settings.
     */
    fun changeCurrentMediaListEntry(entry: MediaListEntry?) = viewModelScope.launch {
        _currentMediaListEntry.emit(entry)
    }

    private val tabsInfo = mutableMapOf<String, TabInfo>()


    /**
     * Toggles the editing state of the audio player queue.
     */
    val editAudioQueue = MutableStateFlow(false)
    fun toggleEditAudioQueue() {
        editAudioQueue.value = !editAudioQueue.value
    }

    /**
     * Exposes the medialibrary parsing progress.
     */
    val progress = medialibraryDelegate.progress

    init {
        medialibraryDelegate.setup(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        medialibraryDelegate.onCleared()
    }

    /**
     * Retrieves the stored tab layout information for a specific key.
     */
    fun getTabInfo(key: String): TabInfo? {
        return tabsInfo[key]
    }

    /**
     * Stores tab layout information (position and size) for smooth animations.
     */
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

    /**
     * Closes the current display settings dialog.
     */
    fun hideDisplaySettings() = viewModelScope.launch {
        _currentMediaListEntry.emit(null)
    }

    /**
     * Triggers a snackbar message in the UI.
     *
     * @param content The [SnackbarContent] containing the message and duration.
     */
    fun showSnackbar(content: SnackbarContent?) = viewModelScope.launch {
        _snackBarFlow.emit(content)
    }

    /**
     * Delegates storage acceptance to [medialibraryDelegate].
     */
    fun acceptStorage(path: String) {
        medialibraryDelegate.acceptStorage(path)
    }

    /**
     * Delegates storage decline to [medialibraryDelegate].
     */
    fun declineStorage() {
        medialibraryDelegate.declineStorage()
    }

    /**
     * Handles context menu item clicks by delegating to [contextMenuDelegate].
     */
    fun onCtxClick(entry: MediaListEntry, item: MediaLibraryItem, position: Int, it: CtxMenuItem) {
        contextMenuDelegate.onCtxClick(entry, item, position, it)
    }

    /**
     * Gets context menu flags for an item by delegating to [contextMenuDelegate].
     */
    fun getFlags(activity: Activity, entry: MediaListEntry, item: MediaLibraryItem): List<CtxMenuItem>? {
        return contextMenuDelegate.getFlags(activity, entry, item) { message ->
            showSnackbar(SnackbarContent(message))
        }
    }

    /**
     * Signals the UI to invalidate/refresh a specific media list.
     */
    fun invalidateList(entry: MediaListEntry) = viewModelScope.launch {
        _invalidateMediaListEntry.emit(entry)
    }

    /**
     * Resets the invalidation state once the refresh is complete.
     */
    fun invalidationDone() = viewModelScope.launch {
        _invalidateMediaListEntry.emit(null)
    }
}

/**
 * Data class representing a change in display settings for a specific list entry.
 */
data class DisplaySettingsChange(val entry: MediaListEntry, val value: Int)

/**
 * Data class for snackbar notifications.
 */
data class SnackbarContent(val message:String, val duration:SnackbarDuration = SnackbarDuration.Short)

/**
 * Data class storing the layout information of a tab for focus and animation purposes.
 * @property x The horizontal position in pixels.
 * @property width The width in pixels.
 * @property height The height in pixels.
 */
data class TabInfo(val x: Int, val width: Int, val height: Int)
