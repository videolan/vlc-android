/*
 * ************************************************************************
 *  WebServerSession.kt
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

import android.content.SharedPreferences
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.Principal
import io.ktor.server.response.respond
import io.ktor.server.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import org.videolan.tools.putSingle
import org.videolan.vlc.webserver.ssl.SecretGenerator

object WebServerSession {
    /**
     * Verify if the user is logged in
     *
     * @param settings the SharedPreferences to look into
     * @param redirect tru if this call needs to be redirected upon login error
     */
    suspend fun PipelineContext<Unit, ApplicationCall>.verifyLogin(settings: SharedPreferences) {
        val userSession: UserSession? = call.sessions.get("user_session") as? UserSession
        val loggedIn = userSession != null && userSession.id == settings.getString("valid_session_id", "")
        if (!loggedIn) {
                call.respond(HttpStatusCode.Unauthorized)
        } else {
            call.sessions.set("user_session", UserSession(id = userSession!!.id, count = userSession.count + 1))
        }
    }

    /**
     * injects the cookie in the [call] headers
     *
     * @param call the call to inject the cookie into
     * @param settings the settings used to store the valid sessions
     */
    fun injectCookie(call: ApplicationCall, settings: SharedPreferences) {
        val id = SecretGenerator.generateRandomString()
        settings.putSingle("valid_session_id", id)
        call.sessions.set("user_session", UserSession(id = id, count = 0))
    }
}

data class UserSession(val id: String, val count: Int) : Principal
