package org.videolan.vlc.util

import android.app.DownloadManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.repository.ExternalSubRepository
import java.io.File


object VLCDownloadManager: BroadcastReceiver(), LifecycleObserver {
    private val downloadManager = VLCApplication.getAppContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            val id = it.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
            id.let {
                val subtitleItem = ExternalSubRepository.getInstance(context!!).getDownloadingSubtitle(it)
                subtitleItem?.let {
                    val (state, localUri) = getDownloadState(id)
                    when(state) {
                        DownloadManager.STATUS_SUCCESSFUL -> downloadSuccessful(id, subtitleItem, localUri, context!!)
                        DownloadManager.STATUS_FAILED -> downloadFailed(id, context!!)
                    }
                }
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun register() {
        VLCApplication.getAppContext().applicationContext.registerReceiver(this, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun unRegister() {
        ExternalSubRepository.getInstance(VLCApplication.getAppContext()).downloadingSubtitles.observeForever {
            it?.keys?.forEach {
                downloadManager.remove(it)
            }
        }

        VLCApplication.getAppContext().applicationContext.unregisterReceiver(this)
    }

    fun download(context: Context, subtitleItem: SubtitleItem) {
        val request = DownloadManager.Request(Uri.parse(subtitleItem.zipDownloadLink))
        request.setDescription(subtitleItem.movieReleaseName)
        request.setTitle(context.resources.getString(R.string.download_subtitle_title))
        request.setVisibleInDownloadsUi(false)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, getDownloadPath(subtitleItem))
        val id = downloadManager.enqueue(request)
        ExternalSubRepository.getInstance(context.applicationContext!!).addDownloadingItem(id, subtitleItem)
    }

    private fun downloadSuccessful(id:Long, subtitleItem: SubtitleItem, localUri: String, context: Context) {
        val baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val extractDirectory = File(baseDirectory, "VLC").absolutePath
        val downloadedPaths = FileUtils.unpackZip(localUri, extractDirectory)
        subtitleItem.apply {
            ExternalSubRepository.getInstance(context).removeDownloadingItem(id)
            downloadedPaths.forEach {
                if (it.endsWith(".srt"))
                    ExternalSubRepository.getInstance(context).saveDownloadedSubtitle(idSubtitle, it, mediaPath, subLanguageID, movieReleaseName)
            }
            FileUtils.deleteFile(localUri)
        }
    }

    private fun downloadFailed(id: Long, context: Context) {
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

        return Pair(status, if (localUri != null) Uri.parse(localUri).path else "")
    }
}