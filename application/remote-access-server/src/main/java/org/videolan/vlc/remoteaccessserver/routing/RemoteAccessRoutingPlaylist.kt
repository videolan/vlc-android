/*
 * ************************************************************************
 *  RemoteAccessRoutingPlaylist.kt
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
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.resources.util.getFromMl
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.getPlaylistByName
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.RemoteAccessSession.verifyLogin
import org.videolan.vlc.remoteaccessserver.utils.servePlaylists

private const val TAG = "RARoutingPlaylist"

fun Route.playlistRouting(appContext: Context, settings: SharedPreferences) {
    // List of all the playlists
    get("/playlist-list") {
        verifyLogin(settings)
        if (!settings.servePlaylists(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val playlists = appContext.getFromMl { getPlaylists(Playlist.Type.All, false) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(playlists.size)
        playlists.forEach { playlist ->
            list.add(playlist.toPlayQueueItem(appContext))
        }
        call.respondJson(convertToJson(list))
    }
    // Get an playlist details
    get("/playlist") {
        verifyLogin(settings)
        if (!settings.servePlaylists(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val id = call.request.queryParameters["id"]?.toLong() ?: 0L

        val playlist = appContext.getFromMl { getPlaylist(id, false, false) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(playlist.tracksCount)
        playlist.tracks.forEach { track ->
            list.add(track.toPlayQueueItem(defaultArtist = if (track.type == MediaWrapper.TYPE_AUDIO) appContext.getString(R.string.unknown_artist) else "").apply {
                if (track.type == MediaWrapper.TYPE_VIDEO) fileType = "video"
            })
        }
        val result = RemoteAccessServer.PlaylistResult(list, playlist.title)
        call.respondJson(convertToJson(result))
    }
    // Create a new playlist
    post("/playlist-create") {
        verifyLogin(settings)
        if (!settings.servePlaylists(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        val formParameters = try {
            call.receiveParameters()
        } catch (e: Exception) {
            null
        }

        val name = formParameters?.get("name") ?: run {
            call.respond(HttpStatusCode.NoContent)
            return@post
        }
        val created = appContext.getFromMl {
            if (getPlaylistByName(name as String) == null) {
                createPlaylist(name, true, false)
                true
            } else {
                false
            }
        }
        if (!created)
            call.respond(HttpStatusCode.Conflict, appContext.getString(R.string.playlist_existing, name))
        else
            call.respondText("")
    }
    // Add a media to playlists
    post("/playlist-add") {
        verifyLogin(settings)
        if (!settings.servePlaylists(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        val formParameters = try {
            call.receiveParameters()
        } catch (e: Exception) {
            null
        }

        val mediaId = formParameters?.get("mediaId")?.toLong()
        val mediaType = formParameters?.get("mediaType")
        val playlists = formParameters?.getAll("playlists[]") ?: emptyList<String>()
        if (mediaId == null || mediaType == null) {
            call.respond(HttpStatusCode.NoContent)
            return@post
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "mediaId: $mediaId, mediaType: $mediaType, playlists: $playlists")

        val medias = appContext.getFromMl {
            when (mediaType) {
                "album" -> getAlbum(mediaId).tracks
                "artist" -> getArtist(mediaId).tracks
                "genre" -> getGenre(mediaId).tracks
                "video-group" -> {
                    val group = getVideoGroup(mediaId)
                    group.media(Medialibrary.SORT_DEFAULT, false, false, false, group.mediaCount(), 0)
                }
                "video-folder" -> {
                    val folder = getFolder(Folder.TYPE_FOLDER_VIDEO, mediaId)
                    folder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, false, false, false, folder.mediaCount(Folder.TYPE_FOLDER_VIDEO), 0)
                }
                else -> arrayOf(getMedia(mediaId))
            }
        } ?: run {
            call.respond(HttpStatusCode.NoContent)
            return@post
        }
        appContext.getFromMl {
            playlists.forEach {
                val playlist = getPlaylist(it.toLong(), true, false)
                medias.forEach {
                    playlist.append(it.id)
                }
            }

        }

        call.respondText("")
    }
}
