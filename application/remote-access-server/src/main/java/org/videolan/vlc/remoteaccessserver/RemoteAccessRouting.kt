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
import io.ktor.server.plugins.origin
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.vlc.remoteaccessserver.routing.*
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
import org.videolan.tools.REMOTE_ACCESS_PLAYBACK_CONTROL
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
import org.videolan.vlc.repository.SlaveRepository
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

private const val TAG = "VLC/HttpSharingServer"

/**
 * Setup the server routing
 *
 */
fun Route.setupRouting(appContext: Context, scope: CoroutineScope) {
    val settings = Settings.getInstance(appContext)
    staticFiles("", File(getServerFiles(appContext)))

    publicAuthRouting(appContext, scope, settings)

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
                    val fileBytes = part.streamProvider().readBytes()
                    val file = File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/uploads/$fileName")
                    if (file.canonicalFile.parent?.startsWith(File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/uploads").absolutePath) != true) {
                        call.respond(HttpStatusCode.Unauthorized)
                        throw (IllegalStateException("${file.canonicalFile.parent} is not a valid path"))
                    }
                    file.writeBytes(fileBytes)
                }
                else -> {}
            }
        }
        call.respondText("$fileDescription is uploaded to 'uploads/$fileName'")
    }
    // Upload a subtitle to the device
    post("/upload-subtitle") {
        verifyLogin(settings)
        if (!settings.getBoolean(REMOTE_ACCESS_PLAYBACK_CONTROL, true)) {
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }
        var uploaded = false
        val multipartData = call.receiveMultipart()

        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val uploadDir = File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/subtitles")
                    uploadDir.mkdirs()
                    val fileName = part.originalFileName?.let { File(it).name } ?: "subtitle.srt"
                    val file = File(uploadDir, fileName)
                    if (!file.canonicalFile.canonicalPath.startsWith(uploadDir.canonicalPath + File.separator)) {
                        call.respond(HttpStatusCode.Unauthorized)
                        throw (IllegalStateException("${file.canonicalFile.parent} is not a valid path"))
                    }
                    part.streamProvider().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    uploaded = true

                    val service = RemoteAccessServer.getInstance(appContext).service
                    if (service?.hasMedia() == true) {
                        val uri = Uri.fromFile(file)
                        withContext(Dispatchers.Main) {
                            service.addSubtitleTrack(uri, true)
                            service.currentMediaWrapper?.let { media ->
                                SlaveRepository.getInstance(appContext).saveSlave(media.location, IMedia.Slave.Type.Subtitle, 2, uri.toString())
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        if (uploaded) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
    // Download a log file
    get("/download-logfile") {
        verifyLogin(settings)
        if (!settings.getBoolean(REMOTE_ACCESS_LOGS, false)) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        call.request.queryParameters["file"]?.let { filePath ->
            if (getLogsFiles(appContext).none { it.path == filePath }) {
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
        val logs = getLogsFiles(appContext).sortedByDescending { it.date }

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

    authenticate("user_session", optional = RemoteAccessServer.byPassAuth) {
        authenticatedAuthRouting()
        mediaRouting(appContext, scope, settings)
        post("/logs") {
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
        // Download a file previously prepared
        get("/download") {
            val requested = call.request.queryParameters["file"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing file parameter")
                return@get
            }

            val baseDir = File(RemoteAccessServer.getInstance(appContext).downloadFolder).canonicalFile
            val dstFile = File(baseDir, requested).canonicalFile

            // Enforce that the resolved path stays within the intended download directory
            if (!dstFile.path.startsWith(baseDir.path + File.separator)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid file path")
                return@get
            }

            // Send as attachment with a safe filename
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, dstFile.name)
                    .toString()
            )

            // Stream the file, then return early to avoid double responses
            call.respondFile(dstFile)
            // Optionally delete only if it resides in baseDir
            runCatching { if (dstFile.exists()) dstFile.delete() }
            return@get
        }
    }
}

private suspend fun ApplicationCall.respondJson(text: String, status: HttpStatusCode? = null, configure: OutgoingContent.() -> Unit = {}) {
    respond(TextContent(text, ContentType.Application.Json, status).apply(configure))
}
