/*****************************************************************************
 * RemoteAccessService.kt
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
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.ACTION_DISABLE_SERVER
import org.videolan.resources.ACTION_RESTART_SERVER
import org.videolan.resources.ACTION_START_SERVER
import org.videolan.resources.ACTION_STOP_SERVER
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.registerReceiverCompat
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.Settings
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.putSingle
import org.videolan.vlc.gui.helpers.NotificationHelper


class RemoteAccessService : LifecycleService(), CoroutineScope by MainScope() {

    private lateinit var server: RemoteAccessServer
    private val startServerActor = actor<String>(capacity = Channel.CONFLATED) {
        for (entry in channel) {
            when (entry) {
                ACTION_STOP_SERVER -> {
                    server.stop()
                }
                ACTION_START_SERVER -> {
                    server.start()
                }
                ACTION_RESTART_SERVER ->{
                    val observer = object : Observer<ServerStatus> {
                        override fun onChanged(serverStatus: ServerStatus) {
                            if (serverStatus == ServerStatus.STOPPED) {
                                lifecycleScope.launch { server.start() }
                                server.serverStatus.removeObserver(this)
                            }
                        }
                    }
                    server.serverStatus.observe(this@RemoteAccessService, observer)
                    server.stop()
                }
            }
        }
    }


    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("WakelockTimeout")
        override fun onReceive(context: Context, intent: Intent) {
            if (!::server.isInitialized) return
            when (intent.action) {
                ACTION_STOP_SERVER -> {
                    startServerActor.trySend(ACTION_STOP_SERVER)
                }
                ACTION_DISABLE_SERVER -> {
                    lifecycleScope.launch {
                        server.stop()
                        Settings.getInstance(this@RemoteAccessService).putSingle(KEY_ENABLE_REMOTE_ACCESS, false)
                        stopService(Intent(applicationContext, RemoteAccessService::class.java))
                    }
                }
                ACTION_START_SERVER -> {
                    startServerActor.trySend(ACTION_START_SERVER)
                }
                ACTION_RESTART_SERVER -> {
                    startServerActor.trySend(ACTION_RESTART_SERVER)
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
            server = RemoteAccessServer.getInstance(applicationContext)
            server.start()
            withContext(Dispatchers.Main) {
                server.serverStatus.observe(this@RemoteAccessService) {
                    forceForeground()
                }
            }
        }
        val filter = IntentFilter()
        filter.addAction(ACTION_STOP_SERVER)
        filter.addAction(ACTION_START_SERVER)
        filter.addAction(ACTION_DISABLE_SERVER)
        filter.addAction(ACTION_RESTART_SERVER)
        registerReceiverCompat(receiver, filter, false)
    }

    private fun forceForeground() {
        val contentString = if (!::server.isInitialized) getString(R.string.remote_access_notification_not_init) else
            when (server.serverStatus.value) {
                ServerStatus.NOT_INIT -> getString(R.string.remote_access_notification_not_init)
                ServerStatus.STARTED -> getString(R.string.remote_access_notification, server.getServerAddresses().joinToString("\n"))
                ServerStatus.STOPPED -> getString(R.string.remote_access_notification_stopped)
                ServerStatus.CONNECTING -> getString(R.string.remote_access_notification_connecting)
                ServerStatus.ERROR -> getString(R.string.remote_access_notification_error)
                ServerStatus.STOPPING -> getString(R.string.remote_access_notification_stopping)
                else -> ""
            }
        val started = ::server.isInitialized && server.serverStatus.value == ServerStatus.STARTED
        val notification = NotificationHelper.createRemoteAccessNotification(applicationContext, contentString, started)
        try {
            startForeground(44, notification)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w("RemoteAccessService", "ForegroundServiceStartNotAllowedException caught!")
            }
        }
    }

    override fun onDestroy() {
        if (::server.isInitialized) lifecycleScope.launch { server.stop() }
        unregisterReceiver(receiver)
        super.onDestroy()
    }

}


