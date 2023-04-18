/*
 * ************************************************************************
 *  TranslationMapping.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
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

package org.videolan.vlc.webserver

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONObject

object TranslationMapping {
    fun generateTranslations(context: Context): String {
        val map = HashMap<String, String>()
        StringMapping.values().forEach {
            map[it.name] = context.getString(it.id)
        }
        return JSONObject(map.toMap()).toString()
    }

    enum class StringMapping(@StringRes val id:Int) {
        VIDEO(R.string.video),
        AUDIO(R.string.audio),
        BROWSE(R.string.browse),
        PLAYLISTS(R.string.playlists),
        ARTISTS(R.string.artists),
        ALBUMS(R.string.albums),
        TRACKS(R.string.tracks),
        GENRES(R.string.genres),
        LOG_FILE(R.string.ns_log_file),
        DROP_FILES(R.string.ns_drop_files),
    }
}