/**
 * **************************************************************************
 * MediaParsingService.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */
package org.videolan.vlc

import android.app.Service
import android.arch.lifecycle.MutableLiveData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.gui.wizard.startMLWizard
import org.videolan.vlc.util.*
import java.io.File
import java.util.*

private const val TAG = "VLC/MediaParsingService"
private const val NOTIFICATION_DELAY = 1000L

private const val SHOW_NOTIFICATION = 0
private const val HIDE_NOTIFICATION = 1
private const val UPDATE_NOTIFICATION_TIME = 2

class MediaParsingService : Service(), DevicesDiscoveryCb {
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private val binder = LocalBinder()
    private lateinit var medialibrary: Medialibrary
    private var parsing = 0
    private var reload = 0
    private var currentDiscovery: String? = null
    private var lastNotificationTime = 0L
    private var notificationJob: Job? = null
    private var scanActivated = false

    private val callsCtx = newSingleThreadContext("ml-calls")

    private val settings by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    internal var scanPaused = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_PAUSE_SCAN -> {
                    if (wakeLock.isHeld) wakeLock.release()
                    scanPaused = true
                    medialibrary.pauseBackgroundOperations()
                }
                Constants.ACTION_RESUME_SCAN -> {
                    if (!wakeLock.isHeld) wakeLock.acquire()
                    medialibrary.resumeBackgroundOperations()
                    scanPaused = false
                }
                Medialibrary.ACTION_IDLE -> if (intent.getBooleanExtra(Medialibrary.STATE_IDLE, true)) {
                    if (!scanPaused) {
                        exitCommand()
                        return
                    }
                }
            }
        }
    }

    @Volatile
    private var serviceLock = false
    private var wasWorking: Boolean = false
    internal val sb = StringBuilder()

    private val notificationActor by lazy {
        actor<Int>(UI, capacity = Channel.UNLIMITED, start = CoroutineStart.UNDISPATCHED) {
            for (update in channel) when (update) {
                SHOW_NOTIFICATION -> showNotification()
                HIDE_NOTIFICATION -> hideNotification()
                UPDATE_NOTIFICATION_TIME -> lastNotificationTime = System.currentTimeMillis()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        medialibrary = Medialibrary.getInstance()
        medialibrary.addDeviceDiscoveryCb(this@MediaParsingService)
        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_PAUSE_SCAN)
        filter.addAction(Constants.ACTION_RESUME_SCAN)
        registerReceiver(receiver, filter)
        localBroadcastManager.registerReceiver(receiver, IntentFilter(Medialibrary.ACTION_IDLE))
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        wakeLock.acquire()

        if (lastNotificationTime == 5L) stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            exitCommand()
            return Service.START_NOT_STICKY
        }
            // Set 1s delay before displaying scan icon
            // Except for Android 8+ which expects startForeground immediately

        if (AndroidUtil.isOOrLater && lastNotificationTime == 0L) forceForeground()
        else if (lastNotificationTime <= 0L) notificationActor.offer(UPDATE_NOTIFICATION_TIME)
        when (intent.action) {
            Constants.ACTION_INIT -> {
                val upgrade = intent.getBooleanExtra(Constants.EXTRA_UPGRADE, false)
                val parse = intent.getBooleanExtra(Constants.EXTRA_PARSE, true)
                setupMedialibrary(upgrade, parse)
            }
            Constants.ACTION_RELOAD -> reload(intent.getStringExtra(Constants.EXTRA_PATH))
            Constants.ACTION_DISCOVER -> discover(intent.getStringExtra(Constants.EXTRA_PATH))
            Constants.ACTION_DISCOVER_DEVICE -> discoverStorage(intent.getStringExtra(Constants.EXTRA_PATH))
            Constants.ACTION_CHECK_STORAGES -> if (scanActivated) updateStorages() else exitCommand()
            else -> {
                exitCommand()
                return Service.START_NOT_STICKY
            }
        }
        started.value = true
        return Service.START_NOT_STICKY
    }

    private fun forceForeground() {
        val ctx = this@MediaParsingService
        val notification = NotificationHelper.createScanNotification(ctx, getString(R.string.loading_medialibrary), false, scanPaused)
        startForeground(43, notification)
    }

    private fun discoverStorage(path: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "discoverStorage: $path")
        if (TextUtils.isEmpty(path)) {
            exitCommand()
            return
        }
        launch(callsCtx) {
            for (folder in Medialibrary.getBlackList()) medialibrary.banFolder(path + folder)
            medialibrary.discover(path)
        }
    }

    private fun discover(path: String) {
        if (TextUtils.isEmpty(path)) {
            exitCommand()
            return
        }
        launch(callsCtx) {
            addDeviceIfNeeded(path)
            medialibrary.discover(path)
        }
    }

    private fun addDeviceIfNeeded(path: String) {
        for (devicePath in medialibrary.devices) {
            if (path.startsWith(Strings.removeFileProtocole(devicePath))) {
                exitCommand()
                return
            }
        }
        for (storagePath in AndroidDevices.getExternalStorageDirectories()) {
            if (path.startsWith(storagePath)) {
                val uuid = FileUtils.getFileNameFromPath(path)
                if (TextUtils.isEmpty(uuid)) {
                    exitCommand()
                    return
                }
                medialibrary.addDevice(uuid, path, true)
                for (folder in Medialibrary.getBlackList())
                    medialibrary.banFolder(path + folder)
            }
        }
    }

    private fun reload(path: String?) {
        if (reload > 0) return
        if (TextUtils.isEmpty(path)) medialibrary.reload()
        else medialibrary.reload(path)
    }

    private fun setupMedialibrary(upgrade: Boolean, parse: Boolean) {
        val context = this
        if (medialibrary.isInitiated) {
            medialibrary.resumeBackgroundOperations()
            if (parse && !scanActivated) launch(callsCtx) {
                scanActivated = true
                addDevices(context, true)
                startScan(false, upgrade)
            }
            exitCommand()
        } else launch(callsCtx) {
            var shouldInit = !dbExists(context)
            val initCode = medialibrary.init(context)
            shouldInit = shouldInit or (initCode == Medialibrary.ML_INIT_DB_RESET)
            if (initCode != Medialibrary.ML_INIT_FAILED) initMedialib(parse, context, shouldInit, upgrade)
            else exitCommand()
        }
    }

    private fun initMedialib(parse: Boolean, context: Context, shouldInit: Boolean, upgrade: Boolean) {
        addDevices(context, parse)
        medialibrary.start()
        localBroadcastManager.sendBroadcast(Intent(VLCApplication.ACTION_MEDIALIBRARY_READY))
        if (parse) startScan(shouldInit, upgrade)
        else exitCommand()
    }

    private fun addDevices(context: Context, addExternal: Boolean) {
        val devices = ArrayList<String>()
        Collections.addAll(devices, *AndroidDevices.getMediaDirectories(context))
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        for (device in devices) {
            val isMainStorage = TextUtils.equals(device, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
            val uuid = FileUtils.getFileNameFromPath(device)
            if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid)) continue
            val isNew = (addExternal || isMainStorage) && medialibrary.addDevice(if (isMainStorage) "main-storage" else uuid, device, !isMainStorage)
            val isIgnored = sharedPreferences.getBoolean("ignore_$uuid", false)
            if (!isMainStorage && isNew && !isIgnored) showStorageNotification(device)
        }
    }

    private fun startScan(shouldInit: Boolean, upgrade: Boolean) {
        scanActivated = true
        when {
            shouldInit -> {
                for (folder in Medialibrary.getBlackList())
                    medialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + folder)
                medialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
            }
            upgrade -> {
                medialibrary.unbanFolder("${AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY}/WhatsApp/")
                medialibrary.banFolder("${AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY}/WhatsApp/Media/WhatsApp Animated Gifs/")
                medialibrary.forceParserRetry()
            }
            settings.getBoolean("auto_rescan", true) -> reload(null)
            else -> exitCommand()
        }
    }

    private fun showStorageNotification(device: String) {
        newStorages.value.apply {
            newStorages.postValue(if (this === null) mutableListOf(device) else this.apply { add(device) })
        }
    }

    private fun updateStorages() = launch(callsCtx) {
        serviceLock = true
        val ctx = VLCApplication.getAppContext()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val devices = AndroidDevices.getExternalStorageDirectories()
        val knownDevices = medialibrary.devices
        val missingDevices = Util.arrayToArrayList(knownDevices)
        missingDevices.remove("file://${AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY}")
        for (device in devices) {
            val uuid = FileUtils.getFileNameFromPath(device)
            if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid)) continue
            if (ExternalMonitor.containsDevice(knownDevices, device)) {
                missingDevices.remove("file://$device")
                continue
            }
            val isNew = medialibrary.addDevice(uuid, device, true)
            val isIgnored = sharedPreferences.getBoolean("ignore_$uuid", false)
            if (!isIgnored && isNew) showStorageNotification(device)
        }
        for (device in missingDevices) medialibrary.removeDevice(FileUtils.getFileNameFromPath(device))
        serviceLock = false
        exitCommand()
    }

    private suspend fun showNotification() {
        val currentTime = System.currentTimeMillis()
        if (lastNotificationTime == -1L || currentTime - lastNotificationTime < NOTIFICATION_DELAY) return
        lastNotificationTime = currentTime
        notificationJob = launch {
            sb.setLength(0)
            when {
                parsing > 0 -> sb.append(getString(R.string.ml_parse_media)).append(' ').append(parsing).append("%")
                currentDiscovery != null -> sb.append(getString(R.string.ml_discovering)).append(' ').append(Uri.decode(Strings.removeFileProtocole(currentDiscovery)))
                else -> sb.append(getString(R.string.ml_parse_media))
            }
            val progressText = sb.toString()
            val updateAction = wasWorking != medialibrary.isWorking
            if (updateAction) wasWorking = !wasWorking
            if (!isActive) return@launch
            val notification = NotificationHelper.createScanNotification(this@MediaParsingService, progressText, updateAction, scanPaused)
            if (lastNotificationTime != -1L) {
                showProgress(parsing, progressText)
                try {
                    startForeground(43, notification)
                } catch (ignored: IllegalArgumentException) {}
            }
        }
        notificationJob?.join()
    }

    private suspend fun hideNotification() {
        notificationJob?.cancelAndJoin()
        lastNotificationTime = -1L
        NotificationManagerCompat.from(this@MediaParsingService).cancel(43)
    }

    override fun onDiscoveryStarted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryStarted: $entryPoint")
    }

    override fun onDiscoveryProgress(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryProgress: $entryPoint")
        currentDiscovery = entryPoint
        notificationActor.offer(SHOW_NOTIFICATION)
    }

    override fun onDiscoveryCompleted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryCompleted: $entryPoint")
    }

    override fun onParsingStatsUpdated(percent: Int) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onParsingStatsUpdated: $percent")
        parsing = percent
        if (parsing != 100) notificationActor.offer(SHOW_NOTIFICATION)
    }

    override fun onReloadStarted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadStarted: $entryPoint")
        if (TextUtils.isEmpty(entryPoint)) ++reload
    }

    override fun onReloadCompleted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadCompleted $entryPoint")
        if (TextUtils.isEmpty(entryPoint)) --reload
    }

    private fun exitCommand() {
        if (!medialibrary.isWorking && !serviceLock) stopSelf()
    }

    override fun onDestroy() {
        progress.postValue(null)
        started.value = false
        notificationActor.offer(HIDE_NOTIFICATION)
        medialibrary.removeDeviceDiscoveryCb(this)
        unregisterReceiver(receiver)
        localBroadcastManager.unregisterReceiver(receiver)
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private inner class LocalBinder : Binder()

    private fun showProgress(parsing: Int, discovery: String) {
        val status = progress.value
        progress.postValue(if (status === null) ScanProgress(parsing, discovery) else status.copy(parsing = parsing, discovery = discovery))
    }

    companion object {
        val progress = MutableLiveData<ScanProgress>()
        val started = MutableLiveData<Boolean>()
        val newStorages = MutableLiveData<MutableList<String>>()
        var wizardShowing = false
    }
}

data class ScanProgress(val parsing: Int, val discovery: String)

fun reload(ctx: Context) {
    ContextCompat.startForegroundService(ctx, Intent(Constants.ACTION_RELOAD, null, ctx, MediaParsingService::class.java))
}

fun Context.startMedialibrary(firstRun: Boolean = false, upgrade: Boolean = false, parse: Boolean = true) = uiJob {
    if (Medialibrary.getInstance().isInitiated || !Permissions.canReadStorage(this@startMedialibrary)) return@uiJob
    val prefs = withContext(VLCIO) { VLCApplication.getSettings() ?: android.preference.PreferenceManager.getDefaultSharedPreferences(this@startMedialibrary) }
    val scanOpt = if (VLCApplication.showTvUi()) Constants.ML_SCAN_ON else prefs.getInt(Constants.KEY_MEDIALIBRARY_SCAN, -1)
    if (parse && scanOpt == -1) {
        if (dbExists(this@startMedialibrary)) prefs.edit().putInt(Constants.KEY_MEDIALIBRARY_SCAN, Constants.ML_SCAN_ON).apply()
        else {
            if (MediaParsingService.wizardShowing) return@uiJob
            MediaParsingService.wizardShowing = true
            startMLWizard()
        }
    } else {
        val intent = Intent(Constants.ACTION_INIT, null, this@startMedialibrary, MediaParsingService::class.java)
        ContextCompat.startForegroundService(this@startMedialibrary, intent
                .putExtra(Constants.EXTRA_FIRST_RUN, firstRun)
                .putExtra(Constants.EXTRA_UPGRADE, upgrade)
                .putExtra(Constants.EXTRA_PARSE, parse && scanOpt == Constants.ML_SCAN_ON))
    }
}

private suspend fun dbExists(context: Context) = withContext(VLCIO) {
    File(context.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME).exists()
}