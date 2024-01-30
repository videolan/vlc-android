/*
 * ************************************************************************
 *  PreferencesWidgets.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.preferences.widgets

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.SeekBarPreference
import com.google.android.material.color.DynamicColors
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import org.videolan.tools.WIDGETS_BACKGROUND_LAST_COLORS
import org.videolan.tools.WIDGETS_FOREGROUND_LAST_COLORS
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.preferences.BasePreferenceFragment
import org.videolan.vlc.gui.view.NumberPickerPreference
import org.videolan.vlc.repository.WidgetRepository
import org.videolan.vlc.widget.WidgetViewModel
import org.videolan.vlc.widget.utils.WidgetSizeUtil
import org.videolan.vlc.widget.utils.WidgetType
import org.videolan.vlc.widget.utils.WidgetUtils
import org.videolan.vlc.widget.utils.WidgetUtils.getWidgetType
import org.videolan.vlc.widget.utils.WidgetUtils.hasEnoughSpaceForSeek

const val WIDGET_ID = "WIDGET_ID"

class PreferencesWidgets : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: SharedPreferences
    private lateinit var jsonAdapter: JsonAdapter<SavedColors>
    internal lateinit var model: WidgetViewModel
    private lateinit var backgroundPreference: ColorPreferenceCompat
    private lateinit var foregroundPreference: ColorPreferenceCompat
    private lateinit var lightThemePreference: CheckBoxPreference
    private lateinit var showSeek: CheckBoxPreference
    private lateinit var showCover: CheckBoxPreference
    private lateinit var forwardDelay: NumberPickerPreference
    private lateinit var rewindDelay: NumberPickerPreference

    override fun getXml() = R.xml.preferences_widgets

    override fun getTitleId() = R.string.widget_preferences

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moshi: Moshi = Moshi.Builder().build()
        jsonAdapter = moshi.adapter(SavedColors::class.java)
        settings = Settings.getInstance(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundPreference = findPreference("background_color")!!
        foregroundPreference = findPreference("foreground_color")!!
        lightThemePreference = findPreference("widget_light_theme")!!
        showCover = findPreference("widget_show_cover")!!
        showSeek = findPreference("widget_show_seek")!!
        forwardDelay = findPreference("widget_forward_delay")!!
        rewindDelay = findPreference("widget_rewind_delay")!!
        val configurationIcon = findPreference<CheckBoxPreference>("widget_show_configure")!!
        val themePreference = findPreference<ListPreference>("widget_theme")!!
        val typePreference = findPreference<ListPreference>("widget_type")!!

        val id = (arguments?.getInt(WIDGET_ID) ?: -2)
        if (id == -2) throw IllegalStateException("Invalid widget id")
        model = ViewModelProvider(this, WidgetViewModel.Factory(requireActivity(), id))[WidgetViewModel::class.java]
        updateSavedColors(true)
        updateSavedColors(false)
        model.widget.observe(requireActivity()) { widget ->
            if (widget == null) return@observe
            if (!DynamicColors.isDynamicColorAvailable() && widget.theme == 0) {
                widget.theme = 1
                updateWidgetEntity()
            }
            themePreference.value = widget.theme.toString()
            typePreference.value = widget.type.toString()
            backgroundPreference.isVisible = widget.theme != 0
            foregroundPreference.isVisible = widget.theme != 0
            backgroundPreference.saveValue(widget.backgroundColor)
            foregroundPreference.saveValue(widget.foregroundColor)
            findPreference<SeekBarPreference>("opacity")?.value = widget.opacity
            lightThemePreference.isChecked = widget.lightTheme
            lightThemePreference.isVisible = widget.theme != 2
            configurationIcon.isChecked = widget.showConfigure
            val widgetType = getWidgetType(widget)
            val showSeekPrefs = (widgetType == WidgetType.MINI || widgetType == WidgetType.MACRO) && hasEnoughSpaceForSeek(widget, widgetType)
            showSeek.isVisible = showSeekPrefs
            forwardDelay.isVisible = showSeekPrefs
            rewindDelay.isVisible = showSeekPrefs
            showCover.isVisible = widgetType == WidgetType.MINI
        }

        if (!DynamicColors.isDynamicColorAvailable()) {
            themePreference.entryValues = themePreference.entryValues.filter { it != "0"}.toTypedArray()
            themePreference.entries = themePreference.entries.filter { it != getString(R.string.material_you) }.toTypedArray()
        }


    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            "opacity" -> {
                model.widget.value?.opacity = sharedPreferences.getInt(key, 100)
            }
            "background_color" -> {
                val newColor = sharedPreferences.getInt(key, ContextCompat.getColor(requireActivity(), R.color.black))
                saveNewColor(false, newColor)
                model.widget.value?.backgroundColor = newColor
            }
            "foreground_color" -> {
                val newColor = sharedPreferences.getInt(key, ContextCompat.getColor(requireActivity(), R.color.white))
                saveNewColor(true, newColor)
                model.widget.value?.foregroundColor = newColor
            }
            "widget_theme" -> {
                val newValue = sharedPreferences.getString(key, "0")?.toInt() ?: 0
                model.widget.value?.theme = newValue
                backgroundPreference.isVisible = newValue == 2
                foregroundPreference.isVisible = newValue == 2
                lightThemePreference.isVisible = newValue != 2

            }
            "widget_type" -> {
                val newValue = sharedPreferences.getString(key, "0")?.toInt() ?: 0
                model.widget.value?.type = newValue
                model.widget.value?.let {
                    val size = WidgetSizeUtil.getWidgetsSize(requireActivity(), it.widgetId)
                    val minimalSize = WidgetUtils.getMinimalWidgetSize(WidgetUtils.getWidgetType(it))
                    if (size.first < minimalSize.first || size.second < minimalSize.second) {
                        UiTools.snackerConfirm(requireActivity(), getString(R.string.widget_type_error)) { }
                    }

                }
            }
            "widget_light_theme" -> {
                val newValue = sharedPreferences.getBoolean(key, true)
                model.widget.value?.lightTheme = newValue

            }
            "widget_forward_delay" -> {
                val newValue = sharedPreferences.getInt(key, 10)
                model.widget.value?.forwardDelay = newValue

            }
            "widget_rewind_delay" -> {
                val newValue = sharedPreferences.getInt(key, 10)
                model.widget.value?.rewindDelay = newValue

            }
            "widget_show_configure" -> {
                val newValue = sharedPreferences.getBoolean(key, true)
                model.widget.value?.showConfigure = newValue

            }
            "widget_show_seek" -> {
                val newValue = sharedPreferences.getBoolean(key, true)
                model.widget.value?.showSeek = newValue
            }
            "widget_show_cover" -> {
                val newValue = sharedPreferences.getBoolean(key, true)
                model.widget.value?.showCover = newValue
            }
        }
        updateWidgetEntity()
    }

    /**
     * Saves a new color to the shared pref to show it again in the color picker next time
     *
     * @param foreground is this for the foreground color?
     * @param newColor the color to save
     */
    private fun saveNewColor(foreground:Boolean, newColor: Int) {
        val pref = if (foreground)foregroundPreference else backgroundPreference
        val key = if (foreground)WIDGETS_FOREGROUND_LAST_COLORS else WIDGETS_BACKGROUND_LAST_COLORS
        if (!pref.presets.contains(newColor)) {
            val colorListString = settings.getString(key, "")
            val oldColors = if (!colorListString.isNullOrBlank()) ArrayList(jsonAdapter.fromJson(colorListString)!!.colors) else ArrayList()
            oldColors.add(0, newColor)
            val newColors = SavedColors((if (oldColors.size > 5) oldColors.slice(0..5) else oldColors).distinct())
            settings.putSingle(key, jsonAdapter.toJson(newColors))
        }
        updateSavedColors(foreground)
    }

    /**
     * Update a color picker colors with saved ones
     *
     * @param foreground is this for the foreground color?
     */
    private fun updateSavedColors(foreground:Boolean) {
        val colorListString = settings.getString(if (foreground) WIDGETS_FOREGROUND_LAST_COLORS else WIDGETS_BACKGROUND_LAST_COLORS, "")
        val oldColors = if (!colorListString.isNullOrBlank()) ArrayList(jsonAdapter.fromJson(colorListString)!!.colors) else ArrayList()
        val pref = if (foreground)foregroundPreference else backgroundPreference
        pref.presets = pref.presets.toMutableList().apply { addAll(oldColors) }.distinct().toIntArray()

    }

    private fun updateWidgetEntity() {
        model.widget.value?.let { widget ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { WidgetRepository.getInstance(requireActivity()).updateWidget(widget) }
            }
        }
    }

}

class SavedColors(
        val colors: List<Int>,
)