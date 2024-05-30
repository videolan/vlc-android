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

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.tools.KEY_APP_THEME
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.KEY_BLACK_THEME
import org.videolan.tools.KEY_DAYNIGHT
import org.videolan.tools.KEY_SHOW_HEADERS
import org.videolan.tools.LIST_TITLE_ELLIPSIZE
import org.videolan.tools.LocaleUtils
import org.videolan.tools.PREF_TV_UI
import org.videolan.tools.RESULT_UPDATE_SEEN_MEDIA
import org.videolan.tools.SHOW_VIDEO_THUMBNAILS
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.helpers.UiTools


class PreferencesUi : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: SharedPreferences
    private lateinit var sleepTimerPreference: PreferenceScreen
    override fun getXml() = R.xml.preferences_ui

    override fun getTitleId() = R.string.interface_prefs_screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareLocaleList()
        setupTheme()
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        super.onCreatePreferences(bundle, s)
        val groupVideoPreference = preferenceManager.findPreference<EditTextPreference>("video_group_size")
        groupVideoPreference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
        groupVideoPreference?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            val text = preference.text
            if (text.isNullOrEmpty()) {
                ""
            } else {
                getString(R.string.video_group_size_summary, text)
            }
        }
        settings = Settings.getInstance(requireActivity())
        sleepTimerPreference = findPreference("default_sleep_timer")!!
        manageSleepTimerSummary()
    }

    private fun setupTheme() {
        val prefs = preferenceScreen.sharedPreferences!!
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
            prefs.putSingle(KEY_APP_THEME, theme.toString())
        }
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

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null) return false
        when (preference.key) {
            PREF_TV_UI -> {
                Settings.tvUI = (preference as TwoStatePreference).isChecked
                (activity as PreferencesActivity).setRestartApp()
                return true
            }
            SHOW_VIDEO_THUMBNAILS -> {
                Settings.showVideoThumbs = (preference as TwoStatePreference).isChecked
                (activity as PreferencesActivity).setRestart()
                return true
            }
            KEY_SHOW_HEADERS -> {
                Settings.showHeaders = (preference as TwoStatePreference).isChecked
                (activity as PreferencesActivity).setRestart()
                return true
            }
            "default_sleep_timer" -> {
                val newFragment = SleepTimerDialog.newInstance(true)
                newFragment.onDismissListener  = DialogInterface.OnDismissListener { manageSleepTimerSummary() }
                newFragment.show((activity as FragmentActivity).supportFragmentManager, "time")
            }
            "media_seen" -> requireActivity().setResult(RESULT_UPDATE_SEEN_MEDIA)
            KEY_ARTISTS_SHOW_ALL -> (activity as PreferencesActivity).updateArtists()
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            "set_locale" -> {
                (activity as PreferencesActivity).setRestart()
                UiTools.restartDialog(requireActivity())
            }
            "video_min_group_length" -> (activity as PreferencesActivity).setRestart()
            KEY_APP_THEME -> {
                if (!AppContextProvider.locale.isNullOrEmpty()) UiTools.restartDialog(requireActivity()) else (activity as PreferencesActivity).exitAndRescan()
            }
            LIST_TITLE_ELLIPSIZE -> {
                Settings.listTitleEllipsize = sharedPreferences.getString(LIST_TITLE_ELLIPSIZE, "0")?.toInt() ?: 0
                (activity as PreferencesActivity).setRestart()
            }
            "video_group_size" -> {
                val goupSizeValue = try {
                    Settings.getInstance(requireActivity()).getString(key, "6")?.toInt() ?: 6
                } catch (e: NumberFormatException) {
                    6
                }
                Medialibrary.getInstance().setVideoGroupsPrefixLength(goupSizeValue)
                (activity as PreferencesActivity).setRestart()
            }
            "include_missing" -> {
                Settings.includeMissing = sharedPreferences.getBoolean(key, true)
                (activity as PreferencesActivity).setRestart()
            }
        }
    }

    private fun manageSleepTimerSummary() {
        val interval = settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL,  -1L)
        if (interval == -1L) {
            sleepTimerPreference.summary = getString(R.string.disabled)
            return
        }
        val wait = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT,  false)
        val reset = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION,  false)
        sleepTimerPreference.summary = getString(R.string.default_sleep_timer_summary, Tools.millisToString(interval), if (wait) "true" else "false", if (reset) "true" else "false")
    }

    private fun prepareLocaleList() {
        val localePair = LocaleUtils.getLocalesUsedInProject(BuildConfig.TRANSLATION_ARRAY, getString(R.string.device_default))
        val lp = findPreference<ListPreference>("set_locale")
        lp?.entries = localePair.localeEntries
        lp?.entryValues = localePair.localeEntryValues
    }
}
