/*
 * ************************************************************************
 *  RemoteAccessRouting.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.vlc.remoteaccessserver

import android.content.Context
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import kotlinx.coroutines.CoroutineScope
import org.videolan.tools.Settings
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer.Companion.getServerFiles
import org.videolan.vlc.remoteaccessserver.routing.*
import java.io.File

/**
 * Setup the server routing
 *
 */
fun Route.setupRouting(appContext: Context, scope: CoroutineScope) {
    val settings = Settings.getInstance(appContext)
    staticFiles("", File(getServerFiles(appContext)))

    publicAuthRouting(appContext, scope, settings)
    publicFileRouting(appContext, settings)
    publicCommonRouting(appContext)

    authenticate("user_session", optional = RemoteAccessServer.byPassAuth) {
        authenticatedAuthRouting()
        mediaRouting(appContext, scope, settings)
        authenticatedFileRouting(appContext, scope, settings)
        authenticatedCommonRouting(appContext, settings)
    }
}
