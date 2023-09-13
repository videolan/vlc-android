/*
 * ************************************************************************
 *  VLCAudioFocusHelper.kt
 * *************************************************************************
 *  Copyright © 2018 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */
package org.videolan.vlc.util

import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.tools.AUDIO_DUCKING
import org.videolan.tools.RESUME_PLAYBACK
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService

private const val TAG = "VLCAudioFocusHelper"

@Suppress("DEPRECATION")
class VLCAudioFocusHelper(private val service: PlaybackService) {

    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var hasAudioFocus = false
    @Volatile
    private var podcastPlaying = false
    @Volatile
    internal var lossTransient = false

    private val audioFocusListener = createOnAudioFocusChangeListener()

    internal fun changeAudioFocus(acquire: Boolean) {
        if (!this::audioManager.isInitialized) audioManager = service.getSystemService() ?: return

        if (acquire && !service.hasRenderer()) {
            podcastPlaying = service.isPodcastPlaying
            if (!hasAudioFocus || podcastPlaying) {
                val result = requestAudioFocus()
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioManager.setParameters("bgm_state=true")
                    hasAudioFocus = true
                }
            }
        } else if (hasAudioFocus) {
            abandonAudioFocus()
            audioManager.setParameters("bgm_state=false")
            hasAudioFocus = false
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun abandonAudioFocus() = if (AndroidUtil.isOOrLater) {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    } else {
        audioManager.abandonAudioFocus(audioFocusListener)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus() = if (AndroidUtil.isOOrLater) {
        val attributes = AudioAttributes.Builder()
                .setContentType(if (service.isVideoPlaying) AudioAttributes.CONTENT_TYPE_MOVIE else AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(podcastPlaying)
                .setAudioAttributes(attributes)
                .build()
        audioManager.requestAudioFocus(audioFocusRequest)
    } else {
        audioManager.requestAudioFocus(audioFocusListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    private fun createOnAudioFocusChangeListener(): AudioManager.OnAudioFocusChangeListener {
        return object : AudioManager.OnAudioFocusChangeListener {
            private val handler by lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }
            private var lossTransientVolume = -1
            private var lossTime: Long = 0
            private var wasPlaying = false

            override fun onAudioFocusChange(focusChange: Int) {
                /*
             * Pause playback during alerts and notifications
             */
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_LOSS")
                        // Pause playback
                        changeAudioFocus(false)
                        service.pause()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT")
                        // Pause playback
                        pausePlayback()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                        // Lower the volume
                        if (service.isPlaying) {
                            if (AndroidDevices.isAmazon || podcastPlaying) {
                                pausePlayback()
                            } else if (service.settings.getBoolean(AUDIO_DUCKING, true)) {
                                val volume = service.volume
                                lossTransientVolume = volume
                                service.setVolume(volume / 3)
                            }
                        }
                    }

                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_GAIN: ")
                        // Resume playback
                        if (lossTransientVolume != -1) {
                            service.setVolume(lossTransientVolume)
                            lossTransientVolume = -1
                        }

                        if (lossTransient && wasPlaying) {
                            if (podcastPlaying) {
                                // If the interruption was short just restart playback
                                val deltaTime = System.currentTimeMillis() - lossTime
                                if (deltaTime > 1_000L) schedulePlayback(2_000L) else resumePlayback()
                            } else if (service.settings.getBoolean(RESUME_PLAYBACK, true)) {
                                resumePlayback()
                            }
                        }
                    }
                }
            }

            private fun pausePlayback() {
                if (lossTransient) return
                lossTransient = true
                wasPlaying = service.isPlaying
                if (wasPlaying) {
                    service.pause()
                    lossTime = System.currentTimeMillis()
                }
            }

            private fun schedulePlayback(delay: Long) {
                handler.removeCallbacks(delayedPodcastRunnable)
                handler.postDelayed(delayedPodcastRunnable, delay)
            }

            private val delayedPodcastRunnable = Runnable {
                val position = (service.getTime() - 5_000L).coerceAtLeast(0)
                resumePlayback()
                service.seek(position, fromUser = true)
                service.playlistManager.player.updateProgress(position)
            }

            private fun resumePlayback() {
                service.play()
                lossTransient = false
            }
        }
    }
}