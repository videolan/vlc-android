/*
 * *************************************************************************
 *  PreferencesSubtitles.java
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

package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.SeekBarPreference
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import kotlinx.coroutines.launch
import org.videolan.resources.VLCInstance
import org.videolan.tools.LocaleUtils
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.restartMediaPlayer

class PreferencesSubtitles : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: SharedPreferences
    private lateinit var preferredSubtitleTrack: ListPreference

    private lateinit var subtitlesSize: ListPreference
    private lateinit var subtitlesBold: CheckBoxPreference
    private lateinit var subtitlesOpacity: SeekBarPreference
    private lateinit var subtitlesColor: ColorPreferenceCompat

    private lateinit var subtitlesBackgroundEnabled: CheckBoxPreference
    private lateinit var subtitlesBackgroundColor: ColorPreferenceCompat
    private lateinit var subtitlesBackgroundOpacity: SeekBarPreference

    private lateinit var subtitlesShadowEnabled: CheckBoxPreference
    private lateinit var subtitlesShadowColor: ColorPreferenceCompat
    private lateinit var subtitlesShadowOpacity: SeekBarPreference

    private lateinit var subtitlesOutlineEnabled: CheckBoxPreference
    private lateinit var subtitlesOutlineSize: ListPreference
    private lateinit var subtitlesOutlineColor: ColorPreferenceCompat
    private lateinit var subtitlesOutlineOpacity: SeekBarPreference

    override fun getXml(): Int {
        return R.xml.preferences_subtitles
    }

    override fun getTitleId(): Int {
        return R.string.subtitles_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.getInstance(requireActivity())
        preferredSubtitleTrack = findPreference("subtitle_preferred_language")!!

        //main
        subtitlesSize = findPreference("subtitles_size")!!
        subtitlesBold = findPreference("subtitles_bold")!!
        subtitlesColor = findPreference("subtitles_color")!!
        subtitlesOpacity = findPreference("subtitles_color_opacity")!!

        //background
        subtitlesBackgroundEnabled = findPreference("subtitles_background")!!
        subtitlesBackgroundColor = findPreference("subtitles_background_color")!!
        subtitlesBackgroundOpacity = findPreference("subtitles_background_color_opacity")!!

        //shadow
        subtitlesShadowEnabled = findPreference("subtitles_shadow")!!
        subtitlesShadowColor = findPreference("subtitles_shadow_color")!!
        subtitlesShadowOpacity = findPreference("subtitles_shadow_color_opacity")!!

        //outline
        subtitlesOutlineEnabled = findPreference("subtitles_outline")!!
        subtitlesOutlineSize = findPreference("subtitles_outline_size")!!
        subtitlesOutlineColor = findPreference("subtitles_outline_color")!!
        subtitlesOutlineOpacity = findPreference("subtitles_outline_color_opacity")!!

        val presetPreference = findPreference<ListPreference>("subtitles_presets")!!
        presetPreference.value = "-1"
        presetPreference.setOnPreferenceChangeListener { _, newValue ->
            resetAll()
            when (newValue) {
                "1" -> subtitlesSize.value = "13"
                "2" -> {
                    subtitlesSize.value = "10"
                    subtitlesBackgroundEnabled.isChecked = true
                    subtitlesBackgroundOpacity.value = 255
                    subtitlesShadowEnabled.isChecked = false
                    subtitlesOutlineEnabled.isChecked = false
                }
                "3" -> {
                    subtitlesBackgroundEnabled.isChecked = true
                    subtitlesBackgroundOpacity.value = 128
                    subtitlesShadowEnabled.isChecked = false
                }
                "4" -> subtitlesColor.saveValue(Color.YELLOW)
                "5" -> {
                    subtitlesColor.saveValue(Color.YELLOW)
                    subtitlesBackgroundEnabled.isChecked = true
                    subtitlesBackgroundOpacity.value = 128
                    subtitlesShadowEnabled.isChecked = false
                }
            }
            false
        }

        updatePreferredSubtitleTrack()
        prepareLocaleList()
        managePreferenceVisibilities()
    }

    private fun resetAll() {
        subtitlesSize.value = "16"
        subtitlesBold.isChecked = false
        subtitlesColor.saveValue(ContextCompat.getColor(requireActivity(), R.color.white))
        subtitlesOpacity.value = 255

        subtitlesBackgroundEnabled.isChecked = false
        subtitlesBackgroundColor.saveValue(ContextCompat.getColor(requireActivity(), R.color.black))
        subtitlesBackgroundOpacity.value = 255

        subtitlesShadowEnabled.isChecked = true
        subtitlesShadowColor.saveValue(ContextCompat.getColor(requireActivity(), R.color.black))
        subtitlesShadowOpacity.value = 128

        subtitlesOutlineEnabled.isChecked = true
        subtitlesOutlineColor.saveValue(ContextCompat.getColor(requireActivity(), R.color.black))
        subtitlesOutlineOpacity.value = 255
    }

    private fun updatePreferredSubtitleTrack() {
        val value = Settings.getInstance(requireActivity()).getString("subtitle_preferred_language", null)
        if (value.isNullOrEmpty())
            preferredSubtitleTrack.summary = getString(R.string.no_track_preference)
        else
            preferredSubtitleTrack.summary = getString(R.string.track_preference, value)
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
        when (key) {
            "subtitles_size", "subtitles_bold", "subtitle_text_encoding",
            "subtitles_color", "subtitles_color_opacity",
            "subtitles_background_color", "subtitles_background_color_opacity", "subtitles_background",
            "subtitles_outline", "subtitles_outline_size", "subtitles_outline_color", "subtitles_outline_color_opacity",
            "subtitles_shadow", "subtitles_shadow_color", "subtitles_shadow_color_opacity" -> {
                lifecycleScope.launch {
                    VLCInstance.restart()
                    restartMediaPlayer()
                }
                managePreferenceVisibilities()
            }
            "subtitle_preferred_language" -> updatePreferredSubtitleTrack()
        }
    }

    private fun managePreferenceVisibilities() {
        val subtitleBackgroundEnabled = settings.getBoolean("subtitles_background", false)
        subtitlesBackgroundColor.isVisible = subtitleBackgroundEnabled
        subtitlesBackgroundOpacity.isVisible = subtitleBackgroundEnabled

        val subtitleShadowEnabled = settings.getBoolean("subtitles_shadow", true)
        subtitlesShadowColor.isVisible = subtitleShadowEnabled
        subtitlesShadowOpacity.isVisible = subtitleShadowEnabled

        val subtitleOutlineEnabled = settings.getBoolean("subtitles_outline", true)
        //we disable the size for now as it causes some render issues. May be shown in the future
//        subtitlesOutlineSize.isVisible = subtitleOutlineEnabled
        subtitlesOutlineColor.isVisible = subtitleOutlineEnabled
        subtitlesOutlineOpacity.isVisible = subtitleOutlineEnabled
    }

    private fun prepareLocaleList() {
        val localePair = LocaleUtils.getLocalesUsedInProject(BuildConfig.TRANSLATION_ARRAY, getString(R.string.no_track_preference))
        preferredSubtitleTrack.entries = localePair.localeEntries
        preferredSubtitleTrack.entryValues = localePair.localeEntryValues
    }

}
