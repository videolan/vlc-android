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

package org.videolan.television.ui.preferences

import android.annotation.TargetApi
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.FeatureFlagWarningDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.util.FeatureFlag
import org.videolan.vlc.util.FeatureFlagManager

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesOptional : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {
    override fun getXml(): Int {
        return R.xml.preferences_optional
    }


    override fun getTitleId(): Int {
        return R.string.optional_features
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parent = findPreference<PreferenceScreen>("optional_features")
        FeatureFlag.values().forEach { featureFlags ->
            val pref = CheckBoxPreference(activity)
            pref.isChecked = FeatureFlagManager.isEnabled(activity, featureFlags)
            pref.title = getString(featureFlags.title)
            pref.key = featureFlags.getKey()
            parent?.addPreference(pref)
            featureFlags.dependsOn?.let { pref.dependency = it.getKey() }
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
        val enabled = findPreference<CheckBoxPreference>(key)!!.isChecked
        FeatureFlagManager.getByKey(key)?.let { FeatureFlagManager.enable(activity, it, enabled) }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null) return super.onPreferenceTreeClick(preference)
        FeatureFlagManager.getByKey(preference.key)?.let {
            if (it.warning != null) {
                val currentPreference = findPreference<CheckBoxPreference>(preference.key)!!
                if (!currentPreference.isChecked) return true
                currentPreference.isChecked = false
                val dialog = FeatureFlagWarningDialog.newInstance(it) {
                    currentPreference.isChecked = true
                }
                dialog.show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

}
