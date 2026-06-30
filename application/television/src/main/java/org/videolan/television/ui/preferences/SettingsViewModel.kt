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
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager

/**
 * ViewModel for managing TV settings state and logic.
 *
 * This ViewModel serves as the central point for managing the list of available settings categories
 * and items, handling their visibility based on device capabilities and TV-specific rules,
 * and persisting changes to [android.content.SharedPreferences].
 *
 * @param context The context used to access SharedPreferences.
 */
class SettingsViewModel(context: Context) : ViewModel() {

    /**
     * The [android.content.SharedPreferences] instance used for storing and retrieving settings.
     */
    private val settings = Settings.getInstance(context)

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
        // Initial setup - Categories will be populated here
        val generalItems = listOf(
            SettingItem.Action(
                key = "directories",
                title = org.videolan.vlc.R.string.medialibrary_directories,
                summary = org.videolan.vlc.R.string.directories_summary
            ),
            SettingItem.Toggle(
                key = "auto_rescan",
                title = org.videolan.vlc.R.string.auto_rescan,
                summary = org.videolan.vlc.R.string.auto_rescan_summary,
                defaultValue = true
            )
        )

        val initialCategories = listOf(
            SettingCategory(
                title = org.videolan.vlc.R.string.medialibrary,
                items = generalItems
            )
        )

        setCategories(initialCategories)
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
        if (_selectedCategory.value == null || !filtered.contains(_selectedCategory.value)) {
            _selectedCategory.value = filtered.firstOrNull()
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
        }
    }

    /**
     * Updates a boolean setting in [android.content.SharedPreferences].
     *
     * Triggers [refreshCategories] after the update, as some settings changes
     * may affect the visibility of other settings.
     *
     * @param key The preference key.
     * @param value The new boolean value.
     */
    fun updateBooleanSetting(key: String, value: Boolean) {
        settings.edit { putBoolean(key, value) }
        // Some settings might affect visibility of others
        refreshCategories()
    }

    /**
     * Updates a string setting in [android.content.SharedPreferences].
     *
     * @param key The preference key.
     * @param value The new string value.
     */
    fun updateStringSetting(key: String, value: String) {
        settings.edit { putString(key, value) }
        refreshCategories()
    }

    /**
     * Retrieves the current boolean value for a specific key.
     *
     * @param key The preference key.
     * @param defaultValue The value to return if the key is not present.
     * @return The current boolean value.
     */
    fun getBooleanValue(key: String, defaultValue: Boolean): Boolean {
        return settings.getBoolean(key, defaultValue)
    }

    /**
     * Retrieves the current string value for a specific key.
     *
     * @param key The preference key.
     * @param defaultValue The value to return if the key is not present.
     * @return The current string value, or null if not found and defaultValue is null.
     */
    fun getStringValue(key: String, defaultValue: String?): String? {
        return settings.getString(key, defaultValue)
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
