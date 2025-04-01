/*
 * ************************************************************************
 *  PreferenceVisibilityManager.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.children
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.tools.AUDIO_DUCKING
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.RESUME_PLAYBACK
import org.videolan.vlc.VlcMigrationHelper
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager.isPreferenceVisible

object PreferenceVisibilityManager {

    /**
     * Return true if the preference should be visible
     *
     * @param key the preference key
     * @param sharedPreferences the [SharedPreferences] that is used for some conditions
     * @param forTv true it has been called from the TV UI
     */
    fun isPreferenceVisible(key:String?, sharedPreferences: SharedPreferences, forTv: Boolean = false) = when (key) {
        AUDIO_DUCKING -> !AndroidUtil.isOOrLater
        RESUME_PLAYBACK -> AndroidDevices.isPhone && !forTv
        KEY_AOUT -> VlcMigrationHelper.getAudioOutputFromDevice() == VlcMigrationHelper.AudioOutput.ALL
        "audio_digital_output" -> sharedPreferences.getString("aout", "0") != "2"
        "enable_headset_detection", "enable_play_on_headset_insertion", "ignore_headset_media_button_presses", "headset_prefs_category" -> !forTv
        else -> true
    }

    /**
     * Manage preferences visibility by applying [isPreferenceVisible] on each item of the [preferenceScreen]
     *
     * @param settings the settings to pass to [isPreferenceVisible]
     * @param preferenceScreen the [PreferenceScreen] to loop into
     * @param forTv true it has been called from the TV UI
     */
    fun manageVisibility(settings: SharedPreferences, preferenceScreen: PreferenceScreen, forTv: Boolean) {
        preferenceScreen.children.forEach {
            it.isVisible = isPreferenceVisible(it.key, settings, forTv)
            if (it is PreferenceCategory) {
                it.children.forEach { child ->
                    child.isVisible = isPreferenceVisible(child.key, settings, forTv)
                }
            }

        }
    }
}