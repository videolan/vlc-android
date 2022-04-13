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
import kotlinx.coroutines.*
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.getFromMl
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.media.MediaPlayerEventListener
import org.videolan.vlc.media.PlayerController
import org.videolan.resources.VLCInstance
import org.videolan.vlc.util.random
import java.io.IOException

private const val TAG = "PreviewInputService"

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class PreviewVideoInputService : TvInputService(), CoroutineScope by MainScope() {

    internal val factory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory

    override fun onCreateSession(inputId: String): TvInputService.Session? {
        return PreviewSession(this)
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
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
                val mw = getFromMl { getMedia(id) }
                if (mw == null) {
                    Log.w(TAG, "Could not find video $id")
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                    return@launch
                }
                try {
                    val media = factory.getFromUri(VLCInstance.getInstance(this@PreviewVideoInputService), mw.uri)
                    val start = if (mw.length <= 0L) 0L else mw.length.random()
                    media.addOption(":start-time=${start/1000L}")
                    awaitSurface()
                    player.getVout()?.apply {
                        setVideoSurface(surface, null)
                        attachViews(null)
                        setWindowSize(width, height)
                    }
                    player.setVideoAspectRatio(null)
                    player.setVideoScale(0f)
                    player.startPlayback(media, this@PreviewSession, start)
                    notifyVideoAvailable()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not prepare media player", e)
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Could not prepare media player", e)
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                }
            }
            return true
        }

        private var width = 0
        private var height = 0
        private lateinit var surface: Surface
        private var surfaceReady : CompletableDeferred<Unit>? = null
        override fun onSetSurface(surface: Surface?): Boolean {
            if (surface == null) return false
            this.surface = surface
            surfaceReady?.complete(Unit)
            return true
        }

        override fun onSurfaceChanged(format: Int, width: Int, height: Int) {
            this.width = width
            this.height = height
        }

        override fun onSetStreamVolume(volume: Float) {}

        override fun onSetCaptionEnabled(enabled: Boolean) {}

        override suspend fun onEvent(event: MediaPlayer.Event) {
            when(event.type) {
                MediaPlayer.Event.EndReached -> player.release()
            }
        }

        private suspend fun awaitSurface() {
            if (!::surface.isInitialized) {
                surfaceReady = CompletableDeferred(Unit)
                surfaceReady?.await()
                surfaceReady = null
            }
        }
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }
}