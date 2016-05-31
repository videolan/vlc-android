/*****************************************************************************
 * PlaybackService.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.browser.NetworkBrowserFragment;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.video.PopupManager;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.media.MediaWrapperList;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlaybackService extends Service implements IVLCVout.Callback {

    private static final String TAG = "VLC/PlaybackService";

    private static final int SHOW_PROGRESS = 0;
    private static final int SHOW_TOAST = 1;
    public static final String ACTION_REMOTE_GENERIC =  Strings.buildPkgString("remote.");
    public static final String ACTION_REMOTE_BACKWARD = ACTION_REMOTE_GENERIC+"Backward";
    public static final String ACTION_REMOTE_PLAY = ACTION_REMOTE_GENERIC+"Play";
    public static final String ACTION_REMOTE_PLAYPAUSE = ACTION_REMOTE_GENERIC+"PlayPause";
    public static final String ACTION_REMOTE_PAUSE = ACTION_REMOTE_GENERIC+"Pause";
    public static final String ACTION_REMOTE_STOP = ACTION_REMOTE_GENERIC+"Stop";
    public static final String ACTION_REMOTE_FORWARD = ACTION_REMOTE_GENERIC+"Forward";
    public static final String ACTION_REMOTE_LAST_PLAYLIST = ACTION_REMOTE_GENERIC+"LastPlaylist";
    public static final String ACTION_REMOTE_LAST_VIDEO_PLAYLIST = ACTION_REMOTE_GENERIC+"LastVideoPlaylist";
    public static final String ACTION_REMOTE_SWITCH_VIDEO = ACTION_REMOTE_GENERIC+"SwitchToVideo";

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

    private SharedPreferences mSettings;
    private final IBinder mBinder = new LocalBinder();
    private MediaWrapperList mMediaList = new MediaWrapperList();
    private MediaPlayer mMediaPlayer;
    private boolean mParsed = false;
    private boolean mSeekable = false;
    private boolean mPausable = false;
    private boolean mIsAudioTrack = false;
    private boolean mHasHdmiAudio = false;
    private boolean mSwitchingToVideo = false;
    private boolean mVideoBackground = false;

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

    MediaSessionCompat mMediaSession;
    protected MediaSessionCallback mSessionCallback;
    private static final long PLAYBACK_ACTIONS = PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;
    private boolean mShuffling = false;
    private int mRepeating = REPEAT_NONE;
    private Random mRandom = null; // Used in shuffling process
    private long mSavedTime = 0l;
    private boolean mHasAudioFocus = false;
    // RemoteControlClient-related
    /**
     * RemoteControlClient is for lock screen playback control.
     */
    private RemoteControlClientReceiver mRemoteControlClientReceiver = null;
    /**
     * Last widget position update timestamp
     */
    private long mWidgetPositionTimestamp = Calendar.getInstance().getTimeInMillis();
    private ComponentName mRemoteControlClientReceiverComponent;
    private PopupManager mPopupManager;

    private static LibVLC LibVLC() {
        return VLCInstance.get();
    }

    private MediaPlayer newMediaPlayer() {
        final MediaPlayer mp = new MediaPlayer(LibVLC());
        final String aout = VLCOptions.getAout(mSettings);
        if (mp.setAudioOutput(aout) && aout.equals("android_audiotrack")) {
            mIsAudioTrack = true;
            if (mHasHdmiAudio)
                mp.setAudioOutputDevice("hdmi");
        } else
            mIsAudioTrack = false;
        mp.getVLCVout().addCallback(this);

        return mp;
    }

    private static boolean readPhoneState() {
        return !AndroidUtil.isFroyoOrLater();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mMediaPlayer = newMediaPlayer();
        mMediaPlayer.setEqualizer(VLCOptions.getEqualizer(this));

        if (!VLCInstance.testCompatibleCPU(this)) {
            stopSelf();
            return;
        }

        if (!AndroidDevices.hasTsp() && !AndroidDevices.hasPlayServices())
            AndroidDevices.setRemoteControlReceiverEnabled(true);

        mDetectHeadset = mSettings.getBoolean("enable_headset_detection", true);

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
        filter.addAction(ACTION_REMOTE_LAST_VIDEO_PLAYLIST);
        filter.addAction(ACTION_REMOTE_SWITCH_VIDEO);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_INIT);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);
        registerV21();

        boolean stealRemoteControl = mSettings.getBoolean("enable_steal_remote_control", false);

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

        if (readPhoneState()) {
            initPhoneListener();
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, mPhoneEvents);
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
                loadLastPlaylist(TYPE_AUDIO);
        } else if (ACTION_REMOTE_PLAY.equals(intent.getAction())) {
            if (hasCurrentMedia())
                play();
            else
                loadLastPlaylist(TYPE_AUDIO);
        }
        updateWidget();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();

        if (!AndroidDevices.hasTsp() && !AndroidDevices.hasPlayServices())
            AndroidDevices.setRemoteControlReceiverEnabled(false);

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

        if (readPhoneState()) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!hasCurrentMedia())
            stopSelf();
        return true;
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
            private boolean wasPlaying = false;

            @Override
            public void onAudioFocusChange(int focusChange) {
                /*
                 * Pause playback during alerts and notifications
                 */
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.i(TAG, "AUDIOFOCUS_LOSS");
                        // Pause playback
                        changeAudioFocus(false);
                        pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        // Pause playback
                        mLossTransient = true;
                        wasPlaying = isPlaying();
                        if (wasPlaying)
                            pause();
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
                        } else if (mLossTransient) {
                            if (wasPlaying)
                                mMediaPlayer.play();
                            mLossTransient = false;
                        }
                        break;
                }
            }
        };
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private void changeAudioFocus(boolean acquire) {
        final AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (am == null)
            return;

        if (acquire) {
            if (!mHasAudioFocus) {
                final int result = am.requestAudioFocus(mAudioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    am.setParameters("bgm_state=true");
                    mHasAudioFocus = true;
                }
            }
        } else {
            if (mHasAudioFocus) {
                final int result = am.abandonAudioFocus(mAudioFocusListener);
                am.setParameters("bgm_state=false");
                mHasAudioFocus = false;
            }
        }
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
        private boolean wasPlaying = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state = intent.getIntExtra("state", 0);
            if( mMediaPlayer == null ) {
                Log.w(TAG, "Intent received, but VLC is not loaded, skipping.");
                return;
            }

            // skip all headsets events if there is a call
            TelephonyManager telManager = (TelephonyManager) VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !mMediaPlayer.isPlaying() && !hasCurrentMedia()) {
                context.startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(ACTION_REMOTE_PLAYPAUSE)) {
                if (!hasCurrentMedia())
                    return;
                if (mMediaPlayer.isPlaying())
                    pause();
                else
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PLAY)) {
                if (!mMediaPlayer.isPlaying() && hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PAUSE)) {
                if (hasCurrentMedia())
                    pause();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_BACKWARD)) {
                previous();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_STOP) ||
                    action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
                stop();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_FORWARD)) {
                next();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_PLAYLIST)) {
                loadLastPlaylist(TYPE_AUDIO);
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_VIDEO_PLAYLIST)) {
                loadLastPlaylist(TYPE_VIDEO);
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_SWITCH_VIDEO)) {
                removePopup();
                if (hasMedia()) {
                    getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    switchToVideo();
                }
            } else if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_INIT)) {
                updateWidget();
            }

            /*
             * headset plug events
             */
            else if (mDetectHeadset && !mHasHdmiAudio) {
                if (action.equalsIgnoreCase(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    Log.i(TAG, "Headset Removed.");
                    wasPlaying = isPlaying();
                    if (wasPlaying && hasCurrentMedia())
                        pause();
                } else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.i(TAG, "Headset Inserted.");
                    if (wasPlaying && hasCurrentMedia())
                        play();
                }
            }
        }
    };


    @Override
    public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
        hideNotification(false);
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
        mSwitchingToVideo = false;
    }

    @Override
    public void onHardwareAccelerationError(IVLCVout vlcVout) {
    }

    private final Media.EventListener mMediaListener = new Media.EventListener() {
        @Override
        public void onEvent(Media.Event event) {
            boolean update = true;
            switch (event.type) {
                case Media.Event.MetaChanged:
                    /* Update Meta if file is already parsed */
                    if (mParsed && updateCurrentMeta(event.getMetaId()))
                        executeUpdate();
                    Log.i(TAG, "Media.Event.MetaChanged: " + event.getMetaId());
                    break;
                case Media.Event.ParsedChanged:
                    Log.i(TAG, "Media.Event.ParsedChanged");
                    updateCurrentMeta(-1);
                    mParsed = true;
                    break;
                default:
                    update = false;

            }
            if (update) {
                for (Callback callback : mCallbacks)
                    callback.onMediaEvent(event);
                if (mParsed)
                    showNotification();
            }
        }
    };

    /**
     * Update current media meta and return true if player needs to be updated
     *
     * @param id of the Meta event received, -1 for none
     * @return true if UI needs to be updated
     */
    private boolean updateCurrentMeta(int id) {
        if (id == Media.Meta.Publisher)
            return false;
        final MediaWrapper mw = getCurrentMedia();
        if (mw != null)
            mw.updateMeta(mMediaPlayer);
        return id != Media.Meta.NowPlaying || getCurrentMedia().getNowPlaying() != null;
    }

    private final MediaPlayer.EventListener mMediaPlayerListener = new MediaPlayer.EventListener() {

        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Playing:

                    Log.i(TAG, "MediaPlayer.Event.Playing");
                    executeUpdate();
                    publishState(event.type);
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
                                    MediaDatabase.INDEX_MEDIA_LENGTH, length);
                        }
                    }

                    changeAudioFocus(true);
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    if (!mVideoBackground && switchToVideo())
                        hideNotification(false);
                    else
                        showNotification();
                    mVideoBackground = false;
                    break;
                case MediaPlayer.Event.Paused:
                    Log.i(TAG, "MediaPlayer.Event.Paused");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();
                    showNotification();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.Stopped:
                    Log.i(TAG, "MediaPlayer.Event.Stopped");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EndReached:
                    Log.i(TAG, "MediaPlayer.Event.EndReached");
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
                    if (event.getEsChangedType() == Media.Track.Type.Video && (mVideoBackground || !switchToVideo())) {
                        /* Update notification content intent: resume video or resume audio activity */
                        updateMetadata();
                    }
                    break;
                case MediaPlayer.Event.ESDeleted:
                    break;
                case MediaPlayer.Event.PausableChanged:
                    mPausable = event.getPausable();
                    break;
                case MediaPlayer.Event.SeekableChanged:
                    mSeekable = event.getSeekable();
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

    public boolean canSwitchToVideo() {
        return hasCurrentMedia() && mMediaPlayer.getVideoTracksCount() > 0;
    }

    @MainThread
    public boolean switchToVideo() {
        if (mMediaList.getMedia(mCurrentIndex).hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !canSwitchToVideo())
            return false;
        mVideoBackground = false;
        if (isVideoPlaying()) {//Player is already running, just send it an intent
            setVideoTrackEnabled(true);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    VideoPlayerActivity.getIntent(VideoPlayerActivity.PLAY_FROM_SERVICE,
                            getCurrentMediaWrapper(), false, mCurrentIndex));
        } else if (!mSwitchingToVideo) {//Start the video player
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    getCurrentMediaWrapper().getUri(), mCurrentIndex);
            mSwitchingToVideo = true;
        }
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
        updateMetadata();
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
        if (mMediaPlayer.getVLCVout().areViewsAttached()) {
            hideNotification(false);
            return;
        }
        try {
            boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
            MediaMetadataCompat metaData = mMediaSession.getController().getMetadata();
            String title = metaData.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            String artist = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
            String album = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
            Bitmap cover = coverOnLockscreen ?
                    metaData.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) :
                    AudioUtil.getCover(this, getCurrentMedia(), 512);
            if (cover == null)
                cover = BitmapFactory.decodeResource(VLCApplication.getAppContext().getResources(), R.drawable.icon);
            Notification notification;

            //Watch notification dismissed
            PendingIntent piStop = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_REMOTE_STOP), PendingIntent.FLAG_UPDATE_CURRENT);

            // add notification to status bar
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setSmallIcon(R.drawable.ic_stat_vlc)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(title)
                .setContentText(artist + " - " + album)
                .setLargeIcon(cover)
                .setTicker(title + " - " + artist)
                .setAutoCancel(!mMediaPlayer.isPlaying())
                .setOngoing(mMediaPlayer.isPlaying())
                .setDeleteIntent(piStop);


            PendingIntent pendingIntent;
            if (mVideoBackground || (canSwitchToVideo() && !mMediaList.getMedia(mCurrentIndex).hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO))) {
                /* Resume VideoPlayerActivity from ACTION_REMOTE_SWITCH_VIDEO intent */
                final Intent notificationIntent = new Intent(ACTION_REMOTE_SWITCH_VIDEO);
                pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                /* Resume AudioPlayerActivity */

                final Intent notificationIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                notificationIntent.setAction(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER);
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }

            builder.setContentIntent(pendingIntent);

            PendingIntent piBackward = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_BACKWARD), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piPlay = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_PLAYPAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent piForward = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REMOTE_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(R.drawable.ic_previous_w, getString(R.string.previous), piBackward);
            if (mMediaPlayer.isPlaying())
                builder.addAction(R.drawable.ic_pause_w, getString(R.string.pause), piPlay);
            else
                builder.addAction(R.drawable.ic_play_w, getString(R.string.play), piPlay);
            builder.addAction(R.drawable.ic_next_w, getString(R.string.next), piForward);

            builder.setStyle(new NotificationCompat.MediaStyle()
                            .setMediaSession(mMediaSession.getSessionToken())
                            .setShowActionsInCompactView(new int[] {0,1,2})
                            .setShowCancelButton(true)
                            .setCancelButtonIntent(piStop)
            );

            notification = builder.build();

            startService(new Intent(this, PlaybackService.class));
            if (!AndroidUtil.isLolliPopOrLater() || mMediaPlayer.isPlaying())
                startForeground(3, notification);
            else {
                stopForeground(false);
                NotificationManagerCompat.from(this).notify(3, notification);
            }
        } catch (IllegalArgumentException e){
            // On somme crappy firmwares, shit can happen
            Log.e(TAG, "Failed to display notification", e);
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
        NotificationManagerCompat.from(this).cancel(3);
        if(stopPlayback)
            stopSelf();
    }

    @MainThread
    public void pause() {
        if (mPausable) {
            savePosition();
            mHandler.removeMessages(SHOW_PROGRESS);
            // hideNotification(); <-- see event handler
            mMediaPlayer.pause();
            broadcastMetadata();
        }
    }

    @MainThread
    public void play() {
        if(hasCurrentMedia()) {
            mMediaPlayer.play();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            updateMetadata();
            updateWidget();
            broadcastMetadata();
        }
    }

    @MainThread
    public void stopPlayback() {
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        removePopup();
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
        mCurrentIndex = -1;
        mPrevious.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        broadcastMetadata();
        executeUpdate();
        executeUpdateProgress();
        changeAudioFocus(false);
    }

    @MainThread
    public void stop() {
        stopPlayback();
        stopSelf();
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
            if (mRepeating == REPEAT_ONE) {
                mPrevIndex = mNextIndex = mCurrentIndex;
            } else {

                if(mShuffling) {
                    if(mPrevious.size() > 0)
                        mPrevIndex = mPrevious.peek();
                    // If we've played all songs already in shuffle, then either
                    // reshuffle or stop (depending on RepeatType).
                    if(mPrevious.size() + 1 == size) {
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex = -1;
                            return;
                        } else {
                            mPrevious.clear();
                            mRandom = new Random(System.currentTimeMillis());
                        }
                    }
                    if(mRandom == null) mRandom = new Random(System.currentTimeMillis());
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
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex = -1;
                        } else {
                            mNextIndex = 0;
                        }
                    }
                }
            }
        }
    }

    private void initMediaSession() {
         ComponentName mediaButtonEventReceiver = new ComponentName(this,
                    RemoteControlClientReceiver.class);
        mSessionCallback = new MediaSessionCallback();
        mMediaSession = new MediaSessionCompat(this, "VLC", mediaButtonEventReceiver, null);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setCallback(mSessionCallback);
        try {
            mMediaSession.setActive(true);
        } catch (NullPointerException e) {
            // Some versions of KitKat do not support AudioManager.registerMediaButtonIntent
            // with a PendingIntent. They will throw a NullPointerException, in which case
            // they should be able to activate a MediaSessionCompat with only transport
            // controls.
            mMediaSession.setActive(false);
            mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mMediaSession.setActive(true);
        }
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            play();
        }
        @Override
        public void onPause() {
            pause();
        }

        @Override
        public void onStop() {
            stop();
        }

        @Override
        public void onSkipToNext() {
            next();
        }

        @Override
        public void onSkipToPrevious() {
            previous();
        }

        @Override
        public void onSeekTo(long pos) {
            setTime(pos);
        }

        @Override
        public void onFastForward() {
            next();
        }

        @Override
        public void onRewind() {
            previous();
        }
    }

    protected void updateMetadata() {
        MediaWrapper media = getCurrentMedia();
        if (media == null)
            return;
        if (mMediaSession == null)
            initMediaSession();
        String title = media.getNowPlaying();
        if (title == null)
            title = media.getTitle();
        boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
        MediaMetadataCompat.Builder bob = new MediaMetadataCompat.Builder();
        bob.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, MediaUtils.getMediaGenre(this, media))
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, media.getTrackNumber())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, MediaUtils.getMediaArtist(this, media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, MediaUtils.getMediaReferenceArtist(this, media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, MediaUtils.getMediaAlbum(this, media))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.getLength());
        if (coverOnLockscreen) {
            Bitmap cover = AudioUtil.getCover(this, media, 512);
            if (cover != null && cover.getConfig() != null) //In case of format not supported
                bob.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover.copy(cover.getConfig(), false));
        }
        mMediaSession.setMetadata(bob.build());

        //Send metadata to Pebble watch
        if (mPebbleEnabled) {
            final Intent i = new Intent("com.getpebble.action.NOW_PLAYING");
            i.putExtra("artist", MediaUtils.getMediaArtist(this, media));
            i.putExtra("album", MediaUtils.getMediaAlbum(this, media));
            i.putExtra("track", media.getTitle());
            sendBroadcast(i);
        }
    }

    protected void publishState(int state) {
        if (mMediaSession == null)
            return;
        PlaybackStateCompat.Builder bob = new PlaybackStateCompat.Builder();
        bob.setActions(PLAYBACK_ACTIONS);
        switch (state) {
            case MediaPlayer.Event.Playing:
                bob.setState(PlaybackStateCompat.STATE_PLAYING, -1, 1);
                break;
            case MediaPlayer.Event.Stopped:
                bob.setState(PlaybackStateCompat.STATE_STOPPED, -1, 0);
                break;
            default:
            bob.setState(PlaybackStateCompat.STATE_PAUSED, -1, 0);
        }
        PlaybackStateCompat pbState = bob.build();
        mMediaSession.setPlaybackState(pbState);
        mMediaSession.setActive(state != PlaybackStateCompat.STATE_STOPPED);
    }

    private void notifyTrackChanged() {
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        updateMetadata();
        updateWidget();
        broadcastMetadata();
    }

    private void onMediaChanged() {
        notifyTrackChanged();

        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    private void onMediaListChanged() {
        saveMediaList();
        determinePrevAndNextIndices();
        executeUpdate();
    }

    @MainThread
    public void next() {
        int size = mMediaList.size();

        mPrevious.push(mCurrentIndex);
        mCurrentIndex = mNextIndex;
        if (size == 0 || mCurrentIndex < 0 || mCurrentIndex >= size) {
            if (mCurrentIndex < 0)
                saveCurrentMedia();
            Log.w(TAG, "Warning: invalid next index, aborted !");
            //Close video player if started
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(VideoPlayerActivity.EXIT_PLAYER));
            stop();
            return;
        }
        mVideoBackground = !isVideoPlaying() && canSwitchToVideo();
        playIndex(mCurrentIndex, 0);
        saveCurrentMedia();
    }

    @MainThread
    public void previous() {
        int size = mMediaList.size();
        if (hasPrevious() && mCurrentIndex > 0 &&
                (!mMediaPlayer.isSeekable() || mMediaPlayer.getTime() < 2000l)) {
            mCurrentIndex = mPrevIndex;
            if (mPrevious.size() > 0)
                mPrevious.pop();
            if (size == 0 || mPrevIndex < 0 || mCurrentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !");
                stop();
                return;
            }
        } else
            setPosition(0f);

        playIndex(mCurrentIndex, 0);
        saveCurrentMedia();
    }

    @MainThread
    public void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
        savePosition();
        determinePrevAndNextIndices();
    }

    @MainThread
    public void setRepeatType(int repeatType) {
        mRepeating = repeatType;
        savePosition();
        determinePrevAndNextIndices();
    }

    private void updateWidget() {
        updateWidgetState();
        updateWidgetCover();
    }

    private void updateWidgetState() {
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE);

        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            i.putExtra("title", media.getTitle());
            i.putExtra("artist", media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : MediaUtils.getMediaArtist(this, media));
        }
        else {
            i.putExtra("title", getString(R.string.widget_default_text));
            i.putExtra("artist", "");
        }
        i.putExtra("isplaying", mMediaPlayer.isPlaying());

        sendBroadcast(i);
    }

    private void updateWidgetCover() {
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_COVER);

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
        Intent i = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_POSITION);
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

    public synchronized void loadLastPlaylist(int type) {
        boolean audio = type == TYPE_AUDIO;
        String currentMedia = mSettings.getString(audio ? "current_song" : "current_media", "");
        if (currentMedia.equals(""))
            return;
        String[] locations = mSettings.getString(audio ? "audio_list" : "media_list", "").split(" ");
        if (locations.length == 0)
            return;

        List<String> mediaPathList = new ArrayList<String>(locations.length);
        for (int i = 0 ; i < locations.length ; ++i)
            mediaPathList.add(Uri.decode(locations[i]));

        mShuffling = mSettings.getBoolean(audio ? "audio_shuffling" : "media_shuffling", false);
        mRepeating = mSettings.getInt(audio ? "audio_repeating" : "media_repeating", REPEAT_NONE);
        int position = mSettings.getInt(audio ? "position_in_audio_list" : "position_in_media_list",
                Math.max(0, mediaPathList.indexOf(currentMedia)));
        long time = mSettings.getLong(audio ? "position_in_song" : "position_in_media", -1);
        mSavedTime = time;
        // load playlist
        loadLocations(mediaPathList, position);
        if (time > 0)
            setTime(time);
        if(!audio) {
            boolean paused = mSettings.getBoolean(PreferencesActivity.VIDEO_PAUSED, !isPlaying());
            float rate = mSettings.getFloat(PreferencesActivity.VIDEO_SPEED, getRate());
            if (paused)
                pause();
            if (rate != 1.0f)
                setRate(rate);
        }
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", 0);
        editor.putLong(audio ? "position_in_song" : "position_in_media", 0);
        Util.commitPreferences(editor);
    }

    private synchronized void saveCurrentMedia() {
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "current_song" : "current_media", mMediaList.getMRL(Math.max(mCurrentIndex, 0)));
        Util.commitPreferences(editor);
    }

    private synchronized void saveMediaList() {
        if (getCurrentMedia() == null)
            return;
        StringBuilder locations = new StringBuilder();
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
            locations.append(" ").append(Uri.encode(mMediaList.getMRL(i)));
        }
        //We save a concatenated String because putStringSet is APIv11.
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "audio_list" : "media_list", locations.toString().trim());
        Util.commitPreferences(editor);
    }

    private synchronized void savePosition(){
        if (getCurrentMedia() == null)
            return;
        SharedPreferences.Editor editor = mSettings.edit();
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        editor.putBoolean(audio ? "audio_shuffling" : "media_shuffling", mShuffling);
        editor.putInt(audio ? "audio_repeating" : "media_repeating", mRepeating);
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", mCurrentIndex);
        editor.putLong(audio ? "position_in_song" : "position_in_media", mMediaPlayer.getTime());
        if(!audio) {
            editor.putBoolean(PreferencesActivity.VIDEO_PAUSED, !isPlaying());
            editor.putFloat(PreferencesActivity.VIDEO_SPEED, getRate());
        }
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
    public boolean isSeekable() {
        return mSeekable;
    }

    @MainThread
    public boolean isPausable() {
        return mPausable;
    }

    @MainThread
    public boolean isShuffling() {
        return mShuffling;
    }

    @MainThread
    public boolean canShuffle()  {
        return getMediaListSize() > 2;
    }

    @MainThread
    public int getRepeatType() {
        return mRepeating;
    }

    @MainThread
    public boolean hasMedia()  {
        return hasCurrentMedia();
    }

    @MainThread
    public boolean hasPlaylist()  {
        return getMediaListSize() > 1;
    }

    @MainThread
    public boolean isVideoPlaying() {
        return mMediaPlayer.getVLCVout().areViewsAttached();
    }

    @MainThread
    public String getAlbum() {
        if (hasCurrentMedia())
            return MediaUtils.getMediaAlbum(PlaybackService.this, getCurrentMedia());
        else
            return null;
    }

    @MainThread
    public String getArtist() {
        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            return media.getNowPlaying() != null ?
                    media.getTitle()
                    : MediaUtils.getMediaArtist(PlaybackService.this, media);
        } else
            return null;
    }

    @MainThread
    public String getArtistPrev() {
        if (mPrevIndex != -1)
            return MediaUtils.getMediaArtist(PlaybackService.this, mMediaList.getMedia(mPrevIndex));
        else
            return null;
    }

    @MainThread
    public String getArtistNext() {
        if (mNextIndex != -1)
            return MediaUtils.getMediaArtist(PlaybackService.this, mMediaList.getMedia(mNextIndex));
        else
            return null;
    }

    @MainThread
    public String getTitle() {
        if (hasCurrentMedia())
            return getCurrentMedia().getNowPlaying() != null ? getCurrentMedia().getNowPlaying() : getCurrentMedia().getTitle();
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
    public void loadUri(Uri uri) {
        String path = uri.toString();
        if (TextUtils.equals(uri.getScheme(), "content")) {
            path = "file://"+ FileUtils.getPathFromURI(uri);
        }
        loadLocation(path);
    }

    @MainThread
    public void loadLocation(String mediaPath) {
        loadLocations(Collections.singletonList(mediaPath), 0);
    }

    @MainThread
    public void load(List<MediaWrapper> mediaList, int position) {
        Log.v(TAG, "Loading position " + ((Integer) position).toString() + " in " + mediaList.toString());

        if (hasCurrentMedia())
            savePosition();

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
        if (!mVideoBackground && mw.getType() == MediaWrapper.TYPE_VIDEO && isVideoPlaying())
            mw.addFlags(MediaWrapper.MEDIA_VIDEO);

        if (mVideoBackground)
            mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);

        /* Pausable and seekable are true by default */
        mParsed = false;
        mSwitchingToVideo = false;
        mPausable = mSeekable = true;
        final Media media = new Media(VLCInstance.get(), mw.getUri());
        VLCOptions.setMediaOptions(media, this, flags | mw.getFlags());

        if (mw.getSlaves() != null) {
            for (Media.Slave slave : mw.getSlaves())
                media.addSlave(slave);
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    MediaDatabase.getInstance().saveSlaves(mw);
                }
            });
        }
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Media.Slave> list = MediaDatabase.getInstance().getSlaves(mw.getLocation());
                for (Media.Slave slave : list)
                    mMediaPlayer.addSlave(slave.type, Uri.parse(slave.uri));
            }
        });

        media.setEventListener(mMediaListener);
        mMediaPlayer.setMedia(media);
        media.release();

        if (mw .getType() != MediaWrapper.TYPE_VIDEO || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)
                || isVideoPlaying()) {
            mMediaPlayer.setEqualizer(VLCOptions.getEqualizer(this));
            mMediaPlayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
            changeAudioFocus(true);
            mMediaPlayer.setEventListener(mMediaPlayerListener);
            mMediaPlayer.play();
            if(mSavedTime != 0l)
                mMediaPlayer.setTime(mSavedTime);
            mSavedTime = 0l;

            determinePrevAndNextIndices();
            if (mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true))
                VLCApplication.runBackground(new Runnable() {
                    @Override
                    public void run() {
                        MediaDatabase.getInstance().addHistoryItem(mw);
                    }
                });
        } else {//Start VideoPlayer for first video, it will trigger playIndex when ready.
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    getCurrentMediaWrapper().getUri(), mCurrentIndex);
        }
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
        setVideoTrackEnabled(false);
        MediaWrapper media = mMediaList.getMedia(index);

        if(media == null || !mMediaPlayer.isPlaying())
            return;
        // Show an URI without interrupting/losing the current stream
        Log.v(TAG, "Showing index " + index + " with playing URI " + media.getUri());
        mCurrentIndex = index;

        notifyTrackChanged();
        showNotification();
    }

    @MainThread
    public void switchToPopup(int index) {
        showWithoutParse(index);
        showPopup();
    }

    @MainThread
    public void removePopup() {
        if (mPopupManager != null) {
            mPopupManager.removePopup();
        }
        mPopupManager = null;
    }

    @MainThread
    public boolean isPlayingPopup() {
        return mPopupManager != null;
    }

    @MainThread
    public void showPopup() {
        if (mPopupManager == null)
            mPopupManager = new PopupManager(this);
        mPopupManager.showPopup();
    }

    public void setVideoTrackEnabled(boolean enabled) {
        if (!hasMedia() || !isPlaying())
            return;
        if (enabled)
            getCurrentMedia().addFlags(MediaWrapper.MEDIA_VIDEO);
        else
            getCurrentMedia().removeFlags(MediaWrapper.MEDIA_VIDEO);
        mMediaPlayer.setVideoTrackEnabled(enabled);
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
    public void insertItem(int position, MediaWrapper mw) {
        mMediaList.insert(position, mw);
        saveMediaList();
        determinePrevAndNextIndices();
    }


    @MainThread
    public void remove(int position) {
        mMediaList.remove(position);
        saveMediaList();
        determinePrevAndNextIndices();
    }

    @MainThread
    public void removeLocation(String location) {
        mMediaList.remove(location);
        saveMediaList();
        determinePrevAndNextIndices();
    }

    public int getMediaListSize() {
        return mMediaList.size();
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
        if (mSeekable)
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
        if (mSeekable)
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
        return mMediaPlayer.addSlave(Media.Slave.Type.Subtitle, path);
    }

    @MainThread
    public boolean addSubtitleTrack(Uri uri) {
        return mMediaPlayer.addSlave(Media.Slave.Type.Subtitle, uri);
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
            for (int i = ml.getCount() - 1; i >= 0; --i) {
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
                if (!mBound)
                    return;

                final PlaybackService service = PlaybackService.getService(iBinder);
                if (service != null)
                    mCallback.onConnected(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mBound = false;
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

    int mPhoneEvents = PhoneStateListener.LISTEN_CALL_STATE;
    PhoneStateListener mPhoneStateListener;

    private void initPhoneListener() {
        mPhoneStateListener = new PhoneStateListener(){
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (!mMediaPlayer.isPlaying() || !hasCurrentMedia())
                    return;
                if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK)
                    pause();
                else if (state == TelephonyManager.CALL_STATE_IDLE)
                    play();
            }
        };
    }
}
