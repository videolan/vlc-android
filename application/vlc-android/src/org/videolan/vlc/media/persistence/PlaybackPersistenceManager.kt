package org.videolan.vlc.media.persistence

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AppContextProvider
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.resources.util.VLCCrashHandler
import org.videolan.tools.AUDIO_DELAY_GLOBAL
import org.videolan.tools.AUDIO_RESUME_PLAYBACK
import org.videolan.tools.AUDIO_SHUFFLING
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
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.media.ABRepeat
import org.videolan.vlc.media.PlaybackServiceCallback
import org.videolan.vlc.media.PlayerController
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.isSchemeFD
import org.videolan.vlc.util.isSchemeStreaming
import org.videolan.vlc.util.validateLocation
import kotlin.math.max

/**
 * Handles persistent storage of media position, active playlist preferences,
 * audio/SPU delays, play counts, and speed settings.
 */
class PlaybackPersistenceManager(
    private val settings: SharedPreferences,
    private val medialibrary: Medialibrary
) {

    @Volatile
    var loadingLastPlaylist = false

    fun loadLastPlaylist(
        type: Int,
        scope: CoroutineScope,
        player: PlayerController,
        onShufflingChanged: (Boolean) -> Unit,
        onSavedTimeChanged: (Long) -> Unit,
        loadFn: suspend (List<MediaWrapper>, Int, Boolean, Boolean) -> Unit
    ): Boolean {
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
        scope.launch {
            val playList = withContext(Dispatchers.Default) {
                locations.asSequence().mapTo(ArrayList(locations.size)) {
                    MLServiceLocator.getAbstractMediaWrapper(it.toUri())
                }
            }
            onShufflingChanged(settings.getBoolean(if (audio) AUDIO_SHUFFLING else MEDIA_SHUFFLING, false))
            val position = max(0, settings.getInt(if (audio) POSITION_IN_AUDIO_LIST else POSITION_IN_MEDIA_LIST, 0))
            onSavedTimeChanged(settings.getLong(if (audio) POSITION_IN_SONG else POSITION_IN_MEDIA, -1))
            if (!audio && position < playList.size && settings.getBoolean(VIDEO_PAUSED, false)) {
                playList[position].addFlags(MediaWrapper.MEDIA_PAUSED)
            }
            if (audio && position < playList.size) playList[position].addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            loadFn(playList, position, true, true)
            loadingLastPlaylist = false
            if (!audio) {
                val rate = settings.getFloat(VIDEO_SPEED, player.getRate())
                if (rate != 1.0f) player.setRate(rate, false)
            }
        }
        return true
    }

    @MainThread
    fun loadLocations(
        mediaPathList: List<String>,
        position: Int,
        scope: CoroutineScope,
        service: PlaybackServiceCallback,
        loadFn: suspend (List<MediaWrapper>, Int) -> Unit
    ) {
        if (BuildConfig.BETA) {
            Log.d("VLC/PlaybackPersistMngr", "loadLocations with values: ", Exception("Call stack"))
            mediaPathList.forEach {
                try {
                    Log.d("VLC/PlaybackPersistMngr", "Media location: $it")
                } catch (e: NullPointerException) {
                    Log.d("VLC/PlaybackPersistMngr", "Media crash", e)
                }
            }
        }
        scope.launch {
            val mediaList = ArrayList<MediaWrapper>()
            withContext(Dispatchers.IO) {
                for (location in mediaPathList) {
                    var mediaWrapper = medialibrary.getMedia(location)
                    if (mediaWrapper === null) {
                        if (!location.validateLocation()) {
                            Log.w("VLC/PlaybackPersistMngr", "Invalid location $location")
                            service.showToast(
                                if (location.toUri().scheme == "missing") service.getString(R.string.missing_location)
                                else service.getString(R.string.invalid_location, location as Any),
                                Toast.LENGTH_SHORT
                            )
                            continue
                        }
                        Log.v("VLC/PlaybackPersistMngr", "Creating on-the-fly Media object for $location")
                        mediaWrapper = MLServiceLocator.getAbstractMediaWrapper(location.toUri())
                        if (BuildConfig.BETA) Log.d("VLC/PlaybackPersistMngr", "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
                    } else {
                        if (BuildConfig.BETA) Log.d("VLC/PlaybackPersistMngr", "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
                    }
                }
            }
            loadFn(mediaList, position)
        }
    }

    fun saveMediaMeta(
        scope: CoroutineScope,
        end: Boolean = false,
        currentMedia: MediaWrapper?,
        player: PlayerController,
        entryUrl: String?,
        endReachedFor: String?,
        onSkipMediaUpdateRefresh: () -> Unit
    ): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) outerLaunch@ {
        if (endReachedFor != null && endReachedFor == currentMedia?.uri.toString() && !end) return@outerLaunch
        val titleIdx = player.getTitleIdx()
        val mediaItem = currentMedia ?: return@outerLaunch
        if (mediaItem.uri.scheme.isSchemeFD()) return@outerLaunch
        if (Settings.getInstance(AppContextProvider.appContext).getBoolean(KEY_INCOGNITO, false)) return@outerLaunch

        val time = player.mediaplayer.time
        val length = player.getLength()
        val canSwitchToVideo = player.canSwitchToVideo()

        launch(Dispatchers.IO) innerLaunch@ {
            val media = if (entryUrl != null) medialibrary.getMedia(entryUrl) else medialibrary.findMedia(mediaItem) ?: return@innerLaunch
            if (PlaylistManager.showAudioPlayer.value != true) BaseBrowserFragment.needRefresh.postValue(true)
            if (media.id == 0L) return@innerLaunch
            if (titleIdx > 0) media.setLongMeta(MediaWrapper.META_TITLE, titleIdx.toLong())

            if (time != 0L || player.isPlaying()) {
                if (media.type == MediaWrapper.TYPE_VIDEO || canSwitchToVideo || media.isPodcast) {
                    if (length == 0L) {
                        media.time = -1L
                        media.position = player.lastPosition
                        medialibrary.setLastPosition(media.id, if (end) 1F else media.position)
                    } else {
                        if (media.length <= 0 && length > 0) media.length = length
                        try {
                            when (medialibrary.setLastTime(media.id, if (end) length else time)) {
                                Medialibrary.ML_SET_TIME_ERROR -> {}
                                Medialibrary.ML_SET_TIME_END, Medialibrary.ML_SET_TIME_BEGIN -> media.time = 0
                                Medialibrary.ML_SET_TIME_AS_IS -> {
                                    media.time = time
                                    // the Medialibrary sometimes doesn't have the media's length,
                                    // and won't know that the file has ended. This forces the ML
                                    // to mark the file as finished and reset it's time
                                    // When we are handling the end of the media, the value written
                                    // to the ML is `length`, so the raw `time` may already have been
                                    // reset to 0: only compare it when this is a regular save.
                                    if (end || time == length) {
                                        medialibrary.setLastTime(media.id, 0)
                                        media.seen = 1L
                                        media.playCount += 1
                                        media.time = 0
                                    }
                                }
                            }
                        } catch (e: NullPointerException) {
                            VLCCrashHandler.saveLog(e, "NullPointerException in PlaylistManager saveMediaMeta")
                        }
                    }
                }
            }
            if (media.type != MediaWrapper.TYPE_VIDEO && !canSwitchToVideo && !media.isPodcast) {
                onSkipMediaUpdateRefresh()
            }
        }
    }

    fun loadMediaMeta(
        media: MediaWrapper,
        player: PlayerController,
        abRepeatOn: MutableLiveData<Boolean>,
        abRepeat: MutableLiveData<ABRepeat>
    ) {
        if (player.canSwitchToVideo()) {
            val savedDelay = media.getMetaLong(MediaWrapper.META_AUDIODELAY)
            val globalDelay = Settings.getInstance(AppContextProvider.appContext).getLong(AUDIO_DELAY_GLOBAL, 0L)
            if (savedDelay == 0L && globalDelay != 0L) {
                player.setAudioDelay(globalDelay)
            } else if (settings.getBoolean(KEY_SAVE_INDIVIDUAL_AUDIO_DELAY, true)) {
                player.setAudioDelay(savedDelay)
            }
            val abStart = if (settings.getBoolean(PLAYBACK_HISTORY, true)) media.getMetaLong(MediaWrapper.META_AB_REPEAT_START) else 0L
            if (abStart != 0L) {
                abRepeatOn.value = true
                val abStop = media.getMetaLong(MediaWrapper.META_AB_REPEAT_STOP)
                abRepeat.postValue(ABRepeat(abStart, if (abStop == 0L) -1L else abStop))
            }
            player.setSpuTrack(media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK).toString())
            player.setSpuDelay(media.getMetaLong(MediaWrapper.META_SUBTITLE_DELAY))
        }
    }

    fun setSpuTrack(scope: CoroutineScope, index: String, player: PlayerController, currentMedia: MediaWrapper?) {
        if (!player.setSpuTrack(index)) return
        val media = currentMedia ?: return
        if (media.id != 0L) scope.launch(Dispatchers.IO) { media.setStringMeta(MediaWrapper.META_SUBTITLE_TRACK, index) }
    }

    fun setAudioDelay(scope: CoroutineScope, delay: Long, player: PlayerController, currentMedia: MediaWrapper?) {
        if (!player.setAudioDelay(delay)) return
        val media = currentMedia ?: return
        if (media.id != 0L && settings.getBoolean(KEY_SAVE_INDIVIDUAL_AUDIO_DELAY, true)) {
            scope.launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_AUDIODELAY, player.getAudioDelay()) }
        }
    }

    fun setSpuDelay(scope: CoroutineScope, delay: Long, player: PlayerController, currentMedia: MediaWrapper?) {
        if (!player.setSpuDelay(delay)) return
        val media = currentMedia ?: return
        if (media.id != 0L) scope.launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_SUBTITLE_DELAY, player.getSpuDelay()) }
    }

    fun savePosition(
        hasMedia: Boolean,
        isAudioList: Boolean,
        shuffling: Boolean,
        currentIndex: Int,
        currentTime: Long,
        rate: Float,
        firstMedia: MediaWrapper?,
        reset: Boolean = false,
        video: Boolean = false
    ) {
        if (settings.getBoolean(KEY_INCOGNITO, false)) return
        if (!hasMedia) return
        settings.edit {
            val audio = !video && isAudioList
            putBoolean(if (audio) AUDIO_SHUFFLING else MEDIA_SHUFFLING, shuffling)
            putInt(if (audio) POSITION_IN_AUDIO_LIST else POSITION_IN_MEDIA_LIST, if (reset) 0 else currentIndex)
            putLong(if (audio) POSITION_IN_SONG else POSITION_IN_MEDIA, if (reset) 0L else currentTime)
            if (!audio) {
                putFloat(VIDEO_SPEED, rate)
            }
            if (reset && firstMedia != null) {
                if (audio && settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)) {
                    putString(KEY_CURRENT_AUDIO, firstMedia.location)
                }
            }
        }
    }

    fun restoreSpeed(media: MediaWrapper, isVideoPlaying: Boolean): Float {
        val incognitoMode = settings.getBoolean(KEY_INCOGNITO, false)
        val playbackSpeedModeAll = settings.getBoolean(
            if (isVideoPlaying) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL,
            false
        )
        return when {
            incognitoMode && playbackSpeedModeAll -> settings.getFloat(
                if (isVideoPlaying) KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE,
                1.0f
            )
            playbackSpeedModeAll -> settings.getFloat(
                if (isVideoPlaying) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE,
                1.0f
            )
            incognitoMode -> 1F
            else -> try {
                media.getMetaString(MediaWrapper.META_SPEED).toFloat()
            } catch (e: Exception) {
                1F
            }
        }
    }

    suspend fun savePlaycount(mw: MediaWrapper, canSwitchToVideo: Boolean, entryUrl: String?) {
        if (settings.getBoolean(KEY_INCOGNITO, false)) return
        var currentMedia = mw
        if (settings.getBoolean(PLAYBACK_HISTORY, true) && !mw.uri.scheme.isSchemeFD()) {
            withContext(Dispatchers.IO) {
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
                            if (internalMedia.title != mw.title) internalMedia.rename(mw.title)
                        }
                    }
                }
                if (id != 0L && currentMedia.type != MediaWrapper.TYPE_VIDEO && !canSwitchToVideo && !currentMedia.isPodcast) {
                    currentMedia.markAsPlayed()
                }
            }
        }
    }

    fun saveCurrentMedia(currentMedia: MediaWrapper?, isAudio: Boolean, isVideoPlaying: Boolean) {
        if (settings.getBoolean(KEY_INCOGNITO, false)) return
        if (currentMedia == null) return

        settings.putSingle(KEY_CURRENT_MEDIA_IS_AUDIO, isAudio)

        if (currentMedia.uri.scheme.isSchemeFD()) {
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
            settings.putSingle(KEY_CURRENT_MEDIA_RESUME, currentMedia.location)
            if (settings.getString(KEY_VIDEO_APP_SWITCH, "0") == "1" || !isVideoPlaying) {
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, currentMedia.title ?: "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, currentMedia.artistName ?: "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, currentMedia.artworkURL ?: "")
            }
            settings.putSingle(KEY_CURRENT_MEDIA, currentMedia.location)
        }
        if (isAudio && saveAudioPlayQueue) {
            settings.putSingle(KEY_CURRENT_MEDIA_RESUME, currentMedia.location)
            settings.putSingle(KEY_CURRENT_AUDIO, currentMedia.location)
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, currentMedia.title ?: "")
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, currentMedia.artistName ?: "")
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, currentMedia.artworkURL ?: "")
        }
    }

    suspend fun saveMediaList(currentMedia: MediaWrapper?, mediaList: List<MediaWrapper>, isAudio: Boolean) {
        if (settings.getBoolean(KEY_INCOGNITO, false)) return
        if (currentMedia == null || currentMedia.uri.scheme.isSchemeFD()) return

        withContext(Dispatchers.Default) {
            val list = mediaList.takeIf { it.isNotEmpty() } ?: return@withContext
            val locations = StringBuilder()
            for (mw in list) locations.append(" ").append(mw.uri.toString())
            val formattedLocations = locations.toString().trim()

            if (isAudio) {
                if (settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)) {
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST_RESUME, formattedLocations)
                    settings.putSingle(KEY_AUDIO_LAST_PLAYLIST, formattedLocations)
                }
            } else {
                if (settings.getBoolean(VIDEO_RESUME_PLAYBACK, true)) {
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST_RESUME, formattedLocations)
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST, formattedLocations)
                }
            }
        }
    }

    fun loadResumeStatus(key: String): ResumeStatus {
        val value = settings.getString(key, "0")
        return when (value) {
            "2" -> ResumeStatus.ASK
            "0" -> ResumeStatus.ALWAYS
            else -> ResumeStatus.NEVER
        }
    }
}

enum class ResumeStatus {
    ALWAYS, ASK, NEVER
}
