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

import android.content.Context
import org.videolan.tools.KEY_AUDIO_DIGITAL_OUTPUT
import org.videolan.tools.KEY_AUDIO_PREFERRED_LANGUAGE
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
     * @param context The context used to load resource arrays.
     * @return A list of [SettingCategory].
     */
    fun createSettings(context: Context): List<SettingCategory> {
        return listOf(
            createMedialibraryCategory(),
            createVideoCategory(context),
            createAudioCategory(context)
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
    private fun createVideoCategory(context: Context) = SettingCategory(
        title = R.string.video_prefs_category,
        icon = R.drawable.ic_pref_video,
        items = listOf(
            SettingItem.Options(
                key = "hardware_acceleration",
                title = R.string.hardware_acceleration,
                summary = R.string.hardware_acceleration_summary,
                entries = context.resources.getStringArray(R.array.hardware_acceleration_list).toList(),
                entryValues = context.resources.getStringArray(R.array.hardware_acceleration_values).toList(),
                defaultValue = "-1"
            ),
            SettingItem.Options(
                key = KEY_PREFERRED_RESOLUTION,
                title = R.string.preferred_resolution,
                summary = R.string.preferred_resolution_summary,
                entries = context.resources.getStringArray(R.array.preferred_resolution).toList(),
                entryValues = context.resources.getStringArray(R.array.preferred_resolution_values).toList(),
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

    /**
     * Defines the Audio settings category.
     */
    private fun createAudioCategory(context: Context) = SettingCategory(
        title = R.string.audio_prefs_category,
        icon = R.drawable.ic_pref_audio,
        items = listOf(
            SettingItem.Toggle(
                key = KEY_AUDIO_DIGITAL_OUTPUT,
                title = R.string.audio_digital_title
            ),
            SettingItem.Options(
                key = KEY_AUDIO_PREFERRED_LANGUAGE,
                title = R.string.audio_preferred_language,
                entries = emptyList(), // Will be populated dynamically in ViewModel
                entryValues = emptyList(),
                defaultValue = ""
            ),
            SettingItem.Options(
                key = "audio_confirm_resume",
                title = R.string.confirm_resume_audio_title,
                entries = context.resources.getStringArray(R.array.ask_confirmation_entries).toList(),
                entryValues = context.resources.getStringArray(R.array.ask_confirmation_values).toList(),
                defaultValue = "0"
            ),
            SettingItem.Toggle(
                key = "audio_ducking",
                title = R.string.audio_ducking_title,
                summary = R.string.audio_ducking_summary,
                defaultValue = true
            ),
            SettingItem.Action(
                key = "soundfont",
                title = R.string.soundfont,
                summary = R.string.soundfont_summary
            )
        )
    )
}
