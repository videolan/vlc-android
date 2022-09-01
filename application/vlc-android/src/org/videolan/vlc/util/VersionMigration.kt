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

private const val CURRENT_VERSION = 9

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
        if (lastVersion < 6) {
            migrateToVersion6(settings)
        }
        if (lastVersion < 7) {
            migrateToVersion7(settings)
        }
        if (lastVersion < 8) {
            migrateToVersion8(settings)
        }

        if (lastVersion < 9) {
            migrateToVersion9(settings)
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
     * Migrate the TV Ui to make sure the preference is setup
     */
    private fun migrateToVersion5(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating to Version 5: force the TV ui setting if device is TV")
        if (Settings.device.isTv && settings.getBoolean("tv_ui", false) != settings.getBoolean("tv_ui", true)) settings.putSingle("tv_ui", true)
    }

    /**
     * Migrate the Video hud timeout to the new range
     */
    private fun migrateToVersion6(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating to Version 6: Migrate the Video hud timeout to the new range")
        val hudTimeOut = settings.getInt(VIDEO_HUD_TIMEOUT, 4)
        if (hudTimeOut == 0) settings.edit { putInt(VIDEO_HUD_TIMEOUT, 16) }
        Settings.videoHudDelay = settings.getInt(VIDEO_HUD_TIMEOUT, 4).coerceInOrDefault(1,15,-1)
    }

    /**
     * Migrate the PLAYLIST_REPEAT_MODE_KEY from the PlaylistManager to split it in two
     * audio / video separate preferences, PLAYLIST_VIDEO_REPEAT_MODE_KEY and
     * PLAYLIST_AUDIO_REPEAT_MODE_KEY, but keep the value previously set by the user
     */
    private fun migrateToVersion7(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migrating to Version 7: migrate PlaylistManager " +
                "PLAYLIST_REPEASE_MODE_KEY to PLAYLIST_VIDEO_REPEAT_MODE_KEY " + 
                "and PLAYLIST_AUDIO_REPEAT_MODE_KEY")
        val repeat = settings.getInt("audio_repeat_mode", -1)
        if (repeat != -1) {
            settings.putSingle("video_repeat_mode", repeat)
        }
    }

    /**
     * Migrate from having one force_play_all that was labeled as Video Playlist Mode in the settings
     * but also affected some audio in the browser to two separate settings force_play_all,
     * historically will continue forcing to play all videos, and force_play_all_audio which will
     * do the same when playing audio files. Migration to keep the previous value in both settings
     */
    private fun migrateToVersion8(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migration to Version 8: split force_play_all " +
                "and add force_play_all_audio to separately handle video and audio")
        if (settings.contains("force_play_all"))
            settings.edit {
                val oldSetting = settings.getBoolean("force_play_all", false)
                putBoolean(FORCE_PLAY_ALL_VIDEO, oldSetting)
                putBoolean(FORCE_PLAY_ALL_AUDIO, oldSetting)
                remove("force_play_all")
            }
    }

    /**
     * Migrate the video screenshot control setting from a boolean
     * to a multiple entry value
     */
    private fun migrateToVersion9(settings: SharedPreferences) {
        Log.i(this::class.java.simpleName, "Migration to Version 9: migrate the screenshot setting to a multiple entry value")
        if (settings.contains("enable_screenshot_gesture"))
            settings.edit {
                val oldSetting = settings.getBoolean("enable_screenshot_gesture", false)
                if (oldSetting) putString(SCREENSHOT_MODE, "2")
                remove("enable_screenshot_gesture")
            }
    }
}