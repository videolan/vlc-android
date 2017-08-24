/*****************************************************************************
 * PlaybackService.java
 *****************************************************************************
 * Copyright © 2011-2017 VLC authors and VideoLAN
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
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.SearchAggregate;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapUtil;
import org.videolan.vlc.gui.helpers.NotificationHelper;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.video.PopupManager;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.BrowserProvider;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.MediaWrapperList;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;
import org.videolan.vlc.util.VoiceSearchParams;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.VLCAppWidgetProvider;
import org.videolan.vlc.widget.VLCAppWidgetProviderBlack;
import org.videolan.vlc.widget.VLCAppWidgetProviderWhite;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.R.attr.width;

public class PlaybackService extends MediaBrowserServiceCompat implements IVLCVout.Callback {

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
    public static final String ACTION_PLAY_FROM_SEARCH = ACTION_REMOTE_GENERIC+"play_from_search";

    public static final String EXTRA_SEARCH_BUNDLE = ACTION_REMOTE_GENERIC+"extra_search_bundle";

    private static final int DELAY_DOUBLE_CLICK = 800;
    private static final int DELAY_LONG_CLICK = 1000;
    public static final String AUDIO_REPEAT_MODE_KEY = "audio_repeat_mode";

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

    private KeyguardManager mKeyguardManager;
    private SharedPreferences mSettings;
    private final IBinder mBinder = new LocalBinder();
    private final MediaWrapperList mMediaList = new MediaWrapperList();
    private Medialibrary mMedialibrary;
    private MediaPlayer mMediaPlayer;
    private boolean mParsed = false;
    private boolean mSeekable = false;
    private boolean mPausable = false;
    private boolean mSwitchingToVideo = false;
    private boolean mVideoBackground = false;

    private final ArrayList<Callback> mCallbacks = new ArrayList<>();
    private boolean mDetectHeadset = true;
    private PowerManager.WakeLock mWakeLock;
    private final AtomicBoolean mExpanding = new AtomicBoolean(false);
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mUpdateMeta = new AtomicBoolean(false);

    // Index management
    /**
     * Stack of previously played indexes, used in shuffle mode
     */
    private final Stack<Integer> mPrevious = new Stack<>();
    private int mCurrentIndex = -1; // Set to -1 if no media is currently loaded
    private int mPrevIndex = -1; // Set to -1 if no previous media
    private int mNextIndex = -1; // Set to -1 if no next media

    // Playback management

    private MediaSessionCompat mMediaSession;
    protected MediaSessionCallback mSessionCallback;
    private static final long PLAYBACK_BASE_ACTIONS = PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID | PlaybackStateCompat.ACTION_PLAY_FROM_URI
            | PlaybackStateCompat.ACTION_PLAY_PAUSE;

    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;
    private boolean mHasWidget;
    private boolean mShuffling = false;
    private int mRepeating = REPEAT_NONE;
    private Random mRandom = null; // Used in shuffling process
    private long mSavedTime = 0L;
    private boolean mHasAudioFocus = false;
    // RemoteControlClient-related
    /**
     * RemoteControlClient is for lock screen playback control.
     */
    private RemoteControlClientReceiver mRemoteControlClientReceiver = null;
    /**
     * Last widget position update timestamp
     */
    private long mWidgetPositionTimestamp = System.currentTimeMillis();
    private PopupManager mPopupManager;

    /* boolean indicating if the player is in benchmark mode */
    private boolean mIsBenchmark = false;
    /* boolenan indication if the player is in hardware mode */
    private boolean mIsHardware = false;

    private static LibVLC LibVLC() {
        return VLCInstance.get();
    }

    private MediaPlayer newMediaPlayer() {
        final MediaPlayer mp = new MediaPlayer(LibVLC());
        final String aout = VLCOptions.getAout(mSettings);
        if (aout != null)
            mp.setAudioOutput(aout);
        mp.getVLCVout().addCallback(this);

        return mp;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        hideNotification();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mMediaPlayer = newMediaPlayer();
        mMediaPlayer.setEqualizer(VLCOptions.getEqualizerSetFromSettings(this));

        if (!VLCInstance.testCompatibleCPU(this)) {
            stopSelf();
            return;
        }

        mMedialibrary = VLCApplication.getMLInstance();
        if (!AndroidDevices.hasTsp() && !AndroidDevices.hasPlayServices())
            AndroidDevices.setRemoteControlReceiverEnabled(true);

        mDetectHeadset = mSettings.getBoolean("enable_headset_detection", true);

        // Make sure the audio player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stop.
        PowerManager pm = (PowerManager) VLCApplication.getAppContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        updateHasWidget();
        initMediaSession();

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
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_ENABLED);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_DISABLED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);

        boolean stealRemoteControl = mSettings.getBoolean("enable_steal_remote_control", false);

        if (stealRemoteControl) {
            /* Backward compatibility for API 7 */
            filter = new IntentFilter();
            if (stealRemoteControl)
                filter.setPriority(Integer.MAX_VALUE);
            filter.addAction(Intent.ACTION_MEDIA_BUTTON);
            mRemoteControlClientReceiver = new RemoteControlClientReceiver();
            registerReceiver(mRemoteControlClientReceiver, filter);
        }
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    }

    private void updateHasWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        mHasWidget = manager.getAppWidgetIds(new ComponentName(this, VLCAppWidgetProviderWhite.class)).length != 0
                || manager.getAppWidgetIds(new ComponentName(this, VLCAppWidgetProviderBlack.class)).length != 0;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        String action = intent.getAction();
        if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
            MediaButtonReceiver.handleIntent(mMediaSession, intent);
            return START_STICKY;
        }
        if (ACTION_REMOTE_PLAYPAUSE.equals(action)) {
            if (hasCurrentMedia())
                return super.onStartCommand(intent, flags, startId);
            else
                loadLastAudioPlaylist();
        } else if (ACTION_REMOTE_PLAY.equals(action)) {
            if (hasCurrentMedia())
                play();
            else
                loadLastAudioPlaylist();
        } else if (ACTION_PLAY_FROM_SEARCH.equals(action)) {
            if (mMediaSession == null)
                initMediaSession();
            Bundle extras = intent.getBundleExtra(EXTRA_SEARCH_BUNDLE);
            mMediaSession.getController().getTransportControls()
                    .playFromSearch(extras.getString(SearchManager.QUERY), extras);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }

        if (!AndroidDevices.hasTsp() && !AndroidDevices.hasPlayServices())
            AndroidDevices.setRemoteControlReceiverEnabled(false);

        if (mWakeLock.isHeld())
            mWakeLock.release();
        unregisterReceiver(mReceiver);
        if (mRemoteControlClientReceiver != null) {
            unregisterReceiver(mRemoteControlClientReceiver);
            mRemoteControlClientReceiver = null;
        }
        mMediaPlayer.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return SERVICE_INTERFACE.equals(intent.getAction()) ? super.onBind(intent) : mBinder;
    }

    public IVLCVout getVLCVout()  {
        return mMediaPlayer.getVLCVout();
    }

    private final OnAudioFocusChangeListener mAudioFocusListener = createOnAudioFocusChangeListener();

    private OnAudioFocusChangeListener createOnAudioFocusChangeListener() {
        return new OnAudioFocusChangeListener() {
            private boolean mLossTransient = false;
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
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.i(TAG, "AUDIOFOCUS_GAIN: ");
                        // Resume playback
                        if (mLossTransient) {
                            if (wasPlaying && mSettings.getBoolean("resume_playback", true))
                                mMediaPlayer.play();
                            mLossTransient = false;
                        }
                        break;
                }
            }
        };
    }

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
                am.abandonAudioFocus(mAudioFocusListener);
                am.setParameters("bgm_state=false");
                mHasAudioFocus = false;
            }
        }
    }

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
                    loadLastAudioPlaylist();
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
                previous(false);
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_STOP) ||
                    action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
                stop();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_FORWARD)) {
                next();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_LAST_PLAYLIST)) {
                loadLastAudioPlaylist();
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
            } else if (action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_ENABLED)
                    || action.equalsIgnoreCase(VLCAppWidgetProvider.ACTION_WIDGET_DISABLED)) {
               updateHasWidget();
            }
            /*
             * headset plug events
             */
            else if (mDetectHeadset) {
                if (action.equalsIgnoreCase(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    Log.i(TAG, "Becoming noisy");
                    wasPlaying = isPlaying();
                    if (wasPlaying && hasCurrentMedia())
                        pause();
                } else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.i(TAG, "Headset Inserted.");
                    if (wasPlaying && hasCurrentMedia() && mSettings.getBoolean("enable_play_on_headset_insertion", false))
                        play();
                }
            }
        }
    };

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
        hideNotification();
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
        mSwitchingToVideo = false;
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
                synchronized (mCallbacks) {
                    for (Callback callback : mCallbacks)
                        callback.onMediaEvent(event);
                }
                if (mParsed && mMediaSession != null)
                    showNotification();
            }
        }
    };

    public void setBenchmark() { mIsBenchmark = true; }
    public void setHardware() { mIsHardware = true; }

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

    private Media.Stats previousMediaStats = null;

    public Media.Stats getLastStats() {
       return previousMediaStats;
    }

    private final MediaPlayer.EventListener mMediaPlayerListener = new MediaPlayer.EventListener() {

        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    loadMediaMeta();
                    if (mSavedTime > 0L)
                        seek(mSavedTime);
                    mSavedTime = 0L;

                    Log.i(TAG, "MediaPlayer.Event.Playing");
                    executeUpdate();
                    publishState();
                    executeUpdateProgress();
                    mHandler.sendEmptyMessage(SHOW_PROGRESS);
                    changeAudioFocus(true);
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    if (!mKeyguardManager.inKeyguardRestrictedInputMode() && !mVideoBackground && switchToVideo()) {
                        hideNotification();
                    } else {
                        showPlayer();
                        showNotification();
                    }
                    mVideoBackground = false;
                    if (getCurrentMediaWrapper().getType() == MediaWrapper.TYPE_STREAM)
                        mMedialibrary.addToHistory(getCurrentMediaLocation(), getCurrentMediaWrapper().getTitle());
                    break;
                case MediaPlayer.Event.Paused:
                    Log.i(TAG, "MediaPlayer.Event.Paused");
                    executeUpdate();
                    publishState();
                    executeUpdateProgress();
                    showNotification();
                    mHandler.removeMessages(SHOW_PROGRESS);
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.Stopped:
                    Log.i(TAG, "MediaPlayer.Event.Stopped");
                    saveMediaMeta();
                    executeUpdate();
                    publishState();
                    executeUpdateProgress();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EndReached:
                    saveMediaMeta();
                    executeUpdateProgress();
                    previousMediaStats = mMediaPlayer.getMedia().getStats();
                    determinePrevAndNextIndices(true);
                    if (mNextIndex != -1) {
                        next();
                    } else {
                        if (mWakeLock.isHeld())
                            mWakeLock.release();
                        changeAudioFocus(false);
                        executeUpdate();
                        publishState();
                    }
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
                case MediaPlayer.Event.MediaChanged:
                    Log.d(TAG, "onEvent: MediaChanged");
            }
            synchronized (mCallbacks) {
                for (Callback callback : mCallbacks)
                    callback.onMediaPlayerEvent(event);
            }
        }
    };

    private void showPlayer() {
        sendBroadcast(new Intent(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER));
    }

    public void saveMediaMeta() {
        MediaWrapper media = mMedialibrary.findMedia(getCurrentMediaWrapper());
        if (media == null || media.getId() == 0)
            return;
        boolean canSwitchToVideo = canSwitchToVideo();
        if (canSwitchToVideo || media.isPodcast()) {
            //Save progress
            long time = getTime();
            float progress = time / (float)media.getLength();
            if (progress > 0.90f) {
                //increase seen counter if more than 90% of the media have been seen
                //and reset progress to 0
                long incSeen = media.getSeen() + 1L;
                media.setLongMeta(MediaWrapper.META_SEEN, incSeen);
                media.setSeen(incSeen);
                progress = 0f;
            }
            media.setTime(progress == 0f ? 0L : time);
            media.setLongMeta(MediaWrapper.META_PROGRESS, (long) (progress*100));
        }
        if (canSwitchToVideo) {
            //Save audio delay
            if (mSettings.getBoolean("save_individual_audio_delay", false))
                media.setLongMeta(MediaWrapper.META_AUDIODELAY, mMediaPlayer.getAudioDelay());
            media.setLongMeta(MediaWrapper.META_SUBTITLE_DELAY, mMediaPlayer.getSpuDelay());
            media.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, mMediaPlayer.getSpuTrack());
        }
    }

    private void loadMediaMeta() {
        MediaWrapper media = mMedialibrary.findMedia(getCurrentMediaWrapper());
        if (media == null || media.getId() == 0)
            return;
        if (canSwitchToVideo()) {
            if (mSettings.getBoolean("save_individual_audio_delay", false))
                mMediaPlayer.setAudioDelay(media.getMetaLong(MediaWrapper.META_AUDIODELAY));
            mMediaPlayer.setSpuTrack((int) media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK));
            mMediaPlayer.setSpuDelay(media.getMetaLong(MediaWrapper.META_SUBTITLE_DELAY));
        }
    }

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
        MediaWrapper media = mMediaList.getMedia(mCurrentIndex);
        if (media == null || media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !canSwitchToVideo())
            return false;
        mVideoBackground = false;
        if (isVideoPlaying()) {//Player is already running, just send it an intent
            setVideoTrackEnabled(true);
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    VideoPlayerActivity.getIntent(VideoPlayerActivity.PLAY_FROM_SERVICE,
                            media, false, mCurrentIndex));
        } else if (!mSwitchingToVideo) {//Start the video player
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                    media.getUri(), mCurrentIndex);
            mSwitchingToVideo = true;
        }
        return true;
    }

    private void executeUpdate() {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.update();
            }
        }
        updateWidget();
        updateMetadata();
        broadcastMetadata();
    }

    private void executeUpdateProgress() {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.updateProgress();
            }
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
        return isValidIndex(mCurrentIndex);
    }

    private final Handler mHandler = new AudioServiceHandler(this);

    private static class AudioServiceHandler extends WeakHandler<PlaybackService> {
        public AudioServiceHandler(PlaybackService fragment) {
            super(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            PlaybackService service = getOwner();
            if (service == null)
                return;

            switch (msg.what) {
                case SHOW_PROGRESS:
                    synchronized (service.mCallbacks) {
                        if (service.mCallbacks.size() > 0) {
                            removeMessages(SHOW_PROGRESS);
                            service.executeUpdateProgress();
                            sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
                        }
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
        if (VLCApplication.showTvUi())
            return;
        if (mMediaPlayer.getVLCVout().areViewsAttached()) {
            hideNotification();
            return;
        }
        final MediaWrapper mw = getCurrentMedia();
        if (mw != null) {
            final boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
            final boolean playing = mMediaPlayer.isPlaying();
            final MediaSessionCompat.Token sessionToken = mMediaSession.getSessionToken();
            final Context ctx = this;
            mExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Bitmap cover;
                        String title, artist, album;
                        synchronized (mUpdateMeta) {
                            try {
                                while (mUpdateMeta.get())
                                    mUpdateMeta.wait();
                            } catch (InterruptedException ignored) {}
                            final MediaMetadataCompat metaData = mMediaSession.getController().getMetadata();
                            title = metaData.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                            artist = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
                            album = metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
                            cover = coverOnLockscreen ?
                                    metaData.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) :
                                    AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), width);
                        }
                        if (cover == null || cover.isRecycled())
                            cover = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_no_media);

                        Notification notification = NotificationHelper.createPlaybackNotification(PlaybackService.this,
                                mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO), title, artist, album,
                                cover, playing, sessionToken, getSessionPendingIntent());
                        if (!AndroidUtil.isLolliPopOrLater || playing)
                            PlaybackService.this.startForeground(3, notification);
                        else {
                            PlaybackService.this.stopForeground(false);
                            NotificationManagerCompat.from(ctx).notify(3, notification);
                        }
                    } catch (IllegalArgumentException e){
                        // On somme crappy firmwares, shit can happen
                        Log.e(TAG, "Failed to display notification", e);
                    }
                }
            });
        }
    }

    private PendingIntent getSessionPendingIntent() {
        if (mMediaPlayer.getVLCVout().areViewsAttached()) { //PIP
            final Intent notificationIntent = new Intent(this, VideoPlayerActivity.class);
            return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } if (mVideoBackground || (canSwitchToVideo() && !mMediaList.getMedia(mCurrentIndex).hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO))) { //resume video playback
            /* Resume VideoPlayerActivity from ACTION_REMOTE_SWITCH_VIDEO intent */
            final Intent notificationIntent = new Intent(ACTION_REMOTE_SWITCH_VIDEO);
            return PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            /* Show audio player */
            final Intent notificationIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (notificationIntent != null) {
                notificationIntent.setAction(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER);
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            }
            return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private void hideNotification() {
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                PlaybackService.this.stopForeground(true);
                NotificationManagerCompat.from(PlaybackService.this).cancel(3);
            }
        });
    }

    @MainThread
    public void pause() {
        if (mPausable) {
            savePosition();
            mMediaPlayer.pause();
        }
    }

    @MainThread
    public void play() {
        if (hasCurrentMedia())
            mMediaPlayer.play();
    }

    @MainThread
    public void stop() {
        removePopup();
        if (mMediaPlayer == null)
            return;
        savePosition();
        final Media media = mMediaPlayer.getMedia();
        if (media != null) {
            saveMediaMeta();
            media.setEventListener(null);
            mMediaPlayer.setEventListener(null);
            final MediaPlayer mp = mMediaPlayer;
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    mp.stop();
                    mp.setMedia(null);
                }
            });
            media.release();
            publishState();
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

    private void determinePrevAndNextIndices() {
        determinePrevAndNextIndices(false);
    }

    private void determinePrevAndNextIndices(boolean expand) {
        if (expand) {
            mExpanding.set(true);
            mNextIndex = expand(getCurrentMedia().getType() == MediaWrapper.TYPE_STREAM);
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
                    if(!mPrevious.isEmpty()){
                        mPrevIndex = mPrevious.peek();
                        while (!isValidIndex(mPrevIndex)) {
                            mPrevious.remove(mPrevious.size() - 1);
                            if (mPrevious.isEmpty()) {
                                mPrevIndex = -1;
                                break;
                            }
                            mPrevIndex = mPrevious.peek();
                        }
                    }
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

    private boolean isValidIndex(int position) {
        return position >= 0 && position < mMediaList.size();
    }

    private void initMediaSession() {
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);

        mediaButtonIntent.setClass(this, RemoteControlClientReceiver.class);
        PendingIntent mbrIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
        ComponentName mbrName = new ComponentName(this, RemoteControlClientReceiver.class);

        mSessionCallback = new MediaSessionCallback();
        mMediaSession = new MediaSessionCompat(this, "VLC", mbrName, mbrIntent);
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
        setSessionToken(mMediaSession.getSessionToken());
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {
    private long mHeadsetDownTime = 0;
    private long mHeadsetUpTime = 0;

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && !isVideoPlaying()) {
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                        || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    long time = SystemClock.uptimeMillis();
                    switch (event.getAction()) {
                        case KeyEvent.ACTION_DOWN:
                            if (event.getRepeatCount() <= 0)
                                mHeadsetDownTime = time;
                            if (!hasMedia()) {
                                loadLastAudioPlaylist();
                                return true;
                            }
                            break;
                        case KeyEvent.ACTION_UP:
                            if (AndroidDevices.hasTsp()) { //no backward/forward on TV
                                if (time - mHeadsetDownTime >= DELAY_LONG_CLICK) { // long click
                                    mHeadsetUpTime = time;
                                    previous(false);
                                    return true;
                                } else if (time - mHeadsetUpTime <= DELAY_DOUBLE_CLICK) { // double click
                                    mHeadsetUpTime = time;
                                    next();
                                    return true;
                                } else {
                                    mHeadsetUpTime = time;
                                    return false;
                                }
                            }
                            break;
                    }
                    return false;
                } else if (!AndroidUtil.isLolliPopOrLater) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            onSkipToNext();
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            onSkipToPrevious();
                            return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void onPlay() {
            if (hasMedia())
                play();
            else
                loadLastAudioPlaylist();
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            if (TextUtils.equals(action, "shuffle")) {
                shuffle();
            } else if (TextUtils.equals(action, "repeat")) {
                switch (getRepeatType()) {
                    case PlaybackService.REPEAT_NONE:
                        setRepeatType(PlaybackService.REPEAT_ALL);
                        break;
                    case PlaybackService.REPEAT_ALL:
                        setRepeatType(PlaybackService.REPEAT_ONE);
                        break;
                    default:
                    case PlaybackService.REPEAT_ONE:
                        setRepeatType(PlaybackService.REPEAT_NONE);
                        break;
                }
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            if (mediaId.startsWith(BrowserProvider.ALBUM_PREFIX)) {
                load(mMedialibrary.getAlbum(Long.parseLong(mediaId.split("_")[1])).getTracks(), 0);
            } else if (mediaId.startsWith(BrowserProvider.PLAYLIST_PREFIX)) {
                load(mMedialibrary.getPlaylist(Long.parseLong(mediaId.split("_")[1])).getTracks(), 0);
            } else
                try {
                    load(mMedialibrary.getMedia(Long.parseLong(mediaId)));
                } catch (NumberFormatException e) {
                    loadLocation(mediaId);
                }
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            loadUri(uri);
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            if (!mMedialibrary.isInitiated()) {
                startService(new Intent(MediaParsingService.ACTION_INIT, null, PlaybackService.this, MediaParsingService.class));
                final BroadcastReceiver libraryReadyReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        LocalBroadcastManager.getInstance(PlaybackService.this).unregisterReceiver(this);
                        onPlayFromSearch(query, extras);
                    }
                };
                LocalBroadcastManager.getInstance(PlaybackService.this).registerReceiver(libraryReadyReceiver, new IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY));
                return;
            }
            mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_CONNECTING, getTime(), 1.0f).build());
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    VoiceSearchParams vsp = new VoiceSearchParams(query, extras);
                    MediaLibraryItem[] items = null;
                    MediaWrapper[] tracks = null;
                    if (vsp.isAny) {
                        items = mMedialibrary.getAudio();
                        if (!isShuffling())
                            shuffle();
                    } else if (vsp.isArtistFocus) {
                        items = mMedialibrary.searchArtist(vsp.artist);
                    } else if (vsp.isAlbumFocus) {
                        items = mMedialibrary.searchAlbum(vsp.album);
                    } else if (vsp.isGenreFocus) {
                        items = mMedialibrary.searchGenre(vsp.genre);
                    } else if (vsp.isSongFocus) {
                        tracks = mMedialibrary.searchMedia(vsp.song).getTracks();
                    }
                    if (Tools.isArrayEmpty(tracks)){
                        SearchAggregate result = mMedialibrary.search(query);
                        if (!Tools.isArrayEmpty(result.getAlbums()))
                            tracks = result.getAlbums()[0].getTracks();
                        else if (!Tools.isArrayEmpty(result.getArtists()))
                            tracks = result.getArtists()[0].getTracks();
                        else if (!Tools.isArrayEmpty(result.getGenres()))
                            tracks = result.getGenres()[0].getTracks();
                    }
                    if (tracks == null && !Tools.isArrayEmpty(items))
                        tracks = items[0].getTracks();
                    if (!Tools.isArrayEmpty(tracks))
                        load(tracks, 0);
                }
            });
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
            previous(false);
        }

        @Override
        public void onSeekTo(long pos) {
            seek(pos);
        }

        @Override
        public void onFastForward() {
            seek(Math.min(getLength(), getTime()+5000));
        }

        @Override
        public void onRewind() {
            seek(Math.max(0, getTime()-5000));
        }

        @Override
        public void onSkipToQueueItem(long id) {
            playIndex((int) id);
        }
    }

    protected void updateMetadata() {
        final MediaWrapper media = getCurrentMedia();
        if (media == null)
            return;
        if (mMediaSession == null)
            initMediaSession();
        final Context ctx = this;
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (mUpdateMeta) {
                    mUpdateMeta.set(true);
                }
                String title = media.getNowPlaying();
                if (title == null)
                    title = media.getTitle();
                boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
                final MediaMetadataCompat.Builder bob = new MediaMetadataCompat.Builder();
                bob.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, BrowserProvider.generateMediaId(media))
                        .putString(MediaMetadataCompat.METADATA_KEY_GENRE, MediaUtils.getMediaGenre(ctx, media))
                        .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, media.getTrackNumber())
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, MediaUtils.getMediaArtist(ctx, media))
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, MediaUtils.getMediaReferenceArtist(ctx, media))
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, MediaUtils.getMediaAlbum(ctx, media))
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.getLength());
                if (coverOnLockscreen) {
                    Bitmap cover = AudioUtil.readCoverBitmap(Uri.decode(media.getArtworkMrl()), 512);
                    if (cover != null && cover.getConfig() != null) //In case of format not supported
                        bob.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover.copy(cover.getConfig(), false));
                }
                bob.putLong("shuffle", 1L);
                bob.putLong("repeat", getRepeatType());
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mMediaSession.setMetadata(bob.build());
                        synchronized (mUpdateMeta) {
                            mUpdateMeta.set(false);
                            mUpdateMeta.notify();
                        }
                    }
                });
            }
        });
    }

    protected void publishState() {
        if (mMediaSession == null)
            return;
        PlaybackStateCompat.Builder pscb = new PlaybackStateCompat.Builder();
        long actions = PLAYBACK_BASE_ACTIONS;
        if (isPlaying()) {
            actions |= PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_STOP;
            pscb.setState(PlaybackStateCompat.STATE_PLAYING, getTime(), getRate());
        } else if (hasMedia()) {
            actions |= PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP;
            pscb.setState(PlaybackStateCompat.STATE_PAUSED, getTime(), getRate());
        } else {
            actions |= PlaybackStateCompat.ACTION_PLAY;
            pscb.setState(PlaybackStateCompat.STATE_STOPPED, getTime(), getRate());
        }
        if (mRepeating != REPEAT_NONE || hasNext())
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        if (mRepeating != REPEAT_NONE || hasPrevious() || isSeekable())
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        if (isSeekable())
            actions |= PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND;
        actions |= PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
        pscb.setActions(actions);
        int repeatResId = getRepeatType() == REPEAT_ALL ? R.drawable.ic_auto_repeat_pressed : getRepeatType() == REPEAT_ONE ? R.drawable.ic_auto_repeat_one_pressed : R.drawable.ic_auto_repeat_normal;
        if (mMediaList.size() > 2)
            pscb.addCustomAction("shuffle", getString(R.string.shuffle_title), isShuffling() ? R.drawable.ic_auto_shuffle_pressed : R.drawable.ic_auto_shuffle_normal);
        pscb.addCustomAction("repeat", getString(R.string.repeat_title), repeatResId);
        mMediaSession.setPlaybackState(pscb.build());
        mMediaSession.setActive(hasMedia());
        mMediaSession.setQueueTitle(getString(R.string.music_now_playing));
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
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
    }

    @MainThread
    public void previous(boolean force) {
        if (hasPrevious() && mCurrentIndex > 0 &&
                (force || !mMediaPlayer.isSeekable() || mMediaPlayer.getTime() < 2000l)) {
            int size = mMediaList.size();
            mCurrentIndex = mPrevIndex;
            if (mPrevious.size() > 0)
                mPrevious.pop();
            if (size == 0 || mPrevIndex < 0 || mCurrentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !");
                stop();
                return;
            }
            playIndex(mCurrentIndex, 0);
            saveCurrentMedia();
        } else
            setPosition(0f);
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
    }

    @MainThread
    public void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
        savePosition();
        determinePrevAndNextIndices();
        publishState();
    }

    @MainThread
    public void setRepeatType(int repeatType) {
        mRepeating = repeatType;
        if (mMediaList.isAudioList() && mSettings.getBoolean("audio_save_repeat", false))
            mSettings.edit().putInt(AUDIO_REPEAT_MODE_KEY, mRepeating).apply();
        savePosition();
        determinePrevAndNextIndices();
        publishState();
    }

    private void updateWidget() {
        if (mHasWidget && !isVideoPlaying()) {
            updateWidgetState();
            updateWidgetCover();
        }
    }

    private void updateWidgetState() {
        final MediaWrapper media = getCurrentMedia();
        final Intent widgetIntent = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE);
        if (hasCurrentMedia()) {
            widgetIntent.putExtra("title", media.getTitle());
            widgetIntent.putExtra("artist", media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : MediaUtils.getMediaArtist(PlaybackService.this, media));
        } else {
            widgetIntent.putExtra("title", getString(R.string.widget_default_text));
            widgetIntent.putExtra("artist", "");
        }
        widgetIntent.putExtra("isplaying", isPlaying());
        sendBroadcast(widgetIntent);
    }

    private String mCurrentWidgetCover = null;
    private void updateWidgetCover() {
    String newWidgetCover = hasCurrentMedia() ? getCurrentMedia().getArtworkMrl() : null;
        if (!TextUtils.equals(mCurrentWidgetCover, newWidgetCover)) {
            mCurrentWidgetCover = newWidgetCover;
            sendBroadcast(new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_COVER)
                            .putExtra("artworkMrl", newWidgetCover));
        }
    }

    private void updateWidgetPosition(final float pos) {
        if (!mHasWidget || isVideoPlaying())
            return;
        // no more than one widget mUpdateMeta for each 1/50 of the song
        long timestamp = System.currentTimeMillis();
        if (!hasCurrentMedia() || timestamp - mWidgetPositionTimestamp < getCurrentMedia().getLength() / 50)
            return;
        mWidgetPositionTimestamp = timestamp;
        sendBroadcast(new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_POSITION)
                .putExtra("position", pos));
    }

    private void broadcastMetadata() {
        final MediaWrapper media = getCurrentMedia();
        if (media == null || isVideoPlaying())
            return;
        sendBroadcast(new Intent("com.android.music.metachanged")
                .putExtra("track", media.getTitle())
                .putExtra("artist", media.getArtist())
                .putExtra("album", media.getAlbum())
                .putExtra("duration", media.getLength())
                .putExtra("playing", mMediaPlayer.isPlaying())
                .putExtra("package", "org.videolan.vlc"));
    }

    BroadcastReceiver mLibraryReceiver = null;
    private void loadLastAudioPlaylist() {
        if (mLibraryReceiver != null)
            return;
        if (mMedialibrary.isInitiated())
            loadLastPlaylist(TYPE_AUDIO);
        else {
            final LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
            mLibraryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    lbm.unregisterReceiver(this);
                    mLibraryReceiver = null;
                    loadLastPlaylist(TYPE_AUDIO);
                }
            };
            lbm.registerReceiver(mLibraryReceiver, new IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY));
            startService(new Intent(MediaParsingService.ACTION_INIT, null, this, MediaParsingService.class));
        }
    }

    public void loadLastPlaylist(final int type) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final boolean audio = type == TYPE_AUDIO;
                String[] locations;
                synchronized (PlaybackService.this) {
                    String currentMedia = mSettings.getString(audio ? "current_song" : "current_media", "");
                    if (currentMedia.equals(""))
                        return;
                    locations = mSettings.getString(audio ? "audio_list" : "media_list", "").split(" ");
                }
                if (locations.length == 0)
                    return;

                final List<MediaWrapper> playList = new ArrayList<>(locations.length);
                for (String location : locations) {
                    String mrl = Uri.decode(location);
                    MediaWrapper mw = mMedialibrary.getMedia(mrl);
                    if (mw == null)
                        mw = new MediaWrapper(Uri.parse(mrl));
                    playList.add(mw);
                }
                // load playlist
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        final int position;
                        synchronized (PlaybackService.this) {
                            mShuffling = mSettings.getBoolean(audio ? "audio_shuffling" : "media_shuffling", false);
                            mRepeating = mSettings.getInt(audio ? "audio_repeating" : "media_repeating", REPEAT_NONE);
                            position = mSettings.getInt(audio ? "position_in_audio_list" : "position_in_media_list", 0);
                            mSavedTime = mSettings.getLong(audio ? "position_in_song" : "position_in_media", -1);
                        }
                        if (!audio) {
                            if (position < playList.size()) {
                                boolean paused = mSettings.getBoolean(PreferencesActivity.VIDEO_PAUSED, false);
                                if (paused)
                                    playList.get(position).addFlags(MediaWrapper.MEDIA_PAUSED);
                            }
                            float rate = mSettings.getFloat(PreferencesActivity.VIDEO_SPEED, getRate());
                            if (rate != 1.0f)
                                setRate(rate, false);
                        }
                        load(playList, position);
                    }
                });
            }
        });
    }

    private synchronized void saveCurrentMedia() {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(mMediaList.isAudioList() ? "current_song" : "current_media", mMediaList.getMRL(Math.max(mCurrentIndex, 0)));
        editor.apply();
    }

    private synchronized void saveMediaList() {
        if (getCurrentMedia() == null)
            return;
        StringBuilder locations = new StringBuilder();
        for (int i = 0; i < mMediaList.size(); i++)
            locations.append(" ").append(Uri.encode(mMediaList.getMRL(i)));
        //We save a concatenated String because putStringSet is APIv11.
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(canSwitchToVideo() || !mMediaList.isAudioList() ? "media_list" : "audio_list", locations.toString().trim());
        editor.apply();
    }

    private synchronized void savePosition(){
        if (!hasMedia())
            return;
        SharedPreferences.Editor editor = mSettings.edit();
        boolean audio = mMediaList.isAudioList();
        editor.putBoolean(audio ? "audio_shuffling" : "media_shuffling", mShuffling);
        editor.putInt(audio ? "audio_repeating" : "media_repeating", mRepeating);
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", mCurrentIndex);
        editor.putLong(audio ? "position_in_song" : "position_in_media", mMediaPlayer.getTime());
        if(!audio) {
            editor.putBoolean(PreferencesActivity.VIDEO_PAUSED, !isPlaying());
            editor.putFloat(PreferencesActivity.VIDEO_SPEED, getRate());
        }
        editor.apply();
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
        if (isValidIndex(mPrevIndex))
            return mMediaList.getMedia(mPrevIndex).getTitle();
        else
            return null;
    }

    @MainThread
    public String getTitleNext() {
        if (isValidIndex(mNextIndex))
            return mMediaList.getMedia(mNextIndex).getTitle();
        else
            return null;
    }

    @MainThread
    public String getCoverArt() {
        return getCurrentMedia().getArtworkMrl();
    }

    @MainThread
    public String getPrevCoverArt() {
        if (isValidIndex(mPrevIndex))
            return mMediaList.getMedia(mPrevIndex).getArtworkMrl();
        else
            return null;
    }

    @MainThread
    public String getNextCoverArt() {
        if (isValidIndex(mNextIndex))
            return mMediaList.getMedia(mNextIndex).getArtworkMrl();
        else
            return null;
    }

    @MainThread
    public void addCallback(Callback cb) {
        synchronized (mCallbacks) {
            if (!mCallbacks.contains(cb)) {
                mCallbacks.add(cb);
                if (hasCurrentMedia())
                    mHandler.sendEmptyMessage(SHOW_PROGRESS);
            }
        }
    }

    @MainThread
    public void removeCallback(Callback cb) {
        synchronized (mCallbacks) {
            mCallbacks.remove(cb);
        }
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
        ArrayList<MediaWrapper> mediaList = new ArrayList<>();

        for (int i = 0; i < mediaPathList.size(); i++) {
            String location = mediaPathList.get(i);
            MediaWrapper mediaWrapper = mMedialibrary.getMedia(location);
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
    public void load(MediaWrapper[] mediaList, int position) {
        load(Arrays.asList(mediaList), position);
    }

    @MainThread
    public void load(List<MediaWrapper> mediaList, int position) {
        Log.v(TAG, "Loading position " + ((Integer) position).toString() + " in " + mediaList.toString());

        if (hasCurrentMedia())
            savePosition();

        mMediaList.removeEventListener(mListEventListener);
        mMediaList.clear();

        mPrevious.clear();

        for (MediaWrapper media : mediaList)
            mMediaList.add(media);

        if (mMediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (isValidIndex(position)) {
            mCurrentIndex = position;
        } else {
            Log.w(TAG, "Warning: positon " + position + " out of bounds");
            mCurrentIndex = 0;
        }

        // Add handler after loading the list
        mMediaList.addEventListener(mListEventListener);

        if (mMediaList.isAudioList() && mSettings.getBoolean("audio_save_repeat", false))
            mRepeating = mSettings.getInt(AUDIO_REPEAT_MODE_KEY, REPEAT_NONE);
        playIndex(mCurrentIndex, 0);
        saveMediaList();
        onMediaChanged();
        updateMediaQueue();
    }

    private void updateMediaQueue() {
        LinkedList<MediaSessionCompat.QueueItem> queue = new LinkedList<>();
        long position = -1;
        for (MediaWrapper media : mMediaList.getAll()) {
            String title = media.getNowPlaying();
            if (title == null)
                title = media.getTitle();
                MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder();
                builder.setTitle(title)
                        .setDescription(Util.getMediaDescription(MediaUtils.getMediaArtist(this, media), MediaUtils.getMediaAlbum(this, media)))
                        .setIconBitmap(BitmapUtil.getPictureFromCache(media))
                        .setMediaUri(media.getUri())
                        .setMediaId(BrowserProvider.generateMediaId(media));
                queue.add(new MediaSessionCompat.QueueItem(builder.build(), ++position));
        }
        mMediaSession.setQueue(queue);
    }

    @MainThread
    public void load(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<>();
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
        if (isValidIndex(index)) {
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

        boolean isVideoPlaying = mw.getType() == MediaWrapper.TYPE_VIDEO && isVideoPlaying();
        if (!mVideoBackground && isVideoPlaying)
            mw.addFlags(MediaWrapper.MEDIA_VIDEO);

        if (mVideoBackground)
            mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);

        /* Pausable and seekable are true by default */
        mParsed = false;
        mSwitchingToVideo = false;
        mPausable = mSeekable = true;
        final Media media = new Media(VLCInstance.get(), FileUtils.getUri(mw.getUri()));
        VLCOptions.setMediaOptions(media, this, flags | mw.getFlags());

        /* keeping only video during benchmark */
        if (mIsBenchmark) {
            media.addOption(":no-audio");
            media.addOption(":no-spu");
            if (mIsHardware) {
                media.addOption(":codec=mediacodec_ndk,mediacodec_jni,none");
                mIsHardware = false;
            }
        }

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
                    mMediaPlayer.addSlave(slave.type, Uri.parse(slave.uri), false);
            }
        });

        media.setEventListener(mMediaListener);
        mMediaPlayer.setMedia(media);
        media.release();

        if (mw .getType() != MediaWrapper.TYPE_VIDEO || isVideoPlaying || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)) {
            mMediaPlayer.setEqualizer(VLCOptions.getEqualizerSetFromSettings(this));
            mMediaPlayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
            changeAudioFocus(true);
            mMediaPlayer.setEventListener(mMediaPlayerListener);
            if (!isVideoPlaying && mMediaPlayer.getRate() == 1.0F && mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, true))
                setRate(mSettings.getFloat(PreferencesActivity.KEY_AUDIO_PLAYBACK_RATE, 1.0F), true);
            if (mSavedTime <= 0L && mw.getTime() >= 0L && mw.isPodcast())
                mSavedTime = mw.getTime();
            mMediaPlayer.play();

            determinePrevAndNextIndices();
            mMediaSession.setSessionActivity(getSessionPendingIntent());
            if (mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true))
                VLCApplication.runBackground(new Runnable() {
                    @Override
                    public void run() {
                        long id = mw.getId();
                        if (id == 0L) {
                            MediaWrapper media = mMedialibrary.findMedia(mw);
                            if (media != null && media.getId() != 0L)
                                id = media.getId();
                            else {
                                media = mMedialibrary.addMedia(mw.getUri().toString());
                                if (media != null)
                                    id = media.getId();
                            }
                        }
                        mMedialibrary.increasePlayCount(id);
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

    @MainThread
    public void flush() {
        /* HACK: flush when activating a video track. This will force an
         * I-Frame to be displayed right away. */
        if (isSeekable()) {
            long time = getTime();
            if (time > 0 )
                seek(time);
        }
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
        saveMediaMeta();
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
        hideNotification();
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
    public void append(MediaWrapper[] mediaList) {
        append(Arrays.asList(mediaList));
    }

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
        updateMediaQueue();
    }

    @MainThread
    public void append(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<>();
        arrayList.add(media);
        append(arrayList);
    }

    /**
     * Insert into the current existing playlist
     */

    @MainThread
    public void insertNext(MediaWrapper[] mediaList) {
        insertNext(Arrays.asList(mediaList));
    }

    @MainThread
    public void insertNext(List<MediaWrapper> mediaList) {
        if (!hasCurrentMedia()) {
            load(mediaList, 0);
            return;
        }

        int startIndex = mCurrentIndex + 1;

        for (int i = 0; i < mediaList.size(); i++) {
            MediaWrapper mediaWrapper = mediaList.get(i);
            mMediaList.insert(startIndex + i, mediaWrapper);
        }
        onMediaListChanged();
        updateMediaQueue();
    }

    @MainThread
    public void insertNext(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<>();
        arrayList.add(media);
        insertNext(arrayList);
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
    public ArrayList<MediaWrapper> getMedias() {
        final ArrayList<MediaWrapper> ml = new ArrayList<>();
        for (int i = 0; i < mMediaList.size(); i++) {
            ml.add(mMediaList.getMedia(i));
        }
        return ml;
    }

    @MainThread
    public List<String> getMediaLocations() {
        ArrayList<String> medias = new ArrayList<>();
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
    public void setRate(float rate, boolean save) {
        mMediaPlayer.setRate(rate);
        if (save && mSettings.getBoolean(PreferencesActivity.KEY_AUDIO_PLAYBACK_SPEED_PERSIST, true))
            mSettings.edit().putFloat(PreferencesActivity.KEY_AUDIO_PLAYBACK_RATE, rate).apply();
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
    public void seek(long position) {
        seek(position, getLength());
    }

    @MainThread
    public void seek(long position, double length) {
        if (length > 0.0D)
            setPosition((float) (position/length));
        else
            setTime(position);
    }

    @MainThread
    public boolean updateViewpoint(float yaw, float pitch, float roll, float fov, boolean absolute) {
        return mMediaPlayer.updateViewpoint(yaw, pitch, roll, fov, absolute);
    }

    @MainThread
    public void saveTimeToSeek(long time) {
        mSavedTime = time;
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
    public MediaPlayer.TrackDescription[] getVideoTracks() {
        return mMediaPlayer.getVideoTracks();
    }

    @MainThread
    public Media.VideoTrack getCurrentVideoTrack() {
        return mMediaPlayer.getCurrentVideoTrack();
    }

    @MainThread
    public int getVideoTrack() {
        return mMediaPlayer.getVideoTrack();
    }

    @MainThread
    public boolean addSubtitleTrack(String path, boolean select) {
        return mMediaPlayer.addSlave(Media.Slave.Type.Subtitle, path, select);
    }

    @MainThread
    public boolean addSubtitleTrack(Uri uri,boolean select) {
        return mMediaPlayer.addSlave(Media.Slave.Type.Subtitle, uri, select);
    }

    @MainThread
    public boolean addSubtitleTrack(String path) {
        return addSubtitleTrack(path, false);
    }

    @MainThread
    public boolean addSubtitleTrack(Uri uri) {
        return addSubtitleTrack(uri, false);
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

    @MainThread
    public void setVideoScale(float scale) {
        mMediaPlayer.setScale(scale);
    }

    @MainThread
    public void setVideoAspectRatio(String aspect) {
        mMediaPlayer.setAspectRatio(aspect);
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    public int expand(boolean updateHistory) {
        final Media media = mMediaPlayer.getMedia();
        String mrl = updateHistory ? getCurrentMediaLocation() : null;
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
            if (updateHistory && ml.getCount() == 1)
                mMedialibrary.addToHistory(mrl, mMediaList.getMedia(mCurrentIndex).getTitle());
            ret = 0;
        } else {
            ret = -1;
        }
        ml.release();
        return ret;
    }

    public void restartMediaPlayer() {
        stop();
        final MediaPlayer mp = mMediaPlayer;
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                mp.release();
            }
        });
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

    /*
     * Browsing
     */

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return Permissions.canReadStorage() ? new BrowserRoot(BrowserProvider.ID_ROOT, null) : null;
    }

    boolean mMLInitializing = false;
    @Override
    public void onLoadChildren(@NonNull final String parentId, @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.detach();
        if (!mMLInitializing && !mMedialibrary.isInitiated() && BrowserProvider.ID_ROOT.equals(parentId)) {
            startService(new Intent(MediaParsingService.ACTION_INIT, null, this, MediaParsingService.class));
            mMLInitializing = true;
        }
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                result.sendResult(BrowserProvider.browse(parentId));
            }
        });
    }
}
