package org.videolan.vlc.media

import android.content.Intent
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.preferences.PreferencesFragment
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.util.*
import java.util.*

private const val TAG = "VLC/PlaylistManager"
private const val PREVIOUS_LIMIT_DELAY = 5000L
private const val AUDIO_REPEAT_MODE_KEY = "audio_repeat_mode"

class PlaylistManager(val service: PlaybackService) : MediaWrapperList.EventListener, Media.EventListener, CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate

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
    var repeating = REPEAT_NONE
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
    val abRepeat by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<ABRepeat>().apply { value = ABRepeat() } }

    fun hasCurrentMedia() = isValidPosition(currentIndex)

    fun hasPlaylist() = mediaList.size() > 1

    fun canShuffle() = mediaList.size() > 2

    fun isValidPosition(position: Int) = position in 0 until mediaList.size()

    init {
        if (settings.getBoolean("audio_save_repeat", false)) repeating = settings.getInt(AUDIO_REPEAT_MODE_KEY, REPEAT_NONE)
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
        val mediaList = ArrayList<MediaWrapper>()

        for (location in mediaPathList) {
            var mediaWrapper = medialibrary.getMedia(location)
            if (mediaWrapper === null) {
                if (!location.validateLocation()) {
                    Log.w(TAG, "Invalid location $location")
                    service.showToast(service.resources.getString(R.string.invalid_location, location), Toast.LENGTH_SHORT)
                    continue
                }
                Log.v(TAG, "Creating on-the-fly Media object for $location")
                mediaWrapper = MediaWrapper(Uri.parse(location))
            }
            mediaList.add(mediaWrapper)
        }
        load(mediaList, position)
    }

    @MainThread
    fun load(list: List<MediaWrapper>, position: Int, mlUpdate: Boolean = false) {
        mediaList.removeEventListener(this)
        mediaList.clear()
        previous.clear()
        videoBackground = false
        launch {
            withContext(Dispatchers.IO) { for (media in list) mediaList.add(medialibrary.findMedia(media)) }
            if (!hasMedia()) {
                Log.w(TAG, "Warning: empty media list, nothing to play !")
                return@launch
            }
            medialibrary.pauseBackgroundOperations()
            currentIndex = if (isValidPosition(position)) position else 0

            // Add handler after loading the list
            mediaList.addEventListener(this@PlaylistManager)
            stopAfter = -1
            clearABRepeat()
            playIndex(currentIndex)
            onPlaylistLoaded()
            if (mlUpdate) launch(Dispatchers.IO) { mediaList.updateWithMLMeta() }
        }
    }

    @Volatile
    private var loadingLastPlaylist = false
    fun loadLastPlaylist(type: Int = PLAYLIST_TYPE_AUDIO) : Boolean {
        if (loadingLastPlaylist) return true
        loadingLastPlaylist = true
        val audio = type == PLAYLIST_TYPE_AUDIO
        val currentMedia = settings.getString(if (audio) "current_song" else "current_media", "")!!
        if (currentMedia.isEmpty()) {
            loadingLastPlaylist = false
            return false
        }
        val locations = settings.getString(if (audio) "audio_list" else "media_list", null)?.split(" ".toRegex())?.dropLastWhile({ it.isEmpty() })?.toTypedArray()
        if (locations?.isNotEmpty() != true) {
            loadingLastPlaylist = false
            return false
        }
        launch {
            val playList = withContext(Dispatchers.Default) {
                locations.asSequence().map { Uri.decode(it) }.mapTo(ArrayList(locations.size)) { MediaWrapper(Uri.parse(it)) }
            }
            // load playlist
            shuffling = settings.getBoolean(if (audio) "audio_shuffling" else "media_shuffling", false)
            repeating = settings.getInt(if (audio) "audio_repeating" else "media_repeating", REPEAT_NONE)
            val position = settings.getInt(if (audio) "position_in_audio_list" else "position_in_media_list", 0)
            savedTime = settings.getLong(if (audio) "position_in_song" else "position_in_media", -1)
            if (!audio) {
                if (position < playList.size && settings.getBoolean(PreferencesActivity.VIDEO_PAUSED, false)) {
                    playList[position].addFlags(MediaWrapper.MEDIA_PAUSED)
                }
                val rate = settings.getFloat(PreferencesActivity.VIDEO_SPEED, player.getRate())
                if (rate != 1.0f) player.setRate(rate, false)
            }
            load(playList, position, true)
            loadingLastPlaylist = false
        }
        return true
    }

    private suspend fun onPlaylistLoaded() {
        service.onPlaylistLoaded()
        determinePrevAndNextIndices()
    }

    fun play() {
        if (hasMedia()) player.play()
    }

    fun pause() {
        if (player.pause()) savePosition()
    }

    @MainThread
    fun next() {
        val size = mediaList.size()
        previous.push(currentIndex)
        currentIndex = nextIndex
        if (size == 0 || currentIndex < 0 || currentIndex >= size) {
            Log.w(TAG, "Warning: invalid next index, aborted !")
            //Close video player if started
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(EXIT_PLAYER))
            stop()
            return
        }
        videoBackground = videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        launch { playIndex(currentIndex) }
    }

    fun stop(systemExit: Boolean = false) {
        clearABRepeat()
        stopAfter = -1
        videoBackground = false
        getCurrentMedia()?.let {
            savePosition()
            saveMediaMeta()
            if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && !isAudioList()) setResumeProgram(service.applicationContext, it)

        }
        mediaList.removeEventListener(this)
        previous.clear()
        currentIndex = -1
        if (systemExit) player.release()
        else player.restart()
        mediaList.clear()
        showAudioPlayer.value = false
        service.onPlaybackStopped(systemExit)
        medialibrary.resumeBackgroundOperations()
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
        if (isAudioList() && settings.getBoolean("audio_save_repeat", false))
            settings.edit().putInt(AUDIO_REPEAT_MODE_KEY, repeating).apply()
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    fun setRenderer(item: RendererItem?) {
        player.setRenderer(item)
        showAudioPlayer.value = player.playbackState != PlaybackStateCompat.STATE_STOPPED && (item !== null || !player.isVideoPlaying())
    }

    suspend fun playIndex(index: Int, flags: Int = 0) {
        if (mediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            medialibrary.resumeBackgroundOperations()
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
        if (TextUtils.equals(mw.uri.scheme, "content")) MediaUtils.retrieveMediaTitle(mw)

        if (mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) && player.getAudioTracksCount() == 0) {
            determinePrevAndNextIndices(true)
            skipMedia()
        } else if (mw.type != MediaWrapper.TYPE_VIDEO || isVideoPlaying || player.hasRenderer
                || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)) {
            val uri = FileUtils.getUri(mw.uri)
            if (uri == null) {
                skipMedia()
                return
            }
            val start = getStartTime(mw)
            val media = Media(VLCInstance.get(), Uri.parse(Tools.encodeVLCString(uri.toString())))
            media.addOption(":start-time=$start")
            VLCOptions.setMediaOptions(media, ctx, flags or mw.flags)
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
            player.startPlayback(media, mediaplayerEventListener)
            player.setSlaves(media, mw)
            newMedia = true
            determinePrevAndNextIndices()
            service.onNewPlayback()
            if (settings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)) launch(Dispatchers.IO) {
                var id = mw.id
                if (id == 0L) {
                    var internalMedia = medialibrary.findMedia(mw)
                    if (internalMedia != null && internalMedia.id != 0L)
                        id = internalMedia.id
                    else {
                        internalMedia = if (mw.type == MediaWrapper.TYPE_STREAM) medialibrary.addStream(Uri.decode(mw.uri.toString()), mw.title) else medialibrary.addMedia(Uri.decode(mw.uri.toString()))
                        if (internalMedia != null)
                            id = internalMedia.id
                    }
                }
                medialibrary.increasePlayCount(id)
            }
            saveCurrentMedia()
        } else { //Start VideoPlayer for first video, it will trigger playIndex when ready.
            player.stop()
            VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
        }
    }

    private fun skipMedia() {
        if (currentIndex != nextIndex) next()
        else stop(false)
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
        if (player.isVideoPlaying() && !hasRenderer) {//Player is already running, just send it an intent
            player.setVideoTrackEnabled(true)
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(service).sendBroadcast(
                    VideoPlayerActivity.getIntent(PLAY_FROM_SERVICE,
                            media, false, currentIndex))
        } else if (!player.switchToVideo) { //Start the video player
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(), media.uri, currentIndex)
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
    override fun onItemAdded(index: Int, mrl: String?) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemAdded")
        if (currentIndex >= index && !expanding) ++currentIndex
        launch {
            determinePrevAndNextIndices()
            executeUpdate()
            saveMediaList()
        }
    }

    @MainThread
    override fun onItemRemoved(index: Int, mrl: String?) {
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

    fun saveMediaMeta() {
        val media = medialibrary.findMedia(getCurrentMedia())
        if (media === null || media.id == 0L) return
        val canSwitchToVideo = player.canSwitchToVideo()
        if (media.type == MediaWrapper.TYPE_VIDEO || canSwitchToVideo || media.isPodcast) {
            //Save progress
            val time = player.getCurrentTime()
            val length = player.getLength()
            var progress = time / length.toFloat()
            if (progress > 0.95f || length - time < 10000) {
                //increase seen counter if more than 95% of the media have been seen
                //and reset progress to 0
                media.setLongMeta(MediaWrapper.META_SEEN, ++media.seen)
                progress = 0f
            }
            media.time = if (progress == 0f) 0L else time
            media.setLongMeta(MediaWrapper.META_PROGRESS, media.time)
        }
    }

    fun setSpuTrack(index: Int) {
        if (!player.setSpuTrack(index)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L) launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, player.getSpuTrack().toLong()) }
    }

    fun setAudioDelay(delay: Long) {
        if (!player.setAudioDelay(delay)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L && settings.getBoolean("save_individual_audio_delay", false)) {
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
            if (settings.getBoolean("save_individual_audio_delay", false))
                player.setAudioDelay(media.getMetaLong(MediaWrapper.META_AUDIODELAY))
            player.setSpuTrack(media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK).toInt())
            player.setSpuDelay(media.getMetaLong(MediaWrapper.META_SUBTITLE_DELAY))
        }
    }

    @Synchronized
    private fun saveCurrentMedia() {
        val media = getCurrentMedia() ?: return
        val isAudio = isAudioList()
        settings.edit()
                .putString(if (isAudio) "current_song" else "current_media", media.location)
                .apply()
    }

    @Synchronized
    private fun saveMediaList() {
        if (getCurrentMedia() === null) return
        val locations = StringBuilder()
        for (mw in mediaList.all) locations.append(" ").append(Uri.encode(Uri.decode(mw.uri.toString())))
        //We save a concatenated String because putStringSet is APIv11.
        settings.edit()
                .putString(if (isAudioList()) "audio_list" else "media_list", locations.toString().trim())
                .apply()
    }

    override fun onItemMoved(indexBefore: Int, indexAfter: Int, mrl: String?) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemMoved")
        if (currentIndex == indexBefore) {
            currentIndex = indexAfter
            if (indexAfter > indexBefore)
                --currentIndex
        } else if (currentIndex in indexAfter..(indexBefore - 1))
            ++currentIndex
        else if (currentIndex in (indexBefore + 1)..(indexAfter - 1))
            --currentIndex

        // If we are in random mode, we completely reset the stored previous track
        // as their indices changed.
        previous.clear()
        launch {
            determinePrevAndNextIndices()
            executeUpdate()
            saveMediaList()
        }
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

            // Repeating once doesn't change the index
            if (repeating == REPEAT_ONE) {
                nextIndex = currentIndex
                prevIndex = nextIndex
            } else {
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
                        if (repeating == REPEAT_NONE) {
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
                    if (currentIndex > 0)
                        prevIndex = currentIndex - 1
                    nextIndex = when {
                        currentIndex + 1 < size -> currentIndex + 1
                        repeating == REPEAT_NONE -> -1
                        else -> 0
                    }
                }
            }
        }
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    private suspend fun expand(updateHistory: Boolean): Int {
        val index = currentIndex
        val stream = getCurrentMedia()?.type == MediaWrapper.TYPE_STREAM
        val ml = player.expand()
        var ret = -1

        if (ml != null && ml.count > 0) {
            val mrl = if (updateHistory) getCurrentMedia()?.location else null
            mediaList.remove(index)
            for (i in ml.count - 1 downTo 0) {
                val child = ml.getMediaAt(i)
                child.parse()
                mediaList.insert(index, MediaWrapper(child))
                child.release()
            }
            if (mrl !== null && ml.count == 1) {
                if (stream) {
                    getCurrentMedia()!!.type = MediaWrapper.TYPE_STREAM
                    medialibrary.addStream(mrl, getCurrentMedia()!!.title)
                } else medialibrary.addToHistory(mrl, getCurrentMedia()!!.title)
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
                mw.time >= 0L -> mw.time
                mw.type == MediaWrapper.TYPE_VIDEO || mw.isPodcast -> withContext(Dispatchers.IO) { medialibrary.findMedia(mw).getMetaLong(MediaWrapper.META_PROGRESS) }
                else -> 0L
            }
            else -> savedTime
        }
        savedTime = 0L
        return start/1000L
    }

    @Synchronized
    private fun savePosition(reset: Boolean = false) {
        if (!hasMedia()) return
        val editor = settings.edit()
        val audio = isAudioList()
        editor.putBoolean(if (audio) "audio_shuffling" else "media_shuffling", shuffling)
        editor.putInt(if (audio) "audio_repeating" else "media_repeating", repeating)
        editor.putInt(if (audio) "position_in_audio_list" else "position_in_media_list", if (reset) 0 else currentIndex)
        editor.putLong(if (audio) "position_in_song" else "position_in_media", if (reset) 0L else player.getCurrentTime())
        if (!audio) {
            editor.putBoolean(PreferencesActivity.VIDEO_PAUSED, !player.isPlaying())
            editor.putFloat(PreferencesActivity.VIDEO_SPEED, player.getRate())
        }
        editor.apply()
    }

    /**
     * Append to the current existing playlist
     */
    @MainThread
    suspend fun append(list: List<MediaWrapper>) {
        if (!hasCurrentMedia()) {
            load(list, 0)
            return
        }
        val list = withContext(Dispatchers.IO) { list.getWithMLMeta() }
        mediaList.removeEventListener(this)
        for (media in list) mediaList.add(media)
        mediaList.addEventListener(this)
    }

    /**
     * Insert into the current existing playlist
     */

    @MainThread
    fun insertNext(list: List<MediaWrapper>) {
        if (!hasCurrentMedia()) {
            load(list, 0)
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

    fun getMediaList(): List<MediaWrapper> = mediaList.all.toList()

    fun toggleABRepeat() {
        val time = player.getCurrentTime()
        val value = abRepeat.value ?: ABRepeat()
        when {
            abRepeat.value?.start == -1L -> abRepeat.value = value.apply { start = time }
            abRepeat.value?.stop == -1L && time > abRepeat.value?.start ?: 0L -> {
                abRepeat.value = value.apply { stop = time }
                player.seek(abRepeat.value!!.start)
            }
            else -> clearABRepeat()
        }
    }

    fun clearABRepeat() {
        abRepeat.value = abRepeat.value?.apply {
            start = -1L
            stop = -1L
        }
    }

    override fun onEvent(event: Media.Event) {
        var update = true
        when (event.type) {
            Media.Event.MetaChanged -> {
                /* Update Meta if file is already parsed */
                if (parsed && player.updateCurrentMeta(event.metaId, getCurrentMedia())) service.executeUpdate()
                if (BuildConfig.DEBUG) Log.i(TAG, "Media.Event.MetaChanged: " + event.metaId)
            }
            Media.Event.ParsedChanged -> {
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
                    medialibrary.pauseBackgroundOperations()
                    val mw = withContext(Dispatchers.IO) {
                        val current = getCurrentMedia()
                        medialibrary.findMedia(current).apply { if (type == -1) type = current?.type ?: -1 }
                    }
                    if (newMedia) {
                        loadMediaMeta(mw)
                        if (mw.type == MediaWrapper.TYPE_STREAM) medialibrary.addToHistory(mw.location, mw.title)
                        saveMediaList()
                        savePosition(true)
                        saveCurrentMedia()
                        newMedia = false
                        if (player.hasRenderer || !player.isVideoPlaying()) showAudioPlayer.value = true
                    }
                }
                MediaPlayer.Event.Paused -> medialibrary.resumeBackgroundOperations()
                MediaPlayer.Event.EndReached -> {
                    clearABRepeat()
                    if (currentIndex != nextIndex) {
                        saveMediaMeta()
                        if (isBenchmark) player.setPreviousStats()
                        if (nextIndex == -1) savePosition(true)
                    }
                    if (stopAfter == currentIndex) {
                        stop()
                    } else {
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
                            getCurrentMedia()?.location ?: ""), Toast.LENGTH_SHORT)
                    if (currentIndex != nextIndex) next() else stop()
                }
                MediaPlayer.Event.TimeChanged -> {
                    abRepeat.value?.let {
                        if (it.stop != -1L && player.getCurrentTime() > it.stop) player.seek(it.start)
                    }
                }
            }
            service.onMediaPlayerEvent(event)
        }
    }

    internal fun isAudioList() = !player.canSwitchToVideo() && mediaList.isAudioList
}

class ABRepeat(var start: Long = -1L, var stop: Long = -1L)
