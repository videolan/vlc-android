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

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.mediadb.models.BrowserFav
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.convertFavorites

class BrowserFavoritesModel(context: Context) : AndroidViewModel(context.applicationContext as Application) {
    private val browserFavRepository = BrowserFavRepository.getInstance(context)
    private val favorites: LiveData<List<BrowserFav>> = browserFavRepository.browserFavorites

    var updatedFavoriteList = MutableLiveData<List<MediaWrapper>>()

    private val favObserver = Observer<List<BrowserFav>> { list ->
        updatedFavoriteList.value = convertFavorites(list.sortedBy { it.type })
    }

    override fun onCleared() {
        super.onCleared()
        favorites.removeObserver(favObserver)
    }

    init {
        favorites.observeForever(favObserver)
    }
}