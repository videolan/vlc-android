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
import org.videolan.tools.*
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
            createAudioCategory(context),
            createSubtitlesCategory(context),
            createUiCategory(context),
            createParentalControlCategory(),
            createRemoteAccessCategory(context),
            createAdvancedCategory(context)
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

    /**
     * Defines the Subtitles settings category.
     */
    private fun createSubtitlesCategory(context: Context) = SettingCategory(
        title = R.string.subtitles_prefs_category,
        icon = R.drawable.ic_pref_subtitles,
        items = listOf(
            SettingItem.Options(
                key = KEY_SUBTITLE_PREFERRED_LANGUAGE,
                title = R.string.subtitle_preferred_language,
                entries = emptyList(), // Will be populated dynamically in ViewModel
                entryValues = emptyList(),
                defaultValue = ""
            ),
            SettingItem.Options(
                key = KEY_SUBTITLE_TEXT_ENCODING,
                title = R.string.subtitle_text_encoding,
                entries = context.resources.getStringArray(R.array.subtitles_encoding_list).toList(),
                entryValues = context.resources.getStringArray(R.array.subtitles_encoding_values).toList(),
                defaultValue = ""
            ),
            SettingItem.Options(
                key = "subtitles_presets",
                title = R.string.subtitles_presets_title,
                entries = context.resources.getStringArray(R.array.subtitles_presets_entries).toList(),
                entryValues = context.resources.getStringArray(R.array.subtitles_presets_values).toList(),
                defaultValue = "-1"
            ),
            SettingItem.Options(
                key = KEY_SUBTITLES_SIZE,
                title = R.string.subtitles_size_title,
                entries = context.resources.getStringArray(R.array.subtitles_size_entries).toList(),
                entryValues = context.resources.getStringArray(R.array.subtitles_size_values).toList(),
                defaultValue = "16"
            ),
            SettingItem.Toggle(
                key = KEY_SUBTITLES_BOLD,
                title = R.string.subtitles_bold_title,
                defaultValue = false
            ),
            SettingItem.Color(
                key = KEY_SUBTITLES_COLOR,
                title = R.string.subtitles_color_title,
                defaultColor = android.graphics.Color.WHITE
            ),
            SettingItem.Toggle(
                key = KEY_SUBTITLES_BACKGROUND,
                title = R.string.subtitles_background_title,
                defaultValue = false
            ),
            SettingItem.Color(
                key = KEY_SUBTITLES_BACKGROUND_COLOR,
                title = R.string.subtitles_background_color_title,
                defaultColor = android.graphics.Color.BLACK
            )
        )
    )

    /**
     * Defines the UI settings category.
     */
    private fun createUiCategory(context: Context) = SettingCategory(
        title = R.string.interface_prefs_screen,
        icon = R.drawable.ic_ui,
        items = listOf(
            SettingItem.Options(
                key = KEY_APP_THEME,
                title = R.string.daynight_title,
                entries = context.resources.getStringArray(R.array.daynight_mode_entries).toList(),
                entryValues = context.resources.getStringArray(R.array.daynight_mode_values).toList(),
                defaultValue = "2"
            ),
            SettingItem.Toggle(
                key = PREF_TV_UI,
                title = R.string.tv_ui_title,
                summary = R.string.tv_ui_summary,
                defaultValue = true
            ),
            SettingItem.Options(
                key = KEY_SET_LOCALE,
                title = R.string.language,
                entries = emptyList(), // Will be populated dynamically in ViewModel
                entryValues = emptyList(),
                defaultValue = ""
            ),
            SettingItem.Toggle(
                key = TV_FOLDERS_FIRST,
                title = R.string.tv_folders_first,
                defaultValue = true
            ),
            SettingItem.Toggle(
                key = SHOW_VIDEO_THUMBNAILS,
                title = R.string.show_video_thumbnails,
                defaultValue = true
            ),
            SettingItem.Action(
                key = "default_sleep_timer",
                title = R.string.sleep_title,
                summary = R.string.default_sleep_timer_summary
            )
        )
    )

    /**
     * Defines the Parental Control settings category.
     */
    private fun createParentalControlCategory() = SettingCategory(
        title = R.string.parental_control,
        icon = R.drawable.ic_pref_parental_control,
        items = listOf(
            SettingItem.Toggle(
                key = KEY_SAFE_MODE,
                title = R.string.safe_mode,
                summary = R.string.safe_mode_summary,
                defaultValue = false
            ),
            SettingItem.Action(
                key = "modify_pin_code",
                title = R.string.pin_code_reason_modify
            )
        )
    )

    /**
     * Defines the Remote Access settings category.
     */
    private fun createRemoteAccessCategory(context: Context) = SettingCategory(
        title = R.string.remote_access,
        icon = R.drawable.ic_pref_remote_access,
        items = listOf(
            SettingItem.Toggle(
                key = KEY_ENABLE_REMOTE_ACCESS,
                title = R.string.enable_remote_access,
                summary = R.string.remote_access_notification_not_init,
                defaultValue = false
            ),
            SettingItem.Action(
                key = "remote_access_status",
                title = R.string.remote_access_status
            ),
            SettingItem.Action(
                key = "remote_access_info",
                title = R.string.remote_access_info
            ),
            SettingItem.Options(
                key = KEY_REMOTE_ACCESS_ML_CONTENT,
                title = R.string.remote_access_medialibrary_content,
                entries = context.resources.getStringArray(R.array.remote_access_content_entries).toList(),
                entryValues = context.resources.getStringArray(R.array.remote_access_content_values).toList(),
                defaultValue = ""
            )
        )
    )

    /**
     * Defines the Advanced settings category.
     */
    private fun createAdvancedCategory(context: Context) = SettingCategory(
        title = R.string.advanced_prefs_category,
        icon = R.drawable.ic_pref_advanced_settings,
        items = listOf(
            SettingItem.Options(
                key = KEY_AOUT,
                title = R.string.aout,
                summary = R.string.aout_summary,
                entries = context.resources.getStringArray(R.array.aouts).toList(),
                entryValues = context.resources.getStringArray(R.array.aouts_values).toList(),
                defaultValue = "0"
            ),
            SettingItem.Toggle(
                key = KEY_ENABLE_TIME_STRETCHING_AUDIO,
                title = R.string.enable_time_stretching_audio,
                summary = R.string.enable_time_stretching_audio_summary,
                defaultValue = true
            ),
            SettingItem.Options(
                key = KEY_DEBLOCKING,
                title = R.string.deblocking,
                summary = R.string.deblocking_summary,
                entries = context.resources.getStringArray(R.array.deblocking_list).toList(),
                entryValues = context.resources.getStringArray(R.array.deblocking_values).toList(),
                defaultValue = "-1"
            ),
            SettingItem.Action(
                key = "debug_logs",
                title = R.string.debug_logs
            ),
            SettingItem.Action(
                key = "clear_history",
                title = R.string.clear_playback_history,
                summary = R.string.clear_history_message
            ),
            SettingItem.Action(
                key = "clear_media_db",
                title = R.string.clear_media_db,
                summary = R.string.clear_media_db_message
            ),
            SettingItem.Action(
                key = "dump_media_db",
                title = R.string.dump_media_db,
                summary = R.string.dump_db_succes
            ),
            SettingItem.Action(
                key = "quit_app",
                title = R.string.quit
            )
        )
    )
}
