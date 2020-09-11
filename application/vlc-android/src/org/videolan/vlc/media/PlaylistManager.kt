package org.videolan.vlc.media

import android.content.Intent
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.util.*
import org.videolan.vlc.util.FileUtils
import java.util.*
import kotlin.math.max

private const val TAG = "VLC/PlaylistManager"
private const val PREVIOUS_LIMIT_DELAY = 5000L
private const val PLAYLIST_REPEAT_MODE_KEY = "audio_repeat_mode" //we keep the old string for migration reasons

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PlaylistManager(val service: PlaybackService) : MediaWrapperList.EventListener, IMedia.EventListener, CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    companion object {
        val showAudioPlayer = MutableLiveData<Boolean>().apply { value = false }
        private val mediaList = MediaWrapperList()
        fun hasMedia() = mediaList.size() != 0
    }

    private val medialibrary by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
    val player by lazy(LazyThreadSafetyMode.NONE) { PlayerController(service.applicationContext) }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(service) }
    private val ctx by lazy(LazyThreadSafetyMode.NONE) { service.applicationContext }
    var currentIndex = -1
    private var nextIndex = -1
    private var prevIndex = -1
    private var previous = Stack<Int>()
    var stopAfter = -1
    var repeating = PlaybackStateCompat.REPEAT_MODE_NONE
    var shuffling = false
    var videoBackground = false
        private set
    var isBenchmark = false
    var isHardware = false
    private var parsed = false
    var savedTime = 0L
    private var random = Random(System.currentTimeMillis())
    private var newMedia = false
    @Volatile
    private var expanding = false
    private var entryUrl : String? = null
    val abRepeat by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<ABRepeat>().apply { value = ABRepeat() } }
    val abRepeatOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val videoStatsOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val delayValue by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<DelayValues>().apply { value = DelayValues() } }

    private val mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory

    fun hasCurrentMedia() = isValidPosition(currentIndex)

    fun hasPlaylist() = mediaList.size() > 1

    fun canShuffle() = mediaList.size() > 2

    fun isValidPosition(position: Int) = position in 0 until mediaList.size()

    init {
        repeating = settings.getInt(PLAYLIST_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)
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
        launch {
            val mediaList = ArrayList<MediaWrapper>()
            withContext(Dispatchers.IO) {
                for (location in mediaPathList) {
                    var mediaWrapper = medialibrary.getMedia(location)
                    if (mediaWrapper === null) {
                        if (!location.validateLocation()) {
                            Log.w(TAG, "Invalid location $location")
                            service.showToast(service.resources.getString(R.string.invalid_location, location), Toast.LENGTH_SHORT)
                            continue
                        }
                        Log.v(TAG, "Creating on-the-fly Media object for $location")
                        mediaWrapper = MLServiceLocator.getAbstractMediaWrapper(location.toUri())
                        mediaList.add(mediaWrapper)
                    } else
                        mediaList.add(mediaWrapper)
                }
            }
            load(mediaList, position)
        }
    }

    @MainThread
    suspend fun load(list: List<MediaWrapper>, position: Int, mlUpdate: Boolean = false) {
        saveMediaList()
        savePosition()
        mediaList.removeEventListener(this@PlaylistManager)
        previous.clear()
        videoBackground = false
        mediaList.replaceWith(list)
        if (!hasMedia()) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        currentIndex = if (isValidPosition(position)) position else 0

        // Add handler after loading the list
        mediaList.addEventListener(this@PlaylistManager)
        stopAfter = -1
        clearABRepeat()
        player.setRate(1.0f, false)
        playIndex(currentIndex)
        service.onPlaylistLoaded()
        if (mlUpdate) {
            service.awaitMedialibraryStarted()
            mediaList.replaceWith(withContext(Dispatchers.IO) { mediaList.copy.updateWithMLMeta() })
            executeUpdate()
            service.showNotification()
        }
    }

    @Volatile
    private var loadingLastPlaylist = false
    fun loadLastPlaylist(type: Int = PLAYLIST_TYPE_AUDIO) : Boolean {
        if (loadingLastPlaylist) return true
        loadingLastPlaylist = true
        val audio = type == PLAYLIST_TYPE_AUDIO
        val currentMedia = settings.getString(if (audio) KEY_CURRENT_AUDIO else KEY_CURRENT_MEDIA, "")
        if (currentMedia.isNullOrEmpty()) {
            loadingLastPlaylist = false
            return false
        }
        val locations = settings.getString(if (audio) KEY_AUDIO_LAST_PLAYLIST else KEY_MEDIA_LAST_PLAYLIST, null)
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
            shuffling = settings.getBoolean(if (audio) "audio_shuffling" else "media_shuffling", false)
            val position = max(0, settings.getInt(if (audio) "position_in_audio_list" else "position_in_media_list", 0))
            savedTime = settings.getLong(if (audio) "position_in_song" else "position_in_media", -1)
            if (!audio && position < playList.size && settings.getBoolean(VIDEO_PAUSED, false)) {
                playList[position].addFlags(MediaWrapper.MEDIA_PAUSED)
            }
            if (audio && position < playList.size) playList[position].addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            load(playList, position, true)
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
            if (getCurrentMedia()?.isPodcast == true) saveMediaMeta()
        }
    }

    @MainThread
    fun next(force : Boolean = false) {
        val size = mediaList.size()
        if (force || repeating != PlaybackStateCompat.REPEAT_MODE_ONE) {
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

    fun stop(systemExit: Boolean = false, video: Boolean = false) {
        clearABRepeat()
        stopAfter = -1
        videoBackground = false
        val job = getCurrentMedia()?.let {
            savePosition(video = video)
            val audio = isAudioList() // check before dispatching in saveMediaMeta()
            launch(start = CoroutineStart.UNDISPATCHED) {
                saveMediaMeta().join()
                if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && !audio) setResumeProgram(service.applicationContext, it)
            }
        }
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
    }

    @MainThread
    fun previous(force : Boolean) {
        if (hasPrevious() && currentIndex > 0 &&
                (force || !player.seekable || player.getCurrentTime() < PREVIOUS_LIMIT_DELAY)) {
            val size = mediaList.size()
            currentIndex = prevIndex
            if (previous.size > 0) previous.pop()
            if (size == 0 || prevIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !")
                player.stop()
                return
            }
            launch { playIndex(currentIndex) }
        } else player.setPosition(0F)
    }

    @MainThread
    fun shuffle() {
        if (shuffling) previous.clear()
        shuffling = !shuffling
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    @MainThread
    fun setRepeatType(repeatType: Int) {
        repeating = repeatType
        settings.putSingle(PLAYLIST_REPEAT_MODE_KEY, repeating)
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    fun setRenderer(item: RendererItem?) {
        player.setRenderer(item)
        showAudioPlayer.value = PlayerController.playbackState != PlaybackStateCompat.STATE_STOPPED && (item !== null || !player.isVideoPlaying())
    }

    suspend fun playIndex(index: Int, flags: Int = 0) {
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
        val isVideoPlaying = mw.type == MediaWrapper.TYPE_VIDEO && player.isVideoPlaying()
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
            if (AndroidDevices.isTv && isVideoPlaying) {
                VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
            }
            val title = mw.getMetaLong(MediaWrapper.META_TITLE)
            if (title > 0) uri = "$uri#$title".toUri()
            val start = getStartTime(mw)
            val media = mediaFactory.getFromUri(VLCInstance.getInstance(service), uri)
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
        addUpdateActor.offer(Unit)
    }

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
        service.executeUpdate()
    }

    fun saveMediaMeta() = launch(start = CoroutineStart.UNDISPATCHED) {
        val titleIdx = player.getTitleIdx()
        val chapterIdx = player.getChapterIdx()
        val currentMedia = getCurrentMedia() ?: return@launch
        if (currentMedia.uri.scheme == "fd") return@launch
        //Save progress
        val time = player.getCurrentTime()
        val length = player.getLength()
        val canSwitchToVideo = player.canSwitchToVideo()
        val rate = player.getRate()
        launch(Dispatchers.IO) {
            val media = medialibrary.findMedia(currentMedia) ?: return@launch
            if (media.id == 0L) return@launch
            if (titleIdx > 0) media.setLongMeta(MediaWrapper.META_TITLE, titleIdx.toLong())
            if (media.type == MediaWrapper.TYPE_VIDEO || canSwitchToVideo || media.isPodcast) {
                var progress = time / length.toFloat()
                if (progress > 0.95f || length - time < 10000) {
                    //increase seen counter if more than 95% of the media have been seen
                    //and reset progress to 0
                    media.setLongMeta(MediaWrapper.META_SEEN, media.seen+1)
                    progress = 0f
                }
                media.time = if (progress == 0f) 0L else time
                media.setLongMeta(MediaWrapper.META_PROGRESS, media.time)
            }
            media.setStringMeta(MediaWrapper.META_SPEED, rate.toString())
        }
    }

    fun setSpuTrack(index: Int) {
        if (!player.setSpuTrack(index)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L) launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, index.toLong()) }
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
        if (media.id == 0L) return
        if (player.canSwitchToVideo()) {
            if (settings.getBoolean("save_individual_audio_delay", true))
                player.setAudioDelay(media.getMetaLong(MediaWrapper.META_AUDIODELAY))
            player.setSpuTrack(media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK).toInt())
            player.setSpuDelay(media.getMetaLong(MediaWrapper.META_SUBTITLE_DELAY))
            val rateString = media.getMetaString(MediaWrapper.META_SPEED)
            if (!rateString.isNullOrEmpty()) {
                player.setRate(rateString.toFloat(), false)
            }
        }
    }

    @Synchronized
    private fun saveCurrentMedia() {
        val media = getCurrentMedia() ?: return
        val isAudio = isAudioList()
        settings.putSingle(if (isAudio) KEY_CURRENT_AUDIO else KEY_CURRENT_MEDIA, media.location)
    }

    private suspend fun saveMediaList() {
        if (getCurrentMedia() === null) return
        val locations = StringBuilder()
        withContext(Dispatchers.Default) {
            val list = mediaList.copy.takeIf { it.isNotEmpty() } ?: return@withContext
            for (mw in list) locations.append(" ").append(mw.uri.toString())
            //We save a concatenated String because putStringSet is APIv11.
            settings.putSingle(if (isAudioList()) KEY_AUDIO_LAST_PLAYLIST else KEY_MEDIA_LAST_PLAYLIST, locations.toString().trim())
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
        addUpdateActor.safeOffer(Unit)
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
                    if (repeating == PlaybackStateCompat.REPEAT_MODE_NONE) {
                        nextIndex = -1
                        return
                    } else {
                        previous.clear()
                        random = Random(System.currentTimeMillis())
                    }
                }
                random = Random(System.currentTimeMillis())
                // Find a new index not in previous.
                do {
                    nextIndex = random.nextInt(size)
                } while (nextIndex == currentIndex || previous.contains(nextIndex))
            } else {
                // normal playback
                if (currentIndex > 0) prevIndex = currentIndex - 1
                nextIndex = when {
                    currentIndex + 1 < size -> currentIndex + 1
                    repeating == PlaybackStateCompat.REPEAT_MODE_NONE -> -1
                    else -> 0
                }
            }
        }
    }

    fun previousTotalTime() = if (shuffling) {
        mediaList.copy.asSequence()
                .filterIndexed { index, _ -> previous.contains(index) }
                .map { it.length }
                .sum()
    } else {
        mediaList.copy.asSequence()
                .take(currentIndex)
                .map { it.length }
                .sum()
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    private suspend fun expand(updateHistory: Boolean): Int {
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
                withContext(Dispatchers.IO) { child.parse() }
                mediaList.insert(index+i, MLServiceLocator.getAbstractMediaWrapper(child))
                child.release()
            }
            mediaList.addEventListener(this)
            addUpdateActor.offer(Unit)
            service.onMediaListChanged()
            if (mrl !== null && ml.count == 1) {
                getCurrentMedia()?.apply {
                    AppScope.launch(Dispatchers.IO) {
                        if (stream) {
                            type = MediaWrapper.TYPE_STREAM
                            entryUrl = mrl
                            medialibrary.getMedia(mrl)?.run { if (id > 0) medialibrary.removeExternalMedia(id) }
                        } else if (uri.scheme != "fd") {
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

    private suspend fun getStartTime(mw: MediaWrapper) : Long {
        val start = when {
            mw.hasFlag(MediaWrapper.MEDIA_FROM_START) -> {
                mw.removeFlags(MediaWrapper.MEDIA_FROM_START)
                0L
            }
            savedTime <= 0L -> when {
                mw.time > 0L -> mw.time
                mw.type == MediaWrapper.TYPE_VIDEO || mw.isPodcast -> withContext(Dispatchers.IO) { medialibrary.findMedia(mw).getMetaLong(MediaWrapper.META_PROGRESS) }
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
            putBoolean(if (audio) "audio_shuffling" else "media_shuffling", shuffling)
            putInt(if (audio) "position_in_audio_list" else "position_in_media_list", if (reset) 0 else currentIndex)
            putLong(if (audio) "position_in_song" else "position_in_media", if (reset) 0L else player.getCurrentTime())
            if (!audio) {
                putFloat(VIDEO_SPEED, player.getRate())
            }
        }
    }

    /**
     * Append to the current existing playlist
     */
    @MainThread
    suspend fun append(list: List<MediaWrapper>) {
        if (!hasCurrentMedia()) {
            launch { load(list, 0, mlUpdate = true) }
            return
        }
        val list = withContext(Dispatchers.IO) { list.updateWithMLMeta() }
        mediaList.removeEventListener(this)
        for (media in list) mediaList.add(media)
        mediaList.addEventListener(this)
        addUpdateActor.offer(Unit)
    }

    /**
     * Insert into the current existing playlist
     */

    @MainThread
    fun insertNext(list: List<MediaWrapper>) {
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
    fun insertItem(position: Int, mw: MediaWrapper) = mediaList.insert(position, mw)


    @MainThread
    fun remove(position: Int) = mediaList.remove(position)

    @MainThread
    fun removeLocation(location: String) = mediaList.remove(location)

    fun getMediaListSize()= mediaList.size()

    fun getMediaList(): List<MediaWrapper> = mediaList.copy

    fun setABRepeatValue(time: Long) {
        val value = abRepeat.value ?: ABRepeat()
        when {
            value.start == -1L -> value.start = time
            value.start > time -> {
                value.stop = value.start
                value.start = time
            }
            else -> value.stop = time
        }
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

    fun resetABRepeatValues() {
        abRepeat.value = ABRepeat()
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
                if (parsed && player.updateCurrentMeta(event.metaId, getCurrentMedia())) service.executeUpdate()
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
            if (parsed) service.showNotification()
        }
    }

    private val mediaplayerEventListener = object : MediaPlayerEventListener {
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
                        saveMediaList()
                        savePosition(reset = true)
                        saveCurrentMedia()
                        newMedia = false
                        if (player.hasRenderer || !player.isVideoPlaying()) showAudioPlayer.value = true
                        savePlaycount(mw)
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    clearABRepeat()
                    if (currentIndex != nextIndex) {
                        saveMediaMeta()
                        if (isBenchmark) player.setPreviousStats()
                        if (nextIndex == -1) savePosition(reset = true)
                    }
                    if (stopAfter == currentIndex) {
                        stop()
                    } else {
                        if (isBenchmark) player.setCurrentStats()
                        determinePrevAndNextIndices(true)
                        if (!hasNext()) getCurrentMedia()?.let {
                            if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && !isAudioList()) setResumeProgram(service.applicationContext, it)
                        }
                        next()
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    service.showToast(service.getString(
                            R.string.invalid_location,
                            getCurrentMedia()?.location ?: ""), Toast.LENGTH_SHORT, true)
                    if (currentIndex != nextIndex) next() else stop()
                }
                MediaPlayer.Event.TimeChanged -> {
                    abRepeat.value?.let {
                        if (it.stop != -1L && player.getCurrentTime() > it.stop) player.seek(it.start)
                    }
                }
                MediaPlayer.Event.SeekableChanged -> if (event.seekable && settings.getBoolean(KEY_PLAYBACK_SPEED_PERSIST, false)) {
                    player.setRate(settings.getFloat(KEY_PLAYBACK_RATE, 1.0f), false)
                }
            }
            service.onMediaPlayerEvent(event)
        }
    }

    private suspend fun savePlaycount(mw: MediaWrapper) {
        if (settings.getBoolean(PLAYBACK_HISTORY, true)) withContext(Dispatchers.IO) {
            var id = mw.id
            if (id == 0L) {
                var internalMedia = medialibrary.findMedia(mw)
                if (internalMedia != null && internalMedia.id != 0L)
                    id = internalMedia.id
                else {
                    internalMedia = if (mw.type == MediaWrapper.TYPE_STREAM) {
                        medialibrary.addStream(Uri.decode(entryUrl ?: mw.uri.toString()), mw.title).also {
                            entryUrl = null
                        }
                    } else medialibrary.addMedia(Uri.decode(mw.uri.toString()))
                    if (internalMedia != null) id = internalMedia.id
                }
            }
            if (id != 0L) medialibrary.increasePlayCount(id)
        }
    }

    internal fun isAudioList() = !player.isVideoPlaying() && mediaList.isAudioList
}

class ABRepeat(var start: Long = -1L, var stop: Long = -1L)
class DelayValues(var start: Long = -1L, var stop: Long = -1L)
