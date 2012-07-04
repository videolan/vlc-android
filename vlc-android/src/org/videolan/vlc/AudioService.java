/*****************************************************************************
 * AudioService.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.audio.AudioPlayerActivity;
import org.videolan.vlc.interfaces.IAudioService;
import org.videolan.vlc.interfaces.IAudioServiceCallback;
import org.videolan.vlc.widget.VLCAppWidgetProvider;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RemoteViews;

public class AudioService extends Service {

    private static final String TAG = "VLC/AudioService";

    private static final int SHOW_PROGRESS = 0;
    public static final String START_FROM_NOTIFICATION = "from_notification";

    private LibVLC mLibVLC;
    private ArrayList<Media> mMediaList;
    private Stack<Media> mPrevious;
    private Media mCurrentMedia;
    private HashMap<IAudioServiceCallback, Integer> mCallback;
    private EventManager mEventManager;
    private Notification mNotification;
    private boolean mShuffling = false;
    private RepeatType mRepeating = RepeatType.None;
    private boolean mDetectHeadset = true;
    private long mHeadsetDownTime = 0;
    private long mHeadsetUpTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        // Get libVLC instance
        try {
            LibVLC.useIOMX(this);
            mLibVLC = LibVLC.getInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
        }

        Thread.setDefaultUncaughtExceptionHandler(new VlcCrashHandler());

        mCallback = new HashMap<IAudioServiceCallback, Integer>();
        mMediaList = new ArrayList<Media>();
        mPrevious = new Stack<Media>();
        mEventManager = EventManager.getIntance();

        IntentFilter filter = new IntentFilter();
        filter.setPriority(Integer.MAX_VALUE);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_BACKWARD);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_PLAY);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_STOP);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_FORWARD);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_MEDIA_BUTTON);
        registerReceiver(serviceReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateWidget(this);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(serviceReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mInterface;
    }

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state = intent.getIntExtra("state", 0);
            if( mLibVLC == null ) {
                Log.w(TAG, "Intent received, but VLC is not loaded, skipping.");
                return;
            }

            /*
             * widget events
             */
            if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_PLAY)) {
                if (mLibVLC.isPlaying() && mCurrentMedia != null) {
                    pause();
                } else if (!mLibVLC.isPlaying() && mCurrentMedia != null) {
                    play();
                } else {
                    Intent iVlc = new Intent(context, MainActivity.class);
                    iVlc.putExtra(START_FROM_NOTIFICATION, true);
                    iVlc.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(iVlc);
                }
            } else if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_BACKWARD)) {
                previous();
            }
            else if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_STOP)) {
                stop();
            }
            else if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_FORWARD)) {
                next();
            }

            /*
             * headset controller events
             */
            else if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_BUTTON)) {
                KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (event == null || telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                    return;

                if (mCurrentMedia == null) {
                    abortBroadcast();
                    return;
                }

                switch (event.getKeyCode())
                {
                /*
                 * one click => play/pause
                 * long click => previous
                 * double click => next
                 */
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        long time = SystemClock.uptimeMillis();
                        switch (event.getAction())
                        {
                            case KeyEvent.ACTION_DOWN:
                                if (event.getRepeatCount() > 0)
                                    break;
                                mHeadsetDownTime = time;
                                break;
                            case KeyEvent.ACTION_UP:
                                // long click
                                if (time - mHeadsetDownTime >= 1000) {
                                    previous();
                                    time = 0;
                                    // double click
                                } else if (time - mHeadsetUpTime <= 500) {
                                    next();
                                }
                                // one click
                                else {
                                    if (mLibVLC.isPlaying())
                                        pause();
                                    else
                                        play();
                                }
                                mHeadsetUpTime = time;
                                break;
                        }
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        play();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        pause();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_STOP:
                        stop();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        next();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        previous();
                        break;
                }
                abortBroadcast();
            }

            /*
             * headset plug events
             */
            if (mDetectHeadset) {
                if (action.equalsIgnoreCase(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    Log.i(TAG, "Headset Removed.");
                    if (mLibVLC.isPlaying() && mCurrentMedia != null)
                        pause();
                }
                else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.i(TAG, "Headset Inserted.");
                    if (!mLibVLC.isPlaying() && mCurrentMedia != null)
                        play();
                }
            }
        }
    };

    /**
     * Handle libvlc asynchronous events
     */
    private final Handler mEventHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.getData().getInt("event")) {
                case EventManager.MediaPlayerPlaying:
                    Log.i(TAG, "MediaPlayerPlaying");
                    break;
                case EventManager.MediaPlayerPaused:
                    Log.i(TAG, "MediaPlayerPaused");
                    executeUpdate();
                    // also hide notification if phone ringing
                    hideNotification();
                    break;
                case EventManager.MediaPlayerStopped:
                    Log.i(TAG, "MediaPlayerStopped");
                    executeUpdate();
                    break;
                case EventManager.MediaPlayerEndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    executeUpdate();
                    next();
                    break;
                default:
                    Log.e(TAG, "Event not handled");
                    break;
            }
        }
    };

    private void executeUpdate() {
        executeUpdate(true);
    }

    private void executeUpdate(Boolean updateWidget) {
        for (IAudioServiceCallback callback : mCallback.keySet()) {
            try {
                callback.update();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (updateWidget)
            updateWidget(this);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (mCallback.size() > 0) {
                        removeMessages(SHOW_PROGRESS);
                        executeUpdate(false);
                        sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
                    }
                    break;
            }
        }
    };

    private void showNotification() {
        // add notification to status bar
        if (mNotification == null) {
            mNotification = new Notification(R.drawable.icon, null,
                    System.currentTimeMillis());
        }
        Intent notificationIntent = new Intent(this, AudioPlayerActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.putExtra(START_FROM_NOTIFICATION, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        mNotification.setLatestEventInfo(this, mCurrentMedia.getTitle(),
                mCurrentMedia.getArtist() + " - " + mCurrentMedia.getAlbum(), pendingIntent);
        startForeground(3, mNotification);

    }

    private void hideNotification() {
        mNotification = null;
        stopForeground(true);
    }

    private void pause() {
        mHandler.removeMessages(SHOW_PROGRESS);
        // hideNotification(); <-- see event handler
        mLibVLC.pause();
    }

    private void play() {
        if (mCurrentMedia != null) {
            mLibVLC.play();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            showNotification();
            updateWidget(this);
        }
    }

    private void stop() {
        mEventManager.removeHandler(mEventHandler);
        mLibVLC.stop();
        mCurrentMedia = null;
        mMediaList.clear();
        mPrevious.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        executeUpdate();
    }

    private void next() {
        int index = mMediaList.indexOf(mCurrentMedia);
        mPrevious.push(mCurrentMedia);
        if (mRepeating == RepeatType.Once)
            mCurrentMedia = mMediaList.get(index);
        else if (mShuffling && mPrevious.size() < mMediaList.size()) {
            while (mPrevious.contains(mCurrentMedia = mMediaList
                           .get((int) (Math.random() * mMediaList.size()))))
                ;
        } else if (!mShuffling && index < mMediaList.size() - 1) {
            mCurrentMedia = mMediaList.get(index + 1);
        } else {
            if (mRepeating == RepeatType.All)
                mCurrentMedia = mMediaList.get(0);
            else {
                stop();
                return;
            }
        }
        mLibVLC.readMedia(mCurrentMedia.getLocation(), true);
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        showNotification();
        updateWidget(this);
    }

    private void previous() {
        int index = mMediaList.indexOf(mCurrentMedia);
        if (mPrevious.size() > 0)
            mCurrentMedia = mPrevious.pop();
        else if (index > 0)
            mCurrentMedia = mMediaList.get(index - 1);
        else
            return;
        mLibVLC.readMedia(mCurrentMedia.getLocation(), true);
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        showNotification();
        updateWidget(this);
    }

    private void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
    }

    private void setRepeatType(int t) {
        mRepeating = RepeatType.values()[t];
    }

    @SuppressLint("SdCardPath")
    private Bitmap getCover() {
        try {
            // try to get the cover from android MediaStore
            ContentResolver contentResolver = getContentResolver();
            Uri uri = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
            Cursor cursor = contentResolver.query(uri, new String[] {
                           MediaStore.Audio.Albums.ALBUM,
                           MediaStore.Audio.Albums.ALBUM_ART },
                           MediaStore.Audio.Albums.ALBUM + " LIKE ?",
                           new String[] { mCurrentMedia.getAlbum() }, null);
            if (cursor == null) {
                // do nothing
            } else if (!cursor.moveToFirst()) {
                // do nothing
                cursor.close();
            } else {
                int titleColumn = cursor.getColumnIndex(android.provider.MediaStore.Audio.Albums.ALBUM_ART);
                String albumArt = cursor.getString(titleColumn);
                cursor.close();
                Bitmap b = BitmapFactory.decodeFile(albumArt);
                if (b != null)
                    return b;
            }

            //cover not in MediaStore, trying vlc
            String artworkURL = mCurrentMedia.getArtworkURL();
            final String cacheDir = "/sdcard/Android/data/org.videolan.vlc/cache";
            if (artworkURL != null && artworkURL.startsWith("file://")) {
                return BitmapFactory.decodeFile(Uri.decode(artworkURL).replace("file://", ""));
            } else if(artworkURL != null && artworkURL.startsWith("attachment://")) {
                // Decode if the album art is embedded in the file
                String mArtist = mCurrentMedia.getArtist();
                String mAlbum = mCurrentMedia.getAlbum();

                /* Parse decoded attachment */
                if( mArtist.length() == 0 || mAlbum.length() == 0 ||
                    mArtist.equals(VLCApplication.getAppContext().getString(R.string.unknown_artist)) ||
                    mAlbum.equals(VLCApplication.getAppContext().getString(R.string.unknown_album)) )
                {
                    /* If artist or album are missing, it was cached by title MD5 hash */
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    byte[] binHash = md.digest((artworkURL + mCurrentMedia.getTitle()).getBytes("UTF-8"));
                    /* Convert binary hash to normal hash */
                    BigInteger hash = new BigInteger(1, binHash);
                    String titleHash = hash.toString(16);
                    while(titleHash.length() < 32) {
                        titleHash = "0" + titleHash;
                    }
                    /* Use generated hash to find art */
                    artworkURL = cacheDir + "/art/arturl/" + titleHash + "/art.png";
                } else {
                    /* Otherwise, it was cached by artist and album */
                    artworkURL = cacheDir + "/art/artistalbum/" + mArtist + "/" + mAlbum + "/art.png";
                }

                Log.v(TAG, "artworkURL (calculated) = " + artworkURL);
                return BitmapFactory.decodeFile(artworkURL);
            }

            //still no cover found, looking in folder ...
            File f = Util.URItoFile(mCurrentMedia.getLocation());
            for (File s : f.getParentFile().listFiles()) {
                if (s.getAbsolutePath().endsWith("png") ||
                        s.getAbsolutePath().endsWith("jpg"))
                    return BitmapFactory.decodeFile(s.getAbsolutePath());
            }
        } catch (Exception e) {
        }
        return null;
    }

    private final IAudioService.Stub mInterface = new IAudioService.Stub() {

        @Override
        public String getCurrentMediaLocation() throws RemoteException {
            return mCurrentMedia.getLocation();
        }

        @Override
        public void pause() throws RemoteException {
            AudioService.this.pause();
        }

        @Override
        public void play() throws RemoteException {
            AudioService.this.play();
        }

        @Override
        public void stop() throws RemoteException {
            AudioService.this.stop();
        }

        @Override
        public boolean isPlaying() throws RemoteException {
            return mLibVLC.isPlaying();
        }

        @Override
        public boolean isShuffling() {
            return mShuffling;
        }

        @Override
        public int getRepeatType() {
            return mRepeating.ordinal();
        }

        @Override
        public boolean hasMedia() throws RemoteException {
            return mMediaList.size() != 0;
        }

        @Override
        public String getAlbum() throws RemoteException {
            if (mCurrentMedia != null)
                return mCurrentMedia.getAlbum();
            else
                return null;
        }

        @Override
        public String getArtist() throws RemoteException {
            if (mCurrentMedia != null)
                return mCurrentMedia.getArtist();
            else
                return null;
        }

        @Override
        public String getTitle() throws RemoteException {
            if (mCurrentMedia != null)
                return mCurrentMedia.getTitle();
            else
                return null;
        }

        @Override
        public Bitmap getCover() {
            if (mCurrentMedia != null) {
                return AudioService.this.getCover();
            }
            return null;
        }

        @Override
        public synchronized void addAudioCallback(IAudioServiceCallback cb)
                throws RemoteException {
            Integer count = mCallback.get(cb);
            if (count == null)
                count = 0;
            mCallback.put(cb, count + 1);
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }

        @Override
        public synchronized void removeAudioCallback(IAudioServiceCallback cb)
                throws RemoteException {
            Integer count = mCallback.get(cb);
            if (count == null)
                count = 0;
            if (count > 1)
                mCallback.put(cb, count - 1);
            else
                mCallback.remove(cb);
        }

        @Override
        public int getTime() throws RemoteException {
            return (int) mLibVLC.getTime();
        }

        @Override
        public int getLength() throws RemoteException {
            return (int) mLibVLC.getLength();
        }

        @Override
        public void load(List<String> mediaPathList, int position)
                throws RemoteException {
            Log.v(TAG, "Loading position " + ((Integer)position).toString() + " in " + mediaPathList.toString());
            mEventManager.addHandler(mEventHandler);
            mMediaList.clear();
            mPrevious.clear();
            DatabaseManager db = DatabaseManager.getInstance(AudioService.this);
            for (int i = 0; i < mediaPathList.size(); i++) {
                String path = mediaPathList.get(i);
                Media media = db.getMedia(AudioService.this, path);
                if(media == null) {
                    Log.v(TAG, "Creating on-the-fly Media object for " + path);
                    media = new Media(path, false);
                }
                mMediaList.add(media);
            }

            if (mMediaList.size() > position) {
                mCurrentMedia = mMediaList.get(position);
            }

            if (mCurrentMedia != null) {
                mLibVLC.readMedia(mCurrentMedia.getLocation(), true);
                showNotification();
            }
        }

        @Override
        public void append(List<String> mediaPathList) throws RemoteException {
            if (mMediaList.size() == 0) {
                load(mediaPathList, 0);
                return;
            }

            DatabaseManager db = DatabaseManager.getInstance(AudioService.this);
            for (int i = 0; i < mediaPathList.size(); i++) {
                String path = mediaPathList.get(i);
                Media media = db.getMedia(AudioService.this, path);
                mMediaList.add(media);
            }
        }

        @Override
        public List<String> getItems() {
            ArrayList<String> medias = new ArrayList<String>();
            for (int i = 0; i < mMediaList.size(); i++) {
                Media item = mMediaList.get(i);
                medias.add(item.getLocation());
            }
            return medias;
        }

        @Override
        public String getItem() {
            return mCurrentMedia != null
                    ? mCurrentMedia.getLocation()
                    : null;
        }

        @Override
        public void next() throws RemoteException {
            AudioService.this.next();
        }

        @Override
        public void previous() throws RemoteException {
            AudioService.this.previous();
        }

        @Override
        public void shuffle() throws RemoteException {
            AudioService.this.shuffle();
        }

        @Override
        public void setRepeatType(int t) throws RemoteException {
            AudioService.this.setRepeatType(t);
        }

        @Override
        public void setTime(long time) throws RemoteException {
            mLibVLC.setTime(time);
        }

        @Override
        public boolean hasNext() throws RemoteException {
            if (mRepeating == RepeatType.Once)
                return false;
            int index = mMediaList.indexOf(mCurrentMedia);
            if (mShuffling && mPrevious.size() < mMediaList.size() - 1 ||
                !mShuffling && index < mMediaList.size() - 1)
                return true;
            else
                return false;
        }

        @Override
        public boolean hasPrevious() throws RemoteException {
            if (mRepeating == RepeatType.Once)
                return false;
            int index = mMediaList.indexOf(mCurrentMedia);
            if (mPrevious.size() > 0 || index > 0)
                return true;
            else
                return false;
        }

        @Override
        public void detectHeadset(boolean enable) throws RemoteException {
            mDetectHeadset = enable;
        }
    };

    private void updateWidget(Context context)
    {
        Log.d(TAG, "Updating widget");
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.vlcwidget);
        Bitmap cover = null;

        if (mCurrentMedia != null) {
            views.setTextViewText(R.id.songName, mCurrentMedia.getTitle());
            views.setTextViewText(R.id.artist, mCurrentMedia.getArtist());
            cover = Util.scaleDownBitmap(context, getCover(), 64);
        }
        else {
            views.setTextViewText(R.id.songName, "VLC mini player");
            views.setTextViewText(R.id.artist, "");
            cover = null;
        }

        if (cover != null)
            views.setImageViewBitmap(R.id.cover, cover);
        else
            views.setImageViewResource(R.id.cover, R.drawable.cone);

        views.setImageViewResource(R.id.play_pause, mLibVLC.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        /* commands */
        Intent iBackward = new Intent();
        iBackward.setAction(VLCAppWidgetProvider.ACTION_WIDGET_BACKWARD);
        Intent iPlay = new Intent();
        iPlay.setAction(VLCAppWidgetProvider.ACTION_WIDGET_PLAY);
        Intent iStop = new Intent();
        iStop.setAction(VLCAppWidgetProvider.ACTION_WIDGET_STOP);
        Intent iForward = new Intent();
        iForward.setAction(VLCAppWidgetProvider.ACTION_WIDGET_FORWARD);
        Intent iVlc = new Intent(context, MainActivity.class);
        iVlc.putExtra(START_FROM_NOTIFICATION, true);

        PendingIntent piBackward = PendingIntent.getBroadcast(context, 0, iBackward, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piPlay = PendingIntent.getBroadcast(context, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piStop = PendingIntent.getBroadcast(context, 0, iStop, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piForward = PendingIntent.getBroadcast(context, 0, iForward, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piVlc = PendingIntent.getActivity(context, 0, iVlc, PendingIntent.FLAG_UPDATE_CURRENT);

        views.setOnClickPendingIntent(R.id.backward, piBackward);
        views.setOnClickPendingIntent(R.id.play_pause, piPlay);
        views.setOnClickPendingIntent(R.id.stop, piStop);
        views.setOnClickPendingIntent(R.id.forward, piForward);
        views.setOnClickPendingIntent(R.id.cover, piVlc);

        /* update widget */
        ComponentName widget = new ComponentName(context, VLCAppWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        manager.updateAppWidget(widget, views);
    }
}
