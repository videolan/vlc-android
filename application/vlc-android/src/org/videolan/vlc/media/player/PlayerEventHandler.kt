/*
 * ************************************************************************
 *  PlayerEventHandler.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.media.player

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.tools.KEY_ALWAYS_FAST_SEEK
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaPlayerEventListener
import org.videolan.vlc.media.PlaybackServiceCallback
import org.videolan.vlc.media.PlayerController
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.PlaylistOrchestrator
import org.videolan.vlc.util.setResumeProgram
import org.videolan.vlc.util.updateNextProgramAfterThumbnailGeneration

/**
 * Encapsulates [IMedia.EventListener] and [MediaPlayerEventListener] handling for media player events.
 */
class PlayerEventHandler(
    private val service: PlaybackServiceCallback,
    private val playlistManager: PlaylistOrchestrator,
    private val player: PlayerController,
    private val medialibrary: Medialibrary,
    private val settings: SharedPreferences
) : IMedia.EventListener, MediaPlayerEventListener {

    private var lastTimeMetaSaved = 0L

    override fun onEvent(event: IMedia.Event) {
        var update = true
        when (event.type) {
            IMedia.Event.MetaChanged -> {
                if (playlistManager.parsed && player.updateCurrentMeta(event.metaId, playlistManager.getCurrentMedia())) {
                    service.onMediaListChanged()
                }
                if (BuildConfig.DEBUG) Log.i("VLC/PlayerEventHandler", "Media.Event.MetaChanged: ${event.metaId}")
            }
            IMedia.Event.ParsedChanged -> {
                if (BuildConfig.DEBUG) Log.i("VLC/PlayerEventHandler", "Media.Event.ParsedChanged")
                player.updateCurrentMeta(-1, playlistManager.getCurrentMedia())
                playlistManager.parsed = true
            }
            else -> update = false
        }
        if (update) {
            service.onMediaEvent(event)
            if (playlistManager.parsed) {
                service.notifyTrackChanged()
                service.showNotification()
            }
        }
    }

    override suspend fun onEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                val mw: MediaWrapper? = withContext(Dispatchers.IO) {
                    playlistManager.getCurrentMedia()?.let { media ->
                        medialibrary.findMedia(media).apply {
                            if (type == -1) type = media.type
                        }
                    }
                }
                if (mw == null) return
                if (playlistManager.newMedia) {
                    playlistManager.loadMediaMeta(mw)
                    mw.length = player.getLength()
                    playlistManager.saveMediaList()
                    playlistManager.savePosition()
                    playlistManager.saveCurrentMedia()
                    playlistManager.newMedia = false
                    if (player.hasRenderer || (!player.isVideoPlaying() && (mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo()))) {
                        PlaylistManager.showAudioPlayer.value = true
                    }
                    playlistManager.savePlaycount(mw)
                    if (mw.title == mw.fileName || (mw.type == MediaWrapper.TYPE_STREAM && (mw.title != player.mediaplayer.media?.getMeta(IMedia.Meta.Title, true) || mw.artistName != player.mediaplayer.media?.getMeta(IMedia.Meta.Artist, true)))) {
                        refreshTrackMeta(mw)
                    }
                }
                PlaylistManager.playingState.value = true
            }
            MediaPlayer.Event.EndReached -> {
                playlistManager.clearABRepeat()
                playlistManager.getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_FROM_START)
                if (playlistManager.currentIndex != playlistManager.nextIndex) {
                    playlistManager.endReachedFor = playlistManager.getCurrentMedia()?.uri.toString()
                    playlistManager.saveMediaMeta(true)
                    if (playlistManager.isBenchmark) player.setPreviousStats()
                    if (playlistManager.nextIndex == -1) playlistManager.savePosition(reset = true)
                }
                if (playlistManager.stopAfter == playlistManager.currentIndex) {
                    if (BuildConfig.DEBUG) Log.d("AUDIO_STOP_AFTER", "reset")
                    playlistManager.stopAfter = -1
                    playlistManager.stop()
                } else {
                    if (playlistManager.isBenchmark) player.setCurrentStats()
                    playlistManager.determinePrevAndNextIndices(true)
                    if (!playlistManager.hasNext()) playlistManager.getCurrentMedia()?.let {
                        if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && !playlistManager.isAudioList()) {
                            setResumeProgram(service.context, it)
                            updateNextProgramAfterThumbnailGeneration(service.lifecycleOwner, service.context, it)
                        }
                    }
                    playlistManager.next()
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                val location: String? = try {
                    playlistManager.getCurrentMedia()?.location
                } catch (e: NullPointerException) {
                    null
                }
                Log.w("VLC/PlayerEventHandler", "Invalid location $location")

                service.showToast(
                    if (location != null && location.toUri().scheme == "missing") service.getString(R.string.missing_location)
                    else service.getString(R.string.invalid_location, location ?: ""),
                    Toast.LENGTH_SHORT,
                    true
                )
                if (location != null) PlaybackService.lastError.postValue(location)
                if (playlistManager.currentIndex != playlistManager.nextIndex) playlistManager.next() else playlistManager.stop()
            }
            MediaPlayer.Event.TimeChanged -> {
                playlistManager.abRepeat.value?.let {
                    val fastSeek = settings.getBoolean(KEY_ALWAYS_FAST_SEEK, false)
                    if (it.stop != -1L && player.getCurrentTime() > it.stop) service.setTime(it.start, false)
                    if ((fastSeek && player.getCurrentTime() < it.start - 30000L) || (!fastSeek && player.getCurrentTime() < it.start))
                        service.setTime(it.start, false)
                }
                if (player.getCurrentTime() % 10 == 0L) playlistManager.savePosition()
                val now = System.currentTimeMillis()
                if (now - lastTimeMetaSaved > 5000L) {
                    lastTimeMetaSaved = now
                    playlistManager.saveMediaMeta()
                }
            }
            MediaPlayer.Event.SeekableChanged -> {
                if (event.seekable) {
                    playlistManager.getCurrentMedia()?.let { media ->
                        playlistManager.restoreSpeed(media)
                    }
                }
            }
            MediaPlayer.Event.ESSelected -> {
                playlistManager.getCurrentMedia()?.let { media ->
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

    fun refreshTrackMeta(mw: MediaWrapper) {
        mw.updateMeta(player.mediaplayer)
        service.onMediaListChanged()
        service.showNotification()
    }
}
