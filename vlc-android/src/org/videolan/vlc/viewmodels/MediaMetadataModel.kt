/*
 * ************************************************************************
 *  MediaMetadataModel.kt
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
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.videolan.vlc.database.models.MediaMetadataWithImages
import org.videolan.vlc.database.models.Person
import org.videolan.vlc.database.models.PersonType
import org.videolan.vlc.repository.MediaMetadataRepository
import org.videolan.vlc.repository.MediaPersonRepository

class MediaMetadataModel(context: Context, mlId: Long) : ViewModel() {

    var mediaMetadataLiveData: MediatorLiveData<MediaMetadataWithImages> = MediatorLiveData()
    val actorsLiveData: MediatorLiveData<List<Person>> = MediatorLiveData()
    val writersLiveData: MediatorLiveData<List<Person>> = MediatorLiveData()
    val producersLiveData: MediatorLiveData<List<Person>> = MediatorLiveData()
    val musiciansLiveData: MediatorLiveData<List<Person>> = MediatorLiveData()
    val directorsLiveData: MediatorLiveData<List<Person>> = MediatorLiveData()

    init {
        val metadata = MediaMetadataRepository.getInstance(context).getMetadata(mlId)
        mediaMetadataLiveData.addSource(metadata) {
            mediaMetadataLiveData.postValue(it)
        }
        actorsLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.ACTOR)) {
            actorsLiveData.postValue(it)
        }
        writersLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.WRITER)) {
            writersLiveData.postValue(it)
        }
        producersLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.PRODUCER)) {
            producersLiveData.postValue(it)
        }
        musiciansLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.MUSICIAN)) {
            musiciansLiveData.postValue(it)
        }
        directorsLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.DIRECTOR)) {
            directorsLiveData.postValue(it)
        }
    }

    class Factory(private val context: Context, private val mlId: Long) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MediaMetadataModel(context.applicationContext, mlId) as T
        }
    }
}