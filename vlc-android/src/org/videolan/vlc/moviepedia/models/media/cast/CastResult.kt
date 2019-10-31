/*
 * ************************************************************************
 *  CastResult.kt
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

package org.videolan.vlc.moviepedia.models.media.cast

import com.squareup.moshi.Json

data class CastResult(
        @field:Json(name = "actor")
        val actor: List<Actor>?,
        @field:Json(name = "director")
        val director: List<Director>?,
        @field:Json(name = "musician")
        val musician: List<Musician>?,
        @field:Json(name = "producer")
        val producer: List<Producer>?,
        @field:Json(name = "writer")
        val writer: List<Writer>?
)