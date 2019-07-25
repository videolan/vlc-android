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
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesFragment : BasePreferenceFragment() {

    override fun getXml() = R.xml.preferences

    override fun getTitleId() = R.string.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference<Preference>(SCREEN_ORIENTATION)?.isVisible = false
        findPreference<Preference>("extensions_category")?.isVisible = false
        findPreference<Preference>("casting_category")?.isVisible = false
        findPreference<Preference>(KEY_VIDEO_APP_SWITCH)?.isVisible = AndroidDevices.hasPiP
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val context = activity ?: return false
        return when (preference.key) {
            "directories" -> {
                when {
                    AbstractMedialibrary.getInstance().isWorking -> Toast.makeText(context, getString(R.string.settings_ml_block_scan), Toast.LENGTH_SHORT).show()
                    Permissions.canReadStorage(context) -> {
                        val intent = Intent(context.applicationContext, SecondaryActivity::class.java)
                        intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
                        startActivity(intent)
                        activity.setResult(RESULT_RESTART)
                    }
                    else -> Permissions.showStoragePermissionDialog(activity as FragmentActivity, false)
                }
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }
}
