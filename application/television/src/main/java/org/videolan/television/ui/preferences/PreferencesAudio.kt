/*
 * *************************************************************************
 *  PreferencesAudio.java
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

import android.annotation.TargetApi
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import androidx.core.content.edit
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.libvlc.util.HWDecoderUtil
import org.videolan.resources.VLCInstance
import org.videolan.tools.AUDIO_DUCKING
import org.videolan.tools.KEY_PLAYBACK_RATE
import org.videolan.tools.KEY_PLAYBACK_SPEED_PERSIST
import org.videolan.tools.LocaleUtils
import org.videolan.tools.RESUME_PLAYBACK
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.isVLC4
import org.videolan.vlc.util.LocaleUtil
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

private const val TAG = "VLC/PreferencesAudio"

@Suppress("DEPRECATION")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesAudio : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {

    private lateinit var preferredAudioTrack: ListPreference

    override fun getXml(): Int {
        return R.xml.preferences_audio
    }

    override fun getTitleId(): Int {
        return R.string.audio_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findPreference<Preference>("enable_headset_detection")?.isVisible = false
        findPreference<Preference>("enable_play_on_headset_insertion")?.isVisible = false
        findPreference<Preference>("ignore_headset_media_button_presses")?.isVisible = false
        findPreference<Preference>("headset_prefs_category")?.isVisible = false
        val aoutPref = findPreference<ListPreference>("aout")
        findPreference<Preference>(RESUME_PLAYBACK)?.isVisible = false
        findPreference<Preference>(AUDIO_DUCKING)?.isVisible = !AndroidUtil.isOOrLater

        val aout = HWDecoderUtil.getAudioOutputFromDevice()
        if (aout != HWDecoderUtil.AudioOutput.ALL) {
            /* no AudioOutput choice */
            aoutPref?.isVisible = false
        }

        if (isVLC4() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            aoutPref?.entryValues = activity.resources.getStringArray(R.array.aouts_complete_values)
            aoutPref?.entries = activity.resources.getStringArray(R.array.aouts_complete)
        }

        updatePassThroughSummary()
        val opensles = "2" == preferenceManager.sharedPreferences!!.getString("aout", "0")
        if (opensles) findPreference<Preference>("audio_digital_output")?.isVisible = false
        preferredAudioTrack = findPreference("audio_preferred_language")!!
        updatePreferredAudioTrack()
        prepareLocaleList()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        val f = super.buildPreferenceDialogFragment(preference)
        if (f is CustomEditTextPreferenceDialogFragment) {
            when (preference.key) {
                "audio-replay-gain-default", "audio-replay-gain-preamp" -> {
                    f.setFilters(arrayOf(InputFilter.LengthFilter(6)))
                    f.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
                }
            }
            return
        }
        super.onDisplayPreferenceDialog(preference)
    }

    private fun updatePreferredAudioTrack() {
        val value = Settings.getInstance(activity).getString("audio_preferred_language", null)
        if (value.isNullOrEmpty())
            preferredAudioTrack.summary = getString(R.string.no_track_preference)
        else
            preferredAudioTrack.summary = getString(R.string.track_preference, LocaleUtil.getLocaleName(value))
    }

    private fun updatePassThroughSummary() {
        val pt = preferenceManager.sharedPreferences!!.getBoolean("audio_digital_output", false)
        findPreference<Preference>("audio_digital_output")?.setSummary(if (pt) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled)
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            "aout" -> {
                launch { restartLibVLC() }
                val opensles = "2" == preferenceManager.sharedPreferences!!.getString("aout", "0")
                if (opensles) findPreference<CheckBoxPreference>("audio_digital_output")?.isChecked = false
                findPreference<Preference>("audio_digital_output")?.isVisible = !opensles
            }
            "audio_digital_output" -> updatePassThroughSummary()
            "audio_preferred_language" -> updatePreferredAudioTrack()
            "audio-replay-gain-enable", "audio-replay-gain-mode", "audio-replay-gain-peak-protection" -> launch { restartLibVLC() }
            "audio-replay-gain-default", "audio-replay-gain-preamp" -> {
                val defValue = if (key == "audio-replay-gain-default") "-7.0" else "0.0"
                val newValue = sharedPreferences.getString(key, defValue)
                var fmtValue = defValue
                try {
                    fmtValue = DecimalFormat("###0.0###", DecimalFormatSymbols(Locale.ENGLISH)).format(newValue?.toDouble())
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Could not parse value: $newValue. Setting $key to $fmtValue", e)
                } finally {
                    if (fmtValue != newValue) {
                        sharedPreferences.edit {
                            // putString will trigger another preference change event. Restart libVLC when it settles.
                            putString(key, fmtValue)
                            findPreference<EditTextPreference>(key)?.let { it.text = fmtValue }
                        }
                    } else launch { restartLibVLC() }
                }
            }
            KEY_PLAYBACK_SPEED_PERSIST -> sharedPreferences.putSingle(KEY_PLAYBACK_RATE, 1.0f)
        }
    }

    private suspend fun restartLibVLC() {
        VLCInstance.restart()
        restartMediaPlayer()
    }

    private fun prepareLocaleList() {
        val localePair = LocaleUtils.getLocalesUsedInProject(BuildConfig.TRANSLATION_ARRAY, getString(R.string.no_track_preference))
        preferredAudioTrack.entries = localePair.localeEntries
        preferredAudioTrack.entryValues = localePair.localeEntryValues
    }
}
