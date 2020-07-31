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

package org.videolan.moviepedia.models.media

import androidx.core.net.toUri
import com.squareup.moshi.Json
import org.videolan.moviepedia.models.identify.Images
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
    "${imageEndpoint}img${it.path}".toUri()
}

fun MediaResult.getYear() = date?.let {
    SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
}

data class Medias(
        @field:Json(name = "phrases")
        val phrases: List<Phrase>,
        @field:Json(name = "resultCount")
        val resultCount: Int,
        @field:Json(name = "results")
        val results: List<MediaResult>,
        @field:Json(name = "total")
        val total: Int
)

data class MoviepediaResults(
        @field:Json(name = "medias")
        val medias: Medias,
        @field:Json(name = "persons")
        val persons: Persons
)

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

data class Persons(
        @field:Json(name = "phrases")
        val phrases: List<Phrase>,
        @field:Json(name = "resultCount")
        val resultCount: Int,
        @field:Json(name = "results")
        val results: List<PersonResult>,
        @field:Json(name = "total")
        val total: Int
)

data class Phrase(
        @field:Json(name = "highlighted")
        val highlighted: String,
        @field:Json(name = "text")
        val text: String
)