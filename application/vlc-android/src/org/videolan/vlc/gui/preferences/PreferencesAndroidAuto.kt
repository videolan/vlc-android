/*
 * *************************************************************************
 *  PreferencesAndroidAuto.kt
 * **************************************************************************
 *  Copyright © 2024 VLC authors and VideoLAN
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
import androidx.core.content.edit
import androidx.preference.Preference
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import java.lang.NumberFormatException

class PreferencesAndroidAuto : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: SharedPreferences

    override fun getTitleId() = R.string.android_auto

    override fun getXml() = R.xml.preferences_android_auto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        updatePassThroughSummary()
    }

    private fun updatePassThroughSummary() {
        preferenceManager.sharedPreferences!!.getInt("android_auto_queue_info_pos_val", 3).also {
            findPreference<Preference>("android_auto_queue_format")?.isEnabled = (it > 0)
        }
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        super.onCreatePreferences(bundle, s)
        settings = Settings.getInstance(requireActivity())
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return

        val mapOfKeys = mapOf(
                "android_auto_queue_info_pos" to 3,
                "android_auto_queue_format" to 1
        )

        mapOfKeys[key]?.let { defaultValue ->
            sharedPreferences.edit {
                try {
                    putInt("${key}_val", getIntegerPreference(sharedPreferences, key, defaultValue))
                } catch (e: NumberFormatException) {
                }
            }
        }
        when (key) {
            "android_auto_queue_info_pos" -> updatePassThroughSummary()
        }
        PlaybackService.updateState()
    }

    private fun getIntegerPreference(sharedPreferences: SharedPreferences, key: String, defaultValue: Int): Int {
        return sharedPreferences.getString(key, defaultValue.toString())?.toInt() ?: defaultValue
    }
}