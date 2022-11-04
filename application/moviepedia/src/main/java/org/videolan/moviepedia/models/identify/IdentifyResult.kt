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

import androidx.core.net.toUri
import com.squareup.moshi.Json
import org.videolan.moviepedia.models.resolver.ResolverImage
import org.videolan.moviepedia.models.resolver.ResolverMedia
import org.videolan.moviepedia.models.resolver.ResolverMediaType
import org.videolan.moviepedia.models.resolver.ResolverResult
import org.videolan.vlc.util.TextUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Comparator

data class IdentifyResult(
        @field:Json(name = "lucky")
        val lucky: MoviepediaMedia?,
        @field:Json(name = "results")
        val results: List<MoviepediaMedia>
) : ResolverResult() {
    override fun lucky() = lucky

    override fun results() = results
}

data class Backdrop(
        @field:Json(name = "language")
        val language: String,
        @field:Json(name = "path")
        val path: String,
        @field:Json(name = "ratio")
        val ratio: Double
) : ResolverImage() {
    override fun language() = language
    override fun path() = path
}

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

data class MoviepediaMedia(
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
) : ResolverMedia() {
    override fun mediaType() = mediaType.toResolverClass()
    override fun showId() = showId
    override fun mediaId() = mediaId
    override fun title() = title
    override fun summary() = summary ?: ""
    override fun genres() = genre?.joinToString { genre -> genre } ?: ""
    override fun date() = date
    override fun countries() = country?.joinToString { genre -> genre } ?: ""
    override fun season() = season
    override fun episode() = episode
    override fun year() = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)

    override fun imageUri(languages: List<String>) = retrieveImageUri(languages)
    override fun backdropUri(languages: List<String>) = getBackdropUri(languages)
    override fun getBackdrops(languages: List<String>) = retrieveBackdrops(languages)
    override fun getPosters(languages: List<String>) = retrievePosters(languages)
    override fun getImageUriFromPath(path: String) = retrieveImageUriFromPath(path)
    override fun getCardSubtitle() = if (mediaType == MediaType.TV_EPISODE) getShow() else year()
}

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

fun MediaType.toResolverClass(): ResolverMediaType = when (this) {
    MediaType.MOVIE -> ResolverMediaType.MOVIE
    MediaType.TV_EPISODE -> ResolverMediaType.TV_EPISODE
    MediaType.TV_SHOW -> ResolverMediaType.TV_SHOW
    else -> ResolverMediaType.TV_SEASON
}


fun MoviepediaMedia.getShow() = TextUtils.separatedString(showTitle, "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}")

fun MoviepediaMedia.retrievePosters(languages: List<String>) = images?.posters?.sortedWith(Comparator { p0, p1 ->
    -(languages.indexOf(p0.language) - languages.indexOf(p1.language))
})

fun MoviepediaMedia.retrieveImageUri(languages: List<String>) = getPosters(languages)?.firstOrNull()?.let {
    "${imageEndpoint}img${it.path()}".toUri()
}

fun MoviepediaMedia.retrieveBackdrops(languages: List<String>) = images?.backdrops?.sortedWith(Comparator { p0, p1 ->
    -(languages.indexOf(p0.language) - languages.indexOf(p1.language))
})

fun MoviepediaMedia.getBackdropUri(languages: List<String>) = getBackdrops(languages)?.firstOrNull()?.let {
    "${imageEndpoint}img${it.path()}".toUri()
}

fun MoviepediaMedia.retrieveImageUriFromPath(path: String) = imageEndpoint + "img" + path

data class Poster(
        @field:Json(name = "language")
        val language: String,
        @field:Json(name = "path")
        val path: String,
        @field:Json(name = "ratio")
        val ratio: Double
) : ResolverImage() {
    override fun language() = language
    override fun path() = path
}

data class Video(
        @field:Json(name = "language")
        val language: String,
        @field:Json(name = "type")
        val type: String,
        @field:Json(name = "url")
        val url: String
)