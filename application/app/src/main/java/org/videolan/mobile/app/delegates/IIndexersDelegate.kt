package org.videolan.mobile.app.delegates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.launch
import org.videolan.moviepedia.MediaScraper
import org.videolan.resources.ACTION_CONTENT_INDEXING
import org.videolan.resources.util.registerReceiverCompat
import org.videolan.tools.AppScope

internal interface IIndexersDelegate {
    fun Context.setupIndexers()
}

internal class IndexersDelegate : BroadcastReceiver(), IIndexersDelegate {

    override fun Context.setupIndexers() {
        registerReceiverCompat(this@IndexersDelegate, IntentFilter(ACTION_CONTENT_INDEXING), false)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        AppScope.launch {
            MediaScraper.indexListener.onIndexingDone()
        }
    }
}