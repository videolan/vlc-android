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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.BuildConfig
import org.videolan.resources.HEADER_ADD_STREAM
import org.videolan.resources.util.getFromMl
import org.videolan.vlc.R
import org.videolan.vlc.media.getAll
import org.videolan.vlc.util.Permissions
import kotlin.collections.forEach
import kotlin.collections.isNullOrEmpty

private const val TAG = "VLC/MoreViewModel"

class MoreViewModel(app: Application) : TvMediaViewModel(app) {


    val history: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()
    val streams: MutableLiveData<List<MediaLibraryItem>> = MutableLiveData()

    private val _streamsFlow: MutableStateFlow<List<MediaLibraryItem>?> = MutableStateFlow(null)
    val streamsFlow: StateFlow<List<MediaLibraryItem>?> = _streamsFlow.asStateFlow()

    val historyLoading = MutableLiveData(false)
    val streamsLoading = MutableLiveData(false)

    var historyLoaded = false
    var streamsLoaded = false
    var deletingMedia: MediaWrapper? = null

    suspend fun updateHistory() {
        if (historyLoaded) return
        historyLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateHistory")
        setLoading(historyLoading, true)
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(history)
            return
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

    suspend fun updateStreams() {
        if (streamsLoaded) return
        streamsLoaded = true
        if (BuildConfig.DEBUG) Log.d(TAG, "updateStreams")
        setLoading(streamsLoading, true)
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(streams)
            return
        }
        getContext().getFromMl {
            history(Medialibrary.HISTORY_TYPE_NETWORK)
        }.let {
            it?.forEach {
                if (org.videolan.vlc.BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "VM Stream found: ${it.title}")
            }
            streams.value = mutableListOf<MediaLibraryItem>().apply {
                add(DummyItem(HEADER_ADD_STREAM, getContext().getString(R.string.new_stream), ""))
                addAll(it)
            }
            _streamsFlow.emit(streams.value)
        }
        setLoading(streamsLoading, false)
    }

    fun delete() {
        deletingMedia?.let { media ->
            viewModelScope.launch {
                getContext().getFromMl { removeExternalMedia(media.id) }
                streamsLoaded = false
                updateStreams()
            }
        }
    }

    fun rename(media: MediaWrapper, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { media.rename(name) }
            updateStreams()
        }
    }

    fun invalidate(invalidateListener: () -> Unit) {
        viewModelScope.launch {
            streamsLoaded = false
            historyLoaded = false
            _streamsFlow.emit(null)
            updateStreams()
            updateHistory()
            delay(500)
            invalidateListener()
        }
    }
}