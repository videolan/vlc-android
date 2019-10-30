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
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.database.models.*
import org.videolan.vlc.next.models.body.ScrobbleBody
import org.videolan.vlc.next.models.identify.IdentifyResult
import org.videolan.vlc.next.models.identify.Media
import org.videolan.vlc.next.models.identify.getImageUriFromPath
import org.videolan.vlc.next.models.media.cast.CastResult
import org.videolan.vlc.next.models.media.cast.image
import org.videolan.vlc.repository.MediaMetadataRepository
import org.videolan.vlc.repository.MediaPersonRepository
import org.videolan.vlc.repository.NextApiRepository
import org.videolan.vlc.repository.PersonRepository
import org.videolan.vlc.util.FileUtils
import java.io.File

class NextModel : ViewModel() {

    val apiResultLiveData: MutableLiveData<IdentifyResult> = MutableLiveData()
    val getMediaResultLiveData: MutableLiveData<Media> = MutableLiveData()
    val getMediaCastResultLiveData: MutableLiveData<CastResult> = MutableLiveData()
    private var searchJob: Job? = null
    private var mediaJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val scrobbleBody = ScrobbleBody(title = query)
                apiResultLiveData.postValue(NextApiRepository.getInstance().searchMedia(scrobbleBody))
            }
        }
    }

    fun search(file: Uri) {
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val scrobbleBody = ScrobbleBody(filename = file.lastPathSegment, osdbhash = FileUtils.computeHash(File(file.path)))
                apiResultLiveData.postValue(NextApiRepository.getInstance().searchMedia(scrobbleBody))
            }
        }
    }

    fun getMedia(mediaId: String) {
        mediaJob?.cancel()

        mediaJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                getMediaResultLiveData.postValue(NextApiRepository.getInstance().getMedia(mediaId))
            }
        }
    }


    fun saveMediaMetadata(context: Context, media: AbstractMediaWrapper, item: Media) {
        val type = when (item.type) {
            "tvshow" -> 1
            else -> 0
        }

        mediaJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val mediaMetadata = MediaMetadata(
                        media.id,
                        type,
                        item.mediaId,
                        item.title,
                        item.summary ?: "",
                        item.genre?.joinToString { genre -> genre } ?: "",
                        item.date,
                        item.country?.joinToString { genre -> genre } ?: "")

                val mediaMetadataRepository = MediaMetadataRepository.getInstance(context)

                val oldMediaMetadata = mediaMetadataRepository.getMetadata(media.id)
                val oldImages = oldMediaMetadata?.images

                mediaMetadataRepository.addMetadataImmediate(mediaMetadata)

                val images = ArrayList<MediaImage>()
                item.images?.backdrops?.forEach {
                    images.add(MediaImage(item.getImageUriFromPath(it.path), mediaMetadata.mlId, MediaImageType.BACKDROP))
                }
                item.images?.posters?.forEach {
                    images.add(MediaImage(item.getImageUriFromPath(it.path), mediaMetadata.mlId, MediaImageType.POSTER))
                }
                //delete old images
                oldImages?.let {
                    mediaMetadataRepository.deleteImages(it.filter { images.any { newImage -> it.url == newImage.url }.not() })
                }
                mediaMetadataRepository.addImagesImmediate(images)

                val personsToAdd = ArrayList<MediaPersonJoin>()

                val castResult = NextApiRepository.getInstance().getMediaCast(item.mediaId)
                castResult.actor?.forEach { actor ->
                    val actorEntity = Person(actor.person.personId, actor.person.name, actor.person.image())
                    if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Inserting ${actor.person.name} - ${actor.person.personId} as actor")
                    PersonRepository.getInstance(context).addPersonImmediate(actorEntity)
                    personsToAdd.add(MediaPersonJoin(mediaMetadata.mlId, actorEntity.nextId, PersonType.ACTOR))

                }
                castResult.director?.forEach { actor ->
                    val actorEntity = Person(actor.person.personId, actor.person.name, actor.person.image())
                    if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Inserting ${actor.person.name} - ${actor.person.personId} as director")
                    PersonRepository.getInstance(context).addPersonImmediate(actorEntity)
                    personsToAdd.add(MediaPersonJoin(mediaMetadata.mlId, actorEntity.nextId, PersonType.DIRECTOR))

                }
                castResult.writer?.forEach { actor ->
                    val actorEntity = Person(actor.person.personId, actor.person.name, actor.person.image())
                    if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Inserting ${actor.person.name} - ${actor.person.personId} as writer")
                    PersonRepository.getInstance(context).addPersonImmediate(actorEntity)
                    personsToAdd.add(MediaPersonJoin(mediaMetadata.mlId, actorEntity.nextId, PersonType.WRITER))

                }
                castResult.musician?.forEach { actor ->
                    val actorEntity = Person(actor.person.personId, actor.person.name, actor.person.image())
                    if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Inserting ${actor.person.name} - ${actor.person.personId} as musician")
                    PersonRepository.getInstance(context).addPersonImmediate(actorEntity)
                    personsToAdd.add(MediaPersonJoin(mediaMetadata.mlId, actorEntity.nextId, PersonType.MUSICIAN))

                }
                castResult.producer?.forEach { actor ->
                    val actorEntity = Person(actor.person.personId, actor.person.name, actor.person.image())
                    if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Inserting ${actor.person.name} - ${actor.person.personId} as producer")
                    PersonRepository.getInstance(context).addPersonImmediate(actorEntity)
                    personsToAdd.add(MediaPersonJoin(mediaMetadata.mlId, actorEntity.nextId, PersonType.PRODUCER))

                }
                MediaPersonRepository.getInstance(context).removeAllFor(mediaMetadata.mlId)
                MediaPersonRepository.getInstance(context).addPersons(personsToAdd)

                //Remove orphans
                val allPersons = PersonRepository.getInstance(context).getAll()
                val allPersonJoins = MediaPersonRepository.getInstance(context).getAll()
                val personsToRemove = allPersons.filter { person -> allPersonJoins.any { personJoin -> person.nextId == personJoin.personId }.not() }
                PersonRepository.getInstance(context).deleteAll(personsToRemove)
            }
        }
    }

    class Factory : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NextModel() as T
        }
    }
}