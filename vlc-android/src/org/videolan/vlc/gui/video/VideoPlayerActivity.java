/*****************************************************************************
 * VideoPlayerActivity.java
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

package org.videolan.vlc.gui.video;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PictureInPictureParams;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.Nullable;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.RendererItem;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.DisplayManager;
import org.videolan.libvlc.util.VLCVideoLayout;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.RendererDelegate;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.database.models.ExternalSub;
import org.videolan.vlc.databinding.PlayerHudBinding;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.audio.PlaylistAdapter;
import org.videolan.vlc.gui.browser.FilePickerActivity;
import org.videolan.vlc.gui.browser.FilePickerFragmentKt;
import org.videolan.vlc.gui.dialogs.ContextSheetKt;
import org.videolan.vlc.gui.dialogs.CtxActionReceiver;
import org.videolan.vlc.gui.dialogs.RenderersDialog;
import org.videolan.vlc.gui.helpers.OnRepeatListener;
import org.videolan.vlc.gui.helpers.PlayerOptionsDelegate;
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.interfaces.IPlaybackSettingsController;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.repository.ExternalSubRepository;
import org.videolan.vlc.repository.SlaveRepository;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.viewmodels.PlaylistModel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.ViewStubCompat;
import androidx.databinding.BindingAdapter;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class VideoPlayerActivity extends AppCompatActivity implements IPlaybackSettingsController,
        PlaybackService.Client.Callback, PlaybackService.Callback,PlaylistAdapter.IPlayer,
        OnClickListener, OnLongClickListener, StoragePermissionsDelegate.CustomActionController, CtxActionReceiver {

    private final static String TAG = "VLC/VideoPlayerActivity";

    private final static String ACTION_RESULT = Strings.buildPkgString("player.result");
    private final static String EXTRA_POSITION = "extra_position";
    private final static String EXTRA_DURATION = "extra_duration";
    private final static String EXTRA_URI = "extra_uri";
    private final static int RESULT_CONNECTION_FAILED = RESULT_FIRST_USER + 1;
    private final static int RESULT_PLAYBACK_ERROR = RESULT_FIRST_USER + 2;
    private final static int RESULT_VIDEO_TRACK_LOST = RESULT_FIRST_USER + 3;
    static final float DEFAULT_FOV = 80f;

    private final PlaybackServiceActivity.Helper mHelper = new PlaybackServiceActivity.Helper(this, this);
    protected PlaybackService mService;
    private Medialibrary mMedialibrary;
    private VLCVideoLayout mVideoLayout;
    public DisplayManager mDisplayManager;
    private View mRootView;
    private Uri mUri;
    private boolean mAskResume = true;

    private ImageView mPlaylistToggle;
    private RecyclerView mPlaylist;
    private PlaylistAdapter mPlaylistAdapter;
    private PlaylistModel mPlaylistModel;

    private ImageView mOrientationToggle;

    private SharedPreferences mSettings;

    /** Overlay */
    private ActionBar mActionBar;
    private ViewGroup mActionBarView;
    private View mOverlayBackground;
    private static final String KEY_TIME = "saved_time";
    private static final String KEY_URI = "saved_uri";
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int OVERLAY_INFINITE = -1;
    private static final int FADE_OUT = 1;
    private static final int FADE_OUT_INFO = 2;
    private static final int START_PLAYBACK = 3;
    private static final int AUDIO_SERVICE_CONNECTION_FAILED = 4;
    private static final int RESET_BACK_LOCK = 5;
    private static final int CHECK_VIDEO_TRACKS = 6;
    private static final int LOADING_ANIMATION = 7;
    static final int SHOW_INFO = 8;
    static final int HIDE_INFO = 9;

    private static final int LOADING_ANIMATION_DELAY = 1000;

    private boolean mDragging;
    private boolean mShowing;
    private boolean mShowingDialog;
    private DelayState mPlaybackSetting = DelayState.OFF;
    private TextView mTitle;
    private TextView mInfo;
    private View mOverlayInfo;
    private View mVerticalBar;
    private View mVerticalBarProgress;
    private View mVerticalBarBoostProgress;
    private boolean mIsLoading;
    private boolean mIsPlaying = false;
    private ImageView mLoading;
    private ImageView mNavMenu;
    private ImageView mRendererBtn;
    private ImageView mPlaybackSettingPlus;
    private ImageView mPlaybackSettingMinus;
    protected boolean mEnableCloneMode;
    private static volatile boolean sDisplayRemainingTime;
    private int mScreenOrientation;
    private int mScreenOrientationLock;
    private int mCurrentScreenOrientation;
    private String KEY_REMAINING_TIME_DISPLAY = "remaining_time_display";
    private String KEY_BLUETOOTH_DELAY = "key_bluetooth_delay";
    private long mSpuDelay = 0L;
    private long mAudioDelay = 0L;
    private int mCurrentAudioTrack = -2, mCurrentSpuTrack = -2;

    private boolean mIsLocked = false;
    /* -1 is a valid track (Disable) */
    private int mLastAudioTrack = -2;
    private int mLastSpuTrack = -2;
    private int mOverlayTimeout = 0;
    private boolean mLockBackButton = false;
    boolean mWasPaused = false;
    private long mSavedTime = -1;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mSwitchToPopup;

    //Volume
    private AudioManager mAudioManager;
    private int mAudioMax;
    private boolean audioBoostEnabled;
    private boolean mMute = false;
    private int mVolSave;
    private float mVol;
    private float mOriginalVol;
    private Toast warningToast;

    private float mFov;
    private VideoTouchDelegate mTouchDelegate;
    private boolean mIsTv;

    // Tracks & Subtitles
    private MediaPlayer.TrackDescription[] mAudioTracksList;
    private MediaPlayer.TrackDescription[] mVideoTracksList;
    private MediaPlayer.TrackDescription[] mSubtitleTracksList;

    /**
     * Flag to indicate whether the media should be paused once loaded
     * (e.g. lock screen, or to restore the pause state)
     */
    private boolean mPlaybackStarted = false;

    // Tips
    private View mOverlayTips;
    private static final String PREF_TIPS_SHOWN = "video_player_tips_shown";

    // Navigation handling (DVD, Blu-Ray...)
    private int mMenuIdx = -1;
    private boolean mIsNavMenu = false;

    /* for getTime and seek */
    private long mForcedTime = -1;
    private long mLastTime = -1;

    private AlertDialog mAlertDialog;

    protected boolean mIsBenchmark = false;

    private ArrayList<ExternalSub> addedExternalSubs = new ArrayList<>();
    private LiveData downloadedSubtitleLiveData = null;
    private String previousMediaPath = null;

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Util.checkCpuCompatibility(this);

        mSettings = Settings.INSTANCE.getInstance(this);

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioBoostEnabled = mSettings.getBoolean("audio_boost", false);

        mEnableCloneMode = mSettings.getBoolean("enable_clone_mode", false);
        mDisplayManager = new DisplayManager(this, AndroidDevices.isChromeBook ? RendererDelegate.INSTANCE.getSelectedRenderer() : null, false, mEnableCloneMode, mIsBenchmark);
        setContentView(mDisplayManager.isPrimary() ? R.layout.player : R.layout.player_remote_control);

        /** initialize Views an their Events */
        mActionBar = getSupportActionBar();
        mActionBar.setDisplayShowHomeEnabled(false);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setBackgroundDrawable(null);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setCustomView(R.layout.player_action_bar);

        mRootView = findViewById(R.id.player_root);
        mActionBarView = (ViewGroup) mActionBar.getCustomView();

        mTitle = mActionBarView.findViewById(R.id.player_overlay_title);

        mPlaylistToggle = findViewById(R.id.playlist_toggle);
        mPlaylist = findViewById(R.id.video_playlist);

        mOrientationToggle = findViewById(R.id.orientation_toggle);

        mScreenOrientation = Integer.valueOf(
                mSettings.getString("screen_orientation", "99" /*SCREEN ORIENTATION SENSOR*/));

        mVideoLayout = findViewById(R.id.video_layout);

        /* Loading view */
        mLoading = findViewById(R.id.player_overlay_loading);
        dimStatusBar(true);
        mHandler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY);

        mSwitchingView = false;

        mAskResume = mSettings.getBoolean("dialog_confirm_resume", false);
        sDisplayRemainingTime = mSettings.getBoolean(KEY_REMAINING_TIME_DISPLAY, false);
        // Clear the resume time, since it is only used for resumes in external
        // videos.
        final SharedPreferences.Editor editor = mSettings.edit();
        editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
        // Paused flag - per session too, like the subs list.
        editor.remove(PreferencesActivity.VIDEO_PAUSED);
        editor.apply();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // 100 is the value for screen_orientation_start_lock
        setRequestedOrientation(getScreenOrientation(mScreenOrientation));
        // Extra initialization when no secondary display is detected
        mIsTv = AndroidDevices.showTvUi(this);
        if (mDisplayManager.isPrimary()) {
            // Orientation
            // Tips
            if (!BuildConfig.DEBUG && !mIsTv && !mSettings.getBoolean(PREF_TIPS_SHOWN, false)
                    && !mIsBenchmark) {
                ((ViewStubCompat) findViewById(R.id.player_overlay_tips)).inflate();
                mOverlayTips = findViewById(R.id.overlay_tips_layout);
            }

            //Set margins for TV overscan
            if (mIsTv) {
                int hm = getResources().getDimensionPixelSize(R.dimen.tv_overscan_horizontal);
                int vm = getResources().getDimensionPixelSize(R.dimen.tv_overscan_vertical);

                final RelativeLayout uiContainer = findViewById(R.id.player_ui_container);
                final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) uiContainer.getLayoutParams();
                lp.setMargins(hm, 0, hm, vm);
                uiContainer.setLayoutParams(lp);

                final LinearLayout.LayoutParams titleParams = (LinearLayout.LayoutParams) mTitle.getLayoutParams();
                titleParams.setMargins(0, vm, 0, 0);
                mTitle.setLayoutParams(titleParams);
            }
        }


        mMedialibrary = VLCApplication.getMLInstance();
        final int touch;
        if (!mIsTv) {
            touch = (mSettings.getBoolean("enable_volume_gesture", true) ? VideoTouchDelegateKt.TOUCH_FLAG_AUDIO_VOLUME : 0)
                    + (mSettings.getBoolean("enable_brightness_gesture", true) ? VideoTouchDelegateKt.TOUCH_FLAG_BRIGHTNESS : 0)
                    + (mSettings.getBoolean("enable_double_tap_seek", true) ? VideoTouchDelegateKt.TOUCH_FLAG_SEEK : 0);
        } else touch = 0;
        mCurrentScreenOrientation = getResources().getConfiguration().orientation;
        if (touch != 0) {
            final DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            int yRange = Math.min(dm.widthPixels, dm.heightPixels);
            int xRange = Math.max(dm.widthPixels, dm.heightPixels);
            final ScreenConfig sc = new ScreenConfig(dm, xRange, yRange, mCurrentScreenOrientation);
            mTouchDelegate = new VideoTouchDelegate(this, touch, sc, mIsTv);
        }
        UiTools.setRotationAnimation(this);
        if (savedInstanceState != null) {
            mSavedTime = savedInstanceState.getLong(KEY_TIME);
            mUri = (Uri) savedInstanceState.getParcelable(KEY_URI);
        }
    }

    @Override
    protected void onResume() {
        overridePendingTransition(0,0);
        super.onResume();
        mShowingDialog = false;
        /*
         * Set listeners here to avoid NPE when activity is closing
         */
        setListeners(true);

        if (mIsLocked && mScreenOrientation == 99) setRequestedOrientation(mScreenOrientationLock);
    }

    private void setListeners(boolean enabled) {
        if (mHudBinding != null) mHudBinding.playerOverlaySeekbar.setOnSeekBarChangeListener(enabled ? mSeekListener : null);
        if (mNavMenu != null) mNavMenu.setOnClickListener(enabled ? this : null);
        if (mOrientationToggle != null) {
            mOrientationToggle.setOnClickListener(enabled ? this : null);
            mOrientationToggle.setOnLongClickListener(enabled ? this : null);
        }

        UiTools.setViewOnClickListener(mRendererBtn, enabled ? this : null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        if (mPlaybackStarted && mService.getCurrentMediaWrapper() != null) {
            Uri uri = intent.hasExtra(Constants.PLAY_EXTRA_ITEM_LOCATION) ?
                    (Uri) intent.getExtras().getParcelable(Constants.PLAY_EXTRA_ITEM_LOCATION) : intent.getData();
            if (uri == null || uri.equals(mUri))
                return;
            if (TextUtils.equals("file", uri.getScheme()) && uri.getPath().startsWith("/sdcard")) {
                Uri convertedUri = FileUtils.convertLocalUri(uri);
                if (convertedUri == null || convertedUri.equals(mUri))
                    return;
                else
                    uri = convertedUri;
            }
            mUri = uri;
            mTitle.setText(mService.getCurrentMediaWrapper().getTitle());
            if (isPlaylistVisible()) {
                mPlaylistAdapter.setCurrentIndex(mService.getCurrentMediaPosition());
                mPlaylist.setVisibility(View.GONE);
            }
            if (mSettings.getBoolean("video_transition_show", true)) showTitle();
            initUI();
            setPlaybackParameters();
            mForcedTime = mLastTime = -1;
            enableSubs();
            if (mOptionsDelegate != null) mOptionsDelegate.setup();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onPause() {
        if (isFinishing())
            overridePendingTransition(0, 0);
        else
            hideOverlay(true);
        super.onPause();
        setListeners(false);

        /* Stop the earliest possible to avoid vout error */

        if (!isInPictureInPictureMode()) {
            if (isFinishing() ||
                    (AndroidUtil.isNougatOrLater && !AndroidUtil.isOOrLater //Video on background on Nougat Android TVs
                            && AndroidDevices.isAndroidTv && !requestVisibleBehind(true)))
                stopPlayback();
            else if (!isFinishing() && !mShowingDialog && "2".equals(mSettings.getString(PreferencesActivity.KEY_VIDEO_APP_SWITCH, "0")) && isInteractive()) {
                switchToPopup();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null && !"content".equals(mUri.getScheme())) {
            outState.putLong(KEY_TIME, mSavedTime);
            outState.putParcelable(KEY_URI, mUri);
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    public void switchToPopup() {
        final MediaWrapper mw = mService != null ? mService.getCurrentMediaWrapper() : null;
        if (mw == null) return;
        if (AndroidDevices.hasPiP) {
            if (AndroidUtil.isOOrLater) try {
                    final SurfaceView videoSurface = mVideoLayout.findViewById(R.id.surface_video);
                    final int height = videoSurface != null && videoSurface.getHeight() != 0 ? videoSurface.getHeight() : mw.getHeight();
                    final int width = Math.min(videoSurface != null && videoSurface.getWidth() != 0 ? videoSurface.getWidth() : mw.getWidth(), (int) (height*2.39f));
                    enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(width, height)).build());
                } catch (IllegalArgumentException e) { // Fallback with default parameters
                    //noinspection deprecation
                    enterPictureInPictureMode();
                }
            else {
                //noinspection deprecation
                enterPictureInPictureMode();
            }
        } else {
            if (Permissions.canDrawOverlays(this)) {
                mSwitchingView = true;
                mSwitchToPopup = true;
                if (mService != null && !mService.isPlaying())
                    mw.addFlags(MediaWrapper.MEDIA_PAUSED);
                cleanUI();
                exitOK();
            } else
                Permissions.checkDrawOverlaysPermission(this);
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private boolean isInteractive() {
        final PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return pm != null && (AndroidUtil.isLolliPopOrLater ? pm.isInteractive() : pm.isScreenOn());
    }

    @Override
    public void onVisibleBehindCanceled() {
        super.onVisibleBehindCanceled();
        stopPlayback();
        exitOK();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCurrentScreenOrientation = newConfig.orientation;
        if (mScreenOrientation == 98) {
            @StringRes int message;
            if (mCurrentScreenOrientation == Configuration.ORIENTATION_LANDSCAPE)
                message = R.string.locked_in_landscape_mode;
            else
                message = R.string.locked_in_portrait_mode;
            if (mRootView != null) UiTools.snacker(mRootView, message);
        }

        if (mTouchDelegate != null) {
            final DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            final ScreenConfig sc = new ScreenConfig(dm,
                    Math.max(dm.widthPixels, dm.heightPixels),
                    Math.min(dm.widthPixels, dm.heightPixels),
                    mCurrentScreenOrientation);
            mTouchDelegate.setScreenConfig(sc);
        }
        resetHudLayout();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void resetHudLayout() {
        if (mHudBinding == null) return;
        final RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)mHudBinding.playerOverlayButtons.getLayoutParams();
        final int orientation = getScreenOrientation(100);
        final boolean portrait = orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
                orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START, portrait ? 1 : 0);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END, portrait ? 1 : 0);
        layoutParams.addRule(RelativeLayout.BELOW, portrait ? R.id.player_overlay_length : R.id.player_overlay_seekbar);
        layoutParams.addRule(RelativeLayout.END_OF, portrait ? 0 : R.id.player_overlay_time);
        layoutParams.addRule(RelativeLayout.START_OF, portrait ? 0 : R.id.player_overlay_length);
        mHudBinding.playerOverlayButtons.setLayoutParams(layoutParams);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onStart() {
        super.onStart();
        mHelper.onStart();
        restoreBrightness();
        final IntentFilter filter = new IntentFilter(Constants.PLAY_FROM_SERVICE);
        filter.addAction(Constants.EXIT_PLAYER);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mServiceReceiver, filter);
        final IntentFilter btFilter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        btFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(mBtReceiver, btFilter);
        UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceReceiver);

        unregisterReceiver(mBtReceiver);
        if (mAlertDialog != null && mAlertDialog.isShowing())
            mAlertDialog.dismiss();
        if (mDisplayManager.isPrimary() && !isFinishing() && mService != null && mService.isPlaying()
                && "1".equals(mSettings.getString(PreferencesActivity.KEY_VIDEO_APP_SWITCH, "0"))) {
            switchToAudioMode(false);
        }

        cleanUI();
        stopPlayback();

        final SharedPreferences.Editor editor = mSettings.edit();
        if (mSavedTime != -1) editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, mSavedTime);

        editor.apply();

        saveBrightness();

        if (mService != null) mService.removeCallback(this);
        mHelper.onStop();
        // Clear Intent to restore playlist on activity restart
        setIntent(new Intent());
    }

    private void saveBrightness() {
        // Save brightness if user wants to
        if (mSettings.getBoolean("save_brightness", false)) {
            float brightness = getWindow().getAttributes().screenBrightness;
            if (brightness != -1f) {
                SharedPreferences.Editor editor = mSettings.edit();
                editor.putFloat("brightness_value", brightness);
                editor.apply();
            }
        }
    }

    private void restoreBrightness() {
        if (mSettings.getBoolean("save_brightness", false)) {
            float brightness = mSettings.getFloat("brightness_value", -1f);
            if (brightness != -1f) setWindowBrightness(brightness);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        if (mPlaylistModel != null) {
            mPlaylistModel.getDataset().removeObserver(mPlaylistObserver);
            mPlaylistModel.onCleared();
        }

        // Dismiss the presentation when the activity is not visible.
        mDisplayManager.release();
        mAudioManager = null;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void startPlayback() {
        /* start playback only when audio service and both surfaces are ready */
        if (mPlaybackStarted || mService == null)
            return;

        mPlaybackStarted = true;

        IVLCVout vlcVout = mService.getVout();
        if (vlcVout != null && vlcVout.areViewsAttached()) {
            if (mService.isPlayingPopup()) {
                mService.stop();
            } else
                vlcVout.detachViews();
        }
        final MediaPlayer mediaPlayer = mService.getMediaplayer();
        mediaPlayer.attachViews(mVideoLayout, mDisplayManager, true, false);
        final MediaPlayer.ScaleType size = mIsBenchmark ? MediaPlayer.ScaleType.SURFACE_FILL : MediaPlayer.ScaleType.values()[mSettings.getInt(PreferencesActivity.VIDEO_RATIO, MediaPlayer.ScaleType.SURFACE_BEST_FIT.ordinal())];
        mediaPlayer.setVideoScale(size);

        initUI();

        loadMedia();
    }

    private Observer<List<MediaWrapper>> mPlaylistObserver = new Observer<List<MediaWrapper>>() {
        @Override
        public void onChanged(List<MediaWrapper> mediaWrappers) {
            if (mediaWrappers != null) mPlaylistAdapter.update(mediaWrappers);
        }
    };

    private void initPlaylistUi() {
        if (mService != null && mService.hasPlaylist()) {
            mHasPlaylist = true;
            if (mPlaylistAdapter == null) {
                mPlaylistAdapter = new PlaylistAdapter(this);
                final LinearLayoutManager layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
                mPlaylist.setLayoutManager(layoutManager);
            }
            if (mPlaylistModel == null) {
                mPlaylistModel = ViewModelProviders.of(this).get(PlaylistModel.class);
                if (mService != null) mPlaylistModel.onConnected(mService);
                mPlaylistAdapter.setModel(mPlaylistModel);
                mPlaylistModel.getDataset().observe(this, mPlaylistObserver);
            }
            mPlaylistToggle.setVisibility(View.VISIBLE);
            mHudBinding.playlistPrevious.setVisibility(View.VISIBLE);
            mHudBinding.playlistNext.setVisibility(View.VISIBLE);
            mPlaylistToggle.setOnClickListener(VideoPlayerActivity.this);

            final ItemTouchHelper.Callback callback =  new SwipeDragItemTouchHelperCallback(mPlaylistAdapter);
            final ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(mPlaylist);
        }
    }

    private void initUI() {

        /* Dispatch ActionBar touch events to the Activity */
        mActionBarView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onTouchEvent(event);
                return true;
            }
        });

        /* Listen for changes to media routes. */
        if (!mIsBenchmark) mDisplayManager.setMediaRouterCallback();

        if (mRootView != null) mRootView.setKeepScreenOn(true);
    }

    private void setPlaybackParameters() {
        if (mAudioDelay != 0L && mAudioDelay != mService.getAudioDelay())
            mService.setAudioDelay(mAudioDelay);
        else if (mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn())
            toggleBtDelay(true);
        if (mSpuDelay != 0L && mSpuDelay != mService.getSpuDelay())
            mService.setSpuDelay(mSpuDelay);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void stopPlayback() {
        if (!mPlaybackStarted) return;

        if (!mDisplayManager.isPrimary() && !isFinishing()) {
            mPlaybackStarted = false;
            return;
        }
        mWasPaused = !mService.isPlaying();
        if (!isFinishing()) {
            mCurrentAudioTrack = mService.getAudioTrack();
            mCurrentSpuTrack = mService.getSpuTrack();
        }

        if (mMute) mute(false);

        mPlaybackStarted = false;

        mService.removeCallback(this);

        mHandler.removeCallbacksAndMessages(null);
        mService.getMediaplayer().detachViews();
        if (mService.hasMedia() && mSwitchingView) {
            if (BuildConfig.DEBUG) Log.d(TAG, "mLocation = \"" + mUri + "\"");
            if (mSwitchToPopup)
                mService.switchToPopup(mService.getCurrentMediaPosition());
            else {
                mService.getCurrentMediaWrapper().addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                mService.showWithoutParse(mService.getCurrentMediaPosition());
            }
            return;
        }

        if (mService.isSeekable()) {
            mSavedTime = getTime();
            long length = mService.getLength();
            //remove saved position if in the last 5 seconds
            if (length - mSavedTime < 5000) mSavedTime = 0;
            else mSavedTime -= 2000; // go back 2 seconds, to compensate loading time
        }

        mService.setRate(1.0f, false);
        mService.stop();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void cleanUI() {

        if (mRootView != null) mRootView.setKeepScreenOn(false);

        /* Stop listening for changes to media routes. */
        if (!mIsBenchmark) mDisplayManager.removeMediaRouterCallback();

        if (!mDisplayManager.isSecondary() && mService != null) mService.getMediaplayer().detachViews();

        mActionBarView.setOnTouchListener(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if(data == null) return;

        if(data.hasExtra(FilePickerFragmentKt.EXTRA_MRL)) {
            mService.addSubtitleTrack(Uri.parse(data.getStringExtra(FilePickerFragmentKt.EXTRA_MRL)), true);
            SlaveRepository.Companion.getInstance(this).saveSlave(mService.getCurrentMediaLocation(), Media.Slave.Type.Subtitle, 2, data.getStringExtra(FilePickerFragmentKt.EXTRA_MRL));
        } else if (BuildConfig.DEBUG) Log.d(TAG, "Subtitle selection dialog was cancelled");
    }

    public static void start(Context context, Uri uri) {
        start(context, uri, null, false, -1);
    }

    public static void start(Context context, Uri uri, boolean fromStart) {
        start(context, uri, null, fromStart, -1);
    }

    public static void start(Context context, Uri uri, String title) {
        start(context, uri, title, false, -1);
    }
    public static void startOpened(Context context, Uri uri, int openedPosition) {
        start(context, uri, null, false, openedPosition);
    }

    private static void start(Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        final Intent intent = getIntent(context, uri, title, fromStart, openedPosition);
        context.startActivity(intent);
    }

    public static Intent getIntent(String action, MediaWrapper mw, boolean fromStart, int openedPosition) {
        return getIntent(action, VLCApplication.getAppContext(), mw.getUri(), mw.getTitle(), fromStart, openedPosition);
    }

    @NonNull
    public static Intent getIntent(Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        return getIntent(Constants.PLAY_FROM_VIDEOGRID, context, uri, title, fromStart, openedPosition);
    }

    @NonNull
    public static Intent getIntent(String action, Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        final Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.setAction(action);
        intent.putExtra(Constants.PLAY_EXTRA_ITEM_LOCATION, uri);
        intent.putExtra(Constants.PLAY_EXTRA_ITEM_TITLE, title);
        intent.putExtra(Constants.PLAY_EXTRA_FROM_START, fromStart);

        if (openedPosition != -1 || !(context instanceof Activity)) {
            if (openedPosition != -1)
                intent.putExtra(Constants.PLAY_EXTRA_OPENED_POSITION, openedPosition);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (VLCApplication.SLEEP_INTENT.equalsIgnoreCase(action)) exitOK();
        }
    };

    public void exit(int resultCode) {
        if (isFinishing()) return;
        final Intent resultIntent = new Intent(ACTION_RESULT);
        if (mUri != null && mService != null) {
            if (AndroidUtil.isNougatOrLater) resultIntent.putExtra(EXTRA_URI, mUri.toString());
            else resultIntent.setData(mUri);
            resultIntent.putExtra(EXTRA_POSITION, mService.getTime());
            resultIntent.putExtra(EXTRA_DURATION, mService.getLength());
        }
        setResult(resultCode, resultIntent);
        finish();
    }

    private void exitOK() {
        exit(RESULT_OK);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if (mIsLoading) return false;
        showOverlay();
        return true;
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return !mIsLoading && mTouchDelegate != null && mTouchDelegate.dispatchGenericMotionEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mOptionsDelegate != null && mOptionsDelegate.isShowing()) {
            mOptionsDelegate.hide();
        } else if (mLockBackButton) {
            mLockBackButton = false;
            mHandler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000);
            Toast.makeText(getApplicationContext(), getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show();
        } else if(isPlaylistVisible()) {
            togglePlaylist();
        } else if (isPlaybackSettingActive()){
            endPlaybackSetting();
        } else if (mIsTv && mShowing && !mIsLocked) {
            hideOverlay(true);
        } else {
            exitOK();
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mService == null || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B)
            return super.onKeyDown(keyCode, event);
        if (isPlaybackSettingActive() || isOptionsListShowing()) return false;
        if (mIsLoading) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_S:
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    exitOK();
                    return true;
            }
            return false;
        }
        //Handle playlist d-pad navigation
        if (mPlaylist.hasFocus()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    mPlaylistAdapter.setCurrentIndex(mPlaylistAdapter.getCurrentIndex() - 1);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mPlaylistAdapter.setCurrentIndex(mPlaylistAdapter.getCurrentIndex() + 1);
                    break;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    mService.playIndex(mPlaylistAdapter.getCurrentIndex());
                    break;
            }
            return true;
        }
        if (mShowing || (mFov == 0f && keyCode == KeyEvent.KEYCODE_DPAD_DOWN))
            showOverlayTimeout(OVERLAY_TIMEOUT);
        switch (keyCode) {
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekDelta(10000);
                return true;
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekDelta(-10000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                seekDelta(60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                seekDelta(-60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (mHudBinding != null && mHudBinding.progressOverlay.getVisibility() == View.VISIBLE)
                    return false;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) //prevent conflict with remote control
                    return super.onKeyDown(keyCode, event);
                else
                    doPlayPause();
                return true;
            case KeyEvent.KEYCODE_O:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_MENU:
                showAdvancedOptions();
                return true;
            case KeyEvent.KEYCODE_V:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
            case KeyEvent.KEYCODE_BUTTON_X:
                onAudioSubClick(mHudBinding != null ? mHudBinding.playerOverlayTracks : null);
                return true;
            case KeyEvent.KEYCODE_N:
                showNavMenu();
                return true;
            case KeyEvent.KEYCODE_A:
                resizeVideo();
                return true;
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                updateMute();
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                exitOK();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (!mShowing) {
                    if (mFov == 0f) seekDelta(-10000);
                    else mService.updateViewpoint(-5f, 0f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (!mShowing) {
                    if (mFov == 0f) seekDelta(10000);
                    else mService.updateViewpoint(5f, 0f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (event.isCtrlPressed()) {
                    volumeUp();
                    return true;
                } else if (!mShowing) {
                    if (mFov == 0f) showAdvancedOptions();
                    else mService.updateViewpoint(0f, -5f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (event.isCtrlPressed()) {
                    volumeDown();
                    return true;
                } else if (!mShowing && mFov != 0f) {
                    mService.updateViewpoint(0f, 5f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (!mShowing) {
                    doPlayPause();
                    return true;
                }
            case KeyEvent.KEYCODE_ENTER:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else
                    return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_J:
                delayAudio(-50000L);
                return true;
            case KeyEvent.KEYCODE_K:
                delayAudio(50000L);
                return true;
            case KeyEvent.KEYCODE_G:
                delaySubs(-50000L);
                return true;
            case KeyEvent.KEYCODE_H:
                delaySubs(50000L);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                volumeDown();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                volumeUp();
                return true;
            case KeyEvent.KEYCODE_CAPTIONS:
                selectSubtitles();
                return true;
            case KeyEvent.KEYCODE_PLUS:
                mService.setRate(mService.getRate()*1.2f, true);
                return true;
            case KeyEvent.KEYCODE_EQUALS:
                if (event.isShiftPressed()) {
                    mService.setRate(mService.getRate() * 1.2f, true);
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MINUS:
                mService.setRate(mService.getRate()/1.2f, true);
                return true;
            case KeyEvent.KEYCODE_C:
                resizeVideo();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    int getAudioMax() {
        return mAudioMax;
    }

    AudioManager getAudiomanager() {
        return mAudioManager;
    }

    float getVolume() {
        return mVol;
    }

    void setVolume(float vol) {
        mVol = vol;
    }

    float getOriginalVol() {
        return mOriginalVol;
    }

    void setOriginalVol(float vol) {
        mOriginalVol = vol;
    }

    boolean isAudioBoostEnabled() {
        return audioBoostEnabled;
    }

    private void volumeUp() {
        if (mMute) {
            updateMute();
        } else {
            int volume;
            if (mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < mAudioMax)
                volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1;
            else
                volume = Math.round(((float)mService.getVolume())*mAudioMax/100 + 1);
            volume = Math.min(Math.max(volume, 0), mAudioMax * (audioBoostEnabled ? 2 : 1));
            setAudioVolume(volume);
        }
    }

    private void volumeDown() {
        int vol;
        if (mService.getVolume() > 100)
            vol = Math.round(((float)mService.getVolume())*mAudioMax/100 - 1);
        else
            vol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1;
        vol = Math.min(Math.max(vol, 0), mAudioMax * (audioBoostEnabled ? 2 : 1));
        mOriginalVol = vol;
        setAudioVolume(vol);
    }

    boolean navigateDvdMenu(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                mService.navigate(MediaPlayer.Navigate.Up);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mService.navigate(MediaPlayer.Navigate.Down);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mService.navigate(MediaPlayer.Navigate.Left);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mService.navigate(MediaPlayer.Navigate.Right);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_A:
                mService.navigate(MediaPlayer.Navigate.Activate);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void showAudioDelaySetting() {
        mPlaybackSetting = DelayState.AUDIO;
        showDelayControls();
    }

    @Override
    public void showSubsDelaySetting() {
        mPlaybackSetting = DelayState.SUBS;
        showDelayControls();
    }

    public void showDelayControls(){
        if (mTouchDelegate != null) mTouchDelegate.clearTouchAction();
        if (!mDisplayManager.isPrimary()) showOverlayTimeout(OVERLAY_INFINITE);
        ViewStubCompat vsc = findViewById(R.id.player_overlay_settings_stub);
        if (vsc != null) {
            vsc.inflate();
            mPlaybackSettingPlus = findViewById(R.id.player_delay_plus);
            mPlaybackSettingMinus = findViewById(R.id.player_delay_minus);

        }
        mPlaybackSettingMinus.setOnClickListener(this);
        mPlaybackSettingPlus.setOnClickListener(this);
        mPlaybackSettingMinus.setOnTouchListener(new OnRepeatListener(this));
        mPlaybackSettingPlus.setOnTouchListener(new OnRepeatListener(this));
        mPlaybackSettingMinus.setVisibility(View.VISIBLE);
        mPlaybackSettingPlus.setVisibility(View.VISIBLE);
        mPlaybackSettingPlus.requestFocus();
        initPlaybackSettingInfo();
    }


    private void initPlaybackSettingInfo() {
        initInfoOverlay();
        UiTools.setViewVisibility(mVerticalBar, View.GONE);
        UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        String text = "";
        if (mPlaybackSetting == DelayState.AUDIO) {
            text += getString(R.string.audio_delay)+"\n";
            text += mService.getAudioDelay() / 1000L;
            text += " ms";
        } else if (mPlaybackSetting == DelayState.SUBS) {
            text += getString(R.string.spu_delay)+"\n";
            text += mService.getSpuDelay() / 1000L;
            text += " ms";
        } else
            text += "0";
        mInfo.setText(text);
    }

    @Override
    public void endPlaybackSetting() {
        mService.saveMediaMeta();
        if (mPlaybackSetting == DelayState.AUDIO
                && (mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn())) {
            String msg = getString(R.string.audio_delay) + "\n"
                    + mService.getAudioDelay() / 1000L
                    + " ms";
            Snackbar sb = Snackbar.make(mInfo, msg, Snackbar.LENGTH_LONG);
            sb.setAction(R.string.save_bluetooth_delay, mBtSaveListener);
            sb.show();
        }
        mPlaybackSetting = DelayState.OFF;
        if (mPlaybackSettingMinus != null) {
            mPlaybackSettingMinus.setOnClickListener(null);
            mPlaybackSettingMinus.setVisibility(View.INVISIBLE);
        }
        if (mPlaybackSettingPlus != null) {
            mPlaybackSettingPlus.setOnClickListener(null);
            mPlaybackSettingPlus.setVisibility(View.INVISIBLE);
        }
        UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
        mInfo.setText("");
        if (mHudBinding != null)
            mHudBinding.playerOverlayPlay.requestFocus();
    }

    public void delayAudio(long delta) {
        initInfoOverlay();
        long delay = mService.getAudioDelay()+delta;
        mService.setAudioDelay(delay);
        mInfo.setText(getString(R.string.audio_delay)+"\n"+(delay/1000L)+" ms");
        mAudioDelay = delay;
        if (!isPlaybackSettingActive()) {
            mPlaybackSetting = DelayState.AUDIO;
            initPlaybackSettingInfo();
        }
    }

    boolean isPlaybackSettingActive() {
        return mPlaybackSetting != DelayState.OFF;
    }

    public void delaySubs(long delta) {
        initInfoOverlay();
        long delay = mService.getSpuDelay()+delta;
        mService.setSpuDelay(delay);
        mInfo.setText(getString(R.string.spu_delay) + "\n" + (delay / 1000L) + " ms");
        mSpuDelay = delay;
        if (!isPlaybackSettingActive()) {
            mPlaybackSetting = DelayState.SUBS;
            initPlaybackSettingInfo();
        }
    }

    /**
     * Lock screen rotation
     */
    private void lockScreen() {
        if (mScreenOrientation != 100) {
            mScreenOrientationLock = getRequestedOrientation();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            else
                setRequestedOrientation(getScreenOrientation(100));
        }
        showInfo(R.string.locked, 1000);
        if (mHudBinding != null) {
            mHudBinding.lockOverlayButton.setImageResource(R.drawable.ic_locked_circle);
            mHudBinding.playerOverlayTime.setEnabled(false);
            mHudBinding.playerOverlaySeekbar.setEnabled(false);
            mHudBinding.playerOverlayLength.setEnabled(false);
            mHudBinding.playerOverlaySize.setEnabled(false);
            mHudBinding.playlistNext.setEnabled(false);
            mHudBinding.playlistPrevious.setEnabled(false);
        }
        hideOverlay(true);
        mLockBackButton = true;
        mIsLocked = true;
    }

    /**
     * Remove screen lock
     */
    private void unlockScreen() {
        if(mScreenOrientation != 100)
            setRequestedOrientation(mScreenOrientationLock);
        showInfo(R.string.unlocked, 1000);
        if (mHudBinding != null) {
            mHudBinding.lockOverlayButton.setImageResource(R.drawable.ic_lock_circle);
            mHudBinding.playerOverlayTime.setEnabled(true);
            mHudBinding.playerOverlaySeekbar.setEnabled(mService == null || mService.isSeekable());
            mHudBinding.playerOverlayLength.setEnabled(true);
            mHudBinding.playerOverlaySize.setEnabled(true);
            mHudBinding.playlistNext.setEnabled(true);
            mHudBinding.playlistPrevious.setEnabled(true);
        }
        mShowing = false;
        mIsLocked = false;
        showOverlay();
        mLockBackButton = false;
    }

    /**
     * Show text in the info view and vertical progress bar for "duration" milliseconds
     * @param text
     * @param duration
     * @param barNewValue new volume/brightness value (range: 0 - 15)
     */
    private void showInfoWithVerticalBar(String text, int duration, int barNewValue, int max) {
        showInfo(text, duration);
        if (mVerticalBarProgress == null)
            return;
        LinearLayout.LayoutParams layoutParams;
        if (barNewValue <= 100) {
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
            layoutParams.weight = barNewValue * 100 / max;
            mVerticalBarProgress.setLayoutParams(layoutParams);
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarBoostProgress.getLayoutParams();
            layoutParams.weight = 0;
            mVerticalBarBoostProgress.setLayoutParams(layoutParams);
        } else {
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
            layoutParams.weight = 100 * 100 / max;
            mVerticalBarProgress.setLayoutParams(layoutParams);
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarBoostProgress.getLayoutParams();
            layoutParams.weight = (barNewValue - 100) * 100 / max;
            mVerticalBarBoostProgress.setLayoutParams(layoutParams);
        }
        mVerticalBar.setVisibility(View.VISIBLE);
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    void showInfo(String text, int duration) {
        initInfoOverlay();
        UiTools.setViewVisibility(mVerticalBar, View.GONE);
        UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    private void initInfoOverlay() {
        ViewStubCompat vsc = findViewById(R.id.player_info_stub);
        if (vsc != null) {
            vsc.inflate();
            // the info textView is not on the overlay
            mInfo = findViewById(R.id.player_overlay_textinfo);
            mOverlayInfo = findViewById(R.id.player_overlay_info);
            mVerticalBar = findViewById(R.id.verticalbar);
            mVerticalBarProgress = findViewById(R.id.verticalbar_progress);
            mVerticalBarBoostProgress = findViewById(R.id.verticalbar_boost_progress);
        }
    }

    void showInfo(int textid, int duration) {
        showInfo(getString(textid), duration);
    }

    /**
     * hide the info view with "delay" milliseconds delay
     * @param delay
     */
    private void hideInfo(int delay) {
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, delay);
    }

    /**
     * hide the info view
     */
    private void hideInfo() {
        hideInfo(0);
    }

    private void fadeOutInfo() {
        if (mOverlayInfo != null && mOverlayInfo.getVisibility() == View.VISIBLE) {
            mOverlayInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerActivity.this, android.R.anim.fade_out));
            UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
        }
    }

    /* PlaybackService.Callback */

    @Override
    public void update() {
        if (mService == null || mPlaylistAdapter == null) return;
        mPlaylistModel.update();
    }

    @Override
    public void onMediaEvent(Media.Event event) {
        switch (event.type) {
            case Media.Event.ParsedChanged:
                updateNavStatus();
                break;
            case Media.Event.MetaChanged:
                break;
        }
    }

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Playing:
                onPlaying();
                break;
            case MediaPlayer.Event.Paused:
                updateOverlayPausePlay();
                break;
            case MediaPlayer.Event.EncounteredError:
                encounteredError();
                break;
            case MediaPlayer.Event.Vout:
                updateNavStatus();
                if (event.getVoutCount() > 0 && mService != null)
                    mService.getMediaplayer().updateVideoSurfaces();
                if (mMenuIdx == -1)
                    handleVout(event.getVoutCount());
                break;
            case MediaPlayer.Event.ESAdded:
                if (mMenuIdx == -1) {
                    MediaWrapper media = mMedialibrary.findMedia(mService.getCurrentMediaWrapper());
                    if (media == null)
                        return;
                    if (event.getEsChangedType() == Media.Track.Type.Audio) {
                        setESTrackLists();
                        int audioTrack = (int) media.getMetaLong(MediaWrapper.META_AUDIOTRACK);
                        if (audioTrack != 0 || mCurrentAudioTrack != -2)
                            mService.setAudioTrack(media.getId() == 0L ? mCurrentAudioTrack : audioTrack);
                    } else if (event.getEsChangedType() == Media.Track.Type.Text) {
                        setESTrackLists();
                        int spuTrack = (int) media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK);
                        if (spuTrack != 0 || mCurrentSpuTrack != -2)
                            mService.setSpuTrack(media.getId() == 0L ? mCurrentAudioTrack : spuTrack);
                    }
                }
            case MediaPlayer.Event.ESDeleted:
                if (mMenuIdx == -1 && event.getEsChangedType() == Media.Track.Type.Video) {
                    mHandler.removeMessages(CHECK_VIDEO_TRACKS);
                    mHandler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000);
                }
                invalidateESTracks(event.getEsChangedType());
                break;
            case MediaPlayer.Event.ESSelected:
                if (event.getEsChangedType() == Media.VideoTrack.Type.Video) {
                    Media.VideoTrack vt = mService.getCurrentVideoTrack();
                    if (vt != null)
                        mFov = vt.projection == Media.VideoTrack.Projection.Rectangular ? 0f : DEFAULT_FOV;
                }
                break;
            case MediaPlayer.Event.SeekableChanged:
                updateSeekable(event.getSeekable());
                break;
            case MediaPlayer.Event.PausableChanged:
                updatePausable(event.getPausable());
                break;
            case MediaPlayer.Event.Buffering:
                if (!mIsPlaying)
                    break;
                if (event.getBuffering() == 100f)
                    stopLoading();
                else if (!mHandler.hasMessages(LOADING_ANIMATION) && !mIsLoading
                        && (mTouchDelegate == null || !mTouchDelegate.isSeeking()) && !mDragging)
                    mHandler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY);
                break;
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    /**
     * Handle resize of the surface and the overlay
     */
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (mService != null) switch (msg.what) {
                case FADE_OUT:
                    hideOverlay(false);
                    break;
                case FADE_OUT_INFO:
                    fadeOutInfo();
                    break;
                case START_PLAYBACK:
                    startPlayback();
                    break;
                case AUDIO_SERVICE_CONNECTION_FAILED:
                    exit(RESULT_CONNECTION_FAILED);
                    break;
                case RESET_BACK_LOCK:
                    mLockBackButton = true;
                    break;
                case CHECK_VIDEO_TRACKS:
                    if (mService.getVideoTracksCount() < 1 && mService.getAudioTracksCount() > 0) {
                        Log.i(TAG, "No video track, open in audio mode");
                        switchToAudioMode(true);
                    }
                    break;
                case LOADING_ANIMATION:
                    startLoading();
                    break;
                case HIDE_INFO:
                    hideOverlay(true);
                    break;
                case SHOW_INFO:
                    showOverlay();
                    break;
            }
        }
    };

    private void onPlaying() {
        mIsPlaying = true;
        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if (mw == null) return;
        setPlaybackParameters();
        stopLoading();
        updateOverlayPausePlay();
        updateNavStatus();
        if (!mw.hasFlag(MediaWrapper.MEDIA_PAUSED))
            mHandler.sendEmptyMessageDelayed(FADE_OUT, OVERLAY_TIMEOUT);
        else {
            mw.removeFlags(MediaWrapper.MEDIA_PAUSED);
            mWasPaused = false;
        }
        setESTracks();
        if (mTitle != null && mTitle.length() == 0)
            mTitle.setText(mw.getTitle());
        // Get possible subtitles
        observeDownloadedSubtitles();
    }

    private void encounteredError() {
        if (isFinishing() || mService.hasNext()) return;
        /* Encountered Error, exit player with a message */
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        exit(RESULT_PLAYBACK_ERROR);
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        exit(RESULT_PLAYBACK_ERROR);
                    }
                })
                .setTitle(R.string.encountered_error_title)
                .setMessage(R.string.encountered_error_message)
                .create();
        mAlertDialog.show();
    }

    private final Runnable mSwitchAudioRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDisplayManager.isPrimary() && mService.hasMedia() && mService.getVideoTracksCount() == 0) {
                Log.i(TAG, "Video track lost, switching to audio");
                mSwitchingView = true;
                exit(RESULT_VIDEO_TRACK_LOST);
            }
        }
    };

    private void handleVout(int voutCount) {
        mHandler.removeCallbacks(mSwitchAudioRunnable);

        final IVLCVout vlcVout = mService.getVout();
        if (vlcVout == null) return;
        if (mDisplayManager.isPrimary() && vlcVout.areViewsAttached() && voutCount == 0) {
            mHandler.postDelayed(mSwitchAudioRunnable, 4000);
        }
    }

    @Override
    public void recreate() {
        mHandler.removeCallbacks(mSwitchAudioRunnable);
        super.recreate();
    }

    public void switchToAudioMode(boolean showUI) {
        if (mService == null) return;
        mSwitchingView = true;
        // Show the MainActivity if it is not in background.
        if (showUI) {
            Intent i = new Intent(this, mIsTv ? AudioPlayerActivity.class : MainActivity.class);
            startActivity(i);
        }
        exitOK();
    }

    @Override
    public boolean isInPictureInPictureMode() {
        return AndroidUtil.isNougatOrLater && super.isInPictureInPictureMode();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (mService != null) mService.getMediaplayer().updateVideoSurfaces();
    }

    void sendMouseEvent(int action, int x, int y) {
        if (mService == null) return;
        final IVLCVout vlcVout = mService.getVout();
        vlcVout.sendMouseEvent(action, 0, x, y);
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mService != null && mTouchDelegate != null && mTouchDelegate.onTouchEvent(event);
    }

    boolean updateViewpoint(float yaw, float pitch, float fov) {
        return mService.updateViewpoint(yaw, pitch, 0, fov, false);
    }

    void initAudioVolume() {
        if (mService.getVolume() <= 100) {
            mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mOriginalVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        else {
            mVol = ((float)mService.getVolume()) * mAudioMax / 100;
        }
    }

    public void toggleOverlay() {
        if (!mShowing) showOverlay();
        else hideOverlay(true);
    }

    //Toast that appears only once
    public void displayWarningToast() {
        if(warningToast != null) warningToast.cancel();
        warningToast = Toast.makeText(getApplication(), R.string.audio_boost_warning, Toast.LENGTH_SHORT);
        warningToast.show();
    }

    void setAudioVolume(int vol) {
        if (AndroidUtil.isNougatOrLater && (vol <= 0 ^ mMute)) {
            mute(!mMute);
            return; //Android N+ throws "SecurityException: Not allowed to change Do Not Disturb state"
        }

        /* Since android 4.3, the safe volume warning dialog is displayed only with the FLAG_SHOW_UI flag.
         * We don't want to always show the default UI volume, so show it only when volume is not set. */
        if (vol <= mAudioMax) {
            mService.setVolume(100);
            if (vol !=  mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                try {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                    // High Volume warning can block volume setting
                    if (mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != vol)
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, AudioManager.FLAG_SHOW_UI);
                } catch (RuntimeException ignored) {} //Some device won't allow us to change volume
            }
            vol = Math.round(vol * 100 / mAudioMax);
        } else {
            vol = Math.round(vol * 100 / mAudioMax);
            mService.setVolume(Math.round(vol));
        }
        showInfoWithVerticalBar(getString(R.string.volume) + "\n" + Integer.toString(vol) + '%', 1000, vol, audioBoostEnabled ? 200 : 100);
    }

    private void mute(boolean mute) {
        mMute = mute;
        if (mMute)
            mVolSave = mService.getVolume();
        mService.setVolume(mMute ? 0 : mVolSave);
    }

    private void updateMute () {
        mute(!mMute);
        showInfo(mMute ? R.string.sound_off : R.string.sound_on, 1000);
    }

    void changeBrightness(float delta) {
        // Estimate and adjust Brightness
        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightness =  Math.min(Math.max(lp.screenBrightness + delta, 0.01f), 1f);
        setWindowBrightness(brightness);
        brightness = Math.round(brightness * 100);
        showInfoWithVerticalBar(getString(R.string.brightness) + "\n" + (int) brightness + '%', 1000, (int) brightness, 100);
    }

    void setWindowBrightness(float brightness) {
        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness;
        // Set Brightness
        getWindow().setAttributes(lp);
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlayTimeout(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay(true);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!isFinishing() && fromUser && mService.isSeekable()) {
                seek(progress);
                showInfo(Tools.millisToString(progress), 1000);
            }
        }
    };

    public void onAudioSubClick(View anchor) {
        int flags = 0;
        if (enableSubs) {
            flags |= Constants.CTX_DOWNLOAD_SUBTITLES_PLAYER;
            if (mDisplayManager.isPrimary()) flags |= Constants.CTX_PICK_SUBS;
        }
        if (mService.getVideoTracksCount() > 2) flags |= Constants.CTX_VIDEO_TRACK;
        if (mService.getAudioTracksCount() > 0) flags |= Constants.CTX_AUDIO_TRACK;
        if (mService.getSpuTracksCount() > 0) flags |= Constants.CTX_SUBS_TRACK;
        ContextSheetKt.showContext(this, this, -1, getString(R.string.ctx_player_tracks_title), flags);
        hideOverlay(false);
    }

    @Override
    public void onCtxAction(int position, int option) {
        if (mUri == null) return;
        switch (option) {
            case Constants.CTX_VIDEO_TRACK:
                selectVideoTrack();
                break;
            case Constants.CTX_AUDIO_TRACK:
                selectAudioTrack();
                break;
            case Constants.CTX_SUBS_TRACK:
                selectSubtitles();
                break;
            case Constants.CTX_PICK_SUBS:
                mShowingDialog = true;
                final Intent filePickerIntent = new Intent(this, FilePickerActivity.class);
                filePickerIntent.setData(Uri.parse(FileUtils.getParent(mUri.toString())));
                startActivityForResult(filePickerIntent, 0);
                break;
            case Constants.CTX_DOWNLOAD_SUBTITLES_PLAYER:
                final MediaWrapper mw = mService != null ? mService.getCurrentMediaWrapper() : null;
                if (mw != null) MediaUtils.INSTANCE.getSubs(VideoPlayerActivity.this, mw);
                break;
        }
    }

    @Override
    public void onPopupMenu(View anchor, final int position, MediaWrapper media) {
        final PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.audio_player, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if(item.getItemId() == R.id.audio_player_mini_remove) {
                    if (mService != null) {
                        mPlaylistAdapter.remove(position);
                        mService.remove(position);
                        return true;
                    }
                }
                return false;
            }
        });
        popupMenu.show();
    }

    @Override
    public void onSelectionSet(int position) {
        mPlaylist.scrollToPosition(position);
    }

    @Override
    public void playItem(int position, MediaWrapper item) {
        mService.playIndex(position);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.orientation_toggle:
                toggleOrientation();
                break;
            case R.id.playlist_toggle:
                togglePlaylist();
                break;
            case R.id.player_overlay_forward:
                seekDelta(10000);
                break;
            case R.id.player_overlay_rewind:
                seekDelta(-10000);
                break;
            case R.id.player_overlay_navmenu:
                showNavMenu();
                break;
            case R.id.player_overlay_length:
            case R.id.player_overlay_time:
                toggleTimeDisplay();
                break;
            case R.id.player_delay_minus:
                if (mPlaybackSetting == DelayState.AUDIO)
                    delayAudio(-50000);
                else if (mPlaybackSetting == DelayState.SUBS)
                    delaySubs(-50000);
                break;
            case R.id.player_delay_plus:
                if (mPlaybackSetting == DelayState.AUDIO)
                    delayAudio(50000);
                else if (mPlaybackSetting == DelayState.SUBS)
                    delaySubs(50000);
                break;
            case R.id.video_renderer:
                if (getSupportFragmentManager().findFragmentByTag("renderers") == null)
                    new RenderersDialog().show(getSupportFragmentManager(), "renderers");
                break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.orientation_toggle:
                return resetOrientation();
        }

        return false;
    }

    public void toggleTimeDisplay() {
        sDisplayRemainingTime = !sDisplayRemainingTime;
        showOverlay();
        mSettings.edit().putBoolean(KEY_REMAINING_TIME_DISPLAY, sDisplayRemainingTime).apply();
    }

    public void toggleLock() {
        if (mIsLocked) unlockScreen();
        else lockScreen();
    }

    boolean isLoading() {
        return mIsLoading;
    }

    boolean isShowing() {
        return mShowing;
    }

    boolean isLocked() {
        return mIsLocked;
    }

    float getFov() {
        return mFov;
    }

    void setFov(float fov) {
        mFov = fov;
    }

    boolean isOnPrimaryDisplay() {
        return mDisplayManager.isPrimary();
    }

    public MediaPlayer.ScaleType getCurrentScaleType() {
        return mService == null ? MediaPlayer.ScaleType.SURFACE_BEST_FIT : mService.getMediaplayer().getVideoScale();
    }

    public boolean toggleLoop(View v) {
        if (mService == null) return false;
        if (mService.getRepeatType() == Constants.REPEAT_ONE) {
            showInfo(getString(R.string.repeat), 1000);
            mService.setRepeatType(Constants.REPEAT_NONE);
        } else {
            mService.setRepeatType(Constants.REPEAT_ONE);
            showInfo(getString(R.string.repeat_single), 1000);
        }
        return true;
    }

    @Override
    public void onStorageAccessGranted() {
        mHandler.sendEmptyMessage(START_PLAYBACK);
    }

    public boolean isOptionsListShowing() {
        return mOptionsDelegate != null && mOptionsDelegate.isShowing();
    }

    public void hideOptions() {
        if (mOptionsDelegate != null) mOptionsDelegate.hide();
    }


    private interface TrackSelectedListener {
        void onTrackSelected(int trackID);
    }

    private void selectTrack(final MediaPlayer.TrackDescription[] tracks, int currentTrack, int titleId,
                             final TrackSelectedListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        if (tracks == null)
            return;
        final String[] nameList = new String[tracks.length];
        final int[] idList = new int[tracks.length];
        int i = 0;
        int listPosition = 0;
        for (MediaPlayer.TrackDescription track : tracks) {
            idList[i] = track.id;
            nameList[i] = track.name;
            // map the track position to the list position
            if (track.id == currentTrack)
                listPosition = i;
            i++;
        }

        if (!isFinishing()) {
            mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                    .setTitle(titleId)
                    .setSingleChoiceItems(nameList, listPosition, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int listPosition) {
                            int trackID = -1;
                            // Reverse map search...
                            for (MediaPlayer.TrackDescription track : tracks) {
                                if (idList[listPosition] == track.id) {
                                    trackID = track.id;
                                    break;
                                }
                            }
                            listener.onTrackSelected(trackID);
                            dialog.dismiss();
                        }
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
            mAlertDialog.show();
        }
    }

    private void selectVideoTrack() {
        setESTrackLists();
        selectTrack(mVideoTracksList, mService.getVideoTrack(), R.string.track_video,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mService == null) return;
                        mService.setVideoTrack(trackID);
                        seek(mService.getTime());
                    }
                });
    }

    private void selectAudioTrack() {
        setESTrackLists();
        selectTrack(mAudioTracksList, mService.getAudioTrack(), R.string.track_audio,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mService == null) return;
                        mService.setAudioTrack(trackID);
                        MediaWrapper mw = mMedialibrary.findMedia(mService.getCurrentMediaWrapper());
                        if (mw != null && mw.getId() != 0L)
                            mw.setLongMeta(MediaWrapper.META_AUDIOTRACK, trackID);
                    }
                });
    }

    private void selectSubtitles() {
        setESTrackLists();
        selectTrack(mSubtitleTracksList, mService.getSpuTrack(), R.string.track_text,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mService == null)
                            return;
                        mService.setSpuTrack(trackID);
                        final MediaWrapper mw = mMedialibrary.findMedia(mService.getCurrentMediaWrapper());
                        if (mw != null && mw.getId() != 0L)
                            mw.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, trackID);
                    }
                });
    }

    private void showNavMenu() {
        if (mMenuIdx >= 0)
            mService.setTitleIdx(mMenuIdx);
    }

    private void updateSeekable(boolean seekable) {
        if (mHudBinding == null) return;
        mHudBinding.playerOverlayRewind.setEnabled(seekable);
        mHudBinding.playerOverlayRewind.setImageResource(seekable
                ? R.drawable.ic_rewind_circle
                : R.drawable.ic_rewind_circle_disable_o);
        mHudBinding.playerOverlayForward.setEnabled(seekable);
        mHudBinding.playerOverlayForward.setImageResource(seekable
                ? R.drawable.ic_forward_circle
                : R.drawable.ic_forward_circle_disable_o);
        if (!mIsLocked)
            mHudBinding.playerOverlaySeekbar.setEnabled(seekable);
    }

    private void updatePausable(boolean pausable) {
        if (mHudBinding == null) return;
        mHudBinding.playerOverlayPlay.setEnabled(pausable);
        if (!pausable)
            mHudBinding.playerOverlayPlay.setImageResource(R.drawable.ic_play_circle_disable_o);
    }

    public void doPlayPause() {
        if (!mService.isPausable()) return;
        if (mService.isPlaying()) {
            showOverlayTimeout(OVERLAY_INFINITE);
            pause();
        } else {
            hideOverlay(true);
            play();
        }
    }

    private long getTime() {
        long time = mService.getTime();
        if (mForcedTime != -1 && mLastTime != -1) {
            /* XXX: After a seek, mService.getTime can return the position before or after
             * the seek position. Therefore we return mForcedTime in order to avoid the seekBar
             * to move between seek position and the actual position.
             * We have to wait for a valid position (that is after the seek position).
             * to re-init mLastTime and mForcedTime to -1 and return the actual position.
             */
            if (mLastTime > mForcedTime) {
                if (time <= mLastTime && time > mForcedTime || time > mLastTime)
                    mLastTime = mForcedTime = -1;
            } else {
                if (time > mForcedTime)
                    mLastTime = mForcedTime = -1;
            }
        } else if (time == 0) {
            final MediaWrapper mw = mService.getCurrentMediaWrapper();
            if (mw != null)
                time = (int) mw.getTime();
        }
        return mForcedTime == -1 ? time : mForcedTime;
    }

    protected void seek(long position) {
        seek(position, mService.getLength());
    }

    void seek(long position, long length) {
        mForcedTime = position;
        mLastTime = mService.getTime();
        mService.seek(position, length);
        mService.getPlaylistManager().getPlayer().updateProgress(position);
    }

    void seekDelta(int delta) {
        // unseekable stream
        if (mService.getLength() <= 0 || !mService.isSeekable()) return;

        long position = getTime() + delta;
        if (position < 0) position = 0;
        seek(position);
        StringBuilder sb = new StringBuilder();
        if (delta > 0f)
            sb.append('+');
        sb.append((int)(delta/1000f))
                .append("s (")
                .append(Tools.millisToString(mService.getTime()))
                .append(')');
        showInfo(sb.toString(), 1000);
    }

    private void initSeekButton() {
        mHudBinding.playerOverlayRewind.setOnClickListener(this);
        mHudBinding.playerOverlayForward.setOnClickListener(this);
        mHudBinding.playerOverlayRewind.setOnTouchListener(new OnRepeatListener(this));
        mHudBinding.playerOverlayForward.setOnTouchListener(new OnRepeatListener(this));
    }

    public void resizeVideo() {
        final int next = (mService.getMediaplayer().getVideoScale().ordinal()+1)%MediaPlayer.SURFACE_SCALES_COUNT;
        final MediaPlayer.ScaleType scale = MediaPlayer.ScaleType.values()[next];
        setVideoScale(scale);
    }

    void setVideoScale(MediaPlayer.ScaleType scale) {
        mService.getMediaplayer().setVideoScale(scale);
        final MediaPlayer.ScaleType newSize = mService.getMediaplayer().getVideoScale();
        switch (newSize) {
            case SURFACE_BEST_FIT:
                showInfo(R.string.surface_best_fit, 1000);
                break;
            case SURFACE_FIT_SCREEN:
                showInfo(R.string.surface_fit_screen, 1000);
                break;
            case SURFACE_FILL:
                showInfo(R.string.surface_fill, 1000);
                break;
            case SURFACE_16_9:
                showInfo("16:9", 1000);
                break;
            case SURFACE_4_3:
                showInfo("4:3", 1000);
                break;
            case SURFACE_ORIGINAL:
                showInfo(R.string.surface_original, 1000);
                break;
        }
        mSettings.edit()
                .putInt(PreferencesActivity.VIDEO_RATIO, newSize.ordinal())
                .apply();
    }

    /**
     * show overlay
     * @param forceCheck: adjust the timeout in function of playing state
     */
    private void showOverlay(boolean forceCheck) {
        if (forceCheck)
            mOverlayTimeout = 0;
        showOverlayTimeout(0);
    }

    /**
     * show overlay with the previous timeout value
     */
    private void showOverlay() {
        showOverlay(false);
    }

    /**
     * show overlay
     */
    private void showOverlayTimeout(int timeout) {
        if (mService == null) return;
        initOverlay();
        if (timeout != 0) mOverlayTimeout = timeout;
        else mOverlayTimeout = mService.isPlaying() ? OVERLAY_TIMEOUT : OVERLAY_INFINITE;
        if (mIsNavMenu){
            mShowing = true;
            return;
        }
        if (!mShowing) {
            mShowing = true;
            if (!mIsLocked) {
                showControls(true);
            }
            dimStatusBar(false);
            mHudBinding.progressOverlay.setVisibility(View.VISIBLE);
            if (!mDisplayManager.isPrimary())
                mOverlayBackground.setVisibility(View.VISIBLE);
            updateOverlayPausePlay();
        }
        mHandler.removeMessages(FADE_OUT);
        if (mOverlayTimeout != OVERLAY_INFINITE)
            mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT), mOverlayTimeout);
    }

    private void showControls(boolean show) {
        if (mHudBinding != null) {
            mHudBinding.playerOverlayPlay.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            if (mSeekButtons) {
                mHudBinding.playerOverlayRewind.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                mHudBinding.playerOverlayForward.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
            if (mDisplayManager.isPrimary()) mHudBinding.playerOverlaySize.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            mHudBinding.playerOverlayTracks.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            mHudBinding.playerOverlayAdvFunction.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            if (mHasPlaylist) {
                mHudBinding.playlistPrevious.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                mHudBinding.playlistNext.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private PlayerHudBinding mHudBinding;
    private boolean mSeekButtons, mHasPlaylist;
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void initOverlay() {
        final ViewStubCompat vsc = findViewById(R.id.player_hud_stub);
        if (vsc != null) {
            mSeekButtons = mSettings.getBoolean("enable_seek_buttons", false);
            vsc.inflate();
            mHudBinding = DataBindingUtil.bind(findViewById(R.id.progress_overlay));
            mHudBinding.setPlayer(this);
            mHudBinding.setProgress(mService.getPlaylistManager().getPlayer().getProgress());
            mHudBinding.setLifecycleOwner(this);
            final RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams)mHudBinding.progressOverlay.getLayoutParams();
            if (AndroidDevices.isPhone || !AndroidDevices.hasNavBar)
                layoutParams.width = LayoutParams.MATCH_PARENT;
            else
                layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
            mHudBinding.progressOverlay.setLayoutParams(layoutParams);
            mOverlayBackground = findViewById(R.id.player_overlay_background);
            mNavMenu = findViewById(R.id.player_overlay_navmenu);
            if (!AndroidDevices.isChromeBook && !mIsTv
                    && Settings.INSTANCE.getInstance(this).getBoolean("enable_casting", true)) {
                mRendererBtn = findViewById(R.id.video_renderer);
                RendererDelegate.INSTANCE.getSelectedRenderer().observe(this, new Observer<RendererItem>() {
                    @Override
                    public void onChanged(@androidx.annotation.Nullable RendererItem rendererItem) {
                        if (mRendererBtn != null) mRendererBtn.setImageResource(rendererItem == null ? R.drawable.ic_renderer_circle : R.drawable.ic_renderer_on_circle);
                    }
                });
                RendererDelegate.INSTANCE.getRenderers().observe(this, new Observer<List<RendererItem>>() {
                    @Override
                    public void onChanged(@Nullable List<RendererItem> rendererItems) {
                        UiTools.setViewVisibility(mRendererBtn, Util.isListEmpty(rendererItems) ? View.GONE : View.VISIBLE);
                    }
                });
            }
            if (mSeekButtons) initSeekButton();
            resetHudLayout();
            updateOverlayPausePlay();
            updateSeekable(mService.isSeekable());
            updatePausable(mService.isPausable());
            updateNavStatus();
            setListeners(true);
            initPlaylistUi();
            if (!mDisplayManager.isPrimary()) {
                mHudBinding.lockOverlayButton.setVisibility(View.GONE);
                mHudBinding.playerOverlaySize.setVisibility(View.GONE);
            }

            if(!mIsTv)
                mOrientationToggle.setVisibility(View.VISIBLE);
        } else {
            mHudBinding.setProgress(mService.getPlaylistManager().getPlayer().getProgress());
            mHudBinding.setLifecycleOwner(this);
        }
    }


    /**
     * hider overlay
     */
    void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(FADE_OUT);
            Log.i(TAG, "remove View!");
            UiTools.setViewVisibility(mOverlayTips, View.INVISIBLE);
            if (!mDisplayManager.isPrimary()) {
                mOverlayBackground.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayBackground.setVisibility(View.INVISIBLE);
            }
            mHudBinding.progressOverlay.setVisibility(View.INVISIBLE);
            showControls(false);
            mShowing = false;
            dimStatusBar(true);
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void dimStatusBar(boolean dim) {
        if (mIsNavMenu) return;
        if (dim || mIsLocked) mActionBar.hide();
        else mActionBar.show();

        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        int navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (dim || mIsLocked) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            navbar |= View.SYSTEM_UI_FLAG_LOW_PROFILE|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            if (AndroidUtil.isKitKatOrLater) visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
            visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        } else {
            mActionBar.show();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
        }

        if (AndroidDevices.hasNavBar)
            visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showTitle() {
        if (mIsNavMenu) return;
        mActionBar.show();

        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        int navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        if (AndroidDevices.hasNavBar) visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);

    }

    private void updateOverlayPausePlay() {
        if (mService == null || mHudBinding == null)
            return;
        if (mService.isPausable())
            mHudBinding.playerOverlayPlay.setImageResource(mService.isPlaying() ? R.drawable.ic_pause_circle
                    : R.drawable.ic_play_circle);
        mHudBinding.playerOverlayPlay.requestFocus();
    }

    private void invalidateESTracks(int type) {
        switch (type) {
            case Media.Track.Type.Audio:
                mAudioTracksList = null;
                break;
            case Media.Track.Type.Text:
                mSubtitleTracksList = null;
                break;
        }
    }

    private void setESTracks() {
        if (mLastAudioTrack >= -1) {
            mService.setAudioTrack(mLastAudioTrack);
            mLastAudioTrack = -2;
        }
        if (mLastSpuTrack >= -1) {
            mService.setSpuTrack(mLastSpuTrack);
            mLastSpuTrack = -2;
        }
    }

    private void setESTrackLists() {
        if (mAudioTracksList == null && mService.getAudioTracksCount() > 0)
            mAudioTracksList = mService.getAudioTracks();
        if (mSubtitleTracksList == null && mService.getSpuTracksCount() > 0)
            mSubtitleTracksList = mService.getSpuTracks();
        if (mVideoTracksList == null && mService.getVideoTracksCount() > 0)
            mVideoTracksList = mService.getVideoTracks();
    }


    /**
     *
     */
    private void play() {
        mService.play();
        if (mRootView != null)
            mRootView.setKeepScreenOn(true);
    }

    /**
     *
     */
    private void pause() {
        mService.pause();
        if (mRootView != null)
            mRootView.setKeepScreenOn(false);
    }

    public void next() {
        if (mService != null) mService.next();
    }

    public void previous() {
        if (mService != null) mService.previous(false);
    }

    /*
     * Additionnal method to prevent alert dialog to pop up
     */
    @SuppressWarnings({ "unchecked" })
    private void loadMedia(boolean fromStart) {
        mAskResume = false;
        getIntent().putExtra(Constants.PLAY_EXTRA_FROM_START, fromStart);
        loadMedia();
    }

    /**
     * External extras:
     * - position (long) - position of the video to start with (in ms)
     * - subtitles_location (String) - location of a subtitles file to load
     * - from_start (boolean) - Whether playback should start from start or from resume point
     * - title (String) - video title, will be guessed from file if not set.
     */
    @SuppressLint("SdCardPath")
    @TargetApi(12)
    @SuppressWarnings({ "unchecked" })
    protected void loadMedia() {
        if (mService == null) return;
        mIsPlaying = false;
        String title = null;
        boolean fromStart = false;
        String itemTitle = null;
        int positionInPlaylist = -1;
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        long savedTime = 0L;
        final MediaWrapper currentMedia = mService.getCurrentMediaWrapper();
        final boolean hasMedia = currentMedia != null;
        final boolean isPlaying = mService.isPlaying();
        /*
         * If the activity has been paused by pressing the power button, then
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in
         * the background.
         * To workaround this, pause playback if the lockscreen is displayed.
         */
        final KeyguardManager km = (KeyguardManager) getApplicationContext().getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.inKeyguardRestrictedInputMode())
            mWasPaused = true;
        if (mWasPaused && BuildConfig.DEBUG)
            Log.d(TAG, "Video was previously paused, resuming in paused mode");

        if (intent.getData() != null) mUri = intent.getData();
        if (extras != null) {
            if (intent.hasExtra(Constants.PLAY_EXTRA_ITEM_LOCATION))
                mUri = extras.getParcelable(Constants.PLAY_EXTRA_ITEM_LOCATION);
            fromStart = extras.getBoolean(Constants.PLAY_EXTRA_FROM_START, false);
            // Consume fromStart option after first use to prevent
            // restarting again when playback is paused.
            intent.putExtra(Constants.PLAY_EXTRA_FROM_START, false);
            mAskResume &= !fromStart;
            savedTime = fromStart ? 0L : extras.getLong(Constants.PLAY_EXTRA_START_TIME); // position passed in by intent (ms)
            if (!fromStart && savedTime == 0L) {
                savedTime = extras.getInt(Constants.PLAY_EXTRA_START_TIME);
            }
            positionInPlaylist = extras.getInt(Constants.PLAY_EXTRA_OPENED_POSITION, -1);

            final String path = extras.getString(Constants.PLAY_EXTRA_SUBTITLES_LOCATION);
            if (!TextUtils.isEmpty(path)) mService.addSubtitleTrack(path, true);
            if (intent.hasExtra(Constants.PLAY_EXTRA_ITEM_TITLE))
                itemTitle = extras.getString(Constants.PLAY_EXTRA_ITEM_TITLE);
        }
        if (savedTime == 0L && mSavedTime > 0L) savedTime = mSavedTime;
        final boolean restorePlayback = hasMedia && currentMedia.getUri().equals(mUri);

        MediaWrapper openedMedia = null;
        final boolean resumePlaylist = mService.isValidIndex(positionInPlaylist);
        final boolean continueplayback = isPlaying && (restorePlayback || positionInPlaylist == mService.getCurrentMediaPosition());
        if (resumePlaylist) {
            // Provided externally from AudioService
            if (BuildConfig.DEBUG) Log.d(TAG, "Continuing playback from PlaybackService at index " + positionInPlaylist);
            openedMedia = mService.getMedias().get(positionInPlaylist);
            if (openedMedia == null) {
                encounteredError();
                return;
            }
            itemTitle = openedMedia.getTitle();
            updateSeekable(mService.isSeekable());
            updatePausable(mService.isPausable());
        }
        mService.addCallback(this);
        if (mUri != null) {
            MediaWrapper media = null;
            if (!continueplayback) {
                if (!resumePlaylist) {
                    // restore last position
                    media = mMedialibrary.getMedia(mUri);
                    if (media == null && TextUtils.equals(mUri.getScheme(), "file") &&
                            mUri.getPath() != null && mUri.getPath().startsWith("/sdcard")) {
                        mUri = FileUtils.convertLocalUri(mUri);
                        media = mMedialibrary.getMedia(mUri);
                    }
                    if (media != null && media.getId() != 0L && media.getTime() == 0L)
                        media.setTime(media.getMetaLong(MediaWrapper.META_PROGRESS));
                } else
                    media = openedMedia;
                if (media != null) {
                    // in media library
                    if (mAskResume && !fromStart && positionInPlaylist == -1 && media.getTime() > 0) {
                        showConfirmResumeDialog();
                        return;
                    }

                    mLastAudioTrack = media.getAudioTrack();
                    mLastSpuTrack = media.getSpuTrack();
                } else if (!fromStart) {
                    // not in media library
                    if (mAskResume && savedTime > 0L) {
                        showConfirmResumeDialog();
                        return;
                    } else {
                        long rTime = mSettings.getLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
                        if (rTime > 0) {
                            if (mAskResume) {
                                showConfirmResumeDialog();
                                return;
                            } else {
                                mSettings.edit()
                                        .putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1)
                                        .apply();
                                savedTime = rTime;
                            }
                        }
                    }
                }
            }

            // Start playback & seek
            /* prepare playback */
            final boolean medialoaded = media != null;
            if (!medialoaded) media = hasMedia ? currentMedia : new MediaWrapper(mUri);
            if (mWasPaused)
                media.addFlags(MediaWrapper.MEDIA_PAUSED);
            if (intent.hasExtra(Constants.PLAY_DISABLE_HARDWARE))
                media.addFlags(MediaWrapper.MEDIA_NO_HWACCEL);
            media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            media.addFlags(MediaWrapper.MEDIA_VIDEO);

            // Set resume point
            if (!continueplayback) {
                if (!fromStart && savedTime <= 0L && media.getTime() > 0L) savedTime = media.getTime();
                if (savedTime > 0L) mService.saveStartTime(savedTime);
            }

            // Handle playback
            if (resumePlaylist) {
                if (continueplayback) {
                    if (mDisplayManager.isPrimary()) mService.flush();
                    onPlaying();
                } else
                    mService.playIndex(positionInPlaylist);
            } else if (medialoaded)
                mService.load(media);
            else
                mService.loadUri(mUri);

            // Get the title
            if (itemTitle == null && !TextUtils.equals(mUri.getScheme(), "content"))
                title = mUri.getLastPathSegment();
        } else if (mService.hasMedia() && mService.hasRenderer()){
            onPlaying();
        } else {
            mService.loadLastPlaylist(Constants.PLAYLIST_TYPE_VIDEO);
        }
        if (itemTitle != null) title = itemTitle;
        mTitle.setText(title);

        if (mWasPaused) {
            // XXX: Workaround to update the seekbar position
            mForcedTime = savedTime;
            mForcedTime = -1;
            showOverlay(true);
        }
        enableSubs();
    }

    private boolean enableSubs = true;
    private void enableSubs() {
        if (mUri != null) {
            final String lastPath = mUri.getLastPathSegment();
            enableSubs = !TextUtils.isEmpty(lastPath) && !lastPath.endsWith(".ts") && !lastPath.endsWith(".m2ts")
                    && !lastPath.endsWith(".TS") && !lastPath.endsWith(".M2TS");
        }
    }

    private Observer downloadedSubtitleObserver = new Observer<List<ExternalSub>>() {
        @Override
        public void onChanged
                (@androidx.annotation.Nullable List < ExternalSub > externalSubs) {
            for (ExternalSub externalSub : externalSubs) {
                if (!addedExternalSubs.contains(externalSub)) {
                    mService.addSubtitleTrack(externalSub.getSubtitlePath(), false);
                    addedExternalSubs.add(externalSub);
                }
            }
        }
    };

    public void removeDownloadedSubtitlesObserver() {
        if (downloadedSubtitleLiveData != null)
            downloadedSubtitleLiveData.removeObserver(downloadedSubtitleObserver);
        downloadedSubtitleLiveData = null;
    }

    public void observeDownloadedSubtitles() {
        if (previousMediaPath == null || !mService.getCurrentMediaWrapper().getUri().getPath().equals(previousMediaPath)) {
            previousMediaPath = mService.getCurrentMediaWrapper().getUri().getPath();
            removeDownloadedSubtitlesObserver();
            downloadedSubtitleLiveData = ExternalSubRepository.Companion.getInstance(VideoPlayerActivity.this).getDownloadedSubtitles(mService.getCurrentMediaWrapper().getUri().getPath());
            downloadedSubtitleLiveData.observe(this, downloadedSubtitleObserver);
        }
    }

    @SuppressWarnings("deprecation")
    private int getScreenRotation(){
        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return Surface.ROTATION_0;
        final Display display = wm.getDefaultDisplay();
        try {
            final Method m = display.getClass().getDeclaredMethod("getRotation");
            return (Integer) m.invoke(display);
        } catch (Exception e) {
            return Surface.ROTATION_0;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private int getScreenOrientation(int mode){
        switch(mode) {
            case 98: //toggle button
                if (mCurrentScreenOrientation == Configuration.ORIENTATION_LANDSCAPE)
                    return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
                else
                    return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            case 99: //screen orientation user
                return AndroidUtil.isJellyBeanMR2OrLater ?
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR :
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR;
            case 101: //screen orientation landscape
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            case 102: //screen orientation portrait
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        }
        /*
         mScreenOrientation = 100, we lock screen at its current orientation
         */
        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return 0;
        final Display display = wm.getDefaultDisplay();
        int rot = getScreenRotation();
        /*
         * Since getRotation() returns the screen's "natural" orientation,
         * which is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT,
         * we have to invert the SCREEN_ORIENTATION value if it is "naturally"
         * landscape.
         */
        @SuppressWarnings("deprecation")
        boolean defaultWide = display.getWidth() > display.getHeight();
        if(rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
            defaultWide = !defaultWide;
        if(defaultWide) {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                default:
                    return 0;
            }
        } else {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                default:
                    return 0;
            }
        }
    }

    public void showConfirmResumeDialog() {
        if (isFinishing()) return;
        mService.pause();
        /* Encountered Error, exit player with a message */
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setMessage(R.string.confirm_resume)
                .setPositiveButton(R.string.resume_from_position, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        loadMedia(false);
                    }
                })
                .setNegativeButton(R.string.play_from_start, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        loadMedia(true);
                    }
                })
                .create();
        mAlertDialog.setCancelable(false);
        mAlertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss();
                    finish();
                    return true;
                }
                return false;
            }
        });
        mAlertDialog.show();
    }

    private PlayerOptionsDelegate mOptionsDelegate;
    public void showAdvancedOptions() {
        if (mOptionsDelegate == null) mOptionsDelegate = new PlayerOptionsDelegate(this, mService);
        mOptionsDelegate.show();
        hideOverlay(false);
    }

    private void toggleOrientation() {
        mScreenOrientation = 98; //Rotate button
        setRequestedOrientation(getScreenOrientation(mScreenOrientation));
    }

    private boolean resetOrientation() {
        if (mScreenOrientation == 98) {
            mScreenOrientation = Integer.valueOf(
                    mSettings.getString("screen_orientation", "99" /*SCREEN ORIENTATION SENSOR*/));
            UiTools.snacker(mRootView, R.string.reset_orientation);
            setRequestedOrientation(getScreenOrientation(mScreenOrientation));
            return true;
        }
        return false;
    }

    void togglePlaylist() {
        if (isPlaylistVisible()) {
            mPlaylist.setVisibility(View.GONE);
            mPlaylist.setOnClickListener(null);
            return;
        }
        hideOverlay(true);
        mPlaylist.setVisibility(View.VISIBLE);
        mPlaylist.setAdapter(mPlaylistAdapter);
        update();
    }

    public boolean isPlaylistVisible() {
        return mPlaylist.getVisibility() == View.VISIBLE;
    }

    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            switch (intent.getAction()) {
                case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    long savedDelay = mSettings.getLong(KEY_BLUETOOTH_DELAY, 0L);
                    long currentDelay = mService.getAudioDelay();
                    if (savedDelay != 0L) {
                        boolean connected = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1) == BluetoothA2dp.STATE_CONNECTED;
                        if (connected && currentDelay == 0L)
                            toggleBtDelay(true);
                        else if (!connected && savedDelay == currentDelay)
                            toggleBtDelay(false);
                    }
            }
        }
    };

    private void toggleBtDelay(boolean connected) {
        mService.setAudioDelay(connected ? mSettings.getLong(KEY_BLUETOOTH_DELAY, 0) : 0L);
    }

    private OnClickListener mBtSaveListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            mSettings.edit().putLong(KEY_BLUETOOTH_DELAY, mService.getAudioDelay()).apply();
        }
    };

    /**
     * Start the video loading animation.
     */
    private void startLoading() {
        if (mIsLoading)
            return;
        mIsLoading = true;
        final AnimationSet anim = new AnimationSet(true);
        final RotateAnimation rotate = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(800);
        rotate.setInterpolator(new DecelerateInterpolator());
        rotate.setRepeatCount(RotateAnimation.INFINITE);
        anim.addAnimation(rotate);
        mLoading.setVisibility(View.VISIBLE);
        mLoading.startAnimation(anim);
    }

    /**
     * Stop the video loading animation.
     */
    private void stopLoading() {
        mHandler.removeMessages(LOADING_ANIMATION);
        if (!mIsLoading) return;
        mIsLoading = false;
        mLoading.setVisibility(View.INVISIBLE);
        mLoading.clearAnimation();
    }

    public void onClickOverlayTips(View v) {
        UiTools.setViewVisibility(mOverlayTips, View.GONE);
    }

    public void onClickDismissTips(View v) {
        UiTools.setViewVisibility(mOverlayTips, View.GONE);
        mSettings.edit().putBoolean(PREF_TIPS_SHOWN, true).apply();
    }

    private void updateNavStatus() {
        if (mService == null) return;
        mIsNavMenu = false;
        mMenuIdx = -1;

        final MediaPlayer.Title[] titles = mService.getTitles();
        if (titles != null) {
            final int currentIdx = mService.getTitleIdx();
            for (int i = 0; i < titles.length; ++i) {
                final MediaPlayer.Title title = titles[i];
                if (title.isMenu()) {
                    mMenuIdx = i;
                    break;
                }
            }
            mIsNavMenu = mMenuIdx == currentIdx;
        }

        if (mIsNavMenu) {
            /*
             * Keep the overlay hidden in order to have touch events directly
             * transmitted to navigation handling.
             */
            hideOverlay(false);
        }
        else if (mMenuIdx != -1)
            setESTracks();

        UiTools.setViewVisibility(mNavMenu, mMenuIdx >= 0 && mNavMenu != null ? View.VISIBLE : View.GONE);
        supportInvalidateOptionsMenu();
    }

    public PlaybackServiceActivity.Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(PlaybackService service) {
        if (mPlaylistModel != null) mPlaylistModel.onConnected(service);
        mService = service;
        //We may not have the permission to access files
        if (Permissions.checkReadStoragePermission(this, true) && !mSwitchingView)
            mHandler.sendEmptyMessage(START_PLAYBACK);
        mSwitchingView = false;
        if (mService.getVolume() > 100 && !audioBoostEnabled)
            mService.setVolume(100);
    }

    @Override
    public void onDisconnected() {
        if (mPlaylistModel != null) mPlaylistModel.onDisconnected();
        mService = null;
        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED);
    }

    private BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), Constants.PLAY_FROM_SERVICE))
                onNewIntent(intent);
            else if (TextUtils.equals(intent.getAction(), Constants.EXIT_PLAYER))
                exitOK();
        }
    };

    @BindingAdapter({"length", "time"})
    public static void setPlaybackTime(TextView view, long length, long time) {
        view.setText(sDisplayRemainingTime && length > 0
                ? "-" + '\u00A0' + Tools.millisToString(length - time)
                : Tools.millisToString(length));
    }

    @BindingAdapter({"mediamax"})
    public static void setProgressMax(SeekBar view, long length) {
        view.setMax((int) length);
    }
}
