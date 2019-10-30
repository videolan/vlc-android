/*
 * ************************************************************************
 *  MediaResult.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.next.models.media

import android.net.Uri
import com.squareup.moshi.Json
import java.text.SimpleDateFormat
import java.util.*

data class MediaResult(
        @field:Json(name = "date")
        val date: Date?,
        @field:Json(name = "followed")
        val followed: Boolean,
        @field:Json(name = "globalRating")
        val globalRating: Int,
        @field:Json(name = "imageEndpoint")
        val imageEndpoint: String,
        @field:Json(name = "images")
        val images: Images?,
        @field:Json(name = "mediaId")
        val mediaId: String,
        @field:Json(name = "showId")
        val showId: String,
        @field:Json(name = "showTitle")
        val showTitle: String,
        @field:Json(name = "summary")
        val summary: String,
        @field:Json(name = "title")
        val title: String,
        @field:Json(name = "type")
        val type: String,
        @field:Json(name = "wished")
        val wished: Any
)

fun MediaResult.getImageUri() = images?.posters?.firstOrNull()?.let {
    Uri.parse(imageEndpoint + "img" + it.path)
}

fun MediaResult.getYear() = date?.let {
    SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
}