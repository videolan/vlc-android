/*
 * ************************************************************************
 *  SettingsMigration.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.videolan.vlc.BuildConfig

object SettingsMigration {

    fun migrateSettings(context: Context) {
        val settings = Settings.getInstance(context)
        val lastVersion = settings.getInt(KEY_CURRENT_SETTINGS_VERSION, 0)
        if (lastVersion < 3030000) {
            migrateToVersion3030000(settings)
        }
        settings.edit().putInt(KEY_CURRENT_SETTINGS_VERSION, BuildConfig.VERSION_CODE).apply()
    }

    private fun migrateToVersion3030000(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating preferences to 3030000")
        val editor = settings.edit()
        val dialogConfirmResume = settings.getBoolean("dialog_confirm_resume", false)
        if (dialogConfirmResume) {
            editor.putString(KEY_VIDEO_CONFIRM_RESUME, "2")
        }
        editor.remove("dialog_confirm_resume")
        editor.apply()
    }
}