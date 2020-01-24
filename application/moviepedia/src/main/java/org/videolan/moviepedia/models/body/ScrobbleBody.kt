/*
 * ************************************************************************
 *  ScrobbleBody.kt
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

package org.videolan.moviepedia.models.body

data class ScrobbleBody(
        val osdbhash: String? = null,
        val infohash: String? = null,
        val imdbId: String? = null,
        val dvdId: String? = null,
        val title: String? = null,
        val alternativeTitles: String? = null,
        val filename: String? = null,
        val show: String? = null,
        val year: String? = null,
        val season: String? = null,
        val episode: String? = null,
        val duration: String? = null
)

data class ScrobbleBodyBatch(
        val id: String,
        val metadata: ScrobbleBody
)

