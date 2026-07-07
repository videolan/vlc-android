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

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.resources.ROOM_DATABASE
import org.videolan.resources.VLCInstance
import org.videolan.resources.util.startRemoteAccess
import org.videolan.resources.util.stopRemoteAccess
import org.videolan.television.di.LocalizedContext
import org.videolan.television.ui.COLOR_PICKER_SELECTED_COLOR
import org.videolan.television.ui.COLOR_PICKER_TITLE
import org.videolan.television.ui.ColorPickerActivity
import org.videolan.tools.BROWSER_SHOW_HIDDEN_FILES
import org.videolan.tools.BitmapCache
import org.videolan.tools.DAV1D_THREAD_NUMBER
import org.videolan.tools.HTTP_USER_AGENT
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_APP_THEME
import org.videolan.tools.KEY_AUDIO_DIGITAL_OUTPUT
import org.videolan.tools.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.tools.KEY_AUDIO_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_DEFAULT
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_ENABLE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_MODE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PREAMP
import org.videolan.tools.KEY_CURRENT_AUDIO
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_ARTIST
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_THUMB
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_TITLE
import org.videolan.tools.KEY_CURRENT_MEDIA
import org.videolan.tools.KEY_CURRENT_MEDIA_RESUME
import org.videolan.tools.KEY_CUSTOM_LIBVLC_OPTIONS
import org.videolan.tools.KEY_DEBLOCKING
import org.videolan.tools.KEY_ENABLE_FRAME_SKIP
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.KEY_ENABLE_VERBOSE_MODE
import org.videolan.tools.KEY_HARDWARE_ACCELERATION
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST_RESUME
import org.videolan.tools.KEY_MEDIA_SEEN
import org.videolan.tools.KEY_OPENGL
import org.videolan.tools.KEY_PREFERRED_RESOLUTION
import org.videolan.tools.KEY_PREFER_SMBV1
import org.videolan.tools.KEY_QUICK_PLAY
import org.videolan.tools.KEY_QUICK_PLAY_DEFAULT
import org.videolan.tools.KEY_SAFE_MODE
import org.videolan.tools.KEY_SET_LOCALE
import org.videolan.tools.KEY_SHOW_HEADERS
import org.videolan.tools.KEY_SUBTITLES_AUTOLOAD
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_BOLD
import org.videolan.tools.KEY_SUBTITLES_COLOR
import org.videolan.tools.KEY_SUBTITLES_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_OUTLINE
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_COLOR
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_SIZE
import org.videolan.tools.KEY_SUBTITLES_SHADOW
import org.videolan.tools.KEY_SUBTITLES_SHADOW_COLOR
import org.videolan.tools.KEY_SUBTITLES_SHADOW_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_SIZE
import org.videolan.tools.KEY_SUBTITLE_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_SUBTITLE_TEXT_ENCODING
import org.videolan.tools.LocaleUtils
import org.videolan.tools.LocaleUtils.getLocales
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.PREF_TV_UI
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.SCREEN_ORIENTATION
import org.videolan.tools.SHOW_VIDEO_THUMBNAILS
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.Settings.isPinCodeSet
import org.videolan.tools.TV_FOLDERS_FIRST
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.FeatureTouchOnlyWarningDialog
import org.videolan.vlc.gui.dialogs.PermissionListDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.gui.preferences.EXTRA_PREF_END_POINT
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager
import org.videolan.vlc.gui.preferences.search.PreferenceItem
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.util.AutoUpdate
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.LocaleUtil
import org.videolan.vlc.util.deleteAllWatchNext
import java.io.File
import java.io.IOException
import javax.inject.Inject

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
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    @LocalizedContext private val localizedContext: Context,
    private val settings: SharedPreferences
) : AndroidViewModel(application), SettingsProvider {

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
    override val categories: StateFlow<List<SettingCategory>> = _categories.asStateFlow()

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
    override val selectedCategory: StateFlow<SettingCategory?> = _selectedCategory.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    override val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _targetSettingKey = MutableStateFlow<String?>(null)
    override val targetSettingKey: StateFlow<String?> = _targetSettingKey.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    override val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Cache for all searchable settings items.
     */
    private var allPreferenceItems: List<PreferenceItem> = emptyList()

    /**
     * Reactive search results based on the current query.
     */
    override val searchResults: StateFlow<List<PreferenceItem>> = _searchQuery
        .combine(_allCategories) { query, _ ->
            if (query.length < 2) emptyList()
            else {
                if (allPreferenceItems.isEmpty()) {
                    allPreferenceItems = PreferenceParser.parsePreferences(localizedContext)
                }
                allPreferenceItems.filter {
                    it.title.contains(query, ignoreCase = true) || 
                    it.summary.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    override fun init(extraEndPoint: Any?) {
        if (extraEndPoint == null) return
        val allCategories = _allCategories.value
        val category = when (extraEndPoint) {
            is Int -> {
                when (extraEndPoint) {
                    R.xml.preferences_remote_access -> allCategories.find { it.title == R.string.remote_access }
                    R.xml.preferences_video -> allCategories.find { it.title == R.string.video_prefs_category }
                    R.xml.preferences_audio -> allCategories.find { it.title == R.string.audio_prefs_category }
                    R.xml.preferences_subtitles -> allCategories.find { it.title == R.string.subtitles_prefs_category }
                    R.xml.preferences_ui -> allCategories.find { it.title == R.string.interface_prefs_screen }
                    else -> null
                }
            }
            is String -> {
                // If it's a string, it might be a preference key. 
                // We find the category containing this key.
                allCategories.find { cat -> cat.items.any { it.key == extraEndPoint } }
            }
            is PreferenceItem -> {
                _isNavigating.value = true
                _targetSettingKey.value = extraEndPoint.key
                // First update categories to make sure the target category is available
                refreshCategories()
                val targetCategory = _allCategories.value.find { cat -> cat.items.any { it.key == extraEndPoint.key } }
                // Directly set the value
                targetCategory?.let { _selectedCategory.value = it }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(200)
                    _isNavigating.value = false
                }
                null
            }
            else -> null
        }
        category?.let { selectCategory(it) }
    }

    override fun clearTargetSetting() {
        _targetSettingKey.value = null
    }

    init {
        // Load settings from factory
        val initialSettings = SettingsFactory.createSettings(localizedContext)
        
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
            localizedContext.getString(R.string.no_track_preference),
            application.getLocales()
        )

        val uiLocalePair = LocaleUtils.getLocalesUsedInProject(
            BuildConfig.TRANSLATION_ARRAY,
            localizedContext.getString(R.string.device_default)
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
            val visibleItems = category.items.filter { item ->
                // Headers don't have visibility rules themselves, they depend on items under them
                if (item is SettingItem.Header) true
                else PreferenceVisibilityManager.isPreferenceVisible(item.key, settings, true)
            }
            
            // Further filter to remove headers that have no visible items following them 
            // until the next header or the end of the list.
            val cleanedItems = mutableListOf<SettingItem>()
            var lastHeader: SettingItem.Header? = null
            
            for (item in visibleItems) {
                if (item is SettingItem.Header) {
                    lastHeader = item
                } else {
                    // We found a non-header item, so if we had a pending header, add it now
                    lastHeader?.let {
                        cleanedItems.add(it)
                        lastHeader = null
                    }
                    cleanedItems.add(item)
                }
            }
            
            category.copy(items = cleanedItems)
        }.filter { category ->
            // Category is visible only if it has at least one actual setting (non-header)
            // Or if it is the Search category (which has no items in the list)
            category.title == R.string.search || category.items.any { it !is SettingItem.Header }
        }
        
        _categories.value = filtered
        val currentSelection = _selectedCategory.value
        if (currentSelection == null || !filtered.any { it.title == currentSelection.title }) {
            // Default selection: General (not Search)
            val defaultCategory = filtered.find { it.title == R.string.general } ?: filtered.firstOrNull()
            defaultCategory?.let { selectCategory(it) }
        } else {
            // Update the selected category if items within it changed
            val updatedSelection = filtered.first { it.title == currentSelection.title }
            _selectedCategory.value = updatedSelection
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
    override fun selectCategory(category: SettingCategory) {
        if (_isNavigating.value) {
            return
        }
        _selectedCategory.value = category
    }

    /**
     * Called when the detail pane (second pane) receives focus.
     * Triggers side effects like the remote access onboarding.
     */
    override fun onDetailFocused(context: Context) {
        val category = _selectedCategory.value ?: return
        
        // Show remote access onboarding if needed
        if (category.title == R.string.remote_access) {
            if (!settings.getBoolean(REMOTE_ACCESS_ONBOARDING, false)) {
                settings.edit { putBoolean(REMOTE_ACCESS_ONBOARDING, true) }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setClassName(context, REMOTE_ACCESS_ONBOARDING)
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Updates a boolean setting in [android.content.SharedPreferences].
     *
     * Triggers [refreshCategories] after the update, as some settings changes
     * may affect the visibility of other settings.
     *
     * @param context The context used for activity side effects.
     * @param item The toggle setting item.
     * @param value The new boolean value.
     */
    override fun updateBooleanSetting(context: Context, item: SettingItem.Toggle, value: Boolean) {
        val key = item.getEffectiveKey()
        
        // Side effects for boolean settings
        when (item.key) {
            PREF_TV_UI -> {
                if (!value && Settings.device.isTv) {
                    (context as? FragmentActivity)?.let { activity ->
                        val dialog = FeatureTouchOnlyWarningDialog.newInstance {
                            Settings.tvUI = false
                            settings.edit { putBoolean(PREF_TV_UI, false) }
                            _settingsValues[PREF_TV_UI] = false
                            (context as? PreferencesActivity)?.setRestartApp()
                            refreshCategories()
                        }
                        dialog.show(activity.supportFragmentManager, FeatureTouchOnlyWarningDialog::class.simpleName)
                    }
                    return
                }
                Settings.tvUI = value
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                (context as? PreferencesActivity)?.setRestartApp()
            }
            KEY_INCOGNITO -> {
                Settings.incognitoMode = value
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
            }
            TV_FOLDERS_FIRST -> {
                Settings.tvFoldersFirst = value
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
            }
            BROWSER_SHOW_HIDDEN_FILES -> {
                Settings.showHiddenFiles = value
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
            }
            SHOW_VIDEO_THUMBNAILS -> {
                Settings.showVideoThumbs = value
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                (context as? PreferencesActivity)?.setRestart()
            }
            KEY_SHOW_HEADERS -> {
                Settings.showHeaders = value
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                (context as? PreferencesActivity)?.setRestart()
            }
            KEY_SUBTITLES_BACKGROUND, KEY_SUBTITLES_SHADOW, KEY_SUBTITLES_OUTLINE, KEY_ENABLE_FRAME_SKIP,
            KEY_SUBTITLES_AUTOLOAD, KEY_AUDIO_REPLAY_GAIN_ENABLE, KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION,
            KEY_ENABLE_VERBOSE_MODE -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                viewModelScope.launch { restartLibVLC() }
            }
            KEY_QUICK_PLAY -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                if (!value) {
                    settings.edit { putBoolean(KEY_QUICK_PLAY_DEFAULT, false) }
                    _settingsValues[KEY_QUICK_PLAY_DEFAULT] = false
                }
            }
            KEY_PREFER_SMBV1 -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                viewModelScope.launch { VLCInstance.restart() }
            }
            KEY_ENABLE_REMOTE_ACCESS -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                Settings.remoteAccessEnabled.postValue(value)
                if (value) context.startRemoteAccess() else context.stopRemoteAccess()
            }
            KEY_SAFE_MODE -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                Settings.safeMode = value && context.isPinCodeSet()
            }
            KEY_MEDIA_SEEN -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                (context as? PreferencesActivity)?.setRestart()
            }
            PLAYBACK_HISTORY -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
                if (!value) Medialibrary.getInstance().clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL)
            }
            else -> {
                settings.edit { putBoolean(key, value) }
                _settingsValues[key] = value
            }
        }
        
        refreshCategories()
    }

    /**
     * Updates an integer setting (like sliders) in [android.content.SharedPreferences].
     */
    override fun updateIntSetting(item: SettingItem.Slider, value: Int) {
        val key = item.getEffectiveKey()
        settings.edit { putInt(key, value) }
        _settingsValues[key] = value

        // Handle side effects
        when (item.key) {
            KEY_SUBTITLES_COLOR_OPACITY, KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY,
            KEY_SUBTITLES_SHADOW_COLOR_OPACITY, KEY_SUBTITLES_OUTLINE_COLOR_OPACITY -> {
                viewModelScope.launch { restartLibVLC() }
            }
        }

        refreshCategories()
    }

    /**
     * Updates a string setting in [android.content.SharedPreferences].
     *
     * @param context The context used for activity side effects.
     * @param item The setting item.
     * @param value The new string value.
     */
    override fun updateStringSetting(context: Context, item: SettingItem, value: String) {
        val key = item.getEffectiveKey()
        
        // Handle specialized storage types based on SettingType
        when (item.type) {
            SettingType.INT -> {
                val intValue = value.toIntOrNull() ?: 0
                settings.edit { putInt(key, intValue) }
                _settingsValues[key] = intValue
            }
            SettingType.LONG -> {
                val longValue = value.toLongOrNull() ?: 0L
                settings.edit { putLong(key, longValue) }
                _settingsValues[key] = longValue
            }
            SettingType.BOOLEAN -> {
                val boolValue = value.toBoolean()
                settings.edit { putBoolean(key, boolValue) }
                _settingsValues[key] = boolValue
            }
            else -> {
                settings.edit { putString(key, value) }
                _settingsValues[key] = value
            }
        }

        // Handle side effects based on UI key
        when (item.key) {
            KEY_PREFERRED_RESOLUTION, KEY_AUDIO_PREFERRED_LANGUAGE,
            KEY_SUBTITLE_PREFERRED_LANGUAGE, KEY_SUBTITLE_TEXT_ENCODING,
            KEY_SUBTITLES_SIZE, KEY_AOUT, KEY_OPENGL, KEY_DEBLOCKING,
            KEY_AUDIO_REPLAY_GAIN_MODE, KEY_AUDIO_REPLAY_GAIN_PREAMP, KEY_AUDIO_REPLAY_GAIN_DEFAULT,
            KEY_HARDWARE_ACCELERATION, KEY_SUBTITLES_OUTLINE_SIZE,
            KEY_SUBTITLES_COLOR_OPACITY, KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY,
            KEY_SUBTITLES_SHADOW_COLOR_OPACITY, KEY_SUBTITLES_OUTLINE_COLOR_OPACITY,
            "network_caching", DAV1D_THREAD_NUMBER, HTTP_USER_AGENT -> {
                viewModelScope.launch { restartLibVLC() }
            }
            "subtitles_presets" -> applySubtitlePreset(value)
            KEY_APP_THEME -> (context as? PreferencesActivity)?.setRestartApp()
            KEY_SET_LOCALE -> {
                AppContextProvider.setLocale(value)
                (context as? PreferencesActivity)?.setRestartApp()
            }
            SCREEN_ORIENTATION -> (context as? Activity)?.requestedOrientation = value.toInt()
            KEY_CUSTOM_LIBVLC_OPTIONS -> {
                viewModelScope.launch {
                    try {
                        restartLibVLC()
                    } catch (e: IllegalStateException) {
                        Log.e("SettingsViewModel", "Invalid custom options", e)
                    }
                }
            }
        }
        
        refreshCategories()
    }

    /**
     * Updates a color setting by its key.
     *
     * Used mainly for [onActivityResult] where only the key/request code is known.
     */
    override fun updateColorSetting(key: String, value: Int) {
        val item = _allCategories.value.flatMap { it.items }.filterIsInstance<SettingItem.Color>().firstOrNull { it.key == key }
        if (item != null) {
            updateColorSetting(item, value)
        } else {
            // Fallback if item not found in categories
            settings.edit { putInt(key, value) }
            _settingsValues[key] = value
            viewModelScope.launch { restartLibVLC() }
            refreshCategories()
        }
    }

    /**
     * Updates a color setting.
     *
     * @param item The color setting item.
     * @param value The new color value as an ARGB integer.
     */
    override fun updateColorSetting(item: SettingItem.Color, value: Int) {
        val key = item.getEffectiveKey()
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
            putInt(KEY_SUBTITLES_SHADOW_COLOR_OPACITY, 128)
            putBoolean(KEY_SUBTITLES_OUTLINE, true)
            putInt(KEY_SUBTITLES_OUTLINE_COLOR, ContextCompat.getColor(application, R.color.black))
            putInt(KEY_SUBTITLES_OUTLINE_COLOR_OPACITY, 255)

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
     * @return The current boolean value.
     */
    fun getBooleanValue(key: String): Boolean {
        return (_settingsValues[key] as? Boolean) ?: settings.getBoolean(key, false)
    }

    /**
     * Checks if a setting item is enabled based on its dependency.
     *
     * @param item The setting item to check.
     * @return True if the item is enabled.
     */
    override fun isEnabled(item: SettingItem): Boolean {
        val depKey = item.dependencyKey ?: return true
        val depValue = getBooleanValue(depKey)
        return if (item.disableIfDependencyIsSet) !depValue else depValue
    }

    /**
     * Retrieves the current integer value for a specific slider item.
     *
     * @param item The slider setting item.
     * @return The current integer value.
     */
    override fun getIntValue(item: SettingItem.Slider): Int {
        val key = item.getEffectiveKey()
        return (_settingsValues[key] as? Int) ?: settings.getInt(key, item.defaultValue)
    }

    /**
     * Retrieves the current boolean value for a specific toggle item.
     *
     * @param item The toggle setting item.
     * @return The current boolean value.
     */
    override fun getBooleanValue(item: SettingItem.Toggle): Boolean {
        val key = item.getEffectiveKey()
        return (_settingsValues[key] as? Boolean) ?: settings.getBoolean(key, item.defaultValue)
    }

    /**
     * Retrieves the current string value for a specific setting item.
     *
     * Handles type-safe conversion from stored primitives (Int, Long) if required by [item.type].
     *
     * @param item The setting item.
     * @return The current string representation of the value.
     */
    override fun getStringValue(item: SettingItem): String? {
        val key = item.getEffectiveKey()
        val reactiveValue = _settingsValues[key]
        if (reactiveValue != null) return reactiveValue.toString()

        val defaultValue = when (item) {
            is SettingItem.Options -> item.defaultValue
            is SettingItem.Input -> item.defaultValue
            else -> null
        }

        return when (item.type) {
            SettingType.INT -> settings.getInt(key, defaultValue?.toIntOrNull() ?: 0).toString()
            SettingType.LONG -> settings.getLong(key, defaultValue?.toLongOrNull() ?: 0L).toString()
            SettingType.BOOLEAN -> settings.getBoolean(key, defaultValue?.toBoolean() ?: false).toString()
            else -> settings.getString(key, defaultValue)
        }
    }

    /**
     * Retrieves the current color value for a specific color item.
     *
     * @param item The color setting item.
     * @return The current color value as an ARGB integer.
     */
    override fun getColorValue(item: SettingItem.Color): Int {
        val key = item.getEffectiveKey()
        return (_settingsValues[key] as? Int) ?: settings.getInt(key, item.defaultColor)
    }

    /**
     * Retrieves the summary for a setting item, handling dynamic values if necessary.
     *
     * @param item The setting item.
     * @return The summary string, or null if none.
     */
    override fun getSummary(item: SettingItem): String? {
        return when (item.key) {
            KEY_AUDIO_DIGITAL_OUTPUT -> {
                val pt = if (item is SettingItem.Toggle) getBooleanValue(item) else false
                localizedContext.getString(if (pt) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled)
            }
            KEY_AUDIO_PREFERRED_LANGUAGE -> {
                val value = getStringValue(item)
                if (value.isNullOrEmpty()) localizedContext.getString(R.string.no_track_preference)
                else localizedContext.getString(R.string.track_preference, LocaleUtil.getLocaleName(value))
            }
            KEY_SUBTITLE_PREFERRED_LANGUAGE -> {
                val value = getStringValue(item)
                if (value.isNullOrEmpty()) localizedContext.getString(R.string.no_track_preference)
                else localizedContext.getString(R.string.track_preference, value)
            }
            "default_sleep_timer" -> {
                val interval = settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
                if (interval == -1L) localizedContext.getString(R.string.disabled)
                else {
                    val wait = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
                    val reset = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
                    localizedContext.getString(R.string.default_sleep_timer_summary, Tools.millisToString(interval), wait.toString(), reset.toString())
                }
            }
            else -> if (item is SettingItem.Slider) {
                val value = getIntValue(item)
                if (item.valueDisplay == SliderValueDisplay.PERCENT) {
                    "${(value * 100 / item.max)}%"
                } else {
                    value.toString()
                }
            } else {
                item.summary?.let { localizedContext.getString(it) }
            }
        }
    }

    /**
     * Executes the action associated with a [SettingItem.Action].
     *
     * @param context The context used to start activities or show toasts.
     * @param item The action item to execute.
     */
    override fun executeAction(context: Context, item: SettingItem.Action) {
        when (item.key) {
            "optional_features" -> {
                val intent = Intent(context, PreferencesActivity::class.java)
                intent.putExtra(EXTRA_PREF_END_POINT, R.xml.preferences_optional)
                context.startActivity(intent)
            }
            "export_settings" -> {
                (context as? FragmentActivity)?.let { activity ->
                    val dst = File(org.videolan.resources.AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + org.videolan.resources.EXPORT_SETTINGS_FILE)
                    viewModelScope.launch {
                        if (activity.getWritePermission(Uri.fromFile(dst))) {
                            val success = withContext(Dispatchers.IO) {
                                try {
                                    PreferenceParser.exportPreferences(activity, dst)
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            Toast.makeText(context, context.getString(if (success) R.string.export_settings_success else R.string.export_settings_failure), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            "restore_settings" -> {
                val filePickerIntent = Intent(context, FilePickerActivity::class.java)
                filePickerIntent.putExtra(KEY_PICKER_TYPE, PickerType.SETTINGS.ordinal)
                (context as? Activity)?.startActivityForResult(filePickerIntent, 10002)
            }
            "nightly_install" -> {
                android.app.AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.install_nightly))
                    .setMessage(context.getString(R.string.install_nightly_alert))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        viewModelScope.launch {
                            AutoUpdate.checkUpdate((context as Activity).application, true) { url, date ->
                                // On TV we might just want to trigger the download or show a specific dialog
                                // Replicating mobile logic for now
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
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
            "dump_app_db" -> {
                (context as? FragmentActivity)?.let { activity ->
                    val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + ROOM_DATABASE)
                    viewModelScope.launch {
                        if (activity.getWritePermission(Uri.fromFile(dst))) {
                            val copied = withContext(Dispatchers.IO) {
                                val db = File(context.getDir("db", Context.MODE_PRIVATE).parent!! + "/databases")
                                val files = db.listFiles()?.map { it.path }?.toTypedArray()
                                if (files == null) false else FileUtils.zip(files, dst.path)
                            }
                            Toast.makeText(context, context.getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            "clear_app_data" -> {
                (context as? FragmentActivity)?.let { activity ->
                    val dialog = ConfirmDeleteDialog.newInstance(
                        title = context.getString(R.string.clear_app_data),
                        description = context.getString(R.string.clear_app_data_message),
                        buttonText = context.getString(R.string.clear)
                    )
                    dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
                    dialog.setListener {
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.clearApplicationUserData()
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
            "permissions" -> {
                (context as? FragmentActivity)?.let {
                    PermissionListDialog.newInstance().show(it.supportFragmentManager, "PermissionListDialog")
                }
            }
        }
    }

    /**
     * Handles color picking.
     */
    override fun pickColor(context: Context, item: SettingItem.Color) {
        val currentColor = getColorValue(item)
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
}
