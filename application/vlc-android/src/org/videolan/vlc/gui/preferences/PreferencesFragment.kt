/*
 * *************************************************************************
 *  PreferencesFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.vlc.util.Permissions
import org.videolan.tools.RESULT_RESTART

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() = R.xml.preferences

    override fun getTitleId() = R.string.preferences

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<Preference>("extensions_category")?.isVisible = BuildConfig.DEBUG
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "directories" -> {
                when {
                    Medialibrary.getInstance().isWorking -> UiTools.snacker(requireActivity(), getString(R.string.settings_ml_block_scan))
                    Permissions.canReadStorage(requireContext()) -> {
                        val activity = requireActivity()
                        val intent = Intent(activity.applicationContext, SecondaryActivity::class.java)
                        intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
                        startActivity(intent)
                        activity.setResult(RESULT_RESTART)
                    }
                    else -> Permissions.showStoragePermissionDialog(requireActivity(), false)
                }
                return true
            }
            "ui_category" -> loadFragment(PreferencesUi())
            "video_category" -> loadFragment(PreferencesVideo())
            "subtitles_category" -> loadFragment(PreferencesSubtitles())
            "audio_category" -> loadFragment(PreferencesAudio())
            "extensions_category" -> loadFragment(PreferencesExtensions())
            "adv_category" -> loadFragment(PreferencesAdvanced())
            "casting_category" -> loadFragment(PreferencesCasting())
            PLAYBACK_HISTORY -> {
                val activity = activity
                activity?.setResult(RESULT_RESTART)
                return true
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        val activity = activity ?: return
        when (key) {
            "video_action_switch" -> if (!AndroidUtil.isOOrLater && findPreference<ListPreference>(key)?.value == "2"
                    && !Permissions.canDrawOverlays(activity))
                Permissions.checkDrawOverlaysPermission(activity)
        }
    }
}
