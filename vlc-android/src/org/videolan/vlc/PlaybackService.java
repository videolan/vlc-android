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
import android.media.audiofx.AudioEffect;
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
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.RendererItem;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.SearchAggregate;
import org.videolan.vlc.extensions.ExtensionsManager;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapUtil;
import org.videolan.vlc.gui.helpers.NotificationHelper;
import org.videolan.vlc.gui.video.PopupManager;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.media.BrowserProvider;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.media.PlaylistManager;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCOptions;
import org.videolan.vlc.util.VoiceSearchParams;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.VLCAppWidgetProvider;
import org.videolan.vlc.widget.VLCAppWidgetProviderBlack;
import org.videolan.vlc.widget.VLCAppWidgetProviderWhite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlaybackService extends MediaBrowserServiceCompat{

    private static final String TAG = "VLC/PlaybackService";

    private static final int SHOW_PROGRESS = 0;
    private static final int SHOW_TOAST = 1;
    private static final int END_MEDIASESSION = 2;

    private static final int DELAY_DOUBLE_CLICK = 800;
    private static final int DELAY_LONG_CLICK = 1000;

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
        final LocalBinder binder = (LocalBinder) iBinder;
        return binder.getService();
    }

    private PlaylistManager playlistManager;
    private KeyguardManager mKeyguardManager;
    private SharedPreferences mSettings;
    private final IBinder mBinder = new LocalBinder();
    private Medialibrary mMedialibrary;

    private final List<Callback> mCallbacks = new ArrayList<>();
    private boolean mDetectHeadset = true;
    private PowerManager.WakeLock mWakeLock;

    private static class ExecutorHolder {
        static final ExecutorService executorService = Executors.newSingleThreadExecutor();
        static final AtomicBoolean updateMeta = new AtomicBoolean(false);
    }

    // Playback management

    private MediaSessionCompat mMediaSession;
    protected MediaSessionCallback mSessionCallback;
    private static final long PLAYBACK_BASE_ACTIONS = PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID | PlaybackStateCompat.ACTION_PLAY_FROM_URI
            | PlaybackStateCompat.ACTION_PLAY_PAUSE;

    private int mWidget = 0;
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

    @Override
    public void onCreate() {
        super.onCreate();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        playlistManager = new PlaylistManager(this);
        if (!VLCInstance.testCompatibleCPU(this)) {
            stopSelf();
            return;
        }

        mMedialibrary = VLCApplication.getMLInstance();
        if (!mMedialibrary.isInitiated()) registerMedialibrary(null);
        if (!AndroidDevices.hasTsp && !AndroidDevices.hasPlayServices)
            AndroidDevices.setRemoteControlReceiverEnabled(true);

        mDetectHeadset = mSettings.getBoolean("enable_headset_detection", true);

        // Make sure the audio player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stop.
        final PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        updateHasWidget();
        initMediaSession();

        final IntentFilter filter = new IntentFilter();
        filter.setPriority(Integer.MAX_VALUE);
        filter.addAction(Constants.ACTION_REMOTE_BACKWARD);
        filter.addAction(Constants.ACTION_REMOTE_PLAYPAUSE);
        filter.addAction(Constants.ACTION_REMOTE_PLAY);
        filter.addAction(Constants.ACTION_REMOTE_PAUSE);
        filter.addAction(Constants.ACTION_REMOTE_STOP);
        filter.addAction(Constants.ACTION_REMOTE_FORWARD);
        filter.addAction(Constants.ACTION_REMOTE_LAST_PLAYLIST);
        filter.addAction(Constants.ACTION_REMOTE_LAST_VIDEO_PLAYLIST);
        filter.addAction(Constants.ACTION_REMOTE_SWITCH_VIDEO);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_INIT);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_ENABLED);
        filter.addAction(VLCAppWidgetProvider.ACTION_WIDGET_DISABLED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        filter.addAction(Constants.ACTION_CAR_MODE_EXIT);
        registerReceiver(mReceiver, filter);

        final boolean stealRemoteControl = mSettings.getBoolean("enable_steal_remote_control", false);

        if (stealRemoteControl) {
            /* Backward compatibility for API 7 */
            final IntentFilter stealFilter = new IntentFilter();
            stealFilter.setPriority(Integer.MAX_VALUE);
            stealFilter.addAction(Intent.ACTION_MEDIA_BUTTON);
            mRemoteControlClientReceiver = new RemoteControlClientReceiver();
            registerReceiver(mRemoteControlClientReceiver, stealFilter);
        }
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    }

    private MedialibraryReceiver mLibraryReceiver = null;
    private void registerMedialibrary(final Runnable action) {
        if (!Permissions.canReadStorage(this)) return;
        final LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        if (mLibraryReceiver == null) {
            mLibraryReceiver = new MedialibraryReceiver();
            lbm.registerReceiver(mLibraryReceiver, new IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY));
            Util.startService(PlaybackService.this, new Intent(Constants.ACTION_INIT, null, this, MediaParsingService.class));
        }
        if (action != null) mLibraryReceiver.addAction(action);
    }

    private void updateHasWidget() {
        final AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager.getAppWidgetIds(new ComponentName(this, VLCAppWidgetProviderWhite.class)).length != 0) mWidget = 1;
        else if (manager.getAppWidgetIds(new ComponentName(this, VLCAppWidgetProviderBlack.class)).length != 0) mWidget = 2;
        else mWidget = 0;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        final String action = intent.getAction();
        if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
            MediaButtonReceiver.handleIntent(mMediaSession, intent);
            return START_NOT_STICKY;
        }
        if (Constants.ACTION_REMOTE_PLAYPAUSE.equals(action)) {
            if (playlistManager.hasCurrentMedia()) return START_NOT_STICKY;
            else loadLastAudioPlaylist();
        } else if (Constants.ACTION_REMOTE_PLAY.equals(action)) {
            if (playlistManager.hasCurrentMedia()) play();
            else loadLastAudioPlaylist();
        } else if (Constants.ACTION_PLAY_FROM_SEARCH.equals(action)) {
            if (mMediaSession == null)
                initMediaSession();
            final Bundle extras = intent.getBundleExtra(Constants.EXTRA_SEARCH_BUNDLE);
            mMediaSession.getController().getTransportControls()
                    .playFromSearch(extras.getString(SearchManager.QUERY), extras);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        if (mMediaSession != null) {
            mMediaSession.release();
            mMediaSession = null;
        }
        //Call it once mMediaSession is null, to not publish playback state
        stop(true);

        if (!AndroidDevices.hasTsp && !AndroidDevices.hasPlayServices)
            AndroidDevices.setRemoteControlReceiverEnabled(false);
        unregisterReceiver(mReceiver);
        if (mRemoteControlClientReceiver != null) {
            unregisterReceiver(mRemoteControlClientReceiver);
            mRemoteControlClientReceiver = null;
        }
        playlistManager.onServiceDestroyed();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return SERVICE_INTERFACE.equals(intent.getAction()) ? super.onBind(intent) : mBinder;
    }

    public IVLCVout getVLCVout()  {
        return playlistManager.getPlayer().getVout();
    }

    private final OnAudioFocusChangeListener mAudioFocusListener = createOnAudioFocusChangeListener();

    private volatile boolean mLossTransient = false;
    private OnAudioFocusChangeListener createOnAudioFocusChangeListener() {
        return new OnAudioFocusChangeListener() {
            int audioDuckLevel = -1;
            private int mLossTransientVolume = -1;
            private boolean wasPlaying = false;

            @Override
            public void onAudioFocusChange(int focusChange) {
                /*
                 * Pause playback during alerts and notifications
                 */
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_LOSS");
                        // Pause playback
                        changeAudioFocus(false);
                        pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        // Pause playback
                        pausePlayback();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        // Lower the volume
                        if (isPlaying()) {
                            if (AndroidDevices.isAmazon) {
                                pausePlayback();
                            } else if (mSettings.getBoolean("audio_ducking", true)) {
                                final int volume = AndroidDevices.isAndroidTv ? getVolume()
                                        : mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                if (audioDuckLevel == -1)
                                    audioDuckLevel = AndroidDevices.isAndroidTv ? 50
                                            : mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/5;
                                if (volume > audioDuckLevel) {
                                    mLossTransientVolume = volume;
                                    if (AndroidDevices.isAndroidTv)
                                        setVolume(audioDuckLevel);
                                    else
                                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioDuckLevel, 0);
                                }
                            }
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        if (BuildConfig.DEBUG) Log.i(TAG, "AUDIOFOCUS_GAIN: ");
                        // Resume playback
                        if (mLossTransientVolume != -1) {
                            if (AndroidDevices.isAndroidTv)
                                setVolume(mLossTransientVolume);
                            else
                                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mLossTransientVolume, 0);
                            mLossTransientVolume = -1;
                        }
                        if (mLossTransient) {
                            if (wasPlaying && mSettings.getBoolean("resume_playback", true))
                                play();
                            mLossTransient = false;
                        }
                        break;
                }
            }

            private void pausePlayback() {
                if (mLossTransient) return;
                mLossTransient = true;
                wasPlaying = isPlaying();
                if (wasPlaying) pause();
            }
        };
    }

    private void sendStartSessionIdIntent() {
        int sessionId = VLCOptions.getAudiotrackSessionId();
        if (sessionId == 0)
            return;

        Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        if (isVideoPlaying())
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE);
        else
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        sendBroadcast(intent);
    }

    private void sendStopSessionIdIntent() {
        int sessionId = VLCOptions.getAudiotrackSessionId();
        if (sessionId == 0)
            return;

        Intent intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(intent);
    }

    private AudioManager mAudioManager = null;
    private void changeAudioFocus(boolean acquire) {
        if (mAudioManager == null)
            mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (mAudioManager == null)
            return;

        if (acquire && !hasRenderer()) {
            if (!mHasAudioFocus) {
                final int result = mAudioManager.requestAudioFocus(mAudioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    mAudioManager.setParameters("bgm_state=true");
                    mHasAudioFocus = true;
                }
            }
        } else if (mHasAudioFocus) {
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
            mAudioManager.setParameters("bgm_state=false");
            mHasAudioFocus = false;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private boolean wasPlaying = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int state = intent.getIntExtra("state", 0);

            // skip all headsets events if there is a call
            final TelephonyManager telManager = (TelephonyManager) VLCApplication.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(Constants.ACTION_REMOTE_GENERIC) && !isPlaying() && !playlistManager.hasCurrentMedia()) {
                final Intent activityIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (activityIntent != null)
                    context.startActivity(activityIntent);
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_PLAYPAUSE)) {
                if (!playlistManager.hasCurrentMedia())
                    loadLastAudioPlaylist();
                if (isPlaying()) pause();
                else play();
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_PLAY)) {
                if (!isPlaying() && playlistManager.hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_PAUSE)) {
                if (playlistManager.hasCurrentMedia())
                    pause();
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_BACKWARD)) {
                previous(false);
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_STOP) ||
                    action.equalsIgnoreCase(VLCApplication.SLEEP_INTENT)) {
                stop();
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_FORWARD)) {
                next();
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_LAST_PLAYLIST)) {
                loadLastAudioPlaylist();
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_LAST_VIDEO_PLAYLIST)) {
                playlistManager.loadLastPlaylist(Constants.PLAYLIST_TYPE_VIDEO);
            } else if (action.equalsIgnoreCase(Constants.ACTION_REMOTE_SWITCH_VIDEO)) {
                removePopup();
                if (hasMedia()) {
                    getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    playlistManager.switchToVideo();
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
                    if (BuildConfig.DEBUG) Log.i(TAG, "Becoming noisy");
                    wasPlaying = isPlaying();
                    if (wasPlaying && playlistManager.hasCurrentMedia())
                        pause();
                } else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Headset Inserted.");
                    if (wasPlaying && playlistManager.hasCurrentMedia() && mSettings.getBoolean("enable_play_on_headset_insertion", false))
                        play();
                }
            } else if (action.equalsIgnoreCase(Constants.ACTION_CAR_MODE_EXIT))
                BrowserProvider.unbindExtensionConnection();
        }
    };

    public void setBenchmark() { playlistManager.setBenchmark(true); }
    public void setHardware() { playlistManager.setHardware(true); }

    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        mMediaPlayerListener.onEvent(event);
    }

    private final MediaPlayer.EventListener mMediaPlayerListener = new MediaPlayer.EventListener() {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    if (BuildConfig.DEBUG) Log.i(TAG, "MediaPlayer.Event.Playing");
                    executeUpdate();
                    publishState();
                    executeUpdateProgress();
                    mHandler.sendEmptyMessage(SHOW_PROGRESS);
                    changeAudioFocus(true);
                    if (!mWakeLock.isHeld()) mWakeLock.acquire();
                    if (!mKeyguardManager.inKeyguardRestrictedInputMode()
                            && !playlistManager.getVideoBackground()
                            && !hasRenderer()
                            && playlistManager.switchToVideo()) {
                        hideNotification();
                    } else {
                        if (!hasRenderer() || !canSwitchToVideo()) showPlayer();
                        showNotification();
                    }
                    break;
                case MediaPlayer.Event.Paused:
                    if (BuildConfig.DEBUG) Log.i(TAG, "MediaPlayer.Event.Paused");
                    executeUpdate();
                    publishState();
                    executeUpdateProgress();
                    showNotification();
                    mHandler.removeMessages(SHOW_PROGRESS);
                    if (mWakeLock.isHeld()) mWakeLock.release();
                    break;
                case MediaPlayer.Event.EndReached:
                    executeUpdateProgress();
                    break;
                case MediaPlayer.Event.EncounteredError:
                    executeUpdate();
                    executeUpdateProgress();
                    break;
                case MediaPlayer.Event.TimeChanged:
                    break;
                case MediaPlayer.Event.PositionChanged:
                    updateWidgetPosition(event.getPositionChanged());
                    publishState();
                    break;
                case MediaPlayer.Event.Vout:
                    break;
                case MediaPlayer.Event.ESAdded:
                    if (event.getEsChangedType() == Media.Track.Type.Video && (playlistManager.getVideoBackground() || !playlistManager.switchToVideo())) {
                        /* Update notification content intent: resume video or resume audio activity */
                        updateMetadata();
                    }
                    break;
                case MediaPlayer.Event.ESDeleted:
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

    public void onPlaybackStopped() {
        hideNotification();
        if (mWakeLock.isHeld()) mWakeLock.release();
        changeAudioFocus(false);
        mMedialibrary.resumeBackgroundOperations();
        // We must publish state before resetting mCurrentIndex
        publishState();
        executeUpdate();
        executeUpdateProgress();
    }

    private void showPlayer() {
        sendBroadcast(new Intent(Constants.ACTION_SHOW_PLAYER));
    }

    public boolean canSwitchToVideo() {
        return playlistManager.getPlayer().canSwitchToVideo();
    }

    public void onMediaEvent(Media.Event event) {
        synchronized(mCallbacks) {
            for (Callback callback : mCallbacks) callback.onMediaEvent(event);
        }
    }

    public void executeUpdate() {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) callback.update();
        }
        updateWidget();
        updateMetadata();
        broadcastMetadata();
    }

    private void executeUpdateProgress() {
        synchronized (mCallbacks) {
            for (Callback callback : mCallbacks) callback.updateProgress();
        }
    }

    private final Handler mHandler = new PlaybackServiceHandler(this);

    private static class PlaybackServiceHandler extends WeakHandler<PlaybackService> {
        PlaybackServiceHandler(PlaybackService fragment) {
            super(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            final PlaybackService service = getOwner();
            if (service == null) return;
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
                case END_MEDIASESSION:
                    if (service.mMediaSession != null) service.mMediaSession.setActive(false);
                    break;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void showNotification() {
        if (!AndroidDevices.isAndroidTv && VLCApplication.showTvUi()) return;
        if (isPlayingPopup() || !hasRenderer() && playlistManager.getPlayer().isVideoPlaying()) {
            hideNotification();
            return;
        }
        final MediaWrapper mw = playlistManager.getCurrentMedia();
        if (mw != null) {
            final boolean coverOnLockscreen = mSettings.getBoolean("lockscreen_cover", true);
            final boolean playing = isPlaying();
            final MediaSessionCompat.Token sessionToken = mMediaSession.getSessionToken();
            final Context ctx = this;
            ExecutorHolder.executorService.execute(new Runnable() {
                @Override
                public void run() {
                    if (isPlayingPopup()) return;
                    try {
                        Bitmap cover;
                        final String title, artist, album;
                        synchronized (ExecutorHolder.updateMeta) {
                            try {
                                while (ExecutorHolder.updateMeta.get())
                                    ExecutorHolder.updateMeta.wait();
                            } catch (InterruptedException ignored) {}
                            final MediaMetadataCompat metaData = mMediaSession != null ? mMediaSession.getController().getMetadata() : null;
                            title = metaData == null ? mw.getTitle() : metaData.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                            artist = metaData == null ? mw.getArtist() : metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
                            album = metaData == null ? mw.getAlbum() : metaData.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
                            cover = coverOnLockscreen && metaData != null
                                    ? metaData.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                                    : AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), 256);
                        }
                        if (cover == null || cover.isRecycled())
                            cover = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_no_media);

                        final Notification notification = NotificationHelper.createPlaybackNotification(ctx,
                                mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO), title, artist, album,
                                cover, playing, sessionToken, getSessionPendingIntent());
                        if (isPlayingPopup()) return;
                        if (!AndroidUtil.isLolliPopOrLater || playing || mLossTransient) {
                            if (!mIsForeground) {
                                PlaybackService.this.startForeground(3, notification);
                                mIsForeground = true;
                            } else
                                NotificationManagerCompat.from(ctx).notify(3, notification);
                        } else {
                            if (mIsForeground) {
                                PlaybackService.this.stopForeground(false);
                                mIsForeground = false;
                            }
                            NotificationManagerCompat.from(ctx).notify(3, notification);
                        }
                    } catch (IllegalArgumentException|IllegalStateException e){
                        // On somme crappy firmwares, shit can happen
                        Log.e(TAG, "Failed to display notification", e);
                    }
                }
            });
        }
    }

    private boolean currentMediaHasFlag(int flag) {
        final MediaWrapper mw = playlistManager.getCurrentMedia();
        return mw != null && mw.hasFlag(flag);
    }

    public PendingIntent getSessionPendingIntent() {
        if (playlistManager.getPlayer().isVideoPlaying()) { //PIP
            final Intent notificationIntent = new Intent(this, VideoPlayerActivity.class);
            return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } if (playlistManager.getVideoBackground() || (canSwitchToVideo() && !currentMediaHasFlag(MediaWrapper.MEDIA_FORCE_AUDIO))) { //resume video playback
            /* Resume VideoPlayerActivity from ACTION_REMOTE_SWITCH_VIDEO intent */
            final Intent notificationIntent = new Intent(Constants.ACTION_REMOTE_SWITCH_VIDEO);
            return PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            /* Show audio player */
            final Intent notificationIntent = new Intent(this, StartActivity.class);
            notificationIntent.setAction(Constants.ACTION_SHOW_PLAYER);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            return PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private volatile boolean mIsForeground = false;
    public void hideNotification() {
        ExecutorHolder.executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (!isPlayingPopup() && mIsForeground) {
                    PlaybackService.this.stopForeground(true);
                    mIsForeground = false;
                }
                NotificationManagerCompat.from(PlaybackService.this).cancel(3);
            }
        });
    }

    public void onNewPlayback(final MediaWrapper mw) {
        mMediaSession.setSessionActivity(getSessionPendingIntent());
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
    }

    public void onPlaylistLoaded() {
        notifyTrackChanged();
        updateMediaQueue();
    }

    @MainThread
    public void pause() {
        playlistManager.pause();
    }

    @MainThread
    public void play() {
        playlistManager.play();
    }

    @MainThread
    public void stop() {
        stop(false);
    }

    @MainThread
    public void stop(boolean systemExit) {
        removePopup();
        playlistManager.stop(systemExit);
    }

    private void initMediaSession() {
        final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);

        mediaButtonIntent.setClass(this, RemoteControlClientReceiver.class);
        final PendingIntent mbrIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
        final ComponentName mbrName = new ComponentName(this, RemoteControlClientReceiver.class);

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
            if (!mSettings.getBoolean("enable_headset_actions", true) || VLCApplication.showTvUi()) return false;
            final KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && !isVideoPlaying()) {
                final int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                        || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    final long time = SystemClock.uptimeMillis();
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
                            if (AndroidDevices.hasTsp) { //no backward/forward on TV
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
            if (hasMedia()) play();
            else loadLastAudioPlaylist();
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            if (TextUtils.equals(action, "shuffle")) {
                shuffle();
            } else if (TextUtils.equals(action, "repeat")) {
                switch (getRepeatType()) {
                    case Constants.REPEAT_NONE:
                        setRepeatType(Constants.REPEAT_ALL);
                        break;
                    case Constants.REPEAT_ALL:
                        setRepeatType(Constants.REPEAT_ONE);
                        break;
                    default:
                    case Constants.REPEAT_ONE:
                        setRepeatType(Constants.REPEAT_NONE);
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
            } else if (mediaId.startsWith(ExtensionsManager.EXTENSION_PREFIX)) {
                onPlayFromUri(Uri.parse(mediaId.replace(ExtensionsManager.EXTENSION_PREFIX + "_" + mediaId.split("_")[1] + "_", "")), null);
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
            if (!mMedialibrary.isInitiated() || mLibraryReceiver != null) {
                registerMedialibrary(new Runnable() {
                    @Override
                    public void run() {
                        onPlayFromSearch(query, extras);
                    }
                });
                return;
            }
            mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_CONNECTING, getTime(), 1.0f).build());
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    final VoiceSearchParams vsp = new VoiceSearchParams(query, extras);
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
                    if (Tools.isArrayEmpty(tracks)) {
                        final SearchAggregate result = mMedialibrary.search(query);
                        if (result != null) {
                            if (!Tools.isArrayEmpty(result.getAlbums()))
                                tracks = result.getAlbums()[0].getTracks();
                            else if (!Tools.isArrayEmpty(result.getArtists()))
                                tracks = result.getArtists()[0].getTracks();
                            else if (!Tools.isArrayEmpty(result.getGenres()))
                                tracks = result.getGenres()[0].getTracks();
                        }
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
        final MediaWrapper media = playlistManager.getCurrentMedia();
        if (media == null) return;
        if (mMediaSession == null)
            initMediaSession();
        final Context ctx = this;
        ExecutorHolder.executorService.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (ExecutorHolder.updateMeta) {
                    ExecutorHolder.updateMeta.set(true);
                }
                if (media == null) return;
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
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getLength());
                if (coverOnLockscreen) {
                    final Bitmap cover = AudioUtil.readCoverBitmap(Uri.decode(media.getArtworkMrl()), 512);
                    if (cover != null && cover.getConfig() != null) //In case of format not supported
                        bob.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover.copy(cover.getConfig(), false));
                }
                bob.putLong("shuffle", 1L);
                bob.putLong("repeat", getRepeatType());
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mMediaSession != null)
                            mMediaSession.setMetadata(bob.build());
                        synchronized (ExecutorHolder.updateMeta) {
                            ExecutorHolder.updateMeta.set(false);
                            ExecutorHolder.updateMeta.notify();
                        }
                    }
                });
            }
        });
    }

    protected void publishState() {
        if (mMediaSession == null) return;
        if (AndroidDevices.isAndroidTv) mHandler.removeMessages(END_MEDIASESSION);
        final PlaybackStateCompat.Builder pscb = new PlaybackStateCompat.Builder();
        long actions = PLAYBACK_BASE_ACTIONS;
        final boolean hasMedia = playlistManager.hasCurrentMedia();
        long time = getTime();
        int state = playlistManager.getPlayer().getPlaybackState();
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            actions |= PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_STOP;
        } else if (state == PlaybackStateCompat.STATE_PAUSED) {
            actions |= PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP;
        } else {
            actions |= PlaybackStateCompat.ACTION_PLAY;
            final MediaWrapper media = AndroidDevices.isAndroidTv && hasMedia ? playlistManager.getCurrentMedia() : null;
            if (media != null) {
                final long length = media.getLength();
                time = media.getTime();
                float progress = length <= 0L ? 0f : time / (float)length;
                if (progress < 0.95f) {
                    state = PlaybackStateCompat.STATE_PAUSED;
                    mHandler.sendEmptyMessageDelayed(END_MEDIASESSION, 900_000L);
                }
            }
        }
        pscb.setState(state, time, playlistManager.getPlayer().getRate());
        final int repeatType = playlistManager.getRepeating();
        if (repeatType != Constants.REPEAT_NONE || hasNext())
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        if (repeatType != Constants.REPEAT_NONE || hasPrevious() || isSeekable())
            actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        if (isSeekable())
            actions |= PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND;
        actions |= PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
        pscb.setActions(actions);
        final int repeatResId = repeatType == Constants.REPEAT_ALL ? R.drawable.ic_auto_repeat_pressed : repeatType == Constants.REPEAT_ONE ? R.drawable.ic_auto_repeat_one_pressed : R.drawable.ic_auto_repeat_normal;
        if (playlistManager.hasPlaylist())
            pscb.addCustomAction("shuffle", getString(R.string.shuffle_title), isShuffling() ? R.drawable.ic_auto_shuffle_pressed : R.drawable.ic_auto_shuffle_normal);
        pscb.addCustomAction("repeat", getString(R.string.repeat_title), repeatResId);

        boolean mediaIsActive = state != PlaybackStateCompat.STATE_STOPPED;

        mMediaSession.setPlaybackState(pscb.build());
        mMediaSession.setActive(mediaIsActive);
        mMediaSession.setQueueTitle(getString(R.string.music_now_playing));

        if (mediaIsActive)
            sendStartSessionIdIntent();
        else
            sendStopSessionIdIntent();
    }

    private void notifyTrackChanged() {
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        updateMetadata();
        updateWidget();
        broadcastMetadata();
    }

    public void onMediaListChanged() {
        executeUpdate();
        updateMediaQueue();
    }

    @MainThread
    public void next() {
        playlistManager.next();
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
    }

    @MainThread
    public void previous(boolean force) {
        playlistManager.previous(force);
    }

    @MainThread
    public void shuffle() {
        playlistManager.shuffle();
        publishState();
    }

    @MainThread
    public void setRepeatType(int repeatType) {
        playlistManager.setRepeatType(repeatType);
        publishState();
    }

    private void updateWidget() {
        if (mWidget != 0 && !isVideoPlaying()) {
            updateWidgetState();
            updateWidgetCover();
        }
    }

    private void sendWidgetBroadcast(Intent intent) {
        intent.setComponent(new ComponentName(PlaybackService.this, mWidget == 1 ? VLCAppWidgetProviderWhite.class : VLCAppWidgetProviderBlack.class));
        sendBroadcast(intent);
    }

    private void updateWidgetState() {
        final MediaWrapper media = playlistManager.getCurrentMedia();
        final Intent widgetIntent = new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE);
        if (playlistManager.hasCurrentMedia()) {
            widgetIntent.putExtra("title", media.getTitle());
            widgetIntent.putExtra("artist", media.isArtistUnknown() && media.getNowPlaying() != null ?
                    media.getNowPlaying()
                    : MediaUtils.getMediaArtist(PlaybackService.this, media));
        } else {
            widgetIntent.putExtra("title", getString(R.string.widget_default_text));
            widgetIntent.putExtra("artist", "");
        }
        widgetIntent.putExtra("isplaying", isPlaying());
        sendWidgetBroadcast(widgetIntent);
    }

    private String mCurrentWidgetCover = null;
    private void updateWidgetCover() {
        final MediaWrapper mw = playlistManager.getCurrentMedia();
        final String newWidgetCover = mw != null ? mw.getArtworkMrl() : null;
        if (!TextUtils.equals(mCurrentWidgetCover, newWidgetCover)) {
            mCurrentWidgetCover = newWidgetCover;
            sendWidgetBroadcast(new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_COVER)
                    .putExtra("artworkMrl", newWidgetCover));
        }
    }

    private void updateWidgetPosition(final float pos) {
        final MediaWrapper mw = playlistManager.getCurrentMedia();
        if (mw == null || mWidget == 0 || isVideoPlaying()) return;
        // no more than one widget mUpdateMeta for each 1/50 of the song
        long timestamp = System.currentTimeMillis();
        if (!playlistManager.hasCurrentMedia()
                || timestamp - mWidgetPositionTimestamp < mw.getLength() / 50)
            return;
        mWidgetPositionTimestamp = timestamp;
        sendWidgetBroadcast(new Intent(VLCAppWidgetProvider.ACTION_WIDGET_UPDATE_POSITION)
                .putExtra("position", pos));
    }

    private void broadcastMetadata() {
        final MediaWrapper media = playlistManager.getCurrentMedia();
        if (media == null || isVideoPlaying()) return;
        sendBroadcast(new Intent("com.android.music.metachanged")
                .putExtra("track", media.getTitle())
                .putExtra("artist", media.getArtist())
                .putExtra("album", media.getAlbum())
                .putExtra("duration", media.getLength())
                .putExtra("playing", isPlaying())
                .putExtra("package", "org.videolan.vlc"));
    }

    private void loadLastAudioPlaylist() {
        if (AndroidDevices.isAndroidTv) return;
        if (mMedialibrary.isInitiated() && mLibraryReceiver == null) playlistManager.loadLastPlaylist(Constants.PLAYLIST_TYPE_AUDIO);
        else registerMedialibrary(new Runnable() {
                @Override
                public void run() {
                    playlistManager.loadLastPlaylist(Constants.PLAYLIST_TYPE_AUDIO);
                }
            });
    }

    public void loadLastPlaylist(int type) {
        playlistManager.loadLastPlaylist(type);
    }

    public void showToast(String text, int duration) {
        final Message msg = new Message();
        final Bundle bundle = new Bundle();
        bundle.putString("text", text);
        bundle.putInt("duration", duration);
        msg.setData(bundle);
        msg.what = SHOW_TOAST;
        mHandler.sendMessage(msg);
    }

    @MainThread
    public boolean isPlaying() {
        return playlistManager.getPlayer().isPlaying();
    }

    @MainThread
    public boolean isSeekable() {
        return playlistManager.getPlayer().getSeekable();
    }

    @MainThread
    public boolean isPausable() {
        return playlistManager.getPlayer().getPausable();
    }

    @MainThread
    public boolean isShuffling() {
        return playlistManager.getShuffling();
    }

    @MainThread
    public boolean canShuffle()  {
        return playlistManager.canShuffle();
    }

    @MainThread
    public int getRepeatType() {
        return playlistManager.getRepeating();
    }

    @MainThread
    public boolean hasMedia()  {
        return playlistManager.hasMedia();
    }

    @MainThread
    public boolean hasPlaylist()  {
        return playlistManager.hasPlaylist();
    }

    @MainThread
    public boolean isVideoPlaying() {
        return playlistManager.getPlayer().isVideoPlaying();
    }

    @MainThread
    public String getAlbum() {final MediaWrapper media = playlistManager.getCurrentMedia();
        return media != null ? MediaUtils.getMediaAlbum(PlaybackService.this, media) : null;
    }

    @MainThread
    public String getArtist() {
        final MediaWrapper media = playlistManager.getCurrentMedia();
        if (media != null) {
            return media.getNowPlaying() != null ? media.getNowPlaying()
                    : MediaUtils.getMediaArtist(PlaybackService.this, media);
        } else return null;
    }

    @MainThread
    public String getArtistPrev() {
        final MediaWrapper prev = playlistManager.getPrevMedia();
        return prev != null ? MediaUtils.getMediaArtist(PlaybackService.this, prev) : null;
    }

    @MainThread
    public String getArtistNext() {
        final MediaWrapper next = playlistManager.getNextMedia();
        return next != null ? MediaUtils.getMediaArtist(PlaybackService.this, next) : null;
    }

    @MainThread
    public String getTitle() {
        final MediaWrapper media = playlistManager.getCurrentMedia();
        return media != null ? media.getNowPlaying() != null ? media.getNowPlaying() : media.getTitle() : null;
    }

    @MainThread
    public String getTitlePrev() {
        final MediaWrapper prev = playlistManager.getPrevMedia();
        return prev != null ? prev.getTitle() : null;
    }

    @MainThread
    public String getTitleNext() {
        final MediaWrapper next = playlistManager.getNextMedia();
        return next != null ? next.getTitle() : null;
    }

    @MainThread
    public String getCoverArt() {
        final MediaWrapper media = playlistManager.getCurrentMedia();
        return media != null ? media.getArtworkMrl() : null;
    }

    @MainThread
    public String getPrevCoverArt() {
        final MediaWrapper prev = playlistManager.getPrevMedia();
        return prev != null ? prev.getArtworkMrl() : null;
    }

    @MainThread
    public String getNextCoverArt() {
        final MediaWrapper next = playlistManager.getNextMedia();
        return next != null ? next.getArtworkMrl() : null;
    }

    @MainThread
    public void addCallback(Callback cb) {
        synchronized (mCallbacks) {
            if (!mCallbacks.contains(cb)) {
                mCallbacks.add(cb);
                if (playlistManager.hasCurrentMedia())
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
        return playlistManager.getPlayer().getTime();
    }

    @MainThread
    public long getLength() {
        return playlistManager.getPlayer().getLength();
    }

    public void restartMediaPlayer() {
        playlistManager.getPlayer().restart();
    }

    public void saveMediaMeta() {
        playlistManager.saveMediaMeta();
    }

    public boolean isValidIndex(int positionInPlaylist) {
        return playlistManager.isValidPosition(positionInPlaylist);
    }

    public Media.Stats getLastStats() {
        return playlistManager.getPlayer().getPreviousMediaStats();
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
        playlistManager.loadLocations(mediaPathList, position);
    }

    @MainThread
    public void loadUri(Uri uri) {
        loadLocation(uri.toString());
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
        playlistManager.load(mediaList, position);
    }

    private void updateMediaQueue() {
        final LinkedList<MediaSessionCompat.QueueItem> queue = new LinkedList<>();
        long position = -1;
        for (MediaWrapper media : playlistManager.getMediaList()) {
            String title = media.getNowPlaying();
            if (title == null) title = media.getTitle();
            final MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder();
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
        load(Collections.singletonList(media), 0);
    }

    /**
     * Play a media from the media list (playlist)
     *
     * @param index The index of the media
     * @param flags LibVLC.MEDIA_* flags
     */
    public void playIndex(int index, int flags) {
        playlistManager.playIndex(index, flags);
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
        playlistManager.setVideoTrackEnabled(false);
        final MediaWrapper media = playlistManager.getMedia(index);
        if (media == null || !isPlaying()) return;
        // Show an URI without interrupting/losing the current stream
        if (BuildConfig.DEBUG) Log.v(TAG, "Showing index " + index + " with playing URI " + media.getUri());
        playlistManager.setCurrentIndex(index);
        notifyTrackChanged();
        showNotification();
    }

    public void setVideoTrackEnabled(boolean enabled) {
        playlistManager.setVideoTrackEnabled(enabled);
    }

    public void switchToVideo() {
        playlistManager.switchToVideo();
    }

    @MainThread
    public void switchToPopup(int index) {
        playlistManager.saveMediaMeta();
        showWithoutParse(index);
        showPopup();
    }

    @MainThread
    public void removePopup() {
        if (mPopupManager != null) mPopupManager.removePopup();
        mPopupManager = null;
    }

    @MainThread
    public boolean isPlayingPopup() {
        return mPopupManager != null;
    }

    @MainThread
    public void showPopup() {
        if (mPopupManager == null) mPopupManager = new PopupManager(this);
        mPopupManager.showPopup();
        hideNotification();
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
        playlistManager.append(mediaList);
        onMediaListChanged();
    }

    @MainThread
    public void append(MediaWrapper media) {
        final List<MediaWrapper> arrayList = new ArrayList<>();
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
        playlistManager.insertNext(mediaList);
        onMediaListChanged();
    }

    @MainThread
    public void insertNext(MediaWrapper media) {
        final List<MediaWrapper> arrayList = new ArrayList<>();
        arrayList.add(media);
        insertNext(arrayList);
    }

    /**
     * Move an item inside the playlist.
     */
    @MainThread
    public void moveItem(int positionStart, int positionEnd) {
        playlistManager.moveItem(positionStart, positionEnd);
    }

    @MainThread
    public void insertItem(int position, MediaWrapper mw) {
        playlistManager.insertItem(position, mw);
    }


    @MainThread
    public void remove(int position) {
        playlistManager.remove(position);
    }

    @MainThread
    public void removeLocation(String location) {
        playlistManager.removeLocation(location);
    }

    public int getMediaListSize() {
        return playlistManager.getMediaListSize();
    }

    @MainThread
    public List<MediaWrapper> getMedias() {
        return new ArrayList<>(playlistManager.getMediaList());
    }

    @MainThread
    public List<String> getMediaLocations() {
        final List<String> medias = new ArrayList<>();
        for (MediaWrapper mw : playlistManager.getMediaList()) medias.add(mw.getLocation());
        return medias;
    }

    @MainThread
    public String getCurrentMediaLocation() {
        return playlistManager.getCurrentMedia().getLocation();
    }

    @MainThread
    public int getCurrentMediaPosition() {
        return playlistManager.getCurrentIndex();
    }

    @MainThread
    public MediaWrapper getCurrentMediaWrapper() {
        return PlaybackService.this.playlistManager.getCurrentMedia();
    }

    @MainThread
    public void setTime(long time) {
        playlistManager.getPlayer().setTime(time);
    }

    @MainThread
    public boolean hasNext() {
        return playlistManager.hasNext();
    }

    @MainThread
    public boolean hasPrevious() {
        return playlistManager.hasPrevious();
    }

    @MainThread
    public void detectHeadset(boolean enable)  {
        mDetectHeadset = enable;
    }

    @MainThread
    public float getRate()  {
        return playlistManager.getPlayer().getRate();
    }

    @MainThread
    public void setRate(float rate, boolean save) {
        playlistManager.getPlayer().setRate(rate, save);
    }

    @MainThread
    public void navigate(int where) {
        playlistManager.getPlayer().navigate(where);
    }

    @MainThread
    public MediaPlayer.Chapter[] getChapters(int title) {
        return playlistManager.getPlayer().getChapters(title);
    }

    @MainThread
    public MediaPlayer.Title[] getTitles() {
        return playlistManager.getPlayer().getTitles();
    }

    @MainThread
    public int getChapterIdx() {
        return playlistManager.getPlayer().getChapterIdx();
    }

    @MainThread
    public void setChapterIdx(int chapter) {
        playlistManager.getPlayer().setChapterIdx(chapter);
    }

    @MainThread
    public int getTitleIdx() {
        return playlistManager.getPlayer().getTitleIdx();
    }

    @MainThread
    public void setTitleIdx(int title) {
        playlistManager.getPlayer().setTitleIdx(title);
    }

    @MainThread
    public int getVolume() {
        return playlistManager.getPlayer().getVolume();
    }

    @MainThread
    public int setVolume(int volume) {
        return playlistManager.getPlayer().setVolume(volume);
    }

    @MainThread
    public void seek(long position) {
        seek(position, getLength());
    }

    @MainThread
    public void seek(long position, double length) {
        if (length > 0.0D) setPosition((float) (position/length));
        else setTime(position);
    }

    @MainThread
    public boolean updateViewpoint(float yaw, float pitch, float roll, float fov, boolean absolute) {
        return playlistManager.getPlayer().updateViewpoint(yaw, pitch, roll, fov, absolute);
    }

    @MainThread
    public void saveTimeToSeek(long time) {
        playlistManager.setSavedTime(time);
    }

    @MainThread
    public void setPosition(float pos) {
        playlistManager.getPlayer().setPosition(pos);
    }

    @MainThread
    public int getAudioTracksCount() {
        return playlistManager.getPlayer().getAudioTracksCount();
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getAudioTracks() {
        return playlistManager.getPlayer().getAudioTracks();
    }

    @MainThread
    public int getAudioTrack() {
        return playlistManager.getPlayer().getAudioTrack();
    }

    @MainThread
    public boolean setAudioTrack(int index) {
        return playlistManager.getPlayer().setAudioTrack(index);
    }

    @MainThread
    public int getVideoTracksCount() {
        return hasMedia() ? playlistManager.getPlayer().getVideoTracksCount() : 0;
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getVideoTracks() {
        return playlistManager.getPlayer().getVideoTracks();
    }

    @MainThread
    public Media.VideoTrack getCurrentVideoTrack() {
        return playlistManager.getPlayer().getCurrentVideoTrack();
    }

    @MainThread
    public int getVideoTrack() {
        return playlistManager.getPlayer().getVideoTrack();
    }

    @MainThread
    public boolean addSubtitleTrack(String path, boolean select) {
        return playlistManager.getPlayer().addSubtitleTrack(path, select);
    }

    @MainThread
    public boolean addSubtitleTrack(Uri uri,boolean select) {
        return playlistManager.getPlayer().addSubtitleTrack(uri, select);
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getSpuTracks() {
        return playlistManager.getPlayer().getSpuTracks();
    }

    @MainThread
    public int getSpuTrack() {
        return playlistManager.getPlayer().getSpuTrack();
    }

    @MainThread
    public boolean setSpuTrack(int index) {
        return playlistManager.getPlayer().setSpuTrack(index);
    }

    @MainThread
    public int getSpuTracksCount() {
        return playlistManager.getPlayer().getSpuTracksCount();
    }

    @MainThread
    public boolean setAudioDelay(long delay) {
        return playlistManager.getPlayer().setAudioDelay(delay);
    }

    @MainThread
    public long getAudioDelay() {
        return playlistManager.getPlayer().getAudioDelay();
    }

    @MainThread
    public boolean setSpuDelay(long delay) {
        return playlistManager.getPlayer().setSpuDelay(delay);
    }

    @MainThread
    public boolean hasRenderer() {
        return playlistManager.getPlayer().getHasRenderer();
    }

    @MainThread
    public void setRenderer(RendererItem item) {
        final boolean wasOnRenderer = hasRenderer();
        if (wasOnRenderer && !hasRenderer() && canSwitchToVideo()) VideoPlayerActivity.startOpened(VLCApplication.getAppContext(),
                playlistManager.getCurrentMedia().getUri(), playlistManager.getCurrentIndex());
        playlistManager.getPlayer().setRenderer(item);
        if (!wasOnRenderer && item != null) changeAudioFocus(false);
        else if (wasOnRenderer && item == null && isPlaying()) changeAudioFocus(true);
    }

    @MainThread
    public long getSpuDelay() {
        return playlistManager.getPlayer().getSpuDelay();
    }

    @MainThread
    public void setEqualizer(MediaPlayer.Equalizer equalizer) {
        playlistManager.getPlayer().setEqualizer(equalizer);
    }

    @MainThread
    public void setVideoScale(float scale) {
        playlistManager.getPlayer().setVideoScale(scale);
    }

    @MainThread
    public void setVideoAspectRatio(@Nullable String aspect) {
        playlistManager.getPlayer().setVideoAspectRatio(aspect);
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
            Util.startService(context, getServiceIntent(context));
        }

        private static void stopService(Context context) {
            context.stopService(getServiceIntent(context));
        }

        public Client(Context context, Callback callback) {
            if (context == null || callback == null) throw new IllegalArgumentException("Context and callback can't be null");
            mContext = context;
            mCallback = callback;
        }

        @MainThread
        public void connect() {
            if (mBound) throw new IllegalStateException("already connected");
            final Intent serviceIntent = getServiceIntent(mContext);
            Util.startService(mContext, serviceIntent);
            mBound = mContext.bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
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
        return Permissions.canReadStorage(PlaybackService.this) ? new BrowserRoot(BrowserProvider.ID_ROOT, null) : null;
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId, @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.detach();
        if (!mMedialibrary.isInitiated() || mLibraryReceiver != null)
            registerMedialibrary(new Runnable() {
                @Override
                public void run() {
                    sendResults(result, parentId);
                }
            });
        else
            sendResults(result, parentId);
    }

    private void sendResults(@NonNull final MediaBrowserServiceCompat.Result result, @NonNull final String parentId) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    result.sendResult(BrowserProvider.browse(parentId));
                } catch (RuntimeException ignored) {} //bitmap parcelization can fail
            }
        });
    }

    private class MedialibraryReceiver extends BroadcastReceiver {
        private final List<Runnable> pendingActions = new LinkedList<>();
        @Override
        public void onReceive(Context context, Intent intent) {
            mLibraryReceiver = null;
            LocalBroadcastManager.getInstance(PlaybackService.this).unregisterReceiver(this);
            synchronized (pendingActions) {
                for (Runnable r : pendingActions)
                    r.run();
            }
        }
        public void addAction(Runnable r) {
            synchronized (pendingActions) {
                pendingActions.add(r);
            }
        }
    }
}
