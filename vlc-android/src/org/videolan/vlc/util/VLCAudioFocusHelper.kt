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

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService

private const val TAG = "VLCAudioFocusHelper"

class VLCAudioFocusHelper(private val service: PlaybackService) {

    private lateinit var audioManager: AudioManager
    private var hasAudioFocus = false
    @Volatile
    internal var lossTransient = false

    private val audioFocusListener = createOnAudioFocusChangeListener()

    internal fun changeAudioFocus(acquire: Boolean) {
        if (!this::audioManager.isInitialized) audioManager = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        if (acquire && !service.hasRenderer()) {
            if (!hasAudioFocus) {
                val result = audioManager.requestAudioFocus(audioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioManager.setParameters("bgm_state=true")
                    hasAudioFocus = true
                }
            }
        } else if (hasAudioFocus) {
            audioManager.abandonAudioFocus(audioFocusListener)
            audioManager.setParameters("bgm_state=false")
            hasAudioFocus = false
        }
    }

    private fun createOnAudioFocusChangeListener(): AudioManager.OnAudioFocusChangeListener {
        return object : AudioManager.OnAudioFocusChangeListener {
            private var lossTransientVolume = -1
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
                            if (AndroidDevices.isAmazon) {
                                pausePlayback()
                            } else if (service.settings.getBoolean("audio_ducking", true)) {
                                val volume = service.volume
                                lossTransientVolume = volume
                                service.setVolume(volume/3)
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
                        if (lossTransient) {
                            if (wasPlaying && service.settings.getBoolean("resume_playback", true))
                                service.play()
                            lossTransient = false
                        }
                    }
                }
            }

            private fun pausePlayback() {
                if (lossTransient) return
                lossTransient = true
                wasPlaying = service.isPlaying
                if (wasPlaying) service.pause()
            }
        }
    }
}