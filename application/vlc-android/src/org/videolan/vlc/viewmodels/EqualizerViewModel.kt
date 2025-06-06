/*
 * ************************************************************************
 *  DisplaySettingsViewModel.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels

import androidx.lifecycle.ViewModel
import org.videolan.libvlc.MediaPlayer
import org.videolan.vlc.PlaybackService

/**
 * View model storing data for the equalizer dialog
 *
 */
class EqualizerViewModel : ViewModel() {
    private val history = ArrayList<MediaPlayer.Equalizer>()
    var bandCount = -1
    var lastSaveToHistoryFrom = -2


    fun setEqualizer(equalizer: MediaPlayer.Equalizer?) {
        PlaybackService.equalizer.value = equalizer
    }

    /**
     * Save the current equalizer in history
     *
     */
    fun saveInHistory(equalizer: MediaPlayer.Equalizer, from: Int) {
        val mediaPlayerEqualizer = MediaPlayer.Equalizer.create().apply {
            preAmp = equalizer.preAmp
            for (i in 0..bandCount) {

                setAmp(i, equalizer.getAmp(i))
            }
        }
        if (from != lastSaveToHistoryFrom)
            history.add(mediaPlayerEqualizer)
        lastSaveToHistoryFrom = from
    }

    /**
     * Get last equalizer from history
     *
     * @return the last equalizer from history
     */
    fun getAndRemoveLastFromHistory(): MediaPlayer.Equalizer? {
        lastSaveToHistoryFrom = -2
        if (history.isEmpty()) return null
        return history.removeAt(history.lastIndex)
    }
}