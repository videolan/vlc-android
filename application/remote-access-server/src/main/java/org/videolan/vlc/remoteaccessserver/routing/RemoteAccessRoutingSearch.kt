/*
 * ************************************************************************
 *  RemoteAccessRoutingSearch.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.vlc.remoteaccessserver.routing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.getFromMl
import org.videolan.tools.REMOTE_ACCESS_HISTORY_CONTENT
import org.videolan.tools.Settings
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.RemoteAccessSession.verifyLogin
import org.videolan.vlc.remoteaccessserver.utils.serveSearch

private const val TAG = "RARoutingSearch"

fun Route.searchRouting(appContext: Context, settings: SharedPreferences) {
    // Search media
    get("/search") {
        verifyLogin(settings)
        if (!settings.serveSearch(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        call.request.queryParameters["search"]?.let { query ->
            val searchAggregate = appContext.getFromMl { search(query, Settings.includeMissing, false) }

            val results = searchAggregate?.let { result ->
                RemoteAccessServer.SearchResults(
                    result.albums?.filterNotNull()?.map { it.toPlayQueueItem() }
                        ?: listOf(),
                    result.artists?.filterNotNull()?.map { it.toPlayQueueItem(appContext) }
                        ?: listOf(),
                    result.genres?.filterNotNull()?.map { it.toPlayQueueItem(appContext) }
                        ?: listOf(),
                    result.playlists?.filterNotNull()?.map { it.toPlayQueueItem(appContext) }
                        ?: listOf(),
                    result.videos?.filterNotNull()?.map { it.toPlayQueueItem() }
                        ?: listOf(),
                    result.tracks?.filterNotNull()?.map { it.toPlayQueueItem() }
                        ?: listOf(),
                )
            } ?: RemoteAccessServer.SearchResults(listOf(), listOf(), listOf(), listOf(), listOf(), listOf())
            call.respondJson(convertToJson(results))
            return@get
        }
        call.respondJson(convertToJson(RemoteAccessServer.SearchResults(listOf(), listOf(), listOf(), listOf(), listOf(), listOf())))
    }

    get("/history") {
        verifyLogin(settings)
        if (!settings.getBoolean(REMOTE_ACCESS_HISTORY_CONTENT, false)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val list = try {
            withContext(Dispatchers.Default) {
                appContext.getFromMl {
                    history(
                        Medialibrary.HISTORY_TYPE_LOCAL).toMutableList().map { it.toPlayQueueItem(" ").apply {
                        if (it.type == MediaWrapper.TYPE_VIDEO) fileType = "video"
                    } }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            call.respond(HttpStatusCode.InternalServerError)
            return@get
        }
        call.respondJson(convertToJson(list))
    }

    get("/stream-list") {
        verifyLogin(settings)
        val stream = appContext.getFromMl {
            history(Medialibrary.HISTORY_TYPE_NETWORK)
        }
        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(stream.size)
        stream.forEachIndexed { index, mediaLibraryItem ->
            list.add(RemoteAccessServer.PlayQueueItem(mediaLibraryItem.id, mediaLibraryItem.title, " ", 0, mediaLibraryItem.artworkMrl
                ?: "", false, "", (mediaLibraryItem as MediaWrapper).uri.toString(), true, favorite = mediaLibraryItem.isFavorite))
        }
        call.respondJson(convertToJson(list))
    }
}
