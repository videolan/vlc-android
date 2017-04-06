package org.videolan.vlc;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;

import java.io.File;

public class MediaParsingService extends Service implements DevicesDiscoveryCb {
    public final static String TAG = "VLC/MediaParsingService";


    public final static String ACTION_INIT = "medialibrary_init";
    public final static String ACTION_RELOAD = "medialibrary_reload";
    public final static String ACTION_DISCOVER = "medialibrary_discover";
    public final static String ACTION_DISCOVER_DEVICE = "medialibrary_discover_device";

    public final static String EXTRA_PATH = "extra_path";
    public final static String EXTRA_UUID = "extra_uuid";

    public final static String ACTION_RESUME_SCAN = "action_resume_scan";
    public final static String ACTION_PAUSE_SCAN = "action_pause_scan";
    public static final long NOTIFICATION_DELAY = 1000L;
    private PowerManager.WakeLock mWakeLock;

    private final IBinder mBinder = new LocalBinder();
    private Medialibrary mMedialibrary;
    private int mParsing = 0, mReload = 0;
    private String mCurrentDiscovery = null;
    private long mLastNotificationTime = 0L;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_PAUSE_SCAN:
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    mMedialibrary.pauseBackgroundOperations();
                    break;
                case ACTION_RESUME_SCAN:
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    mMedialibrary.resumeBackgroundOperations();
                    break;
                default:
                    return;
            }
            synchronized (this) {
                mLastNotificationTime = 0L;
            }
            showNotification();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) VLCApplication.getAppContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mMedialibrary = VLCApplication.getMLInstance();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PAUSE_SCAN);
        filter.addAction(ACTION_RESUME_SCAN);
        registerReceiver(mReceiver, filter);
        mMedialibrary.addDeviceDiscoveryCb(MediaParsingService.this);
        switch (intent.getAction()) {
            case ACTION_INIT:
                setupMedialibrary(intent.getBooleanExtra(StartActivity.EXTRA_UPGRADE, false));
                break;
            case ACTION_RELOAD:
                reload();
                break;
            case ACTION_DISCOVER:
                discover(intent.getStringExtra(EXTRA_PATH));
                break;
            case ACTION_DISCOVER_DEVICE:
                discoverStorage(intent.getStringExtra(EXTRA_PATH), intent.getStringExtra(EXTRA_UUID));
                break;
            default:
                return START_NOT_STICKY;
        }
        if (!mWakeLock.isHeld())
            mWakeLock.acquire();
        return START_NOT_STICKY;
    }

    private void discoverStorage(String path, String uuid) {
        if (!TextUtils.isEmpty(uuid))
            mMedialibrary.addDevice(uuid, path, true);
        for (String folder : Medialibrary.getBlackList())
            mMedialibrary.banFolder(path + folder);
        mMedialibrary.discover(path);
    }

    private void discover(String path) {
        if (TextUtils.isEmpty(path))
            return;
        addDeviceIfNeeded(path);
        mMedialibrary.discover(path);
    }

    private void addDeviceIfNeeded(String path) {
        for (String devicePath : mMedialibrary.getDevices()) {
            if (path.startsWith(devicePath))
                return;
        }
        for (String storagePath : AndroidDevices.getExternalStorageDirectories()) {
            if (path.startsWith(storagePath)) {
                String uuid = FileUtils.getFileNameFromPath(path);
                mMedialibrary.addDevice(uuid, path, true);
            }
        }
    }

    private void reload() {
        if (mReload > 0)
            return;
        mMedialibrary.reload();
    }

    private void setupMedialibrary(final boolean upgrade) {
        if (mMedialibrary.isInitiated())
            mMedialibrary.resumeBackgroundOperations();
        else
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    synchronized (this) {
                        mLastNotificationTime = System.currentTimeMillis();
                    }
                    mMedialibrary.setup();
                    boolean shouldInit = !(new File(MediaParsingService.this.getCacheDir()+Medialibrary.VLC_MEDIA_DB_NAME).exists());
                    String[] storages = AndroidDevices.getMediaDirectories();
                    for (String storage : storages) {
                        boolean isMainStorage = TextUtils.equals(storage, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                        mMedialibrary.addDevice(isMainStorage ? "main-storage" : FileUtils.getFileNameFromPath(storage), storage, !isMainStorage);
                    }
                    if (mMedialibrary.init(MediaParsingService.this)) {
                        LocalBroadcastManager.getInstance(MediaParsingService.this).sendBroadcast(new Intent(VLCApplication.ACTION_MEDIALIBRARY_READY));
                        if (shouldInit) {
                            for (String folder : Medialibrary.getBlackList())
                                mMedialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + folder);
                            mMedialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                        } else if (upgrade) {
                            mMedialibrary.forceParserRetry();
                        }
                        final String[] foldersList = mMedialibrary.getFoldersList();
                        final SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(MediaParsingService.this);
                        for (String externalStorage : AndroidDevices.getExternalStorageDirectories()) {
                            if (!TextUtils.equals(externalStorage, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                                    && !Util.arrayContains(foldersList, "file://" + externalStorage + "/")
                                    && !mSettings.getBoolean("ignore_"+FileUtils.getFileNameFromPath(externalStorage), false)) {
                                startActivity(new Intent(MediaParsingService.this, DialogActivity.class)
                                        .setAction(DialogActivity.KEY_STORAGE)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(EXTRA_PATH, externalStorage));
                            }
                        }
                    }
                }
            });
    }

    private NotificationCompat.Builder builder;
    private boolean wasWorking;
    final StringBuilder sb = new StringBuilder();
    private void showNotification() {
        final long currentTime = System.currentTimeMillis();
        synchronized (this) {
            if (mLastNotificationTime == -1L || currentTime-mLastNotificationTime < NOTIFICATION_DELAY)
                return;
            mLastNotificationTime = currentTime;
        }
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                sb.setLength(0);
                if (mParsing > 0)
                    sb.append(getString(R.string.ml_parse_media)).append(' ').append(mParsing).append("%");
                else if (mCurrentDiscovery != null)
                    sb.append(getString(R.string.ml_discovering)).append(' ').append(Uri.decode(Strings.removeFileProtocole(mCurrentDiscovery)));
                else
                    sb.append(getString(R.string.ml_parse_media));
                if (builder == null) {
                    builder = new NotificationCompat.Builder(MediaParsingService.this)
                            .setContentIntent(PendingIntent.getActivity(MediaParsingService.this, 0, new Intent(MediaParsingService.this, StartActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                            .setSmallIcon(R.drawable.ic_notif_scan)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setContentTitle(getString(R.string.ml_scanning))
                            .setAutoCancel(false)
                            .setOngoing(true);
                }
                builder.setContentText(sb.toString());

                boolean isWorking = mMedialibrary.isWorking();
                if (wasWorking != isWorking) {
                    wasWorking = isWorking;
                    PendingIntent pi = PendingIntent.getBroadcast(MediaParsingService.this, 0, new Intent(isWorking ? ACTION_PAUSE_SCAN : ACTION_RESUME_SCAN), PendingIntent.FLAG_UPDATE_CURRENT);
                    NotificationCompat.Action playpause = isWorking ? new NotificationCompat.Action(R.drawable.ic_pause, getString(R.string.pause), pi)
                            : new NotificationCompat.Action(R.drawable.ic_play, getString(R.string.resume), pi);
                    builder.mActions.clear();
                    builder.addAction(playpause);
                }
                final Notification notification = builder.build();
                synchronized (MediaParsingService.this) {
                    if (mLastNotificationTime != -1L) {
                        try {
                            NotificationManagerCompat.from(MediaParsingService.this).notify(43, notification);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        });
    }

    private void hideNotification() {
        synchronized (this) {
            mLastNotificationTime = -1L;
            NotificationManagerCompat.from(MediaParsingService.this).cancel(43);
        }
    }

    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {
        mCurrentDiscovery = entryPoint;
        showNotification();
    }

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        if (!mMedialibrary.isWorking())
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
    public void onReloadStarted(String entryPoint) {
        if (TextUtils.isEmpty(entryPoint))
            ++mReload;
    }

    @Override
    public void onReloadCompleted(String entryPoint) {
        if (TextUtils.isEmpty(entryPoint))
            --mReload;
        if (!mMedialibrary.isWorking())
            stopSelf();
    }

    @Override
    public void onDestroy() {
        hideNotification();
        mMedialibrary.removeDeviceDiscoveryCb(this);
        unregisterReceiver(mReceiver);
        if (mWakeLock.isHeld())
            mWakeLock.release();
        super.onDestroy();
    }

    private class LocalBinder extends Binder {
        MediaParsingService getService() {
            return MediaParsingService.this;
        }
    }
}
