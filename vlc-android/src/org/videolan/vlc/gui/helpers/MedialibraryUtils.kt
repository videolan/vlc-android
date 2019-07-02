package org.videolan.vlc.gui.helpers


import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.util.ACTION_DISCOVER
import org.videolan.vlc.util.ACTION_DISCOVER_DEVICE
import org.videolan.vlc.util.EXTRA_PATH
import org.videolan.vlc.util.runIO

object MedialibraryUtils {

    fun removeDir(path: String) {
        runIO(Runnable { AbstractMedialibrary.getInstance().removeFolder(path) })
    }

    @JvmOverloads
    fun addDir(path: String, context: Context = VLCApplication.appContext) {
        val intent = Intent(ACTION_DISCOVER, null, context, MediaParsingService::class.java)
        intent.putExtra(EXTRA_PATH, path)
        ContextCompat.startForegroundService(context, intent)
    }

    fun addDevice(path: String, context: Context) {
        val intent = Intent(ACTION_DISCOVER_DEVICE, null, context, MediaParsingService::class.java)
        intent.putExtra(EXTRA_PATH, path)
        ContextCompat.startForegroundService(context, intent)
    }
}
