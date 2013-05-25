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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.audio.AudioPlayerActivity;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.IAudioService;
import org.videolan.vlc.interfaces.IAudioServiceCallback;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class AudioService extends Service {

    private static final String TAG = "VLC/AudioService";

    private static final int SHOW_PROGRESS = 0;
    private static final int SHOW_TOAST = 1;
    public static final String START_FROM_NOTIFICATION = "from_notification";
    public static final String ACTION_REMOTE_GENERIC = "org.videolan.vlc.remote.";
    public static final String ACTION_REMOTE_BACKWARD = "org.videolan.vlc.remote.Backward";
    public static final String ACTION_REMOTE_PLAY = "org.videolan.vlc.remote.Play";
    public static final String ACTION_REMOTE_PLAYPAUSE = "org.videolan.vlc.remote.PlayPause";
    public static final String ACTION_REMOTE_PAUSE = "org.videolan.vlc.remote.Pause";
    public static final String ACTION_REMOTE_STOP = "org.videolan.vlc.remote.Stop";
    public static final String ACTION_REMOTE_FORWARD = "org.videolan.vlc.remote.Forward";
    public static final String ACTION_REMOTE_LAST_PLAYLIST = "org.videolan.vlc.remote.LastPlaylist";
    public static final String ACTION_WIDGET_INIT = "org.videolan.vlc.widget.INIT";
    public static final String ACTION_WIDGET_UPDATE = "org.videolan.vlc.widget.UPDATE";
    public static final String ACTION_WIDGET_UPDATE_POSITION = "org.videolan.vlc.widget.UPDATE_POSITION";

    public static final String WIDGET_PACKAGE = "org.videolan.vlc";
    public static final String WIDGET_CLASS = "org.videolan.vlc.widget.VLCAppWidgetProvider";

    private LibVLC mLibVLC;
    private ArrayList<Media> mMediaList;
    private Stack<Media> mPrevious;
    private Media mCurrentMedia;
    private HashMap<IAudioServiceCallback, Integer> mCallback;
    private EventHandler mEventHandler;
    private boolean mShuffling = false;
    private RepeatType mRepeating = RepeatType.None;
    private boolean mDetectHeadset = true;
    private OnAudioFocusChangeListener audioFocusListener;
    private ComponentName mRemoteControlClientReceiverComponent;
    private PowerManager.WakeLock mWakeLock;

    /**
     * RemoteControlClient is for lock screen playback control.
     */
    private RemoteControlClient mRemoteControlClient = null;
    private RemoteControlClientReceiver mRemoteControlClientReceiver = null;

    /**
     * Distinguish between the "fake" (Java-backed) playlist versus the "real"
     * (LibVLC/LibVLCcore) backed playlist.
     *
     * True if being backed by LibVLC, false if "virtually" backed by Java.
     */
    private boolean mLibVLCPlaylistActive = false;

    /**
     * Last widget position update timestamp
     */
    private long mWidgetPositionTimestamp = Calendar.getInstance().getTimeInMillis();

    @Override
    public void onCreate() {
        super.onCreate();

        // Get libVLC instance
        try {
            mLibVLC = Util.getLibVlcInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
        }

        mCallback = new HashMap<IAudioServiceCallback, Integer>();
        mMediaList = new ArrayList<Media>();
        mPrevious = new Stack<Media>();
        mEventHandler = EventHandler.getInstance();
        mRemoteControlClientReceiverComponent = new ComponentName(getPackageName(),
                RemoteControlClientReceiver.class.getName());

        // Make sure the audio player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stop.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        IntentFilter filter = new IntentFilter();
        filter.setPriority(Integer.MAX_VALUE);
        filter.addAction(ACTION_REMOTE_BACKWARD);
        filter.addAction(ACTION_REMOTE_PLAYPAUSE);
        filter.addAction(ACTION_REMOTE_PLAY);
        filter.addAction(ACTION_REMOTE_PAUSE);
        filter.addAction(ACTION_REMOTE_STOP);
        filter.addAction(ACTION_REMOTE_FORWARD);
        filter.addAction(ACTION_REMOTE_LAST_PLAYLIST);
        filter.addAction(ACTION_WIDGET_INIT);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(serviceReceiver, filter);

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean stealRemoteControl = pref.getBoolean("enable_steal_remote_control", false);

        if(!Util.isFroyoOrLater() || stealRemoteControl) {
            /* Backward compatibility for API 7 */
            filter = new IntentFilter();
            if (stealRemoteControl)
                filter.setPriority(Integer.MAX_VALUE);
            filter.addAction(Intent.ACTION_MEDIA_BUTTON);
            mRemoteControlClientReceiver = new RemoteControlClientReceiver();
            registerReceiver(mRemoteControlClientReceiver, filter);
        }

        AudioUtil.prepareCacheFolder(this);
    }


    /**
     * Set up the remote control and tell the system we want to be the default receiver for the MEDIA buttons
     * @see http://android-developers.blogspot.fr/2010/06/allowing-applications-to-play-nicer.html
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void setUpRemoteControlClient() {
        Context context = VLCApplication.getAppContext();
        AudioManager audioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);

        if(Util.isICSOrLater()) {
            audioManager.registerMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);

            if (mRemoteControlClient == null) {
                Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaButtonIntent.setComponent(mRemoteControlClientReceiverComponent);
                PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);

                // create and register the remote control client
                mRemoteControlClient = new RemoteControlClient(mediaPendingIntent);
                audioManager.registerRemoteControlClient(mRemoteControlClient);
            }

            mRemoteControlClient.setTransportControlFlags(
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
                    RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
                    RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
                    RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
                    RemoteControlClient.FLAG_KEY_MEDIA_STOP);
        } else if (Util.isFroyoOrLater()) {
            audioManager.registerMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
        }
    }

    /**
     * A function to control the Remote Control Client. It is needed for
     * compatibility with devices below Ice Cream Sandwich (4.0).
     *
     * @param p Playback state
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setRemoteControlClientPlaybackState(int state) {
        if(!Util.isICSOrLater() || mRemoteControlClient == null)
            return;

        switch (state) {
            case EventHandler.MediaPlayerPlaying:
                mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
                break;
            case EventHandler.MediaPlayerPaused:
                mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
                break;
            case EventHandler.MediaPlayerStopped:
                mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
                break;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateWidget(this);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        if (mWakeLock.isHeld())
            mWakeLock.release();
        unregisterReceiver(serviceReceiver);
        if (mRemoteControlClientReceiver != null) {
            unregisterReceiver(mRemoteControlClientReceiver);
            mRemoteControlClientReceiver = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mInterface;
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private void changeAudioFocus(boolean gain) {
        if(!Util.isFroyoOrLater()) // NOP if not supported
            return;

        audioFocusListener = new OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                if(focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ||
                   focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    /*
                     * Lower the volume to 36% to "duck" when an alert or something
                     * needs to be played.
                     */
                    LibVLC.getExistingInstance().setVolume(36);
                } else {
                    LibVLC.getExistingInstance().setVolume(100);
                }
            }
        };

        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        if(gain)
            am.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        else
            am.abandonAudioFocus(audioFocusListener);

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

            // skip all headsets events if there is a call
            TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !mLibVLC.isPlaying() && mCurrentMedia == null) {
                Intent iVlc = new Intent(context, MainActivity.class);
                iVlc.putExtra(START_FROM_NOTIFICATION, true);
                iVlc.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(iVlc);
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(ACTION_REMOTE_PLAYPAUSE)) {
                if (mLibVLC.isPlaying() && mCurrentMedia != null)
                    pause();
                else if (!mLibVLC.isPlaying() && mCurrentMedia != null)
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PLAY)) {
                if (!mLibVLC.isPlaying() && mCurrentMedia != null)
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PAUSE)) {
                if (mLibVLC.isPlaying() && mCurrentMedia != null)
                    pause();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_BACKWARD)) {
                previous();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_STOP)) {
                stop();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_FORWARD)) {
                next();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_PLAYLIST)) {
                loadLastPlaylist();
            } else if (action.equalsIgnoreCase(ACTION_WIDGET_INIT)) {
                updateWidget(context);
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

            /*
             * Sleep
             */
            if (action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
                stop();
            }
        }
    };

    /**
     * Handle libvlc asynchronous events
     */
    private final Handler mVlcEventHandler = new AudioServiceEventHandler(this);

    private static class AudioServiceEventHandler extends WeakHandler<AudioService> {
        public AudioServiceEventHandler(AudioService fragment) {
            super(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioService service = getOwner();
            if(service == null) return;

            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaPlayerPlaying:
                    Log.i(TAG, "MediaPlayerPlaying");

                    if (service.mCurrentMedia == null)
                        return;
                    String location = service.mCurrentMedia.getLocation();
                    long length = service.mLibVLC.getLength();
                    MediaDatabase dbManager = MediaDatabase
                            .getInstance(VLCApplication.getAppContext());
                    Media m = dbManager.getMedia(VLCApplication.getAppContext(),
                            location);
                    /**
                     * 1) There is a media to update
                     * 2) It has a length of 0
                     * (dynamic track loading - most notably the OGG container)
                     * 3) We were able to get a length even after parsing
                     * (don't want to replace a 0 with a 0)
                     */
                    if(m != null && m.getLength() == 0 && length > 0) {
                        Log.d(TAG, "Updating audio file length");
                        dbManager.updateMedia(location,
                                MediaDatabase.mediaColumn.MEDIA_LENGTH, length);
                    }

                    service.changeAudioFocus(true);
                    service.setRemoteControlClientPlaybackState(EventHandler.MediaPlayerPlaying);
                    if (!service.mWakeLock.isHeld())
                        service.mWakeLock.acquire();
                    break;
                case EventHandler.MediaPlayerPaused:
                    Log.i(TAG, "MediaPlayerPaused");
                    service.executeUpdate();
                    service.showNotification();
                    service.setRemoteControlClientPlaybackState(EventHandler.MediaPlayerPaused);
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                case EventHandler.MediaPlayerStopped:
                    Log.i(TAG, "MediaPlayerStopped");
                    service.executeUpdate();
                    service.setRemoteControlClientPlaybackState(EventHandler.MediaPlayerStopped);
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                case EventHandler.MediaPlayerEndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    service.executeUpdate();
                    service.next();
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                case EventHandler.MediaPlayerVout:
                    if(msg.getData().getInt("data") > 0) {
                        service.handleVout();
                    }
                    break;
                case EventHandler.MediaPlayerPositionChanged:
                    float pos = msg.getData().getFloat("data");
                    service.updateWidgetPosition(service, pos);
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    if (service.mCurrentMedia != null) {
                        service.showToast(service.getString(R.string.invalid_location,
                                service.mCurrentMedia.getLocation()), Toast.LENGTH_SHORT);
                    }
                    service.executeUpdate();
                    service.next();
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                default:
                    Log.e(TAG, "Event not handled");
                    break;
            }
        }
    };

    private void handleVout() {
        Log.i(TAG, "Obtained video track");
        mMediaList.clear();
        hideNotification();

        // Don't crash if user stopped the media
        if(mCurrentMedia == null) return;

        // Switch to the video player & don't lose the currently playing stream
        VideoPlayerActivity.start(VLCApplication.getAppContext(), mCurrentMedia.getLocation(), mCurrentMedia.getTitle(), true);
    }

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

    private final Handler mHandler = new AudioServiceHandler(this);

    private static class AudioServiceHandler extends WeakHandler<AudioService> {
        public AudioServiceHandler(AudioService fragment) {
            super(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioService service = getOwner();
            if(service == null) return;

            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (service.mCallback.size() > 0) {
                        removeMessages(SHOW_PROGRESS);
                        service.executeUpdate(false);
                        sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
                    }
                    break;
                case SHOW_TOAST:
                    final Bundle bundle = msg.getData();
                    final String text = bundle.getString("text");
                    final int duration = bundle.getInt("duration");
                    Toast.makeText(VLCApplication.getAppContext(), text, duration).show();
                    break;
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showNotification() {
        try {
            Bitmap cover = AudioUtil.getCover(this, mCurrentMedia, 64);
            String title = mCurrentMedia.getTitle();
            String artist = mCurrentMedia.getArtist();
            String album = mCurrentMedia.getAlbum();
            Notification notification;

            // add notification to status bar
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_vlc)
                .setTicker(title + " - " + artist)
                .setAutoCancel(false)
                .setOngoing(true);

            Intent notificationIntent = new Intent(this, AudioPlayerActivity.class);
            notificationIntent.setAction(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            notificationIntent.putExtra(START_FROM_NOTIFICATION, true);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            if (Util.isJellyBeanOrLater()) {
                Intent iBackward = new Intent(ACTION_REMOTE_BACKWARD);
                Intent iPlay = new Intent(ACTION_REMOTE_PLAYPAUSE);
                Intent iForward = new Intent(ACTION_REMOTE_FORWARD);
                Intent iStop = new Intent(ACTION_REMOTE_STOP);
                PendingIntent piBackward = PendingIntent.getBroadcast(this, 0, iBackward, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piPlay = PendingIntent.getBroadcast(this, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piForward = PendingIntent.getBroadcast(this, 0, iForward, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piStop = PendingIntent.getBroadcast(this, 0, iStop, PendingIntent.FLAG_UPDATE_CURRENT);

                RemoteViews view = new RemoteViews(getPackageName(), R.layout.notification);
                if (cover != null)
                    view.setImageViewBitmap(R.id.cover, cover);
                view.setTextViewText(R.id.songName, title);
                view.setTextViewText(R.id.artist, artist);
                view.setImageViewResource(R.id.play_pause, mLibVLC.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
                view.setOnClickPendingIntent(R.id.play_pause, piPlay);
                view.setOnClickPendingIntent(R.id.forward, piForward);
                view.setOnClickPendingIntent(R.id.stop, piStop);
                view.setOnClickPendingIntent(R.id.content, pendingIntent);

                RemoteViews view_expanded = new RemoteViews(getPackageName(), R.layout.notification_expanded);
                if (cover != null)
                    view_expanded.setImageViewBitmap(R.id.cover, cover);
                view_expanded.setTextViewText(R.id.songName, title);
                view_expanded.setTextViewText(R.id.artist, artist);
                view_expanded.setTextViewText(R.id.album, album);
                view_expanded.setImageViewResource(R.id.play_pause, mLibVLC.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
                view_expanded.setOnClickPendingIntent(R.id.backward, piBackward);
                view_expanded.setOnClickPendingIntent(R.id.play_pause, piPlay);
                view_expanded.setOnClickPendingIntent(R.id.forward, piForward);
                view_expanded.setOnClickPendingIntent(R.id.stop, piStop);
                view_expanded.setOnClickPendingIntent(R.id.content, pendingIntent);

                notification = builder.build();
                notification.contentView = view;
                notification.bigContentView = view_expanded;
            }
            else {
                builder.setLargeIcon(cover)
                       .setContentTitle(title)
                       .setContentText(Util.isJellyBeanOrLater() ? artist : mCurrentMedia.getSubtitle())
                       .setContentInfo(album)
                       .setContentIntent(pendingIntent);
                notification = builder.build();
            }

            startService(new Intent(this, AudioService.class));
            startForeground(3, notification);
        }
        catch (NoSuchMethodError e){
            // Compat library is wrong on 3.2
            // http://code.google.com/p/android/issues/detail?id=36359
            // http://code.google.com/p/android/issues/detail?id=36502
        }
    }

    private void hideNotification() {
        stopForeground(true);
        stopSelf();
    }

    private void pause() {
        setUpRemoteControlClient();
        mHandler.removeMessages(SHOW_PROGRESS);
        // hideNotification(); <-- see event handler
        mLibVLC.pause();
    }

    private void play() {
        if (mCurrentMedia != null) {
            setUpRemoteControlClient();
            mLibVLC.play();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            showNotification();
            updateWidget(this);
        }
    }

    private void stop() {
        mLibVLC.stop();
        mEventHandler.removeHandler(mVlcEventHandler);
        setRemoteControlClientPlaybackState(EventHandler.MediaPlayerStopped);
        mCurrentMedia = null;
        mMediaList.clear();
        mPrevious.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        executeUpdate();
        changeAudioFocus(false);
    }

    private void next() {
        int index = mMediaList.indexOf(mCurrentMedia);
        mPrevious.push(mCurrentMedia);
        if (mRepeating == RepeatType.Once && index < mMediaList.size())
            mCurrentMedia = mMediaList.get(index);
        else if (mShuffling && mPrevious.size() < mMediaList.size()) {
            while (mPrevious.contains(mCurrentMedia = mMediaList
                           .get((int) (Math.random() * mMediaList.size()))))
                ;
        } else if (!mShuffling && index < mMediaList.size() - 1) {
            mCurrentMedia = mMediaList.get(index + 1);
        } else {
            if (mRepeating == RepeatType.All && mMediaList.size() > 0)
                mCurrentMedia = mMediaList.get(0);
            else {
                stop();
                return;
            }
        }
        if(mLibVLCPlaylistActive) {
            if(mRepeating == RepeatType.None)
                mLibVLC.next();
            else if(mRepeating == RepeatType.Once)
                mLibVLC.playIndex(index);
            else
                mLibVLC.playIndex(mMediaList.indexOf(mCurrentMedia));
        } else {
            mLibVLC.readMedia(mCurrentMedia.getLocation(), true);
        }
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        setUpRemoteControlClient();
        showNotification();
        updateWidget(this);
        updateRemoteControlClientMetadata();
        saveCurrentMedia();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void updateRemoteControlClientMetadata() {
        if(!Util.isICSOrLater()) // NOP check
            return;

        if (mRemoteControlClient != null) {
            MetadataEditor editor = mRemoteControlClient.editMetadata(true);
            editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, mCurrentMedia.getAlbum());
            editor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, mCurrentMedia.getArtist());
            editor.putString(MediaMetadataRetriever.METADATA_KEY_GENRE, mCurrentMedia.getGenre());
            editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, mCurrentMedia.getTitle());
            editor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, mCurrentMedia.getLength());
            editor.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, getCover());
            editor.apply();
        }
    }

    private void previous() {
        int index = mMediaList.indexOf(mCurrentMedia);
        if (mPrevious.size() > 0)
            mCurrentMedia = mPrevious.pop();
        else if (index > 0)
            mCurrentMedia = mMediaList.get(index - 1);
        else
            return;
        if(mLibVLCPlaylistActive) {
            if(mRepeating == RepeatType.None)
                mLibVLC.previous();
            else if(mRepeating == RepeatType.Once)
                mLibVLC.playIndex(index);
            else
                mLibVLC.playIndex(mMediaList.indexOf(mCurrentMedia));
        } else {
            mLibVLC.readMedia(mCurrentMedia.getLocation(), true);
        }
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        setUpRemoteControlClient();
        showNotification();
        updateWidget(this);
        updateRemoteControlClientMetadata();
        saveCurrentMedia();
    }

    private void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
        saveCurrentMedia();
    }

    private void setRepeatType(int t) {
        mRepeating = RepeatType.values()[t];
    }

    private Bitmap getCover() {
        return AudioUtil.getCover(this, mCurrentMedia, 512);
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
        public void load(List<String> mediaPathList, int position, boolean libvlcBacked, boolean noVideo)
                throws RemoteException {
            mLibVLCPlaylistActive = libvlcBacked;

            Log.v(TAG, "Loading position " + ((Integer)position).toString() + " in " + mediaPathList.toString());
            mEventHandler.addHandler(mVlcEventHandler);

            mMediaList.clear();
            mPrevious.clear();

            if(mLibVLCPlaylistActive) {
                for(int i = 0; i < mediaPathList.size(); i++)
                    mMediaList.add(new Media(mediaPathList.get(i), i));
            } else {
                MediaDatabase db = MediaDatabase.getInstance(AudioService.this);
                for (int i = 0; i < mediaPathList.size(); i++) {
                    String location = mediaPathList.get(i);
                    Media media = db.getMedia(AudioService.this, location);
                    if(media == null) {
                        if(!validateLocation(location)) {
                            Log.w(TAG, "Invalid location " + location);
                            showToast(getResources().getString(R.string.invalid_location, location), Toast.LENGTH_SHORT);
                            continue;
                        }
                        Log.v(TAG, "Creating on-the-fly Media object for " + location);
                        media = new Media(location, false);
                    }
                    mMediaList.add(media);
                }
            }

            if (mMediaList.size() > position) {
                mCurrentMedia = mMediaList.get(position);
            }

            if (mCurrentMedia != null) {
                if(mLibVLCPlaylistActive) {
                    mLibVLC.playIndex(position);
                } else {
                    mLibVLC.readMedia(mCurrentMedia.getLocation(), noVideo);
                }
                setUpRemoteControlClient();
                showNotification();
                updateWidget(AudioService.this);
                updateRemoteControlClientMetadata();
            }
            AudioService.this.saveMediaList();
            AudioService.this.saveCurrentMedia();
        }

        @Override
        public void showWithoutParse(String URI) throws RemoteException {
            Log.v(TAG, "Showing playing URI " + URI);
            // Show an URI without interrupting/losing the current stream

            if(!mLibVLC.isPlaying())
                return;
            mEventHandler.addHandler(mVlcEventHandler);
            mMediaList.clear();
            mPrevious.clear();
            // Prevent re-parsing the media, which would mean losing the connection
            mCurrentMedia = new Media(
                    getApplicationContext(),
                    URI,
                    0,
                    0,
                    Media.TYPE_AUDIO,
                    null,
                    URI,
                    VLCApplication.getAppContext().getString(R.string.unknown_artist),
                    VLCApplication.getAppContext().getString(R.string.unknown_genre),
                    VLCApplication.getAppContext().getString(R.string.unknown_album),
                    0,
                    0,
                    "",
                    -1,
                    -1);
            mMediaList.add(mCurrentMedia);

            // Notify everyone
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            showNotification();
            executeUpdate();
        }

        @Override
        public void append(List<String> mediaLocationList) throws RemoteException {
            if (mMediaList.size() == 0) {
                load(mediaLocationList, 0, false, false);
                return;
            }

            if(mLibVLCPlaylistActive) {
                return;
            }
            MediaDatabase db = MediaDatabase.getInstance(AudioService.this);
            for (int i = 0; i < mediaLocationList.size(); i++) {
                String location = mediaLocationList.get(i);
                Media media = db.getMedia(AudioService.this, location);
                if(media == null) {
                    if (!validateLocation(location)) {
                        showToast(getResources().getString(R.string.invalid_location, location), Toast.LENGTH_SHORT);
                        continue;
                    }
                    Log.v(TAG, "Creating on-the-fly Media object for " + location);
                    media = new Media(location, false);
                }
                mMediaList.add(media);
            }
            AudioService.this.saveMediaList();
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

        @Override
        public float getRate() throws RemoteException {
            return mLibVLC.getRate();
        }
    };

    private void updateWidget(Context context)
    {
        Log.d(TAG, "Updating widget");
        Intent i = new Intent();
        i.setClassName(WIDGET_PACKAGE, WIDGET_CLASS);
        i.setAction(ACTION_WIDGET_UPDATE);

        if (mCurrentMedia != null) {
            i.putExtra("title", mCurrentMedia.getTitle());
            i.putExtra("artist", mCurrentMedia.getArtist());
        }
        else {
            i.putExtra("title", context.getString(R.string.widget_name));
            i.putExtra("artist", "");
        }
        i.putExtra("isplaying", mLibVLC.isPlaying());

        Bitmap cover = mCurrentMedia != null ? AudioUtil.getCover(this, mCurrentMedia, 64) : null;
        i.putExtra("cover", cover);

        sendBroadcast(i);
    }

    private void updateWidgetPosition(Context context, float pos)
    {
        // no more than one widget update for each 1/50 of the song
        long timestamp = Calendar.getInstance().getTimeInMillis();
        if (mCurrentMedia == null ||
            timestamp - mWidgetPositionTimestamp < mCurrentMedia.getLength() / 50)
            return;

        mWidgetPositionTimestamp = timestamp;
        Intent i = new Intent();
        i.setClassName(WIDGET_PACKAGE, WIDGET_CLASS);
        i.setAction(ACTION_WIDGET_UPDATE_POSITION);
        i.putExtra("position", pos);
        sendBroadcast(i);
    }

    private synchronized void loadLastPlaylist() {
        if (!Util.hasExternalStorage())
            return;

        String line;
        FileInputStream input;
        BufferedReader br;
        int rowCount = 0;

        int position = 0;
        String currentMedia;
        List<String> mediaPathList = new ArrayList<String>();

        try {
            // read CurrentMedia
            input = new FileInputStream(AudioUtil.CACHE_DIR + "/" + "CurrentMedia.txt");
            br = new BufferedReader(new InputStreamReader(input));
            currentMedia = br.readLine();
            mShuffling = "1".equals(br.readLine());
            br.close();
            input.close();

            // read MediaList
            input = new FileInputStream(AudioUtil.CACHE_DIR + "/" + "MediaList.txt");
            br = new BufferedReader(new InputStreamReader(input));
            while ((line = br.readLine()) != null) {
                mediaPathList.add(line);
                if (line.equals(currentMedia))
                    position = rowCount;
                rowCount++;
            }
            br.close();
            input.close();

            // load playlist
            mInterface.load(mediaPathList, position, false, false);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveCurrentMedia() {
        if (!Util.hasExternalStorage())
            return;

        FileOutputStream output;
        BufferedWriter bw;

        try {
            output = new FileOutputStream(AudioUtil.CACHE_DIR + "/" + "CurrentMedia.txt");
            bw = new BufferedWriter(new OutputStreamWriter(output));
            bw.write(mCurrentMedia != null ? mCurrentMedia.getLocation() : "");
            bw.write('\n');
            bw.write(mShuffling ? "1" : "0");
            bw.write('\n');
            bw.close();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveMediaList() {
        if (!Util.hasExternalStorage())
            return;

        FileOutputStream output;
        BufferedWriter bw;

        try {
            output = new FileOutputStream(AudioUtil.CACHE_DIR + "/" + "MediaList.txt");
            bw = new BufferedWriter(new OutputStreamWriter(output));
            for (int i = 0; i < mMediaList.size(); i++) {
                Media item = mMediaList.get(i);
                bw.write(item.getLocation());
                bw.write('\n');
            }
            bw.close();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean validateLocation(String location)
    {
        /* Check if the MRL contains a scheme */
        if (!location.matches("\\w+://.+"))
            location = "file://".concat(location);
        if (location.toLowerCase(Locale.ENGLISH).startsWith("file://")) {
            /* Ensure the file exists */
            File f;
            try {
                f = new File(new URI(location));
            } catch (URISyntaxException e) {
                return false;
            }
            if (!f.isFile())
                return false;
        }
        return true;
    }

    private void showToast(String text, int duration) {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("text", text);
        bundle.putInt("duration", duration);
        msg.setData(bundle);
        msg.what = SHOW_TOAST;
        mHandler.sendMessage(msg);
    }
}
