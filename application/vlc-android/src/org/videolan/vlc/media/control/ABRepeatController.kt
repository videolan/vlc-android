/*
 * ************************************************************************
 *  ABRepeatController.kt
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

package org.videolan.vlc.media.control

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.vlc.media.ABRepeat
import org.videolan.vlc.media.DelayValues
import org.videolan.vlc.media.WaitConfirmation

/**
 * Manages A-B repeat markers, track delays, and confirmation states.
 */
class ABRepeatController(
    private val settings: SharedPreferences
) {

    val abRepeat by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<ABRepeat>().apply { value = ABRepeat() } }
    val abRepeatOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val videoStatsOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val delayValue by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<DelayValues>().apply { value = DelayValues() } }
    val waitForConfirmation by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<WaitConfirmation?>().apply { value = null } }
    val waitForConfirmationAudio by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<WaitConfirmation?>().apply { value = null } }

    fun setABRepeatValue(media: MediaWrapper?, time: Long) {
        val value = abRepeat.value ?: ABRepeat()
        when {
            value.start == -1L -> {
                value.start = time
            }
            value.start > time && time > -1 -> {
                value.stop = value.start
                value.start = time
            }
            else -> {
                value.stop = time
            }
        }
        if (settings.getBoolean(PLAYBACK_HISTORY, true)) {
            media?.setLongMeta(MediaWrapper.META_AB_REPEAT_START, value.start)
            media?.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, value.stop)
        }
        abRepeat.value = value
    }

    fun setDelayValue(time: Long, start: Boolean) {
        val value = delayValue.value ?: DelayValues()
        if (start) value.start = time else value.stop = time
        delayValue.value = value
    }

    fun resetDelayValues() {
        delayValue.postValue(DelayValues())
    }

    fun resetABRepeatValues(media: MediaWrapper?) {
        abRepeat.value = ABRepeat()
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_START, 0L)
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, 0L)
    }

    fun toggleABRepeat() {
        abRepeatOn.value = !(abRepeatOn.value ?: false)
        if (abRepeatOn.value == false) {
            abRepeat.value = ABRepeat()
        }
    }

    fun toggleStats() {
        videoStatsOn.value = !(videoStatsOn.value ?: false)
    }

    fun clearABRepeat() {
        abRepeat.value = abRepeat.value?.apply {
            start = -1L
            stop = -1L
        }
        abRepeatOn.value = false
    }
}
