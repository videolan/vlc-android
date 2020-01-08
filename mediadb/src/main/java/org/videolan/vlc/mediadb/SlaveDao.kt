/*******************************************************************************
 *  SlaveDao.kt
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

package org.videolan.vlc.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.videolan.vlc.mediadb.models.Slave

@Dao
interface SlaveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(slave: Slave)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(slaves: Array<Slave>)

    @Query("SELECT * from SLAVES_table where slave_media_mrl = :mrl")
    fun get(mrl: String): List<Slave>
}