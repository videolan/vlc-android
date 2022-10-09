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

package org.videolan.moviepedia.viewmodel

import android.content.Context
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.moviepedia.MediaScraper
import org.videolan.moviepedia.database.models.*
import org.videolan.moviepedia.provider.MediaScrapingTvshowProvider
import org.videolan.moviepedia.repository.MediaMetadataRepository
import org.videolan.moviepedia.repository.MediaPersonRepository
import org.videolan.resources.util.getFromMl

class MediaMetadataModel(private val context: Context, mlId: Long? = null, moviepediaId: String? = null) : ViewModel(), CoroutineScope by MainScope() {

    val updateLiveData: MediatorLiveData<MediaMetadataFull> = MediatorLiveData()
    val nextEpisode: MutableLiveData<MediaMetadataWithImages> = MutableLiveData()
    val provider = MediaScrapingTvshowProvider(context)

    @OptIn(ObsoleteCoroutinesApi::class)
    private val updateActor = actor<MediaMetadataFull>(capacity = Channel.CONFLATED) {
        for (entry in channel) {
            updateLiveData.value = entry
            delay(100L)
        }
    }

    init {
        //searching by ML id
        val mediaMetadataFull = MediaMetadataFull()
        mlId?.let { medialibId ->
            val metadata = MediaMetadataRepository.getInstance(context).getMetadataLiveByML(medialibId)
            updateLiveData.addSource(metadata) { mediaMetadataWithImages ->
                mediaMetadataFull.metadata = mediaMetadataWithImages
                updateActor.trySend(mediaMetadataFull)
                if (mediaMetadataFull.metadata?.metadata?.type == MediaMetadataType.TV_EPISODE) {
                    //look for a next episode
                    mediaMetadataFull.metadata?.show?.let {
                        if (mediaMetadataFull.metadata!!.metadata.showId != null && mediaMetadataFull.metadata!!.metadata.season != null && mediaMetadataFull.metadata!!.metadata.episode != null) {
                            launch {
                                val metadataWithImages = withContext(Dispatchers.IO) { MediaMetadataRepository.getInstance(context).findNextEpisode(mediaMetadataFull.metadata!!.metadata.showId!!, mediaMetadataFull.metadata!!.metadata.season!!, mediaMetadataFull.metadata!!.metadata.episode!!) }
                                metadataWithImages?.metadata?.mlId?.let {

                                    val fromMl = context.getFromMl { getMedia(it) }
                                    metadataWithImages.media = fromMl
                                    nextEpisode.postValue(metadataWithImages)
                                }
                            }
                        }
                    }
                }
                mediaMetadataWithImages?.metadata?.let {
                    launch {
                        if (!it.hasCast && it.mlId != null) {
                            withContext(Dispatchers.IO) { MediaScraper.retrieveCasting(context, it) }
                        }
                        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(it.moviepediaId, PersonType.ACTOR)) { persons ->
                            mediaMetadataFull.actors = persons
                            updateActor.trySend(mediaMetadataFull)
                        }
                        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(it.moviepediaId, PersonType.WRITER)) { persons ->
                            mediaMetadataFull.writers = persons
                            updateActor.trySend(mediaMetadataFull)
                        }
                        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(it.moviepediaId, PersonType.PRODUCER)) { persons ->
                            mediaMetadataFull.producers = persons
                            updateActor.trySend(mediaMetadataFull)
                        }
                        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(it.moviepediaId, PersonType.MUSICIAN)) { persons ->
                            mediaMetadataFull.musicians = persons
                            updateActor.trySend(mediaMetadataFull)
                        }
                        updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(it.moviepediaId, PersonType.DIRECTOR)) { persons ->
                            mediaMetadataFull.directors = persons
                            updateActor.trySend(mediaMetadataFull)
                        }
                    }
                }

            }

        }

        //searching by moviepedia id
        moviepediaId?.let { mId ->
            val metadata = MediaMetadataRepository.getInstance(context).getMetadataLive(mId)
            updateLiveData.addSource(metadata) {
                mediaMetadataFull.metadata = it
                updateActor.trySend(mediaMetadataFull)
                if (it?.metadata?.type == MediaMetadataType.TV_SHOW) {
                    val episodes = MediaMetadataRepository.getInstance(context).getEpisodesLive(mId)
                    updateLiveData.addSource(episodes) {
                        launch {
                            val seasons = withContext(Dispatchers.IO) { provider.getAllSeasons(mediaMetadataFull.metadata!!) }
                            mediaMetadataFull.seasons = seasons.sortedBy { season -> season.seasonNumber }
                            updateActor.trySend(mediaMetadataFull)
                        }
                    }
                }
            }
            updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mId, PersonType.ACTOR)) {
                mediaMetadataFull.actors = it
                updateActor.trySend(mediaMetadataFull)
            }
            updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mId, PersonType.WRITER)) {
                mediaMetadataFull.writers = it
                updateActor.trySend(mediaMetadataFull)
            }
            updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mId, PersonType.PRODUCER)) {
                mediaMetadataFull.producers = it
                updateActor.trySend(mediaMetadataFull)
            }
            updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mId, PersonType.MUSICIAN)) {
                mediaMetadataFull.musicians = it
                updateActor.trySend(mediaMetadataFull)
            }
            updateLiveData.addSource(MediaPersonRepository.getInstance(context).getPersonsByType(mId, PersonType.DIRECTOR)) {
                mediaMetadataFull.directors = it
                updateActor.trySend(mediaMetadataFull)
            }
        }
    }

    fun updateMetadataImage(item: MediaImage) {
        val metadata = updateLiveData.value?.metadata?.metadata ?: return
        when (item.imageType) {
            MediaImageType.POSTER -> metadata.currentPoster = item.url
            MediaImageType.BACKDROP -> metadata.currentBackdrop = item.url
        }
        launch {
            withContext(Dispatchers.IO) {
                MediaMetadataRepository.getInstance(context).addMetadataImmediate(metadata)
            }
        }
    }

    class Factory(private val context: Context, private val mlId: Long? = null, private val showId: String? = null) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MediaMetadataModel(context.applicationContext, mlId, showId) as T
        }
    }
}

class MediaMetadataFull {
    var metadata: MediaMetadataWithImages? = null
    var seasons: List<Season>? = null
    var actors: List<Person>? = null
    var writers: List<Person>? = null
    var producers: List<Person>? = null
    var musicians: List<Person>? = null
    var directors: List<Person>? = null
}

data class Season(
        var seasonNumber: Int = 0,
        var episodes: ArrayList<MediaMetadataWithImages> = ArrayList()
)