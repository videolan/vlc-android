/*****************************************************************************
 * DebugLogService.java
 *****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateFormat;

import org.videolan.vlc.gui.DebugLogActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Logcat;
import org.videolan.vlc.util.Util;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;

public class DebugLogService extends Service implements Logcat.Callback, Runnable {

    private static final int MSG_STARTED = 0;
    private static final int MSG_STOPPED = 1;
    private static final int MSG_ONLOG = 2;
    private static final int MSG_SAVED = 3;

    private static final int MAX_LINES = 20000;

    private Logcat mLogcat = null;
    private LinkedList<String> mLogList = new LinkedList<String>();
    private Thread mSaveThread = null;
    private final RemoteCallbackList<IDebugLogServiceCallback> mCallbacks = new RemoteCallbackList<IDebugLogServiceCallback>();
    private final IBinder mBinder = new DebugLogServiceStub(this);

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    static class DebugLogServiceStub extends IDebugLogService.Stub {
        private DebugLogService mService;
        DebugLogServiceStub(DebugLogService service) {
            mService = service;
        }
        public void start() {
            mService.start();
        }
        public void stop() {
            mService.stop();
        }
        public void clear() {
            mService.clear();
        }
        public void save() {
            mService.save();
        }
        public void registerCallback(IDebugLogServiceCallback cb) {
            mService.registerCallback(cb);
        }
        public void unregisterCallback(IDebugLogServiceCallback cb) {
            mService.unregisterCallback(cb);
        }
    }

    private synchronized void sendMessage(int what, String str) {
        int i = mCallbacks.beginBroadcast();
        while (i > 0) {
            i--;
            final IDebugLogServiceCallback cb = mCallbacks.getBroadcastItem(i);
            try {
                switch (what) {
                case MSG_STOPPED:
                    cb.onStopped();
                    break;
                case MSG_STARTED: {
                    cb.onStarted(mLogList);
                    break;
                }
                case MSG_ONLOG:
                    cb.onLog(str);
                    break;
                case MSG_SAVED:
                    cb.onSaved(str != null ? true : false, str);
                    break;
                }
            } catch (RemoteException e) {
            }
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public synchronized void onLog(String log) {
        if (mLogList.size() > MAX_LINES)
            mLogList.remove(0);
        mLogList.add(log);
        sendMessage(MSG_ONLOG, log);
    }

    public synchronized void start() {
        if (mLogcat != null)
            return;
        clear();
        mLogcat = new Logcat();
        mLogcat.start(this);

        final Intent debugLogIntent = new Intent(this, DebugLogActivity.class);
        debugLogIntent.setAction("android.intent.action.MAIN");
        debugLogIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final PendingIntent pi = PendingIntent.getActivity(this, 0, debugLogIntent, 0);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getResources().getString(R.string.log_service_title));
        builder.setContentText(getResources().getString(R.string.log_service_text));
        builder.setSmallIcon(R.drawable.ic_stat_vlc);
        builder.setContentIntent(pi);
        final Notification notification = builder.build();
        startForeground(R.string.log_service_title, notification);

        startService(new Intent(this, DebugLogService.class));
        sendMessage(MSG_STARTED, null);
    }

    public synchronized void stop() {
        mLogcat.stop();
        mLogcat = null;
        sendMessage(MSG_STOPPED, null);
        stopForeground(true);
        stopSelf();
    }

    public synchronized void clear() {
        mLogList.clear();
    }

    /* mSaveThread */
    @Override
    public void run() {
        final CharSequence timestamp = DateFormat.format(
                "yyyyMMdd_kkmmss", System.currentTimeMillis());
        final String filename = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/vlc_logcat_" + timestamp + ".log";
        boolean saved = true;
        FileOutputStream fos = null;
        OutputStreamWriter output = null;
        BufferedWriter bw = null;

        try {
            fos = new FileOutputStream(filename);
            output = new OutputStreamWriter(fos);
            bw = new BufferedWriter(output);
            synchronized (this) {
                for (String line : mLogList) {
                    bw.write(line);
                    bw.newLine();
                }
            }
        } catch (FileNotFoundException e) {
            saved = false;
        } catch (IOException ioe) {
            saved = false;
        } finally {
            saved &= Util.close(bw);
            saved &= Util.close(output);
            saved &= Util.close(fos);
        }
        synchronized (this) {
            mSaveThread = null;
            sendMessage(MSG_SAVED, saved ? filename : null);
        }
    }

    public synchronized void save() {
        if (mSaveThread != null) {
            try {
                mSaveThread.join();
            } catch (InterruptedException e) {}
            mSaveThread = null;
        }
        mSaveThread = new Thread(this);
        mSaveThread.start();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void registerCallback(IDebugLogServiceCallback cb) {
        if (cb != null) {
            mCallbacks.register(cb);
            sendMessage(mLogcat != null ? MSG_STARTED : MSG_STOPPED, null);
        }
    }

    private void unregisterCallback(IDebugLogServiceCallback cb) {
        if (cb != null)
            mCallbacks.unregister(cb);
    }

    public static class Client {

        public interface Callback {
            void onStarted(List<String> lostList);
            void onStopped();
            void onLog(String msg);
            void onSaved(boolean success, String path);
        }

        private boolean mBound = false;
        private final Context mContext;
        private Callback mCallback;
        private IDebugLogService mIDebugLogService;
        private Handler mHandler;

        private final IDebugLogServiceCallback.Stub mICallback = new IDebugLogServiceCallback.Stub() {
            @Override
            public void onStopped() throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onStopped();
                    }
                });
            }
            @Override
            public void onStarted(final List<String> logList) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onStarted(logList);
                    }
                });
            }
            @Override
            public void onLog(final String msg) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onLog(msg);
                    }
                });
            }
            @Override
            public void onSaved(final boolean success, final String path) throws RemoteException {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onSaved(success, path);
                    }
                });
            }
        };

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                synchronized (Client.this) {
                    mIDebugLogService = IDebugLogService.Stub.asInterface(service);
                    try {
                        mIDebugLogService.registerCallback(mICallback);
                    } catch (RemoteException e) {
                        release();
                        mContext.stopService(new Intent(mContext, DebugLogService.class));
                        mCallback.onStopped();
                    }
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                release();
                mContext.stopService(new Intent(mContext, DebugLogService.class));
                mCallback.onStopped();
            }
        };

        public Client(Context context, Callback cb) throws IllegalArgumentException {
            if (context == null | cb == null)
                throw new IllegalArgumentException("Context and Callback can't be null");

            mContext = context;
            mCallback = cb;
            mHandler = new Handler(Looper.getMainLooper());
            mBound = mContext.bindService(new Intent(mContext, DebugLogService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
        }

        public boolean start() {
            synchronized (this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService.start();
                        return true;
                    } catch (RemoteException e) {
                    }
                }
                return false;
            }
        }
        public boolean stop() {
            synchronized (this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService.stop();
                        return true;
                    } catch (RemoteException e) {
                    }
                }
                return false;
            }
        }
        public boolean clear() {
            synchronized (this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService.clear();
                        return true;
                    } catch (RemoteException e) {
                    }
                }
                return false;
            }
        }
        public boolean save() {
            synchronized (this) {
                if (mIDebugLogService != null) {
                    try {
                        mIDebugLogService.save();
                        return true;
                    } catch (RemoteException e) {
                    }
                }
                return false;
            }
        }
        public void release() {
            if (mBound) {
                synchronized (this) {
                    if (mIDebugLogService != null && mICallback != null) {
                        try {
                            mIDebugLogService.unregisterCallback(mICallback);
                        } catch (RemoteException e) {
                        }
                        mIDebugLogService = null;
                    }
                }
                mBound = false;
                mContext.unbindService(mServiceConnection);
            }
            mHandler.removeCallbacksAndMessages(null);
        }
    }
}