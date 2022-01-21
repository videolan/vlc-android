/*
 * ************************************************************************
 *  MediaMetadata.kt
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

package org.videolan.moviepedia.database.models

import androidx.room.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.util.TextUtils
import java.text.SimpleDateFormat
import java.util.*

private const val MEDIA_METADATA_TABLE_NAME = "media_metadata"
private const val MEDIA_METADATA_IMAGE_TABLE_NAME = "media_metadata_image"
private const val MEDIA_METADATA_PERSON_TABLE_NAME = "media_metadata_person"
private const val MEDIA_METADATA_PERSON_JOIN_TABLE_NAME = "media_person_join"

@Entity(tableName = MEDIA_METADATA_TABLE_NAME, foreignKeys = [ForeignKey(entity = MediaMetadata::class, parentColumns = ["moviepedia_id"], childColumns = ["show_id"])])
data class MediaMetadata(
        @PrimaryKey
        @ColumnInfo(name = "moviepedia_id")
        val moviepediaId: String,
        @ColumnInfo(name = "ml_id")
        val mlId: Long?,
        @ColumnInfo(name = "type")
        val type: MediaMetadataType,
        @ColumnInfo(name = "title")
        val title: String,
        @ColumnInfo(name = "summary")
        val summary: String,
        @ColumnInfo(name = "genres")
        val genres: String,
        @ColumnInfo(name = "releaseDate")
        val releaseDate: Date?,
        @ColumnInfo(name = "countries")
        val countries: String,
        @ColumnInfo(name = "season")
        val season: Int?,
        @ColumnInfo(name = "episode")
        val episode: Int?,
        @ColumnInfo(name = "current_poster")
        var currentPoster: String,
        @ColumnInfo(name = "current_backdrop")
        var currentBackdrop: String,
        @ColumnInfo(name = "show_id")
        var showId: String?,
        @ColumnInfo(name = "has_cast")
        var hasCast: Boolean,
        @ColumnInfo(name = "insertDate")
        val insertDate: Date = Date()
)

fun MediaMetadata.getYear() = releaseDate?.let { SimpleDateFormat("yyyy", Locale.getDefault()).format(it) } ?: ""

class MediaMetadataWithImages {
    @Embedded
    lateinit var metadata: MediaMetadata

    @Relation(parentColumn = "show_id", entityColumn = "moviepedia_id", entity = MediaMetadata::class)
    lateinit var show: MediaMetadata

    @Ignore
    var media: MediaWrapper? = null

    @Relation(parentColumn = "moviepedia_id", entityColumn = "media_id", entity = MediaImage::class)
    var images: List<MediaImage> = ArrayList()
}

fun MediaMetadataWithImages.subtitle(): String = if (metadata.type == MediaMetadataType.MOVIE) movieSubtitle() else tvshowSubtitle()

fun MediaMetadataWithImages.movieSubtitle(): String {

    val subtitle = ArrayList<String>()
    metadata.releaseDate?.let {
        subtitle.add(SimpleDateFormat("yyyy", Locale.getDefault()).format(it))
    }
    subtitle.add(metadata.genres)
    subtitle.add(metadata.countries)

    return TextUtils.separatedString(subtitle.toTypedArray())
}

fun MediaMetadataWithImages.tvshowSubtitle(): String {

    val subtitle = ArrayList<String>()
    metadata.releaseDate?.let {
        subtitle.add(SimpleDateFormat("yyyy", Locale.getDefault()).format(it))
    }
    subtitle.add(show.title)
    subtitle.add("S${metadata.season.toString().padStart(2, '0')}E${metadata.episode.toString().padStart(2, '0')}")

    return TextUtils.separatedString(subtitle.toTypedArray())
}

fun MediaMetadataWithImages.tvEpisodeSubtitle(): String {
    return when (metadata.type) {
        MediaMetadataType.TV_EPISODE -> "S${metadata.season.toString().padStart(2, '0')}E${metadata.episode.toString().padStart(2, '0')}"
        else -> metadata.releaseDate?.let { SimpleDateFormat("yyyy", Locale.getDefault()).format(it) } ?: ""
    }
}

@Entity(tableName = MEDIA_METADATA_PERSON_JOIN_TABLE_NAME,
        primaryKeys = ["mediaId", "personId", "type"],
        foreignKeys = [ForeignKey(entity = MediaMetadata::class,
                parentColumns = arrayOf("moviepedia_id"),
                childColumns = arrayOf("mediaId")), ForeignKey(entity = Person::class,
                parentColumns = arrayOf("moviepedia_id"),
                childColumns = arrayOf("personId"))]
)
data class MediaPersonJoin(
        val mediaId: String,
        val personId: String,
        val type: PersonType
)

enum class PersonType(val key: Int) {
    ACTOR(0), DIRECTOR(1), MUSICIAN(2), PRODUCER(3), WRITER(4);

    companion object {
        fun fromKey(key: Int): PersonType {
            values().forEach { if (it.key == key) return it }
            return ACTOR
        }
    }
}

enum class MediaMetadataType(val key: Int) {
    MOVIE(0), TV_EPISODE(1), TV_SHOW(2);

    companion object {
        fun fromKey(key: Int): MediaMetadataType {
            values().forEach { if (it.key == key) return it }
            return MOVIE
        }
    }
}


@Entity(tableName = MEDIA_METADATA_PERSON_TABLE_NAME)
data class Person(
        @PrimaryKey
        @ColumnInfo(name = "moviepedia_id")
        val moviepediaId: String,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "image")
        val image: String?
)

@Entity(tableName = MEDIA_METADATA_IMAGE_TABLE_NAME, primaryKeys = ["url", "media_id"], foreignKeys = [ForeignKey(entity = MediaMetadata::class, parentColumns = ["moviepedia_id"], childColumns = ["media_id"])])
data class MediaImage(
        @ColumnInfo(name = "url")
        val url: String,
        @ColumnInfo(name = "media_id")
        val mediaId: String,
        @ColumnInfo(name = "image_type")
        val imageType: MediaImageType,
        @ColumnInfo(name = "image_language")
        val language: String
)

enum class MediaImageType(val key: Int) {
    BACKDROP(0), POSTER(1);

    companion object {
        fun fromKey(key: Int): MediaImageType {
            values().forEach { if (it.key == key) return it }
            return BACKDROP
        }
    }
}
