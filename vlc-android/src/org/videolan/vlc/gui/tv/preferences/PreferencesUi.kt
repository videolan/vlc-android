/*
 * *************************************************************************
 *  PreferencesUi.java
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

package org.videolan.vlc.gui.tv.preferences

import android.annotation.TargetApi
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.preference.ListPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.AndroidDevices


@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesUi : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml(): Int {
        return R.xml.preferences_ui
    }

    override fun getTitleId(): Int {
        return R.string.interface_prefs_screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findPreference("enable_clone_mode").isVisible = false
        findPreference("tv_ui").isVisible = AndroidDevices.hasTsp
        findPreference("app_theme").isVisible = false
        findPreference("secondary_display_category").isVisible = false
        findPreference("secondary_display_category_summary").isVisible = false
        findPreference("blurred_cover_background").isVisible = false
        findPreference("resume_playback").isVisible = false
        prepareLocaleList()
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
        when (key) {
            "set_locale" -> UiTools.restartDialog(activity)
            "tv_ui" -> (activity as PreferencesActivity).setRestartApp()
            "browser_show_all_files" -> (activity as PreferencesActivity).setRestart()
        }
    }

    private fun prepareLocaleList() {
        val localePair = UiTools.getLocalesUsedInProject(activity)
        val lp = findPreference("set_locale") as ListPreference
        lp.entries = localePair.localeEntries
        lp.entryValues = localePair.localeEntryValues
    }
}
