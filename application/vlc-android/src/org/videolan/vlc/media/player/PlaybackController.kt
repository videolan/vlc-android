/*
 * ************************************************************************
 *  PlaybackController.kt
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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.EXIT_PLAYER
import org.videolan.resources.PLAY_FROM_SERVICE
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.tools.AUDIO_STOP_AFTER
import org.videolan.tools.AppScope
import org.videolan.tools.DAV1D_THREAD_NUMBER
import org.videolan.tools.HTTP_USER_AGENT
import org.videolan.tools.Settings
import org.videolan.tools.isAppStarted
import org.videolan.tools.putSingle
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.MediaWrapperList
import org.videolan.vlc.media.PLAYLIST_AUDIO_REPEAT_MODE_KEY
import org.videolan.vlc.media.PLAYLIST_VIDEO_REPEAT_MODE_KEY
import org.videolan.vlc.media.PREVIOUS_LIMIT_DELAY
import org.videolan.vlc.media.PlaybackServiceCallback
import org.videolan.vlc.media.PlayerController
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.PlaylistOrchestrator
import org.videolan.vlc.media.WaitConfirmation
import org.videolan.vlc.media.persistence.ResumeStatus
import org.videolan.vlc.media.queue.PlayQueue
import org.videolan.vlc.media.queue.QueueNavigator
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.FontCache
import org.videolan.vlc.util.setResumeProgram
import org.videolan.vlc.util.updateNextProgramAfterThumbnailGeneration

/**
 * Manages playback lifecycle execution, mode switching, and LibVLC player controls.
 */
class PlaybackController(
    private val playlistManager: PlaylistOrchestrator,
    private val player: PlayerController,
    private val playQueue: PlayQueue,
    private val navigator: QueueNavigator,
    private val eventHandler: PlayerEventHandler,
    private val medialibrary: Medialibrary,
    private val service: PlaybackServiceCallback,
    private val settings: SharedPreferences,
    private val ctx: Context
) {

    private val mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory

    fun play() {
        if (playQueue.hasMedia()) player.play()
    }

    fun pause() {
        if (player.pause()) {
            playlistManager.savePosition()
            playlistManager.launch {
                playlistManager.saveCurrentMedia()
                playlistManager.saveMediaList()
            }
            if (playlistManager.getCurrentMedia()?.isPodcast == true) playlistManager.saveMediaMeta()
        }
    }

    @MainThread
    fun next(force: Boolean = false) {
        playQueue.getMedia(playlistManager.currentIndex)?.let {
            if (it.type == MediaWrapper.TYPE_VIDEO || it.isPodcast) playlistManager.saveMediaMeta()
        }
        val size = playQueue.size
        if (force || navigator.repeating.value != PlaybackStateCompat.REPEAT_MODE_ONE) {
            navigator.pushPrevious(playlistManager.currentIndex)
            playlistManager.currentIndex = navigator.nextIndex
            if (size == 0 || playlistManager.currentIndex < 0 || playlistManager.currentIndex >= size) {
                Log.w("VLC/PlaybackController", "Warning: invalid next index, aborted !")
                stop()
                return
            }
            playlistManager.videoBackground = playlistManager.videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        }
        playlistManager.launch { playIndex(playlistManager.currentIndex) }
    }

    fun restart() {
        val isPlaying = player.isPlaying() && playlistManager.isAudioList()
        stop()
        if (isPlaying) service.loadLastAudio()
    }

    fun stop(systemExit: Boolean = false, video: Boolean = false) {
        playlistManager.clearABRepeat()
        if (playlistManager.stopAfter != -1) Settings.getInstance(AppContextProvider.appContext).putSingle(AUDIO_STOP_AFTER, playlistManager.stopAfter)
        playlistManager.stopAfter = -1
        playlistManager.videoBackground = false
        val currentMedia: MediaWrapper? = playlistManager.getCurrentMedia()
        val job: Job? = if (currentMedia != null) {
            playlistManager.savePosition(video = video)
            playlistManager.launch(start = CoroutineStart.UNDISPATCHED) {
                playlistManager.saveMediaMeta().join()
                if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && video) {
                    setResumeProgram(service.context, currentMedia)
                    updateNextProgramAfterThumbnailGeneration(service.lifecycleOwner, service.context, currentMedia)
                }
            }
        } else null
        service.setSleepTimer(null)
        playQueue.removeEventListener(playlistManager as MediaWrapperList.EventListener)
        navigator.clearHistory()
        playlistManager.currentIndex = -1
        if (systemExit) player.release() else player.restart()
        playQueue.clear()
        PlaylistManager.showAudioPlayer.value = false
        service.onPlaybackStopped(systemExit)
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(EXIT_PLAYER))
        if (systemExit) playlistManager.launch(start = CoroutineStart.UNDISPATCHED) {
            job?.join()
            playlistManager.cancel()
            player.cancel()
        }
        PlaylistManager.playingState.value = false
    }

    @MainThread
    fun previous(force: Boolean) {
        playQueue.getMedia(playlistManager.currentIndex)?.let { if (it.type == MediaWrapper.TYPE_VIDEO) playlistManager.saveMediaMeta() }
        if (navigator.hasPrevious() &&
            ((force || !player.seekable || (player.getCurrentTime() < PREVIOUS_LIMIT_DELAY) || (navigator.lastPrevious != -1L && System.currentTimeMillis() - navigator.lastPrevious < PREVIOUS_LIMIT_DELAY)))) {
            val size = playQueue.size
            playlistManager.currentIndex = navigator.prevIndex
            navigator.popPrevious()
            if (size == 0 || navigator.prevIndex < 0 || playlistManager.currentIndex >= size) {
                Log.w("VLC/PlaybackController", "Warning: invalid previous index, aborted !")
                player.stop()
                return
            }
            playlistManager.launch { playIndex(playlistManager.currentIndex) }
            navigator.lastPrevious = -1L
        } else {
            player.setPosition(0F)
            navigator.lastPrevious = System.currentTimeMillis()
        }
    }

    @MainThread
    fun shuffle() {
        if (navigator.isShuffling) navigator.clearHistory()
        navigator.isShuffling = !navigator.isShuffling
        playlistManager.savePosition()
        playlistManager.launch { playlistManager.determinePrevAndNextIndices() }
    }

    private fun setRepeatTypeFromSettings() {
        AppScope.launch {
            navigator.repeating.emit(
                if (playlistManager.getCurrentMedia()?.type == MediaWrapper.TYPE_VIDEO) {
                    settings.getInt(PLAYLIST_VIDEO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)
                } else {
                    settings.getInt(PLAYLIST_AUDIO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)
                }
            )
        }
    }

    @MainThread
    fun setRepeatType(repeatType: Int) {
        AppScope.launch { navigator.repeating.emit(repeatType) }
        if (playlistManager.getCurrentMedia()?.type == MediaWrapper.TYPE_VIDEO) {
            settings.putSingle(PLAYLIST_VIDEO_REPEAT_MODE_KEY, navigator.repeating.value)
        } else {
            settings.putSingle(PLAYLIST_AUDIO_REPEAT_MODE_KEY, navigator.repeating.value)
        }
        playlistManager.savePosition()
        playlistManager.launch { playlistManager.determinePrevAndNextIndices() }
    }

    fun setRenderer(item: RendererItem?) {
        player.setRenderer(item)
        PlaylistManager.showAudioPlayer.value = PlayerController.playbackState != PlaybackStateCompat.STATE_STOPPED && (item !== null || !player.isVideoPlaying())
    }

    suspend fun playIndex(index: Int, flags: Int = 0, forceResume: Boolean = false, forceRestart: Boolean = false) {
        FontCache.await(service.context) {
            service.showToast(service.getString(R.string.font_cache_building), Toast.LENGTH_LONG)
        }

        playlistManager.videoBackground = playlistManager.videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        if (playQueue.size == 0) {
            Log.w("VLC/PlaybackController", "Warning: empty media list, nothing to play !")
            return
        }
        // A new playback starts: the previous "media ended" marker only exists to protect
        // the end-of-media position of the media that just ended. It must not keep preventing
        // the progress of this media from being saved (e.g. when a shuffle replay selects it again).
        playlistManager.endReachedFor = null
        playlistManager.currentIndex = if (playQueue.isValidPosition(index)) {
            index
        } else {
            Log.w("VLC/PlaybackController", "Warning: index $index out of bounds")
            0
        }

        val mw = playQueue.getMedia(index) ?: return
        val mediaFromMl = medialibrary.getMedia(mw.uri)
        if (mediaFromMl != null) mw.time = mediaFromMl.time

        val isInCustomPiP: Boolean = service.isInPiPMode.value ?: false
        if (mw.type == MediaWrapper.TYPE_VIDEO && !isAppStarted() && !isInCustomPiP) playlistManager.videoBackground = true
        val isVideoPlaying = mw.type == MediaWrapper.TYPE_VIDEO && player.isVideoPlaying()
        setRepeatTypeFromSettings()
        if (!playlistManager.videoBackground && isVideoPlaying) mw.addFlags(MediaWrapper.MEDIA_VIDEO)
        if (playlistManager.videoBackground) mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        if (playlistManager.isBenchmark) mw.addFlags(MediaWrapper.MEDIA_BENCHMARK)
        playlistManager.parsed = false
        player.switchToVideo = false
        if (mw.uri.scheme == "content") withContext(Dispatchers.IO) { MediaUtils.retrieveMediaTitle(mw) }

        if (mw.type != MediaWrapper.TYPE_VIDEO || isVideoPlaying || player.hasRenderer || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)) {
            var uri = withContext(Dispatchers.IO) { FileUtils.getUri(mw.uri) }
            if (uri == null) {
                skipMedia()
                return
            }
            if (!playlistManager.isBenchmark && AndroidDevices.isTv && isVideoPlaying) {
                VideoPlayerActivity.startOpened(ctx, mw.uri, playlistManager.currentIndex)
            }
            val title = mw.getMetaLong(MediaWrapper.META_TITLE)
            if (title > 0) uri = "$uri#$title".toUri()

            val start: Long
            if (isVideoPlaying) {
                start = if (forceRestart || playlistManager.videoResumeStatus == ResumeStatus.NEVER) 0L else playlistManager.getStartTime(mw)
                if (start == 0L && (forceRestart || playlistManager.videoResumeStatus == ResumeStatus.NEVER)) playlistManager.savedTime = 0L
                if (!forceResume && playlistManager.videoResumeStatus == ResumeStatus.ASK && start > 0 && isAppStarted()) {
                    playlistManager.waitForConfirmation.postValue(WaitConfirmation(mw.title, index, flags))
                    return
                }
            } else {
                start = if (forceRestart || playlistManager.audioResumeStatus == ResumeStatus.NEVER) 0L else playlistManager.getStartTime(mw)
                if (start == 0L && (forceRestart || playlistManager.audioResumeStatus == ResumeStatus.NEVER)) playlistManager.savedTime = 0L
                if (!forceResume && playlistManager.audioResumeStatus == ResumeStatus.ASK && start > 0 && isAppStarted()) {
                    val confirmation = WaitConfirmation(mw.title, index, flags)
                    playlistManager.waitForConfirmationAudio.postValue(confirmation)
                    return
                }
            }

            val media = mediaFactory.getFromUri(VLCInstance.getInstance(service.context), uri)
            if (playlistManager.shouldDisableCookieForwarding) {
                playlistManager.shouldDisableCookieForwarding = false
                media.addOption(":no-http-forward-cookies")
            }
            settings.getString(HTTP_USER_AGENT, null)?.let {
                media.addOption(":http-user-agent=$it")
            }
            val dav1dThreadNumber = settings.getString(DAV1D_THREAD_NUMBER, "") ?: ""
            if (dav1dThreadNumber.isNotEmpty() && dav1dThreadNumber.toInt() >= 1) {
                media.addOption(":dav1d-thread-frames=$dav1dThreadNumber")
            }
            media.addOption(":start-time=${start / 1000L}")
            media.addOption(":no-sout-chromecast-video")
            VLCOptions.setMediaOptions(media, ctx, flags or mw.flags, PlaybackService.hasRenderer())

            if (playlistManager.isBenchmark) {
                media.addOption(":no-audio")
                media.addOption(":no-spu")
                if (playlistManager.isHardware) {
                    media.addOption(":codec=mediacodec_ndk,mediacodec_jni,none")
                    playlistManager.isHardware = false
                }
            }
            media.setEventListener(eventHandler)
            player.startPlayback(media, eventHandler, start)
            player.setSlaves(media, mw)
            if (playlistManager.browserAudioActive) player.setVolume(0)
            playlistManager.newMedia = true
            playlistManager.determinePrevAndNextIndices()
            service.onNewPlayback()
        } else {
            if (player.isPlaying()) player.stop()
            VideoPlayerActivity.startOpened(ctx, mw.uri, playlistManager.currentIndex)
        }
    }

    fun skipMedia() {
        if (playlistManager.currentIndex != navigator.nextIndex) next()
        else stop()
    }

    fun onServiceDestroyed() {
        player.release()
    }

    fun setBrowserAudio(enable: Boolean) {
        if (playlistManager.browserAudioActive == enable) return
        playlistManager.browserAudioActive = enable
        if (enable) {
            playlistManager.preBrowserVolume = player.getVolume()
            player.setVolume(0)
        } else {
            player.setVolume(if (playlistManager.preBrowserVolume >= 0) playlistManager.preBrowserVolume else 100)
            playlistManager.preBrowserVolume = -1
        }
    }

    @MainThread
    fun switchToVideo(): Boolean {
        val media: MediaWrapper? = playlistManager.getCurrentMedia()
        if (media === null || media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo()) {
            return false
        }
        val hasRenderer = player.hasRenderer
        playlistManager.videoBackground = false
        PlaylistManager.showAudioPlayer.postValue(false)
        if (player.isVideoPlaying() && !hasRenderer) {
            player.setVideoTrackEnabled(true)
            LocalBroadcastManager.getInstance(service.context).sendBroadcast(
                VideoPlayerActivity.getIntent(
                    PLAY_FROM_SERVICE,
                    media, false, playlistManager.currentIndex
                )
            )
        } else if (!player.switchToVideo) {
            VideoPlayerActivity.startOpened(AppContextProvider.appContext, media.uri, playlistManager.currentIndex)
            if (!hasRenderer) player.switchToVideo = true
        }
        return true
    }

    fun setVideoTrackEnabled(enabled: Boolean) {
        if (!playQueue.hasMedia()) return
        if (enabled) playlistManager.getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_VIDEO)
        else playlistManager.getCurrentMedia()?.removeFlags(MediaWrapper.MEDIA_VIDEO)
        player.setVideoTrackEnabled(enabled)
    }
}
