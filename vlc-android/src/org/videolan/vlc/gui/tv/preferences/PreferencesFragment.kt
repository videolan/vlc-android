/*
 * *************************************************************************
 *  PreferencesFragment.java
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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.preferences.PreferencesActivity.Companion.KEY_VIDEO_APP_SWITCH
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Permissions

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesFragment : BasePreferenceFragment() {

    override fun getXml(): Int {
        return R.xml.preferences
    }

    override fun getTitleId(): Int {
        return R.string.preferences
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference("screen_orientation").isVisible = false
        findPreference("extensions_category").isVisible = false
        findPreference("casting_category").isVisible = false
        findPreference(KEY_VIDEO_APP_SWITCH).isVisible = AndroidDevices.hasPiP
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val context = activity ?: return false
        when (preference.key) {
            "directories" -> {
                if (VLCApplication.getMLInstance().isWorking)
                    Toast.makeText(context, getString(R.string.settings_ml_block_scan), Toast.LENGTH_SHORT).show()
                else if (Permissions.canReadStorage(context)) {
                    val intent = Intent(context.applicationContext, SecondaryActivity::class.java)
                    intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
                    startActivity(intent)
                    activity.setResult(PreferencesActivity.RESULT_RESTART)
                } else
                    Permissions.showStoragePermissionDialog(activity as FragmentActivity, false)
                return true
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
    }

    companion object {

        val TAG = "VLC/PreferencesFragment"

        val PLAYBACK_HISTORY = "playback_history"
    }
}
