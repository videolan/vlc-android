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
import android.arch.lifecycle.Observer;
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
import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.ViewStubCompat;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.Nullable;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.RendererItem;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.RendererDelegate;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.PlayerHudBinding;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.audio.PlaylistAdapter;
import org.videolan.vlc.gui.browser.FilePickerActivity;
import org.videolan.vlc.gui.browser.FilePickerFragment;
import org.videolan.vlc.gui.dialogs.AdvOptionsDialog;
import org.videolan.vlc.gui.dialogs.RenderersDialog;
import org.videolan.vlc.gui.helpers.OnRepeatListener;
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.interfaces.IPlaybackSettingsController;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.SubtitlesDownloader;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WorkersKt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VideoPlayerActivity extends AppCompatActivity implements IVLCVout.Callback,
        IVLCVout.OnNewVideoLayoutListener, IPlaybackSettingsController,
        PlaybackService.Client.Callback, PlaybackService.Callback,PlaylistAdapter.IPlayer,
        OnClickListener, StoragePermissionsDelegate.CustomActionController {

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
    private SurfaceView mSurfaceView = null;
    private SurfaceView mSubtitlesSurfaceView = null;
    protected DisplayManager mDisplayManager;
    private View mRootView;
    private FrameLayout mSurfaceFrame;
    private Uri mUri;
    private boolean mAskResume = true;

    private ImageView mPlaylistToggle;
    private RecyclerView mPlaylist;
    private PlaylistAdapter mPlaylistAdapter;

    private static final int SURFACE_BEST_FIT = 0;
    static final int SURFACE_FIT_SCREEN = 1;
    private static final int SURFACE_FILL = 2;
    private static final int SURFACE_16_9 = 3;
    private static final int SURFACE_4_3 = 4;
    private static final int SURFACE_ORIGINAL = 5;
    private static final int SURFACE_SIZE_COUNT = 6;

    private int mCurrentSize;

    private SharedPreferences mSettings;

    /** Overlay */
    private ActionBar mActionBar;
    private ViewGroup mActionBarView;
    private View mOverlayBackground;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int OVERLAY_INFINITE = -1;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int FADE_OUT_INFO = 3;
    private static final int START_PLAYBACK = 4;
    private static final int AUDIO_SERVICE_CONNECTION_FAILED = 5;
    private static final int RESET_BACK_LOCK = 6;
    private static final int CHECK_VIDEO_TRACKS = 7;
    private static final int LOADING_ANIMATION = 8;
    static final int SHOW_INFO = 9;
    static final int HIDE_INFO = 10;

    private static final int LOADING_ANIMATION_DELAY = 1000;

    private boolean mDragging;
    private boolean mShowing;
    private boolean mShowingDialog;
    private DelayState mPlaybackSetting = DelayState.OFF;
    private TextView mTitle;
    private TextView mSysTime;
    private TextView mBattery;
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
    private boolean mHasSubItems = false;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

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

    // Tracks & Subtitles
    private MediaPlayer.TrackDescription[] mAudioTracksList;
    private MediaPlayer.TrackDescription[] mVideoTracksList;
    private MediaPlayer.TrackDescription[] mSubtitleTracksList;
    /**
     * Used to store a selected subtitle; see onActivityResult.
     * It is possible to have multiple custom subs in one session
     * (just like desktop VLC allows you as well.)
     */
    private final ArrayList<String> mSubtitleSelectedFiles = new ArrayList<>();

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

    private OnLayoutChangeListener mOnLayoutChangeListener;
    private AlertDialog mAlertDialog;

    protected boolean mIsBenchmark = false;

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Util.checkCpuCompatibility(this);

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioBoostEnabled = mSettings.getBoolean("audio_boost", false);

        mEnableCloneMode = mSettings.getBoolean("enable_clone_mode", false);
        mDisplayManager = new DisplayManager(this, mEnableCloneMode, mIsBenchmark);
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
        if (!AndroidUtil.isJellyBeanOrLater) {
            View v = findViewById(R.id.player_overlay_systime);
            if (v instanceof TextView) mSysTime = (TextView) v;
            v = findViewById(R.id.player_overlay_battery);
            if (v instanceof TextView) mBattery = (TextView) v;
        }

        mPlaylistToggle = findViewById(R.id.playlist_toggle);
        mPlaylist = findViewById(R.id.video_playlist);



        mScreenOrientation = Integer.valueOf(
                mSettings.getString("screen_orientation", "99" /*SCREEN ORIENTATION SENSOR*/));

        mSurfaceView = findViewById(R.id.player_surface);
        mSubtitlesSurfaceView = findViewById(R.id.subtitles_surface);

        mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
        mSubtitlesSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mSurfaceFrame = findViewById(R.id.player_surface_frame);

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
        // Also clear the subs list, because it is supposed to be per session
        // only (like desktop VLC). We don't want the custom subtitle files
        // to persist forever with this video.
        editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
        // Paused flag - per session too, like the subs list.
        editor.remove(PreferencesActivity.VIDEO_PAUSED);
        editor.apply();

        final IntentFilter filter = new IntentFilter();
        if (mBattery != null) filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // 100 is the value for screen_orientation_start_lock
        setRequestedOrientation(getScreenOrientation(mScreenOrientation));
        // Extra initialization when no secondary display is detected
        if (mDisplayManager.isPrimary()) {
            // Orientation
            // Tips
            if (!BuildConfig.DEBUG && !VLCApplication.showTvUi() && !mSettings.getBoolean(PREF_TIPS_SHOWN, false)) {
                ((ViewStubCompat) findViewById(R.id.player_overlay_tips)).inflate();
                mOverlayTips = findViewById(R.id.overlay_tips_layout);
            }

            //Set margins for TV overscan
            if (VLCApplication.showTvUi()) {
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


        if (mIsBenchmark) {
            mCurrentSize = SURFACE_FIT_SCREEN;
        } else {
            mCurrentSize = mSettings.getInt(PreferencesActivity.VIDEO_RATIO, SURFACE_BEST_FIT);
        }
        mMedialibrary = VLCApplication.getMLInstance();
        final int touch;
        if (!VLCApplication.showTvUi()) {
            touch = (mSettings.getBoolean("enable_volume_gesture", true) ? VideoTouchDelegateKt.TOUCH_FLAG_AUDIO_VOLUME : 0)
                    + (mSettings.getBoolean("enable_brightness_gesture", true) ? VideoTouchDelegateKt.TOUCH_FLAG_BRIGHTNESS : 0)
                    + (mSettings.getBoolean("enable_double_tap_seek", true) ? VideoTouchDelegateKt.TOUCH_FLAG_SEEK : 0);
        } else touch = 0;
        mCurrentScreenOrientation = getResources().getConfiguration().orientation;
        if (touch != 0) {
            boolean isRtl = AndroidUtil.isJellyBeanMR1OrLater && TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
            final DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            int yRange = Math.min(dm.widthPixels, dm.heightPixels);
            int xRange = Math.max(dm.widthPixels, dm.heightPixels);
            final ScreenConfig sc = new ScreenConfig(dm, xRange, yRange, mCurrentScreenOrientation);
            mTouchDelegate = new VideoTouchDelegate(this, touch, sc, isRtl);
        }
        UiTools.setRotationAnimation(this);
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
            showTitle();
            initUI();
            setPlaybackParameters();
            mForcedTime = mLastTime = -1;
            enableSubs();
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

    @TargetApi(Build.VERSION_CODES.N)
    public void switchToPopup() {
        final MediaWrapper mw = mService != null ? mService.getCurrentMediaWrapper() : null;
        if (mw == null) return;
        if (AndroidDevices.hasPiP) {
            if (AndroidUtil.isOOrLater)
                try {
                    final int height = mVideoHeight != 0 ? mVideoHeight : mw.getHeight();
                    final int width = Math.min(mVideoWidth != 0 ? mVideoWidth : mw.getWidth(), (int) (height*2.39f));
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
        final int endOf = AndroidUtil.isJellyBeanMR1OrLater ? RelativeLayout.END_OF : RelativeLayout.RIGHT_OF;
        final int startOf = AndroidUtil.isJellyBeanMR1OrLater ? RelativeLayout.START_OF : RelativeLayout.LEFT_OF;
        final int endAlign = AndroidUtil.isJellyBeanMR1OrLater ? RelativeLayout.ALIGN_PARENT_END : RelativeLayout.ALIGN_PARENT_RIGHT;
        final int startAlign = AndroidUtil.isJellyBeanMR1OrLater ? RelativeLayout.ALIGN_PARENT_START : RelativeLayout.ALIGN_PARENT_LEFT;
        layoutParams.addRule(startAlign, portrait ? 1 : 0);
        layoutParams.addRule(endAlign, portrait ? 1 : 0);
        layoutParams.addRule(RelativeLayout.BELOW, portrait ? R.id.player_overlay_length : R.id.player_overlay_seekbar);
        layoutParams.addRule(endOf, portrait ? 0 : R.id.player_overlay_time);
        layoutParams.addRule(startOf, portrait ? 0 : R.id.player_overlay_length);
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

        // Save selected subtitles
        String subtitleList_serialized = null;
        synchronized (mSubtitleSelectedFiles) {
            if(mSubtitleSelectedFiles.size() > 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Saving selected subtitle files");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(mSubtitleSelectedFiles);
                    subtitleList_serialized = bos.toString();
                } catch(IOException ignored) {}
            }
        }
        editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, subtitleList_serialized);
        editor.apply();

        saveBrightness();

        if (mSubtitlesGetTask != null)
            mSubtitlesGetTask.cancel(true);

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

        // Dismiss the presentation when the activity is not visible.
        mDisplayManager.release();
        mAudioManager = null;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void surfaceFrameAddLayoutListener(boolean add) {
        if (mSurfaceFrame == null || add == (mOnLayoutChangeListener != null)) return;
        if (add) {
            mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
                private final Runnable mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        changeSurfaceLayout();
                    }
                };
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        /* changeSurfaceLayout need to be called after the layout changed */
                        mHandler.removeCallbacks(mRunnable);
                        mHandler.post(mRunnable);
                    }
                }
            };
            mSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
            changeSurfaceLayout();
        }
        else {
            mSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);
            mOnLayoutChangeListener = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void startPlayback() {
        /* start playback only when audio service and both surfaces are ready */
        if (mPlaybackStarted || mService == null)
            return;

        mSavedTime = -1;
        mPlaybackStarted = true;

        IVLCVout vlcVout = mService.getVout();
        if (vlcVout.areViewsAttached()) {
            if (mService.isPlayingPopup()) {
                mService.stop();
                vlcVout = mService.getVout();
            } else
                vlcVout.detachViews();
        }
        final DisplayManager.SecondaryDisplay sd = mDisplayManager.getPresentation();
        vlcVout.setVideoView(sd != null ? sd.getSurfaceView() : mSurfaceView);
        vlcVout.setSubtitlesView(sd != null ? sd.getSubtitlesSurfaceView() : mSubtitlesSurfaceView);
        vlcVout.addCallback(this);
        vlcVout.attachViews(this);
        mService.setVideoTrackEnabled(true);

        initUI();

        loadMedia();
    }

    private void initPlaylistUi() {
        if (mService.hasPlaylist()) {
            mHasPlaylist = true;
            mPlaylistAdapter = new PlaylistAdapter(this);
            mPlaylistAdapter.setService(mService);
            final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            mPlaylist.setLayoutManager(layoutManager);
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

        surfaceFrameAddLayoutListener(true);

        /* Listen for changes to media routes. */
        if (!mIsBenchmark) mDisplayManager.mediaRouterAddCallback(true);

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

        if (mDisplayManager.isOnRenderer() && !isFinishing()) {
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

        mService.setVideoTrackEnabled(false);
        mService.removeCallback(this);

        mHandler.removeCallbacksAndMessages(null);
        final IVLCVout vlcVout = mService.getVout();
        vlcVout.removeCallback(this);
        vlcVout.detachViews();
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
        if (!mIsBenchmark) mDisplayManager.mediaRouterAddCallback(false);

        surfaceFrameAddLayoutListener(false);

        mActionBarView.setOnTouchListener(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if(data == null) return;

        if(data.hasExtra(FilePickerFragment.EXTRA_MRL)) {
            mService.addSubtitleTrack(Uri.parse(data.getStringExtra(FilePickerFragment.EXTRA_MRL)), true);
            WorkersKt.runBackground(new Runnable() {
                @Override
                public void run() {
                    MediaDatabase.getInstance().saveSlave(mService.getCurrentMediaLocation(), Media.Slave.Type.Subtitle, 2, data.getStringExtra(FilePickerFragment.EXTRA_MRL));
                }
            });
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
        Intent intent = new Intent(context, VideoPlayerActivity.class);
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
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equalsIgnoreCase(action)) {
                if (mBattery == null) return;
                int batteryLevel = intent.getIntExtra("level", 0);
                if (batteryLevel >= 50)
                    mBattery.setTextColor(Color.GREEN);
                else if (batteryLevel >= 30)
                    mBattery.setTextColor(Color.YELLOW);
                else
                    mBattery.setTextColor(Color.RED);
                mBattery.setText(String.format("%d%%", batteryLevel));
            } else if (VLCApplication.SLEEP_INTENT.equalsIgnoreCase(action)) {
                exitOK();
            }
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
        if (mLockBackButton) {
            mLockBackButton = false;
            mHandler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000);
            Toast.makeText(getApplicationContext(), getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show();
        } else if(isPlaylistVisible()) {
            togglePlaylist();
        } else if (isPlaybackSettingActive()){
            endPlaybackSetting();
        } else if (VLCApplication.showTvUi() && mShowing && !mIsLocked) {
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
        if (isPlaybackSettingActive())
            return false;
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
        initInfoOverlay();
        UiTools.setViewVisibility(mVerticalBar, View.GONE);
        UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
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
        mPlaylistAdapter.update(mService.getMedias());
    }

    @Override
    public void onMediaEvent(Media.Event event) {
        switch (event.type) {
            case Media.Event.ParsedChanged:
                updateNavStatus();
                break;
            case Media.Event.MetaChanged:
                break;
            case Media.Event.SubItemTreeAdded:
                mHasSubItems = true;
                break;
        }
    }

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Opening:
                mHasSubItems = false;
                break;
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
                    changeSurfaceLayout();
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
                case SHOW_PROGRESS:
                    if (mSysTime != null && canShowProgress()) {
                        mSysTime.setText(DateFormat.getTimeFormat(VideoPlayerActivity.this).format(new Date(System.currentTimeMillis())));
                        mHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, 10000L);
                    }
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

    private boolean canShowProgress() {
        return !mDragging && mShowing && mService != null &&  mService.isPlaying();
    }

    private void onPlaying() {
        mIsPlaying = true;
        setPlaybackParameters();
        stopLoading();
        updateOverlayPausePlay();
        updateNavStatus();
        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if (!mw.hasFlag(MediaWrapper.MEDIA_PAUSED))
            mHandler.sendEmptyMessageDelayed(FADE_OUT, OVERLAY_TIMEOUT);
        else {
            mw.removeFlags(MediaWrapper.MEDIA_PAUSED);
            mWasPaused = false;
        }
        setESTracks();
        if (mTitle != null && mTitle.length() == 0)
            mTitle.setText(mw.getTitle());
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
            if (mService.hasMedia()) {
                Log.i(TAG, "Video track lost, switching to audio");
                mSwitchingView = true;
            }
            exit(RESULT_VIDEO_TRACK_LOST);
        }
    };

    private void handleVout(int voutCount) {
        mHandler.removeCallbacks(mSwitchAudioRunnable);

        final IVLCVout vlcVout = mService.getVout();
        if (vlcVout.areViewsAttached() && voutCount == 0) {
            mHandler.postDelayed(mSwitchAudioRunnable, 4000);
        }
    }

    public void switchToAudioMode(boolean showUI) {
        if (mService == null) return;
        mSwitchingView = true;
        // Show the MainActivity if it is not in background.
        if (showUI) {
            Intent i = new Intent(this, VLCApplication.showTvUi() ? AudioPlayerActivity.class : MainActivity.class);
            startActivity(i);
        } else
            mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, true).apply();
        exitOK();
    }

    private void changeMediaPlayerLayout(int displayW, int displayH) {
        /* Change the video placement using MediaPlayer API */
        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                mService.setVideoAspectRatio(null);
                mService.setVideoScale(0);
                break;
            case SURFACE_FIT_SCREEN:
            case SURFACE_FILL: {
                Media.VideoTrack vtrack = mService.getCurrentVideoTrack();
                if (vtrack == null)
                    return;
                final boolean videoSwapped = vtrack.orientation == Media.VideoTrack.Orientation.LeftBottom
                        || vtrack.orientation == Media.VideoTrack.Orientation.RightTop;
                if (mCurrentSize == SURFACE_FIT_SCREEN) {
                    int videoW = vtrack.width;
                    int videoH = vtrack.height;

                    if (videoSwapped) {
                        int swap = videoW;
                        videoW = videoH;
                        videoH = swap;
                    }
                    if (vtrack.sarNum != vtrack.sarDen)
                        videoW = videoW * vtrack.sarNum / vtrack.sarDen;

                    float ar = videoW / (float) videoH;
                    float dar = displayW / (float) displayH;

                    float scale;
                    if (dar >= ar)
                        scale = displayW / (float) videoW; /* horizontal */
                    else
                        scale = displayH / (float) videoH; /* vertical */
                    mService.setVideoScale(scale);
                    mService.setVideoAspectRatio(null);
                } else {
                    mService.setVideoScale(0);
                    mService.setVideoAspectRatio(!videoSwapped ? ""+displayW+":"+displayH
                            : ""+displayH+":"+displayW);
                }
                break;
            }
            case SURFACE_16_9:
                mService.setVideoAspectRatio("16:9");
                mService.setVideoScale(0);
                break;
            case SURFACE_4_3:
                mService.setVideoAspectRatio("4:3");
                mService.setVideoScale(0);
                break;
            case SURFACE_ORIGINAL:
                mService.setVideoAspectRatio(null);
                mService.setVideoScale(1);
                break;
        }
    }

    @Override
    public boolean isInPictureInPictureMode() {
        return AndroidUtil.isNougatOrLater && super.isInPictureInPictureMode();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        changeSurfaceLayout();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceLayout() {
        int sw;
        int sh;

        // get screen size
        if (mDisplayManager.isPrimary()) {
            sw = getWindow().getDecorView().getWidth();
            sh = getWindow().getDecorView().getHeight();
        } else if (mDisplayManager.getPresentation() != null) {
            sw = mDisplayManager.getPresentation().getWindow().getDecorView().getWidth();
            sh = mDisplayManager.getPresentation().getWindow().getDecorView().getHeight();
        } else return;

        // sanity check
        if (sw * sh == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        if (mService != null) {
            final IVLCVout vlcVout = mService.getVout();
            vlcVout.setWindowSize(sw, sh);
        }

        SurfaceView surface;
        SurfaceView subtitlesSurface;
        FrameLayout surfaceFrame;
        if (mDisplayManager.isPrimary()) {
            surface = mSurfaceView;
            subtitlesSurface = mSubtitlesSurfaceView;
            surfaceFrame = mSurfaceFrame;
        } else if (mDisplayManager.getDisplayType() == DisplayManager.DisplayType.PRESENTATION) {
            surface = mDisplayManager.getPresentation().getSurfaceView();
            subtitlesSurface = mDisplayManager.getPresentation().getSubtitlesSurfaceView();
            surfaceFrame = mDisplayManager.getPresentation().getSurfaceFrame();
        } else return;
        LayoutParams lp = surface.getLayoutParams();

        if (mVideoWidth * mVideoHeight == 0 || isInPictureInPictureMode()) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surface.setLayoutParams(lp);
            lp = surfaceFrame.getLayoutParams();
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surfaceFrame.setLayoutParams(lp);
            if (mService != null && mVideoWidth * mVideoHeight == 0)
                changeMediaPlayerLayout(sw, sh);
            return;
        }

        if (mService != null && lp.width == lp.height && lp.width == LayoutParams.MATCH_PARENT) {
            /* We handle the placement of the video using Android View LayoutParams */
            mService.setVideoAspectRatio(null);
            mService.setVideoScale(0);
        }

        double dw = sw, dh = sh;
        boolean isPortrait;

        // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
        isPortrait = mDisplayManager.isPrimary() && mCurrentScreenOrientation == Configuration.ORIENTATION_PORTRAIT;

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_SCREEN:
                if (dar >= ar)
                    dh = dw / ar; /* horizontal */
                else
                    dw = dh * ar; /* vertical */
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        // set display size
        lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        surface.setLayoutParams(lp);
        subtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
        subtitlesSurface.invalidate();
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
                } catch (SecurityException ignored) {} //Some device won't allow us to change volume
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

    public void onAudioSubClick(View anchor){
        if (anchor == null) {
            initOverlay();
            anchor = mHudBinding.playerOverlayTracks;
        }
        final AppCompatActivity context = this;
        final PopupMenu popupMenu = new PopupMenu(this, anchor);
        final Menu menu = popupMenu.getMenu();
        popupMenu.getMenuInflater().inflate(R.menu.audiosub_tracks, menu);
        //FIXME network subs cannot be enabled & screen cast display is broken with picker
        menu.findItem(R.id.video_menu_subtitles_picker).setEnabled(mDisplayManager.isPrimary() && enableSubs);
        menu.findItem(R.id.video_menu_subtitles_download).setEnabled(enableSubs);
        menu.findItem(R.id.video_menu_video_track).setVisible(mService.getVideoTracksCount() > 2);
        menu.findItem(R.id.video_menu_audio_track).setEnabled(mService.getAudioTracksCount() > 0);
        menu.findItem(R.id.video_menu_subtitles).setEnabled(mService.getSpuTracksCount() > 0);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.video_menu_audio_track) {
                    selectAudioTrack();
                    return true;
                } else if (item.getItemId() == R.id.video_menu_video_track) {
                    selectVideoTrack();
                    return true;
                } else if (item.getItemId() == R.id.video_menu_subtitles) {
                    selectSubtitles();
                    return true;
                } else if (item.getItemId() == R.id.video_menu_subtitles_picker) {
                    if (mUri == null)
                        return false;
                    mShowingDialog = true;
                    final Intent filePickerIntent = new Intent(context, FilePickerActivity.class);
                    filePickerIntent.setData(Uri.parse(FileUtils.getParent(mUri.toString())));
                    context.startActivityForResult(filePickerIntent, 0);
                    return true;
                } else if (item.getItemId() == R.id.video_menu_subtitles_download) {
                    if (mUri == null)
                        return false;
                    MediaUtils.getSubs(VideoPlayerActivity.this, mService.getCurrentMediaWrapper(), new SubtitlesDownloader.Callback() {
                        @Override
                        public void onRequestEnded(boolean success) {
                            if (success)
                                getSubtitles();
                        }
                    });
                }
                hideOverlay(true);
                return false;
            }
        });
        popupMenu.show();
        showOverlay();
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

    public int getCurrentSize() {
        return mCurrentSize;
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
        setVideoSurfacesize((mCurrentSize+1)%SURFACE_SIZE_COUNT);
    }

    void setVideoSurfacesize(int size) {
        mCurrentSize = size;
        changeSurfaceLayout();
        switch (mCurrentSize) {
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
                .putInt(PreferencesActivity.VIDEO_RATIO, mCurrentSize)
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
        if (mSysTime != null) mHandler.sendEmptyMessage(SHOW_PROGRESS);
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
            if (!AndroidDevices.isChromeBook && AndroidUtil.isJellyBeanMR1OrLater && !VLCApplication.showTvUi()) {
                mRendererBtn = findViewById(R.id.video_renderer);
                RendererDelegate.INSTANCE.getSelectedRenderer().observe(this, new Observer<RendererItem>() {
                    @Override
                    public void onChanged(@android.support.annotation.Nullable RendererItem rendererItem) {
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
        }
    }


    /**
     * hider overlay
     */
    void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.removeMessages(SHOW_PROGRESS);
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
        int visibility = 0;
        int navbar = 0;

        if (AndroidUtil.isJellyBeanOrLater) {
            visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        if (dim || mIsLocked) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            navbar |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            if (!AndroidDevices.hasCombBar) {
                navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                if (AndroidUtil.isKitKatOrLater)
                    visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
                if (AndroidUtil.isJellyBeanOrLater)
                    visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
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
        int visibility = 0;
        int navbar = 0;
        mActionBar.show();

        if (AndroidUtil.isJellyBeanOrLater) {
            visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
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
        mUri = null;
        mIsPlaying = false;
        String title = null;
        boolean fromStart = false;
        String itemTitle = null;
        int positionInPlaylist = -1;
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        long savedTime = 0L;
        final boolean hasMedia = mService.hasMedia();
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
            if (!fromStart && savedTime == 0L)
                savedTime = extras.getInt(Constants.PLAY_EXTRA_START_TIME);
            positionInPlaylist = extras.getInt(Constants.PLAY_EXTRA_OPENED_POSITION, -1);

            if (intent.hasExtra(Constants.PLAY_EXTRA_SUBTITLES_LOCATION))
                synchronized (mSubtitleSelectedFiles) {
                    mSubtitleSelectedFiles.add(extras.getString(Constants.PLAY_EXTRA_SUBTITLES_LOCATION));
                }
            if (intent.hasExtra(Constants.PLAY_EXTRA_ITEM_TITLE))
                itemTitle = extras.getString(Constants.PLAY_EXTRA_ITEM_TITLE);
        }
        final boolean restorePlayback = hasMedia && mService.getCurrentMediaWrapper().getUri().equals(mUri);

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
            if (!medialoaded) {
                if (hasMedia)
                    media = mService.getCurrentMediaWrapper();
                else
                    media = new MediaWrapper(mUri);
            }
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

            // Get possible subtitles
            getSubtitles();

            // Get the title
            if (itemTitle == null && !TextUtils.equals(mUri.getScheme(), "content"))
                title = mUri.getLastPathSegment();
        } else if (mService.hasMedia() && mService.hasRenderer()){
            onPlaying();
        } else {
            mService.loadLastPlaylist(Constants.PLAYLIST_TYPE_VIDEO);
        }
        if (itemTitle != null)
            title = itemTitle;
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

    private SubtitlesGetTask mSubtitlesGetTask = null;
    private class SubtitlesGetTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... strings) {
            final String subtitleList_serialized = strings[0];
            List<String> prefsList = new ArrayList<>();

            if (subtitleList_serialized != null) {
                final ByteArrayInputStream bis = new ByteArrayInputStream(subtitleList_serialized.getBytes());
                ObjectInputStream ois = null;
                try {
                    ois = new ObjectInputStream(bis);
                    prefsList = (List<String>) ois.readObject();
                } catch (InterruptedIOException ignored) {
                    return prefsList; /* Task is cancelled */
                } catch (ClassNotFoundException | IOException ignored) {
                } finally {
                    Util.close(ois);
                }
            }

            if (!TextUtils.equals(mUri.getScheme(), "content"))
                prefsList.addAll(MediaDatabase.getInstance().getSubtitles(mUri.getLastPathSegment()));

            return prefsList;
        }

        @Override
        protected void onPostExecute(List<String> prefsList) {
            // Add any selected subtitle file from the file picker
            if (prefsList.size() > 0) {
                for (String file : prefsList) {
                    synchronized (mSubtitleSelectedFiles) {
                        if (!mSubtitleSelectedFiles.contains(file))
                            mSubtitleSelectedFiles.add(file);
                    }
                    Log.i(TAG, "Adding user-selected subtitle " + file);
                    mService.addSubtitleTrack(file, true);
                }
            }
            mSubtitlesGetTask = null;
        }

        @Override
        protected void onCancelled() {
            mSubtitlesGetTask = null;
        }
    }

    public void getSubtitles() {
        if (mSubtitlesGetTask != null || mService == null)
            return;
        final String subtitleList_serialized = mSettings.getString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);

        mSubtitlesGetTask = new SubtitlesGetTask();
        mSubtitlesGetTask.execute(subtitleList_serialized);
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
        if (isFinishing())
            return;
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
        mAlertDialog.show();
    }

    public void showAdvancedOptions() {
        final FragmentManager fm = getSupportFragmentManager();
        final AdvOptionsDialog advOptionsDialog = new AdvOptionsDialog();
        final Bundle args = new Bundle(1);
        args.putBoolean(AdvOptionsDialog.PRIMARY_DISPLAY, mDisplayManager.isPrimary());
        args.putBoolean(AdvOptionsDialog.PASSTHROUGH, mService.getPlaylistManager().getPlayer().canDoPassthrough());
        advOptionsDialog.setArguments(args);
        advOptionsDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                dimStatusBar(true);
            }
        });
        advOptionsDialog.show(fm, "fragment_adv_options");
        hideOverlay(false);
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
        mService = service;
        //We may not have the permission to access files
        if (Permissions.checkReadStoragePermission(this, true) && !mSwitchingView)
            mHandler.sendEmptyMessage(START_PLAYBACK);
        mSwitchingView = false;
        mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply();
        if (mService.getVolume() > 100 && !audioBoostEnabled)
            mService.setVolume(100);
    }

    @Override
    public void onDisconnected() {
        mService = null;
        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED);
    }

    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth  = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mSarNum = sarNum;
        mSarDen = sarDen;
        changeSurfaceLayout();
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
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
