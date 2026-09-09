/*
 * ************************************************************************
 *  RemoteAccessRoutingFile.kt
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
import android.text.format.Formatter
import android.util.Log
import androidx.core.net.toUri
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.observeLiveDataUntil
import org.videolan.tools.AppScope
import org.videolan.tools.CloseableUtils
import org.videolan.tools.REMOTE_ACCESS_FILE_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_LOGS
import org.videolan.tools.REMOTE_ACCESS_NETWORK_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_PLAYBACK_CONTROL
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.FeedbackUtil
import org.videolan.vlc.providers.FileBrowserProvider
import org.videolan.vlc.providers.StorageProvider
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.RemoteAccessSession.verifyLogin
import org.videolan.vlc.repository.SlaveRepository
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.isSchemeFile
import org.videolan.vlc.util.isSchemeSupported
import org.videolan.vlc.viewmodels.browser.FavoritesProvider
import org.videolan.vlc.viewmodels.browser.PathOperationDelegate
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

private const val TAG = "RARoutingFile"

fun Route.authenticatedFileRouting(appContext: Context, scope: CoroutineScope, settings: SharedPreferences) {
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
                    val uploadDir = File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/uploads")
                    uploadDir.mkdirs()
                    fileName = part.originalFileName as String
                    val fileBytes = part.streamProvider().readBytes()
                    val file = File(uploadDir, fileName)
                    if (!file.isSafelyWithin(uploadDir)) {
                        call.respond(HttpStatusCode.Forbidden, "Invalid file path")
                        return@forEachPart
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
                    if (!file.isSafelyWithin(uploadDir)) {
                        call.respond(HttpStatusCode.Forbidden, "Invalid file path")
                        return@forEachPart
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
            Log.w(TAG, "Failed to parse form parameters. ${e.message}", e)
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
            Log.e(TAG, e.message, e)
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
            Log.e(TAG, e.message, e)
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
            (it as? MediaWrapper)?.let { media ->
                if (media.type != MediaWrapper.TYPE_DIR) {
                    media.description = if (media.uri.scheme.isSchemeFile()) {
                        media.uri.path?.let { p ->
                            Formatter.formatFileSize(appContext, File(p).length())
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
        if (!checkPermission(settings) { settings.getBoolean(REMOTE_ACCESS_FILE_BROWSER_CONTENT, false) }) return@get
        val requested = call.request.queryParameters["file"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing file parameter")
            return@get
        }

        val baseDir = File(RemoteAccessServer.getInstance(appContext).downloadFolder)
        val dstFile = File(baseDir, requested)

        // Enforce that the resolved path stays within the intended download directory
        if (!dstFile.isSafelyWithin(baseDir)) {
            call.respond(HttpStatusCode.Forbidden, "Invalid file path")
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
    }
}
