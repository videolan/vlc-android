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
import androidx.appcompat.app.AppCompatDelegate
import org.videolan.tools.*

private const val CURRENT_VERSION = 1

object SettingsMigration {

    fun migrateSettings(context: Context) {
        val settings = Settings.getInstance(context)
        val lastVersion = settings.getInt(KEY_CURRENT_SETTINGS_VERSION, 0)
        if (lastVersion < 1) {
            migrateToVersion1(settings)
        }
        settings.putSingle(KEY_CURRENT_SETTINGS_VERSION, CURRENT_VERSION)
    }

    private fun migrateToVersion1(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating preferences to Version 1")
        val editor = settings.edit()
        //Migrate video Resume confirmation
        val dialogConfirmResume = settings.getBoolean("dialog_confirm_resume", false)
        if (dialogConfirmResume) {
            editor.putString(KEY_VIDEO_CONFIRM_RESUME, "2")
        }
        editor.remove("dialog_confirm_resume")
        //Migrate apptheme
        if (!settings.contains(KEY_APP_THEME)) {
            val daynight = settings.getBoolean("daynight", false)
            val dark = settings.getBoolean("enable_black_theme", false)
            val mode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else if (daynight) AppCompatDelegate.MODE_NIGHT_AUTO else AppCompatDelegate.MODE_NIGHT_NO
            editor.putString(KEY_APP_THEME, mode.toString())
        }
        editor.remove("daynight").remove("enable_black_theme")

        editor.apply()
    }
}