/*
 * ************************************************************************
 *  SettingsFactory.kt
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

import org.videolan.tools.KEY_PREFERRED_RESOLUTION
import org.videolan.vlc.R

/**
 * Factory class for defining the settings categories and items used in the TV UI.
 *
 * This class decouples the declarative structure of settings from the state management logic.
 */
object SettingsFactory {

    /**
     * Returns the full list of setting categories and their items.
     *
     * Note: Items will be filtered by visibility in the ViewModel.
     *
     * @return A list of [SettingCategory].
     */
    fun createSettings(): List<SettingCategory> {
        return listOf(
            createMedialibraryCategory(),
            createVideoCategory()
        )
    }

    /**
     * Defines the Medialibrary settings category.
     */
    private fun createMedialibraryCategory() = SettingCategory(
        title = R.string.medialibrary,
        icon = R.drawable.ic_folder,
        items = listOf(
            SettingItem.Action(
                key = "directories",
                title = R.string.medialibrary_directories,
                summary = R.string.directories_summary
            ),
            SettingItem.Toggle(
                key = "auto_rescan",
                title = R.string.auto_rescan,
                summary = R.string.auto_rescan_summary,
                defaultValue = true
            )
        )
    )

    /**
     * Defines the Video settings category.
     */
    private fun createVideoCategory() = SettingCategory(
        title = R.string.video_prefs_category,
        icon = R.drawable.ic_pref_video,
        items = listOf(
            SettingItem.Options(
                key = "hardware_acceleration",
                title = R.string.hardware_acceleration,
                summary = R.string.hardware_acceleration_summary,
                entries = R.array.hardware_acceleration_list,
                entryValues = R.array.hardware_acceleration_values,
                defaultValue = "-1"
            ),
            SettingItem.Options(
                key = KEY_PREFERRED_RESOLUTION,
                title = R.string.preferred_resolution,
                summary = R.string.preferred_resolution_summary,
                entries = R.array.preferred_resolution,
                entryValues = R.array.preferred_resolution_values,
                defaultValue = "-1"
            ),
            SettingItem.Toggle(
                key = "video_confirm_resume",
                title = R.string.confirm_resume_title,
                summary = R.string.confirm_resume,
                defaultValue = false
            )
        )
    )
}
