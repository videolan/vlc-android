/*****************************************************************************
 * NetworkModel.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.NetworkMonitor

class NetworkModel(context: Context, url: String? = null, coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) : BrowserModel(context, url, TYPE_NETWORK, true, coroutineContextProvider = coroutineContextProvider) {

    init {
        NetworkMonitor.getInstance(context).connectionFlow.onEach {
            if (it.connected) refresh()
            else dataset.clear()
        }.launchIn(viewModelScope)
    }

    class Factory(val context: Context, val url: String?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NetworkModel(context.applicationContext, url) as T
        }
    }
}