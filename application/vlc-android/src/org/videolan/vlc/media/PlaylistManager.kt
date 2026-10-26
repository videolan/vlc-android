package org.videolan.vlc.media

import org.videolan.vlc.media.queue.QueueExpander
import org.videolan.vlc.media.control.ABRepeatController
import org.videolan.vlc.media.persistence.PlaybackPersistenceManager
import org.videolan.vlc.media.queue.QueueNavigator
import org.videolan.vlc.media.queue.PlayQueue
import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.EXIT_PLAYER
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.resources.PLAY_FROM_SERVICE
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.resources.util.VLCCrashHandler
import org.videolan.tools.AUDIO_DELAY_GLOBAL
import org.videolan.tools.AUDIO_RESUME_PLAYBACK
import org.videolan.tools.AUDIO_SHUFFLING
import org.videolan.tools.AUDIO_STOP_AFTER
import org.videolan.tools.AppScope
import org.videolan.tools.DAV1D_THREAD_NUMBER
import org.videolan.tools.HTTP_USER_AGENT
import org.videolan.tools.KEY_ALWAYS_FAST_SEEK
import org.videolan.tools.KEY_AUDIO_CONFIRM_RESUME
import org.videolan.tools.KEY_AUDIO_FORCE_SHUFFLE
import org.videolan.tools.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.tools.KEY_CURRENT_AUDIO
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_ARTIST
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_THUMB
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_TITLE
import org.videolan.tools.KEY_CURRENT_MEDIA
import org.videolan.tools.KEY_CURRENT_MEDIA_IS_AUDIO
import org.videolan.tools.KEY_CURRENT_MEDIA_RESUME
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST_RESUME
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.KEY_SAVE_INDIVIDUAL_AUDIO_DELAY
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.KEY_VIDEO_CONFIRM_RESUME
import org.videolan.tools.MEDIA_SHUFFLING
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.POSITION_IN_AUDIO_LIST
import org.videolan.tools.POSITION_IN_MEDIA
import org.videolan.tools.POSITION_IN_MEDIA_LIST
import org.videolan.tools.POSITION_IN_SONG
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_PAUSED
import org.videolan.tools.VIDEO_RESUME_PLAYBACK
import org.videolan.tools.VIDEO_SPEED
import org.videolan.tools.isAppStarted
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.persistence.ResumeStatus
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.FontCache
import org.videolan.vlc.util.awaitMedialibraryStarted
import org.videolan.vlc.util.isSchemeFD
import org.videolan.vlc.util.isSchemeHttpOrHttps
import org.videolan.vlc.util.isSchemeStreaming
import org.videolan.vlc.util.setResumeProgram
import org.videolan.vlc.util.updateNextProgramAfterThumbnailGeneration
import org.videolan.vlc.util.updateWithMLMeta
import org.videolan.vlc.util.validateLocation
import java.security.SecureRandom
import java.util.Calendar
import java.util.Stack
import kotlin.math.max

private const val TAG = "VLC/PlaylistManager"
internal const val PREVIOUS_LIMIT_DELAY = 5000L
internal const val PLAYLIST_AUDIO_REPEAT_MODE_KEY = "audio_repeat_mode"
internal const val PLAYLIST_VIDEO_REPEAT_MODE_KEY = "video_repeat_mode"

class PlaylistManager(val service: PlaybackService) : PlaylistOrchestrator, MediaWrapperList.EventListener, IMedia.EventListener {
    override var endReachedFor: String? = null
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    companion object {
        val showAudioPlayer = MutableLiveData<Boolean>().apply { value = false }
        val currentPlayedMedia = MutableLiveData<MediaWrapper?>().apply { value = null }
        val playingState = MutableLiveData<Boolean>().apply { value = false }
        // The playback will periodically modify the media by saving its meta
        // When playing audio, it will have no impact on UI but will trigger the media modified ML callback
        // On slow devices, it will trigger an unwanted "refresh animation". This flag prevents it
        var skipMediaUpdateRefresh = false
        private val mediaList = MediaWrapperList()
        fun hasMedia() = mediaList.size() != 0
        val repeating = MutableStateFlow(PlaybackStateCompat.REPEAT_MODE_NONE)
        val shuffling = MutableStateFlow(false)
        var playingAsAudio = false
    }

    private val medialibrary by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
    val player by lazy(LazyThreadSafetyMode.NONE) { PlayerController(service.applicationContext) }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(service) }
    private val ctx by lazy(LazyThreadSafetyMode.NONE) { service.applicationContext }
    val persistenceManager by lazy(LazyThreadSafetyMode.NONE) { PlaybackPersistenceManager(settings, medialibrary) }
    val playQueue = PlayQueue(mediaList)
    override var currentIndex: Int
        get() = playQueue.currentIndex
        set(value) {
            playQueue.currentIndex = value
        }
    override val navigator = QueueNavigator(repeating, PlaylistManager.shuffling)
    override var nextIndex: Int
        get() = navigator.nextIndex
        set(value) { navigator.nextIndex = value }
    private var prevIndex: Int
        get() = navigator.prevIndex
        set(value) { navigator.prevIndex = value }
    private val previous: Stack<Int>
        get() = navigator.previous
    override var stopAfter = -1
    override var shuffling: Boolean
        get() = navigator.isShuffling
        set(value) {
            navigator.isShuffling = value
        }
    override var videoBackground = false
    override var isBenchmark = false
    override var isHardware = false
    override var browserAudioActive = false
    override var preBrowserVolume = -1
    override var parsed = false
    override var savedTime = 0L
    private var random = SecureRandom()
    override var newMedia = false
    @Volatile
    private var expanding = false
    override var entryUrl : String? = null
    val queueExpander by lazy(LazyThreadSafetyMode.NONE) { QueueExpander(this, player, playQueue, medialibrary, service) }
    val abRepeatController by lazy(LazyThreadSafetyMode.NONE) { ABRepeatController(settings) }
    override val abRepeat: MutableLiveData<ABRepeat> get() = abRepeatController.abRepeat
    val abRepeatOn: MutableLiveData<Boolean> get() = abRepeatController.abRepeatOn
    val videoStatsOn: MutableLiveData<Boolean> get() = abRepeatController.videoStatsOn
    val delayValue: MutableLiveData<DelayValues> get() = abRepeatController.delayValue
    override val waitForConfirmation: MutableLiveData<WaitConfirmation?> get() = abRepeatController.waitForConfirmation
    override val waitForConfirmationAudio: MutableLiveData<WaitConfirmation?> get() = abRepeatController.waitForConfirmationAudio
    private var lastPrevious = -1L

    private val mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
    override lateinit var videoResumeStatus: ResumeStatus
    override lateinit var audioResumeStatus: ResumeStatus

    fun hasCurrentMedia() = playQueue.isValidPosition(currentIndex)

    fun canRepeat() = playQueue.canRepeat()

    fun hasPlaylist() = playQueue.hasPlaylist()

    override fun canShuffle() = playQueue.canShuffle()

    fun isValidPosition(position: Int) = playQueue.isValidPosition(position)
    override var shouldDisableCookieForwarding: Boolean = false

    override fun notifyUpdate() {
        addUpdateActor.trySend(Unit)
    }

    init {
        AppScope.launch { repeating.emit(settings.getInt(PLAYLIST_AUDIO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)) }
        resetResumeStatus()
    }

    fun resetResumeStatus() {
        videoResumeStatus = persistenceManager.loadResumeStatus(KEY_VIDEO_CONFIRM_RESUME)
        audioResumeStatus = persistenceManager.loadResumeStatus(KEY_AUDIO_CONFIRM_RESUME)
        waitForConfirmation.postValue(null)
        waitForConfirmationAudio.postValue(null)
    }

    /**
     * Loads a selection of files (a non-user-supplied collection of media)
     * into the primary or "currently playing" playlist.
     *
     * @param mediaPathList A list of locations to load
     * @param position The position to start playing at
     */
    @MainThread
    fun loadLocations(mediaPathList: List<String>, position: Int) {
        if (BuildConfig.BETA) {
            Log.d(TAG, "loadLocations with values: ", Exception("Call stack"))
            mediaPathList.forEach {
                try {
                    Log.d(TAG, "Media location: $it")
                } catch (e: NullPointerException) {
                    Log.d(TAG, "Media crash", e)
                }
            }
        }
        launch {
            val mediaList = ArrayList<MediaWrapper>()
            withContext(Dispatchers.IO) {
                for (location in mediaPathList) {
                    var mediaWrapper = medialibrary.getMedia(location)
                    if (mediaWrapper === null) {
                        if (!location.validateLocation()) {
                            Log.w(TAG, "Invalid location $location")
                            service.showToast(if (location.toUri().scheme == "missing")service.resources.getString(R.string.missing_location) else service.resources.getString(R.string.invalid_location,  location), Toast.LENGTH_SHORT)
                            continue
                        }
                        Log.v(TAG, "Creating on-the-fly Media object for $location")
                        mediaWrapper = MLServiceLocator.getAbstractMediaWrapper(location.toUri())
                        if (BuildConfig.BETA) Log.d(TAG, "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
                    } else {
                        if (BuildConfig.BETA) Log.d(TAG, "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
                    }
                }
            }
            load(mediaList, position)
        }
    }

    @MainThread
    suspend fun load(list: List<MediaWrapper>, position: Int, mlUpdate: Boolean = false, avoidErasingStop:Boolean = false) {
        saveMediaList()
        savePosition()
        mediaList.removeEventListener(this@PlaylistManager)
        previous.clear()
        videoBackground = false
        if (BuildConfig.BETA) {
            Log.d(TAG, "load with values: ", Exception("Call stack"))
            list.forEach {
                try {
                    Log.d(TAG, "Media location: ${it.uri}")
                } catch (e: NullPointerException) {
                    Log.d(TAG, "Media crash", e)
                }
            }
        }
        mediaList.replaceWith(list)
        if (!hasMedia()) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        currentIndex = if (isValidPosition(position)) position else 0

        // Add handler after loading the list
        mediaList.addEventListener(this@PlaylistManager)
        val instance = Settings.getInstance(AppContextProvider.appContext)
        if (!avoidErasingStop) instance.putSingle(AUDIO_STOP_AFTER, -1)
        stopAfter = instance.getInt(AUDIO_STOP_AFTER, -1)
        if (stopAfter < position) stopAfter = -1
        clearABRepeat()
        player.setRate(1.0f, false)
        playIndex(currentIndex)
        service.onPlaylistLoaded()
        if (mlUpdate) {
            service.awaitMedialibraryStarted()
            mediaList.replaceWith(withContext(Dispatchers.IO) { mediaList.copy.updateWithMLMeta() })
            getCurrentMedia()?.let { refreshTrackMeta(it) }
            if (BuildConfig.BETA) {
                Log.d(TAG, "load after ml update with values: ")
                mediaList.copy.forEach { Log.d(TAG, "Media location: ${it.uri}") }
            }
            service.onMediaListChanged()
            service.showNotification()
        }
        if (settings.getBoolean(KEY_AUDIO_FORCE_SHUFFLE, false) && getCurrentMedia()?.type == MediaWrapper.TYPE_AUDIO && !shuffling && canShuffle()) shuffle()
        if (settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L) != -1L) {
            service.waitForMediaEnd = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
            service.resetOnInteraction = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
            val sleepTime = Calendar.getInstance()
            sleepTime.timeInMillis += settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
            sleepTime.set(Calendar.SECOND, 0)
            service.setSleepTimer(sleepTime)
        }
    }

    @Volatile
    private var loadingLastPlaylist = false
    fun loadLastPlaylist(type: Int = PLAYLIST_TYPE_AUDIO) : Boolean {
        if (loadingLastPlaylist) return true
        loadingLastPlaylist = true
        val currentMediaKey = when (type) {
            PLAYLIST_TYPE_AUDIO -> KEY_CURRENT_AUDIO
            PLAYLIST_TYPE_VIDEO -> KEY_CURRENT_MEDIA
            else -> KEY_CURRENT_MEDIA_RESUME
        }
        val locationsKey = when (type) {
            PLAYLIST_TYPE_AUDIO -> KEY_AUDIO_LAST_PLAYLIST
            PLAYLIST_TYPE_VIDEO -> KEY_MEDIA_LAST_PLAYLIST
            else -> KEY_MEDIA_LAST_PLAYLIST_RESUME
        }
        val audio = type == PLAYLIST_TYPE_AUDIO
        val currentMedia = settings.getString(currentMediaKey, "")
        if (currentMedia.isNullOrEmpty()) {
            loadingLastPlaylist = false
            return false
        }
        val locations = settings.getString(locationsKey, null)
                ?.split(" ".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        if (locations.isNullOrEmpty()) {
            loadingLastPlaylist = false
            return false
        }
        launch {
            val playList = withContext(Dispatchers.Default) {
                locations.asSequence().mapTo(ArrayList(locations.size)) {
                    MLServiceLocator.getAbstractMediaWrapper(it.toUri())
                }
            }
            // load playlist
            shuffling = settings.getBoolean(if (audio) AUDIO_SHUFFLING else MEDIA_SHUFFLING, false)
            val position = max(0, settings.getInt(if (audio) POSITION_IN_AUDIO_LIST else POSITION_IN_MEDIA_LIST, 0))
            savedTime = settings.getLong(if (audio) POSITION_IN_SONG else POSITION_IN_MEDIA, -1)
            if (!audio && position < playList.size && settings.getBoolean(VIDEO_PAUSED, false)) {
                playList[position].addFlags(MediaWrapper.MEDIA_PAUSED)
            }
            if (audio && position < playList.size) playList[position].addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            load(playList, position, mlUpdate = true, avoidErasingStop = true)
            loadingLastPlaylist = false
            if (!audio) {
                val rate = settings.getFloat(VIDEO_SPEED, player.getRate())
                if (rate != 1.0f) player.setRate(rate, false)
            }
        }
        return true
    }

    fun play() {
        if (hasMedia()) player.play()
    }

    fun pause() {
        if (player.pause()) {
            savePosition()
            launch {
                //needed to save the current media in audio mode when it's a video played as audio
                saveCurrentMedia()
                saveMediaList()
            }
            if (getCurrentMedia()?.isPodcast == true) saveMediaMeta()
        }
    }

    @MainThread
    override fun next(force : Boolean) {
        mediaList.getMedia(currentIndex)?.let {
            if (it.type == MediaWrapper.TYPE_VIDEO || it.isPodcast) saveMediaMeta()
        }
        val size = mediaList.size()
        if (force || repeating.value != PlaybackStateCompat.REPEAT_MODE_ONE) {
            previous.push(currentIndex)
            currentIndex = nextIndex
            if (size == 0 || currentIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid next index, aborted !")
                stop()
                return
            }
            videoBackground = videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        }
        launch { playIndex(currentIndex) }
    }

    fun restart() {
        val isPlaying = player.isPlaying() && isAudioList()
        stop()
        if (isPlaying) PlaybackService.loadLastAudio(service)
    }

    override fun stop(systemExit: Boolean, video: Boolean) {
        clearABRepeat()
        if (stopAfter != -1) Settings.getInstance(AppContextProvider.appContext).putSingle(AUDIO_STOP_AFTER, stopAfter)
        stopAfter = -1
        videoBackground = false
        val job = getCurrentMedia()?.let {
            savePosition(video = video)
            launch(start = CoroutineStart.UNDISPATCHED) {
                saveMediaMeta().join()
                if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && video) {
                    setResumeProgram(service.applicationContext, it)
                    updateNextProgramAfterThumbnailGeneration(service, service.applicationContext, it)
                }
            }
        }
        service.setSleepTimer(null)
        mediaList.removeEventListener(this)
        previous.clear()
        currentIndex = -1
        if (systemExit) player.release()
        else player.restart()
        mediaList.clear()
        showAudioPlayer.value = false
        service.onPlaybackStopped(systemExit)
        //Close video player if started
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(EXIT_PLAYER))
        if (systemExit) launch(start = CoroutineStart.UNDISPATCHED) {
            job?.join()
            cancel()
            player.cancel()
        }
        playingState.value = false
    }

    @MainThread
    fun previous(force: Boolean) {
        mediaList.getMedia(currentIndex)?.let { if (it.type == MediaWrapper.TYPE_VIDEO) saveMediaMeta() }
        if (hasPrevious() &&
                ((force || !player.seekable || (player.getCurrentTime() < PREVIOUS_LIMIT_DELAY) || (lastPrevious != -1L && System.currentTimeMillis() - lastPrevious < PREVIOUS_LIMIT_DELAY)))) {
            val size = mediaList.size()
            currentIndex = prevIndex
            if (previous.size > 0) previous.pop()
            if (size == 0 || prevIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !")
                player.stop()
                return
            }
            launch { playIndex(currentIndex) }
            lastPrevious = -1L
        } else {
            player.setPosition(0F)
            lastPrevious = System.currentTimeMillis()
        }
    }

    @MainThread
    override fun shuffle() {
        if (shuffling) previous.clear()
        shuffling = !shuffling
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    /**
     * Will set the repeating variable from the value that has been saved in settings
     */
    private fun setRepeatTypeFromSettings() {
        AppScope.launch {
            repeating.emit(if (getCurrentMedia()?.type == MediaWrapper.TYPE_VIDEO) {
                settings.getInt(PLAYLIST_VIDEO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)
            } else
                settings.getInt(PLAYLIST_AUDIO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)
            )
        }
    }

    @MainThread
    fun setRepeatType(repeatType: Int) {
        AppScope.launch { repeating.emit(repeatType) }
        if (getCurrentMedia()?.type == MediaWrapper.TYPE_VIDEO)
            settings.putSingle(PLAYLIST_VIDEO_REPEAT_MODE_KEY, repeating.value)
        else
            settings.putSingle(PLAYLIST_AUDIO_REPEAT_MODE_KEY, repeating.value)
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    fun setRenderer(item: RendererItem?) {
        player.setRenderer(item)
        showAudioPlayer.value = PlayerController.playbackState != PlaybackStateCompat.STATE_STOPPED && (item !== null || !player.isVideoPlaying())
    }

    override suspend fun playIndex(index: Int, flags: Int, forceResume: Boolean, forceRestart: Boolean) {
        // The fonts are scanned when the first text renderer is created, which would otherwise
        // delay the playback by several seconds on the first run. See [FontCache]
        // Wait before reading any state, as the queue can change in the meantime
        FontCache.await(service) {
            service.showToast(service.getString(R.string.font_cache_building), Toast.LENGTH_LONG)
        }

        videoBackground = videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        if (mediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        // A new playback starts: the previous "media ended" marker only exists to protect
        // the end-of-media position of the media that just ended. It must not keep preventing
        // the progress of this media from being saved (e.g. when a shuffle replay selects it again).
        endReachedFor = null
        currentIndex = if (isValidPosition(index)) {
            index
        } else {
            Log.w(TAG, "Warning: index $index out of bounds")
            0
        }

        val mw = mediaList.getMedia(index) ?: return
        val mediaFromMl = medialibrary.getMedia(mw.uri)
        if (mediaFromMl != null)
            mw.time = mediaFromMl.time

        val isInCustomPiP: Boolean = service.isInPiPMode.value ?: false
        if (mw.type == MediaWrapper.TYPE_VIDEO && !isAppStarted() && !isInCustomPiP) videoBackground = true
        val isVideoPlaying = mw.type == MediaWrapper.TYPE_VIDEO && player.isVideoPlaying()
        setRepeatTypeFromSettings()
        if (!videoBackground && isVideoPlaying) mw.addFlags(MediaWrapper.MEDIA_VIDEO)
        if (videoBackground) mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        if (isBenchmark) mw.addFlags(MediaWrapper.MEDIA_BENCHMARK)
        parsed = false
        player.switchToVideo = false
        if (mw.uri.scheme == "content") withContext(Dispatchers.IO) { MediaUtils.retrieveMediaTitle(mw) }

        if (mw.type != MediaWrapper.TYPE_VIDEO || isVideoPlaying || player.hasRenderer
                || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)) {
            var uri = withContext(Dispatchers.IO) { FileUtils.getUri(mw.uri) }
            if (uri == null) {
                skipMedia()
                return
            }
            //PiP TV
            if (!isBenchmark && AndroidDevices.isTv && isVideoPlaying) {
                VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
            }
            val title = mw.getMetaLong(MediaWrapper.META_TITLE)
            if (title > 0) uri = "$uri#$title".toUri()

            val start: Long
            if (isVideoPlaying) {
                start = if (forceRestart
                    || videoResumeStatus == ResumeStatus.NEVER) 0L else getStartTime(mw)
                // If we explicitly restart or NEVER resume is set, ensure any leftover savedTime is cleared
                if (start == 0L && (forceRestart || videoResumeStatus == ResumeStatus.NEVER)) savedTime = 0L
                if (!forceResume && videoResumeStatus == ResumeStatus.ASK && start > 0 && isAppStarted()) {
                    waitForConfirmation.postValue(WaitConfirmation(mw.title, index, flags))
                    return
                }
            } else {
                start = if (forceRestart
                    || audioResumeStatus == ResumeStatus.NEVER) 0L else getStartTime(mw)
                // If we explicitly restart or NEVER resume is set, ensure any leftover savedTime is cleared
                if (start == 0L && (forceRestart || audioResumeStatus == ResumeStatus.NEVER)) savedTime = 0L
                if (!forceResume && audioResumeStatus == ResumeStatus.ASK && start > 0 && isAppStarted()) {
                    val confirmation = WaitConfirmation(mw.title, index, flags)
                    waitForConfirmationAudio.postValue(confirmation)
                    return
                }
            }
            //todo restore position as well when we move to VLC 4.0
            val media = mediaFactory.getFromUri(VLCInstance.getInstance(service), uri)
            //fixme workaround to prevent the issue described in https://code.videolan.org/videolan/vlc-android/-/issues/2106
            if (shouldDisableCookieForwarding) {
                shouldDisableCookieForwarding = false
                media.addOption(":no-http-forward-cookies")
            }
            settings.getString(HTTP_USER_AGENT, null)?.let {
                 media.addOption(":http-user-agent=$it")
            }
            val dav1dThreadNumber = settings.getString(DAV1D_THREAD_NUMBER, "") ?: ""
            if (dav1dThreadNumber.isNotEmpty() && dav1dThreadNumber.toInt() >= 1) {
                media.addOption(":dav1d-thread-frames=$dav1dThreadNumber")
            }
            //todo in VLC 4.0, this should be done by using libvlc_media_player_set_time instead of start-time
            media.addOption(":start-time=${start/1000L}")
            media.addOption(":no-sout-chromecast-video")
            VLCOptions.setMediaOptions(media, ctx, flags or mw.flags, PlaybackService.hasRenderer())
            /* keeping only video during benchmark */
            if (isBenchmark) {
                media.addOption(":no-audio")
                media.addOption(":no-spu")
                if (isHardware) {
                    media.addOption(":codec=mediacodec_ndk,mediacodec_jni,none")
                    isHardware = false
                }
            }
            media.setEventListener(this@PlaylistManager)
            player.startPlayback(media, mediaplayerEventListener, start)
            player.setSlaves(media, mw)
            if (browserAudioActive) player.setVolume(0)
            newMedia = true
            determinePrevAndNextIndices()
            service.onNewPlayback()
        } else { //Start VideoPlayer for first video, it will trigger playIndex when ready.
            if (player.isPlaying()) player.stop()
            VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
        }
    }

    private fun skipMedia() {
        if (currentIndex != nextIndex) next()
        else stop()
    }

    fun onServiceDestroyed() {
        player.release()
    }

    fun setBrowserAudio(enable: Boolean) {
        if (browserAudioActive == enable) return
        browserAudioActive = enable
        if (enable) {
            preBrowserVolume = player.getVolume()
            player.setVolume(0)
        } else {
            player.setVolume(if (preBrowserVolume >= 0) preBrowserVolume else 100)
            preBrowserVolume = -1
        }
    }

    @MainThread
    fun switchToVideo(): Boolean {
        val media = getCurrentMedia()
        if (media === null || media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo())
            return false
        val hasRenderer = player.hasRenderer
        videoBackground = false
        showAudioPlayer.postValue(false)
        if (player.isVideoPlaying() && !hasRenderer) {//Player is already running, just send it an intent
            player.setVideoTrackEnabled(true)
            LocalBroadcastManager.getInstance(service).sendBroadcast(
                    VideoPlayerActivity.getIntent(PLAY_FROM_SERVICE,
                            media, false, currentIndex))
        } else if (!player.switchToVideo) { //Start the video player
            VideoPlayerActivity.startOpened(AppContextProvider.appContext, media.uri, currentIndex)
            if (!hasRenderer) player.switchToVideo = true
        }
        return true
    }

    fun setVideoTrackEnabled(enabled: Boolean) {
        if (!hasMedia()) return
        if (enabled) getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_VIDEO)
        else getCurrentMedia()?.removeFlags(MediaWrapper.MEDIA_VIDEO)
        player.setVideoTrackEnabled(enabled)
    }

    fun hasPrevious() = navigator.hasPrevious()

    override fun hasNext() = navigator.hasNext()

    @MainThread
    override fun onItemAdded(index: Int, mrl: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemAdded")
        if (currentIndex >= index && !expanding) ++currentIndex
        addUpdateActor.trySend(Unit)
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private val addUpdateActor = actor<Unit>(capacity = Channel.CONFLATED) {
        for (update in channel) {
            determinePrevAndNextIndices()
            executeUpdate()
            saveMediaList()
        }
    }

    @MainThread
    override fun onItemRemoved(index: Int, mrl: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemDeleted")
        val currentRemoved = currentIndex == index
        if (currentIndex >= index && !expanding) --currentIndex
        launch {
            determinePrevAndNextIndices()
            if (currentRemoved && !expanding) {
                when {
                    nextIndex != -1 -> next()
                    currentIndex != -1 -> playIndex(currentIndex, 0)
                    else -> stop()
                }
            }
            executeUpdate()
            saveMediaList()
        }
    }

    private fun executeUpdate() {
        service.executeUpdate(true)
    }

    override fun saveMediaMeta(end: Boolean): Job {
        return persistenceManager.saveMediaMeta(
            scope = this,
            end = end,
            currentMedia = getCurrentMedia(),
            player = player,
            entryUrl = entryUrl,
            endReachedFor = endReachedFor,
            onSkipMediaUpdateRefresh = { skipMediaUpdateRefresh = true }
        )
    }

    fun setSpuTrack(index: String) {
        persistenceManager.setSpuTrack(this, index, player, getCurrentMedia())
    }

    fun setAudioDelay(delay: Long) {
        persistenceManager.setAudioDelay(this, delay, player, getCurrentMedia())
    }

    fun setSpuDelay(delay: Long) {
        persistenceManager.setSpuDelay(this, delay, player, getCurrentMedia())
    }

    override fun loadMediaMeta(mw: MediaWrapper) {
        persistenceManager.loadMediaMeta(mw, player, abRepeatOn, abRepeat)
        restoreSpeed(mw)
    }

    /**
     * Restore the playback speed of the current media.
     *
     * @param media
     */
    override fun restoreSpeed(media: MediaWrapper) {
        val playbackRate = persistenceManager.restoreSpeed(media, player.isVideoPlaying())
        player.setRate(playbackRate, false)
    }

    @Synchronized
    override fun saveCurrentMedia() {
        persistenceManager.saveCurrentMedia(getCurrentMedia(), isAudioList(), player.isVideoPlaying())
    }

    override suspend fun saveMediaList() {
        persistenceManager.saveMediaList(getCurrentMedia(), getMediaList(), isAudioList())
    }

    override fun onItemMoved(indexBefore: Int, indexAfter: Int, mrl: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemMoved")
        when (currentIndex) {
            indexBefore -> {
                currentIndex = indexAfter
                if (indexAfter > indexBefore)
                    --currentIndex
            }
            in indexAfter until indexBefore -> ++currentIndex
            in (indexBefore + 1) until indexAfter -> --currentIndex
        }

        // If we are in random mode, we completely reset the stored previous track
        // as their indices changed.
        previous.clear()
        addUpdateActor.trySend(Unit)
    }

    override suspend fun determinePrevAndNextIndices(expand: Boolean) {
        val media = getCurrentMedia()
        val expandFn: (suspend (Boolean, Boolean) -> Int)? = if (expand && media != null) {
            { isStream, noParse ->
                expanding = true
                val result = expand(isStream, noParse)
                expanding = false
                result
            }
        } else null
        navigator.determinePrevAndNextIndices(currentIndex, playQueue, expand, expandFn)
    }

    fun previousTotalTime(): Long = navigator.previousTotalTime(playQueue, currentIndex)

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    private suspend fun expand(updateHistory: Boolean, skipNextParsing: Boolean = false): Int {
        return queueExpander.expand(updateHistory, skipNextParsing)
    }

    override fun getCurrentMedia() = mediaList.getMedia(currentIndex)

    fun getPrevMedia() = if (isValidPosition(prevIndex)) mediaList.getMedia(prevIndex) else null

    fun getNextMedia() = if (isValidPosition(nextIndex)) mediaList.getMedia(nextIndex) else null

    fun getMedia(position: Int) = mediaList.getMedia(position)

    override fun getStartTime(mw: MediaWrapper) : Long {
        val start = when {
            mw.hasFlag(MediaWrapper.MEDIA_FROM_START) -> {
                mw.removeFlags(MediaWrapper.MEDIA_FROM_START)
                0L
            }
            mw.time <= 0L -> when {
                savedTime > 0L -> savedTime
                else -> 0L
            }
            else -> mw.time
        }
        savedTime = 0L
        return start
    }

    @Synchronized
    override fun savePosition(reset: Boolean, video: Boolean) {
        persistenceManager.savePosition(
            hasMedia = hasMedia(),
            isAudioList = isAudioList(),
            shuffling = shuffling,
            currentIndex = currentIndex,
            currentTime = player.getCurrentTime(),
            rate = player.getRate(),
            firstMedia = getMedia(0),
            reset = reset,
            video = video
        )
    }

    /**
     * Append to the current existing playlist
     */
    @MainThread
    suspend fun append(list: List<MediaWrapper>, index: Int = 0) {
        if (BuildConfig.BETA) Log.d(TAG, "append with values: ", Exception("Call stack"))
        if (!hasCurrentMedia()) {
            launch { load(list, index, mlUpdate = true) }
            return
        }
        val newList = withContext(Dispatchers.IO) { list.updateWithMLMeta() }
        mediaList.removeEventListener(this)
        for (media in newList) mediaList.add(media)
        if (BuildConfig.BETA) newList.forEach {
            try {
                Log.d(TAG, "Media location: ${it.uri}")
            } catch (e: NullPointerException) {
                Log.d(TAG, "Media crash", e)
            }
        }
        mediaList.addEventListener(this)
        addUpdateActor.trySend(Unit)
        if (settings.getBoolean(KEY_AUDIO_FORCE_SHUFFLE, false) && getCurrentMedia()?.type == MediaWrapper.TYPE_AUDIO  && !shuffling && canShuffle()) shuffle()
    }

    /**
     * Insert into the current existing playlist
     */

    @MainThread
    fun insertNext(list: List<MediaWrapper>) {
        if (BuildConfig.BETA) Log.d(TAG, "insertNext with values: ", Exception("Call stack"))
        if (BuildConfig.BETA) list.forEach { Log.d(TAG, "Media location: ${it.uri}") }
        if (!hasCurrentMedia()) {
            launch { load(list, 0) }
            return
        }
        val startIndex = currentIndex + 1
        for ((index, mw) in list.withIndex()) mediaList.insert(startIndex + index, mw)
    }

    /**
     * Move an item inside the playlist.
     */
    @MainThread
    fun moveItem(positionStart: Int, positionEnd: Int) = playQueue.moveItem(positionStart, positionEnd)

    @MainThread
    fun insertItem(position: Int, mw: MediaWrapper) {
        if (BuildConfig.BETA) Log.d(TAG, "insertItem with values: ", Exception("Call stack"))
        if (BuildConfig.BETA) Log.d(TAG, "Media location: ${mw.uri}")
        playQueue.insertItem(position, mw)
    }


    @MainThread
    fun remove(position: Int) = playQueue.remove(position)

    @MainThread
    fun removeLocation(location: String) = playQueue.removeLocation(location)

    fun getMediaListSize() = playQueue.size

    fun getMediaList(): List<MediaWrapper> = playQueue.getMediaList()

    fun setABRepeatValue(media: MediaWrapper?, time: Long) = abRepeatController.setABRepeatValue(media, time)

    fun setDelayValue(time: Long, start: Boolean) = abRepeatController.setDelayValue(time, start)

    fun resetDelayValues() = abRepeatController.resetDelayValues()

    fun resetABRepeatValues(media: MediaWrapper?) = abRepeatController.resetABRepeatValues(media)

    fun toggleABRepeat() = abRepeatController.toggleABRepeat()

    fun toggleStats() = abRepeatController.toggleStats()

    override fun clearABRepeat() = abRepeatController.clearABRepeat()

    override fun onEvent(event: IMedia.Event) {
        var update = true
        when (event.type) {
            IMedia.Event.MetaChanged -> {
                /* Update Meta if file is already parsed */
                if (parsed && player.updateCurrentMeta(event.metaId, getCurrentMedia())) service.onMediaListChanged()
                if (BuildConfig.DEBUG) Log.i(TAG, "Media.Event.MetaChanged: " + event.metaId)
            }
            IMedia.Event.ParsedChanged -> {
                if (BuildConfig.DEBUG) Log.i(TAG, "Media.Event.ParsedChanged")
                player.updateCurrentMeta(-1, getCurrentMedia())
                parsed = true
            }
            else -> update = false
        }
        if (update) {
            service.onMediaEvent(event)
            if (parsed) {
                service.notifyTrackChanged()
                service.showNotification()
            }
        }
    }

    private val mediaplayerEventListener = object : MediaPlayerEventListener {
        private var lastTimeMetaSaved = 0L

        override suspend fun onEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    val mw = withContext(Dispatchers.IO) {
                        getCurrentMedia()?.let {
                            medialibrary.findMedia(it).apply {
                                if (type == -1) type = it.type
                            }
                        }
                    } ?: return
                    if (newMedia) {
                        loadMediaMeta(mw)
                        mw.length = player.getLength()
                        saveMediaList()
                        savePosition()
                        saveCurrentMedia()
                        newMedia = false
                        if (player.hasRenderer || (!player.isVideoPlaying() && (mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo()))) {
                            showAudioPlayer.value = true
                        }
                        savePlaycount(mw)
                        if (mw.title == mw.fileName || (mw.type == MediaWrapper.TYPE_STREAM && (mw.title != player.mediaplayer.media?.getMeta(IMedia.Meta.Title, true) || mw.artistName != player.mediaplayer.media?.getMeta(IMedia.Meta.Artist, true)))) {
                            // used for initial metadata update. We avoid the metadata load when the initial MediaPlayer.Event.ESSelected is sent to avoid race conditions
                            refreshTrackMeta(mw)
                        }
                    }
                    playingState.value = true
                }
                MediaPlayer.Event.EndReached -> {
                    clearABRepeat()
                    getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_FROM_START)
                    if (currentIndex != nextIndex) {
                        endReachedFor = getCurrentMedia()?.uri.toString()
                        saveMediaMeta(true)
                        if (isBenchmark) player.setPreviousStats()
                        if (nextIndex == -1) savePosition(reset = true)
                    }
                    if (stopAfter == currentIndex) {
                        if (BuildConfig.DEBUG) Log.d("AUDIO_STOP_AFTER", "reset")
                        stopAfter = -1
                        stop()
                    } else {
                        if (isBenchmark) player.setCurrentStats()
                        determinePrevAndNextIndices(true)
                        if (!hasNext()) getCurrentMedia()?.let {
                            if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && !isAudioList()) {
                                setResumeProgram(service.applicationContext, it)
                                updateNextProgramAfterThumbnailGeneration(service, service.applicationContext, it)
                            }
                        }
                        next()
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    val location = try {
                        getCurrentMedia()?.location
                    } catch (e: NullPointerException) {
                        null
                    }
                    Log.w(TAG, "Invalid location $location")

                    service.showToast(if (location != null && location.toUri().scheme == "missing") service.getString(R.string.missing_location) else service.getString(R.string.invalid_location, location as Any), Toast.LENGTH_SHORT, true)
                    if (location != null) PlaybackService.lastError.postValue(location)
                    if (currentIndex != nextIndex) next() else stop()
                }
                MediaPlayer.Event.TimeChanged -> {
                    abRepeat.value?.let {
                        val fastSeek = settings.getBoolean(KEY_ALWAYS_FAST_SEEK, false)
                        if (it.stop != -1L && player.getCurrentTime() > it.stop) service.setTime(it.start, false)
                        if ((fastSeek && player.getCurrentTime() < it.start - 30000L)
                            || (!fastSeek && player.getCurrentTime() < it.start))
                            service.setTime(it.start, false)
                    }
                    if (player.getCurrentTime() % 10 == 0L) savePosition()
                    val now = System.currentTimeMillis()
                    if (now - lastTimeMetaSaved > 5000L){
                        lastTimeMetaSaved = now
                        saveMediaMeta()
                    }
                }
                MediaPlayer.Event.SeekableChanged -> {
                    if (event.seekable)
                        getCurrentMedia()?.let { media ->
                            restoreSpeed(media)
                        }
                }
                MediaPlayer.Event.ESSelected -> {
                    getCurrentMedia()?.let { media ->
                        if (player.isPlaying()) {
                            if (media.type == MediaWrapper.TYPE_STREAM && (media.title != player.mediaplayer.media?.getMeta(IMedia.Meta.Title, true) || media.artistName != player.mediaplayer.media?.getMeta(IMedia.Meta.Artist, true))) {
                                refreshTrackMeta(media)
                            }
                        }
                    }
                }
            }
            service.onMediaPlayerEvent(event)
        }
    }

    /**
     * Refresh the track meta as the title and then updates the player
     * Useful for web radios changing the metadata during playback
     * @param mw: The [MediaWrapper] to be updated
     */
    override fun refreshTrackMeta(mw: MediaWrapper) {
        mw.updateMeta(player.mediaplayer)
        service.onMediaListChanged()
        service.showNotification()
    }

    override suspend fun savePlaycount(mw: MediaWrapper) {
        if (Settings.getInstance(AppContextProvider.appContext).getBoolean(KEY_INCOGNITO, false)) return
        var currentMedia = mw
        if (settings.getBoolean(PLAYBACK_HISTORY, true) && !mw.uri.scheme.isSchemeFD()) withContext(Dispatchers.IO) {
            var id = mw.id
            if (id == 0L) {
                var internalMedia = medialibrary.findMedia(mw)
                if (internalMedia != null && internalMedia.id != 0L) {
                    id = internalMedia.id
                } else {
                    internalMedia = if (mw.type == MediaWrapper.TYPE_STREAM || isSchemeStreaming(mw.uri.scheme)) {
                        medialibrary.addStream(entryUrl ?: mw.uri.toString(), mw.title)
                    } else {
                        medialibrary.addMedia(mw.uri.toString(), mw.length)
                    }
                    if (internalMedia != null) {
                        currentMedia = internalMedia
                        id = internalMedia.id
                        getCurrentMedia()?.let { currentMedia -> if (internalMedia.title != currentMedia.title) internalMedia.rename(currentMedia.title) }
                    }
                }
            }
            val canSwitchToVideo = player.canSwitchToVideo()
            /*
             * Because progress isn't saved for non podcast audio (ie. saveMediaMeta), it is
             * necessary to mark the media as played to add them to history and increment their
             * playcount.
             */
            if (id != 0L && currentMedia.type != MediaWrapper.TYPE_VIDEO && !canSwitchToVideo && !currentMedia.isPodcast) {
                currentMedia.markAsPlayed()
            }
        }
    }

    override fun isAudioList() = !player.isVideoPlaying() && mediaList.isAudioList
}

class ABRepeat(var start: Long = -1L, var stop: Long = -1L)
class DelayValues(var start: Long = -1L, var stop: Long = -1L)
class WaitConfirmation(val title: String, val index: Int, val flags: Int, var used: Boolean = false)
