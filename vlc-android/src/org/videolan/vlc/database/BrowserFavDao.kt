/*******************************************************************************
 *  BrowserFavDao.kt
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

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import android.net.Uri
import org.videolan.vlc.database.models.BrowserFav


@Dao
interface BrowserFavDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(browserFav: BrowserFav)

    @Query("SELECT * FROM fav_table where uri = :uri")
    fun get(uri: Uri): List<BrowserFav>

    @Query("SELECT * from fav_table")
    fun getAll(): List<BrowserFav>

    @Query("SELECT * from fav_table where type = 0")
    fun getAllNetwrokFavs(): List<BrowserFav>

    @Query("SELECT * from fav_table where type = 1")
    fun getAllLocalFavs(): List<BrowserFav>

    @Query("DELETE from fav_table where uri = :uri")
    fun delete(uri: Uri)

}