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
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.experimental.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.util.*
import java.io.File
import java.util.*

class MediaParsingService : Service(), DevicesDiscoveryCb {
    private lateinit var mWakeLock: PowerManager.WakeLock
    private lateinit var mLocalBroadcastManager: LocalBroadcastManager

    private val mBinder = LocalBinder()
    private lateinit var mMedialibrary: Medialibrary
    private var mParsing = 0
    private var mReload = 0
    private var mCurrentDiscovery: String? = null
    private var mLastNotificationTime = 0L
    private var notificationJob: Job? = null

    private val callsCtx = newSingleThreadContext("ml-calls")
    private val notificationCtx = newSingleThreadContext("ml-notif")

    internal var mScanPaused = false
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_PAUSE_SCAN -> {
                    if (mWakeLock.isHeld) mWakeLock.release()
                    mScanPaused = true
                    mMedialibrary.pauseBackgroundOperations()
                }
                Constants.ACTION_RESUME_SCAN -> {
                    if (!mWakeLock.isHeld) mWakeLock.acquire()
                    mMedialibrary.resumeBackgroundOperations()
                    mScanPaused = false
                }
                Medialibrary.ACTION_IDLE -> if (intent.getBooleanExtra(Medialibrary.STATE_IDLE, true)) {
                    if (!mScanPaused) {
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

    override fun onCreate() {
        super.onCreate()
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this)
        mMedialibrary = Medialibrary.getInstance()
        mMedialibrary.addDeviceDiscoveryCb(this@MediaParsingService)
        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_PAUSE_SCAN)
        filter.addAction(Constants.ACTION_RESUME_SCAN)
        registerReceiver(mReceiver, filter)
        mLocalBroadcastManager.registerReceiver(mReceiver, IntentFilter(Medialibrary.ACTION_IDLE))
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        mWakeLock.acquire()
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            exitCommand()
            return Service.START_NOT_STICKY
        }
        synchronized(this@MediaParsingService) {
            // Set 1s delay before displaying scan icon
            // Except for Android 8+ which expects startForeground immediately
            if (mLastNotificationTime <= 0L)
                mLastNotificationTime = if (AndroidUtil.isOOrLater) 0L else System.currentTimeMillis()
            if (AndroidUtil.isOOrLater)
                showNotification()
        }
        when (intent.action) {
            Constants.ACTION_INIT -> setupMedialibrary(intent.getBooleanExtra(Constants.EXTRA_UPGRADE, false))
            Constants.ACTION_RELOAD -> reload(intent.getStringExtra(Constants.EXTRA_PATH))
            Constants.ACTION_DISCOVER -> discover(intent.getStringExtra(Constants.EXTRA_PATH))
            Constants.ACTION_DISCOVER_DEVICE -> discoverStorage(intent.getStringExtra(Constants.EXTRA_PATH))
            Constants.ACTION_CHECK_STORAGES -> updateStorages()
            else -> {
                exitCommand()
                return Service.START_NOT_STICKY
            }
        }
        started.value = true
        return Service.START_NOT_STICKY
    }

    private fun discoverStorage(path: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "discoverStorage: $path")
        if (TextUtils.isEmpty(path)) {
            exitCommand()
            return
        }
        launch(callsCtx) {
            for (folder in Medialibrary.getBlackList())
                mMedialibrary.banFolder(path + folder)
            mMedialibrary.discover(path)
        }
    }

    private fun discover(path: String) {
        if (TextUtils.isEmpty(path)) {
            exitCommand()
            return
        }
        launch(callsCtx) {
            addDeviceIfNeeded(path)
            mMedialibrary.discover(path)
        }
    }

    private fun addDeviceIfNeeded(path: String) {
        for (devicePath in mMedialibrary.devices) {
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
                mMedialibrary.addDevice(uuid, path, true)
                for (folder in Medialibrary.getBlackList())
                    mMedialibrary.banFolder(path + folder)
            }
        }
    }

    private fun reload(path: String?) {
        if (mReload > 0)
            return
        if (TextUtils.isEmpty(path))
            mMedialibrary.reload()
        else
            mMedialibrary.reload(path)
    }

    private fun setupMedialibrary(upgrade: Boolean) {
        if (mMedialibrary.isInitiated) {
            mMedialibrary.resumeBackgroundOperations()
            exitCommand()
        } else {
            val context = applicationContext
            launch(callsCtx) {
                    var shouldInit = !File(this@MediaParsingService.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME).exists()
                    val initCode = mMedialibrary.init(context)
                    shouldInit = shouldInit or (initCode == Medialibrary.ML_INIT_DB_RESET)
                    if (initCode != Medialibrary.ML_INIT_FAILED) {
                        val devices = ArrayList<String>()
                        Collections.addAll(devices, *AndroidDevices.getMediaDirectories(context))
                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                        for (device in devices) {
                            val isMainStorage = TextUtils.equals(device, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                            val uuid = FileUtils.getFileNameFromPath(device)
                            if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid))
                                continue
                            val isNew = mMedialibrary.addDevice(if (isMainStorage) "main-storage" else uuid, device, !isMainStorage)
                            val isIgnored = sharedPreferences.getBoolean("ignore_$uuid", false)
                            if (!isMainStorage && isNew && !isIgnored) showStorageNotification(device)
                        }
                        mMedialibrary.start()
                        mLocalBroadcastManager.sendBroadcast(Intent(VLCApplication.ACTION_MEDIALIBRARY_READY))
                        when {
                            shouldInit -> {
                                for (folder in Medialibrary.getBlackList())
                                    mMedialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + folder)
                                mMedialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                            }
                            upgrade -> {
                                mMedialibrary.unbanFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/WhatsApp/")
                                mMedialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/WhatsApp/Media/WhatsApp Animated Gifs/")
                                mMedialibrary.forceParserRetry()
                            }
                            PreferenceManager.getDefaultSharedPreferences(this@MediaParsingService).getBoolean("auto_rescan", true) -> reload(null)
                            else -> exitCommand()
                        }
                    }
            }
        }
    }

    private fun showStorageNotification(device: String) {
        newStorages.value.apply {
            newStorages.postValue(if (this === null) mutableListOf(device) else this.apply { add(device) })
        }
    }

    private fun updateStorages() {
        launch(callsCtx) {
            serviceLock = true
            val ctx = VLCApplication.getAppContext()
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
            val devices = AndroidDevices.getExternalStorageDirectories()
            val knownDevices = mMedialibrary.devices
            val missingDevices = Util.arrayToArrayList(knownDevices)
            missingDevices.remove("file://" + AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
            for (device in devices) {
                val uuid = FileUtils.getFileNameFromPath(device)
                if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid))
                    continue
                if (ExternalMonitor.containsDevice(knownDevices, device)) {
                    missingDevices.remove("file://$device")
                    continue
                }
                val isNew = mMedialibrary.addDevice(uuid, device, true)
                val isIgnored = sharedPreferences.getBoolean("ignore_$uuid", false)
                if (!isIgnored && isNew) showStorageNotification(device)
            }
            for (device in missingDevices) mMedialibrary.removeDevice(FileUtils.getFileNameFromPath(device))
            serviceLock = false
            exitCommand()
        }
    }

    private fun showNotification() {
        val currentTime = System.currentTimeMillis()
        synchronized(this@MediaParsingService) {
            if (mLastNotificationTime == -1L || currentTime - mLastNotificationTime < NOTIFICATION_DELAY)
                return
            mLastNotificationTime = currentTime
        }
        notificationJob = launch(notificationCtx) {
            if (!isActive) return@launch
            sb.setLength(0)
            when {
                mParsing > 0 -> sb.append(getString(R.string.ml_parse_media)).append(' ').append(mParsing).append("%")
                mCurrentDiscovery != null -> sb.append(getString(R.string.ml_discovering)).append(' ').append(Uri.decode(Strings.removeFileProtocole(mCurrentDiscovery)))
                else -> sb.append(getString(R.string.ml_parse_media))
            }
            val progressText = sb.toString()
            val updateAction = wasWorking != mMedialibrary.isWorking
            if (updateAction) wasWorking = !wasWorking
            if (!isActive) return@launch
            val notification = NotificationHelper.createScanNotification(this@MediaParsingService, progressText, updateAction, mScanPaused)
            synchronized(this@MediaParsingService) {
                if (mLastNotificationTime != -1L) {
                    showProgress(mParsing, progressText)
                    try {
                        startForeground(43, notification)
                    } catch (ignored: IllegalArgumentException) {}
                }
            }
        }
    }

    private fun hideNotification() {
        launch(Unconfined) {
            notificationJob?.cancelAndJoin()
            synchronized(this@MediaParsingService) {
                mLastNotificationTime = -1L
                NotificationManagerCompat.from(this@MediaParsingService).cancel(43)
            }
        }
    }

    override fun onDiscoveryStarted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryStarted: $entryPoint")
    }

    override fun onDiscoveryProgress(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryProgress: $entryPoint")
        mCurrentDiscovery = entryPoint
        showNotification()
    }

    override fun onDiscoveryCompleted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onDiscoveryCompleted: $entryPoint")
    }

    override fun onParsingStatsUpdated(percent: Int) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onParsingStatsUpdated: $percent")
        mParsing = percent
        if (mParsing != 100) showNotification()
    }

    override fun onReloadStarted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadStarted: $entryPoint")
        if (TextUtils.isEmpty(entryPoint)) ++mReload
    }

    override fun onReloadCompleted(entryPoint: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, "onReloadCompleted $entryPoint")
        if (TextUtils.isEmpty(entryPoint)) --mReload
    }

    private fun exitCommand() {
        if (!mMedialibrary.isWorking && !serviceLock) stopSelf()
    }

    override fun onDestroy() {
        progress.postValue(null)
        started.value = false
        hideNotification()
        mMedialibrary.removeDeviceDiscoveryCb(this)
        unregisterReceiver(mReceiver)
        mLocalBroadcastManager.unregisterReceiver(mReceiver)
        if (mWakeLock.isHeld) mWakeLock.release()
        super.onDestroy()
    }

    private inner class LocalBinder : Binder()

    private fun showProgress(parsing: Int, discovery: String) {
        val status = progress.value
        progress.postValue(if (status === null) ScanProgress(parsing, discovery) else status.copy(parsing = parsing, discovery = discovery))
    }

    companion object {
        private const val TAG = "VLC/MediaParsingService"
        private const val NOTIFICATION_DELAY = 1000L
        val progress = MutableLiveData<ScanProgress>()
        val started = MutableLiveData<Boolean>()
        val newStorages = MutableLiveData<MutableList<String>>()
    }
}

data class ScanProgress(val parsing: Int, val discovery: String)
