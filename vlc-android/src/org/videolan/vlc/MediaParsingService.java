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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.vlc.gui.helpers.NotificationHelper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaParsingService extends Service implements DevicesDiscoveryCb {
    public final static String TAG = "VLC/MediaParsingService";

    private static final long NOTIFICATION_DELAY = 1000L;
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
                case Constants.ACTION_PAUSE_SCAN:
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    mScanPaused = true;
                    mMedialibrary.pauseBackgroundOperations();
                    break;
                case Constants.ACTION_RESUME_SCAN:
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    mMedialibrary.resumeBackgroundOperations();
                    mScanPaused = false;
                    break;
                case Medialibrary.ACTION_IDLE:
                    if (intent.getBooleanExtra(Medialibrary.STATE_IDLE, true)) {
                        if (!mScanPaused) {
                            exitCommand();
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
        filter.addAction(Constants.ACTION_PAUSE_SCAN);
        filter.addAction(Constants.ACTION_RESUME_SCAN);
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
        if (intent == null) {
            exitCommand();
            return START_NOT_STICKY;
        }
        synchronized (MediaParsingService.this) {
            // Set 1s delay before displaying scan icon
            // Except for Android 8+ which expects startForeground immediately
            if (mLastNotificationTime <= 0L)
                mLastNotificationTime = AndroidUtil.isOOrLater ? 0L : System.currentTimeMillis();
            if (AndroidUtil.isOOrLater)
                showNotification();
        }
        switch (intent.getAction()) {
            case Constants.ACTION_INIT:
                setupMedialibrary(intent.getBooleanExtra(Constants.EXTRA_UPGRADE, false));
                break;
            case Constants.ACTION_RELOAD:
                reload(intent.getStringExtra(Constants.EXTRA_PATH));
                break;
            case Constants.ACTION_DISCOVER:
                discover(intent.getStringExtra(Constants.EXTRA_PATH));
                break;
            case Constants.ACTION_DISCOVER_DEVICE:
                discoverStorage(intent.getStringExtra(Constants.EXTRA_PATH));
                break;
            case Constants.ACTION_CHECK_STORAGES:
                updateStorages();
                break;
            default:
                exitCommand();
                return START_NOT_STICKY;
        }
        mLocalBroadcastManager.sendBroadcast(new Intent(Constants.ACTION_SERVICE_STARTED));
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
                mMedialibrary.addDevice(uuid, path, true);
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
        } else {
            final Context context = getApplicationContext();
            mCallsExecutor.execute(new Runnable() {
                Handler handler = null;
                @Override
                public void run() {
                    boolean shouldInit = !(new File(MediaParsingService.this.getDir("db", Context.MODE_PRIVATE) + Medialibrary.VLC_MEDIA_DB_NAME).exists());
                    int initCode = mMedialibrary.init(context);
                    shouldInit |= initCode == Medialibrary.ML_INIT_DB_RESET;
                    if (initCode != Medialibrary.ML_INIT_FAILED) {
                        final List<String> devices = new ArrayList<>();
                        Collections.addAll(devices, AndroidDevices.getMediaDirectories(context));
                        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                        for (final String device : devices) {
                            final boolean isMainStorage = TextUtils.equals(device, AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                            final String uuid = FileUtils.getFileNameFromPath(device);
                            if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid))
                                continue;
                            final boolean isNew = mMedialibrary.addDevice(isMainStorage ? "main-storage" : uuid, device, !isMainStorage);
                            final boolean isIgnored = sharedPreferences.getBoolean("ignore_" + uuid, false);
                            if (!isMainStorage && isNew && !isIgnored)
                                showStorageNotification(device);
                        }
                        mMedialibrary.start();
                        mLocalBroadcastManager.sendBroadcast(new Intent(VLCApplication.ACTION_MEDIALIBRARY_READY));
                        if (shouldInit) {
                            for (String folder : Medialibrary.getBlackList())
                                mMedialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + folder);
                            mMedialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                        } else if (upgrade) {
                            mMedialibrary.unbanFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/WhatsApp/");
                            mMedialibrary.banFolder(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/WhatsApp/Media/WhatsApp Animated Gifs/");
                            mMedialibrary.forceParserRetry();
                        } else if (PreferenceManager.getDefaultSharedPreferences(MediaParsingService.this).getBoolean("auto_rescan", true))
                            reload(null);
                        else
                            exitCommand();
                    }
                }

                private void showStorageNotification(final String device) {
                    if (handler == null) {
                        final HandlerThread ht = new HandlerThread("advisor");
                        ht.start();
                        handler = new Handler(ht.getLooper());
                    }
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mLocalBroadcastManager.sendBroadcast(new Intent(Constants.ACTION_NEW_STORAGE).putExtra(Constants.EXTRA_PATH, device));
                        }
                    }, 2000);
                }
            });
        }
    }

    private volatile boolean serviceLock = false;
    private void updateStorages() {
        mCallsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                serviceLock = true;
                final Context ctx = VLCApplication.getAppContext();
                final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
                final List<String> devices = AndroidDevices.getExternalStorageDirectories();
                final String[] knownDevices = mMedialibrary.getDevices();
                final List<String> missingDevices = Util.arrayToArrayList(knownDevices);
                missingDevices.remove("file://"+AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY);
                for (final String device : devices) {
                    final String uuid = FileUtils.getFileNameFromPath(device);
                    if (TextUtils.isEmpty(device) || TextUtils.isEmpty(uuid))
                        continue;
                    if (ExternalMonitor.containsDevice(knownDevices, device)) {
                        missingDevices.remove("file://"+device);
                        continue;
                    }
                    final boolean isNew = mMedialibrary.addDevice(uuid, device, true);
                    final boolean isIgnored = sharedPreferences.getBoolean("ignore_"+ uuid, false);
                    if (!isIgnored && isNew)
                        LocalBroadcastManager.getInstance(ctx).sendBroadcast(new Intent(Constants.ACTION_NEW_STORAGE).putExtra(Constants.EXTRA_PATH, device));
                }
                for (String device : missingDevices)
                    mMedialibrary.removeDevice(FileUtils.getFileNameFromPath(device));
                serviceLock = false;
                exitCommand();
            }
        });
    }
    private boolean wasWorking;
    final StringBuilder sb = new StringBuilder();
    private final Intent progessIntent = new Intent(Constants.ACTION_PROGRESS);
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
                                .putExtra(Constants.ACTION_PROGRESS_TEXT, progressText)
                                .putExtra(Constants.ACTION_PROGRESS_VALUE, mParsing));
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
        if (!mMedialibrary.isWorking() && !serviceLock)
            stopSelf();
    }

    @Override
    public void onDestroy() {
        mLocalBroadcastManager.sendBroadcast(new Intent(Constants.ACTION_SERVICE_ENDED));
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
