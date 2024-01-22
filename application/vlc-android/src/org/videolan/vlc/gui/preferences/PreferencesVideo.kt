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

package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.VLCInstance
import org.videolan.tools.KEY_PLAYBACK_RATE_VIDEO
import org.videolan.tools.KEY_PLAYBACK_SPEED_PERSIST_VIDEO
import org.videolan.tools.POPUP_FORCE_LEGACY
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.util.Permissions

class PreferencesVideo : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener  {

    override fun getXml() = R.xml.preferences_video

    override fun getTitleId() = R.string.video_prefs_category

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference<Preference>(POPUP_FORCE_LEGACY)?.isVisible = AndroidUtil.isOOrLater
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
        when (key) {
            "preferred_resolution" -> {
                lifecycleScope.launch {
                    VLCInstance.restart()
                    restartMediaPlayer()
                }
            }
            POPUP_FORCE_LEGACY -> {
                if (sharedPreferences.getBoolean(key, false) && !Permissions.canDrawOverlays(requireActivity())) Permissions.checkDrawOverlaysPermission(requireActivity())
                if (!sharedPreferences.getBoolean(key, false) && !Permissions.isPiPAllowed(requireActivity())) Permissions.checkPiPPermission(requireActivity())
            }
            KEY_PLAYBACK_SPEED_PERSIST_VIDEO -> sharedPreferences.putSingle(KEY_PLAYBACK_RATE_VIDEO, 1.0f)
        }
    }
}
