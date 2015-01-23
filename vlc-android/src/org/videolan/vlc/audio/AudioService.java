/*****************************************************************************
 * AudioService.java
 *****************************************************************************
 * Copyright © 2011-2013 VLC authors and VideoLAN
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

package org.videolan.vlc.audio;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaWrapperList;
import org.videolan.vlc.MediaWrapperListPlayer;
import org.videolan.vlc.R;
import org.videolan.vlc.RemoteControlClientReceiver;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.IAudioService;
import org.videolan.vlc.interfaces.IAudioServiceCallback;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

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
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.net.Uri;
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
    public static final String ACTION_WIDGET_UPDATE_COVER = "org.videolan.vlc.widget.UPDATE_COVER";
    public static final String ACTION_WIDGET_UPDATE_POSITION = "org.videolan.vlc.widget.UPDATE_POSITION";

    public static final String WIDGET_PACKAGE = "org.videolan.vlc";
    public static final String WIDGET_CLASS = "org.videolan.vlc.widget.VLCAppWidgetProvider";

    public static final int CURRENT_ITEM = 1;
    public static final int PREVIOUS_ITEM = 2;
    public static final int NEXT_ITEM = 3;

    private LibVLC mLibVLC;
    private MediaWrapperListPlayer mMediaListPlayer;
    private HashMap<IAudioServiceCallback, Integer> mCallback;
    private EventHandler mEventHandler;
    private OnAudioFocusChangeListener audioFocusListener;
    private boolean mDetectHeadset = true;
    private boolean mPebbleEnabled;
    private PowerManager.WakeLock mWakeLock;
    private final AtomicBoolean mExpanding = new AtomicBoolean(false);

    private static boolean mWasPlayingAudio = false;

    // Index management
    /**
     * Stack of previously played indexes, used in shuffle mode
     */
    private Stack<Integer> mPrevious;
    private int mCurrentIndex; // Set to -1 if no media is currently loaded
    private int mPrevIndex; // Set to -1 if no previous media
    private int mNextIndex; // Set to -1 if no next media

    // Playback management
    private boolean mShuffling = false;
    private RepeatType mRepeating = RepeatType.None;
    private Random mRandom = null; // Used in shuffling process

    // RemoteControlClient-related
    /**
     * RemoteControlClient is for lock screen playback control.
     */
    private RemoteControlClient mRemoteControlClient = null;
    private RemoteControlClientReceiver mRemoteControlClientReceiver = null;
    /**
     * Last widget position update timestamp
     */
    private long mWidgetPositionTimestamp = Calendar.getInstance().getTimeInMillis();
    private ComponentName mRemoteControlClientReceiverComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        // Get libVLC instance
        try {
            mLibVLC = VLCInstance.getLibVlcInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
        }
        mMediaListPlayer = MediaWrapperListPlayer.getInstance(mLibVLC);

        mCallback = new HashMap<IAudioServiceCallback, Integer>();
        mCurrentIndex = -1;
        mPrevIndex = -1;
        mNextIndex = -1;
        mPrevious = new Stack<Integer>();
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
        filter.addAction(VLCApplication.INCOMING_CALL_INTENT);
        filter.addAction(VLCApplication.CALL_ENDED_INTENT);
        registerReceiver(serviceReceiver, filter);

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean stealRemoteControl = pref.getBoolean("enable_steal_remote_control", false);

        if (!LibVlcUtil.isFroyoOrLater() || stealRemoteControl) {
            /* Backward compatibility for API 7 */
            filter = new IntentFilter();
            if (stealRemoteControl)
                filter.setPriority(Integer.MAX_VALUE);
            filter.addAction(Intent.ACTION_MEDIA_BUTTON);
            mRemoteControlClientReceiver = new RemoteControlClientReceiver();
            registerReceiver(mRemoteControlClientReceiver, filter);
        }
        try {
            getPackageManager().getPackageInfo("com.getpebble.android", PackageManager.GET_ACTIVITIES);
            mPebbleEnabled = true;
        } catch (PackageManager.NameNotFoundException e) {
            mPebbleEnabled = false;
        }
    }

    /**
     * Set up the remote control and tell the system we want to be the default receiver for the MEDIA buttons
     * @see http://android-developers.blogspot.fr/2010/06/allowing-applications-to-play-nicer.html
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void setUpRemoteControlClient() {
        Context context = VLCApplication.getAppContext();
        AudioManager audioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);

        if (LibVlcUtil.isICSOrLater()) {
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
        } else if (LibVlcUtil.isFroyoOrLater()) {
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
        if (!LibVlcUtil.isICSOrLater() || mRemoteControlClient == null)
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
        if (intent == null)
            return START_STICKY;
        if(ACTION_REMOTE_PLAYPAUSE.equals(intent.getAction())){
            if (hasCurrentMedia())
                return START_STICKY;
            else
                loadLastPlaylist();
        } else if (ACTION_REMOTE_PLAY.equals(intent.getAction())) {
            if (hasCurrentMedia())
                play();
            else
                loadLastPlaylist();
        }
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
        if (!LibVlcUtil.isFroyoOrLater()) // NOP if not supported
            return;

        if (audioFocusListener == null) {
            audioFocusListener = new OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    LibVLC libVLC = LibVLC.getExistingInstance();
                    switch (focusChange)
                    {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            if (libVLC.isPlaying())
                                libVLC.pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            /*
                             * Lower the volume to 36% to "duck" when an alert or something
                             * needs to be played.
                             */
                            libVLC.setVolume(36);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            libVLC.setVolume(100);
                            break;
                    }
                }
            };
        }

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

            /*
             * Incoming Call : Pause if VLC is playing audio or video. 
             */
            if (action.equalsIgnoreCase(VLCApplication.INCOMING_CALL_INTENT)) {
                mWasPlayingAudio = mLibVLC.isPlaying() && mLibVLC.getVideoTracksCount() < 1;
                if (mLibVLC.isPlaying())
                    pause();
            }

            /*
             * Call ended : Play only if VLC was playing audio.
             */
            if (action.equalsIgnoreCase(VLCApplication.CALL_ENDED_INTENT)
                    && mWasPlayingAudio) {
                play();
            }

            // skip all headsets events if there is a call
            TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !mLibVLC.isPlaying() && !hasCurrentMedia()) {
                Intent iVlc = new Intent(context, MainActivity.class);
                iVlc.putExtra(START_FROM_NOTIFICATION, true);
                iVlc.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(iVlc);
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(ACTION_REMOTE_PLAYPAUSE)) {
                if (mLibVLC.isPlaying() && hasCurrentMedia())
                    pause();
                else if (!mLibVLC.isPlaying() && hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PLAY)) {
                if (!mLibVLC.isPlaying() && hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PAUSE)) {
                if (mLibVLC.isPlaying() && hasCurrentMedia())
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
                    if (mLibVLC.isPlaying() && hasCurrentMedia())
                        pause();
                }
                else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.i(TAG, "Headset Inserted.");
                    if (!mLibVLC.isPlaying() && hasCurrentMedia())
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
                case EventHandler.MediaParsedChanged:
                    Log.i(TAG, "MediaParsedChanged");
                    break;
                case EventHandler.MediaPlayerPlaying:
                    Log.i(TAG, "MediaPlayerPlaying");
                    service.executeUpdate();
                    service.executeUpdateProgress();

                    String location = service.mMediaListPlayer.getMediaList().getMRL(service.mCurrentIndex);
                    long length = service.mLibVLC.getLength();
                    MediaDatabase dbManager = MediaDatabase.getInstance();
                    MediaWrapper m = dbManager.getMedia(location);
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
                    service.showNotification();
                    if (!service.mWakeLock.isHeld())
                        service.mWakeLock.acquire();
                    break;
                case EventHandler.MediaPlayerPaused:
                    Log.i(TAG, "MediaPlayerPaused");
                    service.executeUpdate();
                    service.executeUpdateProgress();
                    service.showNotification();
                    service.setRemoteControlClientPlaybackState(EventHandler.MediaPlayerPaused);
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                case EventHandler.MediaPlayerStopped:
                    Log.i(TAG, "MediaPlayerStopped");
                    service.executeUpdate();
                    service.executeUpdateProgress();
                    service.setRemoteControlClientPlaybackState(EventHandler.MediaPlayerStopped);
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                case EventHandler.MediaPlayerEndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    service.executeUpdate();
                    service.executeUpdateProgress();
                    service.determinePrevAndNextIndices(true);
                    service.next();
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                case EventHandler.MediaPlayerPositionChanged:
                    float pos = msg.getData().getFloat("data");
                    service.updateWidgetPosition(service, pos);
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    service.showToast(service.getString(
                        R.string.invalid_location,
                        service.mMediaListPlayer.getMediaList().getMRL(
                                service.mCurrentIndex)), Toast.LENGTH_SHORT);
                    service.executeUpdate();
                    service.executeUpdateProgress();
                    service.next();
                    if (service.mWakeLock.isHeld())
                        service.mWakeLock.release();
                    break;
                case EventHandler.MediaPlayerTimeChanged:
                    // avoid useless error logs
                    break;
                case EventHandler.MediaMetaChanged:
                    if (!service.hasCurrentMedia())
                        break;
                    service.getCurrentMedia().updateMeta(service.mLibVLC);
                    service.setUpRemoteControlClient();
                    service.executeUpdate();
                    service.showNotification();
                    service.updateRemoteControlClientMetadata();
                    break;
                default:
                    Log.e(TAG, String.format("Event not handled (0x%x)", msg.getData().getInt("event")));
                    break;
            }
        }
    };

    private final MediaWrapperList.EventListener mListEventListener = new MediaWrapperList.EventListener() {

        @Override
        public void onItemAdded(int index, String mrl) {
            Log.i(TAG, "CustomMediaListItemAdded");
            if(mCurrentIndex >= index && !mExpanding.get())
                mCurrentIndex++;

            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public void onItemRemoved(int index, String mrl) {
            Log.i(TAG, "CustomMediaListItemDeleted");
            if (mCurrentIndex == index && !mExpanding.get()) {
                // The current item has been deleted
                mCurrentIndex--;
                determinePrevAndNextIndices();
                if (mNextIndex != -1)
                    next();
                else if (mCurrentIndex != -1) {
                    mMediaListPlayer.playIndex(mCurrentIndex);
                    executeOnMediaPlayedAdded();
                } else
                    stop();
            }

            if(mCurrentIndex > index && !mExpanding.get())
                mCurrentIndex--;
            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public void onItemMoved(int indexBefore, int indexAfter, String mrl) {
            Log.i(TAG, "CustomMediaListItemMoved");
            if (mCurrentIndex == indexBefore) {
                mCurrentIndex = indexAfter;
                if (indexAfter > indexBefore)
                    mCurrentIndex--;
            } else if (indexBefore > mCurrentIndex
                    && indexAfter <= mCurrentIndex)
                mCurrentIndex++;
            else if (indexBefore < mCurrentIndex
                    && indexAfter > mCurrentIndex)
                mCurrentIndex--;

            // If we are in random mode, we completely reset the stored previous track
            // as their indices changed.
            mPrevious.clear();

            determinePrevAndNextIndices();
            executeUpdate();
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

    private void executeUpdateProgress() {
        for (IAudioServiceCallback callback : mCallback.keySet()) {
            try {
                callback.updateProgress();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void executeOnMediaPlayedAdded() {
        final MediaWrapper media = mMediaListPlayer.getMediaList().getMedia(mCurrentIndex);
        for (IAudioServiceCallback callback : mCallback.keySet()) {
            try {
                callback.onMediaPlayedAdded(media, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return the current media.
     *
     * @return The current media or null if there is not any.
     */
    private MediaWrapper getCurrentMedia() {
        return mMediaListPlayer.getMediaList().getMedia(mCurrentIndex);
    }

    /**
     * Alias for mCurrentIndex >= 0
     *
     * @return True if a media is currently loaded, false otherwise
     */
    private boolean hasCurrentMedia() {
        return mCurrentIndex >= 0 && mCurrentIndex < mMediaListPlayer.getMediaList().size();
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
                        service.executeUpdateProgress();
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
            MediaWrapper media = getCurrentMedia();
            if (media == null)
                return;
            Bitmap cover = AudioUtil.getCover(this, media, 64);
            String title = media.getTitle();
            String artist = Util.getMediaArtist(this, media);
            String album = Util.getMediaAlbum(this, media);
            Notification notification;

            if (media.isArtistUnknown() && media.isAlbumUnknown() && media.getNowPlaying() != null) {
                artist = media.getNowPlaying();
                album = "";
            }

            // add notification to status bar
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_vlc)
                .setTicker(title + " - " + artist)
                .setAutoCancel(false)
                .setOngoing(true);

            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction(MainActivity.ACTION_SHOW_PLAYER);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            notificationIntent.putExtra(START_FROM_NOTIFICATION, true);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            if (LibVlcUtil.isJellyBeanOrLater()) {
                Intent iBackward = new Intent(ACTION_REMOTE_BACKWARD);
                Intent iPlay = new Intent(ACTION_REMOTE_PLAYPAUSE);
                Intent iForward = new Intent(ACTION_REMOTE_FORWARD);
                Intent iStop = new Intent(ACTION_REMOTE_STOP);
                PendingIntent piBackward = PendingIntent.getBroadcast(this, 0, iBackward, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piPlay = PendingIntent.getBroadcast(this, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piForward = PendingIntent.getBroadcast(this, 0, iForward, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piStop = PendingIntent.getBroadcast(this, 0, iStop, PendingIntent.FLAG_UPDATE_CURRENT);

                RemoteViews view = new RemoteViews(getPackageName(), R.layout.notification);
                view.setImageViewBitmap(R.id.cover, cover == null ? BitmapFactory.decodeResource(getResources(), R.drawable.icon) : cover);
                view.setTextViewText(R.id.songName, title);
                view.setTextViewText(R.id.artist, artist);
                view.setImageViewResource(R.id.play_pause, mLibVLC.isPlaying() ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
                view.setOnClickPendingIntent(R.id.play_pause, piPlay);
                view.setOnClickPendingIntent(R.id.forward, piForward);
                view.setOnClickPendingIntent(R.id.stop, piStop);
                view.setOnClickPendingIntent(R.id.content, pendingIntent);

                RemoteViews view_expanded = new RemoteViews(getPackageName(), R.layout.notification_expanded);
                view_expanded.setImageViewBitmap(R.id.cover, cover == null ? BitmapFactory.decodeResource(getResources(), R.drawable.icon) : cover);
                view_expanded.setTextViewText(R.id.songName, title);
                view_expanded.setTextViewText(R.id.artist, artist);
                view_expanded.setTextViewText(R.id.album, album);
                view_expanded.setImageViewResource(R.id.play_pause, mLibVLC.isPlaying() ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
                view_expanded.setOnClickPendingIntent(R.id.backward, piBackward);
                view_expanded.setOnClickPendingIntent(R.id.play_pause, piPlay);
                view_expanded.setOnClickPendingIntent(R.id.forward, piForward);
                view_expanded.setOnClickPendingIntent(R.id.stop, piStop);
                view_expanded.setOnClickPendingIntent(R.id.content, pendingIntent);

                if (LibVlcUtil.isLolliPopOrLater())
                    builder.setVisibility(Notification.VISIBILITY_PUBLIC);
                notification = builder.build();
                notification.contentView = view;
                notification.bigContentView = view_expanded;
            }
            else {
                builder.setLargeIcon(cover == null ? BitmapFactory.decodeResource(getResources(), R.drawable.icon) : cover)
                       .setContentTitle(title)
                        .setContentText(LibVlcUtil.isJellyBeanOrLater() ? artist
                                        : Util.getMediaSubtitle(this, media))
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
        hideNotification(true);
    }

    /**
     * Hides the VLC notification and stops the service.
     *
     * @param stopPlayback True to also stop playback at the same time. Set to false to preserve playback (e.g. for vout events)
     */
    private void hideNotification(boolean stopPlayback) {
        stopForeground(true);
        if(stopPlayback)
            stopSelf();
    }

    private void pause() {
        setUpRemoteControlClient();
        mHandler.removeMessages(SHOW_PROGRESS);
        // hideNotification(); <-- see event handler
        mLibVLC.pause();
    }

    private void play() {
        if(hasCurrentMedia()) {
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
        mMediaListPlayer.getMediaList().removeEventListener(mListEventListener);
        setRemoteControlClientPlaybackState(EventHandler.MediaPlayerStopped);
        mCurrentIndex = -1;
        mPrevious.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        executeUpdate();
        executeUpdateProgress();
        changeAudioFocus(false);
    }

    private void determinePrevAndNextIndices() {
        determinePrevAndNextIndices(false);
    }

    private void determinePrevAndNextIndices(boolean expand) {
        if (expand) {
            mExpanding.set(true);
            mNextIndex = mMediaListPlayer.expand();
            mExpanding.set(false);
        } else {
            mNextIndex = -1;
        }
        mPrevIndex = -1;

        if (mNextIndex == -1) {
            // No subitems; play the next item.
            int size = mMediaListPlayer.getMediaList().size();
            mShuffling &= size > 2;

            // Repeating once doesn't change the index
            if (mRepeating == RepeatType.Once) {
                mPrevIndex = mNextIndex = mCurrentIndex;
            } else {

                if(mShuffling) {
                    if(mPrevious.size() > 0)
                        mPrevIndex = mPrevious.peek();
                    // If we've played all songs already in shuffle, then either
                    // reshuffle or stop (depending on RepeatType).
                    if(mPrevious.size() + 1 == size) {
                        if(mRepeating == RepeatType.None) {
                            mNextIndex = -1;
                            return;
                        } else {
                            mPrevious.clear();
                        }
                    }
                    if(mRandom == null) mRandom = new Random();
                    // Find a new index not in mPrevious.
                    do
                    {
                        mNextIndex = mRandom.nextInt(size);
                    }
                    while(mNextIndex == mCurrentIndex || mPrevious.contains(mNextIndex));

                } else {
                    // normal playback
                    if(mCurrentIndex > 0)
                        mPrevIndex = mCurrentIndex - 1;
                    if(mCurrentIndex + 1 < size)
                        mNextIndex = mCurrentIndex + 1;
                    else {
                        if(mRepeating == RepeatType.None) {
                            mNextIndex = -1;
                        } else {
                            mNextIndex = 0;
                        }
                    }
                }
            }
        }
    }

    private void next() {
        mPrevious.push(mCurrentIndex);
        mCurrentIndex = mNextIndex;

        int size = mMediaListPlayer.getMediaList().size();
        if (size == 0 || mCurrentIndex < 0 || mCurrentIndex >= size) {
            Log.w(TAG, "Warning: invalid next index, aborted !");
            stop();
            return;
        }

        mMediaListPlayer.playIndex(mCurrentIndex);
        executeOnMediaPlayedAdded();

        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        setUpRemoteControlClient();
        showNotification();
        updateWidget(this);
        updateRemoteControlClientMetadata();
        saveCurrentMedia();

        determinePrevAndNextIndices();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void updateRemoteControlClientMetadata() {
        if (!LibVlcUtil.isICSOrLater()) // NOP check
            return;

        MediaWrapper media = getCurrentMedia();
        if (mRemoteControlClient != null && media != null) {
            MetadataEditor editor = mRemoteControlClient.editMetadata(true);
            if (media.getNowPlaying() != null) {
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, "");
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, "");
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, media.getNowPlaying());
            } else {
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, "");
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, Util.getMediaAlbum(this, media));
                editor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, Util.getMediaArtist(this, media));
            }
            editor.putString(MediaMetadataRetriever.METADATA_KEY_GENRE, Util.getMediaGenre(this, media));
            editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, media.getTitle());
            editor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, media.getLength());
            // Copy the cover bitmap because the RemonteControlClient can recycle its artwork bitmap.
            Bitmap cover = getCover();
            editor.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, ((cover != null) ? cover.copy(cover.getConfig(), false) : null));
            editor.apply();
        }

        //Send metadata to Pebble watch
        if (mPebbleEnabled) {
            final Intent i = new Intent("com.getpebble.action.NOW_PLAYING");
            i.putExtra("artist", Util.getMediaArtist(this, media));
            i.putExtra("album", Util.getMediaAlbum(this, media));
            i.putExtra("track", media.getTitle());
            sendBroadcast(i);
        }
    }

    private void previous() {
        mCurrentIndex = mPrevIndex;
        if (mPrevious.size() > 0)
            mPrevious.pop();

        int size = mMediaListPlayer.getMediaList().size();
        if (size == 0 || mPrevIndex < 0 || mCurrentIndex >= size) {
            Log.w(TAG, "Warning: invalid previous index, aborted !");
            stop();
            return;
        }

        mMediaListPlayer.playIndex(mCurrentIndex);
        executeOnMediaPlayedAdded();
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        setUpRemoteControlClient();
        showNotification();
        updateWidget(this);
        updateRemoteControlClientMetadata();
        saveCurrentMedia();

        determinePrevAndNextIndices();
    }

    private void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    private void setRepeatType(int t) {
        mRepeating = RepeatType.values()[t];
        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    private Bitmap getCover() {
        MediaWrapper media = getCurrentMedia();
        return media != null ? AudioUtil.getCover(this, media, 512) : null;
    }

    private final IAudioService.Stub mInterface = new IAudioService.Stub() {

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
            return hasCurrentMedia();
        }

        @Override
        public String getAlbum() throws RemoteException {
            if (hasCurrentMedia())
                return Util.getMediaAlbum(AudioService.this, getCurrentMedia());
            else
                return null;
        }

        @Override
        public String getArtist() throws RemoteException {
            if (hasCurrentMedia()) {
                final MediaWrapper media = getCurrentMedia();
                return media.isArtistUnknown() && media.getNowPlaying() != null ?
                        media.getNowPlaying()
                        : Util.getMediaArtist(AudioService.this, media);
            } else
                return null;
        }

        @Override
        public String getArtistPrev() throws RemoteException {
            if (mPrevIndex != -1)
                return Util.getMediaArtist(AudioService.this, mMediaListPlayer.getMediaList().getMedia(mPrevIndex));
            else
                return null;
        }

        @Override
        public String getArtistNext() throws RemoteException {
            if (mNextIndex != -1)
                return Util.getMediaArtist(AudioService.this, mMediaListPlayer.getMediaList().getMedia(mNextIndex));
            else
                return null;
        }

        @Override
        public String getTitle() throws RemoteException {
            if (hasCurrentMedia())
                return getCurrentMedia().getTitle();
            else
                return null;
        }

        @Override
        public String getTitlePrev() throws RemoteException {
            if (mPrevIndex != -1)
                return mMediaListPlayer.getMediaList().getMedia(mPrevIndex).getTitle();
            else
                return null;
        }

        @Override
        public String getTitleNext() throws RemoteException {
            if (mNextIndex != -1)
                return mMediaListPlayer.getMediaList().getMedia(mNextIndex).getTitle();
            else
                return null;
        }

        @Override
        public Bitmap getCover() {
            if (hasCurrentMedia()) {
                return AudioService.this.getCover();
            }
            return null;
        }

        @Override
        public Bitmap getCoverPrev() throws RemoteException {
            if (mPrevIndex != -1)
                return AudioUtil.getCover(AudioService.this, mMediaListPlayer.getMediaList().getMedia(mPrevIndex), 64);
            else
                return null;
        }

        @Override
        public Bitmap getCoverNext() throws RemoteException {
            if (mNextIndex != -1)
                return AudioUtil.getCover(AudioService.this, mMediaListPlayer.getMediaList().getMedia(mNextIndex), 64);
            else
                return null;
        }

        @Override
        public synchronized void addAudioCallback(IAudioServiceCallback cb)
                throws RemoteException {
            Integer count = mCallback.get(cb);
            if (count == null)
                count = 0;
            mCallback.put(cb, count + 1);
            if (hasCurrentMedia())
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

        /**
         * Loads a selection of files (a non-user-supplied collection of media)
         * into the primary or "currently playing" playlist.
         *
         * @param mediaPathList A list of locations to load
         * @param position The position to start playing at
         * @param noVideo True to disable video, false otherwise
         * @throws RemoteException
         */
        @Override
        public void load(List<String> mediaPathList, int position, boolean noVideo)
                throws RemoteException {

            Log.v(TAG, "Loading position " + ((Integer)position).toString() + " in " + mediaPathList.toString());
            mEventHandler.addHandler(mVlcEventHandler);

            mMediaListPlayer.getMediaList().removeEventListener(mListEventListener);
            mMediaListPlayer.getMediaList().clear();
            MediaWrapperList mediaList = mMediaListPlayer.getMediaList();

            mPrevious.clear();

            MediaDatabase db = MediaDatabase.getInstance();
            for (int i = 0; i < mediaPathList.size(); i++) {
                String location = mediaPathList.get(i);
                MediaWrapper mediaWrapper = db.getMedia(location);
                if(mediaWrapper == null) {
                    if(!validateLocation(location)) {
                        Log.w(TAG, "Invalid location " + location);
                        showToast(getResources().getString(R.string.invalid_location, location), Toast.LENGTH_SHORT);
                        continue;
                    }
                    Log.v(TAG, "Creating on-the-fly Media object for " + location);
                    final Media media = new Media(mLibVLC, location);
                    media.parse(); // FIXME: parse should be done asynchronously
                    media.release();
                    mediaWrapper = new MediaWrapper(media);
                }
                if (noVideo)
                    mediaWrapper.addFlags(LibVLC.MEDIA_NO_VIDEO);
                mediaList.add(mediaWrapper);
            }

            if (mMediaListPlayer.getMediaList().size() == 0) {
                Log.w(TAG, "Warning: empty media list, nothing to play !");
                return;
            }
            if (mMediaListPlayer.getMediaList().size() > position && position >= 0) {
                mCurrentIndex = position;
            } else {
                Log.w(TAG, "Warning: positon " + position + " out of bounds");
                mCurrentIndex = 0;
            }

            // Add handler after loading the list
            mMediaListPlayer.getMediaList().addEventListener(mListEventListener);

            mMediaListPlayer.playIndex(mCurrentIndex);
            executeOnMediaPlayedAdded();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            setUpRemoteControlClient();
            showNotification();
            updateWidget(AudioService.this);
            updateRemoteControlClientMetadata();
            AudioService.this.saveMediaList();
            AudioService.this.saveCurrentMedia();
            determinePrevAndNextIndices();
        }

        /**
         * Use this function to play a media inside whatever MediaList LibVLC is following.
         *
         * Unlike load(), it does not import anything into the primary list.
         */
        @Override
        public void playIndex(int index) {
            if (mMediaListPlayer.getMediaList().size() == 0) {
                Log.w(TAG, "Warning: empty media list, nothing to play !");
                return;
            }
            if (index >= 0 && index < mMediaListPlayer.getMediaList().size()) {
                mCurrentIndex = index;
            } else {
                Log.w(TAG, "Warning: index " + index + " out of bounds");
                mCurrentIndex = 0;
            }

            mEventHandler.addHandler(mVlcEventHandler);
            mMediaListPlayer.playIndex(mCurrentIndex);
            executeOnMediaPlayedAdded();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            setUpRemoteControlClient();
            showNotification();
            updateWidget(AudioService.this);
            updateRemoteControlClientMetadata();
            determinePrevAndNextIndices();
        }

        /**
         * Use this function to show an URI in the audio interface WITHOUT
         * interrupting the stream.
         *
         * Mainly used by VideoPlayerActivity in response to loss of video track.
         */
        @Override
        public void showWithoutParse(int index) throws RemoteException {
            String URI = mMediaListPlayer.getMediaList().getMRL(index);
            Log.v(TAG, "Showing index " + index + " with playing URI " + URI);
            // Show an URI without interrupting/losing the current stream

            if(URI == null || !mLibVLC.isPlaying())
                return;
            mEventHandler.addHandler(mVlcEventHandler);
            mCurrentIndex = index;

            // Notify everyone
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            showNotification();
            determinePrevAndNextIndices();
            executeUpdate();
            executeUpdateProgress();
        }

        /**
         * Append to the current existing playlist
         */
        @Override
        public void append(List<String> mediaLocationList) throws RemoteException {
            if (!hasCurrentMedia())
            {
                load(mediaLocationList, 0, false);
                return;
            }

            MediaDatabase db = MediaDatabase.getInstance();
            for (int i = 0; i < mediaLocationList.size(); i++) {
                String location = mediaLocationList.get(i);
                MediaWrapper mediaWrapper = db.getMedia(location);
                if(mediaWrapper == null) {
                    if (!validateLocation(location)) {
                        showToast(getResources().getString(R.string.invalid_location, location), Toast.LENGTH_SHORT);
                        continue;
                    }
                    Log.v(TAG, "Creating on-the-fly Media object for " + location);
                    final Media media = new Media(mLibVLC, location);
                    media.parse(); // FIXME: parse should'nt be done asynchronously
                    media.release();
                    mediaWrapper = new MediaWrapper(media);
                }
                mMediaListPlayer.getMediaList().add(mediaWrapper);
            }
            AudioService.this.saveMediaList();
            determinePrevAndNextIndices();
            executeUpdate();
        }

        /**
         * Move an item inside the playlist.
         */
        @Override
        public void moveItem(int positionStart, int positionEnd) throws RemoteException {
            mMediaListPlayer.getMediaList().move(positionStart, positionEnd);
            AudioService.this.saveMediaList();
        }

        @Override
        public void remove(int position) {
            mMediaListPlayer.getMediaList().remove(position);
            AudioService.this.saveMediaList();
            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public void removeLocation(String location) {
            mMediaListPlayer.getMediaList().remove(location);
            AudioService.this.saveMediaList();
            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public List<MediaWrapper> getMedias() {
            final ArrayList<MediaWrapper> ml = new ArrayList<MediaWrapper>();
            for (int i = 0; i < mMediaListPlayer.getMediaList().size(); i++) {
                ml.add(mMediaListPlayer.getMediaList().getMedia(i));
            }
            return ml;
        }

        @Override
        public List<String> getMediaLocations() {
            ArrayList<String> medias = new ArrayList<String>();
            for (int i = 0; i < mMediaListPlayer.getMediaList().size(); i++) {
                medias.add(mMediaListPlayer.getMediaList().getMRL(i));
            }
            return medias;
        }

        @Override
        public String getCurrentMediaLocation() throws RemoteException {
            return mMediaListPlayer.getMediaList().getMRL(mCurrentIndex);
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
            if (mNextIndex != -1)
                return true;
            else
                return false;
        }

        @Override
        public boolean hasPrevious() throws RemoteException {
            if (mPrevIndex != -1)
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

    private void updateWidget(Context context) {
        Log.d(TAG, "Updating widget");
        updateWidgetState(context);
        updateWidgetCover(context);
    }

    private void updateWidgetState(Context context) {
        Intent i = new Intent();
        i.setClassName(WIDGET_PACKAGE, WIDGET_CLASS);
        i.setAction(ACTION_WIDGET_UPDATE);

        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            i.putExtra("title", media.getTitle());
            i.putExtra("artist", media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : Util.getMediaArtist(this, media));
        }
        else {
            i.putExtra("title", context.getString(R.string.widget_name));
            i.putExtra("artist", "");
        }
        i.putExtra("isplaying", mLibVLC.isPlaying());

        sendBroadcast(i);
    }

    private void updateWidgetCover(Context context)
    {
        Intent i = new Intent();
        i.setClassName(WIDGET_PACKAGE, WIDGET_CLASS);
        i.setAction(ACTION_WIDGET_UPDATE_COVER);

        Bitmap cover = hasCurrentMedia() ? AudioUtil.getCover(this, getCurrentMedia(), 64) : null;
        i.putExtra("cover", cover);

        sendBroadcast(i);
    }

    private void updateWidgetPosition(Context context, float pos)
    {
        // no more than one widget update for each 1/50 of the song
        long timestamp = Calendar.getInstance().getTimeInMillis();
        if (!hasCurrentMedia()
                || timestamp - mWidgetPositionTimestamp < getCurrentMedia()
                        .getLength() / 50)
            return;

        updateWidgetState(context);

        mWidgetPositionTimestamp = timestamp;
        Intent i = new Intent();
        i.setClassName(WIDGET_PACKAGE, WIDGET_CLASS);
        i.setAction(ACTION_WIDGET_UPDATE_POSITION);
        i.putExtra("position", pos);
        sendBroadcast(i);
    }

    private synchronized void loadLastPlaylist() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentMedia = prefs.getString("current_media", "");
        String[] locations = prefs.getString("media_list", "").split(" ");

        List<String> mediaPathList = new ArrayList<String>(locations.length);
        for (int i = 0 ; i < locations.length ; ++i)
            mediaPathList.add(Uri.decode(locations[i]));

        mShuffling = prefs.getBoolean("shuffling", false);
        mRepeating = RepeatType.values()[prefs.getInt("repeating", RepeatType.None.ordinal())];
        int position = Math.max(0, mediaPathList.indexOf(currentMedia));
        // load playlist
        try {
            mInterface.load(mediaPathList, position, false);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveCurrentMedia() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("current_media", mMediaListPlayer.getMediaList().getMRL(mCurrentIndex));
        editor.putBoolean("shuffling", mShuffling);
        editor.putInt("repeating", mRepeating.ordinal());
        Util.commitPreferences(editor);
    }

    private synchronized void saveMediaList() {
        String locations = "";
        for (int i = 0; i < mMediaListPlayer.getMediaList().size(); i++)
            locations += " "+ Uri.encode(mMediaListPlayer.getMediaList().getMRL(i));
        //We save a concatenated String because putStringSet is APIv11.
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("media_list", locations.trim());
        Util.commitPreferences(editor);
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
