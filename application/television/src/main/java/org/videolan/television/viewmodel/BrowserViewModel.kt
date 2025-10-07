/*
 * ************************************************************************
 *  BrowserViewModel.kt
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.HEADER_NETWORK
import org.videolan.resources.HEADER_SERVER
import org.videolan.resources.HEADER_STREAM
import org.videolan.television.ui.FAVORITE_FLAG
import org.videolan.tools.NetworkMonitor
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.mediadb.models.BrowserFav
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.convertFavorites
import org.videolan.vlc.util.scanAllowed

class BrowserViewModel(app: Application) : TvMediaViewModel(app) {
    private var updatedFavoriteList: List<MediaWrapper> = listOf()
    val favoritesList: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    private val showInternalStorage = AndroidDevices.showInternalStorage()
    private val networkMonitor = NetworkMonitor.getInstance(getContext())
    val browsers: LiveData<List<MediaLibraryItem>> = MutableLiveData()
    private val updateActor = viewModelScope.actor<Unit>(capacity = Channel.CONFLATED) {
        for (action in channel) updateBrowsers()
    }
    private val browserFavRepository = BrowserFavRepository.getInstance(getContext())
    private val favorites: LiveData<List<BrowserFav>> = browserFavRepository.getFavDao().asLiveData(viewModelScope.coroutineContext)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val favObserver = Observer<List<BrowserFav>> { list ->
        updatedFavoriteList = convertFavorites(list)
        if (!updateActor.isClosedForSend) updateActor.trySend(Unit)
    }

    val favoritesLoading = MutableLiveData(false)
    val browsersLoading = MutableLiveData(false)

    init {
        networkMonitor.connectionFlow.onEach { updateActor.trySend(Unit) }.launchIn(viewModelScope)
        ExternalMonitor.storageEvents.onEach { updateActor.trySend(Unit) }.launchIn(viewModelScope)
        updateActor.trySend(Unit)
        favorites.observeForever(favObserver)
    }

    override fun onCleared() {
        super.onCleared()
        favorites.removeObserver(favObserver)
    }



    private suspend fun updateBrowsers() {
        val favList = mutableListOf<MediaLibraryItem>()
        updatedFavoriteList.forEach {
            it.description = it.uri.scheme
            it.addFlags(FAVORITE_FLAG)
            favList.add(it)
        }
        (favoritesList as MutableLiveData).value = favList
        val list = mutableListOf<MediaLibraryItem>()
        val directories = DirectoryRepository.getInstance(getContext()).getMediaDirectoriesList(getContext()).toMutableList()
        if (!showInternalStorage && directories.isNotEmpty()) directories.removeAt(0)
        directories.forEach { if (it.location.scanAllowed()) list.add(it) }

        if (networkMonitor.isLan) {
            list.add(DummyItem(HEADER_NETWORK, getContext().getString(org.videolan.vlc.R.string.network_browsing), null))
            list.add(DummyItem(HEADER_STREAM, getContext().getString(org.videolan.vlc.R.string.streams), null))
            list.add(DummyItem(HEADER_SERVER, getContext().getString(org.videolan.vlc.R.string.server_add_title), null))
        }
        (browsers as MutableLiveData).value = list
        delay(500L)
    }
}