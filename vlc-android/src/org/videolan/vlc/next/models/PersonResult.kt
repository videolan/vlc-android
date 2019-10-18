/*
 * ************************************************************************
 *  ResultX.kt
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

package org.videolan.vlc.next.models

import com.squareup.moshi.Json
import java.util.*

data class PersonResult(
        @field:Json(name = "backdrop")
        val backdrop: String,
        @field:Json(name = "birthdate")
        val birthdate: Date,
        @field:Json(name = "deathdate")
        val deathdate: Date,
        @field:Json(name = "genre")
        val genre: String,
        @field:Json(name = "hasImage")
        val hasImage: Boolean,
        @field:Json(name = "imageEndpoint")
        val imageEndpoint: String,
        @field:Json(name = "imdbId")
        val imdbId: String,
        @field:Json(name = "imdbid_matched")
        val imdbidMatched: Boolean,
        @field:Json(name = "is_actor_of")
        val isActorOf: List<Any>,
        @field:Json(name = "is_director_of")
        val isDirectorOf: List<String>,
        @field:Json(name = "is_musician_of")
        val isMusicianOf: List<Any>,
        @field:Json(name = "is_producer_of")
        val isProducerOf: List<Any>,
        @field:Json(name = "is_starring_in")
        val isStarringIn: List<Any>,
        @field:Json(name = "is_writer_of")
        val isWriterOf: List<Any>,
        @field:Json(name = "name")
        val name: String,
        @field:Json(name = "personId")
        val personId: String,
        @field:Json(name = "picto")
        val picto: String,
        @field:Json(name = "poster")
        val poster: String,
        @field:Json(name = "square")
        val square: String
)