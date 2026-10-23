package org.videolan.vlc.media

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.Calendar

/**
 * Interface abstracting operations performed by [PlaylistManager] on the playback service.
 */
interface PlaybackServiceCallback {
    val context: Context
    val lifecycleOwner: LifecycleOwner
    fun getServiceResources(): Resources
    val isInPiPMode: LiveData<Boolean>
    var waitForMediaEnd: Boolean
    var resetOnInteraction: Boolean

    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String
    fun onPlaylistLoaded()
    fun onMediaListChanged()
    fun onNewPlayback()
    fun onPlaybackStopped(systemExit: Boolean)
    fun onMediaEvent(event: IMedia.Event)
    fun onMediaPlayerEvent(event: MediaPlayer.Event)
    fun notifyTrackChanged()
    fun showNotification(): Boolean
    fun showToast(text: String, duration: Int, isError: Boolean = false)
    fun executeUpdate(pubState: Boolean = false)
    suspend fun awaitMedialibraryStarted(): Boolean
    fun setSleepTimer(time: Calendar?)
    fun setTime(time: Long, fast: Boolean = false)
    fun loadLastAudio()
}
