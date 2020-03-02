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

package org.videolan.moviepedia.viewmodel

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.videolan.moviepedia.MediaScraper
import org.videolan.moviepedia.models.resolver.ResolverMedia
import org.videolan.moviepedia.models.resolver.ResolverResult
import videolan.org.commontools.LiveEvent

class MediaScrapingModel : ViewModel() {

    val apiResult: MutableLiveData<ResolverResult> = MutableLiveData()
    private val mediaResult: MutableLiveData<ResolverMedia> = MutableLiveData()
    private val repo = MediaScraper.mediaResolverApi
    val exceptionLiveData = LiveEvent<Exception?>()

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
            try {
                apiResult.value = MediaScraper.mediaResolverApi.searchTitle(query)
            } catch (e: Exception) {
                exceptionLiveData.value = e
            }
        }
    }

    fun search(uri: Uri) {
        searchJob = viewModelScope.launch {
            try {
                apiResult.value = MediaScraper.mediaResolverApi.searchMedia(uri)
            } catch (e: Exception) {
                exceptionLiveData.value = e
            }
        }
    }

    fun getMedia(mediaId: String) {
        mediaJob = viewModelScope.launch {
            try {
                mediaResult.value = repo.getMedia(mediaId)
            } catch (e: Exception) {
                exceptionLiveData.value = e
            }
        }
    }
}