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
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AppContextProvider
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.resources.VLCInstance
import org.videolan.resources.util.startRemoteAccess
import org.videolan.resources.util.stopRemoteAccess
import org.videolan.television.di.LocalizedContext
import org.videolan.television.ui.COLOR_PICKER_SELECTED_COLOR
import org.videolan.television.ui.COLOR_PICKER_TITLE
import org.videolan.television.ui.ColorPickerActivity
import org.videolan.tools.*
import org.videolan.tools.LocaleUtils.getLocales
import org.videolan.tools.Settings.isPinCodeSet
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason
import org.videolan.vlc.gui.dialogs.FeatureTouchOnlyWarningDialog
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager
import org.videolan.vlc.gui.preferences.search.PreferenceItem
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.util.LocaleUtil
import javax.inject.Inject

/**
 * ViewModel for managing TV settings state and logic.
 *
 * This ViewModel serves as the central point for managing the list of available settings categories
 * and items, handling their visibility based on device capabilities and TV-specific rules,
 * and persisting changes to [SharedPreferences].
 *
 * @param application The application context.
 * @param localizedContext Localized context for strings.
 * @param settings SharedPreferences instance.
 * @param actionHandler Handler for complex setting actions.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    @LocalizedContext private val localizedContext: Context,
    private val settings: SharedPreferences,
    private val actionHandler: SettingsActionHandler
) : AndroidViewModel(application), SettingsProvider {

    /**
     * Internal flow for navigation events.
     */
    private val _navEvents = MutableSharedFlow<SettingsEvent>()

    /**
     * Public flow for the UI to observe one-shot navigation events.
     */
    override val navEvents: SharedFlow<SettingsEvent> = _navEvents.asSharedFlow()

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

    private val _searchQuery = MutableStateFlow("")
    override val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pendingFocusKey = MutableStateFlow<String?>(null)
    override val pendingFocusKey: StateFlow<String?> = _pendingFocusKey.asStateFlow()

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
        viewModelScope.launch {
            when (extraEndPoint) {
                is Int -> {
                    val category = when (extraEndPoint) {
                        R.xml.preferences_remote_access -> allCategories.find { it.title == R.string.remote_access }
                        R.xml.preferences_video -> allCategories.find { it.title == R.string.video_prefs_category }
                        R.xml.preferences_audio -> allCategories.find { it.title == R.string.audio_prefs_category }
                        R.xml.preferences_subtitles -> allCategories.find { it.title == R.string.subtitles_prefs_category }
                        R.xml.preferences_ui -> allCategories.find { it.title == R.string.interface_prefs_screen }
                        else -> null
                    }
                    category?.let { 
                        _pendingFocusKey.value = null
                        _navEvents.emit(SettingsEvent.ScrollToAndFocus(it.title, null)) 
                    }
                }
                is String -> {
                    // If it's a string, it might be a preference key. 
                    // We find the category containing this key.
                    val category = allCategories.find { cat -> cat.items.any { it.key == extraEndPoint } }
                    category?.let { 
                        _pendingFocusKey.value = extraEndPoint
                        _navEvents.emit(SettingsEvent.ScrollToAndFocus(it.title, extraEndPoint)) 
                    }
                }
                is PreferenceItem -> {
                    // Find the category containing this key.
                    val targetCategory = allCategories.find { cat -> cat.items.any { it.key == extraEndPoint.key } }
                    targetCategory?.let { category ->
                        _pendingFocusKey.value = extraEndPoint.key
                        _navEvents.emit(SettingsEvent.ScrollToAndFocus(category.title, extraEndPoint.key))
                    }
                }
            }
        }
    }

    override fun consumeFocusKey() {
        _pendingFocusKey.value = null
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
            category.copy(
                items = category.items.map { item ->
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
            (category.title == R.string.search) || category.items.any { it !is SettingItem.Header }
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
        _selectedCategory.value = category
    }

    /**
     * Called when the detail pane (second pane) receives focus.
     * Triggers side effects like the remote access onboarding.
     */
    override fun onDetailFocused(context: Context) {
        val category = _selectedCategory.value ?: return
        
        when (category.title) {
            R.string.remote_access -> {
                if (!settings.getBoolean(REMOTE_ACCESS_ONBOARDING, false)) {
                    settings.edit { putBoolean(REMOTE_ACCESS_ONBOARDING, true) }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setClassName(context, REMOTE_ACCESS_ONBOARDING)
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            R.string.parental_control -> {
                if (!context.isPinCodeSet()) {
                    val intent = PinCodeActivity.getIntent(context, PinCodeReason.FIRST_CREATION)
                    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }

    /**
     * Updates a boolean setting in [SharedPreferences].
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
                Settings.safeMode = value && getApplication<Application>().isPinCodeSet()
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
     * Updates an integer setting (like sliders) in [SharedPreferences].
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
     * Updates a set of string settings in [SharedPreferences].
     *
     * @param item The multi-options setting item.
     * @param value The new set of string values.
     */
    override fun updateStringSetSetting(item: SettingItem.MultiOptions, value: Set<String>) {
        val key = item.getEffectiveKey()
        settings.edit { putStringSet(key, value) }
        _settingsValues[key] = value
        refreshCategories()
    }

    /**
     * Updates a string setting in [SharedPreferences].
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
            KEY_ACTION_SUBTITLES_PRESETS -> applySubtitlePreset(value)
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
     * Retrieves the current set of string values for a multi-options item.
     *
     * @param item The multi-options setting item.
     * @return The current set of string values.
     */
    override fun getStringSetValue(item: SettingItem.MultiOptions): Set<String> {
        val key = item.getEffectiveKey()
        @Suppress("UNCHECKED_CAST")
        return (_settingsValues[key] as? Set<String>) ?: settings.getStringSet(key, item.defaultValues) ?: emptySet()
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
        reactiveValue?.let { return it.toString() }

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
                val pt = if (item is SettingItem.Toggle) getBooleanValue(item) else getBooleanValue(KEY_AUDIO_DIGITAL_OUTPUT)
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
            KEY_ACTION_SLEEP_TIMER -> {
                val interval = (_settingsValues[SLEEP_TIMER_DEFAULT_INTERVAL] as? Long) ?: settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
                if (interval == -1L) localizedContext.getString(R.string.disabled)
                else {
                    val wait = (_settingsValues[SLEEP_TIMER_DEFAULT_WAIT] as? Boolean) ?: settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
                    val reset = (_settingsValues[SLEEP_TIMER_DEFAULT_RESET_INTERACTION] as? Boolean) ?: settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
                    localizedContext.getString(R.string.default_sleep_timer_summary, Tools.millisToString(interval), wait.toString(), reset.toString())
                }
            }
            else -> when (item) {
                is SettingItem.Slider -> {
                    val value = getIntValue(item)
                    if (item.valueDisplay == SliderValueDisplay.PERCENT) {
                        "${(value * 100 / item.max)}%"
                    } else {
                        value.toString()
                    }
                }
                is SettingItem.MultiOptions -> {
                    val values = getStringSetValue(item)
                    val selectedEntries = item.entryValues.mapIndexedNotNull { index, value ->
                        if (values.contains(value)) item.entries[index] else null
                    }
                    if (selectedEntries.isEmpty()) "-" else selectedEntries.joinToString(", ")
                }
                else -> item.summary?.let { localizedContext.getString(it) }
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
        actionHandler.execute(
            context = context,
            scope = viewModelScope,
            item = item,
            onRefresh = {
                // Trigger reactive refresh of sleep timer keys
                _settingsValues[SLEEP_TIMER_DEFAULT_INTERVAL] = settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
                _settingsValues[SLEEP_TIMER_DEFAULT_WAIT] = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
                _settingsValues[SLEEP_TIMER_DEFAULT_RESET_INTERACTION] = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
                refreshCategories()
            }
        )
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
            KEY_SUBTITLES_COLOR -> REQUEST_CODE_COLOR_SUBTITLES
            KEY_SUBTITLES_SHADOW_COLOR -> REQUEST_CODE_COLOR_SHADOW
            KEY_SUBTITLES_OUTLINE_COLOR -> REQUEST_CODE_COLOR_OUTLINE
            KEY_SUBTITLES_BACKGROUND_COLOR -> REQUEST_CODE_COLOR_BACKGROUND
            else -> 0
        }
        (context as? Activity)?.startActivityForResult(intent, requestCode)
    }
}
