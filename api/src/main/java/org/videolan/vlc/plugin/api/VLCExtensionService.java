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

    private static final ComponentName VLC_HOST_SERVICE =
            new ComponentName("org.videolan.vlc",
                    "org.videolan.vlc.plugin.PluginService");

    IExtensionHost mHost;
    Context mContext = this;

    private volatile Looper mServiceLooper;
    private volatile Handler mServiceHandler;

    protected abstract void browse(int intId, String stringId);
    protected abstract void updateList(List<VLCExtensionItem> items);

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

    private final IExtensionService.Stub mBinder = new IExtensionService.Stub() {
        @Override
        public void onInitialize(IExtensionHost host) throws RemoteException {
            mHost = host;
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
    };
}
