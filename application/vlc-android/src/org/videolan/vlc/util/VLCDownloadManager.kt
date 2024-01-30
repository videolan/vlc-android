package org.videolan.vlc.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.Extensions
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.registerReceiverCompat
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.gui.helpers.hf.getExtWritePermission
import org.videolan.vlc.repository.ExternalSubRepository


object VLCDownloadManager: BroadcastReceiver(), DefaultLifecycleObserver {
    private val downloadManager = AppContextProvider.appContext.getSystemService<DownloadManager>()!!
    private var dlDeferred : CompletableDeferred<SubDlResult>? = null
    private lateinit var defaultSubsDirectory : String

    override fun onReceive(context: Context, intent: Intent?) {
        intent?.run {
            val id = getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
            ExternalSubRepository.getInstance(context).getDownloadingSubtitle(id)?.let { subtitleItem ->
                val (state, localUri) = getDownloadState(id)
                when(state) {
                    DownloadManager.STATUS_SUCCESSFUL -> dlDeferred?.complete(SubDlSuccess(id, subtitleItem, localUri))
                    DownloadManager.STATUS_FAILED -> dlDeferred?.complete(SubDlFailure(id))
                    else -> return
                }
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        AppContextProvider.appContext.applicationContext.registerReceiverCompat(this, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), true)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        ExternalSubRepository.getInstance(AppContextProvider.appContext).downloadingSubtitles.observeForever { map ->
            map?.keys?.forEach {
                downloadManager.remove(it)
            }
        }

        AppContextProvider.appContext.applicationContext.unregisterReceiver(this)
    }

    suspend fun download(context: FragmentActivity, subtitleItem: SubtitleItem) {
        val request = DownloadManager.Request(subtitleItem.zipDownloadLink.toUri())
        request.setDescription(subtitleItem.movieReleaseName)
        request.setTitle(context.resources.getString(R.string.download_subtitle_title))
        request.setDestinationInExternalFilesDir(context, getDownloadPath(subtitleItem), "")
        val id = downloadManager.enqueue(request)
        val deferred = CompletableDeferred<SubDlResult>().also { dlDeferred = it }
        ExternalSubRepository.getInstance(context.applicationContext).addDownloadingItem(id, subtitleItem)
        when (val result = deferred.await()) {
            is SubDlFailure -> downloadFailed(result.id, context)
            is SubDlSuccess -> downloadSuccessful(result.id, result.subtitleItem, result.localUri, context)
        }
    }

    private suspend fun downloadSuccessful(id:Long, subtitleItem: SubtitleItem, localUri: String, context: FragmentActivity) {
        val extractDirectory = getFinalDirectory(context, subtitleItem) ?: return
        val downloadedPaths = FileUtils.unpackZip(localUri, extractDirectory)
        subtitleItem.run {
            ExternalSubRepository.getInstance(context).removeDownloadingItem(id)
            downloadedPaths.forEach {
                if (Extensions.SUBTITLES.contains(".${it.split('.').last()}")) {
                    ExternalSubRepository.getInstance(context).saveDownloadedSubtitle(
                        idSubtitle,
                        it,
                        mediaUri.path!!,
                        subLanguageID,
                        movieReleaseName
                    )
                }
                else
                    Toast.makeText(context, R.string.subtitles_download_failed, Toast.LENGTH_SHORT).show()
            }
            withContext(Dispatchers.IO) { FileUtils.deleteFile(localUri) }
        }
    }

    private suspend fun getFinalDirectory(context: FragmentActivity, subtitleItem: SubtitleItem) : String? {
        if (!this::defaultSubsDirectory.isInitialized) defaultSubsDirectory = "${context.applicationContext.getExternalFilesDir(null)!!.absolutePath}/subtitles"
        if (subtitleItem.mediaUri.scheme != "file") return defaultSubsDirectory
        val folder = subtitleItem.mediaUri.path.getParentFolder() ?: return context.getExternalFilesDir("subs")?.absolutePath
        val canWrite = context.isStarted() && context.getExtWritePermission(folder.toUri())
        return if (canWrite) folder
        else (context.applicationContext.getExternalFilesDir(null))?.absolutePath ?: defaultSubsDirectory
    }

    private fun downloadFailed(id: Long, context: Context) {
        Toast.makeText(context, R.string.subtitles_download_failed, Toast.LENGTH_SHORT).show()
        ExternalSubRepository.getInstance(context).removeDownloadingItem(id)
    }

    private fun getDownloadPath(subtitleItem: SubtitleItem) = "VLC/${subtitleItem.movieReleaseName}_${subtitleItem.idSubtitle}.zip"

    private fun getDownloadState(downloadId: Long): Pair<Int, String> {
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        cursor.moveToFirst()
        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

        val status = if (statusIndex != -1)
            cursor.getInt(statusIndex)
        else DownloadManager.STATUS_FAILED

        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val localUri = if (localUriIndex != -1)
            cursor.getString(localUriIndex)
        else ""

        return Pair(status, if (localUri != null) localUri.toUri().path!! else "")
    }
}

private sealed class SubDlResult
private class SubDlSuccess(val id:Long, val subtitleItem: SubtitleItem, val localUri: String) : SubDlResult()
private class SubDlFailure(val id:Long) : SubDlResult()
