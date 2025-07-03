/*
 * ************************************************************************
 *  EqualizerRepository.kt
 * *************************************************************************
 * Copyright © 2024 VLC authors and VideoLAN
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

package org.videolan.vlc.repository

import android.content.Context
import org.videolan.tools.CoroutineContextProvider
import org.videolan.tools.KEY_CURRENT_EQUALIZER_ID
import org.videolan.tools.Settings
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.database.EqualizerDao
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.mediadb.models.EqualizerEntry
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import androidx.core.content.edit
import androidx.lifecycle.asLiveData

class EqualizerRepository(private val equalizerDao: EqualizerDao, private val coroutineContextProvider: CoroutineContextProvider = CoroutineContextProvider()) {

    val equalizerEntriesUnfiltered by lazy {
            equalizerDao.getAllEqualizerEntries()
    }

    fun getCurrentEqualizer(context: Context) = equalizerDao.getCurrentEqualizer(Settings.getInstance(context).getLong(KEY_CURRENT_EQUALIZER_ID, 1L)) ?: equalizerDao.getFirstEqualizerEntry().also {
        Settings.getInstance(context).edit { putLong(KEY_CURRENT_EQUALIZER_ID, it.equalizerEntry.id) }
    }

    fun isNameAllowed(name: String): Boolean {
        return name.isNotBlank() && !equalizerDao.getAllEqualizerEntriesSync().any { it.equalizerEntry.name == name }
    }

    /**
     * Add or update equalizer with bands
     *
     * @param context the context used to create the database transaction
     * @param equalizer the equalizer to add or update
     * @return the id of the created/updated equalizer
     */
    fun addOrUpdateEqualizerWithBands(context: Context, equalizer:EqualizerWithBands):Long {
        var id = -1L
        MediaDatabase.getInstance(context).runInTransaction {
            id = equalizerDao.insert(equalizer.equalizerEntry)
            equalizer.bands.forEach {
                it.equalizerEntry = id
                equalizerDao.insertBands(it)
            }
        }
        return id
    }

    fun delete(equalizerEntry: EqualizerEntry) {
        equalizerDao.delete(equalizerEntry)
    }

    fun getCustomEqualizers(): List<EqualizerWithBands> = equalizerDao.getCustomEqualizers()

    fun getDefaultEqualizers(): List<EqualizerWithBands> = equalizerDao.getDefaultEqualizers()

    fun getByName(name: String): EqualizerWithBands = equalizerDao.getByName(name)

    companion object : SingletonHolder<EqualizerRepository, Context>({ EqualizerRepository(MediaDatabase.getInstance(it).equalizerDao()) })
}