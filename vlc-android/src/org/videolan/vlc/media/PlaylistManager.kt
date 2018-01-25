package org.videolan.vlc.media

import android.content.Intent
import android.net.Uri
import android.support.annotation.MainThread
import android.support.v4.content.LocalBroadcastManager
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.*
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.preferences.PreferencesFragment
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.util.*
import java.util.*


class PlaylistManager(val service: PlaybackService) : MediaWrapperList.EventListener, Media.EventListener {

    private val TAG = "VLC/PlaylistManager"
    private val PREVIOUS_LIMIT_DELAY = 5000L
    private val AUDIO_REPEAT_MODE_KEY = "audio_repeat_mode"

    private val medialibrary by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
    val player by lazy(LazyThreadSafetyMode.NONE) { PlayerController() }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { VLCApplication.getSettings() }
    private val ctx by lazy(LazyThreadSafetyMode.NONE) { VLCApplication.getAppContext() }
    private val mediaList = MediaWrapperList()
    var currentIndex = -1
    private var nextIndex = -1
    private var prevIndex = -1
    private var previous = Stack<Int>()
    var repeating = Constants.REPEAT_NONE
    var shuffling = false
    var videoBackground = false
        private set
    var isBenchmark = false
    var isHardware = false
    private var parsed = false
    var savedTime = 0L
    private var random = Random(System.currentTimeMillis())
    private var newMedia = false

    fun hasMedia() = mediaList.size() != 0
    fun hasCurrentMedia() = isValidPosition(currentIndex)

    fun hasPlaylist() = mediaList.size() > 1

    fun canShuffle() = mediaList.size() > 2

    fun isValidPosition(position: Int) = position in 0 until mediaList.size()

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

        for (i in mediaPathList.indices) {
            val location = mediaPathList[i]
            var mediaWrapper = medialibrary.getMedia(location)
            if (mediaWrapper == null) {
                if (!location.validateLocation()) {
                    Log.w(TAG, "Invalid location " + location)
                    service.showToast(service.resources.getString(R.string.invalid_location, location), Toast.LENGTH_SHORT)
                    continue
                }
                Log.v(TAG, "Creating on-the-fly Media object for " + location)
                mediaWrapper = MediaWrapper(Uri.parse(location))
            }
            mediaList.add(mediaWrapper)
        }
        load(mediaList, position)
    }

    fun load(list: List<MediaWrapper>, position: Int) {
        mediaList.removeEventListener(this)
        mediaList.clear()
        previous.clear()
        for (media in list) mediaList.add(media)
        if (!hasMedia()) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        currentIndex = if (isValidPosition(position)) position else 0

        // Add handler after loading the list
        mediaList.addEventListener(this)
        playIndex(position)
        onPlaylistLoaded()
    }

    @Volatile
    private var loadingLastPlaylist = false
    fun loadLastPlaylist(type: Int) {
        if (loadingLastPlaylist) return
        loadingLastPlaylist = true
        launch(UI, CoroutineStart.UNDISPATCHED) {
            val audio = type == Constants.PLAYLIST_TYPE_AUDIO
            val currentMedia = settings.getString(if (audio) "current_song" else "current_media", "")
            if ("" == currentMedia) return@launch
            val locations = settings.getString(if (audio) "audio_list" else "media_list", "")!!.split(" ".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
            if (Util.isArrayEmpty(locations)) return@launch
            val playList = async {
                locations.map { Uri.decode(it) }
                        .mapTo(ArrayList<MediaWrapper>(locations.size)) { medialibrary.findMedia(MediaWrapper(Uri.parse(it))) }
            }.await()
            // load playlist
            shuffling = settings.getBoolean(if (audio) "audio_shuffling" else "media_shuffling", false)
            setRepeatType(settings.getInt(if (audio) "audio_repeating" else "media_repeating", Constants.REPEAT_NONE))
            val position = settings.getInt(if (audio) "position_in_audio_list" else "position_in_media_list", 0)
            savedTime = settings.getLong(if (audio) "position_in_song" else "position_in_media", -1)
            if (!audio) {
                if (position < playList.size && settings.getBoolean(PreferencesActivity.VIDEO_PAUSED, false)) {
                    playList[position].addFlags(MediaWrapper.MEDIA_PAUSED)
                }
                val rate = settings.getFloat(PreferencesActivity.VIDEO_SPEED, player.getRate())
                if (rate != 1.0f) player.setRate(rate, false)
            }
            load(playList, position)
            loadingLastPlaylist = false
        }
    }

    private fun onPlaylistLoaded() {
        service.onPlaylistLoaded()
        saveMediaList()
        savePosition(true)
        saveCurrentMedia()
        determinePrevAndNextIndices()
    }

    fun play() = player.play()

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
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(Constants.EXIT_PLAYER))
            player.stop()
            return
        }
        videoBackground = !player.isVideoPlaying() && player.canSwitchToVideo()
        playIndex(currentIndex)
    }

    fun stop(systemExit: Boolean = false) {
        savePosition()
        if (hasMedia()) saveMediaMeta()
        player.releaseMedia()
        mediaList.removeEventListener(this)
        previous.clear()
        currentIndex = -1
        mediaList.clear()
        if (systemExit) player.release() else player.restart()
        medialibrary.resumeBackgroundOperations()
        service.onPlaybackStopped()
        if (!systemExit) service.hideNotification()
    }

    @MainThread
    fun previous(force : Boolean) {
        if (hasPrevious() && currentIndex > 0 &&
                (force || !player.seekable || player.getTime() < PREVIOUS_LIMIT_DELAY)) {
            val size = mediaList.size()
            currentIndex = prevIndex
            if (previous.size > 0) previous.pop()
            if (size == 0 || prevIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !")
                player.stop()
                return
            }
            playIndex(currentIndex)
        } else player.setPosition(0F)
    }

    fun shuffle() {
        if (shuffling) previous.clear()
        shuffling = !shuffling
        savePosition()
        determinePrevAndNextIndices()
    }

    fun setRepeatType(repeatType: Int) {
        repeating = repeatType
        if (mediaList.isAudioList && settings.getBoolean("audio_save_repeat", false))
            settings.edit().putInt(AUDIO_REPEAT_MODE_KEY, repeating).apply()
        savePosition()
        determinePrevAndNextIndices()
    }

    fun playIndex(index: Int, flags: Int = 0) {
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
        parsed = false
        player.switchToVideo = false
        if (TextUtils.equals(mw.uri.scheme, "content")) MediaUtils.retrieveMediaTitle(mw)

        if (mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) && player.getAudioTracksCount() == 0) {
            next()
        } else if (mw.type != MediaWrapper.TYPE_VIDEO || isVideoPlaying || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)
                || RendererDelegate.selectedRenderer !== null) {
            launch(UI, CoroutineStart.UNDISPATCHED) {
                val media = Media(VLCInstance.get(), FileUtils.getUri(mw.uri))
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
                mw.slaves?.let {
                    for (slave in it) media.addSlave(slave)
                    launch { MediaDatabase.getInstance().saveSlaves(mw) }
                }
                media.setEventListener(this@PlaylistManager)
                player.setSlaves(mw)
                player.startPlayback(media, mediaplayerEventListener)
                media.release()
                if (savedTime <= 0L && mw.time >= 0L && mw.isPodcast) savedTime = mw.time
                determinePrevAndNextIndices()
                service.onNewPlayback(mw)
                if (settings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)) launch {
                    var id = mw.id
                    if (id == 0L) {
                        var internalMedia = medialibrary.findMedia(mw)
                        if (internalMedia != null && internalMedia.id != 0L)
                            id = internalMedia.id
                        else {
                            internalMedia = medialibrary.addMedia(mw.uri.toString())
                            if (internalMedia != null)
                                id = internalMedia.id
                        }
                    }
                    medialibrary.increasePlayCount(id)
                }
                saveCurrentMedia()
                newMedia = true
            }
        } else { //Start VideoPlayer for first video, it will trigger playIndex when ready.
            VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
        }
    }

    fun onServiceDestroyed() {
        player.stop()
        player.release()
    }

    @MainThread
    fun switchToVideo(): Boolean {
        val media = getCurrentMedia()
        if (media === null || media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo())
            return false
        videoBackground = false
        if (player.isVideoPlaying()) {//Player is already running, just send it an intent
            player.setVideoTrackEnabled(true)
            LocalBroadcastManager.getInstance(service).sendBroadcast(
                    VideoPlayerActivity.getIntent(Constants.PLAY_FROM_SERVICE,
                            media, false, currentIndex))
        } else if (!player.switchToVideo) {//Start the video player
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    media.uri, currentIndex)
            player.switchToVideo = true
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

    override fun onItemAdded(index: Int, mrl: String?) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemAdded")
        if (currentIndex >= index && !player.expanding) ++currentIndex
        determinePrevAndNextIndices()
        executeUpdate()
        saveMediaList()
    }

    override fun onItemRemoved(index: Int, mrl: String?) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemDeleted")
        if (currentIndex >= index && !player.expanding) --currentIndex
        determinePrevAndNextIndices()
        if (currentIndex == index && !player.expanding) {
            when {
                nextIndex != -1 -> next()
                currentIndex != -1 -> playIndex(currentIndex, 0)
                else -> stop()
            }
        }
        executeUpdate()
        saveMediaList()
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
            val time = player.getTime()
            val length = player.getLength()
            var progress = time / length.toFloat()
            if (progress > 0.95f || length - time < 10000) {
                //increase seen counter if more than 95% of the media have been seen
                //and reset progress to 0
                media.setLongMeta(MediaWrapper.META_SEEN, ++media.seen)
                progress = 0f
            }
            media.time = if (progress == 0f) 0L else time
            media.setLongMeta(MediaWrapper.META_PROGRESS, (progress * 100).toLong())
        }
        if (canSwitchToVideo) {
            //Save audio delay
            if (settings.getBoolean("save_individual_audio_delay", false))
                media.setLongMeta(MediaWrapper.META_AUDIODELAY, player.getAudioDelay())
            media.setLongMeta(MediaWrapper.META_SUBTITLE_DELAY, player.getSpuDelay())
            media.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, player.getSpuTrack().toLong())
        }
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
    fun saveCurrentMedia() {
        settings.edit()
                .putString(if (mediaList.isAudioList) "current_song" else "current_media", mediaList.getMRL(Math.max(currentIndex, 0)))
                .apply()
    }

    @Synchronized
    private fun saveMediaList() {
        if (getCurrentMedia() == null) return
        val locations = StringBuilder()
        for (mw in mediaList.all) locations.append(" ").append(mw.uri.toString())
        //We save a concatenated String because putStringSet is APIv11.
        settings.edit()
                .putString(if (player.canSwitchToVideo() || !mediaList.isAudioList) "media_list" else "audio_list", locations.toString().trim { it <= ' ' })
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
        determinePrevAndNextIndices()
        executeUpdate()
        saveMediaList()
    }

    private fun determinePrevAndNextIndices(expand: Boolean = false) {
        if (expand) {
            player.expanding = true
            nextIndex = expand(getCurrentMedia()!!.type == MediaWrapper.TYPE_STREAM)
            player.expanding = false
        } else {
            nextIndex = -1
        }
        prevIndex = -1

        if (nextIndex == -1) {
            // No subitems; play the next item.
            val size = mediaList.size()
            shuffling = shuffling and (size > 2)

            // Repeating once doesn't change the index
            if (repeating == Constants.REPEAT_ONE) {
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
                        if (repeating == Constants.REPEAT_NONE) {
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
                        repeating == Constants.REPEAT_NONE -> -1
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
    private fun expand(updateHistory: Boolean): Int {
        val media = player.getMedia()
        if (media === null) return -1
        val mrl = if (updateHistory) media.uri.toString() else null
        val ml = media.subItems()
        media.release()

        var ret = -1

        if (ml != null && ml.count > 0) {
            mediaList.remove(currentIndex)
            for (i in ml.count - 1 downTo 0) {
                val child = ml.getMediaAt(i)
                child.parse()
                mediaList.insert(currentIndex, MediaWrapper(child))
                child.release()
            }
            if (mrl !== null && ml.count == 1)
                Medialibrary.getInstance().addToHistory(mrl, mediaList.getMedia(currentIndex)!!.title)
            ret = currentIndex
        }
        ml?.release()
        return ret
    }

    fun getCurrentMedia() = mediaList.getMedia(currentIndex)

    fun getPrevMedia() = if (isValidPosition(prevIndex)) mediaList.getMedia(prevIndex) else null

    fun getNextMedia() = if (isValidPosition(nextIndex)) mediaList.getMedia(nextIndex) else null

    fun getMedia(position: Int) = mediaList.getMedia(position)

    private fun seekToResume(media: MediaWrapper) {
        var mw = media
        if (savedTime > 0L) {
            if (savedTime < 0.95 * player.getLength()) player.seek(savedTime)
            savedTime = 0L
        } else {
            val length = player.getLength()
            if (mw.length <= 0L && length > 0L) {
                mw = medialibrary.findMedia(mw)
                if (mw.id != 0L) {
                    mw.time = (mw.getMetaLong(MediaWrapper.META_PROGRESS) * length.toDouble()).toLong() / 100L
                    if (mw.time > 0L) player.seek(mw.time)
                }
            }
        }
    }

    @Synchronized
    private fun savePosition(reset: Boolean = false) {
        if (!hasMedia()) return
        val editor = settings.edit()
        val audio = !player.canSwitchToVideo() && mediaList.isAudioList
        editor.putBoolean(if (audio) "audio_shuffling" else "media_shuffling", shuffling)
        editor.putInt(if (audio) "audio_repeating" else "media_repeating", repeating)
        editor.putInt(if (audio) "position_in_audio_list" else "position_in_media_list", if (reset) 0 else currentIndex)
        editor.putLong(if (audio) "position_in_song" else "position_in_media", if (reset) 0L else player.getTime())
        if (!audio) {
            editor.putBoolean(PreferencesActivity.VIDEO_PAUSED, !player.isPlaying())
            editor.putFloat(PreferencesActivity.VIDEO_SPEED, player.getRate())
        }
        editor.apply()
    }

    /**
     * Append to the current existing playlist
     */
    fun append(list: List<MediaWrapper>) {
        if (!hasCurrentMedia()) {
            load(list, 0)
            return
        }
        for (media in list) mediaList.add(media)
    }

    /**
     * Insert into the current existing playlist
     */

    fun insertNext(list: List<MediaWrapper>) {
        if (!hasCurrentMedia()) {
            load(list, 0)
            return
        }

        val startIndex = currentIndex + 1

        for (i in list.indices) {
            val mediaWrapper = list[i]
            mediaList.insert(startIndex + i, mediaWrapper)
        }
    }

    fun insertNext(media: MediaWrapper) {
        val arrayList = ArrayList<MediaWrapper>()
        arrayList.add(media)
        insertNext(arrayList)
    }

    /**
     * Move an item inside the playlist.
     */
    fun moveItem(positionStart: Int, positionEnd: Int) {
        mediaList.move(positionStart, positionEnd)
        determinePrevAndNextIndices()
    }

    fun insertItem(position: Int, mw: MediaWrapper) {
        mediaList.insert(position, mw)
        determinePrevAndNextIndices()
    }


    fun remove(position: Int) {
        mediaList.remove(position)
        determinePrevAndNextIndices()
    }

    fun removeLocation(location: String) {
        mediaList.remove(location)
        determinePrevAndNextIndices()
    }

    fun getMediaListSize()= mediaList.size()

    fun getMediaList()= mediaList.all

    override fun onEvent(event: Media.Event) {
        var update = true
        when (event.type) {
            Media.Event.MetaChanged -> {
                /* Update Meta if file is already parsed */
                if (parsed && player.updateCurrentMeta(event.metaId, getCurrentMedia())) service.executeUpdate()
                if (BuildConfig.DEBUG) Log.i(MediaDatabase.TAG, "Media.Event.MetaChanged: " + event.metaId)
            }
            Media.Event.ParsedChanged -> {
                if (BuildConfig.DEBUG) Log.i(MediaDatabase.TAG, "Media.Event.ParsedChanged")
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

    private val mediaplayerEventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                medialibrary.pauseBackgroundOperations()
                videoBackground = false
                val mw = medialibrary.findMedia(getCurrentMedia())
                if (newMedia) {
                    seekToResume(mw)
                    loadMediaMeta(mw)
                    if (mw.type == MediaWrapper.TYPE_STREAM) medialibrary.addToHistory(mw.location, mw.title)
                }
            }
            MediaPlayer.Event.Paused -> medialibrary.resumeBackgroundOperations()
            MediaPlayer.Event.Stopped -> {
                medialibrary.resumeBackgroundOperations()
                currentIndex = -1
                mediaList.clear()
            }
            MediaPlayer.Event.EndReached -> {
                saveMediaMeta()
                if (isBenchmark) player.setPreviousStats()
                determinePrevAndNextIndices(true)
                if (nextIndex == -1) savePosition(true)
                next()
            }
            MediaPlayer.Event.EncounteredError -> next()
        }
        service.onMediaPlayerEvent(event)
    }
}