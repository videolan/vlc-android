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
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.*
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.VLCOptions
import org.videolan.resources.util.launchForeground
import org.videolan.resources.util.startForegroundCompat
import org.videolan.resources.util.stopForegroundCompat
import org.videolan.tools.CloseableUtils
import org.videolan.tools.Logcat
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.util.Permissions
import java.io.*
import java.util.*

class DebugLogService : Service(), Logcat.Callback, Runnable {

    private var logcat: Logcat? = null
    private val logList = LinkedList<String>()
    private var saveThread: Thread? = null
    private val callbacks = RemoteCallbackList<IDebugLogServiceCallback>()
    private val binder = DebugLogServiceStub(this)

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
        startForegroundCompat(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
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

    @Synchronized
    fun stop() {
        logcat!!.stop()
        logcat = null
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
                bw.write("____________________________\r\n")
                bw.write("Useful info\r\n")
                bw.write("____________________________\r\n")
                bw.write("App version: ${BuildConfig.VLC_VERSION_CODE} / ${BuildConfig.VLC_VERSION_NAME}\r\n")
                bw.write("libvlc: ${BuildConfig.LIBVLC_VERSION}\r\n")
                bw.write("libvlc revision: ${getString(R.string.build_libvlc_revision)}\r\n")
                bw.write("vlc revision: ${getString(R.string.build_vlc_revision)}\r\n")
                bw.write("medialibrary: ${BuildConfig.ML_VERSION}\r\n")
                bw.write("Android version: ${Build.VERSION.SDK_INT}\r\n")
                bw.write("Device Model: ${Build.MANUFACTURER} - ${Build.MODEL}\r\n")
                bw.write("____________________________\r\n")
                bw.write("Permissions\r\n")
                bw.write("____________________________\r\n")
                bw.write("Can read: ${Permissions.canReadStorage(this)}\r\n")
                bw.write("Can write: ${Permissions.canWriteStorage(this)}\r\n")
                bw.write("Storage ALL access: ${Permissions.hasAllAccess(this)}\r\n")
                bw.write("Notifications: ${Permissions.canSendNotifications(this)}\r\n")
                bw.write("PiP Allowed: ${Permissions.isPiPAllowed(this)}\r\n")
                bw.write("____________________________\r\n")
                try {
                    bw.write("Changed settings:\r\n${PreferenceParser.getChangedPrefsString(this)}\r\n")
                } catch (e: Exception) {
                    bw.write("Cannot retrieve changed settings\r\n")
                    bw.write(Log.getStackTraceString(e))
                }
                bw.write("____________________________\r\n")
                bw.write("vlc options: ${VLCOptions.libOptions.joinToString(" ")}\r\n")
                bw.write("____________________________\r\n")
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
    constructor(private val mContext: Context, private val mCallback: Callback) {

        private var mBound = false
        private var mIDebugLogService: IDebugLogService? = null
        private val mHandler = Handler(Looper.getMainLooper())

        private val mICallback = object : IDebugLogServiceCallback.Stub() {
            @Throws(RemoteException::class)
            override fun onStopped() {
                mHandler.post { mCallback.onStopped() }
            }

            @Throws(RemoteException::class)
            override fun onStarted(logList: List<String>) {
                mHandler.post { mCallback.onStarted(logList) }
            }

            @Throws(RemoteException::class)
            override fun onLog(msg: String) {
                mHandler.post { mCallback.onLog(msg) }
            }

            @Throws(RemoteException::class)
            override fun onSaved(success: Boolean, path: String) {
                mHandler.post { mCallback.onSaved(success, path) }
            }
        }

        private val mServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                synchronized(this@Client) {
                    mIDebugLogService = IDebugLogService.Stub.asInterface(service)
                    try {
                        mIDebugLogService!!.registerCallback(mICallback)
                    } catch (e: RemoteException) {
                        release()
                        mContext.stopService(Intent(mContext, DebugLogService::class.java))
                        mCallback.onStopped()
                    }

                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                release()
                mContext.stopService(Intent(mContext, DebugLogService::class.java))
                mCallback.onStopped()
            }
        }

        interface Callback {
            fun onStarted(logList: List<String>)
            fun onStopped()
            fun onLog(msg: String)
            fun onSaved(success: Boolean, path: String)
        }

        init {
            mBound = mContext.bindService(Intent(mContext, DebugLogService::class.java), mServiceConnection, Context.BIND_AUTO_CREATE)
        }

        fun start(): Boolean {
            synchronized(this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService!!.start()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun stop(): Boolean {
            synchronized(this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService!!.stop()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun clear(): Boolean {
            synchronized(this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService!!.clear()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun save(): Boolean {
            synchronized(this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService!!.save()
                        return true
                    } catch (e: RemoteException) {
                    }

                }
                return false
            }
        }

        fun release() {
            if (mBound) {
                synchronized(this) {
                    if (mIDebugLogService != null) {
                        try {
                            mIDebugLogService!!.unregisterCallback(mICallback)
                        } catch (e: RemoteException) {
                        }

                        mIDebugLogService = null
                    }
                }
                mBound = false
                mContext.unbindService(mServiceConnection)
            }
            mHandler.removeCallbacksAndMessages(null)
        }
    }

    companion object {

        private const val MSG_STARTED = 0
        private const val MSG_STOPPED = 1
        private const val MSG_ONLOG = 2
        private const val MSG_SAVED = 3

        private const val MAX_LINES = 20000
    }
}