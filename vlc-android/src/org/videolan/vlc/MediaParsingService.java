package org.videolan.vlc;

import android.app.Notification;
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
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.gui.helpers.NotificationHelper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public final static String ACTION_SERVICE_STARTED = "action_service_started";
    public final static String ACTION_SERVICE_ENDED = "action_service_ended";
    public final static String ACTION_PROGRESS = "action_progress";
    public final static String ACTION_PROGRESS_TEXT = "action_progress_text";
    public final static String ACTION_PROGRESS_VALUE = "action_progress_value";
    public static final long NOTIFICATION_DELAY = 1000L;
    private PowerManager.WakeLock mWakeLock;
    private LocalBroadcastManager mLocalBroadcastManager;

    private final IBinder mBinder = new LocalBinder();
    private Medialibrary mMedialibrary;
    private int mParsing = 0, mReload = 0;
    private String mCurrentDiscovery = null;
    private long mLastNotificationTime = 0L;

    private final ExecutorService mCallsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mNotificationsExecutor = Executors.newSingleThreadExecutor();

    boolean mScanPaused = false;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_PAUSE_SCAN:
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    mScanPaused = true;
                    mMedialibrary.pauseBackgroundOperations();
                    break;
                case ACTION_RESUME_SCAN:
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    mMedialibrary.resumeBackgroundOperations();
                    mScanPaused = false;
                    break;
                case Medialibrary.ACTION_IDLE:
                    if (intent.getBooleanExtra(Medialibrary.STATE_IDLE, true)) {
                        if (!mScanPaused) {
                            stopSelf();
                            return;
                        }
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mMedialibrary = VLCApplication.getMLInstance();
        mMedialibrary.addDeviceDiscoveryCb(MediaParsingService.this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PAUSE_SCAN);
        filter.addAction(ACTION_RESUME_SCAN);
        registerReceiver(mReceiver, filter);
        mLocalBroadcastManager.registerReceiver(mReceiver, new IntentFilter(Medialibrary.ACTION_IDLE));
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_NOT_STICKY;
        synchronized (MediaParsingService.this) {
            if (mLastNotificationTime <= 0L)
                mLastNotificationTime = System.currentTimeMillis();
        }
        switch (intent.getAction()) {
            case ACTION_INIT:
                setupMedialibrary(intent.getBooleanExtra(StartActivity.EXTRA_UPGRADE, false));
                break;
            case ACTION_RELOAD:
                reload(intent.getStringExtra(EXTRA_PATH));
                break;
            case ACTION_DISCOVER:
                discover(intent.getStringExtra(EXTRA_PATH));
                break;
            case ACTION_DISCOVER_DEVICE:
                discoverStorage(intent.getStringExtra(EXTRA_PATH));
                break;
            default:
                exitCommand();
                return START_NOT_STICKY;
        }
        mLocalBroadcastManager.sendBroadcast(new Intent(ACTION_SERVICE_STARTED));
        return START_NOT_STICKY;
    }

    private void discoverStorage(final String path) {
        if (BuildConfig.DEBUG) Log.d(TAG, "discoverStorage: "+path);
        if (TextUtils.isEmpty(path)) {
            exitCommand();
            return;
        }
        mCallsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (String folder : Medialibrary.getBlackList())
                    mMedialibrary.banFolder(path + folder);
                mMedialibrary.discover(path);
            }
        });
    }

    private void discover(final String path) {
        if (TextUtils.isEmpty(path)) {
            exitCommand();
            return;
        }
        mCallsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                addDeviceIfNeeded(path);
                mMedialibrary.discover(path);
            }
        });
    }

    private void addDeviceIfNeeded(String path) {
        for (String devicePath : mMedialibrary.getDevices()) {
            if (path.startsWith(Strings.removeFileProtocole(devicePath))) {
                exitCommand();
                return;
            }
        }
        for (String storagePath : AndroidDevices.getExternalStorageDirectories()) {
            if (path.startsWith(storagePath)) {
                String uuid = FileUtils.getFileNameFromPath(path);
                if (TextUtils.isEmpty(uuid)) {
                    exitCommand();
                    return;
                }
                mMedialibrary.addDevice(uuid, path, true, true);
                for (String folder : Medialibrary.getBlackList())
                    mMedialibrary.banFolder(path + folder);
            }
        }
    }

    private void reload(String path) {
        if (mReload > 0)
            return;
        if (TextUtils.isEmpty(path))
            mMedialibrary.reload();
        else
            mMedialibrary.reload(path);
    }

    private void setupMedialibrary(final boolean upgrade) {
        if (mMedialibrary.isInitiated()) {
            mMedialibrary.resumeBackgroundOperations();
            exitCommand();
        } else
            mCallsExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    boolean shouldInit = !(new File(MediaParsingService.this.getDir("db", Context.MODE_PRIVATE)+Medialibrary.VLC_MEDIA_DB_NAME).exists());
                    if (mMedialibrary.init(getApplicationContext())) {
                        List<String> devices = new ArrayList<>();
                        devices.add(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                        devices.addAll(AndroidDevices.getExternalStorageDirectories());
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MediaParsingService.this);
                        for (String device : devices) {
                            boolean isMainStorage = TextUtils.equals(device, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                            String uuid = FileUtils.getFileNameFromPath(device);
                            if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid))
                                continue;
                            boolean isNew = mMedialibrary.addDevice(isMainStorage ? "main-storage" : uuid, device, !isMainStorage, false);
                            boolean isIgnored = sharedPreferences.getBoolean("ignore_"+ uuid, false);
                            if (!isMainStorage && isNew && !isIgnored) {
                                startActivity(new Intent(MediaParsingService.this, DialogActivity.class)
                                        .setAction(DialogActivity.KEY_STORAGE)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(EXTRA_PATH, device));
                            }
                        }
                        mMedialibrary.start();
                        mLocalBroadcastManager.sendBroadcast(new Intent(VLCApplication.ACTION_MEDIALIBRARY_READY));
                        if (shouldInit) {
                            for (String folder : Medialibrary.getBlackList())
                                mMedialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + folder);
                            mMedialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                        } else if (upgrade)
                            mMedialibrary.forceParserRetry();
                        else if (PreferenceManager.getDefaultSharedPreferences(MediaParsingService.this).getBoolean("auto_rescan", true))
                            reload(null);
                    }
                }
            });
    }

    private boolean wasWorking;
    final StringBuilder sb = new StringBuilder();
    private final Intent progessIntent = new Intent(ACTION_PROGRESS);
    private final Intent notificationIntent = new Intent();
    private void showNotification() {
        final long currentTime = System.currentTimeMillis();
        synchronized (MediaParsingService.this) {
            if (mLastNotificationTime == -1L || currentTime-mLastNotificationTime < NOTIFICATION_DELAY)
                return;
            mLastNotificationTime = currentTime;
        }
        mNotificationsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                sb.setLength(0);
                if (mParsing > 0)
                    sb.append(getString(R.string.ml_parse_media)).append(' ').append(mParsing).append("%");
                else if (mCurrentDiscovery != null)
                    sb.append(getString(R.string.ml_discovering)).append(' ').append(Uri.decode(Strings.removeFileProtocole(mCurrentDiscovery)));
                else
                    sb.append(getString(R.string.ml_parse_media));
                final String progressText = sb.toString();
                final boolean updateAction = wasWorking != mMedialibrary.isWorking();
                if (updateAction)
                    wasWorking = !wasWorking;
                final Notification notification = NotificationHelper.createScanNotification(MediaParsingService.this, progressText, updateAction, mScanPaused);
                synchronized (MediaParsingService.this) {
                    if (mLastNotificationTime != -1L) {
                        mLocalBroadcastManager.sendBroadcast(progessIntent
                                .putExtra(ACTION_PROGRESS_TEXT, progressText)
                                .putExtra(ACTION_PROGRESS_VALUE, mParsing));
                        try {
                            startForeground(43, notification);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        });
    }

    private void hideNotification() {
        mNotificationsExecutor.shutdown();
        synchronized (MediaParsingService.this) {
            mLastNotificationTime = -1L;
            NotificationManagerCompat.from(MediaParsingService.this).cancel(43);
        }
    }

    @Override
    public void onDiscoveryStarted(String entryPoint) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onDiscoveryStarted: "+entryPoint);
    }

    @Override
    public void onDiscoveryProgress(String entryPoint) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onDiscoveryProgress: "+entryPoint);
        mCurrentDiscovery = entryPoint;
        showNotification();
    }

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onDiscoveryCompleted: "+entryPoint);
    }

    @Override
    public void onParsingStatsUpdated(int percent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onParsingStatsUpdated: "+percent);
        mParsing = percent;
        if (mParsing != 100)
            showNotification();
    }

    @Override
    public void onReloadStarted(String entryPoint) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onReloadStarted: "+entryPoint);
        if (TextUtils.isEmpty(entryPoint))
            ++mReload;
    }

    @Override
    public void onReloadCompleted(String entryPoint) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onReloadCompleted "+entryPoint);
        if (TextUtils.isEmpty(entryPoint))
            --mReload;
    }

    private void exitCommand() {
        if (!mMedialibrary.isWorking())
            stopSelf();
    }

    @Override
    public void onDestroy() {
        mLocalBroadcastManager.sendBroadcast(new Intent(ACTION_SERVICE_ENDED));
        hideNotification();
        mMedialibrary.removeDeviceDiscoveryCb(this);
        unregisterReceiver(mReceiver);
        mLocalBroadcastManager.unregisterReceiver(mReceiver);
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
