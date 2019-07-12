/*
 * *************************************************************************
 *  PreferencesAdvanced.java
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
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.util.VLCInstance

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesAdvanced : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun getXml(): Int {
        return R.xml.preferences_adv
    }


    override fun getTitleId(): Int {
        return R.string.advanced_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) findPreference("debug_logs").isVisible = false
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
        val ctx = activity
        if (preference.key == null || ctx == null) return false
        when (preference.key) {
            "debug_logs" -> {
                val intent = Intent(ctx, DebugLogActivity::class.java)
                startActivity(intent)
                return true
            }
            "clear_history" -> {
                AlertDialog.Builder(activity)
                        .setTitle(R.string.clear_playback_history)
                        .setMessage(R.string.validation)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes) { _, _ -> AbstractMedialibrary.getInstance().clearHistory() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                return true
            }
            "clear_media_db" -> {
                val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                i.addCategory(Intent.CATEGORY_DEFAULT)
                i.data = Uri.parse("package:" + ctx.packageName)
                startActivity(i)
                return true
            }
            "quit_app" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "network_caching" -> {
                val editor = sharedPreferences.edit()
                try {
                    editor.putInt("network_caching_value", Integer.parseInt(sharedPreferences.getString(key, "0")!!))
                } catch (e: NumberFormatException) {
                    editor.putInt("network_caching_value", 0)
                    val networkCachingPref = findPreference(key) as EditTextPreference
                    networkCachingPref.text = ""
                    Toast.makeText(activity, R.string.network_caching_popup, Toast.LENGTH_SHORT).show()
                }

                editor.apply()
                VLCInstance.restart()
                if (activity != null)
                    (activity as PreferencesActivity).restartMediaPlayer()
            }
            // No break because need VLCInstance.restart();
            "opengl", "chroma_format", "custom_libvlc_options", "deblocking", "enable_frame_skip", "enable_time_stretching_audio", "enable_verbose_mode" -> {
                VLCInstance.restart()
                if (activity != null)
                    (activity as PreferencesActivity).restartMediaPlayer()
            }
        }
    }
}
