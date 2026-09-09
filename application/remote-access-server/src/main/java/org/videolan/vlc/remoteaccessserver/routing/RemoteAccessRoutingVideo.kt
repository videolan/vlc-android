/*
 * ************************************************************************
 *  RemoteAccessRoutingVideo.kt
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
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.resources.util.getFromMl
import org.videolan.vlc.providers.medialibrary.sanitizeGroups
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.utils.serveVideos

fun Route.videoRouting(appContext: Context, settings: SharedPreferences) {
    // List of all the videos
    get("/video-list") {
        if (!checkPermission(settings) { settings.serveVideos(appContext) }) return@get
        val grouping = call.request.queryParameters["grouping"]?.toInt() ?: 0
        val groupId = call.request.queryParameters["group"]?.toLong() ?: 0L
        val folderId = call.request.queryParameters["folder"]?.toLong() ?: 0L
        var groupTitle = ""
        val videos = appContext.getFromMl {
            if (groupId != 0L) {
                val result = getVideoGroup(groupId)?.let { group ->
                    groupTitle = group.title
                    group.media(Medialibrary.SORT_DEFAULT, false, false, false, group.mediaCount(), 0)
                }
                result
            } else if (folderId != 0L) {
                val result = getFolder(Folder.TYPE_FOLDER_VIDEO, folderId)?.let { folder ->
                    groupTitle = folder.title
                    folder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, false, false, false, folder.mediaCount(Folder.TYPE_FOLDER_VIDEO), 0)
                }
                result
            } else when (grouping) {
                0 -> getVideos(Medialibrary.SORT_DEFAULT, false, false, false)
                1 -> getFolders(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, false, false, false, 100000, 0)
                else -> getVideoGroups(Medialibrary.SORT_DEFAULT, false, false, false, 100000, 0).sanitizeGroups()
            }
        }
        if (videos == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(videos.size)
        videos.forEach { video ->
            when (video) {
                is MediaWrapper -> list.add(video.toPlayQueueItem())
                is Folder -> list.add(video.toPlayQueueItem(appContext))
                is VideoGroup -> list.add(video.toPlayQueueItem(appContext))
            }

        }
        val result = RemoteAccessServer.VideoListResult(list, groupTitle)
        call.respondJson(convertToJson(result))
    }
}
