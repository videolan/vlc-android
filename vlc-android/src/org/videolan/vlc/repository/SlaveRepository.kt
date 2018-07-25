/*******************************************************************************
 *  SlaveRepository.kt
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
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.Media
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.database.SlaveDao
import org.videolan.vlc.database.models.Slave
import org.videolan.vlc.util.VLCIO

class SlaveRepository @JvmOverloads constructor(context: Context,
                       val mediaDatabase:MediaDatabase = MediaDatabase.getDatabase(context),
                       val slaveDao:SlaveDao = mediaDatabase.slaveDao()
){

    fun saveSlave(mediaPath: String, type: Int, priority: Int, uriString: String): Job {
        return launch(VLCIO) {
            slaveDao.insert(Slave(mediaPath, type, priority, uriString))
        }
    }

    fun saveSlaves(mw: MediaWrapper): List<Job>? {
        return mw.slaves?.let{
            it.map {
                saveSlave(mw.location, it.type, it.priority, it.uri)
            }
        }
    }

    suspend fun getSlaves(mrl: String): List<Media.Slave> {
        return withContext(VLCIO) {
            val slaves = slaveDao.get(mrl)
            val mediaSlaves = slaves.map {
                var uri = it.uri
                if (uri.isNotEmpty())
                    uri = Uri.decode(it.uri)
                Media.Slave(it.type, it.priority, uri)
            }
             mediaSlaves
        }
    }
}
