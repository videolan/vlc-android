/**
 * **************************************************************************
 * MediaSessionCallback.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * ***************************************************************************
 */
package org.videolan.vlc

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.resources.*
import org.videolan.resources.util.getFromMl
import org.videolan.resources.util.parcelable
import org.videolan.tools.Settings
import org.videolan.tools.removeQuery
import org.videolan.tools.retrieveParent
import org.videolan.vlc.gui.helpers.MediaComparators
import org.videolan.vlc.media.MediaSessionBrowser
import org.videolan.vlc.util.PlaybackAction
import org.videolan.vlc.util.VoiceSearchParams
import org.videolan.vlc.util.awaitMedialibraryStarted
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.min

private const val TAG = "VLC/MediaSessionCallback"
private const val ONE_SECOND = 1000L

internal class MediaSessionCallback(private val playbackService: PlaybackService) : MediaSessionCompat.Callback() {
    private var prevActionSeek = false

    override fun onPlay() {
        if (playbackService.hasMedia()) playbackService.play()
        else if (!AndroidDevices.isAndroidTv) PlaybackService.loadLastAudio(playbackService)
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        val keyEvent = mediaButtonEvent.parcelable(Intent.EXTRA_KEY_EVENT) as KeyEvent? ?: return false

        if (playbackService.detectHeadset &&
            playbackService.settings.getBoolean("ignore_headset_media_button_presses", false)) {
            // Wired headset
            if (playbackService.headsetInserted && isWiredHeadsetHardKey(keyEvent)) {
                return true
            }

            // Bluetooth headset
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null &&
                BluetoothAdapter.STATE_CONNECTED == bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) &&
                isBluetoothHeadsetHardKey(keyEvent)) {
                return true
            }
        }

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
                            KeyEvent.KEYCODE_MEDIA_NEXT -> if (enabledActions.contains(PlaybackAction.ACTION_SKIP_TO_NEXT)) onSkipToNext()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (enabledActions.contains(PlaybackAction.ACTION_SKIP_TO_PREVIOUS)) onSkipToPrevious()
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
     * The following two functions are based on the following KeyEvent captures. They may need to be updated if the behavior changes in the future.
     *
     * KeyEvent from Media Control UI:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_PLAY_PAUSE, scanCode=0, metaState=0, flags=0x0, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     *
     * KeyEvent from a wired headset's media button:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_PLAY_PAUSE, scanCode=0, metaState=0, flags=0x40000000, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     *
     * KeyEvent from a Bluetooth earphone:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_PLAY, scanCode=0, metaState=0, flags=0x0, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     */
    private fun isWiredHeadsetHardKey(keyEvent: KeyEvent): Boolean {
        return !(keyEvent.deviceId == -1 && keyEvent.flags == 0x0)
    }

    private fun isBluetoothHeadsetHardKey(keyEvent: KeyEvent): Boolean {
        return keyEvent.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && keyEvent.deviceId == -1 && keyEvent.flags == 0x0
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
        val carMode = playbackService.isCarMode()
        if (carMode) Log.i(TAG, "Android Auto Key Press: $keyEvent")
        return carMode && keyEvent.deviceId == 0 && (keyEvent.flags and KeyEvent.FLAG_KEEP_TOUCH_MODE != 0)
    }

    override fun onCustomAction(actionId: String?, extras: Bundle?) {
        when (actionId) {
            CUSTOM_ACTION_SPEED -> {
                val steps = listOf(0.50f, 0.80f, 1.00f, 1.10f, 1.20f, 1.50f, 2.00f)
                val index = 1 + steps.indexOf(steps.minByOrNull { abs(playbackService.rate - it) })
                playbackService.setRate(steps[index % steps.size], true)
            }
            CUSTOM_ACTION_BOOKMARK -> {
                playbackService.lifecycleScope.launch {
                    val context = playbackService.applicationContext
                    playbackService.currentMediaWrapper?.let {
                        val bookmark = it.addBookmark(playbackService.getTime())
                        val bookmarkName = context.getString(R.string.bookmark_default_name, Tools.millisToString(playbackService.getTime()))
                        bookmark?.setName(bookmarkName)
                        playbackService.displayPlaybackMessage(R.string.saved, bookmarkName)
                    }
                }
            }
            CUSTOM_ACTION_REWIND -> onRewind()
            CUSTOM_ACTION_FAST_FORWARD -> onFastForward()
            CUSTOM_ACTION_SHUFFLE -> if (playbackService.canShuffle()) playbackService.shuffle()
            CUSTOM_ACTION_REPEAT -> playbackService.repeatType = when (playbackService.repeatType) {
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
            try {
                val mediaIdUri = Uri.parse(extras?.getString(EXTRA_RELATIVE_MEDIA_ID) ?: mediaId)
                val position = mediaIdUri.getQueryParameter("i")?.toInt() ?: 0
                val page = mediaIdUri.getQueryParameter("p")
                val pageOffset = page?.toInt()?.times(MediaSessionBrowser.MAX_RESULT_SIZE) ?: 0
                when (mediaIdUri.removeQuery().toString()) {
                    MediaSessionBrowser.ID_NO_MEDIA -> playbackService.displayPlaybackError(R.string.search_no_result)
                    MediaSessionBrowser.ID_NO_PLAYLIST -> playbackService.displayPlaybackError(R.string.noplaylist)
                    MediaSessionBrowser.ID_SHUFFLE_ALL -> {
                        val tracks = context.getFromMl { audio }
                        if (tracks.isNotEmpty() && isActive) {
                            tracks.sortWith(MediaComparators.ANDROID_AUTO)
                            loadMedia(tracks.toList(), SecureRandom().nextInt(min(tracks.size, MEDIALIBRARY_PAGE_SIZE)))
                            if (!playbackService.isShuffling) playbackService.shuffle()
                        } else {
                            playbackService.displayPlaybackError(R.string.search_no_result)
                        }
                    }
                    MediaSessionBrowser.ID_LAST_ADDED -> {
                        val tracks = context.getFromMl { getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, false, false, MediaSessionBrowser.MAX_HISTORY_SIZE, 0) }
                        if (tracks.isNotEmpty() && isActive) {
                            loadMedia(tracks.toList(), position)
                        }
                    }
                    MediaSessionBrowser.ID_HISTORY -> {
                        val tracks = context.getFromMl { history(Medialibrary.HISTORY_TYPE_LOCAL)?.toList()?.filter { MediaSessionBrowser.isMediaAudio(it) } }
                        if (!tracks.isNullOrEmpty() && isActive) {
                            val mediaList = tracks.subList(0, tracks.size.coerceAtMost(MediaSessionBrowser.MAX_HISTORY_SIZE))
                            loadMedia(mediaList, position)
                        }
                    }
                    MediaSessionBrowser.ID_STREAM -> {
                        val tracks = context.getFromMl { history(Medialibrary.HISTORY_TYPE_NETWORK) }
                        if (tracks.isNotEmpty() && isActive) {
                            tracks.sortWith(MediaComparators.ANDROID_AUTO)
                            loadMedia(tracks.toList(), position)
                        }
                    }
                    MediaSessionBrowser.ID_TRACK -> {
                        val tracks = context.getFromMl { audio }
                        if (tracks.isNotEmpty() && isActive) {
                            tracks.sortWith(MediaComparators.ANDROID_AUTO)
                            loadMedia(tracks.toList(), pageOffset + position)
                        }
                    }
                    MediaSessionBrowser.ID_SEARCH -> {
                        val query = mediaIdUri.getQueryParameter("query") ?: ""
                        val tracks = context.getFromMl {
                            search(query, false, false)?.tracks?.toList() ?: emptyList()
                        }
                        if (tracks.isNotEmpty() && isActive) {
                            loadMedia(tracks, position)
                        }
                    }
                    else -> {
                        val id = ContentUris.parseId(mediaIdUri)
                        when (mediaIdUri.retrieveParent().toString()) {
                            MediaSessionBrowser.ID_ALBUM -> {
                                val tracks = context.getFromMl { getAlbum(id)?.tracks }
                                if (isActive) tracks?.let { loadMedia(it.toList(), position) }
                            }
                            MediaSessionBrowser.ID_ARTIST -> {
                                val tracks = context.getFromMl { getArtist(id)?.tracks }
                                if (isActive) tracks?.let { loadMedia(it.toList(), allowRandom = true) }
                            }
                            MediaSessionBrowser.ID_GENRE -> {
                                val tracks = context.getFromMl { getGenre(id)?.albums?.flatMap { it.tracks.toList() } }
                                if (isActive) tracks?.let { loadMedia(it.toList(), allowRandom = true) }
                            }
                            MediaSessionBrowser.ID_PLAYLIST -> {
                                val tracks = context.getFromMl { getPlaylist(id, Settings.includeMissing, false)?.tracks }
                                if (isActive) tracks?.let { loadMedia(it.toList(), allowRandom = true) }
                            }
                            MediaSessionBrowser.ID_MEDIA -> {
                                val tracks = context.getFromMl { getMedia(id)?.tracks }
                                if (isActive) tracks?.let { loadMedia(it.toList()) }
                            }
                            else -> throw IllegalStateException("Failed to load: $mediaId")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not play media: $mediaId", e)
                when {
                    playbackService.hasMedia() -> playbackService.play()
                    else -> playbackService.displayPlaybackError(R.string.search_no_result)
                }
            }
        }
    }

    private fun loadMedia(mediaList: List<MediaWrapper>?, position: Int = 0, allowRandom: Boolean = false) {
        mediaList?.let {
            if (playbackService.isCarMode())
                mediaList.forEach { mw -> mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO) }
            // Pick a random first track if allowRandom is true and shuffle is enabled
            playbackService.load(mediaList, if (allowRandom && playbackService.isShuffling) SecureRandom().nextInt(min(mediaList.size, MEDIALIBRARY_PAGE_SIZE)) else position)
        }
    }

    private fun seek(position: Long) {
        playbackService.seek(position, fromUser = true)
        playbackService.playlistManager.player.updateProgress(position)
    }

    private fun checkForSeekFailure(forward: Boolean) {
        if (playbackService.playlistManager.player.lastPosition == 0.0f && (forward || playbackService.getTime() > 0))
            playbackService.displayPlaybackMessage(R.string.unseekable_stream)
    }

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) = playbackService.loadUri(uri)

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        val playbackState = PlaybackStateCompat.Builder()
                .setActions(playbackService.enabledActions.getCapabilities())
                .setState(PlaybackStateCompat.STATE_CONNECTING, playbackService.getTime(), playbackService.speed)
                .build()
        playbackService.mediaSession.setPlaybackState(playbackState)
        playbackService.lifecycleScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            playbackService.awaitMedialibraryStarted()
            val vsp = VoiceSearchParams(query ?: "", extras)
            var tracks = when {
                vsp.isAny -> playbackService.medialibrary.audio
                vsp.isSongFocus -> playbackService.medialibrary.searchMedia(vsp.song)
                else -> null
            }
            tracks?.sortWith(MediaComparators.ANDROID_AUTO)
            val items = when {
                vsp.isAlbumFocus -> playbackService.medialibrary.searchAlbum(vsp.album)
                vsp.isGenreFocus -> playbackService.medialibrary.searchGenre(vsp.genre)
                vsp.isArtistFocus -> playbackService.medialibrary.searchArtist(vsp.artist)
                vsp.isPlaylistFocus -> playbackService.medialibrary.searchPlaylist(vsp.playlist, Playlist.Type.All, Settings.includeMissing, false)
                else -> null
            }
            if (!isActive) return@launch
            if (tracks.isNullOrEmpty() && items.isNullOrEmpty() && query?.isNotEmpty() == true) {
                playbackService.medialibrary.search(query, Settings.includeMissing, false)?.run {
                    tracks = when {
                        !albums.isNullOrEmpty() -> albums!!.flatMap { it.tracks.toList() }.toTypedArray()
                        !artists.isNullOrEmpty() -> artists!!.flatMap { it.tracks.toList() }.toTypedArray()
                        !playlists.isNullOrEmpty() -> playlists!!.flatMap { it.tracks.toList() }.toTypedArray()
                        !genres.isNullOrEmpty() -> genres!!.flatMap { it.tracks.toList() }.toTypedArray()
                        else -> null
                    }
                }
            }
            if (!isActive) return@launch
            if (tracks.isNullOrEmpty() && !items.isNullOrEmpty()) tracks = items.flatMap { it.tracks.toList() }.toTypedArray()
            playbackService.lifecycleScope.launch(Dispatchers.Main) {
                when {
                    !tracks.isNullOrEmpty() -> {
                        loadMedia(tracks?.toList(), if (vsp.isAny) SecureRandom().nextInt(min(tracks!!.size, MEDIALIBRARY_PAGE_SIZE)) else 0)
                        // Enable shuffle when isAny is true and disable when false
                        if (vsp.isAny == !playbackService.isShuffling) playbackService.shuffle()
                    }
                    playbackService.hasMedia() -> playbackService.play()
                    else -> playbackService.displayPlaybackError(R.string.search_no_result)
                }
            }
        }
    }

    override fun onSetShuffleMode(shuffleMode: Int) {
        playbackService.shuffleType = shuffleMode
    }

    override fun onSetRepeatMode(repeatMode: Int) {
        playbackService.repeatType = repeatMode
    }

    override fun onPause() = playbackService.pause()

    override fun onStop() = playbackService.stop()

    override fun onSkipToNext() = playbackService.next()

    override fun onSkipToPrevious() = playbackService.previous(false)

    override fun onSeekTo(pos: Long) = seek(if (pos < 0) playbackService.getTime() + pos else pos)

    override fun onFastForward() {
        seek((playbackService.getTime() + Settings.audioJumpDelay * ONE_SECOND).coerceAtMost(playbackService.length))
        checkForSeekFailure(forward = true)
    }

    override fun onRewind() {
        seek((playbackService.getTime() - Settings.audioJumpDelay * ONE_SECOND).coerceAtLeast(0))
        checkForSeekFailure(forward = false)
    }

    override fun onSkipToQueueItem(id: Long) = playbackService.playIndexOrLoadLastPlaylist(id.toInt())

    override fun onSetPlaybackSpeed(speed: Float) = playbackService.setRate(speed.coerceIn(0.5f, 2.0f), false)

}