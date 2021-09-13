/*
 * *************************************************************************
 *  PreferencesVideoControls.java
 * **************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PreferencesVideoControls : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener  {

    override fun getXml() = R.xml.preferences_video_controls

    override fun getTitleId() = R.string.controls_prefs_category

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference<Preference>(POPUP_KEEPSCREEN)?.isVisible = !AndroidUtil.isOOrLater
        findPreference<Preference>(AUDIO_BOOST)?.isVisible = !AndroidDevices.isAndroidTv
        findPreference<Preference>(ENABLE_DOUBLE_TAP_SEEK)?.isVisible = !AndroidDevices.isAndroidTv
        findPreference<Preference>(ENABLE_VOLUME_GESTURE)?.isVisible = AndroidDevices.hasTsp
        findPreference<Preference>(ENABLE_BRIGHTNESS_GESTURE)?.isVisible = AndroidDevices.hasTsp
        findPreference<Preference>(POPUP_KEEPSCREEN)?.isVisible = !AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater
        findPreference<Preference>(KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY)?.title = getString(if (AndroidDevices.isAndroidTv) R.string.video_key_jump_delay else R.string.video_double_tap_jump_delay)

    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        (activity as? VideoPlayerActivity)?.onChangedControlSetting(key)
        when (key) {
            VIDEO_HUD_TIMEOUT -> {
                Settings.videoHudDelay = sharedPreferences.getString(VIDEO_HUD_TIMEOUT, "2")?.toInt() ?: 2
            }
            KEY_VIDEO_JUMP_DELAY -> {
                Settings.videoJumpDelay = sharedPreferences.getInt(KEY_VIDEO_JUMP_DELAY, 10)
            }
            KEY_VIDEO_LONG_JUMP_DELAY -> {
                Settings.videoLongJumpDelay = sharedPreferences.getInt(KEY_VIDEO_LONG_JUMP_DELAY, 20)
            }
            KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY -> {
                Settings.videoDoubleTapJumpDelay = sharedPreferences.getInt(KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY, 20)
            }
        }
    }
}
