/*****************************************************************************
 * DebugLogService.java
 *
 * Copyright © 2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */

package org.videolan.vlc

import android.annotation.TargetApi
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.NotificationIds
import org.videolan.resources.util.launchForeground
import org.videolan.resources.util.startForegroundCompat
import org.videolan.resources.util.stopForegroundCompat
import org.videolan.tools.CloseableUtils
import org.videolan.tools.Logcat
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.helpers.FeedbackUtil
import org.videolan.vlc.gui.helpers.NotificationHelper
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.LinkedList

class DebugLogService : Service(), Logcat.Callback, Runnable {

    private var logcat: Logcat? = null
    private val logList = LinkedList<String>()
    private var saveThread: Thread? = null
    private val callbacks = RemoteCallbackList<IDebugLogServiceCallback>()
    private val binder = DebugLogServiceStub(this)

    override fun onCreate() {
        super.onCreate()
        if (AndroidUtil.isOOrLater) forceForeground()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    internal class DebugLogServiceStub(private val service: DebugLogService) : IDebugLogService.Stub() {
        override fun start() {
            service.start()
        }

        override fun stop() {
            service.stop()
        }

        override fun clear() {
            service.clear()
        }

        override fun save() {
            service.save()
        }

        override fun registerCallback(cb: IDebugLogServiceCallback) {
            service.registerCallback(cb)
        }

        override fun unregisterCallback(cb: IDebugLogServiceCallback) {
            service.unregisterCallback(cb)
        }
    }

    @Synchronized
    private fun sendMessage(what: Int, str: String?) {
        var i = callbacks.beginBroadcast()
        while (i > 0) {
            i--
            val cb = callbacks.getBroadcastItem(i)
            try {
                when (what) {
                    MSG_STOPPED -> cb.onStopped()
                    MSG_STARTED -> {
                        cb.onStarted(logList)
                    }
                    MSG_ONLOG -> cb.onLog(str)
                    MSG_SAVED -> cb.onSaved(str != null, str)
                }
            } catch (e: RemoteException) {
            }

        }
        callbacks.finishBroadcast()
    }

    @Synchronized
    override fun onLog(log: String) {
        if (logList.size > MAX_LINES)
            logList.removeAt(0)
        logList.add(log)
        sendMessage(MSG_ONLOG, log)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun forceForeground() {
        if (AndroidUtil.isOOrLater)
            NotificationHelper.createDebugServcieChannel(applicationContext)
        val debugLogIntent = Intent(this, DebugLogActivity::class.java)
        debugLogIntent.action = "android.intent.action.MAIN"
        debugLogIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pi = PendingIntent.getActivity(this, 0, debugLogIntent,  PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, NotificationHelper.VLC_DEBUG_CHANNEL)
        builder.setContentTitle(resources.getString(R.string.log_service_title))
        builder.setContentText(resources.getString(R.string.log_service_text))
        builder.setSmallIcon(R.drawable.ic_stat_vlc)
        builder.setContentIntent(pi)
        val notification = builder.build()
        try {
            val type = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else -> 0
            }
            startForegroundCompat(NotificationIds.DEBUG_LOGS, notification, type)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w("DebugLogService", "ForegroundServiceStartNotAllowedException caught! ${e.message}", e)
            }
        }
    }

    @Synchronized
    fun start() {
        if (logcat != null) return
        clear()
        logcat = Logcat()
        logcat!!.start(this)

        launchForeground(Intent(this, DebugLogService::class.java))
        sendMessage(MSG_STARTED, null)
    }

    fun stop() {
        val stoppedLogcat = synchronized(this) {
            val localLogcat = logcat ?: return
            logcat = null
            localLogcat
        }
        stoppedLogcat.stop()
        sendMessage(MSG_STOPPED, null)
        stopForegroundCompat()
        stopSelf()
    }

    @Synchronized
    fun clear() {
        logList.clear()
    }

    /* saveThread */
    override fun run() {
        val timestamp = DateFormat.format(
                "yyyyMMdd_kkmmss", System.currentTimeMillis())
        val filename = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/vlc_logcat_" + timestamp + ".log"
        var saved = true
        var fos: FileOutputStream? = null
        var output: OutputStreamWriter? = null
        var bw: BufferedWriter? = null

        try {
            fos = FileOutputStream(filename)
            output = OutputStreamWriter(fos)
            bw = BufferedWriter(output)
            synchronized(this) {
                bw.write(FeedbackUtil.generateUsefulInfo(this))
                for (line in logList) {
                    bw.write(line)
                    bw.newLine()
                }
            }
        } catch (e: FileNotFoundException) {

            saved = false
        } catch (ioe: IOException) {
            saved = false
        } finally {
            saved = saved and CloseableUtils.close(bw)
            saved = saved and CloseableUtils.close(output)
            saved = saved and CloseableUtils.close(fos)
        }
        synchronized(this) {
            saveThread = null
            sendMessage(MSG_SAVED, if (saved) filename else null)
        }
    }

    @Synchronized
    fun save() {
        if (saveThread != null) {
            try {
                saveThread!!.join()
            } catch (e: InterruptedException) {
            }

            saveThread = null
        }
        saveThread = Thread(this)
        saveThread!!.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AndroidUtil.isOOrLater) forceForeground()
        return START_STICKY
    }

    private fun registerCallback(cb: IDebugLogServiceCallback?) {
        if (cb != null) {
            callbacks.register(cb)
            sendMessage(if (logcat != null) MSG_STARTED else MSG_STOPPED, null)
        }
    }

    private fun unregisterCallback(cb: IDebugLogServiceCallback?) {
        if (cb != null)
            callbacks.unregister(cb)
    }

    class Client @Throws(IllegalArgumentException::class)
    constructor(private val context: Context, private val callback: Callback) {

        private var bound = false
        private var iDebugLogService: IDebugLogService? = null
        private val handler = Handler(Looper.getMainLooper())
        private var isStarted = false

        private val iDebugLogServiceCallbackStub = object : IDebugLogServiceCallback.Stub() {
            @Throws(RemoteException::class)
            override fun onStopped() {
                handler.post { callback.onStopped() }
                isStarted = false
            }

            @Throws(RemoteException::class)
            override fun onStarted(logList: List<String>) {
                handler.post { callback.onStarted(logList) }
                isStarted = true
            }

            @Throws(RemoteException::class)
            override fun onLog(msg: String) {
                handler.post { callback.onLog(msg) }
            }

            @Throws(RemoteException::class)
            override fun onSaved(success: Boolean, path: String) {
                handler.post { callback.onSaved(success, path) }
            }
        }

        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                synchronized(this@Client) {
                    iDebugLogService = IDebugLogService.Stub.asInterface(service)
                    try {
                        iDebugLogService!!.registerCallback(iDebugLogServiceCallbackStub)
                    } catch (e: RemoteException) {
                        release()
                        context.stopService(Intent(context, DebugLogService::class.java))
                        callback.onStopped()
                    }

                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                release()
                context.stopService(Intent(context, DebugLogService::class.java))
                callback.onStopped()
            }
        }

        interface Callback {
            fun onStarted(logList: List<String>)
            fun onStopped()
            fun onLog(msg: String)
            fun onSaved(success: Boolean, path: String)
        }

        init {
            bound = context.bindService(Intent(context, DebugLogService::class.java), serviceConnection, BIND_AUTO_CREATE)
        }

        fun start(): Boolean {
            synchronized(this) {
                if (iDebugLogService != null) {
                    try {
                        iDebugLogService!!.start()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun stop(): Boolean {
            synchronized(this) {
                if (iDebugLogService != null) {
                    try {
                        iDebugLogService!!.stop()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun clear(): Boolean {
            synchronized(this) {
                if (iDebugLogService != null) {
                    try {
                        iDebugLogService!!.clear()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun save(): Boolean {
            synchronized(this) {
                if (iDebugLogService != null) {
                    try {
                        iDebugLogService!!.save()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun release() {
            if (bound) {
                synchronized(this) {
                    if (iDebugLogService != null) {
                        try {
                            iDebugLogService!!.unregisterCallback(iDebugLogServiceCallbackStub)
                        } catch (e: RemoteException) {
                        }

                        iDebugLogService = null
                    }
                }
                bound = false
                context.unbindService(serviceConnection)
            }
            handler.removeCallbacksAndMessages(null)
        }

        fun isStarted() = isStarted
    }

    companion object {

        private const val MSG_STARTED = 0
        private const val MSG_STOPPED = 1
        private const val MSG_ONLOG = 2
        private const val MSG_SAVED = 3

        private const val MAX_LINES = 20000
    }
}