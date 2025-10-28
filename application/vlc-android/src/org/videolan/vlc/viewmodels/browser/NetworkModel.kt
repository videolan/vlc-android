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
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.NetworkMonitor

class NetworkModel(context: Context, url: String? = null, mocked: ArrayList<MediaLibraryItem>? = null, coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) : BrowserModel(context, url, TYPE_NETWORK,false,  mocked = mocked, coroutineContextProvider = coroutineContextProvider) {

    init {
        NetworkMonitor.getInstance(context).connectionFlow.onEach {
            if (it.connected) refresh()
            else dataset.clear()
        }.launchIn(viewModelScope)
    }

    class Factory(val context: Context, val url: String?, val mocked: ArrayList<MediaLibraryItem>? = null): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NetworkModel(context.applicationContext, url, mocked) as T
        }
    }
    companion object {
        // Define a custom key for your dependency
        val URL_KEY = object : CreationExtras.Key<String?> {}
        val MOCKED_KEY = object : CreationExtras.Key<ArrayList<MediaLibraryItem>?> {}
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Get the dependency in your factory
                val application = checkNotNull(this[APPLICATION_KEY])
                val url = this[URL_KEY]
                val mocked = this[MOCKED_KEY]


                NetworkModel(
                    application,
                    url,
                    mocked,
                )
            }
        }
    }
}