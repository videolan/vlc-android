/*
 * ************************************************************************
 *  BrowserFavoritesModel.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels.browser

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.mediadb.models.BrowserFav
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.convertFavorites

class BrowserFavoritesModel(context: Context) : ViewModel() {
    val favorites = LiveDataset<MediaLibraryItem>()
    val provider = FavoritesProvider(context.applicationContext, favorites, viewModelScope)
    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Get the dependency in your factory
                val application = checkNotNull(this[APPLICATION_KEY])

                BrowserFavoritesModel(application)
            }
        }
    }
}

class FavoritesProvider(
        context: Context,
        dataset: LiveDataset<MediaLibraryItem>,
        private val scope: CoroutineScope
) : BrowserProvider(context, dataset, null, Medialibrary.SORT_FILENAME, false) {
    private val browserFavRepository = BrowserFavRepository.getInstance(context)

    init {
        browserFavRepository.getFavDao()
                .onEach { list ->
                    convertFavorites(list.sortedWith(compareBy(BrowserFav::title, BrowserFav::type))).let {
                        @Suppress("UNCHECKED_CAST")
                        dataset.postValue(it as MutableList<MediaLibraryItem>)
                        parseSubDirectories()
                    }
                }
                .flowOn(Dispatchers.IO)
                .launchIn(scope)
    }

    override fun refresh() {
        browserFavRepository.getFavDao()
            .onEach { list ->
                convertFavorites(list.sortedWith(compareBy(BrowserFav::title, BrowserFav::type))).let {
                    @Suppress("UNCHECKED_CAST")
                    dataset.postValue(it as MutableList<MediaLibraryItem>)
                    parseSubDirectories()
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
        super.refresh()
    }

    override suspend fun requestBrowsing(url: String?, eventListener: MediaBrowser.EventListener, interact : Boolean) = withContext(coroutineContextProvider.IO) {
        initBrowser()
        mediabrowser?.let {
            it.changeEventListener(eventListener)
            if (url != null) it.browse(url.toUri(), getFlags(interact))
        }
    }

    override suspend fun browseRootImpl() {}
}
