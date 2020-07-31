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

package org.videolan.moviepedia.provider

import android.content.Context
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.moviepedia.repository.MediaMetadataRepository
import org.videolan.moviepedia.viewmodel.Season
import org.videolan.resources.CONTENT_EPISODE
import org.videolan.resources.CONTENT_RESUME
import org.videolan.resources.interfaces.IMediaContentResolver
import org.videolan.resources.util.getFromMl

class MediaScrapingTvshowProvider(private val context: Context) {

    @MainThread
    fun getFirstResumableEpisode(medialibrary: Medialibrary, mediaMetadataEpisodes: List<MediaMetadataWithImages>): MediaMetadataWithImages? {
        val seasons = ArrayList<Season>()
        mediaMetadataEpisodes.forEach { episode ->
            val season = seasons.firstOrNull { it.seasonNumber == episode.metadata.season }
                    ?: Season(episode.metadata.season ?: 0).also { seasons.add(it) }
            season.episodes.add(episode)
        }
        return seasons.asSequence().mapNotNull { season ->
            season.episodes.sortBy { episode -> episode.metadata.episode }
            season.episodes.asSequence().firstOrNull { episode ->
                if (episode.media == null) episode.media = episode.metadata.mlId?.let { medialibrary.getMedia(it) }
                episode.media?.let { media -> media.seen < 1 } == true
            }
        }.firstOrNull()
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
        val mediaMetadataRepository = MediaMetadataRepository.getInstance(context)
        return mediaMetadataRepository.getTvshow(id)?.let { tvShow ->
            getAllSeasons(tvShow).flatMap { it.episodes }
        } ?: emptyList()
    }

    fun getAllMedias(seasons: List<Season>?): List<MediaWrapper> {
        return seasons?.flatMap { it.episodes }?.mapNotNull { it.media } ?: emptyList()
    }

    companion object {
        fun getProviders() = listOf(ResumeResolver, TvShowResolver)
    }
}

private object ResumeResolver : IMediaContentResolver {
    override val prefix = CONTENT_RESUME
    override suspend fun getList(context: Context, id: String): Pair<List<MediaWrapper>, Int>? {
        val provider = MediaScrapingTvshowProvider(context)
        return withContext(Dispatchers.IO) { Pair(provider.getResumeMediasById(id.substringAfter(prefix)), 0) }
    }
}

private object TvShowResolver : IMediaContentResolver {
    override val prefix = CONTENT_EPISODE
    override suspend fun getList(context: Context, id: String): Pair<List<MediaWrapper>, Int>? {
        val provider = MediaScrapingTvshowProvider(context)
        val moviepediaId = id.substringAfter(prefix)
        return withContext(Dispatchers.IO) { provider.getShowIdForEpisode(moviepediaId)?.let { provider.getAllEpisodesForShow(it) } }?.let {
            Pair(it.mapNotNull { episode -> episode.media }, it.indexOfFirst { it.metadata.moviepediaId == moviepediaId })
        }
    }
}
