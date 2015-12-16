/*
 * *************************************************************************
 *  PluginService.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.plugin;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.plugin.api.IExtensionHost;
import org.videolan.vlc.plugin.api.IExtensionService;
import org.videolan.vlc.plugin.api.VLCExtensionItem;

import java.util.LinkedList;
import java.util.List;

public class PluginService extends Service {

    private static final String TAG = "VLC/PluginService";

    public static final String ACTION_EXTENSION = "org.videolan.vlc.Extension";
    public static final int PROTOCOLE_VERSION = 1;

    private final IBinder mBinder = new LocalBinder();
    private ExtensionManagerActivity mExtensionManagerActivity;

    public interface ExtensionManagerActivity {
        void displayExtensionItems(String title, List<VLCExtensionItem> items, boolean showParams);
    }

    public void setExtensionManagerActivity(ExtensionManagerActivity activity) {
        mExtensionManagerActivity = activity;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getAvailableExtensions();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        setExtensionManagerActivity(null);
        return false;
    }

    public class LocalBinder extends Binder {
        public PluginService getService() {
            return PluginService.this;
        }
    }

    public List<ExtensionListing> getAvailableExtensions() {
        PackageManager pm = VLCApplication.getAppContext().getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(ACTION_EXTENSION), PackageManager.GET_META_DATA);

        mPlugins.clear();
        for (ResolveInfo resolveInfo : resolveInfos) {
            ExtensionListing info = new ExtensionListing();
            info.componentName(new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name));
            info.title(resolveInfo.loadLabel(pm).toString());
            Bundle metaData = resolveInfo.serviceInfo.metaData;
            if (metaData != null) {
                info.compatible(metaData.getInt("protocolVersion") == PROTOCOLE_VERSION);
                if (!info.compatible())
                    continue;
                info.description(metaData.getString("description"));
                String settingsActivity = metaData.getString("settingsActivity");
                if (!TextUtils.isEmpty(settingsActivity)) {
                    info.settingsActivity(ComponentName.unflattenFromString(
                            resolveInfo.serviceInfo.packageName + "/" + settingsActivity));
                }
                mPlugins.add(info);
            }
        }
        return mPlugins;
    }

    private List<ExtensionListing> mPlugins = new LinkedList<>();
    int mCurrentIndex = -1;

    public void openExtension(int index) {
        if (index == mCurrentIndex)
            browse(0, null);
        else
            connectService(index);

    }
    public void connectService(final int index) {
        ExtensionListing info = mPlugins.get(index);

        if (mCurrentIndex != -1) {
            disconnect();
        }

        final Connection conn = new Connection();
        ComponentName cn = info.componentName();
        conn.componentName = cn;
        conn.hostInterface = makeHostInterface();
        conn.serviceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                conn.ready = true;
                conn.binder = IExtensionService.Stub.asInterface(service);
                try {
                    conn.binder.onInitialize(index, conn.hostInterface);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mCurrentIndex = -1;
            }
        };

        info.setConnection(conn);

        try {
            if (!bindService(new Intent().setComponent(cn), conn.serviceConnection,
                    Context.BIND_AUTO_CREATE)) {
                Log.e(TAG, "Error binding to extension " + cn.flattenToShortString());
                info.setConnection(null);
            } else
                mCurrentIndex = index;
        } catch (SecurityException e) {
            Log.e(TAG, "Error binding to extension " + cn.flattenToShortString(), e);
            info.setConnection(null);
        }
    }

    public void refresh() {
        try {
            ExtensionListing plugin = mPlugins.get(mCurrentIndex);
            if (plugin == null)
                return;
            IExtensionService service = plugin.getConnection().binder;
            if (service == null)
                return;
            service.refresh();
        } catch (RemoteException e) {}
    }

    public void browse(int intId, String stringId) {
        try {
            ExtensionListing plugin = mPlugins.get(mCurrentIndex);
            if (plugin == null)
                return;
            IExtensionService service = plugin.getConnection().binder;
            if (service == null)
                return;
            service.browse(intId, stringId);
        } catch (RemoteException e) {}
    }

    public void disconnect() {
        if (mCurrentIndex == -1)
            return;
        ExtensionListing plugin = mPlugins.get(mCurrentIndex);
        Connection conn = plugin.getConnection();
        if (conn != null) {
            try {
                unbindService(conn.serviceConnection);
            } catch (Exception e) {} // In case of extension service crashed
        }
        plugin.setConnection(null);
    }

    private IExtensionHost makeHostInterface() {
        return new IExtensionHost.Stub(){

            @Override
            public void updateList(final String title, final List<VLCExtensionItem> items, final boolean showParams) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mExtensionManagerActivity != null)
                            mExtensionManagerActivity.displayExtensionItems(title, items, showParams);
                    }
                });
            }

            @Override
            public void playUri(Uri uri, String title) throws RemoteException {
                final MediaWrapper media = new MediaWrapper(uri);
                media.setTitle(title);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MediaUtils.openMediaNoUi(PluginService.this, media);
                    }
                });
            }

            @Override
            public void unBind(int index) throws RemoteException {
                if (mCurrentIndex == index)
                    mCurrentIndex = -1;
            }
        };
    }

    public static class Connection {
        boolean ready = false;
        ComponentName componentName;
        ServiceConnection serviceConnection;
        IExtensionService binder;
        IExtensionHost hostInterface;

        /**
         * Only access on the async thread. The pair is (collapse token, operation)
         */
//        final Queue<Pair<Object, Operation>> deferredOps
//                = new LinkedList<Pair<Object, Operation>>();
    }

    private final Handler mHandler = new Handler();
}
