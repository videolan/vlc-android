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

package org.videolan.vlc.repository

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.sqlite.db.SimpleSQLiteQuery
import org.videolan.tools.IOScopedObject
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.*
import org.videolan.vlc.database.models.MediaImage
import org.videolan.vlc.database.models.MediaMetadata
import org.videolan.vlc.database.models.MediaMetadataWithImages
import org.videolan.vlc.database.models.MediaTvshow

class MediaMetadataRepository(private val mediaMetadataFullDao: MediaMetadataDataFullDao, private val mediaMetadataDao: MediaMetadataDao, private val mediaImageDao: MediaImageDao, private val mediaTvshowDao: MediaTvshowDao) : IOScopedObject() {

    @WorkerThread
    fun addMetadataImmediate(mediaMetadata: MediaMetadata) = mediaMetadataDao.insert(mediaMetadata)

    @WorkerThread
    fun addImagesImmediate(images: List<MediaImage>) = mediaImageDao.insertAll(images)

    @WorkerThread
    fun deleteImages(images: List<MediaImage>) = mediaImageDao.deleteAll(images)

    @WorkerThread
    fun getMetadataLiveByML(mediaId: Long): LiveData<MediaMetadataWithImages?> = mediaMetadataFullDao.getMetadataLiveByML(mediaId)

    @WorkerThread
    fun getMetadataLive(mediaId: String): LiveData<MediaMetadataWithImages?> = mediaMetadataFullDao.getMediaLive(mediaId)

    @WorkerThread
    fun getMovieCount(): Int = mediaMetadataFullDao.getMovieCount()

    @WorkerThread
    fun getTvshowsCount(): Int = mediaTvshowDao.getTvshowsCount()

    @WorkerThread
    fun getMetadata(mediaId: Long): MediaMetadataWithImages? = mediaMetadataFullDao.getMedia(mediaId)

    @WorkerThread
    fun insertShow(show: MediaTvshow) = mediaTvshowDao.insert(show)

    fun getMoviePagedList(sortField: String, sortType: String): DataSource.Factory<Int, MediaMetadataWithImages> {
        val query = SimpleSQLiteQuery("SELECT * FROM media_metadata WHERE type = 0 ORDER BY $sortField $sortType")
        return mediaMetadataFullDao.getAllPaged(query)
    }

    fun getTvshowPagedList(sortField: String, sortType: String): DataSource.Factory<Int, MediaTvshow> {
        val query = SimpleSQLiteQuery("SELECT * FROM media_tv_show ORDER BY $sortField $sortType")
        return mediaTvshowDao.getAllPaged(query)
    }

    fun getTvshow(showId: String) = mediaTvshowDao.find(showId)

    companion object : SingletonHolder<MediaMetadataRepository, Context>({ MediaMetadataRepository(MediaDatabase.getInstance(it).mediaMedataDataFullDao(), MediaDatabase.getInstance(it).mediaMetadataDao(), MediaDatabase.getInstance(it).mediaImageDao(), MediaDatabase.getInstance(it).mediaTvshowDao()) })
}
