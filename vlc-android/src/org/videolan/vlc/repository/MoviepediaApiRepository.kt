/*
 * ************************************************************************
 *  NextApiRepository.kt
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

package org.videolan.vlc.repository

import org.videolan.vlc.moviepedia.IMoviepediaApiService
import org.videolan.vlc.moviepedia.NextApiClient
import org.videolan.vlc.moviepedia.models.body.ScrobbleBody
import org.videolan.vlc.moviepedia.models.identify.IdentifyResult
import org.videolan.vlc.moviepedia.models.identify.Media
import org.videolan.vlc.moviepedia.models.media.MoviepediaResults
import org.videolan.vlc.moviepedia.models.media.cast.CastResult

class MoviepediaApiRepository(private val moviepediaApiService: IMoviepediaApiService) {

    suspend fun search(query: String): MoviepediaResults {
        return moviepediaApiService.search(query = query)
    }

    suspend fun searchMedia(query: ScrobbleBody): IdentifyResult {
        return moviepediaApiService.searchMedia(query)
    }

    suspend fun getMedia(mediaId: String): Media {
        return moviepediaApiService.getMedia(mediaId)
    }

    suspend fun getMediaCast(mediaId: String): CastResult {
        return moviepediaApiService.getMediaCast(mediaId)
    }

    companion object {
        fun getInstance() = MoviepediaApiRepository(NextApiClient.instance)
    }
}