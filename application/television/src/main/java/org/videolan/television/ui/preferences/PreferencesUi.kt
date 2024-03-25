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
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import org.videolan.resources.AppContextProvider
import org.videolan.television.ui.browser.REQUEST_CODE_RESTART_APP
import org.videolan.television.ui.dialogs.ConfirmationTvActivity
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.FeatureTouchOnlyWarningDialog

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesUi : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var tvUiPref: CheckBoxPreference
    private var currentLocale: String? = null

    override fun getXml() = R.xml.preferences_ui

    override fun getTitleId() = R.string.interface_prefs_screen

    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.getInstance(activity).run {
            if (!contains(FORCE_PLAY_ALL_VIDEO)) putSingle(FORCE_PLAY_ALL_VIDEO, true)
        }
        super.onCreate(savedInstanceState)
        tvUiPref = findPreference(PREF_TV_UI)!!
        tvUiPref.setDefaultValue(true)
        findPreference<Preference>(KEY_APP_THEME)?.isVisible = false
        findPreference<Preference>(LIST_TITLE_ELLIPSIZE)?.isVisible = false
        findPreference<Preference>(TV_FOLDERS_FIRST)?.isVisible = true
        findPreference<Preference>(BROWSER_SHOW_HIDDEN_FILES)?.isVisible = true
        prepareLocaleList()
        currentLocale = AppContextProvider.locale
    }

    override fun onResume() {
        super.onResume()
        val setLocale = Settings.getInstance(activity).getString("set_locale", "")
        if (currentLocale != setLocale) {
            val intent = Intent(activity, ConfirmationTvActivity::class.java)
            intent.putExtra(ConfirmationTvActivity.CONFIRMATION_DIALOG_TITLE, getString(R.string.restart_vlc))
            intent.putExtra(ConfirmationTvActivity.CONFIRMATION_DIALOG_TEXT, getString(R.string.restart_message))
            activity.startActivityForResult(intent, REQUEST_CODE_RESTART_APP)
            currentLocale = setLocale
        }
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
            PREF_TV_UI -> {
                Settings.tvUI = sharedPreferences.getBoolean(PREF_TV_UI, false)
                (activity as PreferencesActivity).setRestartApp()
            }

            TV_FOLDERS_FIRST -> Settings.tvFoldersFirst = sharedPreferences.getBoolean(TV_FOLDERS_FIRST, true)
            BROWSER_SHOW_HIDDEN_FILES -> Settings.showHiddenFiles = sharedPreferences.getBoolean(BROWSER_SHOW_HIDDEN_FILES, false)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null) return false
        when (preference.key) {
            PREF_TV_UI -> {
                if (!tvUiPref.isChecked && Settings.device.isTv) {
                    tvUiPref.isChecked = true
                    val dialog = FeatureTouchOnlyWarningDialog.newInstance {
                        tvUiPref.isChecked = false
                    }
                    dialog.show((activity as FragmentActivity).supportFragmentManager, FeatureTouchOnlyWarningDialog::class.simpleName)
                    return true
                }
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
            "media_seen" -> (activity as PreferencesActivity).setRestart()
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun prepareLocaleList() {
        val localePair = LocaleUtils.getLocalesUsedInProject(BuildConfig.TRANSLATION_ARRAY, getString(R.string.device_default))
        val lp = findPreference<ListPreference>("set_locale")
        lp?.entries = localePair.localeEntries
        lp?.entryValues = localePair.localeEntryValues
    }
}
