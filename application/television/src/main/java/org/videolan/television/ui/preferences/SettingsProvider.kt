/*
 * ************************************************************************
 *  SettingsProvider.kt
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

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.vlc.gui.preferences.search.PreferenceItem

/**
 * Events sent from the logic to the UI to trigger one-shot actions (like navigation).
 */
sealed class SettingsEvent {
    /**
     * Navigates to a specific item in a category.
     * @param categoryTitle The string resource ID of the category title.
     * @param itemKey The preference key of the item to focus.
     */
    data class ScrollToAndFocus(val categoryTitle: Int, val itemKey: String?) : SettingsEvent()
}

/**
 * Interface defining the contract between the Settings UI and the underlying logic.
 *
 * This provider abstracts away the [SettingsViewModel] to allow for easier testing,
 * better performance in Compose (by reducing prop-drilling), and decoupled previews.
 */
interface SettingsProvider {
    // Events
    val navEvents: SharedFlow<SettingsEvent>

    // State
    val categories: StateFlow<List<SettingCategory>>
    val selectedCategory: StateFlow<SettingCategory?>
    val searchQuery: StateFlow<String>
    val searchResults: StateFlow<List<PreferenceItem>>
    val pendingFocusKey: StateFlow<String?>

    // Value getters
    fun getBooleanValue(item: SettingItem.Toggle): Boolean
    fun getIntValue(item: SettingItem.Slider): Int
    fun getStringValue(item: SettingItem): String?
    fun getStringSetValue(item: SettingItem.MultiOptions): Set<String>
    fun getColorValue(item: SettingItem.Color): Int
    fun getSummary(item: SettingItem): String?
    fun isEnabled(item: SettingItem): Boolean

    // Actions
    fun selectCategory(category: SettingCategory)
    fun setSearchQuery(query: String)
    fun init(extraEndPoint: Any?)
    fun onDetailFocused(context: Context)
    fun updateBooleanSetting(context: Context, item: SettingItem.Toggle, value: Boolean)
    fun updateIntSetting(item: SettingItem.Slider, value: Int)
    fun updateStringSetting(context: Context, item: SettingItem, value: String)
    fun updateStringSetSetting(item: SettingItem.MultiOptions, value: Set<String>)
    fun updateColorSetting(item: SettingItem.Color, value: Int)
    fun updateColorSetting(key: String, value: Int)
    fun executeAction(context: Context, item: SettingItem.Action)
    fun pickColor(context: Context, item: SettingItem.Color)
    fun consumeFocusKey()
}

/**
 * A mock implementation of [SettingsProvider] for Compose Previews and testing.
 */
class MockSettingsProvider(
    categories: List<SettingCategory> = emptyList()
) : SettingsProvider {
    private val _navEvents = MutableSharedFlow<SettingsEvent>()
    override val navEvents: SharedFlow<SettingsEvent> = _navEvents.asSharedFlow()

    private val _categories = MutableStateFlow(categories)
    override val categories: StateFlow<List<SettingCategory>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<SettingCategory?>(categories.firstOrNull())
    override val selectedCategory: StateFlow<SettingCategory?> = _selectedCategory.asStateFlow()

    override val searchQuery = MutableStateFlow("")
    override val searchResults = MutableStateFlow(emptyList<PreferenceItem>())
    
    private val _pendingFocusKey = MutableStateFlow<String?>(null)
    override val pendingFocusKey: StateFlow<String?> = _pendingFocusKey.asStateFlow()

    override fun getBooleanValue(item: SettingItem.Toggle) = item.defaultValue
    override fun getIntValue(item: SettingItem.Slider) = item.defaultValue
    override fun getStringValue(item: SettingItem) = (item as? SettingItem.Options)?.defaultValue ?: (item as? SettingItem.Input)?.defaultValue
    override fun getStringSetValue(item: SettingItem.MultiOptions) = item.defaultValues
    override fun getColorValue(item: SettingItem.Color) = item.defaultColor
    override fun getSummary(item: SettingItem) = "Mock Summary"
    override fun isEnabled(item: SettingItem) = true

    override fun selectCategory(category: SettingCategory) { _selectedCategory.value = category }
    override fun setSearchQuery(query: String) {}
    override fun init(extraEndPoint: Any?) {}
    override fun onDetailFocused(context: Context) {}
    override fun updateBooleanSetting(context: Context, item: SettingItem.Toggle, value: Boolean) {}
    override fun updateIntSetting(item: SettingItem.Slider, value: Int) {}
    override fun updateStringSetting(context: Context, item: SettingItem, value: String) {}
    override fun updateStringSetSetting(item: SettingItem.MultiOptions, value: Set<String>) {}
    override fun updateColorSetting(item: SettingItem.Color, value: Int) {}
    override fun updateColorSetting(key: String, value: Int) {}
    override fun executeAction(context: Context, item: SettingItem.Action) {}
    override fun pickColor(context: Context, item: SettingItem.Color) {}
    override fun consumeFocusKey() { _pendingFocusKey.value = null }
}
