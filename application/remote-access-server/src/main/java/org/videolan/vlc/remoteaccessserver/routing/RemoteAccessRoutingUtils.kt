/*
 * ************************************************************************
 *  RemoteAccessRoutingUtils.kt
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
import android.text.format.Formatter
import android.util.Log
import androidx.core.net.toUri
import com.squareup.moshi.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.await
import org.videolan.resources.util.observeLiveDataUntil
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.remoteaccessserver.RemoteAccessOTP
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.RemoteAccessSession.verifyLogin
import org.videolan.vlc.remoteaccessserver.websockets.RemoteAccessWebSockets
import org.videolan.vlc.util.*
import java.io.File
import java.text.DateFormat
import java.util.*

private const val TAG = "RARoutingUtils"

internal val attempts = ArrayList<Pair<String, Long>>()
internal suspend fun isFlooding(appContext: Context, ip: String): Boolean {
    val now = System.currentTimeMillis()
    attempts.add(Pair(ip, now))
    attempts.removeIf { System.currentTimeMillis() - it.second > 3_600_000L }
    val nbAttemptsBySec = attempts.filter { it.first == ip && it.second > now - 1_000L }.size
    if (nbAttemptsBySec > 1) {
        delay((nbAttemptsBySec - 1) * 500L)
    }
    val nbAttemptsByMin = attempts.filter { it.first == ip && it.second > now - 60_000L }.size
    if (nbAttemptsByMin > 10) {
        RemoteAccessOTP.removeAllCodes(appContext)
        delay((nbAttemptsByMin - 10) * 2_000L)
    }
    val nbAttempts = attempts.filter { it.first == ip }.size
    return nbAttempts > 20
}

/**
 * The the list of all the log files
 *
 * @return a list of file paths
 */
internal suspend fun getLogsFiles(context: Context): List<LogFile> = withContext(Dispatchers.IO) {
    val result = ArrayList<LogFile>()
    val folder = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
    val files = folder.listFiles()
    files?.forEach {
        if (it.isFile && it.name.startsWith("vlc_logcat_"))
            result.add(LogFile(it.path, if (it.name.startsWith("vlc_logcat_remote_access")) "web" else "device", Date(it.lastModified())))
    }

    context.getExternalFilesDir(null)?.let { crashFolder ->
        val crashFiles = crashFolder.listFiles()
        crashFiles?.forEach {
            if (it.isFile && it.name.startsWith("vlc_crash"))
                result.add(LogFile(it.path, "crash", Date(it.lastModified())))
        }
    }

    return@withContext result
}

data class LogFile(val path: String, val type: String, val date: Date)

internal suspend fun getMediaFromProvider(provider: org.videolan.vlc.providers.BrowserProvider, dataset: LiveDataset<MediaLibraryItem>): Pair<List<MediaLibraryItem>, ArrayList<Pair<Int, String>>> {
    dataset.await()
    val descriptions = ArrayList<Pair<Int, String>>()
    observeLiveDataUntil(5000, provider.descriptionUpdate) { pair ->
        descriptions.add(pair)
        //releasing once the number of descriptions is the same as the dataset
        descriptions.size < dataset.getList().size
    }
    return Pair(dataset.getList(), descriptions)
}

internal fun getProviderDescriptions(context: Context, scope: CoroutineScope, provider: org.videolan.vlc.providers.BrowserProvider, dataset: LiveDataset<MediaLibraryItem>) {
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


internal suspend fun getProviderContent(context: Context, provider: org.videolan.vlc.providers.BrowserProvider, dataset: LiveDataset<MediaLibraryItem>, idPrefix: Long): ArrayList<RemoteAccessServer.PlayQueueItem> {
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
        val title = if (provider is org.videolan.vlc.providers.FileBrowserProvider
            && (provider.url == null || provider.url!!.toUri().scheme.isSchemeFile())
            && mediaLibraryItem is MediaWrapper) mediaLibraryItem.fileName else mediaLibraryItem.title
        val isFolder = if (mediaLibraryItem is MediaWrapper) mediaLibraryItem.type == MediaWrapper.TYPE_DIR else true

        var fileType = "folder"
        if (!isFolder) {
            fileType = when ((mediaLibraryItem as MediaWrapper).type) {
                MediaWrapper.TYPE_AUDIO -> "audio"
                MediaWrapper.TYPE_VIDEO -> "video"
                MediaWrapper.TYPE_SUBTITLE -> "subtitle"
                else -> "file"
            }
        }
        list.add(RemoteAccessServer.PlayQueueItem(idPrefix + index, title, description, 0, mediaLibraryItem.artworkMrl
            ?: "", false, "", path, isFolder, fileType = fileType, favorite = mediaLibraryItem.isFavorite))
    }
    return list
}

fun convertToJson(data: Any?): String {
    if (data == null) return "{}"
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter<Any>(data::class.java)
    return adapter.toJson(data)
}

inline fun <reified K, reified V> convertToJson(data: Map<K, V>?): String {
    val moshi = Moshi.Builder().build()
    val type = Types.newParameterizedType(MutableMap::class.java, K::class.java, V::class.java)
    val adapter = moshi.adapter<Map<K, V>>(type).nullSafe()
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

internal val format by lazy {
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

internal suspend fun ApplicationCall.respondJson(text: String, status: HttpStatusCode? = null, configure: OutgoingContent.() -> Unit = {}) {
    respond(TextContent(text, ContentType.Application.Json, status).apply(configure))
}

/**
 * Safely checks if a file resides within the target directory, preventing path traversal attacks.
 */
internal fun File.isSafelyWithin(parentDir: File): Boolean {
    val canonicalParent = parentDir.canonicalFile.path
    val canonicalChild = this.canonicalFile.path
    return canonicalChild == canonicalParent || canonicalChild.startsWith(canonicalParent + File.separator)
}

/**
 * Verifies session authentication and evaluates a permission predicate.
 * Responds with HTTP 403 Forbidden and returns false if permission check fails.
 */
internal suspend inline fun PipelineContext<Unit, ApplicationCall>.checkPermission(
    settings: SharedPreferences,
    predicate: () -> Boolean
): Boolean {
    verifyLogin(settings)
    if (!predicate()) {
        call.respond(HttpStatusCode.Forbidden)
        return false
    }
    return true
}
