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

package org.videolan.vlc.extensions;

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
import org.videolan.vlc.extensions.api.IExtensionHost;
import org.videolan.vlc.extensions.api.IExtensionService;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;

import java.util.ArrayList;
import java.util.List;

public class ExtensionManagerService extends Service {

    private static final String TAG = "VLC/ExtensionManagerService";

    private static final String KEY_PROTOCOL_VERSION = "protocolVersion";
    private static final String KEY_LISTING_TITLE = "title";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_MENU_ICON = "menuicon";
    private static final String KEY_SETTINGS_ACTIVITY = "settingsActivity";

    public static final String ACTION_EXTENSION = "org.videolan.vlc.Extension";
    public static final int PROTOCOLE_VERSION = 1;

    private final IBinder mBinder = new LocalBinder();
    private ExtensionManagerActivity mExtensionManagerActivity;

    private List<ExtensionListing> mExtensions = new ArrayList<>();
    int mCurrentIndex = -1;

    public interface ExtensionManagerActivity {
        void displayExtensionItems(String title, List<VLCExtensionItem> items, boolean showParams, boolean isRefresh);
    }

    public void setExtensionManagerActivity(ExtensionManagerActivity activity) {
        mExtensionManagerActivity = activity;
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
        disconnect();
        return false;
    }

    public class LocalBinder extends Binder {
        public ExtensionManagerService getService() {
            return ExtensionManagerService.this;
        }
    }

    public List<ExtensionListing> updateAvailableExtensions() {
        PackageManager pm = VLCApplication.getAppContext().getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(ACTION_EXTENSION), PackageManager.GET_META_DATA);

        ArrayList<ExtensionListing> extensions = new ArrayList<>();

        for (ResolveInfo resolveInfo : resolveInfos) {
            ExtensionListing extension = new ExtensionListing();
            extension.componentName(new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name));
            Bundle metaData = resolveInfo.serviceInfo.metaData;
            if (metaData != null) {
                extension.compatible(metaData.getInt(KEY_PROTOCOL_VERSION) == PROTOCOLE_VERSION);
                if (!extension.compatible())
                    continue;
                String title = metaData.getString(KEY_LISTING_TITLE);
                extension.title(title != null ? title : resolveInfo.loadLabel(pm).toString());
                extension.description(metaData.getString(KEY_DESCRIPTION));
                String settingsActivity = metaData.getString(KEY_SETTINGS_ACTIVITY);
                if (!TextUtils.isEmpty(settingsActivity)) {
                    extension.settingsActivity(ComponentName.unflattenFromString(
                            resolveInfo.serviceInfo.packageName + "/" + settingsActivity));
                }
                extension.menuIcon(metaData.getInt(KEY_MENU_ICON, 0));
                extensions.add(extension);
            }
        }
        synchronized (mExtensions) {
            mExtensions.clear();
            mExtensions.addAll(extensions);
        }
        return extensions;
    }

    public void openExtension(int index) {
        if (index == mCurrentIndex)
            browse(0, null);
        else
            connectService(index);

    }

    public ExtensionListing getCurrentExtension() {
        return mExtensions.get(mCurrentIndex);
    }

    public void connectService(final int index) {
        ExtensionListing info = mExtensions.get(index);

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
            ExtensionListing extension = mExtensions.get(mCurrentIndex);
            if (extension == null)
                return;
            IExtensionService service = extension.getConnection().binder;
            if (service == null)
                return;
            service.refresh();
        } catch (RemoteException e) {}
    }

    public void browse(int intId, String stringId) {
        try {
            ExtensionListing extension = mExtensions.get(mCurrentIndex);
            if (extension == null)
                return;
            IExtensionService service = extension.getConnection().binder;
            if (service == null)
                return;
            service.browse(intId, stringId);
        } catch (RemoteException e) {}
    }

    public void disconnect() {
        if (mCurrentIndex == -1)
            return;
        ExtensionListing extension = getCurrentExtension();
        Connection conn = extension.getConnection();
        if (conn != null) {
            try {
                unbindService(conn.serviceConnection);
            } catch (Exception e) {} // In case of extension service crashed
        }
        extension.setConnection(null);
    }

    private IExtensionHost makeHostInterface() {
        return new IExtensionHost.Stub(){

            @Override
            public void updateList(final String title, final List<VLCExtensionItem> items, final boolean showParams, final boolean isRefresh) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mExtensionManagerActivity != null)
                            mExtensionManagerActivity.displayExtensionItems(title, items, showParams, isRefresh);
                    }
                });
            }

            @Override
            public void playUri(Uri uri, String title) throws RemoteException {
                final MediaWrapper media = new MediaWrapper(uri);
                if (!TextUtils.isEmpty(title));
                    media.setDisplayTitle(title);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MediaUtils.openMediaNoUi(ExtensionManagerService.this, media);
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
