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

const val RESTART_CODE = 10001

// Request Codes
const val REQUEST_CODE_COLOR_SUBTITLES = 1
const val REQUEST_CODE_COLOR_BACKGROUND = 2
const val REQUEST_CODE_COLOR_SHADOW = 3
const val REQUEST_CODE_COLOR_OUTLINE = 4
const val REQUEST_CODE_SOUNDFONT_PICKER = 10000
const val REQUEST_CODE_SETTINGS_RESTORE = 10002
const val REQUEST_CODE_RESTART_APP = 10001

/**
 * Represents the way a slider value is displayed to the user.
 */
enum class SliderValueDisplay {
    RAW, PERCENT
}

/**
 * Represents the data type of a setting value in storage.
 */
enum class SettingType {
    STRING, INT, BOOLEAN, LONG, COLOR, SET_STRING
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
 * @property dependencyKey The key of the preference this item depends on.
 * @property disableIfDependencyIsSet Whether to disable if dependency is true.
 */
sealed class SettingItem(
    val key: String,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int? = null,
    @param:DrawableRes val icon: Int? = null,
    val type: SettingType = SettingType.STRING,
    val storageKey: String? = null,
    val dependencyKey: String? = null,
    val disableIfDependencyIsSet: Boolean = false
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
     * @param dependencyKey The key of the preference this item depends on.
     * @param disableIfDependencyIsSet Whether to disable if dependency is true.
     */
    class Action(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        dependencyKey: String? = null,
        disableIfDependencyIsSet: Boolean = false
    ) : SettingItem(key, title, summary, icon, dependencyKey = dependencyKey, disableIfDependencyIsSet = disableIfDependencyIsSet)

    /**
     * A boolean toggle setting (e.g., "Auto-rescan").
     *
     * @param key The unique identifier for this toggle.
     * @param title The string resource ID for the toggle title.
     * @param summary The optional string resource ID for the toggle summary.
     * @param icon The optional drawable resource ID for the toggle icon.
     * @param dependencyKey The key of the preference this item depends on.
     * @param disableIfDependencyIsSet Whether to disable if dependency is true.
     * @property defaultValue The default boolean value if none is stored.
     */
    class Toggle(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        val defaultValue: Boolean = true,
        dependencyKey: String? = null,
        disableIfDependencyIsSet: Boolean = false
    ) : SettingItem(key, title, summary, icon, type = SettingType.BOOLEAN, dependencyKey = dependencyKey, disableIfDependencyIsSet = disableIfDependencyIsSet)

    /**
     * A setting with multiple options, similar to [androidx.preference.ListPreference].
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param summary The optional string resource ID for the setting summary.
     * @param icon The optional drawable resource ID for the setting icon.
     * @param type The storage data type for this setting.
     * @param storageKey The actual key used in SharedPreferences if it differs from [key].
     * @param dependencyKey The key of the preference this item depends on.
     * @param disableIfDependencyIsSet Whether to disable if dependency is true.
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
        dependencyKey: String? = null,
        disableIfDependencyIsSet: Boolean = false,
        val entries: List<String> = emptyList(),
        val entryValues: List<String> = emptyList(),
        val defaultValue: String? = null
    ) : SettingItem(key, title, summary, icon, type, storageKey, dependencyKey = dependencyKey, disableIfDependencyIsSet = disableIfDependencyIsSet)

    /**
     * A setting with multiple selectable options, similar to [androidx.preference.MultiSelectListPreference].
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param summary The optional string resource ID for the setting summary.
     * @param icon The optional drawable resource ID for the setting icon.
     * @param storageKey The actual key used in SharedPreferences if it differs from [key].
     * @param dependencyKey The key of the preference this item depends on.
     * @param disableIfDependencyIsSet Whether to disable if dependency is true.
     * @property entries The list of human-readable option titles.
     * @property entryValues The list of machine-readable option values.
     * @property defaultValues The default set of machine-readable values.
     */
    class MultiOptions(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        storageKey: String? = null,
        dependencyKey: String? = null,
        disableIfDependencyIsSet: Boolean = false,
        val entries: List<String> = emptyList(),
        val entryValues: List<String> = emptyList(),
        val defaultValues: Set<String> = emptySet()
    ) : SettingItem(key, title, summary, icon, SettingType.SET_STRING, storageKey, dependencyKey = dependencyKey, disableIfDependencyIsSet = disableIfDependencyIsSet)

    /**
     * A text input setting, similar to [androidx.preference.EditTextPreference].
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param summary The optional string resource ID for the setting summary.
     * @param icon The optional drawable resource ID for the setting icon.
     * @param type The storage data type for this setting.
     * @param storageKey The actual key used in SharedPreferences if it differs from [key].
     * @param dependencyKey The key of the preference this item depends on.
     * @param disableIfDependencyIsSet Whether to disable if dependency is true.
     * @property defaultValue The default string value if none is stored.
     */
    class Input(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        type: SettingType = SettingType.STRING,
        storageKey: String? = null,
        dependencyKey: String? = null,
        disableIfDependencyIsSet: Boolean = false,
        val defaultValue: String? = ""
    ) : SettingItem(key, title, summary, icon, type, storageKey, dependencyKey = dependencyKey, disableIfDependencyIsSet = disableIfDependencyIsSet)

    /**
     * A color selection setting.
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param summary The optional string resource ID for the setting summary.
     * @param icon The optional drawable resource ID for the item icon.
     * @param dependencyKey The key of the preference this item depends on.
     * @param disableIfDependencyIsSet Whether to disable if dependency is true.
     * @property defaultColor The default color value as an ARGB integer.
     */
    class Color(
        key: String,
        @StringRes title: Int,
        @StringRes summary: Int? = null,
        @DrawableRes icon: Int? = null,
        val defaultColor: Int = android.graphics.Color.WHITE,
        dependencyKey: String? = null,
        disableIfDependencyIsSet: Boolean = false
    ) : SettingItem(key, title, summary, icon, type = SettingType.COLOR, dependencyKey = dependencyKey, disableIfDependencyIsSet = disableIfDependencyIsSet)

    /**
     * A slider setting for range-based values.
     *
     * @param key The unique identifier for this setting.
     * @param title The string resource ID for the setting title.
     * @param min The minimum value.
     * @param max The maximum value.
     * @param defaultValue The default value.
     * @param valueDisplay The way the value should be displayed to the user.
     * @param dependencyKey The key of the preference this item depends on.
     * @param disableIfDependencyIsSet Whether to disable if dependency is true.
     */
    class Slider(
        key: String,
        @StringRes title: Int,
        val min: Int = 0,
        val max: Int = 255,
        val defaultValue: Int = 255,
        val valueDisplay: SliderValueDisplay = SliderValueDisplay.RAW,
        dependencyKey: String? = null,
        disableIfDependencyIsSet: Boolean = false
    ) : SettingItem(key, title, type = SettingType.INT, dependencyKey = dependencyKey, disableIfDependencyIsSet = disableIfDependencyIsSet)
}
