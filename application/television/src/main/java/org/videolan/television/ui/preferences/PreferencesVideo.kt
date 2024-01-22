/*
 * *************************************************************************
 *  PreferencesVideo.java
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

package org.videolan.television.ui.preferences

import android.annotation.TargetApi
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.videolan.resources.VLCInstance
import org.videolan.tools.KEY_PLAYBACK_RATE_VIDEO
import org.videolan.tools.KEY_PLAYBACK_SPEED_PERSIST_VIDEO
import org.videolan.tools.LOCK_USE_SENSOR
import org.videolan.tools.POPUP_FORCE_LEGACY
import org.videolan.tools.SAVE_BRIGHTNESS
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.restartMediaPlayer

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesVideo : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {

    override fun getXml() = R.xml.preferences_video

    override fun getTitleId() = R.string.video_prefs_category

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findPreference<Preference>("secondary_display_category")?.isVisible = false
        findPreference<Preference>("secondary_display_category_summary")?.isVisible = false
        findPreference<Preference>("enable_clone_mode")?.isVisible = false
        findPreference<Preference>(SAVE_BRIGHTNESS)?.isVisible = false
        findPreference<Preference>(POPUP_FORCE_LEGACY)?.isVisible = false
        findPreference<Preference>(LOCK_USE_SENSOR)?.isVisible = false
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            "preferred_resolution" -> {
                launch {
                    VLCInstance.restart()
                    restartMediaPlayer()
                }
            }

            KEY_PLAYBACK_SPEED_PERSIST_VIDEO -> sharedPreferences.putSingle(KEY_PLAYBACK_RATE_VIDEO, 1.0f)
        }
    }
}
