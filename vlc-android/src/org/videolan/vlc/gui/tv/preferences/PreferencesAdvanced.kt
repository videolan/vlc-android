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
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.*
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.util.VLCInstance

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesAdvanced : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener,
CoroutineScope by MainScope() {
    override fun getXml() = R.xml.preferences_adv

    override fun getTitleId(): Int {
        return R.string.advanced_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) findPreference<Preference>("debug_logs")?.isVisible = false
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
                AlertDialog.Builder(ctx)
                        .setTitle(R.string.clear_playback_history)
                        .setMessage(R.string.validation)
                        .setIcon(R.drawable.ic_warning)
                        .setPositiveButton(R.string.yes) { _, _ -> launch(Dispatchers.IO) {
                            AbstractMedialibrary.getInstance().clearHistory()
                        }}
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                return true
            }
            "clear_media_db" -> {
                AlertDialog.Builder(ctx)
                        .setTitle(R.string.clear_media_db)
                        .setMessage(R.string.validation)
                        .setIcon(R.drawable.ic_warning)
                        .setPositiveButton(R.string.yes) { _, _ -> launch(Dispatchers.IO) {
                            (AbstractMedialibrary.getInstance() as Medialibrary).clearDatabase(true)
                        }}
                        .setNegativeButton(R.string.cancel, null)
                        .show()
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
                    val networkCachingPref = findPreference<EditTextPreference>(key)
                    networkCachingPref?.text = ""
                    Toast.makeText(activity, R.string.network_caching_popup, Toast.LENGTH_SHORT).show()
                }

                editor.apply()
                VLCInstance.restart()
                (activity as? PreferencesActivity)?.restartMediaPlayer()
            }
            // No break because need VLCInstance.restart();
            "opengl", "chroma_format", "custom_libvlc_options", "deblocking", "enable_frame_skip", "enable_time_stretching_audio", "enable_verbose_mode" -> {
                VLCInstance.restart()
                (activity as? PreferencesActivity)?.restartMediaPlayer()
            }
        }
    }
}
