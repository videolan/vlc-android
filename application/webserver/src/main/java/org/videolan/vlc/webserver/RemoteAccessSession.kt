/*
 * ************************************************************************
 *  RemoteAccessSession.kt
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
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.Principal
import io.ktor.server.response.respond
import io.ktor.server.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import org.videolan.tools.putSingle
import org.videolan.vlc.webserver.ssl.SecretGenerator

private const val VALID_SESSIONS = "valid_sessions"

object RemoteAccessSession {
    val maxAge = if (BuildConfig.DEBUG) 4 * 3600L else 3600L * 24L * 365L

    /**
     * Verify if the user is logged in
     *
     * @param settings the SharedPreferences to look into
     */
    suspend fun PipelineContext<Unit, ApplicationCall>.verifyLogin(settings: SharedPreferences) {
        if (RemoteAccessServer.byPassAuth) return
        val sessions: List<UserSession> = getSessions(settings)
        val userSession: UserSession? = call.sessions.get("user_session") as? UserSession
        val loggedIn = userSession != null && sessions.firstOrNull { it.id == userSession.id } != null
        if (userSession != null) sessions.firstOrNull { it.id == userSession.id }?.let {
            it.maxAge = System.currentTimeMillis() + maxAge
            saveSessions(settings, sessions)
        }
        if (!loggedIn) {
            call.respond(HttpStatusCode.Unauthorized)
        } else {
            call.sessions.set("user_session", UserSession(id = userSession!!.id, userSession.maxAge))
        }
    }

    /**
     * Get all the valid sessions and trim the expired ones if needed
     *
     * @param settings the settings to retrieve the sessions
     * @return a list of valid sessions
     */
    private fun getSessions(settings: SharedPreferences): List<UserSession> {
        val sessionsString = settings.getString(VALID_SESSIONS, "[]") ?: "[]"
        val moshi: Moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(MutableList::class.java, UserSession::class.java)

        val adapter: JsonAdapter<List<UserSession>> = moshi.adapter(type)
        adapter.fromJson(sessionsString)?.let {
            saveSessions(settings, it.filter { it.maxAge > System.currentTimeMillis() + 3600 })
            return it
        }
        return listOf()
    }

    private fun saveSessions(settings: SharedPreferences, newList: List<UserSession>) {
        val moshi: Moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(MutableList::class.java, UserSession::class.java)
        val adapter: JsonAdapter<List<UserSession>> = moshi.adapter(type)

        settings.putSingle(VALID_SESSIONS, adapter.toJson(newList))
    }


    /**
     * injects the cookie in the [call] headers
     *
     * @param call the call to inject the cookie into
     * @param settings the settings used to store the valid sessions
     */
    fun injectCookie(call: ApplicationCall, settings: SharedPreferences) {
        val id = SecretGenerator.generateRandomString()
        val value = UserSession(id = id, System.currentTimeMillis() + maxAge)
        val newList = getSessions(settings).toMutableList()
        newList.add(value)
        saveSessions(settings, newList.toList())
        call.sessions.set("user_session", value)
    }
}

data class UserSession(
        val id: String,
        var maxAge: Long
) : Principal
