/*
 * ************************************************************************
 *  RemoteAccessRouting.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.vlc.remoteaccessserver

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.TextContent
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.resources.util.await
import org.videolan.resources.util.getFromMl
import org.videolan.resources.util.observeLiveDataUntil
import org.videolan.tools.AppScope
import org.videolan.tools.CloseableUtils
import org.videolan.tools.HttpImageLoader
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.KEY_CURRENT_AUDIO
import org.videolan.tools.KEY_CURRENT_MEDIA
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.REMOTE_ACCESS_FILE_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_HISTORY_CONTENT
import org.videolan.tools.REMOTE_ACCESS_LOGS
import org.videolan.tools.REMOTE_ACCESS_NETWORK_BROWSER_CONTENT
import org.videolan.tools.Settings
import org.videolan.tools.awaitAppIsForegroung
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.livedata.LiveDataset
import org.videolan.tools.resIdByName
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.dialogs.getPlaylistByName
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.FeedbackUtil
import org.videolan.vlc.gui.helpers.VectorDrawableUtil
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.gui.helpers.getColoredBitmapFromColor
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.ResumeStatus
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.providers.FileBrowserProvider
import org.videolan.vlc.providers.StorageProvider
import org.videolan.vlc.providers.medialibrary.sanitizeGroups
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer.Companion.getServerFiles
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer.PlayerStatus
import org.videolan.vlc.remoteaccessserver.RemoteAccessSession.verifyLogin
import org.videolan.vlc.remoteaccessserver.utils.MediaZipUtils
import org.videolan.vlc.remoteaccessserver.utils.serveAudios
import org.videolan.vlc.remoteaccessserver.utils.servePlaylists
import org.videolan.vlc.remoteaccessserver.utils.serveSearch
import org.videolan.vlc.remoteaccessserver.utils.serveVideos
import org.videolan.vlc.remoteaccessserver.websockets.RemoteAccessWebSockets
import org.videolan.vlc.remoteaccessserver.websockets.WSIncomingMessage
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.RemoteAccessUtils
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.generateResolutionClass
import org.videolan.vlc.util.getFilesNumber
import org.videolan.vlc.util.getFolderNumber
import org.videolan.vlc.util.isSchemeFile
import org.videolan.vlc.util.isSchemeSupported
import org.videolan.vlc.util.slugify
import org.videolan.vlc.util.toByteArray
import org.videolan.vlc.viewmodels.browser.FavoritesProvider
import org.videolan.vlc.viewmodels.browser.PathOperationDelegate
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Setup the server routing
 *
 */
fun Route.setupRouting(appContext: Context, scope: CoroutineScope) {
    val settings = Settings.getInstance(appContext)
    staticFiles("", File(getServerFiles(appContext)))
    //the client is requesting a new code.
    // if the formparameters "challenge" is sent. Remove the corresponding code
    post("/code") {
        val formParameters = try {
            call.receiveParameters()
        } catch (e: Exception) {
            null
        }
        val challenge = if (formParameters == null) null else formParameters["challenge"].toString()
        if (!challenge.isNullOrBlank()) {
            RemoteAccessOTP.removeCodeWithChallenge(challenge)
        }
        val code = RemoteAccessOTP.getFirstValidCode(appContext)
        scope.launch {
            RemoteAccessUtils.otpFlow.emit(code.code)
        }
        call.respondText(code.challenge)
    }
    //Verify the code and inject the cookie if valid
    post("/verify-code") {
        val formParameters = try {
            call.receiveParameters()
        } catch (e: Exception) {
            null
        }
        val idString = formParameters?.get("code")
        if (idString == null){
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        if (RemoteAccessOTP.verifyCode(appContext, idString)) {
            //verification is OK
            RemoteAccessSession.injectCookie(call, settings)
            scope.launch {
                RemoteAccessUtils.otpFlow.emit(null)
            }
            call.respondRedirect("/")
            return@post
        }
        call.respondRedirect("/index.html#/login/error")
    }
    // Main end point redirect to index.html
    get("/") {
        call.respondRedirect("index.html", permanent = true)
    }
    get("/index.html") {
        try {
            val html = FileUtils.getStringFromFile("${getServerFiles(appContext)}index.html")
            call.respondText(html, ContentType.Text.Html)
        } catch (e: Exception) {
            call.respondText("Failed to load index.html")
        }
    }
    // Upload a file to the device
    post("/upload-media") {
        verifyLogin(settings)
        var fileDescription = ""
        var fileName = ""
        val multipartData = call.receiveMultipart()

        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    fileDescription = part.value
                }
                is PartData.FileItem -> {
                    File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/uploads").mkdirs()
                    fileName = part.originalFileName as String
                    var fileBytes = part.streamProvider().readBytes()
                    File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/uploads/$fileName").writeBytes(fileBytes)
                }
                else -> {}
            }
        }
        call.respondText("$fileDescription is uploaded to 'uploads/$fileName'")
    }
    // Download a log file
    get("/download-logfile") {
        verifyLogin(settings)
        if (!settings.getBoolean(REMOTE_ACCESS_LOGS, false)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        call.request.queryParameters["file"]?.let { filePath ->
            if (getLogsFiles().none { it.path == filePath }) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            val file = File(filePath)
            if (file.exists()) {
                call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString())
                call.respondFile(File(filePath))
            }
        }
        call.respond(HttpStatusCode.NotFound, "")
    }
    // List all log files
    get("/logfile-list") {
        verifyLogin(settings)
        if (!settings.getBoolean(REMOTE_ACCESS_LOGS, false)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        val logs = getLogsFiles().sortedByDescending { it.date }

        call.respondJson(convertToJson(logs))
    }
    // Prepare the feedback data
    post("/feedback-form") {
        verifyLogin(settings)

        val formParameters = try {
            call.receiveParameters()
        } catch (e: Exception) {
            Log.w(this::class.java.simpleName, "Failed to parse form parameters. ${e.message}", e)
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        val feedbackType = formParameters.get("type")?.toInt() ?: 0
        val includeML = formParameters.get("includeML") == "true"
        val includeLogs = formParameters.get("includeLogs") == "true"
        val subject = formParameters.get("subject") ?: ""
        val message = formParameters.get("message") ?: ""

        if ((includeLogs || includeML) && !settings.getBoolean(REMOTE_ACCESS_LOGS, false)) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }

        var zipFile: String? = null

        val externalPath = RemoteAccessServer.getInstance(appContext).downloadFolder
        val logcatZipPath = "$externalPath/logcat.zip"

        // generate logs
        if (includeLogs) {
            File(externalPath).mkdirs()
            RemoteAccessServer.getInstance(appContext).gatherLogs(logcatZipPath)
        }
        val dbPath = "$externalPath${Medialibrary.VLC_MEDIA_DB_NAME}"

        //generate ML
        if (includeML) {
            File(externalPath).mkdirs()
            val db = File(appContext.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)
            val dbFile = File(dbPath)
            FileUtils.copyFile(db, dbFile)
        }

        //Zip needed files
        if (File(logcatZipPath).exists() || File(dbPath).exists()) {
            zipFile = "feedback_report.zip"
            val dbZipPath = "$externalPath/$zipFile"
            val filesToZip = mutableListOf<String>()
            if (File(logcatZipPath).exists()) filesToZip.add(logcatZipPath)
            if (File(dbPath).exists()) filesToZip.add(dbPath)
            FileUtils.zip(filesToZip.toTypedArray(), dbZipPath)
            filesToZip.forEach { FileUtils.deleteFile(it) }
        }

        val completeMessage = "$message\r\n\r\n${FeedbackUtil.generateUsefulInfo(appContext)}"

        val mail = if (feedbackType == 3 && BuildConfig.BETA) FeedbackUtil.SupportType.CRASH_REPORT_EMAIL.email else FeedbackUtil.SupportType.SUPPORT_EMAIL.email
        val result = RemoteAccessServer.FeedbackResult(mail, FeedbackUtil.generateSubject(subject, feedbackType), completeMessage, zipFile)

        call.respondJson(convertToJson(result))
    }
    // Get the translation string list
    get("/translation") {
        call.respondJson(convertToJson(TranslationMapping.generateTranslations(appContext.getContextWithLocale(AppContextProvider.locale))))
    }
    get("/secure-url") {
        call.respondText(RemoteAccessServer.getInstance(appContext).getSecureUrl(call))
    }
    // Sends an icon
    get("/icon") {
        val idString = call.request.queryParameters["id"]
        val width = call.request.queryParameters["width"]?.toInt()?.coerceAtLeast(1) ?: 32
        val preventTint = call.request.queryParameters["preventTint"]?.toBoolean() == true

        val id = try {
            appContext.resIdByName(idString, "drawable")
        } catch (e: Resources.NotFoundException) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        if (id == 0) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        idString?.let {
            try {
                call.respondText(VectorDrawableUtil.convertToSvg(appContext, width, id, it), ContentType.Image.SVG)
                return@get
            } catch (e: Resources.NotFoundException) {
                Log.w(this::class.java.simpleName, "Failed to convert vector drawable. ${e.message}")
                // Continue on and let BitmapUtil attempt to load the file
            }
        }

        val bmp = if (preventTint)
            BitmapUtil.vectorToBitmap(appContext, id, width, width)
        else
            appContext.getColoredBitmapFromColor(id, ContextCompat.getColor(appContext, R.color.black), width, width)

        BitmapUtil.encodeImage(bmp, true)?.let {

            call.respondBytes(ContentType.Image.PNG) { it }
            return@get
        }

        call.respond(HttpStatusCode.NotFound)

    }

    post ("/logs") {
        val formParameters = try {
            call.receiveParameters()
        } catch (e: Exception) {
            null
        }
        val logs = buildString {
            formParameters?.forEach { s, strings ->
                if (s.contains("[time]"))
                    append(format.get()?.format(strings[0].toLong()))
                else if (s.contains("[level]")) {
                    append(" - ")
                    append(strings[0])
                } else {
                    strings.forEach {
                        append(" - ")
                        append(it)
                        append("*")
                    }
                }
            }
        }

        //save
        val timestamp = android.text.format.DateFormat.format("yyyyMMdd_kkmmss", System.currentTimeMillis())
        val filename = File("${AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY}/vlc_logcat_remote_access_${timestamp}.log")
        var saved = true
        var fos: FileOutputStream? = null
        var output: OutputStreamWriter? = null
        var bw: BufferedWriter? = null

        try {
            fos = FileOutputStream(filename)
            output = OutputStreamWriter(fos)
            bw = BufferedWriter(output)
            synchronized(this) {
                bw.write(FeedbackUtil.generateUsefulInfo(appContext))
                for (line in logs.split(("*"))) {
                    bw.write(line)
                    bw.newLine()
                }
            }
        } catch (e: FileNotFoundException) {

            saved = false
        } catch (ioe: IOException) {
            saved = false
        } finally {
            saved = saved and CloseableUtils.close(bw)
            saved = saved and CloseableUtils.close(output)
            saved = saved and CloseableUtils.close(fos)
        }

        if (!saved)
            call.respond(HttpStatusCode.InternalServerError)
        else
            call.respondText("")
    }
    authenticate("user_session", optional = RemoteAccessServer.byPassAuth) {
        //Provide a Websocket auth ticket as auth is validated
        get("/wsticket") {
            val ticket = RemoteAccessWebSockets.createTicket()
            call.respondText(ticket)
        }

        // List of all the videos
        get("/video-list") {
            if (!settings.serveVideos(appContext)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
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
        get("/longpolling") {
            //Empty the queue if needed
            if (RemoteAccessWebSockets.messageQueue.isNotEmpty()) {
                val queue = mutableListOf<RemoteAccessServer.WSMessage>().apply {
                    RemoteAccessWebSockets.messageQueue.drainTo(this)
                }
                call.respondJson(convertToJson(queue))
                return@get
            }
            //block the request until a message is received
            // The 3 second timeout is to avoid blocking forever
            try {
                val message = withTimeout(3000) { RemoteAccessWebSockets.onPlaybackEventChannel.receive() }
                if (message.type == RemoteAccessServer.WSMessageType.BROWSER_DESCRIPTION) {
                    call.respondJson(convertToJson(listOf(message)))
                    return@get
                }
            } catch (e: TimeoutCancellationException) {
                // Fall through to the next block of code
            }
            val remoteAccessServer = RemoteAccessServer.getInstance(appContext)
            val messages = listOfNotNull(
                remoteAccessServer.generatePlayQueue(),
                PlayerStatus(PlaylistManager.showAudioPlayer.value == true),
                remoteAccessServer.generateNowPlaying()
            )
            call.respondJson(convertToJson(messages))
        }
        // Manage playback events
        get("/playback-event") {
            call.request.queryParameters["message"]?.let { message ->
                val id = call.request.queryParameters["id"]?.toInt()
                val longValue = call.request.queryParameters["longValue"]?.toLong()
                val floatValue = call.request.queryParameters["floatValue"]?.toFloat()
                val stringValue = call.request.queryParameters["stringValue"]
                val incomingMessage = WSIncomingMessage(message, id, floatValue, longValue, stringValue)
                val service = RemoteAccessServer.getInstance(appContext).service
                val result = withContext(Dispatchers.Main) {
                    RemoteAccessWebSockets.manageIncomingMessages(incomingMessage, settings, service, appContext)
                }
                if (!result) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
            }
            call.respond(HttpStatusCode.OK)
        }
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
        // Get an playlist details
        get("/playlist") {
            verifyLogin(settings)
            if (!settings.serveAudios(appContext)) {
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

            val name = formParameters?.get("name") ?: call.respond(HttpStatusCode.NoContent)
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
            val playlists = formParameters?.getAll("playlists[]") as List<String>
            if (mediaId == null || mediaType == null) {
                call.respond(HttpStatusCode.NoContent)
                return@post
            }
            if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "mediaId: $mediaId, mediaType: $mediaType, playlists: $playlists")

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
        // Search media
        get("/search") {
            verifyLogin(settings)
            if (!settings.serveSearch(appContext)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.request.queryParameters["search"]?.let { query ->
                val searchAggregate = appContext.getFromMl { search(query, Settings.includeMissing, false) }

                searchAggregate?.let { result ->
                    val results = RemoteAccessServer.SearchResults(
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
                    call.respondJson(convertToJson(results))
                }

            }
            call.respondJson(convertToJson(RemoteAccessServer.SearchResults(listOf(), listOf(), listOf(), listOf(), listOf(), listOf())))
        }
        // List of all the file storages
        get("/storage-list") {
            verifyLogin(settings)
            if (!settings.getBoolean(REMOTE_ACCESS_FILE_BROWSER_CONTENT, false)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            if (!Permissions.canReadStorage(appContext)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            //Get content synchronously
            val dataset = LiveDataset<MediaLibraryItem>()
            val provider = withContext(Dispatchers.Main) {
                StorageProvider(appContext, dataset, null)
            }
            // Launch asynchronous description calculations
            getProviderDescriptions(appContext, scope, provider, dataset)
            val list = try {
                getProviderContent(appContext, provider, dataset, 1000L)
            } catch (e: Exception) {
                Log.e(this::class.java.simpleName, e.message, e)
                call.respond(HttpStatusCode.InternalServerError)
                return@get
            }
            call.respondJson(convertToJson(list))
        }
        // List of all the file favorites
        get("/favorite-list") {
            verifyLogin(settings)
            if (!settings.getBoolean(REMOTE_ACCESS_FILE_BROWSER_CONTENT, false)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            val dataset = LiveDataset<MediaLibraryItem>()
            val provider = withContext(Dispatchers.Main) {
                FavoritesProvider(appContext, dataset, AppScope)
            }
            val list = try {
                getProviderContent(appContext, provider, dataset, 2000L)
            } catch (e: Exception) {
                Log.e(this::class.java.simpleName, e.message, e)
                call.respond(HttpStatusCode.InternalServerError)
                return@get
            }
            call.respondJson(convertToJson(list))
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
                Log.e(this::class.java.simpleName, e.message, e)
                call.respond(HttpStatusCode.InternalServerError)
                return@get
            }
            call.respondJson(convertToJson(list))
        }
        // List of all the network shares
        get("/network-list") {
            verifyLogin(settings)
            if (!settings.getBoolean(REMOTE_ACCESS_NETWORK_BROWSER_CONTENT, false)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            RemoteAccessServer.getInstance(appContext).launchNetworkDiscovery()
            //No response are the result are asynchronous and sent back using websockets / long polling
            call.respondJson("")
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
        //list of folders and files in a path
        get("/browse-list") {
            verifyLogin(settings)
            if (!settings.getBoolean(REMOTE_ACCESS_FILE_BROWSER_CONTENT, false)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            val path = call.request.queryParameters["path"] ?: kotlin.run {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val decodedPath = Uri.decode(path)

            val dataset = LiveDataset<MediaLibraryItem>()
            //Get content synchronously
            val provider = withContext(Dispatchers.Main) {
                FileBrowserProvider(appContext, dataset, decodedPath, false, false, Medialibrary.SORT_FILENAME, false)
            }
            // Launch asynchronous description calculations (for folders)
            getProviderDescriptions(appContext, scope, provider, dataset)
            observeLiveDataUntil(2000, dataset) {
                provider.loading.value == false
            }

            // synchronous descriptions (for files)
            dataset.getList().forEach {
                (it as? MediaWrapper)?.let {
                    if (it.type != MediaWrapper.TYPE_DIR) {
                        it.description = if (it.uri.scheme.isSchemeFile()) {
                            it.uri.path?.let {
                                Formatter.formatFileSize(appContext, File(it).length())
                            } ?: ""
                        } else
                            " "
                    }
                }
            }
            val list = dataset.getList().mapIndexed { index, it ->
                val filePath = when (it) {
                    is MediaWrapper -> it.uri.toString()
                    is Storage -> it.uri.toString()
                    else -> throw IllegalStateException("Unrecognised media type")

                }
                val title = if ((provider.url == null || provider.url!!.toUri().scheme.isSchemeFile())
                        && it is MediaWrapper) it.fileName else it.title
                val isFolder = if (it is MediaWrapper) it.type == MediaWrapper.TYPE_DIR else true
                var fileType = "folder"
                if (!isFolder) {
                    fileType = when ((it as MediaWrapper).type) {
                        MediaWrapper.TYPE_AUDIO -> "audio"
                        MediaWrapper.TYPE_VIDEO -> "video"
                        MediaWrapper.TYPE_SUBTITLE -> "subtitle"
                        else -> "file"
                    }
                }
                val id = if (it is MediaWrapper && it.id > 0) it.id else 1000L + index
                val played = if (it is MediaWrapper) it.seen >  0 else false

                if (it is MediaWrapper && it.id > 0) {
                    it.toPlayQueueItem().apply {
                        this.fileType = fileType
                        this.artist = it.description
                    }
                } else
                    RemoteAccessServer.PlayQueueItem(
                        id, title, it.description ?: "", 0, it.artworkMrl
                            ?: "", false, "", filePath, isFolder, fileType = fileType, played = played)
            }

            //segments
            PathOperationDelegate.storages.put(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY,   RemoteAccessServer.getInstance(appContext).makePathSafe(appContext.getString(org.videolan.vlc.R.string.internal_memory)))
            val breadcrumbItems = if (!isSchemeSupported(decodedPath.toUri().scheme))
                listOf(RemoteAccessServer.BreadcrumbItem(appContext.getString(R.string.home), "root"))
            else
                RemoteAccessServer.getInstance(appContext).prepareSegments(decodedPath.toUri()).map {
                    RemoteAccessServer.BreadcrumbItem(it.first, it.second)
                }.toMutableList().apply {
                    add(0, RemoteAccessServer.BreadcrumbItem(appContext.getString(R.string.home), "root"))
                }

            val result = RemoteAccessServer.BrowsingResult(list, breadcrumbItems)
            call.respondJson(convertToJson(result))
        }
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
                if (medias.isEmpty()) call.respond(HttpStatusCode.NotFound)
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
                            if (applyPlaylist) service.playlistManager.videoResumeStatus =  ResumeStatus.NEVER
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
            type?.let { type ->

                val medias = if (type == "browser") {
                    val path = call.request.queryParameters["path"] ?: kotlin.run {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val decodedPath = Uri.decode(path)

                    val dataset = LiveDataset<MediaLibraryItem>()
                    var list: Pair<List<MediaLibraryItem>, ArrayList<Pair<Int, String>>>? = null
                    val provider = withContext(Dispatchers.Main) {
                        FileBrowserProvider(appContext, dataset, decodedPath, false, false, Medialibrary.SORT_FILENAME, false)
                    }
                    try {
                        list = getMediaFromProvider( provider, dataset)
                    } catch (e: Exception) {
                        Log.e(this::class.java.simpleName, e.message, e)
                    }
                    list?.first?.map { it as MediaWrapper }?.toTypedArray()
                } else appContext.getFromMl {
                    when (type) {
                        "video-group" -> {
                            id?.let { id ->
                                val group = getVideoGroup(id.toLong())
                                group.media(Medialibrary.SORT_DEFAULT, false, false, false, group.mediaCount(), 0)
                            }
                        }
                        "video-folder" -> {
                            id?.let { id ->
                                val folder = getFolder(Folder.TYPE_FOLDER_VIDEO, id.toLong())
                                folder.media(Folder.TYPE_FOLDER_VIDEO, Medialibrary.SORT_DEFAULT, false, false, false, folder.mediaCount(Folder.TYPE_FOLDER_VIDEO), 0)
                            }
                        }
                        "artist" -> {
                            id?.let { id ->
                                val artist = getArtist(id.toLong())
                                artist.tracks
                            }
                        }
                        "album" -> {
                            id?.let { id ->
                                val album = getAlbum(id.toLong())
                                album.tracks
                            }
                        }
                        "genre" -> {
                            id?.let { id ->
                                val genre = getGenre(id.toLong())
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
                    }
                    MediaUtils.openList(appContext, medias.toList(), 0)
                    call.respond(HttpStatusCode.OK)
                }
            }
            call.respond(HttpStatusCode.NotFound)
        }
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
                            }
                        }
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
            call.respond(HttpStatusCode.NotFound)
        }
        //Download a file previously prepared
        get("/download") {
            call.request.queryParameters["file"]?.let {
                val dst = File("${RemoteAccessServer.getInstance(appContext).downloadFolder}/$it")
                call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, dst.toUri().lastPathSegment
                                ?: "")
                                .toString()
                )
                call.respondFile(dst)
                dst.delete()
            }
            call.respond(HttpStatusCode.NotFound)
        }
        //Change the favorite state of a media
        get("/favorite") {
            val type = call.request.queryParameters["type"] ?: "media"
            val favorite = call.request.queryParameters["favorite"] ?: "true" == "true"
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
                BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext,  if (type.endsWith("_big")) R.drawable.ic_folder_big else  R.drawable.ic_folder, 256, 256), true)?.let {
                    call.respondBytes(ContentType.Image.PNG) { it }
                    return@get
                }
            }
            if (type == "new-stream" || type == "new-stream_big") {
                BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, if (type.endsWith("_big")) R.drawable.ic_remote_stream_add_big else R.drawable.ic_remote_stream_add, 256, 256), true)?.let {
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
                    type = type!!.substring(0, type.length -4)
                    bigVariant = "1"
                }
                //check by id and use the ArtworkProvider if provided
                call.request.queryParameters["id"]?.let {
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
                            .appendPath(it)
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
                Log.e("networkShareReplace", e.message, e)
            }
            call.respond(HttpStatusCode.NotFound, "")
        }
    }
}

/**
 * The the list of all the log files
 *
 * @return a list of file paths
 */
private suspend fun getLogsFiles(): List<LogFile> = withContext(Dispatchers.IO) {
    val result = ArrayList<LogFile>()
    val folder = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
    val files = folder.listFiles()
    files.forEach {
        if (it.isFile && it.name.startsWith("vlc_logcat_"))
            result.add(LogFile(it.path, if (it.name.startsWith("vlc_logcat_remote_access")) "web" else "device", Date(it.lastModified())))
    }

    val crashFolder = File(AppContextProvider.appContext.getExternalFilesDir(null)!!.absolutePath )
    val crashFiles = crashFolder.listFiles()
    crashFiles.forEach {
        if (it.isFile && it.name.startsWith("vlc_crash"))
            result.add(LogFile(it.path, "crash", Date(it.lastModified())))
    }

    return@withContext result

}

data class LogFile(val path: String, val type: String, val date: Date)

private suspend fun getMediaFromProvider(provider: BrowserProvider, dataset: LiveDataset<MediaLibraryItem>): Pair<List<MediaLibraryItem>, ArrayList<Pair<Int, String>>> {
    dataset.await()
    val descriptions = ArrayList<Pair<Int, String>>()
    observeLiveDataUntil(5000, provider.descriptionUpdate) { pair ->
        descriptions.add(pair)
        //releasing once the number of descriptions is the same as the dataset
        descriptions.size < dataset.getList().size
    }
   return Pair(dataset.getList(), descriptions)
}

private fun getProviderDescriptions(context: Context, scope: CoroutineScope, provider: BrowserProvider, dataset: LiveDataset<MediaLibraryItem>) {
    val descriptions = ArrayList<Pair<Int, String>>()
    scope.launch(Dispatchers.IO) {

        observeLiveDataUntil(20000, provider.descriptionUpdate) { pair ->

            try {
                (dataset.getList()[pair.first] as? MediaWrapper)?.let { datasetEntry ->
                    val desc =
                            if (datasetEntry.type != MediaWrapper.TYPE_DIR) {
                                if (datasetEntry.uri.scheme.isSchemeFile()) {
                                    datasetEntry.uri.path?.let {
                                        Formatter.formatFileSize(context, File(it).length())
                                    } ?: ""
                                } else
                                    ""
                            } else {
                                val unparsedDescription = pair.second
                                val folders = unparsedDescription.getFolderNumber()
                                val files = unparsedDescription.getFilesNumber()
                                "${context.resources.getQuantityString(org.videolan.vlc.R.plurals.subfolders_quantity, folders, folders)} ${TextUtils.SEPARATOR} ${context.resources.getQuantityString(org.videolan.vlc.R.plurals.mediafiles_quantity, files, files)}"
                            }
                    if (desc.isNotEmpty()) scope.launch(Dispatchers.IO) {
                        RemoteAccessWebSockets.sendToAll(RemoteAccessServer.BrowserDescription(datasetEntry.uri.toString(), desc))
                    }
                }
                (dataset.getList()[pair.first] as? Storage)?.let { datasetEntry ->

                                val unparsedDescription = pair.second
                                val folders = unparsedDescription.getFolderNumber()
                                val files = unparsedDescription.getFilesNumber()
                    val desc = "${context.resources.getQuantityString(org.videolan.vlc.R.plurals.subfolders_quantity, folders, folders)} ${TextUtils.SEPARATOR} ${context.resources.getQuantityString(org.videolan.vlc.R.plurals.mediafiles_quantity, files, files)}"
                    if (desc.isNotEmpty()) scope.launch(Dispatchers.IO) {
                        RemoteAccessWebSockets.sendToAll(RemoteAccessServer.BrowserDescription(datasetEntry.uri.toString(), desc))
                    }
                }
            } catch (e: Exception) {
                Log.e("RemoteAccess", e.message, e)
            }
            descriptions.add(pair)
            //releasing once the number of descriptions is the same as the dataset
            descriptions.size < dataset.getList().size
        }
    }
}


/**
 * Get the content from a [BrowserProvider]
 * Gracefully (or not) waits for the [LiveData] to be set before sending the result back
 *
 * @param provider the [BrowserProvider] to use
 * @param dataset the [LiveDataset] to listen to
 * @param idPrefix a prefix allowing to generate an unique id
 * @return a populated list
 */
private suspend fun getProviderContent(context:Context, provider: BrowserProvider, dataset: LiveDataset<MediaLibraryItem>, idPrefix: Long): ArrayList<RemoteAccessServer.PlayQueueItem> {
    val mediaFromProvider = getMediaFromProvider(provider, dataset)
    val list = ArrayList<RemoteAccessServer.PlayQueueItem>()

    mediaFromProvider.first.forEachIndexed { index, mediaLibraryItem ->
        val description = try {
            if (mediaLibraryItem is MediaWrapper && mediaLibraryItem.type != MediaWrapper.TYPE_DIR) {
                if (mediaLibraryItem.uri.scheme.isSchemeFile()) {
                    mediaLibraryItem.uri.path?.let {
                        Formatter.formatFileSize(context, File(it).length())
                    } ?: ""
                } else
                    ""
            } else {
                val unparsedDescription = mediaFromProvider.second.firstOrNull { it.first == index }?.second
                val folders = unparsedDescription.getFolderNumber()
                val files = unparsedDescription.getFilesNumber()
                if (folders > 0 && files > 0) {
                    "${context.resources.getQuantityString(org.videolan.vlc.R.plurals.subfolders_quantity, folders, folders)} ${TextUtils.SEPARATOR} ${context.resources.getQuantityString(org.videolan.vlc.R.plurals.mediafiles_quantity, files, files)}"
                } else if (files > 0) {
                    context.resources.getQuantityString(org.videolan.vlc.R.plurals.mediafiles_quantity, files, files)
                } else if (folders > 0) {
                    context.resources.getQuantityString(org.videolan.vlc.R.plurals.subfolders_quantity, folders, folders)
                } else mediaLibraryItem.description ?: ""
            }
        } catch (e: Exception) {
            Log.e(RemoteAccessServer::class.java.simpleName, e.message, e)
            ""
        }
        val path = when (mediaLibraryItem) {
            is MediaWrapper -> mediaLibraryItem.uri.toString()
            is Storage -> mediaLibraryItem.uri.toString()
            else -> throw IllegalStateException("Unrecognised media type")

        }
        val title = if (provider is FileBrowserProvider
                && (provider.url == null || provider.url!!.toUri().scheme.isSchemeFile())
                && mediaLibraryItem is MediaWrapper) mediaLibraryItem.fileName else mediaLibraryItem.title
        val isFolder = if (mediaLibraryItem is MediaWrapper) mediaLibraryItem.type == MediaWrapper.TYPE_DIR else true
        list.add(RemoteAccessServer.PlayQueueItem(idPrefix + index, title, description, 0, mediaLibraryItem.artworkMrl
                ?: "", false, "", path, isFolder, favorite = mediaLibraryItem.isFavorite))
    }
    return list
}

fun convertToJson(data: Any?): String {
    if (data == null) return "{}"
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<Any>(data::class.java)
    return adapter.toJson(data)
}

inline fun <reified K, reified V> convertToJson(data: Map<K,V>?): String {
    val moshi = Moshi.Builder().build()
    val type = Types.newParameterizedType(MutableMap::class.java, K::class.java, V::class.java)
    val adapter = moshi.adapter<Map<K,V>>(type).nullSafe()
    return adapter.toJson(data)
}

inline fun <reified T> convertToJson(data: List<T>?): String {
    val moshi = Moshi.Builder()
        .add(Date::class.java, FormattedDateJsonAdapter().nullSafe())
        .build()
    val type = Types.newParameterizedType(MutableList::class.java, Any::class.java)
    val adapter = moshi.adapter<List<T>>(type).nullSafe()
    return adapter.toJson(data)
}

private val format by lazy {
    object : ThreadLocal<DateFormat>() {
        override fun initialValue() = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
    }
}

class FormattedDateJsonAdapter : JsonAdapter<Date>() {
    override fun fromJson(reader: JsonReader): Date? {
        val string = reader.nextString()
        return format.get().parse(string)
    }
    override fun toJson(writer: JsonWriter, value: Date?) {
        val string = format.get().format(value)
        writer.value(string)
    }
}

private suspend fun ApplicationCall.respondJson(text: String, status: HttpStatusCode? = null, configure: OutgoingContent.() -> Unit = {}) {
    respond(TextContent(text, ContentType.Application.Json, status).apply(configure))
}

fun Album.toPlayQueueItem() = RemoteAccessServer.PlayQueueItem(id, title, albumArtist
        ?: "", duration, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun Artist.toPlayQueueItem(appContext: Context) = RemoteAccessServer.PlayQueueItem(id, title, appContext.resources.getQuantityString(R.plurals.albums_quantity, albumsCount, albumsCount), 0, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun Genre.toPlayQueueItem(appContext: Context) = RemoteAccessServer.PlayQueueItem(id, title, appContext.resources.getQuantityString(R.plurals.track_quantity, tracksCount, tracksCount), 0, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun Playlist.toPlayQueueItem(appContext: Context) = RemoteAccessServer.PlayQueueItem(id, title, appContext.resources.getQuantityString(R.plurals.track_quantity, tracksCount, tracksCount), 0, artworkMrl
        ?: "", false, "", favorite = isFavorite)

fun MediaWrapper.toPlayQueueItem(defaultArtist: String = "") = RemoteAccessServer.PlayQueueItem(id, title, artistName?.ifEmpty { defaultArtist } ?: defaultArtist, length, artworkMrl
        ?: "", false, generateResolutionClass(width, height) ?: "", progress = time, played = seen > 0, favorite = isFavorite, path = uri.toString())

fun Folder.toPlayQueueItem(context: Context) = RemoteAccessServer.PlayQueueItem(id, title, context.resources.getQuantityString(org.videolan.vlc.R.plurals.videos_quantity, mediaCount(Folder.TYPE_FOLDER_VIDEO), mediaCount(Folder.TYPE_FOLDER_VIDEO))
        ?: "", 0, artworkMrl
        ?: "", false, "", fileType = "video-folder", favorite = isFavorite)

fun VideoGroup.toPlayQueueItem(context: Context) = RemoteAccessServer.PlayQueueItem(id, title, if (this.mediaCount() > 1) context.resources.getQuantityString(org.videolan.vlc.R.plurals.videos_quantity, this.mediaCount(), this.mediaCount()) else "length", 0, artworkMrl
        ?: "", false, "", played = presentSeen == presentCount && presentCount != 0, fileType = "video-group", favorite = isFavorite)