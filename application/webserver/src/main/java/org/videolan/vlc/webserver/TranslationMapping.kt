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
            map[it.name] = context.getString(it.id).replace("%s", "{msg}")
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
        SEND_FILES(R.string.ns_send_files),
        DOWNLOAD(R.string.download),
        NO_MEDIA(R.string.nomedia),
        PLAY(R.string.play),
        APPEND(R.string.append),
        PLAY_AS_AUDIO(R.string.play_as_audio),
        SEARCH(R.string.search),
        DISCONNECTED(R.string.ns_disconnected),
        FILE(R.string.ns_file),
        UPLOAD_REMAINING(R.string.ns_upload_remaining),
        UPLOAD_ALL(R.string.ns_upload_all),
        DROP_FILES_TIP(R.string.ns_drop_files_tip),
        PREPARING_DOWNLOAD(R.string.ns_prepare_download),
        RESUME_PLAYBACK(R.string.resume_playback_short_title),
        DISPLAY_LIST(R.string.display_in_list),
        DISPLAY_GRID(R.string.display_in_grid),
        SEARCH_HINT(R.string.search_hint),
        SEARCH_NO_RESULT(R.string.search_no_result),
        DIRECTORY_EMPTY(R.string.empty_directory),
        FORBIDDEN(R.string.ns_forbidden),
        PLAYBACK_CONTROL_FORBIDDEN(R.string.ns_playback_forbidden),
        SEND(R.string.send),
        NEW_CODE(R.string.ns_new_code),
        CODE_REQUEST_EXPLANATION(R.string.ns_code_requested_explanation),
        SSL_BUTTON(R.string.ns_ssl_button),
        SSL_EXPLANATION_TITLE(R.string.ns_ssl_explanation_title),
        SSL_EXPLANATION(R.string.ns_ssl_explanation),
        SSL_EXPLANATION_BROWSER(R.string.ns_ssl_explanation_browser),
        SSL_EXPLANATION_ACCEPT(R.string.ns_ssl_explanation_accept),
        SEND_LOGS(R.string.ns_send_logs),
    }
}