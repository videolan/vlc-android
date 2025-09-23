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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.resources.BuildConfig
import org.videolan.resources.HEADER_PERMISSION
import org.videolan.resources.util.getFromMl
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.util.Permissions

class MoreViewModel(app: Application) : AndroidViewModel(app) {


    val history: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    val streams: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    var historyLoaded = false
    var streamsLoaded = false


    val context = getApplication<Application>().getContextWithLocale(AppContextProvider.locale)


    fun updateHistory() = viewModelScope.launch {
        if (historyLoaded) return@launch
        historyLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updateAudioTracks")
        if (!Permissions.canReadStorage(context)) {
            (history as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            history(Medialibrary.HISTORY_TYPE_LOCAL)
        }.let {
            (history as MutableLiveData).value = mutableListOf<MediaWrapper>().apply {
                addAll(it)
            }
        }
    }

    fun updateStreams() = viewModelScope.launch {
        if (streamsLoaded) return@launch
        streamsLoaded = true
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "updateAudioTracks")
        if (!Permissions.canReadStorage(context)) {
            (streams as MutableLiveData).value =
                    listOf(DummyItem(HEADER_PERMISSION, context.getString(org.videolan.vlc.R.string.permission_media), context.getString(org.videolan.vlc.R.string.permission_ask_again)))
            return@launch
        }
        context.getFromMl {
            history(Medialibrary.HISTORY_TYPE_NETWORK)
        }.let {
            (streams as MutableLiveData).value = mutableListOf<MediaWrapper>().apply {
                addAll(it)
            }
        }
    }
}