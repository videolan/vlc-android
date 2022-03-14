/*
 * ************************************************************************
 *  VersionMigration.kt
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
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.getFromMl
import org.videolan.tools.*
import org.videolan.vlc.gui.onboarding.ONBOARDING_DONE_KEY
import java.io.File
import java.io.IOException

private const val CURRENT_VERSION = 5

object VersionMigration {

    suspend fun migrateVersion(context: Context) {
        val settings = Settings.getInstance(context)
        val lastVersion = settings.getInt(KEY_CURRENT_SETTINGS_VERSION, 0)
        if (lastVersion < 1) {
            migrateToVersion1(settings)
        }
        if (lastVersion < 2) {
            migrateToVersion2(context)
        }
        if (lastVersion < 3) {
            migrateToVersion3(context)
        }
        if (lastVersion < 4) {
            migrateToVersion4(settings)
        }
        if (lastVersion < 5) {
            migrateToVersion5(settings)
        }
        settings.putSingle(KEY_CURRENT_SETTINGS_VERSION, CURRENT_VERSION)
    }

    private fun migrateToVersion1(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating preferences to Version 1")
        settings.edit {
            //Migrate video Resume confirmation
            val dialogConfirmResume = settings.getBoolean("dialog_confirm_resume", false)
            if (dialogConfirmResume) {
                putString(KEY_VIDEO_CONFIRM_RESUME, "2")
            }
            remove("dialog_confirm_resume")
            //Migrate apptheme
            if (!settings.contains(KEY_APP_THEME)) {
                val daynight = settings.getBoolean("daynight", false)
                val dark = settings.getBoolean("enable_black_theme", false)
                val mode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else if (daynight) AppCompatDelegate.MODE_NIGHT_AUTO else AppCompatDelegate.MODE_NIGHT_NO
                putString(KEY_APP_THEME, mode.toString())
            }
            remove("daynight")
            remove("enable_black_theme")
        }
    }

    /**
     * Deletes all the video thumbnails as we change the way to name them.
     */
    private suspend fun migrateToVersion2(context: Context) {
        Log.i(this::class.java.simpleName, "Migrating version to Version 2: flush all the video thumbnails")
        withContext(Dispatchers.IO) {
            try {
                context.getExternalFilesDir(null)?. let {
                    val cacheDir = it.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME
                    val files = File(cacheDir).listFiles()
                    files?.forEach { file ->
                        if (file.isFile) FileUtils.deleteFile(file)
                    }
                }
            } catch (e: IOException) {
                Log.e(this::class.java.simpleName, e.message, e)
            }
        }
        val settings = Settings.getInstance(context)
        val onboarding = !settings.getBoolean(ONBOARDING_DONE_KEY, false)
        val tv = AndroidDevices.isAndroidTv || !AndroidDevices.isChromeBook && !AndroidDevices.hasTsp ||
                settings.getBoolean("tv_ui", false)
        if (!tv && !onboarding) context.getFromMl { flushUserProvidedThumbnails() }
    }

    /**
     * Deletes all the programs from the WatchNext channel on the TV Home.
     * After reindexing media ids can change, so programs now also have the uri of their media file.
     */
    private suspend fun migrateToVersion3(context: Context) {
        Log.i(this::class.java.simpleName, "Migrating to Version 3: remove all WatchNext programs")
        withContext(Dispatchers.IO) {
            deleteAllWatchNext(context)
        }
    }

    /**
     * Migrate the video hud timeout preference to a value in seconds
     */
    private fun migrateToVersion4(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating to Version 4: migrate from video_hud_timeout to video_hud_timeout_in_s")
        val hudTimeOut = settings.getString("video_hud_timeout", "2")?.toInt() ?: 2
        settings.edit {
            when  {
                hudTimeOut < 0 -> putInt(VIDEO_HUD_TIMEOUT, -1)
                hudTimeOut == 2 -> putInt(VIDEO_HUD_TIMEOUT, 4)
                hudTimeOut == 3 -> putInt(VIDEO_HUD_TIMEOUT, 8)
                else -> putInt(VIDEO_HUD_TIMEOUT, 2)
            }
            remove("video_hud_timeout")
        }
    }

    /**
     * Migrate the video hud timeout preference to a value in seconds
     */
    private fun migrateToVersion5(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating to Version 5: force the TV ui setting if TVui is enforced")
        if (Settings.showTvUi) settings.putSingle("tv_ui", true)
    }
}