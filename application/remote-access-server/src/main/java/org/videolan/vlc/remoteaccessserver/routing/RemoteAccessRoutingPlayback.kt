/*
 * ************************************************************************
 *  RemoteAccessRoutingPlayback.kt
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
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.resources.util.getFromMl
import org.videolan.tools.KEY_CURRENT_AUDIO
import org.videolan.tools.KEY_CURRENT_MEDIA
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.awaitAppIsForegroung
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.ResumeStatus
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer

private const val TAG = "RARoutingPlayback"

fun Route.playbackRouting(appContext: Context, scope: CoroutineScope, settings: SharedPreferences) {
    // Resume playback
    get("/resume-playback") {
        val audio = call.request.queryParameters["audio"] == "true"
        val currentMediaKey = if (audio) KEY_CURRENT_AUDIO else KEY_CURRENT_MEDIA
        if (settings.getString(currentMediaKey, "")?.isNotEmpty() == false) {
            call.respond(HttpStatusCode.NoContent)
            return@get
        }
        MediaUtils.loadlastPlaylist(appContext, if (audio) PLAYLIST_TYPE_AUDIO else PLAYLIST_TYPE_VIDEO)
        call.respond(HttpStatusCode.OK)
    }
    // Play a media
    get("/play") {
        val type = call.request.queryParameters["type"] ?: "media"
        val append = call.request.queryParameters["append"] == "true"
        val asAudio = call.request.queryParameters["audio"] == "true"
        val path = call.request.queryParameters["path"]
        call.request.queryParameters["id"]?.let { id ->

            val medias = appContext.getFromMl {
                if (path?.isNotBlank() == true) {
                    val media = getMedia(path.toUri())
                    if (media != null)
                        arrayOf(media)
                    else
                        arrayOf(MLServiceLocator.getAbstractMediaWrapper(path.toUri()))
                } else when (type) {
                    "album" -> getAlbum(id.toLong()).tracks
                    "artist" -> getArtist(id.toLong()).tracks
                    "genre" -> getGenre(id.toLong()).tracks
                    "playlist" -> getPlaylist(id.toLong(), false, false).tracks
                    "video-group" -> {
                        val group = getVideoGroup(id.toLong())
                        group.media(Medialibrary.SORT_DEFAULT, false, false, false, group.mediaCount(), 0)
                    }
                    "video-folder" -> {
                        val folder = getFolder(Folder.TYPE_FOLDER_VIDEO, id.toLong())
                        folder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, false, false, false, folder.mediaCount(Folder.TYPE_FOLDER_VIDEO), 0)
                    }
                    else -> arrayOf(getMedia(id.toLong()))
                }
            }
            if (medias.isNullOrEmpty()) call.respond(HttpStatusCode.NotFound)
            else {
                if (medias.size == 1 && medias[0].id == RemoteAccessServer.getInstance(appContext).service?.currentMediaWrapper?.id && medias[0].uri == RemoteAccessServer.getInstance(appContext).service?.currentMediaWrapper?.uri) {
                    call.respond(HttpStatusCode.OK)
                    return@get
                }
                if (asAudio) medias[0].addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                if (medias[0].type == MediaWrapper.TYPE_VIDEO && !appContext.awaitAppIsForegroung() && settings.getString(KEY_VIDEO_APP_SWITCH, "0") != "1") {
                    call.respond(HttpStatusCode.Forbidden, appContext.getString(R.string.ra_not_in_foreground))
                    return@get
                }
                when {
                    append -> MediaUtils.appendMedia(appContext, medias)
                    else -> MediaUtils.openList(appContext, medias.toList(), 0)
                }
                call.respond(HttpStatusCode.OK)
            }
            return@get
        }
        call.respond(HttpStatusCode.NotFound)
    }
    get("/resume") {
        val resume = (call.request.queryParameters["resume"] ?: "true") == "true"
        val applyPlaylist = (call.request.queryParameters["applyPlaylist"] ?: "true") == "true"
        RemoteAccessServer.getInstance(appContext).service?.let { service ->
            service.playlistManager.waitForConfirmation.value?.let { confirmation ->
                scope.launch(Dispatchers.Main) {
                    if (resume) {
                        if (applyPlaylist) service.playlistManager.videoResumeStatus = ResumeStatus.ALWAYS
                        service.playlistManager.playIndex(
                            confirmation.index,
                            confirmation.flags,
                            forceResume = true
                        )
                    } else {
                        if (applyPlaylist) service.playlistManager.videoResumeStatus = ResumeStatus.NEVER
                        service.playlistManager.playIndex(
                            confirmation.index,
                            confirmation.flags,
                            forceRestart = true
                        )
                    }
                }
                service.playlistManager.waitForConfirmation.postValue(null)
            }
            service.playlistManager.waitForConfirmationAudio.value?.let { confirmation ->
                scope.launch(Dispatchers.Main) {
                    if (resume) {
                        if (applyPlaylist) service.playlistManager.audioResumeStatus = ResumeStatus.ALWAYS
                        service.playlistManager.playIndex(
                            confirmation.index,
                            confirmation.flags,
                            forceResume = true
                        )
                    } else {
                        if (applyPlaylist) service.playlistManager.audioResumeStatus = ResumeStatus.NEVER
                        service.playlistManager.playIndex(
                            confirmation.index,
                            confirmation.flags,
                            forceRestart = true
                        )
                    }
                }
                service.playlistManager.waitForConfirmationAudio.postValue(null)
            }
        }
        call.respond(HttpStatusCode.OK)
    }
    // Play a media
    get("/play-all") {
        val type = call.request.queryParameters["type"]
        val id = call.request.queryParameters["id"]
        type?.let { t ->

            val medias = if (t == "browser") {
                val path = call.request.queryParameters["path"] ?: kotlin.run {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val decodedPath = Uri.decode(path)

                val dataset = LiveDataset<MediaLibraryItem>()
                var list: Pair<List<MediaLibraryItem>, ArrayList<Pair<Int, String>>>? = null
                val provider = withContext(Dispatchers.Main) {
                    org.videolan.vlc.providers.FileBrowserProvider(appContext, dataset, decodedPath, false, false, Medialibrary.SORT_FILENAME, false)
                }
                try {
                    list = getMediaFromProvider(provider, dataset)
                } catch (e: Exception) {
                    Log.e(TAG, e.message, e)
                }
                list?.first?.map { it as MediaWrapper }?.toTypedArray()
            } else appContext.getFromMl {
                when (t) {
                    "video-group" -> {
                        id?.let {
                            val group = getVideoGroup(it.toLong())
                            group.media(Medialibrary.SORT_DEFAULT, false, false, false, group.mediaCount(), 0)
                        }
                    }
                    "video-folder" -> {
                        id?.let {
                            val folder = getFolder(Folder.TYPE_FOLDER_VIDEO, it.toLong())
                            folder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, false, false, false, folder.mediaCount(Folder.TYPE_FOLDER_VIDEO), 0)
                        }
                    }
                    "artist" -> {
                        id?.let {
                            val artist = getArtist(it.toLong())
                            artist.tracks
                        }
                    }
                    "album" -> {
                        id?.let {
                            val album = getAlbum(it.toLong())
                            album.tracks
                        }
                    }
                    "genre" -> {
                        id?.let {
                            val genre = getGenre(it.toLong())
                            genre.tracks
                        }
                    }
                    else -> getAudio(Medialibrary.SORT_DEFAULT, false, false, false)
                }
            }
            if (medias.isNullOrEmpty()) call.respond(HttpStatusCode.NotFound)
            else {
                if (medias.size == 1 && medias[0].id == RemoteAccessServer.getInstance(appContext).service?.currentMediaWrapper?.id) {
                    call.respond(HttpStatusCode.OK)
                    return@get
                }
                if (medias[0].type == MediaWrapper.TYPE_VIDEO && !appContext.awaitAppIsForegroung()) {
                    call.respond(HttpStatusCode.Forbidden, appContext.getString(R.string.ra_not_in_foreground))
                    return@get
                }
                MediaUtils.openList(appContext, medias.toList(), 0)
                call.respond(HttpStatusCode.OK)
            }
            return@get
        }
        call.respond(HttpStatusCode.NotFound)
    }
}
