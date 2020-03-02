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

package org.videolan.moviepedia.repository

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.moviepedia.IMoviepediaApiService
import org.videolan.moviepedia.MoviepediaApiClient
import org.videolan.moviepedia.models.body.ScrobbleBody
import org.videolan.moviepedia.models.body.ScrobbleBodyBatch
import org.videolan.moviepedia.models.resolver.ResolverBatchResult
import org.videolan.moviepedia.models.resolver.ResolverResult
import org.videolan.tools.FileUtils
import java.io.File

class MoviepediaApiRepository(private val moviepediaApiService: IMoviepediaApiService) : MediaResolverApi() {

    override suspend fun searchMedia(uri: Uri): ResolverResult {
        val hash = withContext(Dispatchers.IO){ FileUtils.computeHash(File(uri.path)) }
        val scrobbleBody = ScrobbleBody(filename = uri.lastPathSegment, osdbhash = hash)
        return moviepediaApiService.searchMedia(scrobbleBody)
    }

    override suspend fun searchMediaBatch(uris: HashMap<Long, Uri>): List<ResolverBatchResult> {
        val body = ArrayList<ScrobbleBodyBatch>()
        uris.forEach { uri ->
            val hash = withContext(Dispatchers.IO) { FileUtils.computeHash(File(uri.value.path)) }
            val scrobbleBody = ScrobbleBody(filename = uri.value.lastPathSegment, osdbhash = hash)

            val scrobbleBodyBatch = ScrobbleBodyBatch(id = uri.key.toString(), metadata = scrobbleBody)
            body.add(scrobbleBodyBatch)
        }

        return moviepediaApiService.searchMediaBatch(body)
    }

    override suspend fun searchTitle(query: String) = moviepediaApiService.searchMedia(ScrobbleBody(title = query, filename = query))

    override suspend fun getMedia(showId: String) = moviepediaApiService.getMedia(showId)

    override suspend fun getMediaCast(resolverId: String) = moviepediaApiService.getMediaCast(resolverId)

    companion object {
        private val instance = MoviepediaApiRepository(MoviepediaApiClient.instance)
        fun getInstance() = instance
    }
}