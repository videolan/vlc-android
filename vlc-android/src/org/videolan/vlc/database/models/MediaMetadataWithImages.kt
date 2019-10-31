/*
 * ************************************************************************
 *  MediaMetadataWithImages.kt
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

package org.videolan.vlc.database.models

import androidx.room.Embedded
import androidx.room.Relation
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MediaMetadataWithImages {
    @Embedded
    lateinit var metadata: MediaMetadata

    @Relation(parentColumn = "show_id", entityColumn = "moviepedia_show_id", entity = MediaTvshow::class)
    lateinit var show: MediaTvshow

    @Relation(parentColumn = "ml_id", entityColumn = "media_id", entity = MediaImage::class)
    var images: List<MediaImage> = ArrayList()
}

fun MediaMetadataWithImages.subtitle(): String = if (metadata.type == 0) movieSubtitle() else tvshowSubtitle()
fun MediaMetadataWithImages.movieSubtitle(): String {

    val subtitle = ArrayList<String>()
    metadata.releaseDate?.let {
        subtitle.add(SimpleDateFormat("yyyy", Locale.getDefault()).format(it))
    }
    subtitle.add(metadata.genres)
    subtitle.add(metadata.countries)

    return subtitle.filter { it.isNotEmpty() }.joinToString(separator = " · ") { it }
}

fun MediaMetadataWithImages.tvshowSubtitle(): String {

    val subtitle = ArrayList<String>()
    metadata.releaseDate?.let {
        subtitle.add(SimpleDateFormat("yyyy", Locale.getDefault()).format(it))
    }
    subtitle.add(show.name)
    subtitle.add("S${metadata.season.toString().padStart(1, '0')}E${metadata.episode.toString().padStart(1, '0')}")

    return subtitle.filter { it.isNotEmpty() }.joinToString(separator = " · ") { it }
}
