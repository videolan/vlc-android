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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import org.videolan.vlc.database.models.MediaMetadataWithImages
import org.videolan.vlc.database.models.Person
import org.videolan.vlc.database.models.PersonType
import org.videolan.vlc.repository.MediaMetadataRepository
import org.videolan.vlc.repository.MediaPersonRepository

class MediaMetadataModel(context: Context, mlId: Long) : ViewModel(), CoroutineScope by MainScope() {

    val updateLiveData: MediatorLiveData<MediaMetadataFull> = MediatorLiveData()
    private val updateActor = actor<MediaMetadataFull>(capacity = Channel.CONFLATED) {
        for (entry in channel) {
            updateLiveData.value = entry
            delay(100L)
        }
    }

    init {
        val mediaMetadataFull = MediaMetadataFull()
        val metadata = MediaMetadataRepository.getInstance(context).getMetadata(mlId)
        updateLiveData.addSource(metadata) {
            mediaMetadataFull.metadata = it
            updateActor.offer(mediaMetadataFull)

        }
        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.ACTOR)) {
            mediaMetadataFull.actors = it
            updateActor.offer(mediaMetadataFull)
        }
        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.WRITER)) {
            mediaMetadataFull.writers = it
            updateActor.offer(mediaMetadataFull)
        }
        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.PRODUCER)) {
            mediaMetadataFull.producers = it
            updateActor.offer(mediaMetadataFull)
        }
        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.MUSICIAN)) {
            mediaMetadataFull.musicians = it
            updateActor.offer(mediaMetadataFull)
        }
        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mlId, PersonType.DIRECTOR)) {
            mediaMetadataFull.directors = it
            updateActor.offer(mediaMetadataFull)
        }
    }

    class Factory(private val context: Context, private val mlId: Long) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MediaMetadataModel(context.applicationContext, mlId) as T
        }
    }
}

class MediaMetadataFull {
    var metadata: MediaMetadataWithImages? = null
    var actors: List<Person>? = null
    var writers: List<Person>? = null
    var producers: List<Person>? = null
    var musicians: List<Person>? = null
    var directors: List<Person>? = null
}