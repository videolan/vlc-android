/*
 * ************************************************************************
 *  OpensubtitleUser.kt
 * *************************************************************************
 * Copyright © 2024 VLC authors and VideoLAN
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

package main.java.org.videolan.resources.opensubtitles

import android.content.SharedPreferences
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.videolan.resources.opensubtitles.OpenSubtitleAccount
import org.videolan.tools.KEY_OPEN_SUBTITLES_USER
import org.videolan.tools.putSingle

data class OpenSubtitlesUser(
    val logged: Boolean = false,
    val account: OpenSubtitleAccount? = null,
    val username: String = "",
    val errorMessage: String? = null
) {
    fun isVip()  = account?.user?.vip ?: false
}

object OpenSubtitlesUserUtil {
    fun get(settings: SharedPreferences): OpenSubtitlesUser {
        settings.getString(KEY_OPEN_SUBTITLES_USER, "")?.let { userString ->
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(OpenSubtitlesUser::class.java)
            val jsonAdapter: JsonAdapter<OpenSubtitlesUser> = moshi.adapter(type)
            return try {
                jsonAdapter.fromJson(userString) ?: OpenSubtitlesUser()
            } catch (e: Exception) {
                OpenSubtitlesUser()
            }
        }
        return OpenSubtitlesUser()
    }

    fun save(settings: SharedPreferences, user: OpenSubtitlesUser) {
        val moshi = Moshi.Builder().build()
        val jsonAdapter = moshi.adapter(OpenSubtitlesUser::class.java)
        settings.putSingle(KEY_OPEN_SUBTITLES_USER, jsonAdapter.toJson(user))
    }
}