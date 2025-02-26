/*
 * *************************************************************************
 *  PreferencesAudioControls.kt
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
import androidx.preference.CheckBoxPreference
import org.videolan.tools.KEY_AUDIO_JUMP_DELAY
import org.videolan.tools.KEY_AUDIO_LONG_JUMP_DELAY
import org.videolan.tools.KEY_AUDIO_SHOW_BOOkMARK_BUTTONS
import org.videolan.tools.KEY_AUDIO_SHOW_TRACK_NUMBERS
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity

class PreferencesAudioControls : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener  {

    private lateinit var bookmarkMarkersPreference: CheckBoxPreference

    override fun getXml() = R.xml.preferences_audio_controls

    override fun getTitleId() = R.string.controls_prefs_category

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookmarkMarkersPreference = findPreference("audio_show_bookmark_markers")!!
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences!!
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        (activity as? VideoPlayerActivity)?.onChangedControlSetting(key)
        when (key) {
            KEY_AUDIO_JUMP_DELAY -> {
                Settings.audioJumpDelay = sharedPreferences.getInt(KEY_AUDIO_JUMP_DELAY, 10)
            }
            KEY_AUDIO_LONG_JUMP_DELAY -> {
                Settings.audioLongJumpDelay = sharedPreferences.getInt(KEY_AUDIO_LONG_JUMP_DELAY, 20)
            }
            KEY_AUDIO_SHOW_TRACK_NUMBERS -> {
                Settings.audioShowTrackNumbers.postValue(sharedPreferences.getBoolean(KEY_AUDIO_SHOW_TRACK_NUMBERS, false))
            }
            KEY_AUDIO_SHOW_BOOkMARK_BUTTONS -> {
                if (!sharedPreferences.getBoolean(KEY_AUDIO_SHOW_BOOkMARK_BUTTONS, true)) {
                    bookmarkMarkersPreference.isChecked = false
                }
            }
        }
        Settings.onAudioControlsChanged()
    }
}
