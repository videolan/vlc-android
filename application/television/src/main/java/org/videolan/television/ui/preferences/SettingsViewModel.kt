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
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.resources.VLCInstance
import org.videolan.resources.util.startRemoteAccess
import org.videolan.resources.util.stopRemoteAccess
import org.videolan.television.ui.COLOR_PICKER_SELECTED_COLOR
import org.videolan.television.ui.COLOR_PICKER_TITLE
import org.videolan.television.ui.ColorPickerActivity
import org.videolan.tools.BROWSER_SHOW_HIDDEN_FILES
import org.videolan.tools.BitmapCache
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_APP_THEME
import org.videolan.tools.KEY_AUDIO_DIGITAL_OUTPUT
import org.videolan.tools.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.tools.KEY_AUDIO_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_CURRENT_AUDIO
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_ARTIST
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_THUMB
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_TITLE
import org.videolan.tools.KEY_CURRENT_MEDIA
import org.videolan.tools.KEY_CURRENT_MEDIA_RESUME
import org.videolan.tools.KEY_DEBLOCKING
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST_RESUME
import org.videolan.tools.KEY_OPENGL
import org.videolan.tools.KEY_PREFERRED_RESOLUTION
import org.videolan.tools.KEY_SAFE_MODE
import org.videolan.tools.KEY_SET_LOCALE
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_BOLD
import org.videolan.tools.KEY_SUBTITLES_COLOR
import org.videolan.tools.KEY_SUBTITLES_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_OUTLINE
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_COLOR
import org.videolan.tools.KEY_SUBTITLES_SHADOW
import org.videolan.tools.KEY_SUBTITLES_SHADOW_COLOR
import org.videolan.tools.KEY_SUBTITLES_SIZE
import org.videolan.tools.KEY_SUBTITLE_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_SUBTITLE_TEXT_ENCODING
import org.videolan.tools.LocaleUtils
import org.videolan.tools.LocaleUtils.getLocales
import org.videolan.tools.PREF_TV_UI
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.SHOW_VIDEO_THUMBNAILS
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.Settings.isPinCodeSet
import org.videolan.tools.TV_FOLDERS_FIRST
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.LocaleUtil
import org.videolan.vlc.util.deleteAllWatchNext
import java.io.File
import java.io.IOException

const val RESTART_CODE = 10001

/**
 * ViewModel for managing TV settings state and logic.
 *
 * This ViewModel serves as the central point for managing the list of available settings categories
 * and items, handling their visibility based on device capabilities and TV-specific rules,
 * and persisting changes to [android.content.SharedPreferences].
 *
 * @param application The application context.
 */
@SuppressLint("StaticFieldLeak")
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The application context to avoid leaking Activity context.
     */
    private val appContext = application

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
     * Internal state map to track settings values and trigger Compose recomposition.
     */
    private val _settingsValues = mutableStateMapOf<String, Any?>()

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
        val application = getApplication<Application>()
        
        val audioLocalePair = LocaleUtils.getLocalesUsedInProject(
            BuildConfig.TRANSLATION_ARRAY,
            application.getString(R.string.no_track_preference),
            application.getLocales()
        )

        val uiLocalePair = LocaleUtils.getLocalesUsedInProject(
            BuildConfig.TRANSLATION_ARRAY,
            application.getString(R.string.device_default)
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
        _settingsValues[key] = value
        
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
            KEY_ENABLE_REMOTE_ACCESS -> {
                Settings.remoteAccessEnabled.postValue(value)
                if (value) context.startRemoteAccess() else context.stopRemoteAccess()
            }
            KEY_SAFE_MODE -> {
                Settings.safeMode = value && context.isPinCodeSet()
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
        _settingsValues[key] = value
        
        // Handle side effects
        when (key) {
            KEY_PREFERRED_RESOLUTION, KEY_AUDIO_PREFERRED_LANGUAGE,
            KEY_SUBTITLE_PREFERRED_LANGUAGE, KEY_SUBTITLE_TEXT_ENCODING,
            KEY_SUBTITLES_SIZE, KEY_AOUT, KEY_OPENGL, KEY_DEBLOCKING -> {
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
        _settingsValues[key] = value
        viewModelScope.launch { restartLibVLC() }
        refreshCategories()
    }

    /**
     * Applies a subtitle preset.
     */
    private fun applySubtitlePreset(preset: String) {
        val application = getApplication<Application>()
        settings.edit {
            // Reset to defaults first
            putString(KEY_SUBTITLES_SIZE, "16")
            putBoolean(KEY_SUBTITLES_BOLD, false)
            putInt(KEY_SUBTITLES_COLOR, ContextCompat.getColor(application, R.color.white))
            putInt(KEY_SUBTITLES_COLOR_OPACITY, 255)
            putBoolean(KEY_SUBTITLES_BACKGROUND, false)
            putInt(KEY_SUBTITLES_BACKGROUND_COLOR, ContextCompat.getColor(application, R.color.black))
            putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 255)
            putBoolean(KEY_SUBTITLES_SHADOW, true)
            putInt(KEY_SUBTITLES_SHADOW_COLOR, ContextCompat.getColor(application, R.color.black))
            putBoolean(KEY_SUBTITLES_OUTLINE, true)
            putInt(KEY_SUBTITLES_OUTLINE_COLOR, ContextCompat.getColor(application, R.color.black))

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
        // Access _settingsValues[key] to trigger Compose read tracking
        return (_settingsValues[key] as? Boolean) ?: settings.getBoolean(key, defaultValue)
    }

    /**
     * Retrieves the current string value for a specific key.
     *
     * @param key The preference key.
     * @param defaultValue The value to return if the key is not present.
     * @return The current string value, or null if not found and defaultValue is null.
     */
    fun getStringValue(key: String, defaultValue: String? = null): String? {
        return (_settingsValues[key] as? String) ?: settings.getString(key, defaultValue)
    }

    /**
     * Retrieves the current color value.
     */
    fun getColorValue(key: String, defaultColor: Int): Int {
        return (_settingsValues[key] as? Int) ?: settings.getInt(key, defaultColor)
    }

    /**
     * Retrieves the summary for a setting item, handling dynamic values if necessary.
     *
     * @param item The setting item.
     * @return The summary string, or null if none.
     */
    fun getSummary(item: SettingItem): String? {
        val application = getApplication<Application>()
        return when (item.key) {
            KEY_AUDIO_DIGITAL_OUTPUT -> {
                val pt = getBooleanValue(KEY_AUDIO_DIGITAL_OUTPUT, false)
                application.getString(if (pt) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled)
            }
            KEY_AUDIO_PREFERRED_LANGUAGE -> {
                val value = getStringValue(KEY_AUDIO_PREFERRED_LANGUAGE)
                if (value.isNullOrEmpty()) application.getString(R.string.no_track_preference)
                else application.getString(R.string.track_preference, LocaleUtil.getLocaleName(value))
            }
            KEY_SUBTITLE_PREFERRED_LANGUAGE -> {
                val value = getStringValue(KEY_SUBTITLE_PREFERRED_LANGUAGE)
                if (value.isNullOrEmpty()) application.getString(R.string.no_track_preference)
                else application.getString(R.string.track_preference, value)
            }
            "default_sleep_timer" -> {
                val interval = settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
                if (interval == -1L) application.getString(R.string.disabled)
                else {
                    val wait = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
                    val reset = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
                    application.getString(R.string.default_sleep_timer_summary, Tools.millisToString(interval), wait.toString(), reset.toString())
                }
            }
            else -> item.summary?.let { application.getString(it) }
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
            "debug_logs" -> {
                context.startActivity(Intent(context, DebugLogActivity::class.java))
            }
            "clear_history" -> {
                (context as? FragmentActivity)?.let { activity ->
                    val dialog = ConfirmDeleteDialog.newInstance(
                        title = context.getString(R.string.clear_playback_history),
                        description = context.getString(R.string.clear_history_message),
                        buttonText = context.getString(R.string.clear_history)
                    )
                    dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
                    dialog.setListener {
                        Medialibrary.getInstance().clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL)
                        settings.edit {
                            remove(KEY_AUDIO_LAST_PLAYLIST)
                            remove(KEY_MEDIA_LAST_PLAYLIST)
                            remove(KEY_MEDIA_LAST_PLAYLIST_RESUME)
                            remove(KEY_CURRENT_AUDIO)
                            remove(KEY_CURRENT_MEDIA)
                            remove(KEY_CURRENT_MEDIA_RESUME)
                            remove(KEY_CURRENT_AUDIO_RESUME_TITLE)
                            remove(KEY_CURRENT_AUDIO_RESUME_ARTIST)
                            remove(KEY_CURRENT_AUDIO_RESUME_THUMB)
                        }
                    }
                }
            }
            "clear_media_db" -> {
                (context as? FragmentActivity)?.let { activity ->
                    val medialibrary = Medialibrary.getInstance()
                    if (medialibrary.isWorking) {
                        Toast.makeText(context, R.string.settings_ml_block_scan, Toast.LENGTH_LONG).show()
                    } else {
                        val roots = medialibrary.foldersList
                        val dialog = ConfirmDeleteDialog.newInstance(
                            title = context.getString(R.string.clear_media_db),
                            description = context.getString(R.string.clear_media_db_message),
                            buttonText = context.getString(R.string.clear)
                        )
                        dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
                        dialog.setListener {
                            viewModelScope.launch {
                                context.stopService(Intent(context, MediaParsingService::class.java))
                                withContext(Dispatchers.IO) {
                                    medialibrary.clearDatabase(false)
                                    deleteAllWatchNext(context)
                                    try {
                                        context.getExternalFilesDir(null)?.let { dir ->
                                            File(dir.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME).listFiles()
                                                ?.forEach { file -> if (file.isFile) FileUtils.deleteFile(file) }
                                        }
                                        BitmapCache.clear()
                                    } catch (e: IOException) {
                                        Log.e("SettingsViewModel", e.message, e)
                                    }
                                }
                                for (root in roots) {
                                    MedialibraryUtils.addDir(root.removePrefix("file://"), context)
                                }
                            }
                        }
                    }
                }
            }
            "dump_media_db" -> {
                (context as? FragmentActivity)?.let { activity ->
                    if (Medialibrary.getInstance().isWorking) {
                        Toast.makeText(context, R.string.settings_ml_block_scan, Toast.LENGTH_LONG).show()
                    } else {
                        val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + Medialibrary.VLC_MEDIA_DB_NAME)
                        viewModelScope.launch {
                            if (activity.getWritePermission(Uri.fromFile(dst))) {
                                val copied = withContext(Dispatchers.IO) {
                                    val db = File(context.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)
                                    FileUtils.copyFile(db, dst)
                                }
                                Toast.makeText(context, context.getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            "quit_app" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            "modify_pin_code" -> {
                val intent = org.videolan.vlc.gui.PinCodeActivity.getIntent(context, org.videolan.vlc.gui.PinCodeReason.MODIFY)
                (context as? Activity)?.startActivityForResult(intent, 0)
            }
            "remote_access_status" -> {
                context.startActivity(Intent(context, StartActivity::class.java).apply { action = "vlc.remoteaccess.share" })
            }
            "remote_access_info" -> {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply { setClassName(context, REMOTE_ACCESS_ONBOARDING) })
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
     * Factory for creating [SettingsViewModel] with an [Application].
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(application) as T
        }
    }
}
