package org.videolan.vlc


import android.annotation.TargetApi
import android.content.Context
import android.media.tv.TvInputManager
import android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
import android.media.tv.TvInputService
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.vlc.media.MediaPlayerEventListener
import org.videolan.vlc.media.PlayerController
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.getFromMl
import org.videolan.vlc.util.random
import java.io.IOException

private const val TAG = "PreviewInputService"

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class PreviewVideoInputService : TvInputService(), CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate

    override fun onCreateSession(inputId: String): TvInputService.Session? {
        return PreviewSession(this)
    }

    private inner class PreviewSession(context: Context
    ) : TvInputService.Session(context), MediaPlayerEventListener {

        val player by lazy(LazyThreadSafetyMode.NONE) { PlayerController(applicationContext) }

        override fun onRelease() {
            player.release()
        }

        override fun onTune(uri: Uri): Boolean {
            notifyVideoUnavailable(VIDEO_UNAVAILABLE_REASON_TUNING)
            val id = uri.lastPathSegment?.toLong() ?: return false
            launch {
                val mw = this@PreviewVideoInputService.getFromMl { getMedia(id) }
                if (mw == null) {
                    Log.w(TAG, "Could not find video $id")
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                    return@launch
                }
                try {
                    val media = Media(VLCInstance.get(), mw.uri)
                    val start = if (mw.length <= 0L) 0 else mw.length.random()/1000
                    media.addOption(":start-time=$start")
                    player.getVout()?.apply {
                        setVideoSurface(surface, null)
                        attachViews(null)
                        setWindowSize(width, height)
                    }
                    player.setVideoAspectRatio(null)
                    player.setVideoScale(0f)
                    player.startPlayback(media, this@PreviewSession)
                    notifyVideoAvailable()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not prepare media player", e)
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                }
            }
            return true
        }

        private var width = 0
        private var height = 0
        private lateinit var surface: Surface
        override fun onSetSurface(surface: Surface?): Boolean {
            if (surface == null) return false
            this.surface = surface
            return true
        }

        override fun onSurfaceChanged(format: Int, width: Int, height: Int) {
            this.width = width
            this.height = height
        }

        override fun onSetStreamVolume(volume: Float) {
            player.setVolume((volume*100).toInt())
        }

        override fun onSetCaptionEnabled(enabled: Boolean) {}

        override suspend fun onEvent(event: MediaPlayer.Event) {
            when(event.type) {
                MediaPlayer.Event.EndReached -> player.release()
            }
        }
    }
}