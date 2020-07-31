/*
 * ************************************************************************
 *  MediaResolverApi.kt
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

package org.videolan.moviepedia.repository

import android.net.Uri
import org.videolan.moviepedia.models.resolver.ResolverBatchResult
import org.videolan.moviepedia.models.resolver.ResolverCasting
import org.videolan.moviepedia.models.resolver.ResolverMedia
import org.videolan.moviepedia.models.resolver.ResolverResult
import java.util.*

abstract class MediaResolverApi {
    abstract suspend fun searchMediaBatch(filesToIndex: HashMap<Long, Uri>): List<ResolverBatchResult>
    abstract suspend fun getMedia(showId: String): ResolverMedia
    abstract suspend fun searchTitle(query: String): ResolverResult
    abstract suspend fun getMediaCast(resolverId: String): ResolverCasting
    abstract suspend fun searchMedia(uri: Uri): ResolverResult
}