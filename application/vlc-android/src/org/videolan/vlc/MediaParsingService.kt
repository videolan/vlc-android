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
import android.annotation.TargetApi
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.stubs.StubMedialibrary
import org.videolan.resources.*
import org.videolan.resources.util.dbExists
import org.videolan.resources.util.launchForeground
import org.videolan.resources.util.registerReceiverCompat
import org.videolan.resources.util.startForegroundCompat
import org.videolan.resources.util.stopForegroundCompat
import org.videolan.tools.*
import org.videolan.vlc.gui.SendCrashActivity
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.*
import org.videolan.vlc.util.FileUtils

private const val TAG = "VLC/MediaParsingService"

class MediaParsingService : LifecycleService(), DevicesDiscoveryCb {

    private val dispatcher = ServiceLifecycleDispatcher(this)
    private lateinit var wakeLock: PowerManager.WakeLock

    private val binder = LocalBinder()
    private lateinit var medialibrary: Medialibrary
    private var reload = 0
    private var currentDiscovery: String? = null
    @Volatile private var lastNotificationTime = 0L
    @Volatile private var scanActivated = false

    private val settings by lazy { Settings.getInstance(this) }

    private var scanPaused = false

    @Volatile
    private var serviceLock = false
    @Volatile
    private var discoverTriggered = false
    private var inDiscovery = false
    private lateinit var actions : SendChannel<MLAction>
    private lateinit var notificationActor : SendChannel<Notification>
    var lastDone = -1
    var lastScheduled = -1

    private val exceptionHandler = when {
        BuildConfig.BETA -> Medialibrary.MedialibraryExceptionHandler { context, errMsg, _ ->
            val intent = Intent(applicationContext, SendCrashActivity::class.java).apply {
                putExtra(CRASH_ML_CTX, context)
                putExtra(CRASH_ML_MSG, errMsg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.wtf(TAG, "medialibrary reported unhandled exception: -----------------")
            // Lock the Medialibrary thread during DB extraction.
            runBlocking {
                SendCrashActivity.job = Job()
                try {
                    startActivity(intent)
                    SendCrashActivity.job?.join()
                } catch (e: Exception) {
                    SendCrashActivity.job = null
                }
            }
        }
        BuildConfig.DEBUG -> Medialibrary.MedialibraryExceptionHandler { context, errMsg, _ -> throw IllegalStateException("$context:\n$errMsg") }
        else -> null
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    @TargetApi(Build.VERSION_CODES.O)
    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        NotificationHelper.createNotificationChannels(applicationContext)
        if (AndroidUtil.isOOrLater) forceForeground()
        medialibrary = Medialibrary.getInstance()
        medialibrary.addDeviceDiscoveryCb(this@MediaParsingService)
        val filter = IntentFilter()
        filter.addAction(ACTION_PAUSE_SCAN)
        filter.addAction(ACTION_RESUME_SCAN)
        registerReceiverCompat(receiver, filter, false)
        val pm = applicationContext.getSystemService<PowerManager>()!!
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VLC:MediaParsingService")

        if (lastNotificationTime == 5L) stopService(Intent(applicationContext, MediaParsingService::class.java))
        Medialibrary.getState().observe(this, Observer { running ->
            lifecycleScope.launch {
                if (!running) {
                    delay(1000L)
                    if (!medialibrary.isWorking)
                        exitCommand()
                }
            }
        })
        medialibrary.exceptionHandler = exceptionHandler
        setupScope()
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun setupScope() {
        actions = lifecycleScope.actor(context = Dispatchers.IO, capacity = Channel.UNLIMITED) { processAction() }
        notificationActor = lifecycleScope.actor(capacity = Channel.UNLIMITED) {
            for (update in channel) when (update) {
                is Show -> showNotification(update.done, update.scheduled)
                is Error -> discoveryError.value = DiscoveryError(update.entryPoint)
                Hide -> hideNotification()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        dispatcher.onServicePreSuperOnBind()
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Set 1s delay before displaying scan icon
        // Except for Android 8+ which expects startForeground immediately
        if (AndroidUtil.isOOrLater) forceForeground()
        if (lastNotificationTime <= 0L) lastNotificationTime = if (AndroidUtil.isOOrLater) 0L else System.currentTimeMillis()
        super.onStartCommand(intent, flags, startId)
        dispatcher.onServicePreSuperOnStart()
        if (intent == null) {
            exitCommand()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_INIT -> {
                val upgrade = intent.getBooleanExtra(EXTRA_UPGRADE, false)
                val parse = intent.getBooleanExtra(EXTRA_PARSE, true)
                val removeDevices = intent.getBooleanExtra(EXTRA_REMOVE_DEVICE, false)
                setupMedialibrary(upgrade, parse, removeDevices)
            }
            ACTION_RELOAD -> actions.trySend(Reload(intent.getStringExtra(EXTRA_PATH)))
            ACTION_FORCE_RELOAD -> actions.trySend(ForceReload)
            ACTION_DISCOVER -> intent.getStringExtra(EXTRA_PATH)?.let { discover(it) }
            ACTION_DISCOVER_DEVICE -> intent.getStringExtra(EXTRA_PATH)?.let { discoverStorage(it) }
            ACTION_CHECK_STORAGES -> if (settings.getInt(KEY_MEDIALIBRARY_SCAN, -1) != ML_SCAN_OFF) actions.trySend(UpdateStorages) else exitCommand()
            else -> {
                exitCommand()
                return START_NOT_STICKY
            }
        }
        if (!wakeLock.isHeld) wakeLock.acquire()
        return START_NOT_STICKY
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun forceForeground() {
        val notification = NotificationHelper.createScanNotification(applicationContext, getString(R.string.loading_medialibrary), scanPaused, -1, -1)
        try {
            startForegroundCompat(43, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w("MediaParsingService", "ForegroundServiceStartNotAllowedException caught!")
            }
        }
    }

    private fun discoverStorage(path: String) {
        if (path.isEmpty()) {
            exitCommand()
            return
        }
        discoverTriggered = true
        actions.trySend(DiscoverStorage(path))
    }

    private fun discover(path: String) {
        if (path.isEmpty()) {
            exitCommand()
            return
        }
        actions.trySend(DiscoverFolder(path))
    }

    private fun addDeviceIfNeeded(path: String) {
        for (devicePath in medialibrary.devices) {
            if (path.startsWith(devicePath.removeFileScheme())) {
                return
            }
        }
        val isMainStorage = path.removeFileScheme().startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
        if (isMainStorage) {
            medialibrary.addDevice("main-storage", path, false)
        } else if (AndroidDevices.externalStorageDirectories.isNotEmpty()) {
            for (storagePath in AndroidDevices.externalStorageDirectories) {
                if (path.startsWith(storagePath)) {
                    val uuid = FileUtils.getFileNameFromPath(path)
                    if (uuid.isEmpty()) {
                        exitCommand()
                        return
                    }
                    medialibrary.addDevice(uuid, path, true)
                    for (folder in Medialibrary.getBanList()) {
                        medialibrary.banFolder(path + folder)
                    }
                }
            }
        } else {
            val uuid = FileUtils.getFileNameFromPath(path)
            medialibrary.addDevice(uuid, path, false)
        }
    }

    private fun reload(path: String?) {
        if (reload > 0) return
        if (path.isNullOrEmpty()) medialibrary.reload()
        else medialibrary.reload(path)
        val ctx = this@MediaParsingService
        lifecycleScope.launch(Dispatchers.IO) {
            cleanupWatchNextList(ctx)
        }
    }

    private fun setupMedialibrary(upgrade: Boolean, parse: Boolean, removeDevices:Boolean) {
        if (medialibrary.isInitiated) {
            medialibrary.resumeBackgroundOperations()
            if (parse && !scanActivated) actions.trySend(StartScan(upgrade))
        } else actions.trySend(Init(upgrade, parse, removeDevices))
    }

    private suspend fun initMedialib(parse: Boolean, context: Context, shouldInit: Boolean, upgrade: Boolean, removeDevices: Boolean) {
        checkNewDevicesForDialog(context, parse, removeDevices)
        if (upgrade) medialibrary.forceParserRetry()
        medialibrary.start()
        if (parse) startScan(shouldInit, upgrade)
        else exitCommand()
    }

    private suspend fun addDevices(context: Context, removeDevices: Boolean) {
        if (removeDevices) medialibrary.deleteRemovableDevices()
        val devices = DirectoryRepository.getInstance(context).getMediaDirectories()
        for (device in devices) {
            val isMainStorage = device == AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY
            val uuid = FileUtils.getFileNameFromPath(device)
            if (device.isEmpty() || uuid.isEmpty() || !device.scanAllowed()) continue
            medialibrary.addDevice(if (isMainStorage) "main-storage" else uuid, device, !isMainStorage)
        }
    }

    private suspend fun checkNewDevicesForDialog(context: Context, addExternal: Boolean, removeDevices: Boolean) {
        if (removeDevices) medialibrary.deleteRemovableDevices()
        val devices = DirectoryRepository.getInstance(context).getMediaDirectories()
        val knownDevices = if (AndroidDevices.watchDevices) medialibrary.devices else null
        for (device in devices) {
            if (device == AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY) continue
            val uuid = FileUtils.getFileNameFromPath(device)
            if (device.isEmpty() || uuid.isEmpty() || !device.scanAllowed()) continue
            if (addExternal && knownDevices?.contains(device) != true && !medialibrary.isDeviceKnown(uuid, device, true) && preselectedStorages.isEmpty()) showStorageNotification(device)
        }
    }

    private fun startScan(shouldInit: Boolean, upgrade: Boolean) {
        scanActivated = true
        if (MLServiceLocator.getLocatorMode() == MLServiceLocator.LocatorMode.TESTS) {
            (medialibrary as StubMedialibrary).loadJsonData(Util.readAsset("basic_stub.json", ""))
        }
        when {
            shouldInit -> {
                for (folder in Medialibrary.getBanList())
                    medialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + folder)
                if (preselectedStorages.isEmpty()) {
                    medialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                }
                else {
                    for (folder in preselectedStorages) {
                        medialibrary.discover(folder)
                    }
                    preselectedStorages.clear()
                }
            }
            upgrade -> {
                //refresh has already be done in [MediaParsingService.initMedialib]
                exitCommand()
            }
            settings.getBoolean(KEY_MEDIALIBRARY_AUTO_RESCAN, true) -> reload(null)
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
        val (devices, knownDevices) = withContext(Dispatchers.IO) {
            val devices = AndroidDevices.externalStorageDirectories
            Pair(devices, medialibrary.devices)
        }
        val missingDevices = knownDevices.toMutableList()
        missingDevices.remove("file://${AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY}")
        for (device in devices) {
            val uuid = FileUtils.getFileNameFromPath(device)
            if (device.isEmpty() || uuid.isEmpty() || !device.scanAllowed()) continue
            if (containsDevice(knownDevices, device)) {
                missingDevices.remove("file://$device")
                continue
            }
             val isNew = withContext(Dispatchers.IO) {
                 val isNewForML = !medialibrary.isDeviceKnown(uuid, device, true)
                 medialibrary.addDevice(uuid, device, true)
                 isNewForML
             }
            if (isNew) showStorageNotification(device)
        }
        withContext(Dispatchers.IO) { for (device in missingDevices) {
            val uri = device.toUri()
            Log.i("MediaParsingService", "Storage management: storage missing: ${uri.path}")
            medialibrary.removeDevice(uri.lastPathSegment, uri.path)
        } }
        serviceLock = false
        exitCommand()
    }

    private suspend fun showNotification(done:Int, scheduled: Int) {
        val currentTime = System.currentTimeMillis()
        lastNotificationTime = currentTime
        val parsing = (done.toFloat() / scheduled.toFloat() * 100F)
        val discovery = withContext(Dispatchers.Default) {
            val progressText = when {
                inDiscovery -> getString(R.string.ml_discovering) + " " + Uri.decode(currentDiscovery?.removeFileScheme())
                parsing > 0 -> TextUtils.separatedString(getString(R.string.ml_parse_media) + " " + String.format("%.02f",parsing) + "%", "$done/$scheduled")
                else -> getString(R.string.ml_parse_media)
            }
            if (!isActive) return@withContext ""
            if (lastNotificationTime != -1L) {
                try {
                    val notification = NotificationHelper.createScanNotification(applicationContext, progressText, scanPaused, scheduled, done)
                    try {
                        startForegroundCompat(43, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                            Log.w("MediaParsingService", "ForegroundServiceStartNotAllowedException caught!")
                        }
                    }
                } catch (ignored: IllegalArgumentException) {}
                progressText
            } else ""
        }
        showProgress(parsing, discovery)
    }

    private fun hideNotification() {
        lastNotificationTime = -1L
        stopForegroundCompat()
        showProgress(-1F, "")
    }

    override fun onDiscoveryStarted() {
        discoverTriggered = false
        inDiscovery =  true
    }

    override fun onDiscoveryProgress(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryProgress: $entryPoint")
        currentDiscovery = entryPoint
        if (::notificationActor.isInitialized) notificationActor.trySend(Show(-1, -1))
    }

    override fun onDiscoveryCompleted() {
        inDiscovery = false
    }

    override fun onDiscoveryFailed(entryPoint: String) {
        Log.e(TAG, "onDiscoveryFailed")
        notificationActor.trySend(Error(entryPoint))
    }

    override fun onParsingStatsUpdated(done: Int, scheduled:Int) {
        lastDone = done
        lastScheduled = scheduled
        val doneParsing = (done == scheduled)
        if (!doneParsing && ::notificationActor.isInitialized) notificationActor.trySend(Show(done, scheduled))
        else if (doneParsing && ::notificationActor.isInitialized) notificationActor.trySend(Hide)
    }

    override fun onReloadStarted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadStarted: $entryPoint")
        if (entryPoint.isEmpty()) ++reload
    }

    override fun onReloadCompleted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadCompleted $entryPoint")
        if (entryPoint.isEmpty()) --reload
        if (reload <= 0) exitCommand()
    }

    private fun exitCommand() {
        if (!medialibrary.isWorking && !serviceLock && !discoverTriggered) {
            lastNotificationTime = 0L
            if (wakeLock.isHeld) try {
                wakeLock.release()
            } catch (t: Throwable) {
                //catching here as isHeld is not thread safe
            }
            localBroadcastManager.sendBroadcast(Intent(ACTION_CONTENT_INDEXING))
            //todo reenable entry point when ready
            if (::notificationActor.isInitialized) notificationActor.trySend(Hide)
            //Delay service stop to ensure service goes foreground.
            // Otherwise, we get some RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()
            lifecycleScope.launch {
                delay(100L)
                stopService(Intent(applicationContext, MediaParsingService::class.java))
            }
        }
    }

    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        medialibrary.removeDeviceDiscoveryCb(this)
        unregisterReceiver(receiver)
        medialibrary.exceptionHandler = null
        super.onDestroy()
    }

    private inner class LocalBinder : Binder()

    private fun showProgress(parsing: Float, progressText: String) {
        if (parsing == -1F) {
            progress.value = null
            return
        }
        val status = progress.value
        progress.value = if (status === null) ScanProgress(parsing, progressText, inDiscovery) else status.copy(parsing = parsing, progressText = progressText, inDiscovery = inDiscovery)
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private suspend fun ActorScope<MLAction>.processAction() {
        for (action in channel) when (action) {
            is DiscoverStorage -> {
                for (folder in Medialibrary.getBanList()) medialibrary.banFolder(action.path + folder)
                medialibrary.discover(action.path)
            }
            is DiscoverFolder -> {
                addDeviceIfNeeded(action.path)
                medialibrary.discover(action.path)
            }
            is Init -> {
                if (medialibrary.isInitiated) {
                    exitCommand()
                } else {
                    val context = this@MediaParsingService
                    var shouldInit = !dbExists()
                    val constructed = medialibrary.construct(context)
                    if (!constructed) {
                        exitCommand()
                        return
                    }
                    addDevices(context, action.parse)
                    val initCode = medialibrary.init(context)
                    medialibrary.setLibVLCInstance((VLCInstance.getInstance(context) as LibVLC).instance)
                    medialibrary.setDiscoverNetworkEnabled(true)
                    if (initCode == Medialibrary.ML_INIT_DB_UNRECOVERABLE) {
                        throw IllegalStateException("Medialibrary DB file is corrupted and unrecoverable")
                    } else  if (initCode != Medialibrary.ML_INIT_ALREADY_INITIALIZED) {
                        shouldInit = shouldInit or (initCode == Medialibrary.ML_INIT_DB_RESET) or (initCode == Medialibrary.ML_INIT_DB_CORRUPTED)
                        if (initCode != Medialibrary.ML_INIT_FAILED) initMedialib(action.parse, context, shouldInit, action.upgrade, action.removeDevices)
                        else exitCommand()
                    } else exitCommand()
                }
            }
            is StartScan -> {
                scanActivated = true
                addDevices(this@MediaParsingService, removeDevices = false)
                startScan(false, action.upgrade)
            }
            UpdateStorages -> updateStorages()
            is Reload -> reload(action.path)
            ForceReload -> medialibrary.forceRescan()
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
            }
            notificationActor.trySend(Show(lastDone, lastScheduled))
        }
    }

    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    companion object {
        val progress = MutableLiveData<ScanProgress?>()
        val discoveryError = MutableLiveData<DiscoveryError>()
        val newStorages = MutableLiveData<MutableList<String>>()
        val preselectedStorages = mutableListOf<String>()
    }
}

data class ScanProgress(val parsing: Float, val progressText: String, val inDiscovery:Boolean)
data class DiscoveryError(val entryPoint: String)

fun Context.reloadLibrary() {
    launchForeground(Intent(ACTION_RELOAD, null, this, MediaParsingService::class.java))
}

fun Context.rescan() {
    launchForeground(Intent(ACTION_FORCE_RELOAD, null, this, MediaParsingService::class.java))
}

private sealed class MLAction
private class DiscoverStorage(val path: String) : MLAction()
private class DiscoverFolder(val path: String) : MLAction()
private class Init(val upgrade: Boolean, val parse: Boolean, val removeDevices:Boolean) : MLAction()
private class StartScan(val upgrade: Boolean) : MLAction()
private object UpdateStorages : MLAction()
private class Reload(val path: String?) : MLAction()
private object ForceReload : MLAction()

private sealed class Notification
private class Show(val done:Int, val scheduled:Int) : Notification()
private class Error(val entryPoint:String) : Notification()
private object Hide : Notification()
