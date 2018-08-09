/*******************************************************************************
 *  ExternalSubRepository.kt
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
import android.net.Uri
import android.support.annotation.WorkerThread
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.models.ExternalSub
import org.videolan.vlc.database.ExternalSubDao
import org.videolan.vlc.database.MediaDatabase
import java.io.File


class ExternalSubRepository(private val externalSubDao: ExternalSubDao ) {
    fun saveSubtitle(path: String, mediaName: String) {
        externalSubDao.insert(ExternalSub(path, mediaName))
    }

    @WorkerThread
    fun getSubtitles(mediaName: String): List<String> {
        val externalSubs = externalSubDao.get(mediaName)
        val existExternalSubs: MutableList<String> = mutableListOf()

        externalSubs.map {
            if (File(Uri.decode(it.uri)).exists()) existExternalSubs.add(it.uri)
            else externalSubDao.delete(it)
        }
        return existExternalSubs
    }

    companion object : SingletonHolder<ExternalSubRepository, Context>({ ExternalSubRepository(MediaDatabase.getInstance(it).externalSubDao()) })
}