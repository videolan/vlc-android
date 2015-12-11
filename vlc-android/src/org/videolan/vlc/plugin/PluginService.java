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
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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

    private static final int PLAY_MEDIA = 42;

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "service onCreate");
        getAvailableExtensions();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "service onBind");
        return mBinder;
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

        for (ResolveInfo resolveInfo : resolveInfos) {
            ExtensionListing info = new ExtensionListing();
            info.componentName(new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name));
            info.title(resolveInfo.loadLabel(pm).toString());
            Bundle metaData = resolveInfo.serviceInfo.metaData;
            if (metaData != null) {
                info.compatible(metaData.getInt("protocolVersion") == PROTOCOLE_VERSION);
                info.worldReadable(metaData.getBoolean("worldReadable", false));
                info.description(metaData.getString("description"));
                String settingsActivity = metaData.getString("settingsActivity");
                if (!TextUtils.isEmpty(settingsActivity)) {
                    info.settingsActivity(ComponentName.unflattenFromString(
                            resolveInfo.serviceInfo.packageName + "/" + settingsActivity));
                }
                mPlugins.add(info);
            }
            info.icon(resolveInfo.getIconResource());
            //availableExtensions.add(info); TODO
            Log.d(TAG, "componentName "+info.componentName().toString());
            Log.d(TAG, " - title "+info.title());
            Log.d(TAG, " - protocolVersion "+info.protocolVersion());
            Log.d(TAG, " - settingsActivity " + info.settingsActivity());

//            connectService(info);
        }
        return plugins;
    }

    private List<ExtensionListing> mPlugins = new LinkedList<>();
    int mCurrentIndex = -1;
    public void connectService(int index) {
        ExtensionListing info = mPlugins.get(index);

        if (mCurrentIndex != -1) {
            disconnect();
        }

        final Connection conn = new Connection();
        ComponentName cn = info.componentName();
        conn.componentName = cn;
        conn.hostInterface = makeHostInterface(conn);
        conn.serviceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                conn.ready = true;
                conn.binder = IExtensionService.Stub.asInterface(service);
                try {
                    conn.binder.onInitialize(conn.hostInterface);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        info.setConnection(conn);

        try {
            if (!bindService(new Intent().setComponent(cn), conn.serviceConnection,
                    Context.BIND_AUTO_CREATE)) {
                Log.e(TAG, "Error binding to extension " + cn.flattenToShortString());
                info.setConnection(null);
//                return null;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error binding to extension " + cn.flattenToShortString(), e);
                info.setConnection(null);
//            return null;
        }
    }

    private void disconnect() {
        ExtensionListing plugin = mPlugins.get(mCurrentIndex);
        Connection conn = plugin.getConnection();
        if (conn != null)
            unbindService(conn.serviceConnection);
        plugin.setConnection(null);
    }
    private IExtensionHost makeHostInterface(Connection conn) {
        return new IExtensionHost.Stub(){

            @Override
            public void updateList(List<VLCExtensionItem> items) throws RemoteException {
                //TODO
            }

            @Override
            public void playUri(Uri uri, String title) throws RemoteException {
                Log.d(TAG, "play media "+title);
                Log.d(TAG, " - uri is: "+uri);
                MediaWrapper media = new MediaWrapper(uri);
                media.setTitle(title);
                mHandler.obtainMessage(PLAY_MEDIA, media).sendToTarget();
            }
        };
    }

    public static class Connection {
        boolean ready = false;
        ComponentName componentName;
        ServiceConnection serviceConnection;
        IExtensionService binder;
        IExtensionHost hostInterface;
        ContentObserver contentObserver;

        /**
         * Only access on the async thread. The pair is (collapse token, operation)
         */
//        final Queue<Pair<Object, Operation>> deferredOps
//                = new LinkedList<Pair<Object, Operation>>();
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PLAY_MEDIA:
                    MediaWrapper media = (MediaWrapper) msg.obj;
                    MediaUtils.openMediaNoUi(PluginService.this, media);
                    break;
            }
        }
    };
}
