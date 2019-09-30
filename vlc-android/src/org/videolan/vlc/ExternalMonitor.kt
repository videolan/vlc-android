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
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.OtgAccess
import org.videolan.vlc.util.*
import videolan.org.commontools.LiveEvent
import java.lang.ref.WeakReference
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

private const val TAG = "VLC/ExternalMonitor"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
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
                if (ctx.getFromMl { addDevice(action.uuid, action.path, true) }) {
                    notifyNewStorage(action.uri, action.path)
                }
            }
            is MediaUnmounted -> {
                delay(100L)
                AbstractMedialibrary.getInstance().removeDevice(action.uuid, action.path)
                if (storageUnplugged.hasActiveObservers()) storageUnplugged.postValue(action.uri)
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
        when (intent.action) {
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
            Intent.ACTION_MEDIA_MOUNTED -> intent.data?.let { actor.offer(MediaMounted(it)) }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT -> intent.data?.let {
                actor.offer(MediaUnmounted(it))
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
        val ctx = VLCApplication.appContext
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

    @ExperimentalCoroutinesApi
    @ObsoleteCoroutinesApi
    private fun checkNewStorages(ctx: Context) {
        if (AbstractMedialibrary.getInstance().isStarted) {
            val scanOpt = if (Settings.showTvUi) ML_SCAN_ON
            else Settings.getInstance(ctx).getInt(KEY_MEDIALIBRARY_SCAN, -1)
            if (scanOpt == ML_SCAN_ON)
                AppScope.launch { ContextCompat.startForegroundService(ctx,Intent(ACTION_CHECK_STORAGES, null, ctx, MediaParsingService::class.java)) }
        }
        val usbManager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        devices.add(ArrayList(usbManager.deviceList.values))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    internal fun unregister() {
        val ctx = VLCApplication.appContext
        if (registered) try {
            ctx.unregisterReceiver(this)
        } catch (iae: IllegalArgumentException) {}
        registered = false
        connected.value = false
        devices.clear()
    }

    @Synchronized
    private fun notifyNewStorage(uri: Uri, path: String) {
        val activity = storageObserver?.get() ?: return
        UiTools.newStorageDetected(activity, path)
        if (storagePlugged.hasActiveObservers()) storagePlugged.postValue(uri)
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
}

fun containsDevice(devices: Array<String>, device: String): Boolean {
    if (Util.isArrayEmpty(devices)) return false
    for (dev in devices) if (device.startsWith(dev.removeFileProtocole())) return true
    return false
}

private sealed class DeviceAction
private class MediaMounted(val uri : Uri, val path : String = uri.path, val uuid : String = uri.lastPathSegment) : DeviceAction()
private class MediaUnmounted(val uri : Uri, val path : String = uri.path, val uuid : String = uri.lastPathSegment) : DeviceAction()