/*
 * ************************************************************************
 *  PlayerKeyListenerDelegate.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.helpers

import android.view.KeyEvent

/**
 * Delegate managing the key shortcuts for the players.
 * This class routes the key events to the player actions
 */
class PlayerKeyListenerDelegate(private val keycodeListener: KeycodeListener) {
    /**
     * Launches a player action depending on a [KeyEvent]
     *
     * @param keyCode: the keycode from the event
     * @param event: the key event with modifiers etc
     *
     * @return true if the event was consumed, false otherwise
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent):Boolean {
        if (!keycodeListener.isReady()) return false
        return when (keyCode) {

            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_MENU -> {
                keycodeListener.showAdvancedOptions()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                keycodeListener.togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_CHANNEL_UP -> {
                keycodeListener.next()
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                keycodeListener.seek(10000)
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                keycodeListener.seek(-10000)
                true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                keycodeListener.seek(60000)
                true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                keycodeListener.seek(-60000)
                true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_MEDIA_STOP -> {
                keycodeListener.stop()
                true
            }
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                keycodeListener.previous()
                true
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    keycodeListener.showEqualizer()
                }
                true
            }
            KeyEvent.KEYCODE_PLUS -> {
                keycodeListener.increaseRate()
                true
            }
            KeyEvent.KEYCODE_EQUALS -> {
                if (event.isShiftPressed) {
                    keycodeListener.increaseRate()
                } else keycodeListener.resetRate()
                true
            }
            KeyEvent.KEYCODE_MINUS -> {
                keycodeListener.decreaseRate()
                true
            }
            KeyEvent.KEYCODE_B -> {
                keycodeListener.bookmark()
                true
            }
            else -> false
        }
    }
}

/**
 * Interface describing the methods that can be triggered by key events
 */
interface KeycodeListener {

    /**
     * Get the readiness state of the callee. I not ready, no event will be triggered
     * @return true if the callee is ready
     */
    fun isReady(): Boolean

    /**
     * Opens the advanced options menu
     */
    fun showAdvancedOptions()

    /**
     * Switch between the playing and pause states of the player
     */
    fun togglePlayPause()

    /**
     * Go to the next track in the playqueue
     */
    fun next()

    /**
     * Go to the previous track in the playqueue
     */
    fun previous()

    /**
     * Stop the playback
     */
    fun stop()

    /**
     * Show the equalizer dialog
     */
    fun showEqualizer()

    /**
     * Seek to a position in a track depending on the current time
     * @param delta: the time difference that has to be added in ms
     */
    fun seek(delta: Int)

    /**
     * Increase the playback speed rate
     */
    fun increaseRate()

    /**
     * Decrease the playback speed rate
     */
    fun decreaseRate()

    /**
     * Reset the playback speed rate
     */
    fun resetRate()

    /**
     * Add a new bookmark at current time
     */
    fun bookmark()
}