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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.conflatedActor
import org.videolan.vlc.providers.medialibrary.ArtistsProvider
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType

interface IDisplaySettingsCallBackHandler {

    fun CoroutineScope.registerDisplaySettingsCallBacks(refresh: () -> Unit, getAllProviders: () -> Array<MedialibraryProvider<out MediaLibraryItem>>, changeGrouping: (VideoGroupingType) -> Unit)
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
    override fun CoroutineScope.registerDisplaySettingsCallBacks(refresh: () -> Unit, getAllProviders: () -> Array<MedialibraryProvider<out MediaLibraryItem>>, changeGrouping: (VideoGroupingType) -> Unit) {
        refreshActor = conflatedActor {
           if (paused) isInvalid = true else refresh()
        }
        collectionJob = launch {
            DisplaySettingsEventManager.currentDisplaySettingsChange.collect { displaySettingsEvent ->
                val currentEntry = displaySettingsEvent?.currentEntry
                if (currentEntry in watchedEntries) {
                    if (displaySettingsEvent is DisplaySettingsEvent.OnlyFavsChanged) {
                        getAllProviders().forEach {
                            if (it::class.java == displaySettingsEvent.currentEntry.providerClass)
                                it.onlyFavorites = displaySettingsEvent.onlyFavorites
                        }
                    }
                    if (displaySettingsEvent is DisplaySettingsEvent.ShowAllArtistsChanged) {
                        getAllProviders().forEach {
                            if (it is ArtistsProvider)
                                it.showAll = displaySettingsEvent.showAllArtists
                        }
                    }
                    if (displaySettingsEvent is DisplaySettingsEvent.SortChanged) {
                        getAllProviders().forEach {
                            if (it::class.java == displaySettingsEvent.currentEntry.providerClass)
                                it.sort = displaySettingsEvent.sort
                                it.desc = displaySettingsEvent.desc
                        }
                    }
                    if (displaySettingsEvent is DisplaySettingsEvent.GroupingChanged) {
                        changeGrouping.invoke(
                            when (displaySettingsEvent.entry) {
                                MediaListEntry.VIDEO -> VideoGroupingType.NONE
                                MediaListEntry.VIDEO_FOLDER -> VideoGroupingType.FOLDER
                                MediaListEntry.VIDEO_GROUPS -> VideoGroupingType.NAME
                                else -> throw IllegalStateException("Change group called on the bad viewmodel")
                            }
                        )
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
    data class SortChanged(val entry: MediaListEntry, val sort: Int, val desc: Boolean): DisplaySettingsEvent(entry, System.currentTimeMillis())
    data class ShowAllArtistsChanged(val entry: MediaListEntry, val showAllArtists: Boolean): DisplaySettingsEvent(entry, System.currentTimeMillis())
    data class GroupingChanged(val entry: MediaListEntry): DisplaySettingsEvent(entry, System.currentTimeMillis())
}


object DisplaySettingsEventManager {
    private val _currentDisplaySettingsChange: MutableStateFlow<DisplaySettingsEvent?> = MutableStateFlow(null)
    val currentDisplaySettingsChange: StateFlow<DisplaySettingsEvent?> = _currentDisplaySettingsChange.asStateFlow()

    suspend fun onOnlyFavsChanged(entry: MediaListEntry, onlyFavorites: Boolean) {
        _currentDisplaySettingsChange.emit(DisplaySettingsEvent.OnlyFavsChanged(entry, onlyFavorites))
    }

    suspend fun onSortChanged(entry: MediaListEntry, sort: Int, desc: Boolean) {
        _currentDisplaySettingsChange.emit(DisplaySettingsEvent.SortChanged(entry, sort, desc))
    }

    suspend fun onShowAllArtistsChanged(entry: MediaListEntry, showAllArtists: Boolean) {
        _currentDisplaySettingsChange.emit(DisplaySettingsEvent.ShowAllArtistsChanged(entry, showAllArtists))
    }

    suspend fun onGroupingChanged(entry: MediaListEntry) {
        _currentDisplaySettingsChange.emit(DisplaySettingsEvent.GroupingChanged(entry))
    }
}