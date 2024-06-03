package org.videolan.vlc.media

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import org.videolan.resources.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.resources.KEY_CURRENT_AUDIO
import org.videolan.resources.KEY_CURRENT_AUDIO_RESUME_ARTIST
import org.videolan.resources.KEY_CURRENT_AUDIO_RESUME_THUMB
import org.videolan.resources.KEY_CURRENT_AUDIO_RESUME_TITLE
import org.videolan.resources.KEY_CURRENT_MEDIA
import org.videolan.resources.KEY_CURRENT_MEDIA_RESUME
import org.videolan.resources.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.resources.KEY_MEDIA_LAST_PLAYLIST_RESUME
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
import org.videolan.tools.HTTP_USER_AGENT
import org.videolan.tools.KEY_AUDIO_FORCE_SHUFFLE
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_PLAYBACK_RATE
import org.videolan.tools.KEY_PLAYBACK_RATE_VIDEO
import org.videolan.tools.KEY_PLAYBACK_SPEED_PERSIST
import org.videolan.tools.KEY_PLAYBACK_SPEED_PERSIST_VIDEO
import org.videolan.tools.KEY_VIDEO_CONFIRM_RESUME
import org.videolan.tools.MEDIA_SHUFFLING
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.POSITION_IN_AUDIO_LIST
import org.videolan.tools.POSITION_IN_MEDIA
import org.videolan.tools.POSITION_IN_MEDIA_LIST
import org.videolan.tools.POSITION_IN_SONG
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
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.NetworkConnectionManager
import org.videolan.vlc.util.awaitMedialibraryStarted
import org.videolan.vlc.util.isSchemeFD
import org.videolan.vlc.util.isSchemeHttpOrHttps
import org.videolan.vlc.util.isSchemeStreaming
import org.videolan.vlc.util.setResumeProgram
import org.videolan.vlc.util.updateNextProgramAfterThumbnailGeneration
import org.videolan.vlc.util.updateWithMLMeta
import org.videolan.vlc.util.validateLocation
import java.security.SecureRandom
import java.util.Stack
import kotlin.math.max

private const val TAG = "VLC/PlaylistManager"
private const val PREVIOUS_LIMIT_DELAY = 5000L
private const val PLAYLIST_AUDIO_REPEAT_MODE_KEY = "audio_repeat_mode"
private const val PLAYLIST_VIDEO_REPEAT_MODE_KEY = "video_repeat_mode"

class PlaylistManager(val service: PlaybackService) : MediaWrapperList.EventListener, IMedia.EventListener, CoroutineScope {
    private var endReachedFor: String? = null
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
        var playingAsAudio = false
    }

    private val medialibrary by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
    val player by lazy(LazyThreadSafetyMode.NONE) { PlayerController(service.applicationContext) }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(service) }
    private val ctx by lazy(LazyThreadSafetyMode.NONE) { service.applicationContext }
    var currentIndex = -1
        set(value) {
            field = value
            currentPlayedMedia.postValue(mediaList.getMedia(value))
        }
    private var nextIndex = -1
    private var prevIndex = -1
    var startupIndex = -1    
    private var previous = Stack<Int>()
    var stopAfter = -1
    var shuffling = false
    var videoBackground = false
        private set
    var isBenchmark = false
    var isHardware = false
    private var parsed = false
    var savedTime = 0L
    private var random = SecureRandom()
    private var newMedia = false
    @Volatile
    private var expanding = false
    private var entryUrl : String? = null
    val abRepeat by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<ABRepeat>().apply { value = ABRepeat() } }
    val abRepeatOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val videoStatsOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val delayValue by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<DelayValues>().apply { value = DelayValues() } }
    val waitForConfirmation by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<WaitConfirmation?>().apply { value = null } }
    private var lastPrevious = -1L

    private val mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
    lateinit var videoResumeStatus: VideoResumeStatus

    fun hasCurrentMedia() = isValidPosition(currentIndex)

    fun canRepeat() = mediaList.size() > 0

    fun hasPlaylist() = mediaList.size() > 1

    fun canShuffle() = mediaList.size() > 2

    fun isValidPosition(position: Int) = position in 0 until mediaList.size()
    private var shouldDisableCookieForwarding: Boolean = false

    init {
        AppScope.launch { repeating.emit(settings.getInt(PLAYLIST_AUDIO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)) }
        resetResumeStatus()
    }

    fun resetResumeStatus() {
        val string = settings.getString(KEY_VIDEO_CONFIRM_RESUME, "0")
        videoResumeStatus = if (string == "2") VideoResumeStatus.ASK else if (string == "0") VideoResumeStatus.ALWAYS else VideoResumeStatus.NEVER
        waitForConfirmation.postValue(null)
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
                            service.showToast(if (Uri.parse(location).scheme == "missing")service.resources.getString(R.string.missing_location) else service.resources.getString(R.string.invalid_location,  location), Toast.LENGTH_SHORT)
                            continue
                        }
                        Log.v(TAG, "Creating on-the-fly Media object for $location")
                        mediaWrapper = MLServiceLocator.getAbstractMediaWrapper(location.toUri())
                        if (BuildConfig.BETA) Log.d(TAG, "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
                    } else
                        if (BuildConfig.BETA) Log.d(TAG, "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
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
        if (isValidPosition(position)) {
            currentIndex = position
        } else {
            currentIndex = 0
            startupIndex = if(position >= 0) position else 0
        }

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
    fun next(force : Boolean = false) {
        mediaList.getMedia(currentIndex)?.let { if (it.type == MediaWrapper.TYPE_VIDEO) saveMediaMeta() }
        val size = mediaList.size()
        if (force || repeating.value != PlaybackStateCompat.REPEAT_MODE_ONE) {
            previous.push(currentIndex)
            //startup index given?
            if (startupIndex != -1) {
                currentIndex = startupIndex
                startupIndex = -1
            } else {
                //no startup index given, use next
                currentIndex = nextIndex
            }
            if (size == 0 || currentIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid next index, aborted !")
                stop()
                return
            }
            videoBackground = videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
            if (repeating.value == PlaybackStateCompat.REPEAT_MODE_ONE) {
                setRepeatType(PlaybackStateCompat.REPEAT_MODE_NONE)
            }
        }
        launch { playIndex(currentIndex) }
    }

    fun restart() {
        val isPlaying = player.isPlaying() && isAudioList()
        stop()
        if (isPlaying) PlaybackService.loadLastAudio(service)
    }

    fun stop(systemExit: Boolean = false, video: Boolean = false) {
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
    fun shuffle() {
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

    suspend fun playIndex(index: Int, flags: Int = 0, forceResume:Boolean = false, forceRestart:Boolean = false) {
        videoBackground = videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        if (mediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        currentIndex = if (isValidPosition(index)) {
            index
        } else {
            Log.w(TAG, "Warning: index $index out of bounds")
            0
        }

        val mw = mediaList.getMedia(index) ?: return
        if (mw.type == MediaWrapper.TYPE_VIDEO && !isAppStarted()) videoBackground = true
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
            val start = if (forceRestart
                || videoResumeStatus == VideoResumeStatus.NEVER
                || !Settings.getInstance(AppContextProvider.appContext).getBoolean(PLAYBACK_HISTORY, true)) 0L else getStartTime(mw)
            if (isVideoPlaying) {
                if (!forceResume && videoResumeStatus == VideoResumeStatus.ASK && start > 0) {
                    waitForConfirmation.postValue(WaitConfirmation(mw.title, index, flags))
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
            //todo in VLC 4.0, this should be done by using libvlc_media_player_set_time instead of start-time
            media.addOption(":start-time=${start/1000L}")
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
        if (!hasMedia() || !player.isPlaying()) return
        if (enabled) getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_VIDEO)
        else getCurrentMedia()?.removeFlags(MediaWrapper.MEDIA_VIDEO)
        player.setVideoTrackEnabled(enabled)
    }

    fun hasPrevious() = prevIndex != -1

    fun hasNext() = nextIndex != -1

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

    fun saveMediaMeta(end:Boolean = false) = launch(start = CoroutineStart.UNDISPATCHED) outerLaunch@ {
        if (!Settings.getInstance(AppContextProvider.appContext).getBoolean(PLAYBACK_HISTORY, true)) return@outerLaunch
        if (endReachedFor != null && endReachedFor == getCurrentMedia()?.uri.toString() && !end) return@outerLaunch
        val titleIdx = player.getTitleIdx()
        val currentMedia = getCurrentMedia() ?: return@outerLaunch
        if (currentMedia.uri.scheme.isSchemeFD()) return@outerLaunch
        if (Settings.getInstance(AppContextProvider.appContext).getBoolean(KEY_INCOGNITO, false)) return@outerLaunch
        //Save progress
        val time = player.mediaplayer.time
        val length = player.getLength()
        val canSwitchToVideo = player.canSwitchToVideo()
        val rate = player.getRate()
        launch(Dispatchers.IO) innerLaunch@ {
            val media = if (entryUrl != null) medialibrary.getMedia(entryUrl) else medialibrary.findMedia(currentMedia) ?: return@innerLaunch
            if (showAudioPlayer.value != true) BaseBrowserFragment.needRefresh.postValue(true)
            if (media.id == 0L) return@innerLaunch
            if (titleIdx > 0) media.setLongMeta(MediaWrapper.META_TITLE, titleIdx.toLong())
            //checks if the [MediaPlayer] has not been reset in the meantime to prevent saving 0
            if (time != 0L || player.isPlaying())
                if (media.type == MediaWrapper.TYPE_VIDEO || canSwitchToVideo || media.isPodcast) {
                    if (length == 0L) {
                        media.time = -1L
                        media.position = player.lastPosition
                        medialibrary.setLastPosition(media.id, if (end) 1F else media.position)
                    } else {
                        //todo verify that this info is persisted in DB
                        if (media.length <= 0 && length > 0) media.length = length
                        try {
                            when (medialibrary.setLastTime(media.id, if (end) length else time)) {
                                Medialibrary.ML_SET_TIME_ERROR -> {
                                }
                                Medialibrary.ML_SET_TIME_END, Medialibrary.ML_SET_TIME_BEGIN -> media.time = 0
                                Medialibrary.ML_SET_TIME_AS_IS -> media.time = time
                            }
                        } catch (e: NullPointerException) {
                            VLCCrashHandler.saveLog(e, "NullPointerException in PlaylistManager saveMediaMeta")
                        }
                    }
                }
            if (media.type != MediaWrapper.TYPE_VIDEO && !canSwitchToVideo && !media.isPodcast) skipMediaUpdateRefresh = true
            media.setStringMeta(MediaWrapper.META_SPEED, rate.toString())

        }
    }

    fun setSpuTrack(index: String) {
        if (!player.setSpuTrack(index)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L) launch(Dispatchers.IO) { media.setStringMeta(MediaWrapper.META_SUBTITLE_TRACK, index) }
    }

    fun setAudioDelay(delay: Long) {
        if (!player.setAudioDelay(delay)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L && settings.getBoolean("save_individual_audio_delay", true)) {
            launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_AUDIODELAY, player.getAudioDelay()) }
        }
    }

    fun setSpuDelay(delay: Long) {
        if (!player.setSpuDelay(delay)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L) launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_SUBTITLE_DELAY, player.getSpuDelay()) }
    }

    private fun loadMediaMeta(media: MediaWrapper) {
        if (player.canSwitchToVideo()) {
            val savedDelay = media.getMetaLong(MediaWrapper.META_AUDIODELAY)
            val globalDelay = Settings.getInstance(AppContextProvider.appContext).getLong(AUDIO_DELAY_GLOBAL, 0L)
            if (savedDelay == 0L && globalDelay != 0L) {
                player.setAudioDelay(globalDelay)
            } else if (settings.getBoolean("save_individual_audio_delay", true)) {
                player.setAudioDelay(savedDelay)
            }
            val abStart = if (settings.getBoolean(PLAYBACK_HISTORY, true))  media.getMetaLong(MediaWrapper.META_AB_REPEAT_START) else 0L
            if (abStart != 0L) {
                abRepeatOn.value = true
                val abStop = media.getMetaLong(MediaWrapper.META_AB_REPEAT_STOP)
                abRepeat.postValue(ABRepeat(abStart, if (abStop == 0L) -1L else abStop))
            }
            player.setSpuTrack(media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK).toString())
            player.setSpuDelay(media.getMetaLong(MediaWrapper.META_SUBTITLE_DELAY))
            val rateString = if (settings.getBoolean(PLAYBACK_HISTORY, true)) media.getMetaString(MediaWrapper.META_SPEED) else null
            if (!rateString.isNullOrEmpty()) {
                player.setRate(rateString.toFloat(), false)
            }
        }
    }

    @Synchronized
    fun saveCurrentMedia(forceVideo:Boolean = false) {
        val media = getCurrentMedia() ?: return
        val isAudio = isAudioList() || forceVideo
        if (media.uri.scheme.isSchemeFD()) {
            if (isAudio) {
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, "")
            }
            return
        }
        val saveAudioPlayQueue = settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)
        val saveVideoPlayQueue = settings.getBoolean(VIDEO_RESUME_PLAYBACK, true)
        if (!isAudio && saveVideoPlayQueue) {
            settings.putSingle(KEY_CURRENT_MEDIA_RESUME, media.location)
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, media.title ?: "")
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, media.artist ?: "")
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, media.artworkURL ?: "")
            settings.putSingle(KEY_CURRENT_MEDIA, media.location)
        }
        if (isAudio && saveAudioPlayQueue) {
            settings.putSingle(KEY_CURRENT_MEDIA_RESUME, media.location)
            settings.putSingle(KEY_CURRENT_AUDIO, media.location)
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, media.title ?: "")
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, media.artist ?: "")
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, media.artworkURL ?: "")
        }
    }

    suspend fun saveMediaList(forceVideo:Boolean = false) {
        val currentMedia = getCurrentMedia() ?: return
        if (currentMedia.uri.scheme.isSchemeFD()) return
        val locations = StringBuilder()
        val isAudio = isAudioList() || forceVideo
        withContext(Dispatchers.Default) {
            val list = mediaList.copy.takeIf { it.isNotEmpty() } ?: return@withContext
            for (mw in list) locations.append(" ").append(mw.uri.toString())
            //We save a concatenated String because putStringSet is APIv11.
            if (isAudio) {
                if (settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)) {
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST_RESUME, locations.toString().trim())
                    settings.putSingle(KEY_AUDIO_LAST_PLAYLIST, locations.toString().trim())
                }
            } else {
                if (settings.getBoolean(VIDEO_RESUME_PLAYBACK, true)) {
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST_RESUME, locations.toString().trim())
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST, locations.toString().trim())
                }
            }
        }
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

    private suspend fun determinePrevAndNextIndices(expand: Boolean = false) {
        val media = getCurrentMedia()
        if (expand && media !== null) {
            expanding = true
            nextIndex = expand(media.type == MediaWrapper.TYPE_STREAM)
            expanding = false
        } else {
            nextIndex = -1
        }
        prevIndex = -1

        if (nextIndex == -1) {
            // No subitems; play the next item.
            val size = mediaList.size()
            shuffling = shuffling and (size > 2)

            if (shuffling) {
                if (!previous.isEmpty()) {
                    prevIndex = previous.peek()
                    while (!isValidPosition(prevIndex)) {
                        previous.removeAt(previous.size - 1)
                        if (previous.isEmpty()) {
                            prevIndex = -1
                            break
                        }
                        prevIndex = previous.peek()
                    }
                }
                // If we've played all songs already in shuffle, then either
                // reshuffle or stop (depending on RepeatType).
                if (previous.size + 1 == size) {
                    if (repeating.value == PlaybackStateCompat.REPEAT_MODE_NONE) {
                        nextIndex = -1
                        return
                    } else {
                        previous.clear()
                    }
                }
                // Find a new index not in previous.
                do {
                    nextIndex = random.nextInt(size)
                } while (nextIndex == currentIndex || previous.contains(nextIndex))
            } else {
                // normal playback
                if (currentIndex > 0) prevIndex = currentIndex - 1
                nextIndex = when {
                    currentIndex + 1 < size -> currentIndex + 1
                    repeating.value == PlaybackStateCompat.REPEAT_MODE_NONE -> -1
                    else -> 0
                }
            }
        }
    }

    fun previousTotalTime(): Long {
        val index = currentIndex
        val copy = mediaList.copy
        return when {
            copy.size == 0 || index < 0 -> {
                0
            }
            shuffling -> {
                copy.asSequence()
                        .filterIndexed { prevIndex, _ -> previous.contains(prevIndex) }
                        .map { it.length }
                        .sum()
            }
            else -> {
                copy.asSequence()
                        .take(index)
                        .map { it.length }
                        .sum()
            }
        }
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    private suspend fun expand(updateHistory: Boolean): Int {
        entryUrl = null
        if (BuildConfig.BETA) Log.d(TAG, "expand with values: ", Exception("Call stack"))
        val index = currentIndex
        val expandedMedia = getCurrentMedia()
        val stream = expandedMedia?.type == MediaWrapper.TYPE_STREAM
        val ml = player.expand()
        var ret = -1

        if (ml != null && ml.count > 0) {
            val mrl = if (updateHistory) expandedMedia?.location else null
            mediaList.removeEventListener(this)
            mediaList.remove(index)
            for (i in 0 until ml.count) {
                val child = ml.getMediaAt(i)
                //fixme workaround to prevent the issue described in https://code.videolan.org/videolan/vlc-android/-/issues/2106
                if (isSchemeHttpOrHttps(child.uri.scheme) && child.uri.authority?.endsWith(".youtube.com") == true) {
                    shouldDisableCookieForwarding = true
                }
                withContext(Dispatchers.IO) { child.parse() }
                if (BuildConfig.BETA)  Log.d(TAG, "inserting: ${child.uri}")
                mediaList.insert(index + i, MLServiceLocator.getAbstractMediaWrapper(child))
                child.release()
            }
            mediaList.addEventListener(this)
            addUpdateActor.trySend(Unit)
            service.onMediaListChanged()
            if (mrl !== null && ml.count == 1) {
                getCurrentMedia()?.apply {
                    AppScope.launch(Dispatchers.IO) {
                        if (stream) {
                            type = MediaWrapper.TYPE_STREAM
                            entryUrl = mrl
                            medialibrary.getMedia(mrl)?.run { if (id > 0) medialibrary.removeExternalMedia(id) }
                        } else if (!uri.scheme.isSchemeFD()) {
                            medialibrary.addToHistory(mrl, title)
                        }
                    }
                }
            }
            ret = index
        }
        ml?.release()
        return ret
    }

    fun getCurrentMedia() = mediaList.getMedia(currentIndex)

    fun getPrevMedia() = if (isValidPosition(prevIndex)) mediaList.getMedia(prevIndex) else null

    fun getNextMedia() = if (isValidPosition(nextIndex)) mediaList.getMedia(nextIndex) else null

    fun getMedia(position: Int) = mediaList.getMedia(position)

    private fun getStartTime(mw: MediaWrapper) : Long {
        val start = when {
            mw.hasFlag(MediaWrapper.MEDIA_FROM_START) -> {
                mw.removeFlags(MediaWrapper.MEDIA_FROM_START)
                0L
            }
            savedTime <= 0L -> when {
                mw.time > 0L -> mw.time
                else -> 0L
            }
            else -> savedTime
        }
        savedTime = 0L
        return start
    }

    @Synchronized
    private fun savePosition(reset: Boolean = false, video: Boolean = false) {
        if (!hasMedia()) return
        settings.edit {
            val audio = !video && isAudioList()
            putBoolean(if (audio) AUDIO_SHUFFLING else MEDIA_SHUFFLING, shuffling)
            putInt(if (audio) POSITION_IN_AUDIO_LIST else POSITION_IN_MEDIA_LIST, if (reset) 0 else currentIndex)
            putLong(if (audio) POSITION_IN_SONG else POSITION_IN_MEDIA, if (reset) 0L else player.getCurrentTime())
            if (!audio) {
                putFloat(VIDEO_SPEED, player.getRate())
            }
            //we reached the end. The next proposed media should be the first of the list
            if (reset) {
                val media = getMediaList()[0]
                if(audio && settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)) putString(KEY_CURRENT_AUDIO, media.location)
            }
        }
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
    fun moveItem(positionStart: Int, positionEnd: Int) = mediaList.move(positionStart, positionEnd)

    @MainThread
    fun insertItem(position: Int, mw: MediaWrapper) {
        if (BuildConfig.BETA) Log.d(TAG, "insertItem with values: ", Exception("Call stack"))
        if (BuildConfig.BETA) Log.d(TAG, "Media location: ${mw.uri}")
        mediaList.insert(position, mw)
    }


    @MainThread
    fun remove(position: Int) = mediaList.remove(position)

    @MainThread
    fun removeLocation(location: String) = mediaList.remove(location)

    fun getMediaListSize()= mediaList.size()

    fun getMediaList(): List<MediaWrapper> = mediaList.copy

    fun setABRepeatValue(media: MediaWrapper?, time: Long) {
        if (settings.getBoolean(PLAYBACK_HISTORY, true)) return
        val value = abRepeat.value ?: ABRepeat()
        when {
            value.start == -1L -> {
                value.start = time
            }
            value.start > time && time > -1 -> {
                value.stop = value.start
                value.start = time
            }
            else -> {
                value.stop = time
            }
        }
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_START, value.start)
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, value.stop)
        abRepeat.value = value
    }

    fun setDelayValue(time: Long, start: Boolean) {
        val value = delayValue.value ?: DelayValues()
        if (start) value.start = time else value.stop = time
        delayValue.value = value
    }

    fun resetDelayValues() {
        delayValue.postValue(DelayValues())
    }

    fun resetABRepeatValues(media: MediaWrapper?) {
        abRepeat.value = ABRepeat()
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_START, 0L)
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, 0L)
    }

    fun toggleABRepeat() {
        abRepeatOn.value = !abRepeatOn.value!!
        if (abRepeatOn.value == false) {
            abRepeat.value = ABRepeat()
        }
    }

    fun toggleStats() {
        videoStatsOn.value = !videoStatsOn.value!!
    }

    fun clearABRepeat() {
        abRepeat.value = abRepeat.value?.apply {
            start = -1L
            stop = -1L
        }
        abRepeatOn.value = false
    }

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
                        if (player.hasRenderer || !player.isVideoPlaying()) showAudioPlayer.value = true
                        savePlaycount(mw)
                        if (mw.title == mw.fileName || (mw.type == MediaWrapper.TYPE_STREAM && (mw.title != player.mediaplayer.media?.getMeta(IMedia.Meta.Title, true) || mw.artist != player.mediaplayer.media?.getMeta(IMedia.Meta.Artist, true)))) {
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

                    service.showToast(if (location != null && Uri.parse(location).scheme == "missing") service.getString(R.string.missing_location) else service.getString(R.string.invalid_location, location
                            ?: ""), Toast.LENGTH_SHORT, true)
                    if (currentIndex != nextIndex) next() else stop()
                }
                MediaPlayer.Event.TimeChanged -> {
                    abRepeat.value?.let {
                        if (it.stop != -1L && player.getCurrentTime() > it.stop) service.setTime(it.start)
                        if (player.getCurrentTime() < it.start) service.setTime(it.start)
                    }
                    if (player.getCurrentTime() % 10 == 0L) savePosition()
                    val now = System.currentTimeMillis()
                    if (now - lastTimeMetaSaved > 5000L){
                        lastTimeMetaSaved = now
                        saveMediaMeta()
                    }
                }
                MediaPlayer.Event.SeekableChanged -> {
                    val playbackRate = if (event.seekable && settings.getBoolean(if (player.isVideoPlaying()) KEY_PLAYBACK_SPEED_PERSIST_VIDEO else KEY_PLAYBACK_SPEED_PERSIST, false)) {
                        settings.getFloat(if (player.isVideoPlaying()) KEY_PLAYBACK_RATE_VIDEO else KEY_PLAYBACK_RATE, 1.0f)
                    } else 1.0f
                    player.setRate(playbackRate, false)
                }
                MediaPlayer.Event.ESSelected -> {
                    getCurrentMedia()?.let { media ->
                        if (player.isPlaying()) {
                            if (media.type == MediaWrapper.TYPE_STREAM && (media.title != player.mediaplayer.media?.getMeta(IMedia.Meta.Title, true) || media.artist != player.mediaplayer.media?.getMeta(IMedia.Meta.Artist, true))) {
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
    private fun refreshTrackMeta(mw: MediaWrapper) {
        mw.updateMeta(player.mediaplayer)
        service.onMediaListChanged()
        service.showNotification()
    }

    private suspend fun savePlaycount(mw: MediaWrapper) {
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

    internal fun isAudioList() = !player.isVideoPlaying() && mediaList.isAudioList
}

class ABRepeat(var start: Long = -1L, var stop: Long = -1L)
class DelayValues(var start: Long = -1L, var stop: Long = -1L)
class WaitConfirmation(val title: String, val index: Int, val flags: Int)
enum class VideoResumeStatus {
    ALWAYS, ASK, NEVER
}
