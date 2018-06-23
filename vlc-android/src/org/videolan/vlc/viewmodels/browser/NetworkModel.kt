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

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.providers.NetworkProvider

class NetworkModel(url: String? = null, showHiddenFiles: Boolean): BrowserModel(url, TYPE_NETWORK, showHiddenFiles) {
    private val networkProvider = provider as NetworkProvider
    val favorites : MutableLiveData<MutableList<MediaLibraryItem>> by lazy {
        launch(UI) { updateFavs() }
        MutableLiveData<MutableList<MediaLibraryItem>>()
    }

    fun updateFavs() = launch(UI, CoroutineStart.UNDISPATCHED) {
        favorites.value = withContext(CommonPool) { networkProvider.updateFavorites() }
    }

    override fun refresh() : Boolean {
        updateFavs()
        return provider.refresh()
    }

    class Factory(val url: String?, private val showHiddenFiles: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NetworkModel(url, showHiddenFiles) as T
        }
    }
}