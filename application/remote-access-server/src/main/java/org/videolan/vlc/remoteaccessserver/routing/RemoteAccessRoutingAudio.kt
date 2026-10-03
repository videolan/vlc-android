/*
 * ************************************************************************
 *  RemoteAccessRoutingAudio.kt
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
import org.videolan.resources.util.getFromMl
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.vlc.R
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.RemoteAccessSession.verifyLogin
import org.videolan.vlc.remoteaccessserver.utils.serveAudios

fun Route.audioRouting(appContext: Context, settings: SharedPreferences) {
    // List of all the albums
    get("/album-list") {
        verifyLogin(settings)
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val albums = appContext.getFromMl { getAlbums(false, false) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(albums.size)
        albums.forEach { album ->
            list.add(album.toPlayQueueItem())
        }
        call.respondJson(convertToJson(list))
    }
    // List of all the artists
    get("/artist-list") {
        verifyLogin(settings)
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val artists = appContext.getFromMl { getArtists(settings.getBoolean(KEY_ARTISTS_SHOW_ALL, false), false, false) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(artists.size)
        artists.forEach { artist ->
            list.add(artist.toPlayQueueItem(appContext))
        }
        call.respondJson(convertToJson(list))
    }
    // List of all the audio tracks
    get("/track-list") {
        verifyLogin(settings)
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val tracks = appContext.getFromMl { getAudio(Medialibrary.SORT_DEFAULT, false, false, false) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(tracks.size)
        tracks.forEach { track ->
            list.add(track.toPlayQueueItem(defaultArtist = appContext.getString(R.string.unknown_artist)))
        }
        call.respondJson(convertToJson(list))
    }
    // List of all the audio genres
    get("/genre-list") {
        verifyLogin(settings)
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val genres = appContext.getFromMl { getGenres(false, false) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(genres.size)
        genres.forEach { genre ->
            list.add(genre.toPlayQueueItem(appContext))
        }
        call.respondJson(convertToJson(list))
    }
    // Get an album details
    get("/album") {
        verifyLogin(settings)
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val id = call.request.queryParameters["id"]?.toLong() ?: 0L

        val album = appContext.getFromMl { getAlbum(id) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(album.tracksCount)
        album.tracks.forEach { track ->
            list.add(track.toPlayQueueItem(album.albumArtist))
        }
        val result = RemoteAccessServer.AlbumResult(list, album.title)
        call.respondJson(convertToJson(result))
    }
    // Get a genre details
    get("/genre") {
        verifyLogin(settings)
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val id = call.request.queryParameters["id"]?.toLong() ?: 0L

        val genre = appContext.getFromMl { getGenre(id) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(genre.tracksCount)
        genre.tracks.forEach { track ->
            list.add(track.toPlayQueueItem())
        }
        val result = RemoteAccessServer.AlbumResult(list, genre.title)
        call.respondJson(convertToJson(result))
    }
    // Get an artist details
    get("/artist") {
        verifyLogin(settings)
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val id = call.request.queryParameters["id"]?.toLong() ?: 0L

        val artist = appContext.getFromMl { getArtist(id) }

        val list = ArrayList<RemoteAccessServer.PlayQueueItem>(artist.albumsCount)
        artist.albums.forEach { album ->
            list.add(album.toPlayQueueItem())
        }
        val result = RemoteAccessServer.ArtistResult(list, listOf(), artist.title)
        call.respondJson(convertToJson(result))
    }
}
