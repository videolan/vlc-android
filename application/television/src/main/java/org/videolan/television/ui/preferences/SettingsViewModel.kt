/*
 * ************************************************************************
 *  SettingsViewModel.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.ui.preferences

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.VLCInstance
import org.videolan.television.ui.COLOR_PICKER_SELECTED_COLOR
import org.videolan.television.ui.COLOR_PICKER_TITLE
import org.videolan.television.ui.ColorPickerActivity
import org.videolan.tools.*
import org.videolan.tools.LocaleUtils.getLocales
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.util.LocaleUtil

/**
 * ViewModel for managing TV settings state and logic.
 *
 * This ViewModel serves as the central point for managing the list of available settings categories
 * and items, handling their visibility based on device capabilities and TV-specific rules,
 * and persisting changes to [android.content.SharedPreferences].
 *
 * @param context The context used to access SharedPreferences and resources.
 */
@SuppressLint("StaticFieldLeak")
class SettingsViewModel(context: Context) : ViewModel() {

    /**
     * The application context to avoid leaking Activity context.
     */
    private val appContext = context.applicationContext ?: context

    /**
     * The [android.content.SharedPreferences] instance used for storing and retrieving settings.
     */
    private val settings = Settings.getInstance(appContext)

    /**
     * Internal flow containing all possible categories and their items.
     */
    private val _allCategories = MutableStateFlow<List<SettingCategory>>(emptyList())

    /**
     * Internal flow of filtered categories based on their visibility.
     */
    private val _categories = MutableStateFlow<List<SettingCategory>>(emptyList())

    /**
     * Public flow of filtered categories that should be displayed in the UI.
     */
    val categories: StateFlow<List<SettingCategory>> = _categories.asStateFlow()

    /**
     * Internal flow of the currently selected category.
     */
    private val _selectedCategory = MutableStateFlow<SettingCategory?>(null)

    /**
     * Public flow of the currently selected category for the UI to observe.
     */
    val selectedCategory: StateFlow<SettingCategory?> = _selectedCategory.asStateFlow()

    init {
        // Load settings from factory
        val initialSettings = SettingsFactory.createSettings(appContext)
        
        // Populate dynamic lists
        val populatedSettings = populateDynamicSettings(initialSettings)
        
        setCategories(populatedSettings)
    }

    /**
     * Populates settings that require dynamic content (like language lists).
     *
     * @param categories The list of categories to populate.
     * @return A new list of categories with populated dynamic content.
     */
    private fun populateDynamicSettings(categories: List<SettingCategory>): List<SettingCategory> {
        val audioLocalePair = LocaleUtils.getLocalesUsedInProject(
            BuildConfig.TRANSLATION_ARRAY,
            appContext.getString(R.string.no_track_preference),
            appContext.getLocales()
        )

        val uiLocalePair = LocaleUtils.getLocalesUsedInProject(
            BuildConfig.TRANSLATION_ARRAY,
            appContext.getString(R.string.device_default)
        )

        return categories.map { category ->
            category.copy(items = category.items.map { item ->
                when {
                    item is SettingItem.Options && item.key == KEY_AUDIO_PREFERRED_LANGUAGE -> {
                        SettingItem.Options(
                            key = item.key,
                            title = item.title,
                            summary = item.summary,
                            icon = item.icon,
                            entries = audioLocalePair.localeEntries.toList(),
                            entryValues = audioLocalePair.localeEntryValues.toList(),
                            defaultValue = item.defaultValue
                        )
                    }
                    item is SettingItem.Options && item.key == KEY_SUBTITLE_PREFERRED_LANGUAGE -> {
                        SettingItem.Options(
                            key = item.key,
                            title = item.title,
                            summary = item.summary,
                            icon = item.icon,
                            entries = audioLocalePair.localeEntries.toList(), // Same logic for subtitles
                            entryValues = audioLocalePair.localeEntryValues.toList(),
                            defaultValue = item.defaultValue
                        )
                    }
                    item is SettingItem.Options && item.key == KEY_SET_LOCALE -> {
                        SettingItem.Options(
                            key = item.key,
                            title = item.title,
                            summary = item.summary,
                            icon = item.icon,
                            entries = uiLocalePair.localeEntries.toList(),
                            entryValues = uiLocalePair.localeEntryValues.toList(),
                            defaultValue = item.defaultValue
                        )
                    }
                    else -> item
                }
            })
        }
    }

    /**
     * Refreshes the list of categories and items based on their visibility.
     *
     * Items are filtered using [PreferenceVisibilityManager.isPreferenceVisible].
     * Categories with no visible items are also filtered out.
     * If the current selection becomes invalid, it is reset to the first available category.
     */
    fun refreshCategories() {
        val filtered = _allCategories.value.map { category ->
            category.copy(items = category.items.filter { item ->
                PreferenceVisibilityManager.isPreferenceVisible(item.key, settings, true)
            })
        }.filter { it.items.isNotEmpty() }
        
        _categories.value = filtered
        if (_selectedCategory.value == null || !filtered.any { it.title == _selectedCategory.value?.title }) {
            _selectedCategory.value = filtered.firstOrNull()
        } else {
            // Update the selected category if items within it changed
            _selectedCategory.value = filtered.first { it.title == _selectedCategory.value?.title }
        }
    }

    /**
     * Sets the list of available categories and triggers a visibility refresh.
     *
     * @param categories The list of [SettingCategory] to manage.
     */
    fun setCategories(categories: List<SettingCategory>) {
        _allCategories.value = categories
        refreshCategories()
    }

    /**
     * Updates the currently selected category.
     *
     * @param category The [SettingCategory] that has been selected.
     */
    fun selectCategory(category: SettingCategory) {
        _selectedCategory.value = category
    }

    /**
     * Updates a boolean setting in [android.content.SharedPreferences].
     *
     * Triggers [refreshCategories] after the update, as some settings changes
     * may affect the visibility of other settings.
     *
     * @param context The context used for activity side effects.
     * @param key The preference key.
     * @param value The new boolean value.
     */
    fun updateBooleanSetting(context: Context, key: String, value: Boolean) {
        settings.edit { putBoolean(key, value) }
        
        // Side effects for boolean settings
        when (key) {
            PREF_TV_UI -> {
                Settings.tvUI = value
                (context as? PreferencesActivity)?.setRestartApp()
            }
            KEY_INCOGNITO -> Settings.incognitoMode = value
            TV_FOLDERS_FIRST -> Settings.tvFoldersFirst = value
            BROWSER_SHOW_HIDDEN_FILES -> Settings.showHiddenFiles = value
            SHOW_VIDEO_THUMBNAILS -> {
                Settings.showVideoThumbs = value
                (context as? PreferencesActivity)?.setRestart()
            }
            KEY_SUBTITLES_BACKGROUND, KEY_SUBTITLES_SHADOW, KEY_SUBTITLES_OUTLINE -> {
                viewModelScope.launch { restartLibVLC() }
            }
        }
        
        refreshCategories()
    }

    /**
     * Updates a string setting in [android.content.SharedPreferences].
     *
     * @param context The context used for activity side effects.
     * @param key The preference key.
     * @param value The new string value.
     */
    fun updateStringSetting(context: Context, key: String, value: String) {
        settings.edit { putString(key, value) }
        
        // Handle side effects
        when (key) {
            KEY_PREFERRED_RESOLUTION, KEY_AUDIO_PREFERRED_LANGUAGE,
            KEY_SUBTITLE_PREFERRED_LANGUAGE, KEY_SUBTITLE_TEXT_ENCODING,
            KEY_SUBTITLES_SIZE -> {
                viewModelScope.launch { restartLibVLC() }
            }
            "subtitles_presets" -> applySubtitlePreset(value)
            KEY_APP_THEME -> (context as? PreferencesActivity)?.setRestartApp()
        }
        
        refreshCategories()
    }

    /**
     * Updates a color setting.
     */
    fun updateColorSetting(key: String, value: Int) {
        settings.edit { putInt(key, value) }
        viewModelScope.launch { restartLibVLC() }
        refreshCategories()
    }

    /**
     * Applies a subtitle preset.
     */
    private fun applySubtitlePreset(preset: String) {
        settings.edit {
            // Reset to defaults first
            putString(KEY_SUBTITLES_SIZE, "16")
            putBoolean(KEY_SUBTITLES_BOLD, false)
            putInt(KEY_SUBTITLES_COLOR, ContextCompat.getColor(appContext, R.color.white))
            putInt(KEY_SUBTITLES_COLOR_OPACITY, 255)
            putBoolean(KEY_SUBTITLES_BACKGROUND, false)
            putInt(KEY_SUBTITLES_BACKGROUND_COLOR, ContextCompat.getColor(appContext, R.color.black))
            putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 255)
            putBoolean(KEY_SUBTITLES_SHADOW, true)
            putInt(KEY_SUBTITLES_SHADOW_COLOR, ContextCompat.getColor(appContext, R.color.black))
            putBoolean(KEY_SUBTITLES_OUTLINE, true)
            putInt(KEY_SUBTITLES_OUTLINE_COLOR, ContextCompat.getColor(appContext, R.color.black))

            when (preset) {
                "1" -> putString(KEY_SUBTITLES_SIZE, "13")
                "2" -> {
                    putString(KEY_SUBTITLES_SIZE, "10")
                    putBoolean(KEY_SUBTITLES_BACKGROUND, true)
                    putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 255)
                    putBoolean(KEY_SUBTITLES_SHADOW, false)
                    putBoolean(KEY_SUBTITLES_OUTLINE, false)
                }
                "3" -> {
                    putBoolean(KEY_SUBTITLES_BACKGROUND, true)
                    putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 128)
                    putBoolean(KEY_SUBTITLES_SHADOW, false)
                }
                "4" -> putInt(KEY_SUBTITLES_COLOR, Color.YELLOW)
                "5" -> {
                    putInt(KEY_SUBTITLES_COLOR, Color.YELLOW)
                    putBoolean(KEY_SUBTITLES_BACKGROUND, true)
                    putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 128)
                    putBoolean(KEY_SUBTITLES_SHADOW, false)
                }
            }
        }
        viewModelScope.launch { restartLibVLC() }
    }

    /**
     * Restarts LibVLC and the media player.
     */
    private suspend fun restartLibVLC() {
        VLCInstance.restart()
        restartMediaPlayer()
    }

    /**
     * Retrieves the current boolean value for a specific key.
     *
     * @param key The preference key.
     * @param defaultValue The value to return if the key is not present.
     * @return The current boolean value.
     */
    fun getBooleanValue(key: String, defaultValue: Boolean = false): Boolean {
        return settings.getBoolean(key, defaultValue)
    }

    /**
     * Retrieves the current string value for a specific key.
     *
     * @param key The preference key.
     * @param defaultValue The value to return if the key is not present.
     * @return The current string value, or null if not found and defaultValue is null.
     */
    fun getStringValue(key: String, defaultValue: String? = null): String? {
        return settings.getString(key, defaultValue)
    }

    /**
     * Retrieves the current color value.
     */
    fun getColorValue(key: String, defaultColor: Int): Int {
        return settings.getInt(key, defaultColor)
    }

    /**
     * Retrieves the summary for a setting item, handling dynamic values if necessary.
     *
     * @param item The setting item.
     * @return The summary string, or null if none.
     */
    fun getSummary(item: SettingItem): String? {
        return when (item.key) {
            KEY_AUDIO_DIGITAL_OUTPUT -> {
                val pt = getBooleanValue(KEY_AUDIO_DIGITAL_OUTPUT, false)
                appContext.getString(if (pt) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled)
            }
            KEY_AUDIO_PREFERRED_LANGUAGE -> {
                val value = getStringValue(KEY_AUDIO_PREFERRED_LANGUAGE)
                if (value.isNullOrEmpty()) appContext.getString(R.string.no_track_preference)
                else appContext.getString(R.string.track_preference, LocaleUtil.getLocaleName(value))
            }
            KEY_SUBTITLE_PREFERRED_LANGUAGE -> {
                val value = getStringValue(KEY_SUBTITLE_PREFERRED_LANGUAGE)
                if (value.isNullOrEmpty()) appContext.getString(R.string.no_track_preference)
                else appContext.getString(R.string.track_preference, value)
            }
            "default_sleep_timer" -> {
                val interval = settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
                if (interval == -1L) appContext.getString(R.string.disabled)
                else {
                    val wait = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
                    val reset = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
                    appContext.getString(R.string.default_sleep_timer_summary, Tools.millisToString(interval), wait, reset)
                }
            }
            else -> item.summary?.let { appContext.getString(it) }
        }
    }

    /**
     * Executes the action associated with a [SettingItem.Action].
     *
     * @param context The context used to start activities or show toasts.
     * @param item The action item to execute.
     */
    fun executeAction(context: Context, item: SettingItem.Action) {
        when (item.key) {
            "directories" -> {
                if (Medialibrary.getInstance().isWorking) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_ml_block_scan),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val intent = Intent(context.applicationContext, SecondaryActivity::class.java)
                    intent.putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
                    context.startActivity(intent)
                    (context as? Activity)?.setResult(RESULT_RESTART)
                }
            }
            "soundfont" -> {
                val filePickerIntent = Intent(context, FilePickerActivity::class.java)
                filePickerIntent.putExtra(KEY_PICKER_TYPE, PickerType.SOUNDFONT.ordinal)
                (context as? Activity)?.startActivityForResult(filePickerIntent, 10000)
            }
            "default_sleep_timer" -> {
                (context as? FragmentActivity)?.let {
                    val dialog = SleepTimerDialog.newInstance(true)
                    dialog.onDismissListener = DialogInterface.OnDismissListener { refreshCategories() }
                    dialog.show(it.supportFragmentManager, "time")
                }
            }
        }
    }

    /**
     * Handles color picking.
     */
    fun pickColor(context: Context, item: SettingItem.Color) {
        val currentColor = getColorValue(item.key, item.defaultColor)
        val intent = Intent(context, ColorPickerActivity::class.java).apply {
            putExtra(COLOR_PICKER_SELECTED_COLOR, currentColor)
            putExtra(COLOR_PICKER_TITLE, context.getString(item.title))
        }
        val requestCode = when (item.key) {
            KEY_SUBTITLES_COLOR -> 1
            KEY_SUBTITLES_SHADOW_COLOR -> 3
            KEY_SUBTITLES_OUTLINE_COLOR -> 4
            KEY_SUBTITLES_BACKGROUND_COLOR -> 2
            else -> 0
        }
        (context as? Activity)?.startActivityForResult(intent, requestCode)
    }

    /**
     * Factory for creating [SettingsViewModel] with a [Context].
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(context) as T
        }
    }
}
