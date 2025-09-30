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

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.BuildConfig
import org.videolan.resources.util.getFromMl
import org.videolan.vlc.util.Permissions

private const val TAG = "VLC/MoreViewModel"
class MoreViewModel(app: Application) : TvMediaViewModel(app) {


    val history: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val streams: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()

    val historyLoading = MutableLiveData(false)
    val streamsLoading = MutableLiveData(false)

    var historyLoaded = false
    var streamsLoaded = false

    fun updateHistory() = viewModelScope.launch {
        if (historyLoaded) return@launch
        historyLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateHistory")
        setLoading(historyLoading, true)
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(history)
            return@launch
        }
        getContext().getFromMl {
            history(Medialibrary.HISTORY_TYPE_LOCAL)
        }.let {
            history.value = mutableListOf<MediaWrapper>().apply {
                addAll(it)
            }
        }
        setLoading(historyLoading, false)
    }

    fun updateStreams() = viewModelScope.launch {
        if (streamsLoaded) return@launch
        streamsLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateStreams")
        setLoading(streamsLoading, true)
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(streams)
            return@launch
        }
        getContext().getFromMl {
            history(Medialibrary.HISTORY_TYPE_NETWORK)
        }.let {
            streams.value = mutableListOf<MediaWrapper>().apply {
                addAll(it)
            }
        }
        setLoading(streamsLoading, false)
    }
}