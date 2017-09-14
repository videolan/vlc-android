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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import org.videolan.libvlc.util.AndroidUtil;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class NetworkMonitor extends BroadcastReceiver {
    private static volatile boolean connected = true;
    private static volatile boolean mobile = true;
    private static volatile boolean vpn = false;
    private static final NetworkMonitor instance = new NetworkMonitor();
    private static final List<NetworkObserver> observers = new LinkedList<>();

    public interface NetworkObserver {
        void onNetworkConnectionChanged(boolean connected);
    }

    static void register(Context ctx) {
        ctx.registerReceiver(instance, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    static void unregister(Context ctx) {
        ctx.unregisterReceiver(instance);
    }

    public static NetworkMonitor getInstance() {
        return instance;
    }

    private ConnectivityManager cm = null;
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (cm == null)
            cm = (ConnectivityManager) VLCApplication.getAppContext().getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            final boolean isConnected = networkInfo != null && networkInfo.isConnectedOrConnecting();
            mobile = isConnected && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
            vpn = isConnected && updateVPNStatus();
            if (isConnected != connected) {
                connected = isConnected;
                notifyChanges();
            }
        }
    }

    private synchronized void notifyChanges() {
        for (NetworkObserver obs : observers)
            obs.onNetworkConnectionChanged(connected);
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

    public static synchronized void subscribe(NetworkObserver observer) {
        observers.add(observer);
    }

    public static synchronized void unsubscribe(NetworkObserver observer) {
        observers.remove(observer);
    }

    private boolean updateVPNStatus() {
        if (AndroidUtil.isLolliPopOrLater) {
            for (Network network : cm.getAllNetworks()) {
                if (cm.getNetworkCapabilities(network).hasTransport(NetworkCapabilities.TRANSPORT_VPN))
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
}
