/*
 * ************************************************************************
 *  MoviepediaTvshowProvider.kt
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

package org.videolan.vlc.providers

import android.content.Context
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.database.models.MediaMetadataWithImages
import org.videolan.vlc.repository.MediaMetadataRepository
import org.videolan.vlc.util.getFromMl
import org.videolan.vlc.viewmodels.Season

class MoviepediaTvshowProvider(private val context: Context) {

    fun getFirstResumableEpisode(medialibrary: AbstractMedialibrary, mediaMetadataEpisodes: List<MediaMetadataWithImages>): MediaMetadataWithImages? {
        val seasons = ArrayList<Season>()
        mediaMetadataEpisodes.forEach { episode ->
            val existingSeason = seasons.firstOrNull { it.seasonNumber == episode.metadata.season }
            val season = if (existingSeason == null) {
                val newSeason = Season(episode.metadata.season ?: 0)
                seasons.add(newSeason)
                newSeason
            } else existingSeason
            season.episodes.add(episode)
        }
        seasons.forEach { season ->
            season.episodes.sortBy { episode -> episode.metadata.episode }
            //retrieve ML media
            season.episodes.forEach { episode ->
                if (episode.media == null) {
                    episode.metadata.mlId?.let {
                        val fromMl = medialibrary.getMedia(it)
                        episode.media = fromMl
                    }
                }
            }
        }

        seasons.forEach {
            it.episodes.forEach { episode ->
                episode.media?.let { media ->
                    if (media.seen < 1) {
                        return episode
                    }
                }
            }
        }
        return null
    }

    suspend fun getAllSeasons(tvShow: MediaMetadataWithImages): List<Season> {
        val mediaMetadataRepository = MediaMetadataRepository.getInstance(context)
        val mediaMetadataEpisodes = mediaMetadataRepository.getTvShowEpisodes(tvShow.metadata.moviepediaId)
        val seasons = ArrayList<Season>()
        mediaMetadataEpisodes.forEach { episode ->
            val existingSeason = seasons.firstOrNull { it.seasonNumber == episode.metadata.season }
            val season = if (existingSeason == null) {
                val newSeason = Season(episode.metadata.season ?: 0)
                seasons.add(newSeason)
                newSeason
            } else existingSeason
            season.episodes.add(episode)
        }

        seasons.forEach { season ->
            season.episodes.sortBy { episode -> episode.metadata.episode }
            //retrieve ML media
            season.episodes.forEach { episode ->
                if (episode.media == null) {
                    episode.metadata.mlId?.let {
                        val fromMl = context.getFromMl { getMedia(it) }
                        episode.media = fromMl
                    }
                }
            }
        }
        return seasons
    }

    fun getShowIdForEpisode(id: String): String? {
        val mediaMetadataRepository = MediaMetadataRepository.getInstance(context)
        mediaMetadataRepository.getMediaById(id)?.metadata?.showId?.let {
            return mediaMetadataRepository.getTvshow(it)?.metadata?.moviepediaId
        }
        return null
    }

    suspend fun getResumeMediasById(id: String): List<MediaWrapper> {
        val mediaMetadataRepository = MediaMetadataRepository.getInstance(context)
        val mediasToPlay = ArrayList<MediaWrapper>()
        mediaMetadataRepository.getTvshow(id)?.let { tvShow ->
            val seasons = getAllSeasons(tvShow)
            return getResumeMedias(seasons)
        }
        return mediasToPlay
    }

    fun getResumeMedias(seasons: List<Season>?): List<MediaWrapper> {
        val mediasToPlay = ArrayList<MediaWrapper>()
        var firstResumableFound = false
        seasons?.forEach {
            it.episodes.forEach { episode ->
                episode.media?.let { media ->
                    if (media.seen < 1 || firstResumableFound) {
                        firstResumableFound = true
                        mediasToPlay.add(media)
                    }
                }
            }
        }
        return mediasToPlay
    }

    suspend fun getAllEpisodesForShow(id: String): List<MediaMetadataWithImages> {
        val medias = ArrayList<MediaMetadataWithImages>()
        val mediaMetadataRepository = MediaMetadataRepository.getInstance(context)
        mediaMetadataRepository.getTvshow(id)?.let { tvShow ->
            val season = getAllSeasons(tvShow)
            season.forEach {
                it.episodes.forEach { episode ->
                    medias.add(episode)
                }
            }

        }

        return medias
    }

    fun getAllMedias(seasons: List<Season>?): List<MediaWrapper> {
        val mediasToPlay = ArrayList<MediaWrapper>()
        seasons?.forEach {
            it.episodes.forEach { episode ->
                episode.media?.let { media ->
                    mediasToPlay.add(media)
                }
            }
        }
        return mediasToPlay
    }
}