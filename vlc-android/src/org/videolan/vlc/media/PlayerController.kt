package org.videolan.vlc.media

import android.content.Context
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.*
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.RendererDelegate
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.repository.SlaveRepository
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.VLCOptions
import kotlin.math.abs

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
class PlayerController(val context: Context) : IVLCVout.Callback, MediaPlayer.EventListener, CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate

    //    private val exceptionHandler by lazy(LazyThreadSafetyMode.NONE) { CoroutineExceptionHandler { _, _ -> onPlayerError() } }
    private val playerContext by lazy(LazyThreadSafetyMode.NONE) { newSingleThreadContext("vlc-player") }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(context) }
    val progress by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Progress>().apply { value = Progress() } }
    private val slaveRepository by lazy { SlaveRepository.getInstance(context) }

    var mediaplayer = newMediaPlayer()
        private set
    var switchToVideo = false
    var seekable = false
    var pausable = false
    var previousMediaStats: Media.Stats? = null
        private set
    @Volatile var playbackState = PlaybackStateCompat.STATE_STOPPED
        private set
    @Volatile var hasRenderer = false
        private set

    fun getVout(): IVLCVout? = mediaplayer.vlcVout

    fun canDoPassthrough() = mediaplayer.canDoPassthrough()

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

    private fun releaseMedia() = mediaplayer.media?.let {
        it.setEventListener(null)
        it.release()
    }

    private var mediaplayerEventListener: MediaPlayerEventListener? = null
    internal fun startPlayback(media: Media, listener: MediaPlayerEventListener) {
        mediaplayerEventListener = listener
        resetPlaybackState(media.duration)
        mediaplayer.setEventListener(null)
        mediaplayer.media = media.apply { if (hasRenderer) parse() }
        mediaplayer.setEventListener(this@PlayerController)
        mediaplayer.setEqualizer(VLCOptions.getEqualizerSetFromSettings(context))
        mediaplayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0)
        mediaplayer.play()
        if (mediaplayer.rate == 1.0f && settings.getBoolean(PreferencesActivity.KEY_PLAYBACK_SPEED_PERSIST, true))
            setRate(settings.getFloat(PreferencesActivity.KEY_PLAYBACK_RATE, 1.0f), false)
    }

    private fun resetPlaybackState(duration: Long) {
        seekable = true
        pausable = true
        lastTime = 0L
        updateProgress(0L, duration)
    }

    @MainThread
    fun restart() {
        val mp = mediaplayer
        mediaplayer = newMediaPlayer()
        release(mp)
    }

    fun seek(position: Long, length: Double = getLength().toDouble()) {
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

    fun setVideoTrack(index: Int) = mediaplayer.setVideoTrack(index)

    fun setAudioTrack(index: Int) = mediaplayer.setAudioTrack(index)

    fun setAudioDigitalOutputEnabled(enabled: Boolean) = mediaplayer.setAudioDigitalOutputEnabled(enabled)

    fun getAudioDelay() = mediaplayer.audioDelay

    fun getSpuDelay() = mediaplayer.spuDelay

    fun getRate() = if (mediaplayer.hasMedia() && playbackState != PlaybackStateCompat.STATE_STOPPED) mediaplayer.rate else 1.0f

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
        releaseMedia()
        launch(Dispatchers.IO) {
            if (BuildConfig.DEBUG) { // Warn if player release is blocking
                try {
                    withTimeout(5000, { player.release() })
                } catch (exception: TimeoutCancellationException) {
                    launch { Toast.makeText(context, "media stop has timeouted!", Toast.LENGTH_LONG).show() }
                }
            } else player.release()
        }
        setPlaybackStopped()
    }

    fun setSlaves(media: Media, mw: MediaWrapper) = launch {
        val slaves = mw.slaves
        slaves?.let { it.forEach { slave -> media.addSlave(slave) } }
        media.release()
        slaves?.let {
            slaveRepository.saveSlaves(mw)?.forEach { it.join() }
        }
        slaveRepository.getSlaves(mw.location).forEach { slave ->
            mediaplayer.addSlave(slave.type, Uri.parse(slave.uri), false)
        }
    }

    private fun newMediaPlayer() : MediaPlayer {
        return MediaPlayer(VLCInstance.get()).apply {
            setAudioDigitalOutputEnabled(VLCOptions.isAudioDigitalOutputEnabled(settings))
            VLCOptions.getAout(settings)?.let { setAudioOutput(it) }
            setRenderer(RendererDelegate.selectedRenderer.value)
            this.vlcVout.addCallback(this@PlayerController)
        }
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout?) {}

    override fun onSurfacesDestroyed(vlcVout: IVLCVout?) {
        switchToVideo = false
    }

    fun getCurrentTime() = progress.value?.time ?: 0L

    fun getLength() = progress.value?.length ?: 0L

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

    fun getVolume() = if (mediaplayer.hasMedia()) mediaplayer.volume else 100

    fun setVolume(volume: Int) = mediaplayer.setVolume(volume)

    suspend fun expand(): MediaList? {
        return mediaplayer.media?.let {
            return withContext(playerContext) {
                mediaplayer.setEventListener(null)
                val items = it.subItems()
                it.release()
                mediaplayer.setEventListener(this@PlayerController)
                items
            }
        }
    }

    private var lastTime = 0L
    private val eventActor = actor<MediaPlayer.Event>(capacity = Channel.UNLIMITED) {
        for (event in channel) {
            when (event.type) {
                MediaPlayer.Event.Playing -> playbackState = PlaybackStateCompat.STATE_PLAYING
                MediaPlayer.Event.Paused -> playbackState = PlaybackStateCompat.STATE_PAUSED
                MediaPlayer.Event.EncounteredError -> setPlaybackStopped()
                MediaPlayer.Event.PausableChanged -> pausable = event.pausable
                MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
                MediaPlayer.Event.LengthChanged -> updateProgress(newLength = event.lengthChanged)
                MediaPlayer.Event.TimeChanged -> {
                    val time = event.timeChanged
                    if (abs(time - lastTime) > 950L) {
                        updateProgress(newTime = time)
                        lastTime = time
                    }
                }
            }
            mediaplayerEventListener?.onEvent(event)
        }
    }

    @JvmOverloads
    fun updateProgress(newTime: Long = progress.value?.time ?: 0L, newLength: Long = progress.value?.length ?: 0L) {
        progress.value = progress.value?.apply { time = newTime; length = newLength }
    }

    override fun onEvent(event: MediaPlayer.Event?) {
        if (event != null) eventActor.offer(event)
    }

    private fun setPlaybackStopped() {
        playbackState = PlaybackStateCompat.STATE_STOPPED
        updateProgress(0L, 0L)
        lastTime = 0L
    }

//    private fun onPlayerError() {
//        launch(UI) {
//            restart()
//            Toast.makeText(context, context.getString(R.string.feedback_player_crashed), Toast.LENGTH_LONG).show()
//        }
//    }
}

class Progress(var time: Long = 0L, var length: Long = 0L)

internal interface MediaPlayerEventListener {
    suspend fun onEvent(event: MediaPlayer.Event)
}