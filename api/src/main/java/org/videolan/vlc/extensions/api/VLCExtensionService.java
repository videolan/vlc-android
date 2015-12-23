package org.videolan.vlc.extensions.api;

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
import android.support.annotation.NonNull;
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

    /**
     * Called by VLC when users wants to browse one of your {#link VLCExtensionItem.TYPE_DIRECTORY}
     * VLC provides {#intId} and {#stringId} from chosen item
     *
     * @param intId int id of the item to browse
     * @param stringId String id of the item to browse
     */
    protected abstract void browse(int intId, @Nullable String stringId);

    /**
     * Called by VLC when user wants to refresh the current list displayed by the extension.
     */
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

    /**
     * Starts playback of the given uri by VLC
     *
     * @param uri The uri to play
     * @param title Optional - Set the media title to be displayed.
     *              Otherwise, it will be guessed from uri.
     */
    public void playUri(@NonNull Uri uri, @Nullable String title) {
        try {
            mHost.playUri(uri, title);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Displays given items in VLC browser.
     *
     * @param title The title shown in VLC action bar for this list display.
     * @param items The items to show.
     * @param showParams Wether you want to show the FAB to launch your extension settings activity.
     */
    protected void updateList(String title, List<VLCExtensionItem> items, boolean showParams){
        try {
            mHost.updateList(title, items, showParams);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called once VLC is binded to your service.
     * Use it to call {@link #updateList(String, List, boolean)} with root level elements
     * if you want VLC to handle your extension browsing.
     */
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
