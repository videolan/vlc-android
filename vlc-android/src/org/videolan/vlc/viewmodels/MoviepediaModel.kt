/*
 * ************************************************************************
 *  NextModel.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.videolan.vlc.moviepedia.models.identify.IdentifyResult
import org.videolan.vlc.moviepedia.models.identify.Media
import org.videolan.vlc.repository.MoviepediaApiRepository

class MoviepediaModel : ViewModel() {

    val apiResult: MutableLiveData<IdentifyResult> = MutableLiveData()
    private val mediaResult: MutableLiveData<Media> = MutableLiveData()
    private val repo = MoviepediaApiRepository.getInstance()
    private var searchJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var mediaJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    fun search(query: String) {
        searchJob = viewModelScope.launch {
            apiResult.value = repo.searchTitle(query)
        }
    }

    fun search(uri: Uri) {
        searchJob = viewModelScope.launch {
            apiResult.value = repo.searchMedia(uri)
        }
    }

    fun getMedia(mediaId: String) {
        mediaJob = viewModelScope.launch {
            mediaResult.value = repo.getMedia(mediaId)
        }
    }

}