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

package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.*


@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PreferencesUi : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() = R.xml.preferences_ui

    override fun getTitleId() = R.string.interface_prefs_screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareLocaleList()
        setupTheme()
    }

    private fun setupTheme() {
        val prefs = preferenceScreen.sharedPreferences
        if (!prefs.contains(KEY_APP_THEME)) {
            var theme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            if (prefs.getBoolean(KEY_DAYNIGHT, false) && !AndroidDevices.canUseSystemNightMode()) {
                theme = AppCompatDelegate.MODE_NIGHT_AUTO
            } else if (prefs.contains(KEY_BLACK_THEME)) {
                theme = if (prefs.getBoolean(KEY_BLACK_THEME, false))
                    AppCompatDelegate.MODE_NIGHT_YES
                else
                    AppCompatDelegate.MODE_NIGHT_NO
            }
            prefs.edit().putString(KEY_APP_THEME, theme.toString()).apply()
        }
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

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null) return false
        when (preference.key) {
            PREF_TV_UI -> {
                Settings.tvUI = (preference as TwoStatePreference).isChecked
                (activity as PreferencesActivity).setRestartApp()
                return true
            }
            FORCE_LIST_PORTRAIT -> {
                (activity as PreferencesActivity).setRestart()
                return true
            }
            SHOW_VIDEO_THUMBNAILS -> {
                Settings.showVideoThumbs = (preference as TwoStatePreference).isChecked
                (activity as PreferencesActivity).setRestart()
                return true
            }
            "media_seen" -> activity!!.setResult(RESULT_UPDATE_SEEN_MEDIA)
            KEY_ARTISTS_SHOW_ALL -> (activity as PreferencesActivity).updateArtists()
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "set_locale" -> UiTools.restartDialog(requireActivity())
            "browser_show_all_files" -> (activity as PreferencesActivity).setRestart()
            KEY_APP_THEME -> (activity as PreferencesActivity).exitAndRescan()
        }
    }

    private fun prepareLocaleList() {
        val localePair = UiTools.getLocalesUsedInProject(requireActivity())
        val lp = findPreference("set_locale") as ListPreference
        lp.entries = localePair.localeEntries
        lp.entryValues = localePair.localeEntryValues
    }
}
