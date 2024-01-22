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

package org.videolan.television.ui.preferences

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.SeekBarPreference
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.videolan.resources.VLCInstance
import org.videolan.television.ui.COLOR_PICKER_SELECTED_COLOR
import org.videolan.television.ui.COLOR_PICKER_TITLE
import org.videolan.television.ui.ColorPickerActivity
import org.videolan.tools.LocaleUtils
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.restartMediaPlayer

private const val SUBTITLE_COLOR_RESULT = 1
private const val SUBTITLE_BACKGROUND_COLOR_RESULT = 2
private const val SUBTITLE_SHADOW_COLOR_RESULT = 3
private const val SUBTITLE_OUTLINE_COLOR_RESULT = 4
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesSubtitles : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {

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
        settings = Settings.getInstance(activity)
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


        subtitlesColor.setOnShowDialogListener { _, currentColor ->
            val intent = Intent(activity, ColorPickerActivity::class.java)
            intent.putExtra(COLOR_PICKER_SELECTED_COLOR, currentColor)
            intent.putExtra(COLOR_PICKER_TITLE, getString(R.string.subtitles_color_title))
            startActivityForResult(intent, SUBTITLE_COLOR_RESULT)
        }
        subtitlesBackgroundColor.setOnShowDialogListener { title, currentColor ->
            if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Showing dialog2 $title, $currentColor")
            val intent = Intent(activity, ColorPickerActivity::class.java)
            intent.putExtra(COLOR_PICKER_SELECTED_COLOR, currentColor)
            intent.putExtra(COLOR_PICKER_TITLE, getString(R.string.subtitles_background_color_title))
            startActivityForResult(intent, SUBTITLE_BACKGROUND_COLOR_RESULT)
        }
        subtitlesShadowColor.setOnShowDialogListener { _, currentColor ->
            val intent = Intent(activity, ColorPickerActivity::class.java)
            intent.putExtra(COLOR_PICKER_SELECTED_COLOR, currentColor)
            intent.putExtra(COLOR_PICKER_TITLE, getString(R.string.subtitles_shadow_title))
            startActivityForResult(intent, SUBTITLE_SHADOW_COLOR_RESULT)
        }
        subtitlesOutlineColor.setOnShowDialogListener { _, currentColor ->
            val intent = Intent(activity, ColorPickerActivity::class.java)
            intent.putExtra(COLOR_PICKER_SELECTED_COLOR, currentColor)
            intent.putExtra(COLOR_PICKER_TITLE, getString(R.string.subtitles_outline_title))
            startActivityForResult(intent, SUBTITLE_OUTLINE_COLOR_RESULT)
        }

        updatePreferredSubtitleTrack()
        prepareLocaleList()
        managePreferenceVisibilities()
    }

    private fun resetAll() {
        subtitlesSize.value = "16"
        subtitlesBold.isChecked = false
        subtitlesColor.saveValue(ContextCompat.getColor(activity, R.color.white))
        subtitlesOpacity.value = 255

        subtitlesBackgroundEnabled.isChecked = false
        subtitlesBackgroundColor.saveValue(ContextCompat.getColor(activity, R.color.black))
        subtitlesBackgroundOpacity.value = 255

        subtitlesShadowEnabled.isChecked = true
        subtitlesShadowColor.saveValue(ContextCompat.getColor(activity, R.color.black))
        subtitlesShadowOpacity.value = 255

        subtitlesOutlineEnabled.isChecked = true
        subtitlesOutlineColor.saveValue(ContextCompat.getColor(activity, R.color.black))
        subtitlesOutlineOpacity.value = 255
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return

        if (resultCode == Activity.RESULT_OK && data.hasExtra(COLOR_PICKER_SELECTED_COLOR)) {
           when (requestCode) {
               SUBTITLE_COLOR_RESULT -> subtitlesColor
               SUBTITLE_SHADOW_COLOR_RESULT -> subtitlesShadowColor
               SUBTITLE_OUTLINE_COLOR_RESULT -> subtitlesOutlineColor
               SUBTITLE_BACKGROUND_COLOR_RESULT -> subtitlesBackgroundColor
               else -> null
           }?.saveValue(data.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0))
        }
    }

    private fun updatePreferredSubtitleTrack() {
        val value = Settings.getInstance(activity).getString("subtitle_preferred_language", null)
        if (value.isNullOrEmpty())
            preferredSubtitleTrack.summary = getString(R.string.no_track_preference)
        else
            preferredSubtitleTrack.summary = getString(R.string.track_preference, value)
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "subtitles_size", "subtitles_bold", "subtitle_text_encoding",
            "subtitles_color", "subtitles_color_opacity",
            "subtitles_background_color", "subtitles_background_color_opacity", "subtitles_background",
            "subtitles_outline", "subtitles_outline_size", "subtitles_outline_color", "subtitles_outline_color_opacity",
            "subtitles_shadow", "subtitles_shadow_color", "subtitles_shadow_color_opacity" -> {
                launch {
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

