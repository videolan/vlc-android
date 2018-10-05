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

package org.videolan.vlc;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.helpers.hf.OtgAccess;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.LiveDataset;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;

import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import videolan.org.commontools.LiveEvent;

public class ExternalMonitor extends BroadcastReceiver implements LifecycleObserver {
    public final static String TAG = "VLC/ExternalMonitor";
    public static volatile MutableLiveData<Boolean> connected = new MutableLiveData<>();
    private static volatile boolean mobile = true;
    private static volatile boolean vpn = false;
    private static final ExternalMonitor instance = new ExternalMonitor();
    private static WeakReference<Activity> storageObserver = null;

    public static LiveDataset<UsbDevice> devices = new LiveDataset<>();

    public ExternalMonitor() {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    static void register() {
        final Context ctx = VLCApplication.getAppContext();
        final IntentFilter networkFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        final IntentFilter storageFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        final IntentFilter otgFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        storageFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        otgFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        storageFilter.addDataScheme("file");
        ctx.registerReceiver(instance, networkFilter);
        ctx.registerReceiver(instance, storageFilter);
        ctx.registerReceiver(instance, otgFilter);
        checkNewStorages(ctx);
    }

    private static void checkNewStorages(final Context ctx) {
        if (VLCApplication.getMLInstance().isInitiated()) {
            final int scanOpt = AndroidDevices.showTvUi(ctx) ? Constants.ML_SCAN_ON
                    : Settings.INSTANCE.getInstance(ctx).getInt(Constants.KEY_MEDIALIBRARY_SCAN, -1);
            if (scanOpt == Constants.ML_SCAN_ON) new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    ctx.startService(new Intent(Constants.ACTION_CHECK_STORAGES, null, ctx, MediaParsingService.class));
                }
            });
        }
        final UsbManager usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        devices.add(new ArrayList<>(usbManager.getDeviceList().values()));
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    static void unregister() {
        final Context ctx = VLCApplication.getAppContext();
        ctx.unregisterReceiver(instance);
        connected.setValue(false);
        devices.clear();
    }

    public static ExternalMonitor getInstance() {
        return instance;
    }

    private ConnectivityManager cm = null;
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {
            case ConnectivityManager.CONNECTIVITY_ACTION:
                if (cm == null) cm = (ConnectivityManager) VLCApplication.getAppContext().getSystemService(
                            Context.CONNECTIVITY_SERVICE);
                final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                final boolean isConnected = networkInfo != null && networkInfo.isConnected();
                mobile = isConnected && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
                vpn = isConnected && updateVPNStatus();
                if (connected.getValue() == null || isConnected != connected.getValue()) {
                    connected.setValue(isConnected);
                }
                break;
            case Intent.ACTION_MEDIA_MOUNTED:
                if (storageObserver != null && storageObserver.get() != null)
                    mHandler.obtainMessage(ACTION_MEDIA_MOUNTED, intent.getData()).sendToTarget();
                break;
            case Intent.ACTION_MEDIA_UNMOUNTED:
            case Intent.ACTION_MEDIA_EJECT:
                if (storageObserver != null && storageObserver.get() != null)
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(ACTION_MEDIA_UNMOUNTED, intent.getData()), 100);
                break;
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    devices.add(device);
                }
                break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                    ((LiveEvent<Uri>)OtgAccess.Companion.getOtgRoot()).clear();
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    devices.remove(device);
                }
                break;
        }
    }

    private static final int ACTION_MEDIA_MOUNTED = 1337;
    private static final int ACTION_MEDIA_UNMOUNTED = 1338;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            final Context appCtx = VLCApplication.getAppContext();
            final String uuid = ((Uri) msg.obj).getLastPathSegment();
            switch (msg.what) {
                case ACTION_MEDIA_MOUNTED:
                    final String path = ((Uri) msg.obj).getPath();
                    removeMessages(ACTION_MEDIA_UNMOUNTED);
                    if (!TextUtils.isEmpty(uuid)
                            && !Settings.INSTANCE.getInstance(appCtx).getBoolean("ignore_" + uuid, false)) {
                        final Medialibrary ml = VLCApplication.getMLInstance();
                        final String[] knownDevices = ml.getDevices();
                        if (!containsDevice(knownDevices, path) && ml.addDevice(uuid, path, true)) {
                            notifyStorageChanges(path);
                        } else MediaParsingService.Companion.getStarted().setValue(false);
                    }
                    break;
                case ACTION_MEDIA_UNMOUNTED:
                    VLCApplication.getMLInstance().removeDevice(uuid);
                    MediaParsingService.Companion.getStarted().setValue(false);
                    break;
            }
        }
    };

    private static synchronized void notifyStorageChanges(String path) {
        final Activity activity = storageObserver != null ? storageObserver.get() : null;
        if (activity != null)
            UiTools.newStorageDetected(activity, path);
    }

    public static boolean isMobile() {
        return mobile;
    }

    public static boolean isConnected() {
        final Boolean co = connected.getValue();
        return co != null && co.booleanValue();
    }

    public static boolean isLan() {
        final Boolean status = connected.getValue();
        return status != null && status && !mobile;
    }

    public static boolean isVPN() {
        return vpn;
    }

    public static boolean allowLan() {
        return isLan() || isVPN();
    }

    public static synchronized void subscribeStorageCb(Activity observer) {
        storageObserver = new WeakReference<>(observer);
    }

    public static synchronized void unsubscribeStorageCb(Activity observer) {
        if (storageObserver != null && storageObserver.get() == observer) {
            storageObserver.clear();
            storageObserver = null;
        }
    }

    private boolean updateVPNStatus() {
        if (AndroidUtil.isLolliPopOrLater) {
            for (Network network : cm.getAllNetworks()) {
                final NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    return true;
            }
            return false;
        } else {
            try {
                final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    final NetworkInterface networkInterface = networkInterfaces.nextElement();
                    final String name = networkInterface.getDisplayName();
                    if (name.startsWith("ppp") || name.startsWith("tun") || name.startsWith("tap"))
                        return true;
                }
            } catch (SocketException ignored) {}
            return false;
        }
    }

    public static boolean containsDevice(String[] devices, String device) {
        if (Util.isArrayEmpty(devices))
            return false;
        for (String dev : devices)
            if (device.startsWith(Strings.removeFileProtocole(dev)))
                return true;
        return false;
    }
}
