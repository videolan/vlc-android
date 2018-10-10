package org.videolan.vlc.util

import android.app.DownloadManager
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


class DownloadBroadcastReceiver: BroadcastReceiver() {
    var downloadedSubttile: LiveData<Long> = MutableLiveData<Long>()

    override fun onReceive(context: Context?, intent: Intent?) {
        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
        Log.d("Download: ", id.toString())
    }
}