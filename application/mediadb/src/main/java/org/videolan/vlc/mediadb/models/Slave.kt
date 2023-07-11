/*******************************************************************************
 *  Slave.kt
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

package org.videolan.vlc.mediadb.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "SLAVES_table", primaryKeys = ["slave_media_mrl", "slave_uri"])
data class Slave (
    @ColumnInfo(name = "slave_media_mrl")
    val mediaPath: String,
    @ColumnInfo(name = "slave_type")
    val type: Int,
    @ColumnInfo(name = "slave_priority")
    val priority:Int,
    @ColumnInfo(name = "slave_uri")
    val uri: String
)

