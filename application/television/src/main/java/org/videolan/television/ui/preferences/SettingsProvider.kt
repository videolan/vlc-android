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
import kotlinx.coroutines.flow.StateFlow
import org.videolan.vlc.gui.preferences.search.PreferenceItem

/**
 * Interface defining the contract between the Settings UI and the underlying logic.
 *
 * This provider abstracts away the [SettingsViewModel] to allow for easier testing,
 * better performance in Compose (by reducing prop-drilling), and decoupled previews.
 */
interface SettingsProvider {
    // State
    val categories: StateFlow<List<SettingCategory>>
    val selectedCategory: StateFlow<SettingCategory?>
    val searchQuery: StateFlow<String>
    val searchResults: StateFlow<List<PreferenceItem>>
    val targetSettingKey: StateFlow<String?>
    val isNavigating: StateFlow<Boolean>

    // Value getters
    fun getBooleanValue(item: SettingItem.Toggle): Boolean
    fun getIntValue(item: SettingItem.Slider): Int
    fun getStringValue(item: SettingItem): String?
    fun getColorValue(item: SettingItem.Color): Int
    fun getSummary(item: SettingItem): String?
    fun isEnabled(item: SettingItem): Boolean

    // Actions
    fun selectCategory(category: SettingCategory)
    fun setSearchQuery(query: String)
    fun init(extraEndPoint: Any?)
    fun clearTargetSetting()
    fun onDetailFocused(context: Context)
    fun updateBooleanSetting(context: Context, item: SettingItem.Toggle, value: Boolean)
    fun updateIntSetting(item: SettingItem.Slider, value: Int)
    fun updateStringSetting(context: Context, item: SettingItem, value: String)
    fun updateColorSetting(item: SettingItem.Color, value: Int)
    fun updateColorSetting(key: String, value: Int)
    fun executeAction(context: Context, item: SettingItem.Action)
    fun pickColor(context: Context, item: SettingItem.Color)
}
