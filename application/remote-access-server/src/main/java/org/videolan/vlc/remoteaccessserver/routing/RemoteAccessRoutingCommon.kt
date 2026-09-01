/*
 * ************************************************************************
 *  RemoteAccessRoutingCommon.kt
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
import android.content.res.Resources
import android.util.Log
import androidx.core.content.ContextCompat
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.videolan.resources.AppContextProvider
import org.videolan.tools.Settings
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.resIdByName
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.VectorDrawableUtil
import org.videolan.vlc.gui.helpers.getColoredBitmapFromColor
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer.Companion.getServerFiles
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer.PlayerStatus
import org.videolan.vlc.remoteaccessserver.TranslationMapping
import org.videolan.vlc.remoteaccessserver.websockets.RemoteAccessWebSockets
import org.videolan.vlc.remoteaccessserver.websockets.WSIncomingMessage
import org.videolan.vlc.util.FileUtils
import java.io.File

fun Route.publicCommonRouting(appContext: Context) {
    // Main end point redirect to index.html
    get("/") {
        call.respondRedirect("index.html", permanent = true)
    }
    get("/index.html") {
        try {
            val html = FileUtils.getStringFromFile("${getServerFiles(appContext)}index.html")
            call.respondText(html, ContentType.Text.Html)
        } catch (e: Exception) {
            call.respondText("Failed to load index.html")
        }
    }
    // Get the translation string list
    get("/translation") {
        call.respondJson(convertToJson(TranslationMapping.generateTranslations(appContext.getContextWithLocale(AppContextProvider.locale))))
    }
    get("/secure-url") {
        call.respondText(RemoteAccessServer.getInstance(appContext).getSecureUrl(call))
    }
    // Sends an icon
    get("/icon") {
        val idString = call.request.queryParameters["id"]
        val width = call.request.queryParameters["width"]?.toInt()?.coerceAtLeast(1) ?: 32
        val preventTint = call.request.queryParameters["preventTint"]?.toBoolean() == true

        val id = try {
            appContext.resIdByName(idString, "drawable")
        } catch (e: Resources.NotFoundException) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        if (id == 0) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        idString?.let {
            try {
                call.respondText(VectorDrawableUtil.convertToSvg(appContext, width, id, it), ContentType.Image.SVG)
                return@get
            } catch (e: Resources.NotFoundException) {
                Log.w("RARoutingCommon", "Failed to convert vector drawable. ${e.message}")
                // Continue on and let BitmapUtil attempt to load the file
            }
        }

        val bmp = if (preventTint)
            BitmapUtil.vectorToBitmap(appContext, id, width, width)
        else
            appContext.getColoredBitmapFromColor(id, ContextCompat.getColor(appContext, R.color.black), width, width)

        BitmapUtil.encodeImage(bmp, true)?.let {

            call.respondBytes(ContentType.Image.PNG) { it }
            return@get
        }

        call.respond(HttpStatusCode.NotFound)

    }
}

fun Route.authenticatedCommonRouting(appContext: Context, settings: SharedPreferences) {
    get("/longpolling") {
        //Empty the queue if needed
        if (RemoteAccessWebSockets.messageQueue.isNotEmpty()) {
            val queue = mutableListOf<RemoteAccessServer.WSMessage>().apply {
                RemoteAccessWebSockets.messageQueue.drainTo(this)
            }
            call.respondJson(convertToJson(queue))
            return@get
        }
        //block the request until a message is received
        // The 3 second timeout is to avoid blocking forever
        try {
            val message = withTimeout(3000) { RemoteAccessWebSockets.onPlaybackEventChannel.receive() }
            if (message.type == RemoteAccessServer.WSMessageType.BROWSER_DESCRIPTION) {
                call.respondJson(convertToJson(listOf(message)))
                return@get
            }
        } catch (e: TimeoutCancellationException) {
            // Fall through to the next block of code
        }
        val remoteAccessServer = RemoteAccessServer.getInstance(appContext)
        val messages = listOfNotNull(
            remoteAccessServer.generatePlayQueue(),
            PlayerStatus(PlaylistManager.showAudioPlayer.value == true),
            remoteAccessServer.generateNowPlaying()
        )
        call.respondJson(convertToJson(messages))
    }
    // Manage playback events
    get("/playback-event") {
        call.request.queryParameters["message"]?.let { message ->
            val id = call.request.queryParameters["id"]?.toInt()
            val longValue = call.request.queryParameters["longValue"]?.toLong()
            val floatValue = call.request.queryParameters["floatValue"]?.toFloat()
            val stringValue = call.request.queryParameters["stringValue"]
            val incomingMessage = WSIncomingMessage(message, id, floatValue, longValue, stringValue)
            val service = RemoteAccessServer.getInstance(appContext).service
            val result = withContext(Dispatchers.Main) {
                RemoteAccessWebSockets.manageIncomingMessages(incomingMessage, settings, service, appContext)
            }
            if (!result) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
        }
        call.respond(HttpStatusCode.OK)
    }
}
