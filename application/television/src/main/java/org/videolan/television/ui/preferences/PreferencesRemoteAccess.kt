/*
 * *************************************************************************
 *  PreferencesRemoteAccess.java
 * **************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.resources.util.restartRemoteAccess
import org.videolan.resources.util.startRemoteAccess
import org.videolan.resources.util.stopRemoteAccess
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.KEY_REMOTE_ACCESS_ML_CONTENT
import org.videolan.tools.REMOTE_ACCESS_FILE_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_NETWORK_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_PLAYBACK_CONTROL
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.util.TextUtils

class PreferencesRemoteAccess : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {

    private lateinit var settings: SharedPreferences
    private lateinit var medialibraryContentPreference: MultiSelectListPreference

    override fun getTitleId() = R.string.remote_access

    override fun getXml() = R.xml.preferences_remote_access

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        if(!settings.getBoolean(REMOTE_ACCESS_ONBOARDING,  false)) {
            settings.putSingle(REMOTE_ACCESS_ONBOARDING, true)
            startActivity(Intent(Intent.ACTION_VIEW).apply { setClassName(activity, REMOTE_ACCESS_ONBOARDING) })
        }
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        super.onCreatePreferences(bundle, s)
        settings = Settings.getInstance(activity)
        medialibraryContentPreference = findPreference(KEY_REMOTE_ACCESS_ML_CONTENT)!!
        findPreference<PreferenceScreen>("remote_access_info")?.isVisible = true
        manageMLContentSummary()
    }

    private fun manageMLContentSummary() {
        val value = settings.getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, resources.getStringArray(R.array.remote_access_content_values).toSet())!!
        val values = resources.getStringArray(R.array.remote_access_content_values)
        val entries = resources.getStringArray(R.array.remote_access_content_entries)
        val currentValues = mutableListOf<String>()
        val currentDisabledValues = mutableListOf<String>()
        value.forEach {
            currentValues.add(entries[values.indexOf(it)])
        }
        values.forEach {
            if (!value.contains(it)) currentDisabledValues.add(entries[values.indexOf(it)])
        }
        val currentString = if (currentValues.isEmpty()) "-" else TextUtils.separatedString(currentValues.toTypedArray())
        val currentDisabledString = if (currentDisabledValues.isEmpty()) "-" else TextUtils.separatedString(currentDisabledValues.toTypedArray())
        medialibraryContentPreference.summary = getString(R.string.remote_access_medialibrary_content_summary, currentString, currentDisabledString)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference?.key == "remote_access_status") {
            activity.startActivity(Intent(activity, StartActivity::class.java).apply { action = "vlc.remoteaccess.share" })
        }
        if (preference?.key == "remote_access_info") {
            startActivity(Intent(Intent.ACTION_VIEW).apply { setClassName(activity, REMOTE_ACCESS_ONBOARDING) })
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            KEY_ENABLE_REMOTE_ACCESS -> {
                val serverEnabled = sharedPreferences?.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false)
                        ?: false
                Settings.remoteAccessEnabled.postValue(serverEnabled)
                if (serverEnabled) {
                    activity.startRemoteAccess()
                } else {
                    activity.stopRemoteAccess()
                }
            }
            KEY_REMOTE_ACCESS_ML_CONTENT -> {
                manageMLContentSummary()
            }
            REMOTE_ACCESS_NETWORK_BROWSER_CONTENT -> {
                activity.restartRemoteAccess()
            }
            REMOTE_ACCESS_FILE_BROWSER_CONTENT, REMOTE_ACCESS_PLAYBACK_CONTROL -> {

            }
        }
    }
}

