package org.videolan.vlc

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.util.getFromMl
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.media.MediaSessionBrowser
import org.videolan.vlc.util.VoiceSearchParams
import org.videolan.vlc.util.awaitMedialibraryStarted
import java.util.*
import kotlin.math.min

@Suppress("unused")
private const val TAG = "VLC/MediaSessionCallback"
private const val TEN_SECONDS = 10000L

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal class MediaSessionCallback(private val playbackService: PlaybackService) : MediaSessionCompat.Callback() {
    private var prevActionSeek = false

    override fun onPlay() {
        if (playbackService.hasMedia()) playbackService.play()
        else if (!AndroidDevices.isAndroidTv) PlaybackService.loadLastAudio(playbackService)
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        val keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent? ?: return false
        if (!playbackService.hasMedia()
                && (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            return if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                PlaybackService.loadLastAudio(playbackService)
                true
            } else false
        }
        /**
         * Implement fast forward and rewind behavior by directly handling the previous and next button events.
         * Normally the buttons are triggered on ACTION_DOWN; however, we ignore the ACTION_DOWN event when
         * isAndroidAutoHardKey returns true, and perform the operation on the ACTION_UP event instead. If the previous or
         * next button is held down, a callback occurs with the long press flag set. When a long press is received,
         * invoke the onFastForward() or onRewind() methods, and set the prevActionSeek flag. The ACTION_UP event
         * action is bypassed if the flag is set. The prevActionSeek flag is reset to false for the next invocation.
         */
        if (isAndroidAutoHardKey(keyEvent) && (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT)) {
            when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (playbackService.isSeekable && keyEvent.isLongPress) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> onFastForward()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onRewind()
                        }
                        prevActionSeek = true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (!prevActionSeek) {
                        val enabledActions = playbackService.enabledActions
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> if ((enabledActions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0L) onSkipToNext()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if ((enabledActions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0L) onSkipToPrevious()
                        }
                    }
                    prevActionSeek = false
                }
            }
            return true
        }
        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    /**
     * This function is based on the following KeyEvent captures. This may need to be updated if the behavior changes in the future.
     *
     * KeyEvent from Media Control UI:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_NEXT, scanCode=0, metaState=0, flags=0x0, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     *
     * KeyEvent from Android Auto Steering Wheel Control:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_NEXT, scanCode=0, metaState=0, flags=0x4, repeatCount=0, eventTime=0, downTime=0, deviceId=0, source=0x0, displayId=0}
     *
     * KeyEvent from Android Auto Steering Wheel Control, Holding Switch (Long Press):
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_NEXT, scanCode=0, metaState=0, flags=0x84, repeatCount=1, eventTime=0, downTime=0, deviceId=0, source=0x0, displayId=0}
     */
    @SuppressLint("LongLogTag")
    private fun isAndroidAutoHardKey(keyEvent: KeyEvent): Boolean {
        val carMode = AndroidDevices.isCarMode(playbackService.applicationContext)
        if (carMode) Log.i(TAG, "Android Auto Key Press: $keyEvent")
        return carMode && keyEvent.deviceId == 0 && (keyEvent.flags and KeyEvent.FLAG_KEEP_TOUCH_MODE != 0)
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        when (action) {
            "shuffle" -> playbackService.shuffle()
            "repeat" -> playbackService.repeatType = when (playbackService.repeatType) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                PlaybackStateCompat.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_NONE
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            }
        }
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        playbackService.lifecycleScope.launch {
            val context = playbackService.applicationContext
            when {
                mediaId == MediaSessionBrowser.ID_NO_MEDIA -> playbackService.displayPlaybackError(R.string.search_no_result)
                mediaId == MediaSessionBrowser.ID_NO_PLAYLIST -> playbackService.displayPlaybackError(R.string.noplaylist)
                mediaId == MediaSessionBrowser.ID_SHUFFLE_ALL -> {
                    val tracks = context.getFromMl { audio }
                    if (tracks.isNotEmpty() && isActive) {
                        loadMedia(tracks.toList(), Random().nextInt(min(tracks.size, MEDIALIBRARY_PAGE_SIZE)))
                        if (!playbackService.isShuffling) playbackService.shuffle()
                    } else {
                        playbackService.displayPlaybackError(R.string.search_no_result)
                    }
                }
                mediaId == MediaSessionBrowser.ID_LAST_ADDED -> {
                    val tracks = context.getFromMl { recentAudio?.toList() }
                    if (!tracks.isNullOrEmpty() && isActive) {
                        val mediaList = tracks.subList(0, tracks.size.coerceAtMost(MediaSessionBrowser.MAX_HISTORY_SIZE))
                        loadMedia(mediaList)
                    }
                }
                mediaId == MediaSessionBrowser.ID_HISTORY -> {
                    val tracks = context.getFromMl { lastMediaPlayed()?.toList()?.filter { MediaSessionBrowser.isMediaAudio(it) } }
                    if (!tracks.isNullOrEmpty() && isActive) {
                        val mediaList = tracks.subList(0, tracks.size.coerceAtMost(MediaSessionBrowser.MAX_HISTORY_SIZE))
                        loadMedia(mediaList)
                    }
                }
                mediaId.startsWith(MediaSessionBrowser.ALBUM_PREFIX) -> {
                    val tracks = context.getFromMl { getAlbum(mediaId.extractId())?.tracks }
                    if (isActive) tracks?.let { loadMedia(it.toList()) }
                }
                mediaId.startsWith(MediaSessionBrowser.ARTIST_PREFIX) -> {
                    val tracks = context.getFromMl { getArtist(mediaId.extractId())?.tracks }
                    if (isActive) tracks?.let { loadMedia(it.toList()) }
                }
                mediaId.startsWith(MediaSessionBrowser.GENRE_PREFIX) -> {
                    val tracks = context.getFromMl { getGenre(mediaId.extractId())?.tracks }
                    if (isActive) tracks?.let { loadMedia(it.toList()) }
                }
                mediaId.startsWith(MediaSessionBrowser.PLAYLIST_PREFIX) -> {
                    val tracks = context.getFromMl { getPlaylist(mediaId.extractId())?.tracks }
                    if (isActive) tracks?.let { loadMedia(it.toList()) }
                }
                mediaId.startsWith(ExtensionsManager.EXTENSION_PREFIX) -> {
                    val id = mediaId.replace(ExtensionsManager.EXTENSION_PREFIX + "_" + mediaId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1] + "_", "")
                    onPlayFromUri(id.toUri(), null)
                }
                else -> try {
                    context.getFromMl { getMedia(mediaId.toLong()) }?.let { if (isActive) loadMedia(listOf(it)) }
                } catch (e: NumberFormatException) {
                    if (isActive) playbackService.loadLocation(mediaId)
                }
            }
        }
    }

    private fun loadMedia(mediaList: List<MediaWrapper>?, position: Int = 0) {
        mediaList?.let { mediaList ->
            if (AndroidDevices.isCarMode(playbackService.applicationContext))
                mediaList.forEach { if (it.type == MediaWrapper.TYPE_VIDEO) it.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO) }
            playbackService.load(mediaList, position)
        }
    }

    private fun String.extractId() = split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toLong()

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) = playbackService.loadUri(uri)

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        playbackService.mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_CONNECTING, playbackService.time, 1.0f).build())
        playbackService.lifecycleScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            playbackService.awaitMedialibraryStarted()
            val vsp = VoiceSearchParams(query ?: "", extras)
            var items: Array<out MediaLibraryItem>? = null
            var tracks: Array<MediaWrapper>? = null
            when {
                vsp.isAny -> {
                    items = playbackService.medialibrary.audio.also { if (!playbackService.isShuffling) playbackService.shuffle() }
                }
                vsp.isArtistFocus -> items = playbackService.medialibrary.searchArtist(vsp.artist)
                vsp.isAlbumFocus -> items = playbackService.medialibrary.searchAlbum(vsp.album)
                vsp.isGenreFocus -> items = playbackService.medialibrary.searchGenre(vsp.genre)
                vsp.isPlaylistFocus -> items = playbackService.medialibrary.searchPlaylist(vsp.playlist)
                vsp.isSongFocus -> tracks = playbackService.medialibrary.searchMedia(vsp.song)
            }
            if (!isActive) return@launch
            if (tracks.isNullOrEmpty() && items.isNullOrEmpty() && query?.length ?: 0 > 2) playbackService.medialibrary.search(query)?.run {
                when {
                    !albums.isNullOrEmpty() -> tracks = albums!![0].tracks
                    !artists.isNullOrEmpty() -> tracks = artists!![0].tracks
                    !playlists.isNullOrEmpty() -> tracks = playlists!![0].tracks
                    !genres.isNullOrEmpty() -> tracks = genres!![0].tracks
                }
            }
            if (!isActive) return@launch
            if (tracks.isNullOrEmpty() && !items.isNullOrEmpty()) tracks = items[0].tracks
            if (!tracks.isNullOrEmpty()) loadMedia(tracks?.toList())
        }
    }

    override fun onPause() = playbackService.pause()

    override fun onStop() = playbackService.stop()

    override fun onSkipToNext() = playbackService.next()

    override fun onSkipToPrevious() = playbackService.previous(false)

    override fun onSeekTo(pos: Long) = playbackService.seek(if (pos < 0) playbackService.time + pos else pos, fromUser = true)

    override fun onFastForward() = playbackService.seek((playbackService.time + TEN_SECONDS).coerceAtMost(playbackService.length), fromUser = true)

    override fun onRewind() = playbackService.seek((playbackService.time - TEN_SECONDS).coerceAtLeast(0), fromUser = true)

    override fun onSkipToQueueItem(id: Long) = playbackService.playIndex(id.toInt())
}