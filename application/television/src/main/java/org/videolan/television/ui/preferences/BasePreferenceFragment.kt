/*
 * *************************************************************************
 *  BasePreferenceFragment.java
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

import android.app.Fragment
import android.content.Intent
import android.os.Bundle
import androidx.leanback.preference.LeanbackPreferenceFragment
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragment
import org.videolan.television.ui.dialogs.ConfirmationTvActivity

import org.videolan.vlc.R

const val RESTART_CODE = 10001
abstract class BasePreferenceFragment : LeanbackPreferenceFragment() {

    protected abstract fun getXml(): Int
    protected abstract fun getTitleId(): Int

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        addPreferencesFromResource(getXml())
    }

    protected fun loadFragment(fragment: Fragment) {
        activity.fragmentManager.beginTransaction().replace(R.id.fragment_placeholder, fragment)
                .addToBackStack("main")
                .commit()
    }

    protected fun buildPreferenceDialogFragment(preference: Preference): PreferenceDialogFragment? {
        return if (preference is EditTextPreference) {
            CustomEditTextPreferenceDialogFragment.newInstance(preference.getKey()).apply {
                setTargetFragment(this@BasePreferenceFragment, 0)
                show(this@BasePreferenceFragment.fragmentManager, DIALOG_FRAGMENT_TAG)
            }
        } else null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RESTART_CODE) {
            if (resultCode == ConfirmationTvActivity.ACTION_ID_POSITIVE) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    companion object {
        private const val DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}
