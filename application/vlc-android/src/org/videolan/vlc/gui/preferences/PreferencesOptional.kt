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

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.FeatureFlag
import org.videolan.vlc.util.FeatureFlagManager

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesOptional : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() =  R.xml.preferences_optional

    override fun getTitleId(): Int {
        return R.string.optional_features
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parent = findPreference<PreferenceScreen>("optional_features")
        FeatureFlag.values().forEach { featureFlags ->
            val pref = CheckBoxPreference(requireActivity())
            pref.isChecked = FeatureFlagManager.isEnabled(requireActivity(), featureFlags)
            pref.title = getString(featureFlags.title)
            pref.key = featureFlags.getKey()
            parent?.addPreference(pref)
            featureFlags.dependsOn?.let { pref.dependency = it.getKey()}
        }
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
        val enabled = findPreference<CheckBoxPreference>(key)!!.isChecked
        FeatureFlagManager.getByKey(key)?.let { FeatureFlagManager.enable(requireActivity(),it, enabled) }
        if (enabled) UiTools.snacker(requireActivity(), getString(R.string.optional_features_warning))
    }
}
