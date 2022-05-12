/*
 * ************************************************************************
 *  WidgetDao.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.videolan.vlc.mediadb.models.Widget

@Dao
interface WidgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(widget: Widget)

    @Update
    fun update(widget:Widget)

    @Query("SELECT * FROM widget_table where id = :widgetId")
    fun get(widgetId: Int): Widget?


    @Query("SELECT * FROM widget_table where id = :widgetId")
    fun getFlow(widgetId: Int): Flow<Widget>

    @Query("SELECT * FROM widget_table")
    fun getAll(): List<Widget>

    @Query("DELETE from widget_table where id = :id")
    fun delete(id: Int)

}