/*
 * *************************************************************************
 *  PreferencesParentalControl.java
 * **************************************************************************
 *  Copyright © 2023 VLC authors and VideoLAN
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
import org.videolan.tools.KEY_VIDEO_JUMP_DELAY
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity

class PreferencesParentalControl : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener  {

    override fun getXml() = R.xml.preferences_parental_control

    override fun getTitleId() = R.string.parental_control

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        (activity as? VideoPlayerActivity)?.onChangedControlSetting(key)
        when (key) {
            KEY_VIDEO_JUMP_DELAY -> {
                Settings.videoJumpDelay = sharedPreferences.getInt(KEY_VIDEO_JUMP_DELAY, 10)
            }
        }
    }
}
