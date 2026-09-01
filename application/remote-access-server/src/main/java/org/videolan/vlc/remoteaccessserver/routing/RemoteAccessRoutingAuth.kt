/*
 * ************************************************************************
 *  RemoteAccessRoutingAuth.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.vlc.remoteaccessserver.routing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.videolan.vlc.remoteaccessserver.RemoteAccessOTP
import org.videolan.vlc.remoteaccessserver.RemoteAccessSession
import org.videolan.vlc.remoteaccessserver.websockets.RemoteAccessWebSockets
import org.videolan.vlc.util.RemoteAccessUtils

private const val TAG = "RARoutingAuth"

fun Route.publicAuthRouting(appContext: Context, scope: CoroutineScope, settings: SharedPreferences) {
    //the client is requesting a new code.
    // if the formparameters "challenge" is sent. Remove the corresponding code
    post("/code") {
        val formParameters = try {
            call.receiveParameters()
        } catch (_: Exception) {
            null
        }
        val challenge = formParameters?.get("challenge")
        if (!challenge.isNullOrBlank()) {
            RemoteAccessOTP.removeCodeWithChallenge(challenge)
        }
        val code = RemoteAccessOTP.getFirstValidCode(appContext)
        scope.launch {
            RemoteAccessUtils.otpFlow.emit(code.code)
        }
        call.respondText(code.challenge)
    }
    //Verify the code and inject the cookie if valid
    post("/verify-code") {
        val formParameters = try {
            call.receiveParameters()
        } catch (e: Exception) {
            null
        }
        val idString = formParameters?.get("code")
        if (idString == null){
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        if (RemoteAccessOTP.verifyCode(appContext, idString)) {
            //verification is OK
            RemoteAccessSession.injectCookie(call, settings)
            scope.launch {
                RemoteAccessUtils.otpFlow.emit(null)
            }
            call.respondRedirect("/")
            return@post
        }
        if (isFlooding(appContext, call.request.origin.remoteAddress)) {
            Log.w(TAG, "Too many requests from ${call.request.origin.remoteAddress}")
            call.respond(HttpStatusCode.TooManyRequests)
            return@post
        }
        call.respondRedirect("/index.html#/login/error")
    }
}

fun Route.authenticatedAuthRouting() {
    //Provide a Websocket auth ticket as auth is validated
    get("/wsticket") {
        val ticket = RemoteAccessWebSockets.createTicket()
        call.respondText(ticket)
    }
}
