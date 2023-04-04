/*****************************************************************************
 * WebServerService.kt
 * Copyright © 2011-2018 VLC authors and VideoLAN
 *
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
 */

package org.videolan.vlc.webserver

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.ACTION_START_SERVER
import org.videolan.resources.ACTION_STOP_SERVER
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.registerReceiverCompat
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.gui.helpers.NotificationHelper


class WebServerService : LifecycleService() {

    private lateinit var server: HttpSharingServer
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("WakelockTimeout")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP_SERVER -> {
                    lifecycleScope.launch { server.stop() }
                }
                ACTION_START_SERVER -> {
                    lifecycleScope.launch { server.start(this@WebServerService) }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        if (AndroidUtil.isOOrLater) forceForeground()
        lifecycleScope.launch(Dispatchers.IO) {
            server = HttpSharingServer.getInstance(applicationContext)
            server.start(this@WebServerService)
            withContext(Dispatchers.Main) {
                server.serverStatus.observe(this@WebServerService) {
                    forceForeground()
                }
            }
        }
        val filter = IntentFilter()
        filter.addAction(ACTION_STOP_SERVER)
        filter.addAction(ACTION_START_SERVER)
        registerReceiverCompat(receiver, filter, false)
    }

    private fun forceForeground() {
        val contentString = if (!::server.isInitialized) getString(R.string.web_server_notification_not_init) else
            when (server.serverStatus.value) {
                ServerStatus.NOT_INIT -> getString(R.string.web_server_notification_not_init)
                ServerStatus.STARTED -> getString(R.string.web_server_notification, server.serverInfo())
                ServerStatus.STOPPED -> getString(R.string.web_server_notification_stopped)
                ServerStatus.CONNECTING -> getString(R.string.web_server_notification_connecting)
                ServerStatus.ERROR -> getString(R.string.web_server_notification_error)
                ServerStatus.STOPPING -> getString(R.string.web_server_notification_stopping)
                else -> ""
            }
        val started = ::server.isInitialized && server.serverStatus.value == ServerStatus.STARTED
        val notification = NotificationHelper.createWebServerNotification(applicationContext, contentString, started)
        try {
            startForeground(44, notification)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w("WebServerService", "ForegroundServiceStartNotAllowedException caught!")
            }
        }
    }

    override fun onDestroy() {
        if (::server.isInitialized) lifecycleScope.launch { server.stop() }
        unregisterReceiver(receiver)
        super.onDestroy()
    }

}


