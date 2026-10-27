/*
 * ************************************************************************
 *  PlaylistLoader.kt
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

package org.videolan.vlc.media.persistence

import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AppContextProvider
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.tools.AUDIO_STOP_AFTER
import org.videolan.tools.KEY_AUDIO_FORCE_SHUFFLE
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.media.PlaybackServiceCallback
import org.videolan.vlc.media.PlayerController
import org.videolan.vlc.media.PlaylistOrchestrator
import org.videolan.vlc.media.queue.PlayQueue
import org.videolan.vlc.util.updateWithMLMeta
import java.util.Calendar

/**
 * Handles playlist loading operations, media list initialization, and sleep timer defaults.
 */
class PlaylistLoader(
    private val playlistManager: PlaylistOrchestrator,
    private val player: PlayerController,
    private val playQueue: PlayQueue,
    private val persistenceManager: PlaybackPersistenceManager,
    private val service: PlaybackServiceCallback,
    private val settings: SharedPreferences
) {

    @MainThread
    fun loadLocations(mediaPathList: List<String>, position: Int) {
        persistenceManager.loadLocations(mediaPathList, position, playlistManager, service) { list, pos ->
            load(list, pos)
        }
    }

    @MainThread
    suspend fun load(
        list: List<MediaWrapper>,
        position: Int,
        mlUpdate: Boolean = false,
        avoidErasingStop: Boolean = false
    ) {
        playlistManager.saveMediaList()
        playlistManager.savePosition()
        playQueue.removeEventListener(playlistManager)
        playlistManager.navigator.clearHistory()
        playlistManager.videoBackground = false

        if (BuildConfig.BETA) {
            Log.d("VLC/PlaylistLoader", "load with values: ", Exception("Call stack"))
            list.forEach {
                try {
                    Log.d("VLC/PlaylistLoader", "Media location: ${it.uri}")
                } catch (e: NullPointerException) {
                    Log.d("VLC/PlaylistLoader", "Media crash", e)
                }
            }
        }
        try {
            playQueue.replaceWith(list)
            if (!playQueue.hasMedia()) {
                Log.w("VLC/PlaylistLoader", "Warning: empty media list, nothing to play !")
                return
            }
            playlistManager.currentIndex = if (playQueue.isValidPosition(position)) position else 0
        } finally {
            playQueue.addEventListener(playlistManager)
        }

        val instance = Settings.getInstance(AppContextProvider.appContext)
        if (!avoidErasingStop) instance.putSingle(AUDIO_STOP_AFTER, -1)
        playlistManager.stopAfter = instance.getInt(AUDIO_STOP_AFTER, -1)
        if (playlistManager.stopAfter < position) playlistManager.stopAfter = -1
        playlistManager.clearABRepeat()
        player.setRate(1.0f, false)
        playlistManager.playIndex(playlistManager.currentIndex)
        service.onPlaylistLoaded()

        if (mlUpdate) {
            service.awaitMedialibraryStarted()
            playQueue.replaceWith(withContext(Dispatchers.IO) { playQueue.getMediaList().updateWithMLMeta() })
            val currentMedia: MediaWrapper? = playlistManager.getCurrentMedia()
            if (currentMedia != null) playlistManager.refreshTrackMeta(currentMedia)
            if (BuildConfig.BETA) {
                Log.d("VLC/PlaylistLoader", "load after ml update with values: ")
                playQueue.getMediaList().forEach { Log.d("VLC/PlaylistLoader", "Media location: ${it.uri}") }
            }
            service.onMediaListChanged()
            service.showNotification()
        }

        if (settings.getBoolean(KEY_AUDIO_FORCE_SHUFFLE, false) && playlistManager.getCurrentMedia()?.type == MediaWrapper.TYPE_AUDIO && !playlistManager.shuffling && playlistManager.canShuffle()) {
            playlistManager.shuffle()
        }

        if (settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L) != -1L) {
            service.waitForMediaEnd = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
            service.resetOnInteraction = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
            val sleepTime = Calendar.getInstance()
            sleepTime.timeInMillis += settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
            sleepTime.set(Calendar.SECOND, 0)
            service.setSleepTimer(sleepTime)
        }
    }

    fun loadLastPlaylist(type: Int = PLAYLIST_TYPE_AUDIO): Boolean {
        return persistenceManager.loadLastPlaylist(
            type = type,
            scope = playlistManager,
            player = player,
            onShufflingChanged = { playlistManager.shuffling = it },
            onSavedTimeChanged = { playlistManager.savedTime = it },
            loadFn = { list, pos, mlUpdate, avoidErasingStop ->
                load(list, pos, mlUpdate, avoidErasingStop)
            }
        )
    }
}
