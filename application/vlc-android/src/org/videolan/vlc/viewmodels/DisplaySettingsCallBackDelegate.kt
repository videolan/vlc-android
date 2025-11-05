/*
 *****************************************************************************
 * DisplaySettingsCallBackDelegate.kt
 *****************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.tools.conflatedActor
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.MediaListEntry
import java.io.File

interface IDisplaySettingsCallBackHandler {

    fun CoroutineScope.registerDisplaySettingsCallBacks(refresh: () -> Unit, getAllProviders: () -> Array<MedialibraryProvider<out MediaLibraryItem>>)
    fun releaseDisplaySettingsCallbacks()
    fun watchFor(entry:MediaListEntry)
    fun onPause()
    fun onResume()
}

open class DisplaySettingsCallBackDelegate : IDisplaySettingsCallBackHandler
{

    private lateinit var collectionJob: Job
    private lateinit var refreshActor: SendChannel<Unit>

    private var watchedEntries = mutableListOf<MediaListEntry>()



    var paused = false
        set(value) {
            field = value
            if (!value && isInvalid) {
                refreshActor.trySend(Unit)
                isInvalid = false
            }
        }
    var isInvalid = false

    /**
     * Pause the callbacks while the caller is paused to avoid unwanted refreshes
     * During this time, instead of refreshing, it's marked as invalid.
     * If invalid, a refresh is launched upon resuming
     */
    override fun onPause() {
        paused = true
    }

    /**
     * Resumes the callback and refresh if it has been marked invalid in the meantime
     */
    override fun onResume() {
        paused = false
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    override fun CoroutineScope.registerDisplaySettingsCallBacks(refresh: () -> Unit, getAllProviders: () -> Array<MedialibraryProvider<out MediaLibraryItem>>) {
        refreshActor = conflatedActor {
           if (paused) isInvalid = true else refresh()
        }
        collectionJob = launch {
            DisplaySettingsEventManager.currentDisplaySettingsChange.collect { displaySettingsEvent ->
                if (displaySettingsEvent?.currentEntry in watchedEntries) {
                    if (displaySettingsEvent is DisplaySettingsEvent.OnlyFavsChanged) {
                        getAllProviders().forEach {
                            it.onlyFavorites = displaySettingsEvent.onlyFavorites
                        }
                    }
                    refreshActor.trySend(Unit)
                }
            }
        }
    }


    override fun watchFor(entry: MediaListEntry) {
        watchedEntries.add(entry)
    }


    override fun releaseDisplaySettingsCallbacks() {
        refreshActor.close()
        collectionJob.cancel()
    }

}

sealed class DisplaySettingsEvent(val currentEntry: MediaListEntry, time: Long) {
    data class OnlyFavsChanged(val entry: MediaListEntry, val onlyFavorites: Boolean): DisplaySettingsEvent(entry, System.currentTimeMillis())
}


object DisplaySettingsEventManager {
    private val _currentDisplaySettingsChange: MutableStateFlow<DisplaySettingsEvent?> = MutableStateFlow(null)
    val currentDisplaySettingsChange: StateFlow<DisplaySettingsEvent?> = _currentDisplaySettingsChange.asStateFlow()

    suspend fun onOnlyFavsChanged(entry: MediaListEntry, onlyFavorites: Boolean) {
        _currentDisplaySettingsChange.emit(DisplaySettingsEvent.OnlyFavsChanged(entry, onlyFavorites))
    }
}