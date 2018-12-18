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

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.gui.wizard.startMLWizard
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.*
import java.io.File
import java.util.*

private const val TAG = "VLC/MediaParsingService"
private const val NOTIFICATION_DELAY = 1000L

class MediaParsingService : Service(), DevicesDiscoveryCb, CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var localBroadcastManager: androidx.localbroadcastmanager.content.LocalBroadcastManager

    private val binder = LocalBinder()
    private lateinit var medialibrary: Medialibrary
    private var parsing = 0
    private var reload = 0
    private var currentDiscovery: String? = null
    @Volatile private var lastNotificationTime = 0L
    private var notificationJob: Job? = null
    @Volatile private var scanActivated = false

    private val settings by lazy { Settings.getInstance(this) }

    private var scanPaused = false

    @Volatile
    private var serviceLock = false
    private var wasWorking: Boolean = false
    internal val sb = StringBuilder()

    private val notificationActor by lazy {
        actor<Notification>(capacity = Channel.UNLIMITED) {
            for (update in channel) when (update) {
                Show -> showNotification()
                Hide -> hideNotification()
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        localBroadcastManager = androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
        medialibrary = Medialibrary.getInstance()
        medialibrary.addDeviceDiscoveryCb(this@MediaParsingService)
        val filter = IntentFilter()
        filter.addAction(ACTION_PAUSE_SCAN)
        filter.addAction(ACTION_RESUME_SCAN)
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
        else if (lastNotificationTime <= 0L) lastNotificationTime = System.currentTimeMillis()
        when (intent.action) {
            ACTION_INIT -> {
                val upgrade = intent.getBooleanExtra(EXTRA_UPGRADE, false)
                val parse = intent.getBooleanExtra(EXTRA_PARSE, true)
                setupMedialibrary(upgrade, parse)
            }
            ACTION_RELOAD -> reload(intent.getStringExtra(EXTRA_PATH))
            ACTION_FORCE_RELOAD -> medialibrary.forceRescan()
            ACTION_DISCOVER -> discover(intent.getStringExtra(EXTRA_PATH))
            ACTION_DISCOVER_DEVICE -> discoverStorage(intent.getStringExtra(EXTRA_PATH))
            ACTION_CHECK_STORAGES -> if (scanActivated) actions.offer(UpdateStorages) else exitCommand()
            else -> {
                exitCommand()
                return Service.START_NOT_STICKY
            }
        }
        return Service.START_NOT_STICKY
    }

    private fun forceForeground() {
        val ctx = this@MediaParsingService
        val notification = NotificationHelper.createScanNotification(ctx, getString(R.string.loading_medialibrary), false, scanPaused)
        startForeground(43, notification)
    }

    private fun discoverStorage(path: String) {
        if (TextUtils.isEmpty(path)) {
            exitCommand()
            return
        }
        actions.offer(DiscoverStorage(path))
    }

    private fun discover(path: String) {
        if (TextUtils.isEmpty(path)) {
            exitCommand()
            return
        }
        actions.offer(DiscoverFolder(path))
    }

    private fun addDeviceIfNeeded(path: String) {
        for (devicePath in medialibrary.devices) {
            if (path.startsWith(devicePath.removeFileProtocole())) {
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
        if (medialibrary.isInitiated) {
            medialibrary.resumeBackgroundOperations()
            if (parse && !scanActivated) actions.offer(StartScan(upgrade))
        } else actions.offer(Init(upgrade, parse))
    }

    private suspend fun initMedialib(parse: Boolean, context: Context, shouldInit: Boolean, upgrade: Boolean) {
        addDevices(context, parse)
        if (upgrade) medialibrary.forceParserRetry()
        medialibrary.start()
        localBroadcastManager.sendBroadcast(Intent(VLCApplication.ACTION_MEDIALIBRARY_READY))
        if (parse) startScan(shouldInit, upgrade)
        else exitCommand()
    }

    private suspend fun addDevices(context: Context, addExternal: Boolean) {
        val devices = ArrayList<String>()
        Collections.addAll(devices, *DirectoryRepository.getInstance(context).getMediaDirectories())
        val sharedPreferences = Settings.getInstance(context)
        for (device in devices) {
            val isMainStorage = TextUtils.equals(device, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
            val uuid = FileUtils.getFileNameFromPath(device)
            if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid) || !device.scanAllowed()) continue
            val isNew = (isMainStorage || addExternal)
                    && medialibrary.addDevice(if (isMainStorage) "main-storage" else uuid, device, !isMainStorage)
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

    private suspend fun updateStorages() {
        serviceLock = true
        val ctx = applicationContext
        val (sharedPreferences, devices, knownDevices) = withContext(Dispatchers.IO) {
            val sharedPreferences = Settings.getInstance(ctx)
            val devices = AndroidDevices.getExternalStorageDirectories()
            Triple(sharedPreferences, devices, medialibrary.devices)
        }
        val missingDevices = Util.arrayToArrayList(knownDevices)
        missingDevices.remove("file://${AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY}")
        for (device in devices) {
            val uuid = FileUtils.getFileNameFromPath(device)
            if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid) || !device.scanAllowed()) continue
            if (ExternalMonitor.containsDevice(knownDevices, device)) {
                missingDevices.remove("file://$device")
                continue
            }
            val isNew = withContext(Dispatchers.IO) { medialibrary.addDevice(uuid, device, true) }
            val isIgnored = sharedPreferences.getBoolean("ignore_$uuid", false)
            if (!isIgnored && isNew) showStorageNotification(device)
        }
        withContext(Dispatchers.IO) { for (device in missingDevices) {
            val uri = Uri.parse(device)
            medialibrary.removeDevice(uri.lastPathSegment, uri.path)
        } }
        serviceLock = false
        exitCommand()
    }

    private suspend fun showNotification() {
        val currentTime = System.currentTimeMillis()
        if (lastNotificationTime == -1L || currentTime - lastNotificationTime < NOTIFICATION_DELAY) return
        lastNotificationTime = currentTime
        val discovery = withContext(Dispatchers.Default) {
            sb.setLength(0)
            when {
                parsing > 0 -> sb.append(getString(R.string.ml_parse_media)).append(' ').append(parsing).append("%")
                currentDiscovery != null -> sb.append(getString(R.string.ml_discovering)).append(' ').append(Uri.decode(currentDiscovery?.removeFileProtocole()))
                else -> sb.append(getString(R.string.ml_parse_media))
            }
            val progressText = sb.toString()
            val updateAction = wasWorking != medialibrary.isWorking
            if (updateAction) wasWorking = !wasWorking
            if (!isActive) return@withContext ""
            val notification = NotificationHelper.createScanNotification(this@MediaParsingService, progressText, updateAction, scanPaused)
            if (lastNotificationTime != -1L) {
                try {
                    startForeground(43, notification)
                } catch (ignored: IllegalArgumentException) {}
                progressText
            } else ""
        }
        showProgress(parsing, discovery)
    }

    private suspend fun hideNotification() {
        notificationJob?.cancelAndJoin()
        lastNotificationTime = -1L
        NotificationManagerCompat.from(this@MediaParsingService).cancel(43)
        showProgress(-1, "")
    }

    override fun onDiscoveryStarted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryStarted: $entryPoint")
    }

    override fun onDiscoveryProgress(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryProgress: $entryPoint")
        currentDiscovery = entryPoint
        notificationActor.offer(Show)
    }

    override fun onDiscoveryCompleted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryCompleted: $entryPoint")
    }

    override fun onParsingStatsUpdated(percent: Int) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onParsingStatsUpdated: $percent")
        parsing = percent
        if (parsing != 100) notificationActor.offer(Show)
    }

    override fun onReloadStarted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadStarted: $entryPoint")
        if (TextUtils.isEmpty(entryPoint)) ++reload
    }

    override fun onReloadCompleted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadCompleted $entryPoint")
        if (TextUtils.isEmpty(entryPoint)) --reload
    }

    private fun exitCommand() = launch {
        if (!medialibrary.isWorking && !serviceLock) {
            lastNotificationTime = 0L
            stopSelf()
        }
    }

    override fun onDestroy() {
        notificationActor.offer(Hide)
        medialibrary.removeDeviceDiscoveryCb(this)
        unregisterReceiver(receiver)
        localBroadcastManager.unregisterReceiver(receiver)
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private inner class LocalBinder : Binder()

    private fun showProgress(parsing: Int, discovery: String) {
        if (parsing == -1) {
            progress.value = null
            return
        }
        val status = progress.value
        progress.value = if (status === null) ScanProgress(parsing, discovery) else status.copy(parsing = parsing, discovery = discovery)
    }

    private val actions = actor<MLAction>(context = Dispatchers.IO, capacity = Channel.UNLIMITED) {
        for (action in channel) when (action) {
            is DiscoverStorage -> {
                for (folder in Medialibrary.getBlackList()) medialibrary.banFolder(action.path + folder)
                medialibrary.discover(action.path)
            }
            is DiscoverFolder -> {
                addDeviceIfNeeded(action.path)
                medialibrary.discover(action.path)
            }
            is Init -> {
                val context = this@MediaParsingService
                var shouldInit = !dbExists(context)
                val initCode = medialibrary.init(context)
                shouldInit = shouldInit or (initCode == Medialibrary.ML_INIT_DB_RESET)
                if (initCode != Medialibrary.ML_INIT_FAILED) initMedialib(action.parse, context, shouldInit, action.upgrade)
                else exitCommand()
            }
            is StartScan -> {
                scanActivated = true
                addDevices(this@MediaParsingService, true)
                startScan(false, action.upgrade)
            }
            UpdateStorages -> updateStorages()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("WakelockTimeout")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE_SCAN -> {
                    if (wakeLock.isHeld) wakeLock.release()
                    scanPaused = true
                    medialibrary.pauseBackgroundOperations()
                }
                ACTION_RESUME_SCAN -> {
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

    companion object {
        val progress = MutableLiveData<ScanProgress>()
        val newStorages = MutableLiveData<MutableList<String>>()
        var wizardShowing = false
    }
}

data class ScanProgress(val parsing: Int, val discovery: String)

fun Context.reload() {
    ContextCompat.startForegroundService(this, Intent(ACTION_RELOAD, null, this, MediaParsingService::class.java))
}

fun Context.rescan() {
    ContextCompat.startForegroundService(this, Intent(ACTION_FORCE_RELOAD, null, this, MediaParsingService::class.java))
}

fun Context.startMedialibrary(firstRun: Boolean = false, upgrade: Boolean = false, parse: Boolean = true) = AppScope.launch {
    if (Medialibrary.getInstance().isStarted || !Permissions.canReadStorage(this@startMedialibrary)) return@launch
    val prefs = withContext(Dispatchers.IO) { Settings.getInstance(this@startMedialibrary) }
    val scanOpt = if (AndroidDevices.showTvUi(this@startMedialibrary)) ML_SCAN_ON else prefs.getInt(KEY_MEDIALIBRARY_SCAN, -1)
    if (parse && scanOpt == -1) {
        if (dbExists(this@startMedialibrary)) prefs.edit().putInt(KEY_MEDIALIBRARY_SCAN, ML_SCAN_ON).apply()
        else {
            if (MediaParsingService.wizardShowing) return@launch
            MediaParsingService.wizardShowing = true
            startMLWizard()
            return@launch
        }
    }
    val intent = Intent(ACTION_INIT, null, this@startMedialibrary, MediaParsingService::class.java)
    ContextCompat.startForegroundService(this@startMedialibrary, intent
            .putExtra(EXTRA_FIRST_RUN, firstRun)
            .putExtra(EXTRA_UPGRADE, upgrade)
            .putExtra(EXTRA_PARSE, parse && scanOpt != ML_SCAN_OFF))
}

private suspend fun dbExists(context: Context) = withContext(Dispatchers.IO) {
    File(context.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME).exists()
}

private sealed class MLAction
private class DiscoverStorage(val path: String) : MLAction()
private class DiscoverFolder(val path: String) : MLAction()
private class Init(val upgrade: Boolean, val parse: Boolean) : MLAction()
private class StartScan(val upgrade: Boolean) : MLAction()
private object UpdateStorages : MLAction()

private sealed class Notification
private object Show : Notification()
private object Hide : Notification()
