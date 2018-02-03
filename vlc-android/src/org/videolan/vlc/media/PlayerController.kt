package org.videolan.vlc.media

import android.net.Uri
import android.support.annotation.MainThread
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import org.videolan.libvlc.*
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.RendererDelegate
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.VLCOptions

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
class PlayerController : IVLCVout.Callback, MediaPlayer.EventListener {

//    private val exceptionHandler by lazy(LazyThreadSafetyMode.NONE) { CoroutineExceptionHandler { _, _ -> onPlayerError() } }
    private val playerContext by lazy(LazyThreadSafetyMode.NONE) { newSingleThreadContext("vlc-player") }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { VLCApplication.getSettings() }

    private var mediaplayer = newMediaPlayer()
    var switchToVideo = false
    var seekable = false
    var pausable = false
    var previousMediaStats: Media.Stats? = null
        private set
    @Volatile var playbackState = PlaybackStateCompat.STATE_STOPPED
        private set
    @Volatile var hasRenderer = false
        private set
    @Volatile private var currentTime = 0L
    @Volatile var length = 0L
        private set

    fun getVout(): IVLCVout? = mediaplayer.vlcVout

    fun getMedia(): Media? = mediaplayer.media

    fun play() {
        if (mediaplayer.hasMedia()) mediaplayer.play()
    }

    fun pause(): Boolean {
        if (isPlaying() && mediaplayer.hasMedia() && pausable) {
            mediaplayer.pause()
            return true
        }
        return false
    }

    fun stop() {
        if (mediaplayer.hasMedia()) mediaplayer.stop()
        setPlaybackStopped()
    }

    fun releaseMedia() = mediaplayer.media?.let {
        it.setEventListener(null)
        it.release()
    }

    private var mediaplayerEventListener: MediaPlayer.EventListener? = null
    internal fun startPlayback(media: Media, listener: MediaPlayer.EventListener) {
        mediaplayerEventListener = listener
        seekable = true
        pausable = true
        currentTime = 0L
        length = media.duration
        mediaplayer.setEventListener(null)
        mediaplayer.media = media.apply { if (hasRenderer) parse() }
        mediaplayer.setEventListener(this@PlayerController)
        mediaplayer.setEqualizer(VLCOptions.getEqualizerSetFromSettings(VLCApplication.getAppContext()))
        mediaplayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0)
        mediaplayer.play()
        if (mediaplayer.rate == 1.0f && settings.getBoolean(PreferencesActivity.KEY_PLAYBACK_SPEED_PERSIST, true))
            setRate(settings.getFloat(PreferencesActivity.KEY_PLAYBACK_RATE, 1.0f), false)
    }

    @MainThread
    fun restart() {
        val mp = mediaplayer
        mediaplayer = newMediaPlayer()
        release(mp)
    }

    fun seek(position: Long, length: Double = this.length.toDouble()) {
        if (length > 0.0) setPosition((position / length).toFloat())
        else setTime(position)
    }

    fun setPosition(position: Float) {
        if (seekable) mediaplayer.position = position
    }

    fun setTime(time: Long) {
        if (seekable) mediaplayer.time = time
    }

    fun isPlaying() = playbackState == PlaybackStateCompat.STATE_PLAYING

    fun isVideoPlaying() = mediaplayer.vlcVout.areViewsAttached()

    fun canSwitchToVideo() = mediaplayer.hasMedia() && mediaplayer.videoTracksCount > 0

    fun getVideoTracksCount() = if (mediaplayer.hasMedia()) mediaplayer.videoTracksCount else 0

    fun getVideoTracks(): Array<out MediaPlayer.TrackDescription>? = mediaplayer.videoTracks

    fun getVideoTrack() = mediaplayer.videoTrack

    fun getCurrentVideoTrack(): Media.VideoTrack? = mediaplayer.currentVideoTrack

    fun getAudioTracksCount() = mediaplayer.audioTracksCount

    fun getAudioTracks(): Array<out MediaPlayer.TrackDescription>? = mediaplayer.audioTracks

    fun getAudioTrack() = mediaplayer.audioTrack

    fun setAudioTrack(index: Int) = mediaplayer.setAudioTrack(index)

    fun getAudioDelay() = mediaplayer.audioDelay

    fun getSpuDelay() = mediaplayer.spuDelay

    fun getRate() = mediaplayer.rate

    fun setSpuDelay(delay: Long) = mediaplayer.setSpuDelay(delay)

    fun setVideoTrackEnabled(enabled: Boolean) = mediaplayer.setVideoTrackEnabled(enabled)

    fun addSubtitleTrack(path: String, select: Boolean) = mediaplayer.addSlave(Media.Slave.Type.Subtitle, path, select)

    fun addSubtitleTrack(uri: Uri, select: Boolean) = mediaplayer.addSlave(Media.Slave.Type.Subtitle, uri, select)

    fun getSpuTracks(): Array<out MediaPlayer.TrackDescription>? = mediaplayer.spuTracks

    fun getSpuTrack() = mediaplayer.spuTrack

    fun setSpuTrack(index: Int) = mediaplayer.setSpuTrack(index)

    fun getSpuTracksCount() = mediaplayer.spuTracksCount

    fun setAudioDelay(delay: Long) = mediaplayer.setAudioDelay(delay)

    fun setEqualizer(equalizer: MediaPlayer.Equalizer?) = mediaplayer.setEqualizer(equalizer)

    @MainThread
    fun setVideoScale(scale: Float) {
        mediaplayer.scale = scale
    }

    fun setVideoAspectRatio(aspect: String?) {
        mediaplayer.aspectRatio = aspect
    }

    fun setRenderer(renderer: RendererItem?) {
        mediaplayer.setRenderer(renderer)
        hasRenderer = renderer !== null
    }

    fun release(player: MediaPlayer = mediaplayer) {
        player.setEventListener(null)
        if (isVideoPlaying()) player.vlcVout.detachViews()
        launch(newSingleThreadContext("vlc-player-release")) {
            if (BuildConfig.DEBUG) { // Warn if player release is blocking
                try {
                    withTimeout(5000, { player.release() })
                } catch (exception: TimeoutCancellationException) {
                    launch(UI) { Toast.makeText(VLCApplication.getAppContext(), "media stop has timeouted!", Toast.LENGTH_LONG).show() }
                }
            } else player.release()
        }
        setPlaybackStopped()
    }

    fun setSlaves(media: MediaWrapper) = launch {
        val list = MediaDatabase.getInstance().getSlaves(media.location)
        for (slave in list) mediaplayer.addSlave(slave.type, Uri.parse(slave.uri), false)
    }

    private fun newMediaPlayer() : MediaPlayer {
        return MediaPlayer(VLCInstance.get()).apply {
            VLCOptions.getAout(VLCApplication.getSettings())?.let { setAudioOutput(it) }
            setRenderer(RendererDelegate.selectedRenderer)
            this.vlcVout.addCallback(this@PlayerController)
        }
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout?) {}

    override fun onSurfacesDestroyed(vlcVout: IVLCVout?) {
        switchToVideo = false
    }

    fun getTime() = currentTime

    fun setRate(rate: Float, save: Boolean) {
        mediaplayer.rate = rate
        if (save && settings.getBoolean(PreferencesActivity.KEY_PLAYBACK_SPEED_PERSIST, true))
            settings.edit().putFloat(PreferencesActivity.KEY_PLAYBACK_RATE, rate).apply()
    }

    /**
     * Update current media meta and return true if player needs to be updated
     *
     * @param id of the Meta event received, -1 for none
     * @return true if UI needs to be updated
     */
    internal fun updateCurrentMeta(id: Int, mw: MediaWrapper?): Boolean {
        if (id == Media.Meta.Publisher) return false
        mw?.updateMeta(mediaplayer)
        return id != Media.Meta.NowPlaying || mw?.nowPlaying !== null
    }

    fun setPreviousStats() {
        val media = mediaplayer.media ?: return
        previousMediaStats = media.stats
        media.release()
    }

    fun updateViewpoint(yaw: Float, pitch: Float, roll: Float, fov: Float, absolute: Boolean) = mediaplayer.updateViewpoint(yaw, pitch, roll, fov, absolute)

    fun navigate(where: Int) = mediaplayer.navigate(where)

    fun getChapters(title: Int): Array<out MediaPlayer.Chapter>? = mediaplayer.getChapters(title)

    fun getTitles(): Array<out MediaPlayer.Title>? = mediaplayer.titles

    fun getChapterIdx() = mediaplayer.chapter

    fun setChapterIdx(chapter: Int) {
        mediaplayer.chapter = chapter
    }

    fun getTitleIdx() = mediaplayer.title

    fun setTitleIdx(title: Int) {
        mediaplayer.title = title
    }

    fun getVolume() = mediaplayer.volume

    fun setVolume(volume: Int) = mediaplayer.setVolume(volume)

    suspend fun expand(): MediaList? {
        return mediaplayer.media?.let {
            return async(playerContext) {
                mediaplayer.setEventListener(null)
                val items = it.subItems()
                it.release()
                mediaplayer.setEventListener(this@PlayerController)
                items
            }.await()
        }
    }

    override fun onEvent(event: MediaPlayer.Event?) {
        if (event === null) return
        when(event.type) {
            MediaPlayer.Event.Playing -> playbackState = PlaybackStateCompat.STATE_PLAYING
            MediaPlayer.Event.Paused -> playbackState = PlaybackStateCompat.STATE_PAUSED
            MediaPlayer.Event.EncounteredError -> setPlaybackStopped()
            MediaPlayer.Event.PausableChanged -> pausable = event.pausable
            MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
            MediaPlayer.Event.TimeChanged -> currentTime = event.timeChanged
            MediaPlayer.Event.LengthChanged -> length = event.lengthChanged
        }
        mediaplayerEventListener?.onEvent(event)
    }

    private fun setPlaybackStopped() {
        playbackState = PlaybackStateCompat.STATE_STOPPED
        currentTime = 0L
        length = 0L
    }

//    private fun onPlayerError() {
//        launch(UI) {
//            restart()
//            Toast.makeText(VLCApplication.getAppContext(), VLCApplication.getAppContext().getString(R.string.feedback_player_crashed), Toast.LENGTH_LONG).show()
//        }
//    }
}