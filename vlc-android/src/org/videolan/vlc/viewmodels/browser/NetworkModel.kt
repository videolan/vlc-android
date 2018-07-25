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

import android.arch.lifecycle.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.providers.NetworkProvider
import org.videolan.vlc.repository.BrowserFavRepository
import java.util.*

class NetworkModel(url: String? = null, showHiddenFiles: Boolean): BrowserModel(url, TYPE_NETWORK, showHiddenFiles) {
    private val networkProvider = provider as NetworkProvider

    val favorites : LiveData<List<MediaLibraryItem>> by lazy {
        networkProvider.getFavorites()
    }

    override fun refresh() : Boolean {
        return provider.refresh()
    }

    class Factory(val url: String?, private val showHiddenFiles: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NetworkModel(url, showHiddenFiles) as T
        }
    }
}