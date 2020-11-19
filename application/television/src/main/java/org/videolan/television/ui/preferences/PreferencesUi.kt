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

package org.videolan.television.ui.preferences

import android.annotation.TargetApi
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.resources.AndroidDevices
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesUi : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() = R.xml.preferences_ui

    override fun getTitleId() = R.string.interface_prefs_screen

    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.getInstance(activity).run {
            if (!contains(FORCE_PLAY_ALL)) putSingle(FORCE_PLAY_ALL, true)
        }
        super.onCreate(savedInstanceState)
        findPreference<Preference>("ui_audio_category")?.isVisible = false
        findPreference<Preference>(PREF_TV_UI)?.isVisible = AndroidDevices.hasTsp
        findPreference<Preference>(KEY_APP_THEME)?.isVisible = false
        findPreference<Preference>(LIST_TITLE_ELLIPSIZE)?.isVisible = false
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
            "set_locale" -> {
                (activity as PreferencesActivity).setRestartApp()
                UiTools.restartDialog(activity)
            }
            PREF_TV_UI -> {
                Settings.tvUI = sharedPreferences.getBoolean(PREF_TV_UI, false)
                (activity as PreferencesActivity).setRestartApp()
            }
            "browser_show_all_files" -> (activity as PreferencesActivity).setRestart()
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null) return false
        when (preference.key) {
            SHOW_VIDEO_THUMBNAILS -> {
                Settings.showVideoThumbs = (preference as TwoStatePreference).isChecked
                (activity as PreferencesActivity).setRestart()
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun prepareLocaleList() {
        val localePair = LocaleUtils.getLocalesUsedInProject(activity, BuildConfig.TRANSLATION_ARRAY, getString(R.string.device_default))
        val lp = findPreference<ListPreference>("set_locale")
        lp?.entries = localePair.localeEntries
        lp?.entryValues = localePair.localeEntryValues
    }
}
