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
import org.videolan.tools.IOScopedObject
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.MediaImageDao
import org.videolan.vlc.database.MediaMetadataDao
import org.videolan.vlc.database.MediaMetadataDataFullDao
import org.videolan.vlc.database.models.MediaImage
import org.videolan.vlc.database.models.MediaMetadata
import org.videolan.vlc.database.models.MediaMetadataWithImages

class MediaMetadataRepository(private val mediaMetadataFullDao: MediaMetadataDataFullDao, private val mediaMetadataDao: MediaMetadataDao, private val mediaImageDao: MediaImageDao) : IOScopedObject() {

    @WorkerThread
    fun addMetadataImmediate(mediaMetadata: MediaMetadata) = mediaMetadataDao.insert(mediaMetadata)

    @WorkerThread
    fun addImagesImmediate(images: List<MediaImage>) = mediaImageDao.insertAll(images)

    @WorkerThread
    fun getMetadata(mediaId: Long): LiveData<MediaMetadataWithImages?> = mediaMetadataFullDao.getMedia(mediaId)

    companion object : SingletonHolder<MediaMetadataRepository, Context>({ MediaMetadataRepository(MediaDatabase.getInstance(it).mediaMedataDataFullDao(), MediaDatabase.getInstance(it).mediaMetadataDao(), MediaDatabase.getInstance(it).mediaImageDao()) })
}
