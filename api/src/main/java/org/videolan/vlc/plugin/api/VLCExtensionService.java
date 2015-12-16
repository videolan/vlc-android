package org.videolan.vlc.plugin.api;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import java.util.List;

public abstract class VLCExtensionService extends Service{

    private static final String TAG = "VLC/ExtensionService";
    private int mIndex = -1;

    private static final ComponentName VLC_HOST_SERVICE =
            new ComponentName("org.videolan.vlc",
                    "org.videolan.vlc.plugin.PluginService");

    IExtensionHost mHost;
    Context mContext = this;

    private volatile Looper mServiceLooper;
    protected volatile Handler mServiceHandler;

    protected abstract void browse(int intId, String stringId);
    protected abstract void refresh();

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread(
                "VLCExtension:" + getClass().getSimpleName());
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new Handler(mServiceLooper);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mHost.unBind(mIndex);
        } catch (RemoteException e) {}
        mServiceHandler.removeCallbacksAndMessages(null); // remove all callbacks
        mServiceLooper.quit();
    }

    public void playUri(Uri uri, String title) {
        try {
            mHost.playUri(uri, title);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    protected void updateList(String title, List<VLCExtensionItem> items, boolean showParams){
        try {
            mHost.updateList(title, items, showParams);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    protected void onInitialize() {};

    private final IExtensionService.Stub mBinder = new IExtensionService.Stub() {
        @Override
        public void onInitialize(int index, IExtensionHost host) throws RemoteException {
            mIndex = index;
            mHost = host;
            mServiceHandler.post(new Runnable() {
                @Override
                public void run() {
                    VLCExtensionService.this.onInitialize();
                }
            });
        }

        @Override
        public void browse(final int id, final String text) throws RemoteException {
            mServiceHandler.post(new Runnable() {
                @Override
                public void run() {
                    VLCExtensionService.this.browse(id, text);
                }
            });
        }

        @Override
        public void refresh() {
            mServiceHandler.post(new Runnable() {
                @Override
                public void run() {
                    VLCExtensionService.this.refresh();
                }
            });
        }
    };
}
