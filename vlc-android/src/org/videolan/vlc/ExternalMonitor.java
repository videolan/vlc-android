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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;

import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class ExternalMonitor extends BroadcastReceiver {
    public final static String TAG = "VLC/ExternalMonitor";
    private static volatile boolean connected = false;
    private static volatile boolean mobile = true;
    private static volatile boolean vpn = false;
    private static final ExternalMonitor instance = new ExternalMonitor();
    private static final List<NetworkObserver> networkObservers = new LinkedList<>();
    private static WeakReference<Activity> storageObserver = null;
    private static List<Uri> devicesToAdd = AndroidUtil.isICSOrLater ? null : new LinkedList<Uri>();

    public interface NetworkObserver {
        void onNetworkConnectionChanged(boolean connected);
    }

    static void register(Context ctx) {
        final IntentFilter networkFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        final IntentFilter storageFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        storageFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        storageFilter.addDataScheme("file");
        ctx.registerReceiver(instance, networkFilter);
        ctx.registerReceiver(instance, storageFilter);
        if (AndroidUtil.isICSOrLater)
            checkNewStorages(ctx);
    }

    private static void checkNewStorages(final Context ctx) {
        if (VLCApplication.getMLInstance().isInitiated())
            ctx.startService(new Intent(Constants.ACTION_CHECK_STORAGES, null,ctx, MediaParsingService.class));
    }

    static void unregister(Context ctx) {
        ctx.unregisterReceiver(instance);
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
                if (cm == null)
                    cm = (ConnectivityManager) VLCApplication.getAppContext().getSystemService(
                            Context.CONNECTIVITY_SERVICE);
                final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                final boolean isConnected = networkInfo != null && networkInfo.isConnected();
                mobile = isConnected && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
                vpn = isConnected && updateVPNStatus();
                if (isConnected != connected) {
                    connected = isConnected;
                    notifyConnectionChanges();
                }
                break;
            case Intent.ACTION_MEDIA_MOUNTED:
                if (storageObserver != null && storageObserver.get() != null)
                    mHandler.obtainMessage(ACTION_MEDIA_MOUNTED, intent.getData()).sendToTarget();
                else if (devicesToAdd != null)
                    devicesToAdd.add(intent.getData());
                break;
            case Intent.ACTION_MEDIA_UNMOUNTED:
            case Intent.ACTION_MEDIA_EJECT:
                if (storageObserver != null && storageObserver.get() != null)
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(ACTION_MEDIA_UNMOUNTED, intent.getData()), 100);
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
                            && !PreferenceManager.getDefaultSharedPreferences(appCtx).getBoolean("ignore_" + uuid, false)) {
                        final Medialibrary ml = VLCApplication.getMLInstance();
                        final String[] knownDevices = ml.getDevices();
                        if (!containsDevice(knownDevices, path) && ml.addDevice(uuid, path, true)) {
                            notifyStorageChanges(path);
                        } else {
                            LocalBroadcastManager.getInstance(appCtx).sendBroadcast(new Intent(Constants.ACTION_SERVICE_ENDED));
                        }
                    }
                    break;
                case ACTION_MEDIA_UNMOUNTED:
                    VLCApplication.getMLInstance().removeDevice(uuid);
                    LocalBroadcastManager.getInstance(appCtx).sendBroadcast(new Intent(Constants.ACTION_SERVICE_ENDED));
                    break;
            }
        }
    };

    private synchronized void notifyConnectionChanges() {
        for (NetworkObserver obs : networkObservers)
            obs.onNetworkConnectionChanged(connected);
    }

    private static synchronized void notifyStorageChanges(String path) {
        final Activity activity = storageObserver != null ? storageObserver.get() : null;
        if (activity != null)
            UiTools.newStorageDetected(activity, path);
    }

    public static boolean isConnected() {
        return connected;
    }

    public static boolean isMobile() {
        return mobile;
    }

    public static boolean isLan() {
        return connected && !mobile;
    }

    public static boolean isVPN() {
        return vpn;
    }

    public static synchronized void subscribeNetworkCb(NetworkObserver observer) {
        networkObservers.add(observer);
    }

    public static synchronized void unsubscribeNetworkCb(NetworkObserver observer) {
        networkObservers.remove(observer);
    }

    public static synchronized void subscribeStorageCb(Activity observer) {
        final boolean checkSavedStorages = devicesToAdd != null && storageObserver == null;
        storageObserver = new WeakReference<>(observer);
        if (checkSavedStorages && !devicesToAdd.isEmpty())
            for(Uri uri : devicesToAdd)
                instance.mHandler.obtainMessage(ACTION_MEDIA_MOUNTED, uri).sendToTarget();
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
