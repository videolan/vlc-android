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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.vlc.next.models.NextResults
import org.videolan.vlc.repository.NextApiRepository

class NextModel(private val context: Context, private val mediaUri: Uri) : ViewModel() {

    val apiResultLiveData: MutableLiveData<NextResults> = MutableLiveData()
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                apiResultLiveData.postValue(NextApiRepository.getInstance().search(query))
            }
        }
    }

    class Factory(private val context: Context, private val mediaUri: Uri) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NextModel(context.applicationContext, mediaUri) as T
        }
    }
}