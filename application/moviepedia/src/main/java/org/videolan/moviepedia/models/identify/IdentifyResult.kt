/*
 * ************************************************************************
 *  IdentifyResult.kt
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

package org.videolan.moviepedia.models.identify

import android.net.Uri
import com.squareup.moshi.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Comparator

data class IdentifyResult(
        @field:Json(name = "lucky")
        val lucky: Media?,
        @field:Json(name = "results")
        val results: List<Media>
)

fun IdentifyResult.getAllResults() = mutableListOf(lucky).apply { addAll(results) }.distinctBy { media -> media?.mediaId }.filterNotNull()

data class Backdrop(
        @field:Json(name = "language")
        val language: String,
        @field:Json(name = "path")
        val path: String,
        @field:Json(name = "ratio")
        val ratio: Double
)

data class Externalids(
        @field:Json(name = "imdb")
        val imdb: String,
        @field:Json(name = "themoviedb")
        val themoviedb: String
)

data class Images(
        @field:Json(name = "backdrops")
        val backdrops: List<Backdrop>,
        @field:Json(name = "posters")
        val posters: List<Poster>
)

data class Media(
        @field:Json(name = "adult")
        val adult: Boolean,
        @field:Json(name = "budget")
        val budget: Int,
        @field:Json(name = "childrenId")
        val childrenId: List<Any>,
        @field:Json(name = "country")
        val country: List<String>?,
        @field:Json(name = "date")
        val date: Date?,
        @field:Json(name = "externalids")
        val externalids: Externalids,
        @field:Json(name = "followed")
        val followed: Boolean,
        @field:Json(name = "genre")
        val genre: List<String>?,
        @field:Json(name = "globalRating")
        val globalRating: Int,
        @field:Json(name = "imageEndpoint")
        val imageEndpoint: String,
        @field:Json(name = "images")
        val images: Images?,
        @field:Json(name = "language")
        val language: List<String>,
        @field:Json(name = "mediaId")
        val mediaId: String,
        @field:Json(name = "nbFollower")
        val nbFollower: String,
        @field:Json(name = "nbWatches")
        val nbWatches: String,
        @field:Json(name = "rating")
        val rating: Int,
        @field:Json(name = "runtime")
        val runtime: Int,
        @field:Json(name = "status")
        val status: String,
        @field:Json(name = "summary")
        val summary: String?,
        @field:Json(name = "title")
        val title: String,
        @field:Json(name = "type")
        val mediaType: MediaType,
        @field:Json(name = "videos")
        val videos: List<Video>,
        @field:Json(name = "season")
        val season: Int,
        @field:Json(name = "episode")
        val episode: Int,
        @field:Json(name = "showTitle")
        val showTitle: String,
        @field:Json(name = "showId")
        val showId: String,
        @field:Json(name = "wished")
        val wished: Any
)

enum class MediaType {
        @Json(name = "tvshow")
        TV_SHOW,
        @Json(name = "tvseason")
        TV_SEASON,
        @Json(name = "tvepisode")
        TV_EPISODE,
        @Json(name = "movie")
        MOVIE
}

fun Media.getCardSubtitle() = if (mediaType == MediaType.TV_EPISODE) getShow() else getYear()

fun Media.getShow() = "$showTitle · S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"

fun Media.getYear() = date?.let {
        SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
}

fun Media.getPosters(languages: List<String>) = images?.posters?.sortedWith(Comparator { p0, p1 ->
        -(languages.indexOf(p0.language) - languages.indexOf(p1.language))
})

fun Media.getImageUri(languages: List<String>) = getPosters(languages)?.firstOrNull()?.let {
        Uri.parse(imageEndpoint + "img" + it.path)
}

fun Media.getBackdrops(languages: List<String>) = images?.backdrops?.sortedWith(Comparator { p0, p1 ->
        -(languages.indexOf(p0.language) - languages.indexOf(p1.language))
})

fun Media.getBackdropUri(languages: List<String>) = getBackdrops(languages)?.firstOrNull()?.let {
        Uri.parse(imageEndpoint + "img" + it.path)
}

fun Media.getImageUriFromPath(path: String) = imageEndpoint + "img" + path

data class Poster(
        @field:Json(name = "language")
        val language: String,
        @field:Json(name = "path")
        val path: String,
        @field:Json(name = "ratio")
        val ratio: Double
)

data class Video(
        @field:Json(name = "language")
        val language: String,
        @field:Json(name = "type")
        val type: String,
        @field:Json(name = "url")
        val url: String
)