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

package org.videolan.vlc.gui.preferences

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.VLCInstance
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.util.FileUtils
import java.io.File

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesAdvanced : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() =  R.xml.preferences_adv

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
        if (preference.key == null)
            return false
        when (preference.key) {
            "debug_logs" -> {
                val intent = Intent(requireContext(), DebugLogActivity::class.java)
                startActivity(intent)
                return true
            }
            "clear_history" -> {
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.clear_playback_history)
                        .setMessage(R.string.validation)
                        .setIcon(R.drawable.ic_warning)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) { Medialibrary.getInstance().clearHistory() }
                        }

                        .setNegativeButton(R.string.cancel, null).show()
                return true
            }
            "clear_media_db" -> {
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.clear_media_db)
                        .setMessage(getString(R.string.clear_media_db_warning, getString(R.string.validation)))
                        .setIcon(R.drawable.ic_warning)
                        .setPositiveButton(R.string.yes) { _, _ -> lifecycleScope.launch(Dispatchers.IO) {
                            Medialibrary.getInstance().clearDatabase(true)
                        }}
                        .setNegativeButton(R.string.cancel, null).show()
                return true
            }
            "quit_app" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
                return true
            }
            "dump_media_db" -> {
                if (Medialibrary.getInstance().isWorking)
                    view?.let { UiTools.snacker(it, getString(R.string.settings_ml_block_scan)) }
                else {
                    val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + Medialibrary.VLC_MEDIA_DB_NAME)
                    lifecycleScope.launch {
                        if (getWritePermission(Uri.fromFile(dst))) {
                            val copied = withContext(Dispatchers.IO) {
                                val db = File(requireContext().getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)

                                FileUtils.copyFile(db, dst)
                            }
                            Toast.makeText(context, getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
                    UiTools.snacker(view!!, R.string.network_caching_popup)
                }
                editor.apply()
                VLCInstance.restart()
                (activity as? PreferencesActivity)?.restartMediaPlayer()
            }
            // No break because need VLCInstance.restart();
            "custom_libvlc_options" -> {
                try {
                    VLCInstance.restart()
                } catch (e: IllegalStateException){
                    view?.let { UiTools.snacker(it, R.string.custom_libvlc_options_invalid) }
                    sharedPreferences.putSingle("custom_libvlc_options", "")
                } finally {
                    (activity as? PreferencesActivity)?.restartMediaPlayer()
                }

            }
            "opengl", "chroma_format", "deblocking", "enable_frame_skip", "enable_time_stretching_audio", "enable_verbose_mode" -> {
                VLCInstance.restart()
                (activity as? PreferencesActivity)?.restartMediaPlayer()
            }
        }
    }
}
