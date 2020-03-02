package org.videolan.mobile.app.delegates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.launch
import org.videolan.moviepedia.provider.MediaScrapingTvshowProvider
import org.videolan.resources.ACTION_OPEN_CONTENT
import org.videolan.resources.EXTRA_CONTENT_ID
import org.videolan.tools.AppScope
import org.videolan.tools.localBroadcastManager
import org.videolan.vlc.media.MediaUtils


internal interface IMediaContentDelegate {
    fun Context.setupContentResolvers()
}

internal class MediaContentDelegate : BroadcastReceiver(), IMediaContentDelegate {
    override fun Context.setupContentResolvers() {
        localBroadcastManager.registerReceiver(this@MediaContentDelegate, IntentFilter(ACTION_OPEN_CONTENT))
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val id = intent?.getStringExtra(EXTRA_CONTENT_ID) ?: return
        AppScope.launch {
            val provider = MediaScrapingTvshowProvider.getProviders().firstOrNull { id.startsWith(it.prefix) } ?: return@launch
            provider.getList(context, id)?.let { results ->
                MediaUtils.openList(context, results.first, results.second)
            }
        }
    }
}
