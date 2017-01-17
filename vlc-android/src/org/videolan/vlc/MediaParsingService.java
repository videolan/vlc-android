package org.videolan.vlc;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Strings;

import java.io.File;

import static org.videolan.vlc.VLCApplication.ACTION_MEDIALIBRARY_READY;
import static org.videolan.vlc.VLCApplication.getAppContext;
import static org.videolan.vlc.VLCApplication.getMLInstance;
import static org.videolan.vlc.VLCApplication.runBackground;

public class MediaParsingService extends Service implements DevicesDiscoveryCb {
    public final static String TAG = "VLC/MediaParsingService";

    private final IBinder mBinder = new LocalBinder();
    private Medialibrary mMedialibrary;
    private boolean mDiscovering = false;
    private int mParsing = 0;
    private String mCurrentProgress;
    private StringBuilder sb = new StringBuilder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupMedialibrary();
        return START_NOT_STICKY;
    }

    private void setupMedialibrary() {
        mMedialibrary = getMLInstance();
        if (mMedialibrary.isInitiated()) {
            stopSelf();
            return;
        }
        mMedialibrary.addDeviceDiscoveryCb(MediaParsingService.this);
        runBackground(new Runnable() {
            @Override
            public void run() {
                mMedialibrary.setup();
                String[] storages = AndroidDevices.getMediaDirectories();
                for (String storage : storages) {
                    boolean isMainStorage = TextUtils.equals(storage, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                    mMedialibrary.addDevice(isMainStorage ? "main-storage" : storage, storage, !isMainStorage);
                }
                if (mMedialibrary.init(getAppContext())) {
                    showNotification();
                    LocalBroadcastManager.getInstance(MediaParsingService.this).sendBroadcast(new Intent(ACTION_MEDIALIBRARY_READY));
                    if (mMedialibrary.getFoldersList().length == 0) {
                        for (String storage : storages)
                            for (String folder : Medialibrary.getBlackList())
                                mMedialibrary.banFolder(storage+folder);
                        for (File folder : Medialibrary.getDefaultFolders())
                            if (folder.exists())
                                mMedialibrary.discover(folder.getPath());
                        for (String externalStorage : AndroidDevices.getExternalStorageDirectories())
                            if (!TextUtils.equals(externalStorage, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
                                mMedialibrary.discover(externalStorage);
                    }
                }
            }
        });
    }

    private void showNotification() {
        sb.setLength(0);
        if (mParsing > 0)
            sb.append(getString(R.string.ml_parse_media)).append(' ').append(mParsing).append("%");
        else
            sb.append(getString(R.string.ml_discovering)).append(' ').append(Uri.decode(Strings.removeFileProtocole(mCurrentProgress)));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(MediaParsingService.this)
                .setSmallIcon(R.drawable.ic_notif_scan)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(getString(R.string.ml_scanning))
                .setContentText(sb.toString())
                .setAutoCancel(false)
                .setOngoing(true);

        Notification notification = builder.build();
        try {
            NotificationManagerCompat.from(MediaParsingService.this).notify(43, notification);
        } catch (IllegalArgumentException ignored) {}
    }

    private void hideNotification() {
        NotificationManagerCompat.from(MediaParsingService.this).cancel(43);
    }

    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {
        mDiscovering = true;
        mCurrentProgress = entryPoint;
        showNotification();
    }

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        if (mDiscovering && mParsing == 0 && entryPoint.isEmpty())
            stopSelf();
    }

    @Override
    public void onParsingStatsUpdated(int percent) {
        mParsing = percent;
        if (mParsing == 100)
            stopSelf();
        else
            showNotification();
    }

    @Override
    public void onDestroy() {
        mMedialibrary.removeDeviceDiscoveryCb(this);
        hideNotification();
        super.onDestroy();
    }

    private class LocalBinder extends Binder {
        MediaParsingService getService() {
            return MediaParsingService.this;
        }
    }
}
