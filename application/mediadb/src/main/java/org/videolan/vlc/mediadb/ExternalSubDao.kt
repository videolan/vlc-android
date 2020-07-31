/*******************************************************************************
 *  ExternalSubDao.kt
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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.videolan.vlc.mediadb.models.ExternalSub

@Dao
interface ExternalSubDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(externalSub: ExternalSub)

    @Query("DELETE FROM external_subtitles_table WHERE idSubtitle = :idSubtitle and mediaPath = :mediaPath")
    fun delete(mediaPath: String, idSubtitle: String)

    @Query("SELECT * from external_subtitles_table where mediaPath = :mediaPath")
    fun get(mediaPath: String): LiveData<List<ExternalSub>>
}
