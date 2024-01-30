/*
 * *************************************************************************
 *  PreferencesParentalControl.java
 * **************************************************************************
 *  Copyright © 2023 VLC authors and VideoLAN
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

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import org.videolan.tools.KEY_SAFE_MODE
import org.videolan.tools.Settings
import org.videolan.tools.Settings.isPinCodeSet
import org.videolan.vlc.R
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason

class PreferencesParentalControl : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {


    override fun getXml() = R.xml.preferences_parental_control

    override fun getTitleId() = R.string.parental_control

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!activity.isPinCodeSet()) {
            val intent = PinCodeActivity.getIntent(activity, PinCodeReason.FIRST_CREATION)
            startActivityForResult(intent, 1)
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

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null) return false
        when (preference.key) {
            "modify_pin_code" -> {
                val intent = PinCodeActivity.getIntent(activity, PinCodeReason.MODIFY)
                startActivityForResult(intent, 0)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                0 -> {
                    Toast.makeText(activity, R.string.pin_code_modified, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            KEY_SAFE_MODE -> {
                Settings.safeMode = sharedPreferences.getBoolean(key, false) && activity.isPinCodeSet()
            }
        }
    }
}
