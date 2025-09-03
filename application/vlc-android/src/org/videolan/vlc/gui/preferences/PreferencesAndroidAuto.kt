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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.tools.KEY_ANDROID_AUTO_QUEUE_INFO_POS_VAL
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.AboutVersionDialog
import org.videolan.vlc.gui.dialogs.AutoInfoDialog
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
        preferenceManager.sharedPreferences!!.getInt(KEY_ANDROID_AUTO_QUEUE_INFO_POS_VAL, 3).also {
            findPreference<Preference>("android_auto_queue_format")?.isEnabled = (it > 0)
        }
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        super.onCreatePreferences(bundle, s)
        settings = Settings.getInstance(requireActivity())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_android_auto_info)
            AutoInfoDialog.newInstance().show(requireActivity().supportFragmentManager, "AutoInfoDialog")
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.findItem(R.id.menu_android_auto_info).isVisible = true
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_android_auto_info).isVisible = true
        super.onPrepareOptionsMenu(menu)
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
            "playback_speed_audio_global" -> {
                PlaybackService.instance?.let {service ->
                    service.playlistManager.getCurrentMedia()?.let {
                        service.playlistManager.restoreSpeed(it)
                        lifecycleScope.launch(Dispatchers.Main) {
                            PlaybackService.updateState()
                        }
                    }
                }
            }
        }
        PlaybackService.updateState()
    }

    private fun getIntegerPreference(sharedPreferences: SharedPreferences, key: String, defaultValue: Int): Int {
        return sharedPreferences.getString(key, defaultValue.toString())?.toInt() ?: defaultValue
    }
}