/*
 * ************************************************************************
 *  OpenSubtitlesUtils.kt
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
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import org.videolan.resources.opensubtitles.IOpenSubtitleService
import org.videolan.resources.opensubtitles.OpenSubtitleClient
import org.videolan.tools.KEY_OPEN_SUBTITLES_LIMIT
import org.videolan.tools.KEY_OPEN_SUBTITLES_USER
import org.videolan.tools.putSingle
import java.util.Date

object OpenSubtitlesUtils {
    fun getUser(settings: SharedPreferences): OpenSubtitlesUser {
        settings.getString(KEY_OPEN_SUBTITLES_USER, "")?.let { userString ->
            val jsonAdapter = getUserAdapter()
            return try {
                jsonAdapter.fromJson(userString) ?: OpenSubtitlesUser()
            } catch (e: Exception) {
                OpenSubtitlesUser()
            }
        }
        return OpenSubtitlesUser()
    }

    fun getLimit(settings: SharedPreferences): OpenSubtitlesLimit {
        settings.getString(KEY_OPEN_SUBTITLES_LIMIT, "")?.let { limitString ->
            val jsonAdapter = getLimitAdapter()
            return try {
                jsonAdapter.fromJson(limitString) ?: OpenSubtitlesLimit()
            } catch (e: Exception) {
                OpenSubtitlesLimit()
            }
        }
        return OpenSubtitlesLimit()
    }

    fun saveUser(settings: SharedPreferences, user: OpenSubtitlesUser) {
        val jsonAdapter = getUserAdapter()
        settings.putSingle(KEY_OPEN_SUBTITLES_USER, jsonAdapter.toJson(user))
        OpenSubtitleClient.authorizationToken = user.account?.token ?: ""
        OpenSubtitleClient.userDomain = user.account?.baseUrl
    }

    private fun getUserAdapter(): JsonAdapter<OpenSubtitlesUser> {
        val moshi = Moshi.Builder().build()
        val jsonAdapter = moshi.adapter(OpenSubtitlesUser::class.java)
        return jsonAdapter
    }

    fun saveLimit(settings: SharedPreferences, limit: OpenSubtitlesLimit) {
        val jsonAdapter = getLimitAdapter()
        settings.putSingle(KEY_OPEN_SUBTITLES_LIMIT, jsonAdapter.toJson(limit))
    }

    private fun getLimitAdapter(): JsonAdapter<OpenSubtitlesLimit> {
        val moshi =
            Moshi.Builder().add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe()).build()
        val jsonAdapter = moshi.adapter(OpenSubtitlesLimit::class.java)
        return jsonAdapter
    }
}