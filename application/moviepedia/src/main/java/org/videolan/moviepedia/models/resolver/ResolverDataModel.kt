/*
 * ************************************************************************
 *  ResolverBatchResult.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.moviepedia.models.resolver

import android.net.Uri
import java.util.*
import kotlin.collections.ArrayList

abstract class ResolverBatchResult {
    abstract fun getId(): Long
    abstract fun getMedia(): ResolverMedia?
}

abstract class ResolverResult {
    abstract fun lucky(): ResolverMedia?
    abstract fun results(): List<ResolverMedia>
    fun getAllResults(): List<ResolverMedia> = ArrayList(results()).apply { add(lucky()) }.toList()
}

abstract class ResolverCasting {
    abstract fun actors(): List<ResolverPerson>
    abstract fun directors(): List<ResolverPerson>
    abstract fun writers(): List<ResolverPerson>
    abstract fun musicians(): List<ResolverPerson>
    abstract fun producers(): List<ResolverPerson>
}

abstract class ResolverPerson {
    abstract fun name(): String
    abstract fun image(): String?
    abstract fun personId(): String
}

abstract class ResolverMedia {
    abstract fun mediaType(): ResolverMediaType
    abstract fun showId(): String
    abstract fun mediaId(): String
    abstract fun title(): String
    abstract fun summary(): String
    abstract fun genres(): String
    abstract fun date(): Date?
    abstract fun countries(): String
    abstract fun season(): Int?
    abstract fun episode(): Int?
    abstract fun year(): String?
    abstract fun imageUri(languages: List<String>): Uri?
    abstract fun backdropUri(languages: List<String>): Uri?
    abstract fun getBackdrops(languages: List<String>): List<ResolverImage>?
    abstract fun getPosters(languages: List<String>): List<ResolverImage>?
    abstract fun getImageUriFromPath(path: String): String
    abstract fun getCardSubtitle(): String?
}

abstract class ResolverImage {
    abstract fun language(): String
    abstract fun path(): String
}

enum class ResolverMediaType {
    TV_SHOW,
    TV_SEASON,
    TV_EPISODE,
    MOVIE
}