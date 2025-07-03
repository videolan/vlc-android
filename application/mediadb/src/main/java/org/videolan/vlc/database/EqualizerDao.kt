/*
 * ************************************************************************
 *  EqualizerDao.kt
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

package org.videolan.vlc.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.videolan.vlc.mediadb.models.EqualizerBand
import org.videolan.vlc.mediadb.models.EqualizerEntry
import org.videolan.vlc.mediadb.models.EqualizerWithBands

@Dao
interface EqualizerDao {
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(equalizerEntry: EqualizerEntry): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBands(equalizerBand: EqualizerBand)

    @Delete
    fun delete(equalizerEntry: EqualizerEntry)

    @Transaction
    @Query("SELECT * FROM equalizer_entry ORDER BY preset_index ASC")
    fun getAllEqualizerEntries(): Flow<List<EqualizerWithBands>>

    @Transaction
    @Query("SELECT * FROM equalizer_entry ORDER BY preset_index ASC")
    fun getAllEqualizerEntriesSync(): List<EqualizerWithBands>

    @Transaction
    @Query("SELECT * FROM equalizer_entry WHERE is_disabled = 0 ORDER BY preset_index ASC LIMIT 1")
    fun getFirstEqualizerEntry(): EqualizerWithBands

    @Query("SELECT * FROM equalizer_entry WHERE id = :id")
    fun getCurrentEqualizer(id: Long): EqualizerWithBands?

    @Query("SELECT * FROM equalizer_entry WHERE preset_index == -1")
    fun getCustomEqualizers(): List<EqualizerWithBands>

    @Query("SELECT * FROM equalizer_entry WHERE preset_index != -1")
    fun getDefaultEqualizers(): List<EqualizerWithBands>

    @Query("SELECT * FROM equalizer_entry WHERE name = :name")
    fun getByName(name: String): EqualizerWithBands

}
