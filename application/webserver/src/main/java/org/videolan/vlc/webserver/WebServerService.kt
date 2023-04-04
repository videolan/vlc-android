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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleService
import io.ktor.server.netty.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.ACTION_STOP_SERVER
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.registerReceiverCompat
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.gui.helpers.NotificationHelper


class WebServerService :  LifecycleService() {

    private lateinit var server: HttpSharingServer
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("WakelockTimeout")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP_SERVER -> {
                    stopForeground(true)
                    stopSelf()
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
        server = HttpSharingServer.getInstance(applicationContext)
        server.start()
        val filter = IntentFilter()
        filter.addAction(ACTION_STOP_SERVER)
        registerReceiverCompat(receiver, filter, false)
    }

    private fun forceForeground() {
        val notification = NotificationHelper.createWebServerNotification(applicationContext, "")
        startForeground(44, notification)
    }

    override fun onDestroy() {
        if (::server.isInitialized) server.stop()
        unregisterReceiver(receiver)
        super.onDestroy()
    }

}


