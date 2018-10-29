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

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.models.ExternalSub
import org.videolan.vlc.database.ExternalSubDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.gui.dialogs.State
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.util.LiveDataMap
import java.io.File

class ExternalSubRepository(private val externalSubDao: ExternalSubDao ) {

    private var _downloadingSubtitles = LiveDataMap<Long, SubtitleItem>()

    val downloadingSubtitles: LiveData<Map<Long, SubtitleItem>>
        get() = _downloadingSubtitles as LiveData<Map<Long, SubtitleItem>>

    fun saveDownloadedSubtitle(idSubtitle: String, subtitlePath: String, mediaPath: String, language: String, movieReleaseName: String): Job {
        return GlobalScope.launch(Dispatchers.IO) { externalSubDao.insert(ExternalSub(idSubtitle, subtitlePath, mediaPath, language, movieReleaseName)) }
    }

    fun getDownloadedSubtitles(mediaPath: String): LiveData<List<ExternalSub>> {
        val externalSubs = externalSubDao.get(mediaPath)
        return Transformations.map(externalSubs) {
            val existExternalSubs: MutableList<ExternalSub> = mutableListOf()
            it.forEach {
                if (File(Uri.decode(it.subtitlePath)).exists())
                    existExternalSubs.add(it)
                else
                    deleteSubtitle(it.mediaPath, it.idSubtitle)
            }
            existExternalSubs
        }
    }

    fun deleteSubtitle(mediaPath: String, idSubtitle: String) {
        GlobalScope.launch { externalSubDao.delete(mediaPath, idSubtitle) }
    }

    fun addDownloadingItem(key: Long, item: SubtitleItem) {
        _downloadingSubtitles.add(key, item.copy(state = State.Downloading))
    }

    fun removeDownloadingItem(key: Long) {
        _downloadingSubtitles.remove(key)
    }

    fun getDownloadingSubtitle(key: Long) = _downloadingSubtitles.get(key)

    companion object : SingletonHolder<ExternalSubRepository, Context>({ ExternalSubRepository(MediaDatabase.getInstance(it).externalSubDao()) })
}