package org.videolan.vlc.media

import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.tools.AppScope
import org.videolan.tools.KEY_AUDIO_CONFIRM_RESUME
import org.videolan.tools.KEY_AUDIO_FORCE_SHUFFLE
import org.videolan.tools.KEY_VIDEO_CONFIRM_RESUME
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.control.ABRepeatController
import org.videolan.vlc.media.persistence.PlaybackPersistenceManager
import org.videolan.vlc.media.persistence.PlaylistLoader
import org.videolan.vlc.media.persistence.ResumeStatus
import org.videolan.vlc.media.player.PlaybackController
import org.videolan.vlc.media.player.PlayerEventHandler
import org.videolan.vlc.media.queue.PlayQueue
import org.videolan.vlc.media.queue.QueueExpander
import org.videolan.vlc.media.queue.QueueNavigator
import org.videolan.vlc.util.updateWithMLMeta
import java.util.Stack

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
    override var newMedia = false
    @Volatile
    private var expanding = false
    override var entryUrl : String? = null
    val playlistLoader by lazy(LazyThreadSafetyMode.NONE) { PlaylistLoader(this, player, playQueue, persistenceManager, service, settings) }
    val eventHandler by lazy(LazyThreadSafetyMode.NONE) { PlayerEventHandler(service, this, player, medialibrary, settings) }
    val playbackController by lazy(LazyThreadSafetyMode.NONE) { PlaybackController(this, player, playQueue, navigator, eventHandler, medialibrary, service, settings, ctx) }
    val queueExpander by lazy(LazyThreadSafetyMode.NONE) { QueueExpander(this, player, playQueue, medialibrary, service) }
    val abRepeatController by lazy(LazyThreadSafetyMode.NONE) { ABRepeatController(settings) }
    override val abRepeat: MutableLiveData<ABRepeat> get() = abRepeatController.abRepeat
    val abRepeatOn: MutableLiveData<Boolean> get() = abRepeatController.abRepeatOn
    val videoStatsOn: MutableLiveData<Boolean> get() = abRepeatController.videoStatsOn
    val delayValue: MutableLiveData<DelayValues> get() = abRepeatController.delayValue
    override val waitForConfirmation: MutableLiveData<WaitConfirmation?> get() = abRepeatController.waitForConfirmation
    override val waitForConfirmationAudio: MutableLiveData<WaitConfirmation?> get() = abRepeatController.waitForConfirmationAudio

    override lateinit var videoResumeStatus: ResumeStatus
    override lateinit var audioResumeStatus: ResumeStatus

    fun hasCurrentMedia() = playQueue.isValidPosition(currentIndex)

    fun canRepeat() = playQueue.canRepeat()

    fun hasPlaylist() = playQueue.hasPlaylist()

    override fun canShuffle() = playQueue.canShuffle()

    fun isValidPosition(position: Int) = playQueue.isValidPosition(position)
    override var shouldDisableCookieForwarding: Boolean = false
    private val addUpdateChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    override fun notifyUpdate() {
        addUpdateChannel.trySend(Unit)
    }

    init {
        AppScope.launch { repeating.emit(settings.getInt(PLAYLIST_AUDIO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)) }
        launch {
            for (update in addUpdateChannel) {
                determinePrevAndNextIndices()
                executeUpdate()
                saveMediaList()
            }
        }
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
    fun loadLocations(mediaPathList: List<String>, position: Int) = playlistLoader.loadLocations(mediaPathList, position)

    @MainThread
    suspend fun load(list: List<MediaWrapper>, position: Int, mlUpdate: Boolean = false, avoidErasingStop: Boolean = false) {
        playlistLoader.load(list, position, mlUpdate, avoidErasingStop)
    }

    fun loadLastPlaylist(type: Int = PLAYLIST_TYPE_AUDIO): Boolean = playlistLoader.loadLastPlaylist(type)

    fun play() = playbackController.play()

    fun pause() = playbackController.pause()

    @MainThread
    override fun next(force: Boolean) = playbackController.next(force)

    fun restart() = playbackController.restart()

    override fun stop(systemExit: Boolean, video: Boolean) = playbackController.stop(systemExit, video)

    @MainThread
    fun previous(force: Boolean) = playbackController.previous(force)

    @MainThread
    override fun shuffle() = playbackController.shuffle()

    @MainThread
    fun setRepeatType(repeatType: Int) = playbackController.setRepeatType(repeatType)

    fun setRenderer(item: RendererItem?) = playbackController.setRenderer(item)

    override suspend fun playIndex(index: Int, flags: Int, forceResume: Boolean, forceRestart: Boolean) {
        playbackController.playIndex(index, flags, forceResume, forceRestart)
    }

    fun onServiceDestroyed() = playbackController.onServiceDestroyed()

    fun setBrowserAudio(enable: Boolean) = playbackController.setBrowserAudio(enable)

    @MainThread
    fun switchToVideo(): Boolean = playbackController.switchToVideo()

    fun setVideoTrackEnabled(enabled: Boolean) = playbackController.setVideoTrackEnabled(enabled)

    fun hasPrevious() = navigator.hasPrevious()

    override fun hasNext() = navigator.hasNext()

    @MainThread
    override fun onItemAdded(index: Int, mrl: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemAdded")
        if (currentIndex >= index && !expanding) ++currentIndex
        notifyUpdate()
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
        notifyUpdate()
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

    override fun getCurrentMedia() = playQueue.getCurrentMedia()

    fun getPrevMedia() = if (isValidPosition(prevIndex)) playQueue.getMedia(prevIndex) else null

    fun getNextMedia() = if (isValidPosition(nextIndex)) playQueue.getMedia(nextIndex) else null

    fun getMedia(position: Int) = playQueue.getMedia(position)

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
        playQueue.removeEventListener(this)
        playQueue.append(newList)
        if (BuildConfig.BETA) newList.forEach {
            try {
                Log.d(TAG, "Media location: ${it.uri}")
            } catch (e: NullPointerException) {
                Log.d(TAG, "Media crash", e)
            }
        }
        playQueue.addEventListener(this)
        notifyUpdate()
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
        playQueue.insertNext(list)
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
        eventHandler.onEvent(event)
    }

    /**
     * Refresh the track meta as the title and then updates the player
     * Useful for web radios changing the metadata during playback
     * @param mw: The [MediaWrapper] to be updated
     */
    override fun refreshTrackMeta(mw: MediaWrapper) {
        eventHandler.refreshTrackMeta(mw)
    }

    override suspend fun savePlaycount(mw: MediaWrapper) {
        persistenceManager.savePlaycount(mw, player.canSwitchToVideo(), entryUrl)
    }

    override fun isAudioList() = !player.isVideoPlaying() && playQueue.isAudioList
}

class ABRepeat(var start: Long = -1L, var stop: Long = -1L)
class DelayValues(var start: Long = -1L, var stop: Long = -1L)
class WaitConfirmation(val title: String, val index: Int, val flags: Int, var used: Boolean = false)
