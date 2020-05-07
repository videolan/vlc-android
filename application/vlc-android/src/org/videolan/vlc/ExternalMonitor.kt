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
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.ACTION_CHECK_STORAGES
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.getFromMl
import org.videolan.tools.*
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.OtgAccess
import java.lang.ref.WeakReference

private const val TAG = "VLC/ExternalMonitor"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@SuppressLint("StaticFieldLeak")
object ExternalMonitor : BroadcastReceiver(), LifecycleObserver, CoroutineScope by MainScope() {

    private lateinit var ctx: Context
    private var registered = false

    private val actor = actor<DeviceAction>(capacity = Channel.CONFLATED) {
        for (action in channel) when (action){
            is MediaMounted -> {
                if (TextUtils.isEmpty(action.uuid)) return@actor
                val isNew = ctx.getFromMl {
                    val isNewForMl = !isDeviceKnown(action.uuid, action.path, true)
                    addDevice(action.uuid, action.path, true)
                    isNewForMl
                }
                if (isNew) notifyNewStorage(action)
            }
            is MediaUnmounted -> {
                delay(100L)
                Medialibrary.getInstance().removeDevice(action.uuid, action.path)
                storageChannel.safeOffer(action)
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
                OtgAccess.otgRoot.offer(null)
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                devices.remove(device)
            }
        }
    }

    private val storageChannel = BroadcastChannel<DeviceAction>(BUFFERED)
    val storageEvents : Flow<DeviceAction>
        get() = storageChannel.asFlow()
    private var storageObserver: WeakReference<Activity>? = null

    var devices = LiveDataset<UsbDevice>()

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun register() {
        if (registered) return
        val ctx = AppContextProvider.appContext
        val storageFilter = IntentFilter(Intent.ACTION_MEDIA_MOUNTED)
        val otgFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        storageFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        storageFilter.addAction(Intent.ACTION_MEDIA_EJECT)
        otgFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        storageFilter.addDataScheme("file")
        ctx.registerReceiver(this, storageFilter)
        ctx.registerReceiver(this, otgFilter)
        registered = true
        checkNewStorages(ctx)
    }

    @ExperimentalCoroutinesApi
    @ObsoleteCoroutinesApi
    private fun checkNewStorages(ctx: Context) {
        if (Medialibrary.getInstance().isStarted) {
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
        val ctx = AppContextProvider.appContext
        if (registered) try {
            ctx.unregisterReceiver(this)
        } catch (iae: IllegalArgumentException) {}
        registered = false
        devices.clear()
    }

    @Synchronized
    private fun notifyNewStorage(mediaMounted: MediaMounted) {
        val activity = storageObserver?.get() ?: return
        UiTools.newStorageDetected(activity, mediaMounted.path)
        storageChannel.safeOffer(mediaMounted)
    }

    @Synchronized
    fun subscribeStorageCb(observer: Activity) {
        storageObserver = WeakReference(observer)
    }

    @Synchronized
    fun unsubscribeStorageCb(observer: Activity) {
        if (storageObserver?.get() === observer) {
            storageObserver?.clear()
            storageObserver = null
        }
    }
}

fun containsDevice(devices: Array<String>, device: String): Boolean {
    if (devices.isNullOrEmpty()) return false
    for (dev in devices) if (device.startsWith(dev.removeFileProtocole())) return true
    return false
}

sealed class DeviceAction
class MediaMounted(val uri : Uri, val path : String = uri.path!!, val uuid : String = uri.lastPathSegment!!) : DeviceAction()
class MediaUnmounted(val uri : Uri, val path : String = uri.path!!, val uuid : String = uri.lastPathSegment!!) : DeviceAction()
