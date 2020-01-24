/*
 * ************************************************************************
 *  MediaMetadataRepository.kt
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

/*******************************************************************************
 *  BrowserFavRepository.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
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
 ******************************************************************************/

package org.videolan.moviepedia.repository

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.sqlite.db.SimpleSQLiteQuery
import org.videolan.moviepedia.database.MoviePediaDatabase
import org.videolan.tools.IOScopedObject
import org.videolan.tools.SingletonHolder
import org.videolan.moviepedia.database.MediaImageDao
import org.videolan.moviepedia.database.MediaMetadataDao
import org.videolan.moviepedia.database.MediaMetadataDataFullDao
import org.videolan.moviepedia.database.models.MediaImage
import org.videolan.moviepedia.database.models.MediaMetadata
import org.videolan.moviepedia.database.models.MediaMetadataType
import org.videolan.moviepedia.database.models.MediaMetadataWithImages

class MediaMetadataRepository(private val mediaMetadataFullDao: MediaMetadataDataFullDao, private val mediaMetadataDao: MediaMetadataDao, private val mediaImageDao: MediaImageDao) : IOScopedObject() {

    @WorkerThread
    fun addMetadataImmediate(mediaMetadata: MediaMetadata) = mediaMetadataDao.insert(mediaMetadata)

    @WorkerThread
    fun addImagesImmediate(images: List<MediaImage>) = mediaImageDao.insertAll(images)

    @WorkerThread
    fun deleteImages(images: List<MediaImage>) = mediaImageDao.deleteAll(images)

    @WorkerThread
    fun getMetadataLiveByML(mediaId: Long): LiveData<MediaMetadataWithImages?> = mediaMetadataFullDao.getMetadataLiveByML(mediaId)

    @WorkerThread
    fun findNextEpisode(showId: String, season: Int, episode: Int): MediaMetadataWithImages? = mediaMetadataFullDao.findNextEpisode(showId, season, episode)

    @WorkerThread
    fun getMetadataLive(mediaId: String): LiveData<MediaMetadataWithImages?> = mediaMetadataFullDao.getMediaLive(mediaId)

    @WorkerThread
    fun getEpisodesLive(showId: String): LiveData<List<MediaMetadataWithImages>> = mediaMetadataFullDao.getEpisodesLive(showId)

    @WorkerThread
    fun getMovieCount(): Int = mediaMetadataFullDao.getMovieCount()

    @WorkerThread
    fun getTvshowsCount(): Int = mediaMetadataFullDao.getTvshowsCount()

    @WorkerThread
    fun getMetadata(mediaId: Long): MediaMetadataWithImages? = mediaMetadataFullDao.getMedia(mediaId)

    @WorkerThread
    fun getMediaById(mediaId: String): MediaMetadataWithImages? = mediaMetadataFullDao.getMediaById(mediaId)

    fun getMoviePagedList(sortField: String, sortType: String, metadataType: MediaMetadataType): DataSource.Factory<Int, MediaMetadataWithImages> {
        val query = SimpleSQLiteQuery("SELECT * FROM media_metadata WHERE type = ${metadataType.key} ORDER BY $sortField $sortType")
        return mediaMetadataFullDao.getAllPaged(query)
    }

    fun getAllLive(): LiveData<List<MediaMetadataWithImages>> = mediaMetadataFullDao.getAllLive()

    fun getTvshow(showId: String) = mediaMetadataFullDao.getMediaById(showId)

    fun getTvshowLive(showId: String) = mediaMetadataFullDao.getMediaByIdLive(showId)

    fun getByIds(mlids: List<Long>) = mediaMetadataFullDao.getByIds(mlids)

    fun getRecentlyAdded() = mediaMetadataFullDao.getRecentlyAdded()

    fun searchMedia(sanitizedQuery: String) = mediaMetadataFullDao.searchMedia(sanitizedQuery)

    fun getTvShowEpisodes(tvshowId: String) = mediaMetadataFullDao.getTvShowEpisodes(tvshowId)

    companion object : SingletonHolder<MediaMetadataRepository, Context>({ MediaMetadataRepository(MoviePediaDatabase.getInstance(it).mediaMedataDataFullDao(), MoviePediaDatabase.getInstance(it).mediaMetadataDao(), MoviePediaDatabase.getInstance(it).mediaImageDao()) })
}
