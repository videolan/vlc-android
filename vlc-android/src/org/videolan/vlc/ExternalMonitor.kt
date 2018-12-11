/*
 * *************************************************************************
 *  NetworkMonitor.java
 * **************************************************************************
 *  Copyright © 2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.OtgAccess
import org.videolan.vlc.util.*
import videolan.org.commontools.LiveEvent
import java.lang.ref.WeakReference
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

private const val TAG = "VLC/ExternalMonitor"

@SuppressLint("StaticFieldLeak")
object ExternalMonitor : BroadcastReceiver(), LifecycleObserver, CoroutineScope {
    override val coroutineContext = Dispatchers.Main

    private lateinit var cm: ConnectivityManager
    private lateinit var ctx: Context
    private var registered = false

    private val actor = actor<DeviceAction>(capacity = Channel.CONFLATED) {
        for (action in channel) when (action){
            is MediaMounted -> {
                if (TextUtils.isEmpty(action.uuid)) return@actor
                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    if (!Settings.getInstance(ctx).getBoolean("ignore_${action.uuid}", false)) {
                        val ml = VLCApplication.getMLInstance()
                        val knownDevices = ctx.getFromMl { devices }
                        if (!containsDevice(knownDevices, action.path) && ml.addDevice(action.uuid, action.path, true)) {
                            notifyStorageChanges(action.path)
                        }
                    }
                } else if (AndroidDevices.watchDevices && action.path.scanAllowed()) {
                    val knownDevices = ctx.getFromMl { devices }
                    val ml = VLCApplication.getMLInstance()
                    val scan = !containsDevice(knownDevices, action.path) && ml.addDevice(action.uuid, action.path, true)
                    val intent = Intent(ctx, DialogActivity::class.java).apply {
                        setAction(DialogActivity.KEY_DEVICE)
                        putExtra(DialogActivity.EXTRA_PATH, action.path)
                        putExtra(DialogActivity.EXTRA_UUID, action.uuid)
                        putExtra(DialogActivity.EXTRA_SCAN, scan)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                }
            }
            is MediaUnmounted -> {
                delay(100L)
                VLCApplication.getMLInstance().removeDevice(action.uuid, action.path)

            }
        }
    }

    init {
        launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@ExternalMonitor)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!this::ctx.isInitialized) ctx = context.applicationContext
        val action = intent.action
        when (action) {
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                if (!this::cm.isInitialized)
                    cm = context.applicationContext.getSystemService(
                            Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = cm.activeNetworkInfo
                val isConnected = networkInfo != null && networkInfo.isConnected
                isMobile = isConnected && networkInfo!!.type == ConnectivityManager.TYPE_MOBILE
                isVPN = isConnected && updateVPNStatus()
                if (connected.value == null || isConnected != connected.value) {
                    connected.value = isConnected
                }
            }
            Intent.ACTION_MEDIA_MOUNTED -> {
                if (AndroidDevices.watchDevices || storageObserver != null && storageObserver!!.get() != null) {
                    intent.data?.let {
                        actor.offer(MediaMounted(it))
                        storagePlugged.postValue(it)
                    }
                }
            }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT -> {
                if (AndroidDevices.watchDevices || storageObserver != null && storageObserver!!.get() != null)
                    intent.data?.let {
                        actor.offer(MediaUnmounted(it))
                        storageUnplugged.postValue(it)
                    }
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    devices.add(device)
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                (OtgAccess.otgRoot as LiveEvent<Uri>).clear()
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                devices.remove(device)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateVPNStatus(): Boolean {
        if (AndroidUtil.isLolliPopOrLater) {
            for (network in cm.allNetworks) {
                val nc = cm.getNetworkCapabilities(network) ?: return false
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
            }
            return false
        } else {
            try {
                val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                while (networkInterfaces.hasMoreElements()) {
                    val networkInterface = networkInterfaces.nextElement()
                    val name = networkInterface.displayName
                    if (name.startsWith("ppp") || name.startsWith("tun") || name.startsWith("tap"))
                        return true
                }
            } catch (ignored: SocketException) {}
            return false
        }
    }

    val connected = MutableLiveData<Boolean>()
    val storageUnplugged = LiveEvent<Uri>()
    val storagePlugged = LiveEvent<Uri>()
    @Volatile
    var isMobile = true
        private set
    @Volatile
    var isVPN = false
        private set
    private var storageObserver: WeakReference<Activity>? = null

    var devices = LiveDataset<UsbDevice>()

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun register() {
        if (registered) return
        val ctx = VLCApplication.getAppContext()
        val networkFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val storageFilter = IntentFilter(Intent.ACTION_MEDIA_MOUNTED)
        val otgFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        storageFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        storageFilter.addAction(Intent.ACTION_MEDIA_EJECT)
        otgFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        storageFilter.addDataScheme("file")
        ctx.registerReceiver(this, networkFilter)
        ctx.registerReceiver(this, storageFilter)
        ctx.registerReceiver(this, otgFilter)
        registered = true
        checkNewStorages(ctx)
    }

    private fun checkNewStorages(ctx: Context) {
        if (VLCApplication.getMLInstance().isStarted) {
            val scanOpt = if (AndroidDevices.showTvUi(ctx)) ML_SCAN_ON
            else Settings.getInstance(ctx).getInt(KEY_MEDIALIBRARY_SCAN, -1)
            if (scanOpt == ML_SCAN_ON)
                AppScope.launch { ctx.startService(Intent(ACTION_CHECK_STORAGES, null, ctx, MediaParsingService::class.java)) }
        }
        val usbManager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        devices.add(ArrayList(usbManager.deviceList.values))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    internal fun unregister() {
        if (AndroidDevices.watchDevices) return
        val ctx = VLCApplication.getAppContext()
        ctx.unregisterReceiver(this)
        registered = false
        connected.value = false
        devices.clear()
    }

    @Synchronized
    private fun notifyStorageChanges(path: String?) {
        val activity = if (storageObserver != null) storageObserver!!.get() else null
        activity?.let { UiTools.newStorageDetected(it, path) }
    }

    val isConnected: Boolean
        get() {
            return connected.value ?: false
        }

    val isLan: Boolean
        get() {
            val status = connected.value
            return status != null && status && !isMobile
        }

    fun allowLan() = isLan || isVPN

    @Synchronized
    fun subscribeStorageCb(observer: Activity) {
        storageObserver = WeakReference(observer)
    }

    @Synchronized
    fun unsubscribeStorageCb(observer: Activity) {
        if (storageObserver != null && storageObserver!!.get() === observer) {
            storageObserver!!.clear()
            storageObserver = null
        }
    }

    fun containsDevice(devices: Array<String>, device: String): Boolean {
        if (Util.isArrayEmpty(devices)) return false
        for (dev in devices) if (device.startsWith(dev.removeFileProtocole())) return true
        return false
    }
}

private sealed class DeviceAction
private class MediaMounted(val uri : Uri, val path : String = uri.path, val uuid : String = uri.lastPathSegment) : DeviceAction()
private class MediaUnmounted(val uri : Uri, val path : String = uri.path, val uuid : String = uri.lastPathSegment) : DeviceAction()