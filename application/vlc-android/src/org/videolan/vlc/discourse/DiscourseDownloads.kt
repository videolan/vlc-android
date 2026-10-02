package org.videolan.vlc.discourse

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.content.getSystemService
import org.videolan.tools.Settings
import java.io.File
import java.net.URI

private const val CDN_BASE_URL = "https://osho.b-cdn.net/OSHO/"
private const val KEY_DOWNLOADS = "osho_api_discourse_downloads"
private val URL_SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*:.*")

internal fun resolveDiscourseUrl(value: String?): String? {
    val path = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val absolute = runCatching { URI(path) }.getOrNull()
    if (absolute?.isAbsolute == true) return absolute.takeIf { it.scheme.equals("https", true) }?.toASCIIString()
    if (URL_SCHEME.matches(path.substringBefore('/'))) return null
    return runCatching {
        URI(CDN_BASE_URL).resolve(URI(null, null, path.trimStart('/'), null)).toASCIIString()
    }.getOrNull()
}

enum class DiscourseDownloadState { MISSING, DOWNLOADING, DOWNLOADED, FAILED }

internal fun isVerifiedDownload(actualSize: Long?, expectedSize: Long?) =
    actualSize != null && (expectedSize == null || expectedSize <= 0L || actualSize == expectedSize)

internal fun shouldEnqueue(state: DiscourseDownloadState) =
    state != DiscourseDownloadState.DOWNLOADED && state != DiscourseDownloadState.DOWNLOADING

class DiscourseDownloadStore(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService<DownloadManager>()!!
    private val settings = Settings.getInstance(context)

    fun state(audio: DiscourseAudio): DiscourseDownloadState {
        if (verifiedFile(audio) != null) return DiscourseDownloadState.DOWNLOADED
        val id = downloads()[audio.id] ?: return DiscourseDownloadState.MISSING
        return when (status(id)) {
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> DiscourseDownloadState.DOWNLOADING
            DownloadManager.STATUS_SUCCESSFUL -> DiscourseDownloadState.FAILED
            else -> DiscourseDownloadState.FAILED
        }
    }

    fun playbackUri(audio: DiscourseAudio): Uri? = verifiedFile(audio)?.let(Uri::fromFile)
        ?: resolveDiscourseUrl(audio.audioUrl)?.let(Uri::parse)

    fun download(audio: DiscourseAudio): Boolean {
        if (!shouldEnqueue(state(audio))) return true
        val url = resolveDiscourseUrl(audio.audioUrl) ?: return false
        remove(audio)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(audio.title)
            .setDescription(audio.discourseName)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
            .setDestinationInExternalFilesDir(context, "discourses", fileName(audio))
        audio.mimeType?.let(request::setMimeType)
        val id = runCatching { manager.enqueue(request) }.getOrNull() ?: return false
        save(downloads() + (audio.id to id))
        return true
    }

    fun cancel(audio: DiscourseAudio) = remove(audio)

    fun remove(audio: DiscourseAudio) {
        val current = downloads().toMutableMap()
        current.remove(audio.id)?.let { manager.remove(it) }
        file(audio).delete()
        save(current)
    }

    private fun verifiedFile(audio: DiscourseAudio): File? = file(audio).takeIf { isVerifiedDownload(it.takeIf(File::isFile)?.length(), audio.fileSize) }

    private fun file(audio: DiscourseAudio) = File(context.getExternalFilesDir("discourses") ?: context.filesDir, fileName(audio))
    private fun fileName(audio: DiscourseAudio) = "${audio.id}.audio"

    private fun status(id: Long): Int = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
        if (!cursor.moveToFirst()) DownloadManager.STATUS_FAILED
        else cursor.int(DownloadManager.COLUMN_STATUS) ?: DownloadManager.STATUS_FAILED
    }

    private fun downloads(): Map<String, Long> = settings.getStringSet(KEY_DOWNLOADS, emptySet()).orEmpty().mapNotNull {
        val parts = it.split('|', limit = 2)
        parts.getOrNull(1)?.toLongOrNull()?.let { id -> parts[0] to id }
    }.toMap()

    private fun save(downloads: Map<String, Long>) {
        settings.edit().putStringSet(KEY_DOWNLOADS, downloads.mapTo(mutableSetOf()) { "${it.key}|${it.value}" }).apply()
    }

    private fun Cursor.int(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let(::getInt)

}
