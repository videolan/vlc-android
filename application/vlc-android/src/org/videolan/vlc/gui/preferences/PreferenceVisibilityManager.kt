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
import android.os.Build
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.children
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.tools.AUDIO_DUCKING
import org.videolan.tools.BROWSER_SHOW_HIDDEN_FILES
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_APP_THEME
import org.videolan.tools.KEY_QUICK_PLAY
import org.videolan.tools.KEY_QUICK_PLAY_DEFAULT
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.LIST_TITLE_ELLIPSIZE
import org.videolan.tools.LOCKSCREEN_COVER
import org.videolan.tools.PLAYLIST_MODE_AUDIO
import org.videolan.tools.PLAYLIST_MODE_VIDEO
import org.videolan.tools.POPUP_FORCE_LEGACY
import org.videolan.tools.RESUME_PLAYBACK
import org.videolan.tools.SAVE_BRIGHTNESS
import org.videolan.tools.SCREEN_ORIENTATION
import org.videolan.tools.SHOW_SEEK_IN_COMPACT_NOTIFICATION
import org.videolan.tools.TV_FOLDERS_FIRST
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.VlcMigrationHelper
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager.isPreferenceVisible
import org.videolan.vlc.util.FeatureFlag

object PreferenceVisibilityManager {

    /**
     * Return true if the preference should be visible
     *
     * @param key the preference key
     * @param sharedPreferences the [SharedPreferences] that is used for some conditions
     * @param forTv true it has been called from the TV UI
     */
    fun isPreferenceVisible(key:String?, sharedPreferences: SharedPreferences, forTv: Boolean = false) = when (key) {
        //hidden on TV
        KEY_QUICK_PLAY_DEFAULT, KEY_QUICK_PLAY, "secondary_display_category", "secondary_display_category_summary", "enable_clone_mode", SAVE_BRIGHTNESS,
        KEY_APP_THEME, LIST_TITLE_ELLIPSIZE, "enable_headset_detection", "enable_play_on_headset_insertion", "ignore_headset_media_button_presses",
        "headset_prefs_category", "audio_resume_card", LOCKSCREEN_COVER, SHOW_SEEK_IN_COMPACT_NOTIFICATION,
        "audio_task_removed", "casting_category", "android_auto_category", SCREEN_ORIENTATION, -> !forTv
        //only on TV
        TV_FOLDERS_FIRST, BROWSER_SHOW_HIDDEN_FILES, PLAYLIST_MODE_VIDEO, PLAYLIST_MODE_AUDIO -> forTv
        "show_update" -> !forTv && BuildConfig.DEBUG
        KEY_VIDEO_APP_SWITCH -> !forTv || AndroidDevices.hasPiP
        AUDIO_DUCKING -> !AndroidUtil.isOOrLater
        POPUP_FORCE_LEGACY -> AndroidDevices.pipAllowed
        RESUME_PLAYBACK -> AndroidDevices.isPhone && !forTv
        KEY_AOUT -> VlcMigrationHelper.getAudioOutputFromDevice() == VlcMigrationHelper.AudioOutput.ALL
        "audio_digital_output" -> sharedPreferences.getString("aout", "0") != "2"
        "optional_features" -> FeatureFlag.entries.isNotEmpty()
        "remote_access_category" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
        "permissions_title" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
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