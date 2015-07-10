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

package org.videolan.vlc;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.VLCAppWidgetProvider;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlaybackService extends Service implements IVLCVout.Callback {

    private static final String TAG = "VLC/PlaybackService";

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
    public static final String ACTION_REMOTE_RESUME_VIDEO = "org.videolan.vlc.remote.ResumeVideo";
    public static final String ACTION_WIDGET_INIT = "org.videolan.vlc.widget.INIT";
    public static final String ACTION_WIDGET_UPDATE = "org.videolan.vlc.widget.UPDATE";
    public static final String ACTION_WIDGET_UPDATE_COVER = "org.videolan.vlc.widget.UPDATE_COVER";
    public static final String ACTION_WIDGET_UPDATE_POSITION = "org.videolan.vlc.widget.UPDATE_POSITION";

    public interface Callback {
        void update();
        void updateProgress();
        void onMediaEvent(Media.Event event);
        void onMediaPlayerEvent(MediaPlayer.Event event);
    }

    private class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }
    public static PlaybackService getService(IBinder iBinder) {
        LocalBinder binder = (LocalBinder) iBinder;
        return binder.getService();
    }

    private final IBinder mBinder = new LocalBinder();
    private MediaWrapperList mMediaList = new MediaWrapperList();
    private MediaPlayer mMediaPlayer;
    private boolean mIsAudioTrack = false;
    private boolean mHasHdmiAudio = false;

    final private ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private boolean mDetectHeadset = true;
    private boolean mPebbleEnabled;
    private PowerManager.WakeLock mWakeLock;
    private final AtomicBoolean mExpanding = new AtomicBoolean(false);

    private static boolean mWasPlayingAudio = false; // used only if readPhoneState returns true

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

    private boolean mHasAudioFocus = false;
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

    private static LibVLC LibVLC() {
        return VLCInstance.get();
    }

    private MediaPlayer newMediaPlayer() {
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        final MediaPlayer mp = new MediaPlayer(LibVLC());
        final String aout = VLCOptions.getAout(pref);
        if (mp.setAudioOutput(aout) && aout.equals("android_audiotrack")) {
            mIsAudioTrack = true;
            if (mHasHdmiAudio)
                mp.setAudioOutputDevice("hdmi");
        } else
            mIsAudioTrack = false;
        return mp;
    }

    public static enum RepeatType {
        None,
        Once,
        All
    }

    private static boolean readPhoneState() {
        return !AndroidUtil.isFroyoOrLater();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mMediaPlayer = newMediaPlayer();
        mMediaPlayer.getVLCVout().addCallback(this);

        if (!VLCInstance.testCompatibleCPU(this)) {
            stopSelf();
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mDetectHeadset = prefs.getBoolean("enable_headset_detection", true);

        mCurrentIndex = -1;
        mPrevIndex = -1;
        mNextIndex = -1;
        mPrevious = new Stack<Integer>();
        mRemoteControlClientReceiverComponent = new ComponentName(BuildConfig.APPLICATION_ID,
                RemoteControlClientReceiver.class.getName());

        // Make sure the audio player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stop.
        PowerManager pm = (PowerManager) VLCApplication.getAppContext().getSystemService(Context.POWER_SERVICE);
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
        filter.addAction(ACTION_REMOTE_RESUME_VIDEO);
        filter.addAction(ACTION_WIDGET_INIT);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        if (readPhoneState()) {
            filter.addAction(VLCApplication.INCOMING_CALL_INTENT);
            filter.addAction(VLCApplication.CALL_ENDED_INTENT);
        }
        registerReceiver(mReceiver, filter);
        registerV21();

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean stealRemoteControl = pref.getBoolean("enable_steal_remote_control", false);

        if (!AndroidUtil.isFroyoOrLater() || stealRemoteControl) {
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
     * A function to control the Remote Control Client. It is needed for
     * compatibility with devices below Ice Cream Sandwich (4.0).
     *
     * @param state Playback state
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void setRemoteControlClientPlaybackState(int state) {
        if (!AndroidUtil.isICSOrLater() || mRemoteControlClient == null)
            return;

        switch (state) {
            case MediaPlayer.Event.Playing:
                mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
                break;
            case MediaPlayer.Event.Paused:
                mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
                break;
            case MediaPlayer.Event.Stopped:
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
        updateWidget();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        if (mWakeLock.isHeld())
            mWakeLock.release();
        unregisterReceiver(mReceiver);
        if (mReceiverV21 != null)
            unregisterReceiver(mReceiverV21);
        if (mRemoteControlClientReceiver != null) {
            unregisterReceiver(mRemoteControlClientReceiver);
            mRemoteControlClientReceiver = null;
        }
        mMediaPlayer.release();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public IVLCVout getVLCVout()  {
        return mMediaPlayer.getVLCVout();
    }

    private final OnAudioFocusChangeListener mAudioFocusListener = AndroidUtil.isFroyoOrLater() ?
            createOnAudioFocusChangeListener() : null;

    @TargetApi(Build.VERSION_CODES.FROYO)
    private OnAudioFocusChangeListener createOnAudioFocusChangeListener() {
        return new OnAudioFocusChangeListener() {
            private boolean mLossTransient = false;
            private boolean mLossTransientCanDuck = false;

            @Override
            public void onAudioFocusChange(int focusChange) {
                /*
                 * Pause playback during alerts and notifications
                 */
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.i(TAG, "AUDIOFOCUS_LOSS");
                        // Stop playback
                        changeAudioFocus(false);
                        stop();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        // Pause playback
                        if (mMediaPlayer.isPlaying()) {
                            mLossTransient = true;
                            mMediaPlayer.pause();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        // Lower the volume
                        if (mMediaPlayer.isPlaying()) {
                            mMediaPlayer.setVolume(36);
                            mLossTransientCanDuck = true;
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.i(TAG, "AUDIOFOCUS_GAIN: " + mLossTransientCanDuck + ", " + mLossTransient);
                        // Resume playback
                        if (mLossTransientCanDuck) {
                            mMediaPlayer.setVolume(100);
                            mLossTransientCanDuck = false;
                        }
                        if (mLossTransient) {
                            mMediaPlayer.play();
                            mLossTransient = false;
                        }
                        break;
                }
            }
        };
    }

    /**
     * Set up the remote control and tell the system we want to be the default receiver for the MEDIA buttons
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void changeRemoteControlClient(AudioManager am, boolean acquire) {
        if (acquire) {
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(mRemoteControlClientReceiverComponent);
            PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);

            // create and register the remote control client
            mRemoteControlClient = new RemoteControlClient(mediaPendingIntent);
            am.registerRemoteControlClient(mRemoteControlClient);

            mRemoteControlClient.setTransportControlFlags(
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
                            RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
                            RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS |
                            RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
                            RemoteControlClient.FLAG_KEY_MEDIA_STOP);
        } else {
            am.unregisterRemoteControlClient(mRemoteControlClient);
            mRemoteControlClient = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private void changeAudioFocusFroyoOrLater(boolean acquire) {
        final AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (am == null)
            return;

        if (acquire) {
            if (!mHasAudioFocus) {
                final int result = am.requestAudioFocus(mAudioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    am.setParameters("bgm_state=true");
                    am.registerMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
                    if (AndroidUtil.isICSOrLater())
                        changeRemoteControlClient(am, acquire);
                    mHasAudioFocus = true;
                }
            }
        } else {
            if (mHasAudioFocus) {
                final int result = am.abandonAudioFocus(mAudioFocusListener);
                am.setParameters("bgm_state=false");
                am.unregisterMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
                if (AndroidUtil.isICSOrLater())
                    changeRemoteControlClient(am, acquire);
                mHasAudioFocus = false;
            }
        }
    }

    private void changeAudioFocus(boolean acquire) {
        if (AndroidUtil.isFroyoOrLater())
            changeAudioFocusFroyoOrLater(acquire);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void registerV21() {
        final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        registerReceiver(mReceiverV21, intentFilter);
    }

    private final BroadcastReceiver mReceiverV21 = AndroidUtil.isLolliPopOrLater() ? new BroadcastReceiver()
    {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;
            if (action.equalsIgnoreCase(AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
                mHasHdmiAudio = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1;
                if (mMediaPlayer != null && mIsAudioTrack)
                    mMediaPlayer.setAudioOutputDevice(mHasHdmiAudio ? "hdmi" : "stereo");
            }
        }
    } : null;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state = intent.getIntExtra("state", 0);
            if( mMediaPlayer == null ) {
                Log.w(TAG, "Intent received, but VLC is not loaded, skipping.");
                return;
            }

            if (readPhoneState()) {
                /*
                 * Incoming Call : Pause if VLC is playing audio or video.
                 */
                if (action.equalsIgnoreCase(VLCApplication.INCOMING_CALL_INTENT)) {
                    mWasPlayingAudio = mMediaPlayer.isPlaying() && hasCurrentMedia();
                    if (mWasPlayingAudio)
                        pause();
                }

                /*
                 * Call ended : Play only if VLC was playing audio.
                 */
                if (action.equalsIgnoreCase(VLCApplication.CALL_ENDED_INTENT)
                        && mWasPlayingAudio) {
                    play();
                }
            }

            // skip all headsets events if there is a call
            TelephonyManager telManager = (TelephonyManager) VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !mMediaPlayer.isPlaying() && !hasCurrentMedia()) {
                Intent iVlc = new Intent(context, MainActivity.class);
                iVlc.putExtra(START_FROM_NOTIFICATION, true);
                iVlc.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(iVlc);
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(ACTION_REMOTE_PLAYPAUSE)) {
                if (mMediaPlayer.isPlaying() && hasCurrentMedia())
                    pause();
                else if (!mMediaPlayer.isPlaying() && hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PLAY)) {
                if (!mMediaPlayer.isPlaying() && hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PAUSE)) {
                if (mMediaPlayer.isPlaying() && hasCurrentMedia())
                    pause();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_BACKWARD)) {
                previous();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_STOP)) {
                stop();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_FORWARD)) {
                next();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_PLAYLIST)) {
                loadLastPlaylist();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_RESUME_VIDEO)) {
                switchToVideo();
            } else if (action.equalsIgnoreCase(ACTION_WIDGET_INIT)) {
                updateWidget();
            }

            /*
             * headset plug events
             */
            if (mDetectHeadset && !mHasHdmiAudio) {
                if (action.equalsIgnoreCase(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    Log.i(TAG, "Headset Removed.");
                    if (mMediaPlayer.isPlaying() && hasCurrentMedia())
                        pause();
                }
                else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.i(TAG, "Headset Inserted.");
                    if (!mMediaPlayer.isPlaying() && hasCurrentMedia())
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


    @Override
    public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
        handleVout();
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
        handleVout();
    }

    private final Media.EventListener mMediaListener = new Media.EventListener() {
        @Override
        public void onEvent(Media.Event event) {
            switch (event.type) {
                case Media.Event.ParsedChanged:
                    Log.i(TAG, "Media.Event.ParsedChanged");
                    final MediaWrapper mw = getCurrentMedia();
                    if (mw != null)
                        mw.updateMeta(mMediaPlayer);
                    executeUpdate();
                    showNotification();
                    updateRemoteControlClientMetadata();
                    break;
                case Media.Event.MetaChanged:
                    break;
            }
            for (Callback callback : mCallbacks)
                callback.onMediaEvent(event);
        }
    };

    private final MediaPlayer.EventListener mMediaPlayerListener = new MediaPlayer.EventListener() {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    Log.i(TAG, "MediaPlayer.Event.Playing");
                    executeUpdate();
                    executeUpdateProgress();

                    final MediaWrapper mw = mMediaList.getMedia(mCurrentIndex);
                    if (mw != null) {
                        long length = mMediaPlayer.getLength();
                        MediaDatabase dbManager = MediaDatabase.getInstance();
                        MediaWrapper m = dbManager.getMedia(mw.getUri());
                        /**
                         * 1) There is a media to update
                         * 2) It has a length of 0
                         * (dynamic track loading - most notably the OGG container)
                         * 3) We were able to get a length even after parsing
                         * (don't want to replace a 0 with a 0)
                         */
                        if (m != null && m.getLength() == 0 && length > 0) {
                            dbManager.updateMedia(mw.getUri(),
                                    MediaDatabase.mediaColumn.MEDIA_LENGTH, length);
                        }
                    }

                    changeAudioFocus(true);
                    setRemoteControlClientPlaybackState(event.type);
                    showNotification();
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    break;
                case MediaPlayer.Event.Paused:
                    Log.i(TAG, "MediaPlayer.Event.Paused");
                    executeUpdate();
                    executeUpdateProgress();
                    showNotification();
                    setRemoteControlClientPlaybackState(event.type);
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.Stopped:
                    Log.i(TAG, "MediaPlayer.Event.Stopped");
                    executeUpdate();
                    executeUpdateProgress();
                    setRemoteControlClientPlaybackState(event.type);
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    executeUpdate();
                    executeUpdateProgress();
                    determinePrevAndNextIndices(true);
                    next();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EncounteredError:
                    showToast(getString(
                            R.string.invalid_location,
                            mMediaList.getMRL(mCurrentIndex)), Toast.LENGTH_SHORT);
                    executeUpdate();
                    executeUpdateProgress();
                    next();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.TimeChanged:
                    break;
                case MediaPlayer.Event.PositionChanged:
                    updateWidgetPosition(event.getPositionChanged());
                    break;
                case MediaPlayer.Event.Vout:
                    break;
                case MediaPlayer.Event.ESAdded:
                    if (event.getEsChangedType() == Media.Track.Type.Video) {
                        if (!handleVout()) {
                            /* Update notification content intent: resume video or resume audio activity */
                            showNotification();
                        }
                    }
                    break;
                case MediaPlayer.Event.ESDeleted:
                    break;
            }
            for (Callback callback : mCallbacks)
                callback.onMediaPlayerEvent(event);
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
                    playIndex(mCurrentIndex, 0);
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

    private void setVideoTrackEnabled(boolean enabled) {
        if (!enabled) {
            mMediaPlayer.setVideoTrack(-1);
        } else {
            final MediaPlayer.TrackDescription tracks[] = mMediaPlayer.getVideoTracks();

            if (tracks != null) {
                for (MediaPlayer.TrackDescription track : tracks) {
                    if (track.id != -1) {
                        mMediaPlayer.setVideoTrack(track.id);
                        break;
                    }
                }
            }
        }
    }


    private boolean canSwitchToVideo() {
        return hasCurrentMedia() && mMediaPlayer.getVideoTracksCount() > 0;
    }

    private boolean handleVout() {
        if (!canSwitchToVideo() || !mMediaPlayer.isPlaying())
            return false;
        if (mMediaPlayer.getVLCVout().areViewsAttached()) {
            setVideoTrackEnabled(true);
            hideNotification(false);
            return true;
        } else {
            setVideoTrackEnabled(false);
            return false;
        }
    }

    @MainThread
    public boolean switchToVideo() {
        if (!canSwitchToVideo())
            return false;
        if (!mMediaPlayer.getVLCVout().areViewsAttached())
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(), mCurrentIndex);
        return true;
    }

    private void executeUpdate() {
        executeUpdate(true);
    }

    private void executeUpdate(Boolean updateWidget) {
        for (Callback callback : mCallbacks) {
            callback.update();
        }
        if (updateWidget)
            updateWidget();
    }

    private void executeUpdateProgress() {
        for (Callback callback : mCallbacks) {
            callback.updateProgress();
        }
    }

    /**
     * Return the current media.
     *
     * @return The current media or null if there is not any.
     */
    @Nullable
    private MediaWrapper getCurrentMedia() {
        return mMediaList.getMedia(mCurrentIndex);
    }

    /**
     * Alias for mCurrentIndex >= 0
     *
     * @return True if a media is currently loaded, false otherwise
     */
    private boolean hasCurrentMedia() {
        return mCurrentIndex >= 0 && mCurrentIndex < mMediaList.size();
    }

    private final Handler mHandler = new AudioServiceHandler(this);

    private static class AudioServiceHandler extends WeakHandler<PlaybackService> {
        public AudioServiceHandler(PlaybackService fragment) {
            super(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            PlaybackService service = getOwner();
            if(service == null) return;

            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (service.mCallbacks.size() > 0) {
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
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void showNotification() {
        if (mMediaPlayer.getVLCVout().areViewsAttached())
            return;
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

            //Watch notification dismissed
            PendingIntent piStop = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_REMOTE_STOP), PendingIntent.FLAG_UPDATE_CURRENT);

            // add notification to status bar
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_vlc)
                .setTicker(title + " - " + artist)
                .setAutoCancel(!mMediaPlayer.isPlaying())
                .setOngoing(mMediaPlayer.isPlaying())
                .setDeleteIntent(piStop);


            PendingIntent pendingIntent;
            if (canSwitchToVideo()) {
                /* Resume VideoPlayerActivity from from ACTION_REMOTE_RESUME_VIDEO intent */
                final Intent notificationIntent = new Intent(ACTION_REMOTE_RESUME_VIDEO);
                pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                /* Resume AudioPlayerActivity */
                final Intent notificationIntent = new Intent(PlaybackService.this, MainActivity.class);
                notificationIntent.setAction(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER);
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                notificationIntent.putExtra(START_FROM_NOTIFICATION, true);
                pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            if (AndroidUtil.isJellyBeanOrLater()) {
                Intent iBackward = new Intent(ACTION_REMOTE_BACKWARD);
                Intent iPlay = new Intent(ACTION_REMOTE_PLAYPAUSE);
                Intent iForward = new Intent(ACTION_REMOTE_FORWARD);
                PendingIntent piBackward = PendingIntent.getBroadcast(this, 0, iBackward, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piPlay = PendingIntent.getBroadcast(this, 0, iPlay, PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent piForward = PendingIntent.getBroadcast(this, 0, iForward, PendingIntent.FLAG_UPDATE_CURRENT);

                RemoteViews view = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.notification);
                view.setImageViewBitmap(R.id.cover, cover == null ? BitmapFactory.decodeResource(getResources(), R.drawable.icon) : cover);
                view.setTextViewText(R.id.songName, title);
                view.setTextViewText(R.id.artist, artist);
                view.setImageViewResource(R.id.play_pause, mMediaPlayer.isPlaying() ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
                view.setOnClickPendingIntent(R.id.play_pause, piPlay);
                view.setOnClickPendingIntent(R.id.forward, piForward);
                view.setViewVisibility(R.id.forward, hasNext() ? View.VISIBLE : View.INVISIBLE);
                view.setOnClickPendingIntent(R.id.stop, piStop);
                view.setOnClickPendingIntent(R.id.content, pendingIntent);

                RemoteViews view_expanded = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.notification_expanded);
                view_expanded.setImageViewBitmap(R.id.cover, cover == null ? BitmapFactory.decodeResource(getResources(), R.drawable.icon) : cover);
                view_expanded.setTextViewText(R.id.songName, title);
                view_expanded.setTextViewText(R.id.artist, artist);
                view_expanded.setTextViewText(R.id.album, album);
                view_expanded.setImageViewResource(R.id.play_pause, mMediaPlayer.isPlaying() ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
                view_expanded.setOnClickPendingIntent(R.id.backward, piBackward);
                view_expanded.setViewVisibility(R.id.backward, hasPrevious() ? View.VISIBLE : View.INVISIBLE);
                view_expanded.setOnClickPendingIntent(R.id.play_pause, piPlay);
                view_expanded.setOnClickPendingIntent(R.id.forward, piForward);
                view_expanded.setViewVisibility(R.id.forward, hasNext() ? View.VISIBLE : View.INVISIBLE);
                view_expanded.setOnClickPendingIntent(R.id.stop, piStop);
                view_expanded.setOnClickPendingIntent(R.id.content, pendingIntent);

                if (AndroidUtil.isLolliPopOrLater()){
                    //Hide stop button on pause, we swipe notification to stop
                    view.setViewVisibility(R.id.stop, mMediaPlayer.isPlaying() ? View.VISIBLE : View.INVISIBLE);
                    view_expanded.setViewVisibility(R.id.stop, mMediaPlayer.isPlaying() ? View.VISIBLE : View.INVISIBLE);
                    //Make notification appear on lockscreen
                    builder.setVisibility(Notification.VISIBILITY_PUBLIC);
                }

                notification = builder.build();
                notification.contentView = view;
                notification.bigContentView = view_expanded;
            }
            else {
                builder.setLargeIcon(cover == null ? BitmapFactory.decodeResource(getResources(), R.drawable.icon) : cover)
                       .setContentTitle(title)
                        .setContentText(AndroidUtil.isJellyBeanOrLater() ? artist
                                        : Util.getMediaSubtitle(this, media))
                       .setContentInfo(album)
                       .setContentIntent(pendingIntent);
                notification = builder.build();
            }

            startService(new Intent(this, PlaybackService.class));
            if (!AndroidUtil.isLolliPopOrLater() || mMediaPlayer.isPlaying())
                startForeground(3, notification);
            else {
                stopForeground(false);
                NotificationManagerCompat.from(this).notify(3, notification);
            }
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

    @MainThread
    public void pause() {
        mHandler.removeMessages(SHOW_PROGRESS);
        // hideNotification(); <-- see event handler
        mMediaPlayer.pause();
        broadcastMetadata();
    }

    @MainThread
    public void play() {
        if(hasCurrentMedia()) {
            mMediaPlayer.play();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            showNotification();
            updateWidget();
            broadcastMetadata();
        }
    }

    @MainThread
    public void stop() {
        if (mMediaPlayer == null)
            return;
        savePosition();
        final Media media = mMediaPlayer.getMedia();
        if (media != null) {
            media.setEventListener(null);
            mMediaPlayer.setEventListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.setMedia(null);
            media.release();
        }
        mMediaList.removeEventListener(mListEventListener);
        setRemoteControlClientPlaybackState(MediaPlayer.Event.Stopped);
        mCurrentIndex = -1;
        mPrevious.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        broadcastMetadata();
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
            mNextIndex = expand();
            mExpanding.set(false);
        } else {
            mNextIndex = -1;
        }
        mPrevIndex = -1;

        if (mNextIndex == -1) {
            // No subitems; play the next item.
            int size = mMediaList.size();
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

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void updateRemoteControlClientMetadata() {
        if (!AndroidUtil.isICSOrLater()) // NOP check
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
            Bitmap cover = AudioUtil.getCover(this, media, 512);
            if (cover != null && cover.getConfig() != null) //In case of format not supported
                editor.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, (cover.copy(cover.getConfig(), false)));

            editor.apply();
        }

        //Send metadata to Pebble watch
        if (media != null && mPebbleEnabled) {
            final Intent i = new Intent("com.getpebble.action.NOW_PLAYING");
            i.putExtra("artist", Util.getMediaArtist(this, media));
            i.putExtra("album", Util.getMediaAlbum(this, media));
            i.putExtra("track", media.getTitle());
            sendBroadcast(i);
        }
    }

    private void notifyTrackChanged() {
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        showNotification();
        updateWidget();
        broadcastMetadata();
        updateRemoteControlClientMetadata();
    }

    private void onMediaChanged() {
        notifyTrackChanged();

        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    private void onMediaListChanged() {
        saveMediaList();
        executeUpdate();
        determinePrevAndNextIndices();
    }

    @MainThread
    public void next() {
        mPrevious.push(mCurrentIndex);
        mCurrentIndex = mNextIndex;

        int size = mMediaList.size();
        if (size == 0 || mCurrentIndex < 0 || mCurrentIndex >= size) {
            if (mCurrentIndex < 0)
                saveCurrentMedia();
            Log.w(TAG, "Warning: invalid next index, aborted !");
            stop();
            return;
        }

        playIndex(mCurrentIndex, 0);
        onMediaChanged();
    }

    @MainThread
    public void previous() {
        mCurrentIndex = mPrevIndex;
        if (mPrevious.size() > 0)
            mPrevious.pop();

        int size = mMediaList.size();
        if (size == 0 || mPrevIndex < 0 || mCurrentIndex >= size) {
            Log.w(TAG, "Warning: invalid previous index, aborted !");
            stop();
            return;
        }

        playIndex(mCurrentIndex, 0);
        onMediaChanged();
    }

    @MainThread
    public void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    @MainThread
    public void setRepeatType(RepeatType t) {
        mRepeating = t;
        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    private void updateWidget() {
        updateWidgetState();
        updateWidgetCover();
    }

    private void updateWidgetState() {
        Intent i = new Intent(this, VLCAppWidgetProvider.class);
        i.setAction(ACTION_WIDGET_UPDATE);

        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            i.putExtra("title", media.getTitle());
            i.putExtra("artist", media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : Util.getMediaArtist(this, media));
        }
        else {
            i.putExtra("title", getString(R.string.widget_name));
            i.putExtra("artist", "");
        }
        i.putExtra("isplaying", mMediaPlayer.isPlaying());

        sendBroadcast(i);
    }

    private void updateWidgetCover() {
        Intent i = new Intent(this, VLCAppWidgetProvider.class);
        i.setAction(ACTION_WIDGET_UPDATE_COVER);

        Bitmap cover = hasCurrentMedia() ? AudioUtil.getCover(this, getCurrentMedia(), 64) : null;
        i.putExtra("cover", cover);

        sendBroadcast(i);
    }

    private void updateWidgetPosition(float pos) {
        // no more than one widget update for each 1/50 of the song
        long timestamp = Calendar.getInstance().getTimeInMillis();
        if (!hasCurrentMedia()
                || timestamp - mWidgetPositionTimestamp < getCurrentMedia().getLength() / 50)
            return;

        updateWidgetState();

        mWidgetPositionTimestamp = timestamp;
        Intent i = new Intent(this, VLCAppWidgetProvider.class);
        i.setAction(ACTION_WIDGET_UPDATE_POSITION);
        i.putExtra("position", pos);
        sendBroadcast(i);
    }

    private void broadcastMetadata() {
        MediaWrapper media = getCurrentMedia();
        if (media == null || media.getType() != MediaWrapper.TYPE_AUDIO)
            return;

        boolean playing = mMediaPlayer.isPlaying();

        Intent broadcast = new Intent("com.android.music.metachanged");
        broadcast.putExtra("track", media.getTitle());
        broadcast.putExtra("artist", media.getArtist());
        broadcast.putExtra("album", media.getAlbum());
        broadcast.putExtra("duration", media.getLength());
        broadcast.putExtra("playing", playing);

        sendBroadcast(broadcast);
    }

    private synchronized void loadLastPlaylist() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentMedia = prefs.getString("current_media", "");
        if (currentMedia.equals(""))
            return;
        String[] locations = prefs.getString("media_list", "").split(" ");

        List<String> mediaPathList = new ArrayList<String>(locations.length);
        for (int i = 0 ; i < locations.length ; ++i)
            mediaPathList.add(Uri.decode(locations[i]));

        mShuffling = prefs.getBoolean("shuffling", false);
        mRepeating = RepeatType.values()[prefs.getInt("repeating", RepeatType.None.ordinal())];
        int position = prefs.getInt("position_in_list", Math.max(0, mediaPathList.indexOf(currentMedia)));
        long time = prefs.getLong("position_in_song", -1);
        // load playlist
        loadLocations(mediaPathList, position);
        if (time > 0)
            setTime(time);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("position_in_list", 0);
        editor.putLong("position_in_song", 0);
        Util.commitPreferences(editor);
    }

    private synchronized void saveCurrentMedia() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("current_media", mMediaList.getMRL(Math.max(mCurrentIndex, 0)));
        editor.putBoolean("shuffling", mShuffling);
        editor.putInt("repeating", mRepeating.ordinal());
        Util.commitPreferences(editor);
    }

    private synchronized void saveMediaList() {
        StringBuilder locations = new StringBuilder();
        for (int i = 0; i < mMediaList.size(); i++)
            locations.append(" ").append(Uri.encode(mMediaList.getMRL(i)));
        //We save a concatenated String because putStringSet is APIv11.
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString("media_list", locations.toString().trim());
        Util.commitPreferences(editor);
    }

    private synchronized void savePosition(){
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putInt("position_in_list", mCurrentIndex);
        editor.putLong("position_in_song", mMediaPlayer.getTime());
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
            } catch (IllegalArgumentException e) {
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

    @MainThread
    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }

    @MainThread
    public boolean isShuffling() {
        return mShuffling;
    }

    @MainThread
    public RepeatType getRepeatType() {
        return mRepeating;
    }

    @MainThread
    public boolean hasMedia()  {
        return hasCurrentMedia();
    }

    @MainThread
    public boolean isVideoPlaying() {
        return mMediaPlayer.getVLCVout().areViewsAttached();
    }

    @MainThread
    public String getAlbum() {
        if (hasCurrentMedia())
            return Util.getMediaAlbum(PlaybackService.this, getCurrentMedia());
        else
            return null;
    }

    @MainThread
    public String getArtist() {
        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            return media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : Util.getMediaArtist(PlaybackService.this, media);
        } else
            return null;
    }

    @MainThread
    public String getArtistPrev() {
        if (mPrevIndex != -1)
            return Util.getMediaArtist(PlaybackService.this, mMediaList.getMedia(mPrevIndex));
        else
            return null;
    }

    @MainThread
    public String getArtistNext() {
        if (mNextIndex != -1)
            return Util.getMediaArtist(PlaybackService.this, mMediaList.getMedia(mNextIndex));
        else
            return null;
    }

    @MainThread
    public String getTitle() {
        if (hasCurrentMedia())
            return getCurrentMedia().getTitle();
        else
            return null;
    }

    @MainThread
    public String getTitlePrev() {
        if (mPrevIndex != -1)
            return mMediaList.getMedia(mPrevIndex).getTitle();
        else
            return null;
    }

    @MainThread
    public String getTitleNext() {
        if (mNextIndex != -1)
            return mMediaList.getMedia(mNextIndex).getTitle();
        else
            return null;
    }

    @MainThread
    public Bitmap getCover() {
        if (hasCurrentMedia()) {
            return AudioUtil.getCover(PlaybackService.this, getCurrentMedia(), 512);
        }
        return null;
    }

    @MainThread
    public Bitmap getCoverPrev() {
        if (mPrevIndex != -1)
            return AudioUtil.getCover(PlaybackService.this, mMediaList.getMedia(mPrevIndex), 64);
        else
            return null;
    }

    @MainThread
    public Bitmap getCoverNext() {
        if (mNextIndex != -1)
            return AudioUtil.getCover(PlaybackService.this, mMediaList.getMedia(mNextIndex), 64);
        else
            return null;
    }

    @MainThread
    public synchronized void addCallback(Callback cb) {
        if (!mCallbacks.contains(cb)) {
            mCallbacks.add(cb);
            if (hasCurrentMedia())
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }
    }

    @MainThread
    public synchronized void removeCallback(Callback cb) {
        mCallbacks.remove(cb);
    }

    @MainThread
    public long getTime() {
        return mMediaPlayer.getTime();
    }

    @MainThread
    public long getLength() {
        return  mMediaPlayer.getLength();
    }

    /**
     * Loads a selection of files (a non-user-supplied collection of media)
     * into the primary or "currently playing" playlist.
     *
     * @param mediaPathList A list of locations to load
     * @param position The position to start playing at
     */
    @MainThread
    public void loadLocations(List<String> mediaPathList, int position) {
        ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
        MediaDatabase db = MediaDatabase.getInstance();

        for (int i = 0; i < mediaPathList.size(); i++) {
            String location = mediaPathList.get(i);
            MediaWrapper mediaWrapper = db.getMedia(Uri.parse(location));
            if (mediaWrapper == null) {
                if (!validateLocation(location)) {
                    Log.w(TAG, "Invalid location " + location);
                    showToast(getResources().getString(R.string.invalid_location, location), Toast.LENGTH_SHORT);
                    continue;
                }
                Log.v(TAG, "Creating on-the-fly Media object for " + location);
                mediaWrapper = new MediaWrapper(Uri.parse(location));
            }
            mediaList.add(mediaWrapper);
        }
        load(mediaList, position);
    }

    @MainThread
    public void loadLocation(String mediaPath) {
        ArrayList <String> arrayList = new ArrayList<String>();
        arrayList.add(mediaPath);
        loadLocations(arrayList, 0);
    }

    @MainThread
    public void load(List<MediaWrapper> mediaList, int position) {
        Log.v(TAG, "Loading position " + ((Integer) position).toString() + " in " + mediaList.toString());

        mMediaList.removeEventListener(mListEventListener);
        mMediaList.clear();
        MediaWrapperList currentMediaList = mMediaList;

        mPrevious.clear();

        for (int i = 0; i < mediaList.size(); i++) {
            currentMediaList.add(mediaList.get(i));
        }

        if (mMediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (mMediaList.size() > position && position >= 0) {
            mCurrentIndex = position;
        } else {
            Log.w(TAG, "Warning: positon " + position + " out of bounds");
            mCurrentIndex = 0;
        }

        // Add handler after loading the list
        mMediaList.addEventListener(mListEventListener);

        playIndex(mCurrentIndex, 0);
        saveMediaList();
        onMediaChanged();
    }

    @MainThread
    public void load(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        load(arrayList, 0);
    }

    /**
     * Play a media from the media list (playlist)
     *
     * @param index The index of the media
     * @param flags LibVLC.MEDIA_* flags
     */
    public void playIndex(int index, int flags) {
        if (mMediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (index >= 0 && index < mMediaList.size()) {
            mCurrentIndex = index;
        } else {
            Log.w(TAG, "Warning: index " + index + " out of bounds");
            mCurrentIndex = 0;
        }

        String mrl = mMediaList.getMRL(index);
        if (mrl == null)
            return;
        final MediaWrapper mw = mMediaList.getMedia(index);
        if (mw == null)
            return;

        final Media media = new Media(VLCInstance.get(), mw.getUri());
        VLCOptions.setMediaOptions(media, this, flags | mw.getFlags());
        media.setEventListener(mMediaListener);
        mMediaPlayer.setMedia(media);
        media.release();
        mMediaPlayer.setEqualizer(VLCOptions.getEqualizer(this));
        mMediaPlayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
        changeAudioFocus(true);
        mMediaPlayer.setEventListener(mMediaPlayerListener);
        mMediaPlayer.play();

        notifyTrackChanged();
        determinePrevAndNextIndices();
    }

    /**
     * Use this function to play a media inside whatever MediaList LibVLC is following.
     *
     * Unlike load(), it does not import anything into the primary list.
     */
    @MainThread
    public void playIndex(int index) {
        playIndex(index, 0);
    }

    /**
     * Use this function to show an URI in the audio interface WITHOUT
     * interrupting the stream.
     *
     * Mainly used by VideoPlayerActivity in response to loss of video track.
     */
    @MainThread
    public void showWithoutParse(int index) {
        String URI = mMediaList.getMRL(index);
        Log.v(TAG, "Showing index " + index + " with playing URI " + URI);
        // Show an URI without interrupting/losing the current stream

        if(URI == null || !mMediaPlayer.isPlaying())
            return;
        mCurrentIndex = index;

        notifyTrackChanged();
    }

    /**
     * Append to the current existing playlist
     */
    @MainThread
    public void append(List<MediaWrapper> mediaList) {
        if (!hasCurrentMedia())
        {
            load(mediaList, 0);
            return;
        }

        for (int i = 0; i < mediaList.size(); i++) {
            MediaWrapper mediaWrapper = mediaList.get(i);
            mMediaList.add(mediaWrapper);
        }
        onMediaListChanged();
    }

    @MainThread
    public void append(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        append(arrayList);
    }

    /**
     * Move an item inside the playlist.
     */
    @MainThread
    public void moveItem(int positionStart, int positionEnd) {
        mMediaList.move(positionStart, positionEnd);
        PlaybackService.this.saveMediaList();
    }

    @MainThread
    public void remove(int position) {
        mMediaList.remove(position);
        onMediaListChanged();
    }

    @MainThread
    public void removeLocation(String location) {
        mMediaList.remove(location);
        onMediaListChanged();
    }

    @MainThread
    public List<MediaWrapper> getMedias() {
        final ArrayList<MediaWrapper> ml = new ArrayList<MediaWrapper>();
        for (int i = 0; i < mMediaList.size(); i++) {
            ml.add(mMediaList.getMedia(i));
        }
        return ml;
    }

    @MainThread
    public List<String> getMediaLocations() {
        ArrayList<String> medias = new ArrayList<String>();
        for (int i = 0; i < mMediaList.size(); i++) {
            medias.add(mMediaList.getMRL(i));
        }
        return medias;
    }

    @MainThread
    public String getCurrentMediaLocation() {
        return mMediaList.getMRL(mCurrentIndex);
    }

    @MainThread
    public int getCurrentMediaPosition() {
        return mCurrentIndex;
    }

    @MainThread
    public MediaWrapper getCurrentMediaWrapper() {
        return PlaybackService.this.getCurrentMedia();
    }

    @MainThread
    public void setTime(long time) {
        mMediaPlayer.setTime(time);
    }

    @MainThread
    public boolean hasNext() {
        return mNextIndex != -1;
    }

    @MainThread
    public boolean hasPrevious() {
        return mPrevIndex != -1;
    }

    @MainThread
    public void detectHeadset(boolean enable)  {
        mDetectHeadset = enable;
    }

    @MainThread
    public float getRate()  {
        return mMediaPlayer.getRate();
    }

    @MainThread
    public void setRate(float rate) {
        mMediaPlayer.setRate(rate);
    }

    @MainThread
    public void navigate(int where) {
        mMediaPlayer.navigate(where);
    }

    @MainThread
    public MediaPlayer.Chapter[] getChapters(int title) {
        return mMediaPlayer.getChapters(title);
    }

    @MainThread
    public MediaPlayer.Title[] getTitles() {
        return mMediaPlayer.getTitles();
    }

    @MainThread
    public int getChapterIdx() {
        return mMediaPlayer.getChapter();
    }

    @MainThread
    public void setChapterIdx(int chapter) {
        mMediaPlayer.setChapter(chapter);
    }

    @MainThread
    public int getTitleIdx() {
        return mMediaPlayer.getTitle();
    }

    @MainThread
    public void setTitleIdx(int title) {
        mMediaPlayer.setTitle(title);
    }

    @MainThread
    public int getVolume() {
        return mMediaPlayer.getVolume();
    }

    @MainThread
    public int setVolume(int volume) {
        return mMediaPlayer.setVolume(volume);
    }

    @MainThread
    public void setPosition(float pos) {
        mMediaPlayer.setPosition(pos);
    }

    @MainThread
    public int getAudioTracksCount() {
        return mMediaPlayer.getAudioTracksCount();
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getAudioTracks() {
        return mMediaPlayer.getAudioTracks();
    }

    @MainThread
    public int getAudioTrack() {
        return mMediaPlayer.getAudioTrack();
    }

    @MainThread
    public boolean setAudioTrack(int index) {
        return mMediaPlayer.setAudioTrack(index);
    }

    @MainThread
    public int getVideoTracksCount() {
        return mMediaPlayer.getVideoTracksCount();
    }

    @MainThread
    public boolean addSubtitleTrack(String path) {
        return mMediaPlayer.setSubtitleFile(path);
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getSpuTracks() {
        return mMediaPlayer.getSpuTracks();
    }

    @MainThread
    public int getSpuTrack() {
        return mMediaPlayer.getSpuTrack();
    }

    @MainThread
    public boolean setSpuTrack(int index) {
        return mMediaPlayer.setSpuTrack(index);
    }

    @MainThread
    public int getSpuTracksCount() {
        return mMediaPlayer.getSpuTracksCount();
    }

    @MainThread
    public boolean setAudioDelay(long delay) {
        return mMediaPlayer.setAudioDelay(delay);
    }

    @MainThread
    public long getAudioDelay() {
        return mMediaPlayer.getAudioDelay();
    }

    @MainThread
    public boolean setSpuDelay(long delay) {
        return mMediaPlayer.setSpuDelay(delay);
    }

    @MainThread
    public long getSpuDelay() {
        return mMediaPlayer.getSpuDelay();
    }

    @MainThread
    public void setEqualizer(MediaPlayer.Equalizer equalizer) {
        mMediaPlayer.setEqualizer(equalizer);
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    public int expand() {
        final Media media = mMediaPlayer.getMedia();
        if (media == null)
            return -1;
        final MediaList ml = media.subItems();
        media.release();
        int ret;

        if (ml.getCount() > 0) {
            mMediaList.remove(mCurrentIndex);
            for (int i = 0; i < ml.getCount(); ++i) {
                final Media child = ml.getMediaAt(i);
                child.parse();
                mMediaList.insert(mCurrentIndex, new MediaWrapper(child));
                child.release();
            }
            ret = 0;
        } else {
            ret = -1;
        }
        ml.release();
        return ret;
    }

    public void restartMediaPlayer() {
        stop();
        mMediaPlayer.release();
        mMediaPlayer = newMediaPlayer();
        /* TODO RESUME */
    }

    public static class Client {
        public static final String TAG = "PlaybackService.Client";

        @MainThread
        public interface Callback {
            void onConnected(PlaybackService service);
            void onDisconnected();
        }

        private boolean mBound = false;
        private final Callback mCallback;
        private final Context mContext;

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder iBinder) {
                Log.d(TAG, "Service Connected");
                if (!mBound)
                    return;

                final PlaybackService service = PlaybackService.getService(iBinder);
                if (service != null)
                    mCallback.onConnected(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "Service Disconnected");
                mCallback.onDisconnected();
            }
        };

        private static Intent getServiceIntent(Context context) {
            return new Intent(context, PlaybackService.class);
        }

        private static void startService(Context context) {
            context.startService(getServiceIntent(context));
        }

        private static void stopService(Context context) {
            context.stopService(getServiceIntent(context));
        }

        public Client(Context context, Callback callback) {
            if (context == null || callback == null)
                throw new IllegalArgumentException("Context and callback can't be null");
            mContext = context;
            mCallback = callback;
        }

        @MainThread
        public void connect() {
            if (mBound)
                throw new IllegalStateException("already connected");
            startService(mContext);
            mBound = mContext.bindService(getServiceIntent(mContext), mServiceConnection, BIND_AUTO_CREATE);
        }

        @MainThread
        public void disconnect() {
            if (mBound) {
                mBound = false;
                mContext.unbindService(mServiceConnection);
            }
        }

        public static void restartService(Context context) {
            stopService(context);
            startService(context);
        }
    }
}
