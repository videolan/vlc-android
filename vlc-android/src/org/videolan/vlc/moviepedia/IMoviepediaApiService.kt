/*
 * ************************************************************************
 *  INextApiService.kt
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

package org.videolan.vlc.moviepedia

import org.videolan.vlc.moviepedia.models.body.ScrobbleBody
import org.videolan.vlc.moviepedia.models.identify.IdentifyResult
import org.videolan.vlc.moviepedia.models.identify.Media
import org.videolan.vlc.moviepedia.models.media.MoviepediaResults
import org.videolan.vlc.moviepedia.models.media.cast.CastResult
import retrofit2.http.*

interface IMoviepediaApiService {
    @GET("search")
    suspend fun search(@Query("count") count: Int = 20, @Query("q") query: String): MoviepediaResults

    @POST("search-media/identify")
    suspend fun searchMedia(@Body body: ScrobbleBody): IdentifyResult

    @GET("media/{media}")
    suspend fun getMedia(@Path("media") mediaId: String): Media

    @GET("media/{media}/cast")
    suspend fun getMediaCast(@Path("media") mediaId: String): CastResult
}