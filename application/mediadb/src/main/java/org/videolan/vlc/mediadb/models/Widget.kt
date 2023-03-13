/*
 * ************************************************************************
 *  Widget.kt
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

package org.videolan.vlc.mediadb.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_table")
data class Widget(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val widgetId: Int,
        @ColumnInfo(name = "width")
        var width: Int,
        @ColumnInfo(name = "height")
        var height: Int,
        @ColumnInfo(name = "theme")
        var theme: Int,
        @ColumnInfo(name = "type")
        var type: Int,
        @ColumnInfo(name = "light_theme")
        var lightTheme: Boolean,
        @ColumnInfo(name = "background_color")
        var backgroundColor: Int,
        @ColumnInfo(name = "foreground_color")
        var foregroundColor: Int,
        @ColumnInfo(name = "forward_delay")
        var forwardDelay: Int,
        @ColumnInfo(name = "rewind_delay")
        var rewindDelay: Int,
        @ColumnInfo(name = "opacity")
        var opacity: Int,
        @ColumnInfo(name = "show_configure")
        var showConfigure: Boolean,
        @ColumnInfo(name = "show_seek")
        var showSeek: Boolean,
        @ColumnInfo(name = "show_cover")
        var showCover: Boolean
)
