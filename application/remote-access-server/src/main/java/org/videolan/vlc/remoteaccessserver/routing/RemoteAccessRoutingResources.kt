/*
 * ************************************************************************
 *  RemoteAccessRoutingResources.kt
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
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.resources.util.getFromMl
import org.videolan.tools.HttpImageLoader
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.utils.MediaZipUtils
import org.videolan.vlc.remoteaccessserver.utils.serveAudios
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.isSchemeFile
import org.videolan.vlc.util.slugify
import org.videolan.vlc.util.toByteArray
import java.io.File

private const val TAG = "RARoutingResources"

fun Route.resourceRouting(appContext: Context, settings: SharedPreferences) {
    // Download a media file
    get("/prepare-download") {
        val type = call.request.queryParameters["type"] ?: "media"
        call.request.queryParameters["id"]?.let { id ->
            when (type) {
                "album" -> {
                    val album = appContext.getFromMl { getAlbum(id.toLong()) }
                    val dst = MediaZipUtils.generateAlbumZip(album, RemoteAccessServer.getInstance(appContext).downloadFolder)
                    call.respondText(dst)
                    return@get
                }
                "artist" -> {
                    val artist = appContext.getFromMl { getArtist(id.toLong()) }
                    val dst = MediaZipUtils.generateArtistZip(artist, RemoteAccessServer.getInstance(appContext).downloadFolder)
                    call.respondText(dst)
                    return@get
                }
                "genre" -> {
                    val genre = appContext.getFromMl { getGenre(id.toLong()) }
                    val dst = MediaZipUtils.generateGenreZip(genre, RemoteAccessServer.getInstance(appContext).downloadFolder)
                    call.respondText(dst)
                    return@get
                }
                "playlist" -> {
                    val playlist = appContext.getFromMl { getPlaylist(id.toLong(), false, false) }
                    val dst = MediaZipUtils.generatePlaylistZip(playlist, RemoteAccessServer.getInstance(appContext).downloadFolder)
                    call.respondText(dst)
                    return@get
                }
                "video-group" -> {
                    val videoGroup = appContext.getFromMl { getVideoGroup(id.toLong()) }
                    val dst = MediaZipUtils.generateVideoGroupZip(videoGroup, RemoteAccessServer.getInstance(appContext).downloadFolder)
                    call.respondText(dst)
                    return@get
                }
                "video-folder" -> {
                    val videoFolder = appContext.getFromMl { getFolder(Folder.TYPE_FOLDER_VIDEO, id.toLong()) }
                    val dst = MediaZipUtils.generateVideoGroupZip(videoFolder, RemoteAccessServer.getInstance(appContext).downloadFolder)
                    call.respondText(dst)
                    return@get
                }
                else -> {
                    //simple media. It's a direct download
                    appContext.getFromMl { getMedia(id.toLong()) }?.let { media ->
                        media.uri.path?.let { path ->
                            val file = File(path)
                            val name = media.title.slugify("_") + media.uri.toString().substring(media.uri.toString().lastIndexOf("."))
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, name)
                                    .toString()
                            )
                            call.respondFile(file)
                            return@get
                        }
                    }
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
        call.respond(HttpStatusCode.NotFound)
    }

    // Stream an audio file for in-browser playback
    get("/stream") {
        if (!settings.serveAudios(appContext)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val id = call.request.queryParameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing id parameter")
            return@get
        }
        val media = appContext.getFromMl { getMedia(id.toLong()) }
        if (media == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val path = media.uri.path
        if (path == null || !media.uri.scheme.isSchemeFile()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, file.name)
                .toString()
        )
        call.respondFile(file)
    }

    //Change the favorite state of a media
    get("/favorite") {
        val type = call.request.queryParameters["type"] ?: "media"
        val favorite = (call.request.queryParameters["favorite"] ?: "true") == "true"
        call.request.queryParameters["id"]?.let { id ->
            when (type) {
                "album" -> {
                    val album = appContext.getFromMl { getAlbum(id.toLong()) }
                    album.isFavorite = favorite
                    call.respondText("")
                    return@get
                }
                "artist" -> {
                    val artist = appContext.getFromMl { getArtist(id.toLong()) }
                    artist.isFavorite = favorite
                    call.respondText("")
                    return@get
                }
                "genre" -> {
                    val genre = appContext.getFromMl { getGenre(id.toLong()) }
                    genre.isFavorite = favorite
                    call.respondText("")
                    return@get
                }
                "playlist" -> {
                    val playlist = appContext.getFromMl { getPlaylist(id.toLong(), false, false) }
                    playlist.isFavorite = favorite
                    call.respondText("")
                    return@get
                }
                "video-group" -> {
                    val videoGroup = appContext.getFromMl { getVideoGroup(id.toLong()) }
                    videoGroup.isFavorite = favorite
                    call.respondText("")
                    return@get
                }
                "video-folder" -> {
                    val videoFolder = appContext.getFromMl { getFolder(Folder.TYPE_FOLDER_VIDEO, id.toLong()) }
                    videoFolder.isFavorite = favorite
                    call.respondText("")
                    return@get
                }
                else -> {
                    //simple media. It's a direct download
                    appContext.getFromMl { getMedia(id.toLong()) }?.let { media ->
                        media.isFavorite = favorite
                        call.respondText("")
                        return@get

                    }
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
        call.respond(HttpStatusCode.NotFound)
    }

    // Get a media artwork
    get("/artwork") {
        var type = call.request.queryParameters["type"]
        val isBig = type?.endsWith("_big") == true
        if (type in arrayOf("folder", "network", "folder_big", "network_big")) {
            call.request.queryParameters["artwork"]?.let { artworkUrl ->
                if (artworkUrl.startsWith("http")) {
                    val bmp = HttpImageLoader.downloadBitmap(artworkUrl)
                    if (bmp != null) {
                        BitmapUtil.encodeImage(bmp, true)?.let {
                            call.respondBytes(ContentType.Image.PNG) { it }
                            return@get
                        }
                    }
                }
            }
            val size = if (isBig) 256 else 54
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, if (isBig) R.drawable.ic_folder_big else  R.drawable.ic_folder,
                size, size), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        if (call.request.queryParameters["type"]?.startsWith("video-group") == true) {
            call.request.queryParameters["id"]?.let { id ->
                val group = appContext.getFromMl {
                    getVideoGroup(id.toLong())
                }
                val bmp = ThumbnailsProvider.getVideoGroupThumbnail(group, 512)
                if (bmp != null) {
                    BitmapUtil.encodeImage(bmp, true)?.let {
                        call.respondBytes(ContentType.Image.PNG) { it }
                        return@get
                    }
                }
            }
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext,  if (isBig) R.drawable.ic_folder_big else  R.drawable.ic_folder, 256, 256), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        if (type?.startsWith("video-folder") == true) {
            call.request.queryParameters["id"]?.let { id ->
                val folder = appContext.getFromMl {
                    getFolder(Folder.TYPE_FOLDER_VIDEO, id.toLong())
                }
                val bmp = ThumbnailsProvider.getFolderThumbnail(folder, 512)
                if (bmp != null) {
                    BitmapUtil.encodeImage(bmp, true)?.let {
                        call.respondBytes(ContentType.Image.PNG) { it }
                        return@get
                    }
                }
            }
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext,  if (type?.endsWith("_big") == true) R.drawable.ic_folder_big else  R.drawable.ic_folder, 256, 256), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        if (type == "new-stream" || type == "new-stream_big") {
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, if (type?.endsWith("_big") == true) R.drawable.ic_remote_stream_add_big else R.drawable.ic_remote_stream_add, 256, 256), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        if (type == "file") {
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, R.drawable.ic_unknown, 256, 256), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        if (type == "file_big") {
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, R.drawable.ic_unknown_big, 256, 256), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        if (type == "subtitle") {
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, R.drawable.ic_subtitles, 256, 256), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        if (type == "subtitle_big") {
            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, R.drawable.ic_subtitles, 512, 512), true)?.let {
                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }
        }
        try {
            val artworkMrl = call.request.queryParameters["artwork"] ?: RemoteAccessServer.getInstance(appContext).service?.coverArt

            var bigVariant = "0"
            if (isBig) {
                type = type?.substring(0, type!!.length -4)
                bigVariant = "1"
            }
            //check by id and use the ArtworkProvider if provided
            call.request.queryParameters["id"]?.let { id ->
                val cr = appContext.contentResolver
                val mediaType = when (type) {
                    "video" -> ArtworkProvider.VIDEO
                    "album" -> ArtworkProvider.ALBUM
                    "artist" -> ArtworkProvider.ARTIST
                    "genre" -> ArtworkProvider.GENRE
                    "playlist" -> ArtworkProvider.PLAYLIST
                    else -> ArtworkProvider.MEDIA
                }
                val uri = ArtworkProvider.buildUri(appContext, Uri.Builder()
                    .appendPath(mediaType)
                    .appendPath("1")
                    .appendPath(id)
                    .appendQueryParameter(ArtworkProvider.BIG_VARIANT, bigVariant)
                    .appendQueryParameter(ArtworkProvider.REMOTE_ACCESS, "1")
                    .build())
                cr.openInputStream(uri)?.let { inputStream ->
                    call.respondBytes(ContentType.Image.JPEG) { inputStream.toByteArray() }
                    inputStream.close()
                    return@get
                }
            }

            //id is not provided, use the artwork query and fallback on the current playing media
            artworkMrl?.let { coverArt ->
                AudioUtil.readCoverBitmap(Uri.decode(coverArt), 512)?.let { bitmap ->
                    BitmapUtil.convertBitmapToByteArray(bitmap)?.let {

                        call.respondBytes(ContentType.Image.JPEG) { it }
                        return@get
                    }
                }
            }

            // try video cover
            RemoteAccessServer.getInstance(appContext).service?.currentMediaWrapper?.let {
                ThumbnailsProvider.getVideoThumbnail(it, 512)?.let {
                    BitmapUtil.encodeImage(it)?.let {
                        call.respondBytes(ContentType.Image.PNG) { it }
                        return@get
                    }
                }
            }

            // nothing found . Falling back on the no media bitmap
            appContext.getBitmapFromDrawable(R.drawable.ic_no_media, 512, 512)?.let {

                BitmapUtil.encodeImage(it, true)?.let {

                    call.respondBytes(ContentType.Image.PNG) { it }
                    return@get
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
        call.respond(HttpStatusCode.NotFound, "")
    }
}
