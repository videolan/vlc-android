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

package org.videolan.vlc.gui.tv.preferences

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesVideo : BasePreferenceFragment() {

    override fun getXml(): Int {
        return R.xml.preferences_video
    }

    override fun getTitleId(): Int {
        return R.string.video_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findPreference("force_list_portrait").isVisible = false
        findPreference("save_brightness").isVisible = false
        findPreference("enable_double_tap_seek").isVisible = false
        findPreference("enable_volume_gesture").isVisible = AndroidDevices.hasTsp
        findPreference("enable_brightness_gesture").isVisible = AndroidDevices.hasTsp
        findPreference("popup_keepscreen").isVisible = false
        findPreference("popup_force_legacy").isVisible = false
        findPreference("force_play_all").isVisible = false
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null)
            return false
        when (preference.key) {
            "video_min_group_length" -> {
                activity.setResult(PreferencesActivity.RESULT_RESTART)
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}
