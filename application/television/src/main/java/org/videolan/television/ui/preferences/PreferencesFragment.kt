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

package org.videolan.television.ui.preferences

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.tools.Settings.isPinCodeSet
import org.videolan.vlc.R
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.dialogs.ConfirmAudioPlayQueueDialog

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() = R.xml.preferences

    override fun getTitleId() = R.string.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference<Preference>(SCREEN_ORIENTATION)?.isVisible = false
        findPreference<Preference>("casting_category")?.isVisible = false
        findPreference<Preference>(KEY_VIDEO_APP_SWITCH)?.isVisible = AndroidDevices.hasPiP
        findPreference<Preference>("remote_access_category")?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                0 -> {
                    val preferenceScreen: PreferenceScreen? = findPreference("parental_control") as PreferenceScreen?
                    onPreferenceTreeClick(preferenceScreen!!)
                }
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val context = activity ?: return false
        return when (preference.key) {
            "directories" -> {
                if (Medialibrary.getInstance().isWorking) {
                    Toast.makeText(
                        context,
                        getString(R.string.settings_ml_block_scan),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val intent = Intent(context.applicationContext, SecondaryActivity::class.java)
                    intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
                    startActivity(intent)
                    activity.setResult(RESULT_RESTART)
                }
                true
            }
            "parental_control" -> {
                if (!activity.isPinCodeSet()) {
                    val intent = PinCodeActivity.getIntent(activity, PinCodeReason.FIRST_CREATION)
                    startActivityForResult(intent, 0)
                    true
                } else super.onPreferenceTreeClick(preference)
            }
            AUDIO_RESUME_PLAYBACK -> {

                val audioResumePref = findPreference<CheckBoxPreference>(AUDIO_RESUME_PLAYBACK)
                if (audioResumePref?.isChecked == false) {
                    val dialog = ConfirmAudioPlayQueueDialog()
                    dialog.show((activity as FragmentActivity).supportFragmentManager, ConfirmAudioPlayQueueDialog::class.simpleName)
                    dialog.setListener {
                        Settings.getInstance(activity).edit()
                                .remove(KEY_AUDIO_LAST_PLAYLIST)
                                .remove(KEY_MEDIA_LAST_PLAYLIST_RESUME)
                                .remove(KEY_CURRENT_AUDIO_RESUME_TITLE)
                                .remove(KEY_CURRENT_AUDIO_RESUME_ARTIST)
                                .remove(KEY_CURRENT_AUDIO_RESUME_THUMB)
                                .remove(KEY_CURRENT_AUDIO)
                                .remove(KEY_CURRENT_MEDIA)
                                .remove(KEY_CURRENT_MEDIA_RESUME)
                                .apply()
                        activity.setResult(RESULT_RESTART)
                        audioResumePref.isChecked = false
                    }

                    audioResumePref.isChecked = true
                }
                return true
            }
            VIDEO_RESUME_PLAYBACK -> {
                Settings.getInstance(activity).edit()
                        .remove(KEY_MEDIA_LAST_PLAYLIST)
                        .remove(KEY_MEDIA_LAST_PLAYLIST_RESUME)
                        .remove(KEY_CURRENT_MEDIA_RESUME)
                        .remove(KEY_CURRENT_MEDIA)
                        .apply()
                val activity = activity
                activity?.setResult(RESULT_RESTART)
                return true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PLAYBACK_HISTORY -> {
                if (sharedPreferences!!.getBoolean(key, true)) {
                    findPreference<CheckBoxPreference>(AUDIO_RESUME_PLAYBACK)?.isChecked = true
                    findPreference<CheckBoxPreference>(VIDEO_RESUME_PLAYBACK)?.isChecked = true
                }
            }
        }
    }
}
