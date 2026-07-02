/*
 * ************************************************************************
 *  SettingsModels.kt
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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Represents the data type of a setting value in storage.
 */
enum class SettingType {
    STRING, INT, BOOLEAN, LONG, COLOR
}

/**
 * Represents a setting category in the TV settings.
 *
 * @property title The string resource ID for the category title.
 * @property items The list of [SettingItem]s contained in this category.
 * @property icon The optional drawable resource ID for the category icon.
 */
data class SettingCategory(
    @param:StringRes val title: Int,
    val items: List<SettingItem>,
    @param:DrawableRes val icon: Int? = null
)

/**
 * Sealed class representing different types of settings items.
 *
 * Each item is associated with a [key] used for UI identification and potentially storage.
 *
 * @property key The unique identifier for this setting in the UI.
 * @property title The string resource ID for the item title.
 * @property summary The optional string resource ID for the item summary/description.
 * @property icon The optional drawable resource ID for the item icon.
 * @property type The storage data type for this setting.
 * @property storageKey The actual key used in SharedPreferences if it differs from [key].
 */
sealed class SettingItem(
    val key: String,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int? = null,
    @param:DrawableRes val icon: Int? = null,
    val type: SettingType = SettingType.STRING,
    val storageKey: String? = null
) {
    /**
     * Gets the effective key to use for SharedPreferences storage.
     */
    fun getEffectiveKey(): String = storageKey ?: key

    /**
     * A visual header to group related settings.
     *
     * @param title The string resource ID for the header title.
     */
    class Header(
        @StringRes title: Int
    ) : SettingItem("header_$title", title)

    /**
     * A simple clickable action that doesn't store a value directly (e.g., "Clear history").
     *
     * @param key The unique identifier for this action.
     * @param title The string resource ID for the action title.
     * @param summary The optional string resource ID for the action summary.
     * @param icon The optional drawable resource ID for the action icon.
     */
    class Action(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
    ) : SettingItem(key, title, summary, icon)

    /**
     * A boolean toggle setting (e.g., "Auto-rescan").
     *
     * @param key The unique identifier for this toggle.
     * @param title The string resource ID for the toggle title.
     * @param summary The optional string resource ID for the toggle summary.
     * @param icon The optional drawable resource ID for the toggle icon.
     * @property defaultValue The default boolean value if none is stored.
     */
    class Toggle(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        val defaultValue: Boolean = true
    ) : SettingItem(key, title, summary, icon, type = SettingType.BOOLEAN)

    /**
     * A setting with multiple options, similar to [androidx.preference.ListPreference].
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param summary The optional string resource ID for the setting summary.
     * @param icon The optional drawable resource ID for the setting icon.
     * @param type The storage data type for this setting.
     * @param storageKey The actual key used in SharedPreferences if it differs from [key].
     * @property entries The list of human-readable option titles.
     * @property entryValues The list of machine-readable option values.
     * @property defaultValue The default string value if none is stored.
     */
    class Options(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        type: SettingType = SettingType.STRING,
        storageKey: String? = null,
        val entries: List<String> = emptyList(),
        val entryValues: List<String> = emptyList(),
        val defaultValue: String? = null
    ) : SettingItem(key, title, summary, icon, type, storageKey)

    /**
     * A text input setting, similar to [androidx.preference.EditTextPreference].
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param summary The optional string resource ID for the setting summary.
     * @param icon The optional drawable resource ID for the setting icon.
     * @param type The storage data type for this setting.
     * @param storageKey The actual key used in SharedPreferences if it differs from [key].
     * @property defaultValue The default string value if none is stored.
     */
    class Input(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        type: SettingType = SettingType.STRING,
        storageKey: String? = null,
        val defaultValue: String? = ""
    ) : SettingItem(key, title, summary, icon, type, storageKey)

    /**
     * A color selection setting.
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param summary The optional string resource ID for the setting summary.
     * @param icon The optional drawable resource ID for the setting icon.
     * @property defaultColor The default color value as an ARGB integer.
     */
    class Color(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        val defaultColor: Int = android.graphics.Color.WHITE
    ) : SettingItem(key, title, summary, icon, type = SettingType.COLOR)
}
