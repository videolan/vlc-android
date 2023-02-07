package org.videolan.vlc

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.MediaBrowserInstance.init

object MediaBrowserInstance :
    SingletonHolder<MediaBrowserCompat, Context>({ init(it.applicationContext) }) {

    private lateinit var mediaBrowser: MediaBrowserCompat

    fun init(context: Context): MediaBrowserCompat {
        return MediaBrowserCompat(
            context,
            ComponentName(context, PlaybackService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {}

                override fun onConnectionSuspended() {}

                override fun onConnectionFailed() {}
            }, null
        ).apply {
            connect()
        }
    }
}