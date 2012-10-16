/*****************************************************************************
 * VideoPlayerActivity.java
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

package org.videolan.vlc.gui.video;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.DatabaseManager;
import org.videolan.vlc.EventManager;
import org.videolan.vlc.LibVLC;
import org.videolan.vlc.LibVlcException;
import org.videolan.vlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.PreferencesActivity;
import org.videolan.vlc.gui.SpeedSelectorDialog;
import org.videolan.vlc.gui.audio.AudioPlayerActivity;
import org.videolan.vlc.interfaces.IPlayerControl;
import org.videolan.vlc.interfaces.OnPlayerControlListener;
import org.videolan.vlc.widget.PlayerControlClassic;
import org.videolan.vlc.widget.PlayerControlWheel;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings.SettingNotFoundException;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class VideoPlayerActivity extends Activity {

    public final static String TAG = "VLC/VideoPlayerActivity";

    private SurfaceView mSurface;
    private SurfaceHolder mSurfaceHolder;
    private LibVLC mLibVLC;
    private String mLocation;

    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private int mCurrentSize = SURFACE_BEST_FIT;

    /** Overlay */
    private View mOverlayHeader;
    private View mOverlayOption;
    private View mOverlay;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int OVERLAY_INFINITE = 3600000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SURFACE_SIZE = 3;
    private static final int FADE_OUT_INFO = 4;
    private static final int HIDE_NAV = 5;
    private boolean mDragging;
    private boolean mShowing;
    private int mUiVisibility = -1;
    private SeekBar mSeekbar;
    private TextView mTitle;
    private TextView mSysTime;
    private TextView mBattery;
    private TextView mTime;
    private TextView mLength;
    private TextView mInfo;
    private IPlayerControl mControls;
    private boolean mEnableWheelbar;
    private boolean mEnableBrightnessGesture;
    private ImageButton mAudioTrack;
    private ImageButton mSubtitle;
    private ImageButton mLock;
    private ImageButton mSize;
    private TextView mSpeedLabel;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mEndReached;

    // Playlist
    private int savedIndexPosition = -1;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mSarNum;
    private int mSarDen;

    //Volume
    private AudioManager mAudioManager;
    private int mAudioMax;
    private int mAudioDisplayRange;

    //Volume Or Brightness
    private boolean mIsAudioOrBrightnessChanged;
    private float mTouchY, mTouchX, mVol;

    // Brightness
    private boolean mIsFirstBrightnessGesture = true;

    // Tracks & Subtitles
    private String[] mAudioTracksLibVLC;
    private ArrayAdapter<String> mAudioTracksAdapter;
    private String[] mSubtitleTracksLibVLC;
    private ArrayAdapter<String> mSubtitleTracksAdapter;

    @Override
    @TargetApi(11)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if(Util.isICSOrLater())
            getWindow().getDecorView().findViewById(android.R.id.content).setOnSystemUiVisibilityChangeListener(
                    new OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            if (visibility == mUiVisibility)
                                return;
                            setSurfaceSize(mVideoWidth, mVideoHeight, mSarNum, mSarDen);
                            if (visibility == View.SYSTEM_UI_FLAG_VISIBLE && !mShowing) {
                                showOverlay();
                                mHandler.sendMessageDelayed(mHandler.obtainMessage(HIDE_NAV), OVERLAY_TIMEOUT);
                            }
                            mUiVisibility = visibility;
                        }
                    }
            );

        /** initialize Views an their Events */
        mOverlayHeader = findViewById(R.id.player_overlay_header);
        mOverlayOption = findViewById(R.id.option_overlay);
        mOverlay = findViewById(R.id.player_overlay);

        /* header */
        mTitle = (TextView) findViewById(R.id.player_overlay_title);
        mSysTime = (TextView) findViewById(R.id.player_overlay_systime);
        mBattery = (TextView) findViewById(R.id.player_overlay_battery);

        mTime = (TextView) findViewById(R.id.player_overlay_time);
        mLength = (TextView) findViewById(R.id.player_overlay_length);
        // the info textView is not on the overlay
        mInfo = (TextView) findViewById(R.id.player_overlay_info);

        mEnableWheelbar = pref.getBoolean("enable_wheel_bar", false);
        mEnableBrightnessGesture = pref.getBoolean("enable_gesture_brightness", true);
        mControls = mEnableWheelbar
                ? new PlayerControlWheel(this)
                : new PlayerControlClassic(this);
        mControls.setOnPlayerControlListener(mPlayerControlListener);
        FrameLayout mControlContainer = (FrameLayout) findViewById(R.id.player_control);
        mControlContainer.addView((View) mControls);

        mAudioTrack = (ImageButton) findViewById(R.id.player_overlay_audio);
        mAudioTrack.setEnabled(false);
        mSubtitle = (ImageButton) findViewById(R.id.player_overlay_subtitle);
        mSubtitle.setEnabled(false);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                /*FIXME
                 * The setTracksAndSubtitles method probably doesn't work in case of many many Tracks and Subtitles
                 * Moreover, in a video stream, if Tracks & Subtitles change, they won't be updated
                 */
                setTracksAndSubtitles();
            }}, 1500);


        mLock = (ImageButton) findViewById(R.id.player_overlay_lock);
        mLock.setOnClickListener(mLockListener);

        mSize = (ImageButton) findViewById(R.id.player_overlay_size);
        mSize.setOnClickListener(mSizeListener);

        mSpeedLabel = (TextView) findViewById(R.id.player_overlay_speed);
        mSpeedLabel.setOnClickListener(mSpeedLabelListener);

        mSurface = (SurfaceView) findViewById(R.id.player_surface);
        mSurfaceHolder = mSurface.getHolder();
        mSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
        mSurfaceHolder.addCallback(mSurfaceCallback);

        mSeekbar = (SeekBar) findViewById(R.id.player_overlay_seekbar);
        mSeekbar.setOnSeekBarChangeListener(mSeekListener);

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        mSwitchingView = false;
        mEndReached = false;

        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        try {
            LibVLC.useIOMX(this);
            mLibVLC = LibVLC.getInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
        }

        EventManager em = EventManager.getInstance();
        em.addHandler(eventHandler);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    @Override
    protected void onStart() {
        super.onStart();
        dimStatusBar(true);
        mSwitchingView = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mSwitchingView)
            return;

        long time = mLibVLC.getTime();
        long length = mLibVLC.getLength();
        //remove saved position if in the last 5 seconds
        if (length - time < 5000)
            time = 0;
        else
            time -= 5000; // go back 5 seconds, to compensate loading time

        /*
         * Pausing here generates errors because the vout is constantly
         * trying to refresh itself every 80ms while the surface is not
         * accessible anymore.
         * To workaround that, we keep the last known position in the playlist
         * in savedIndexPosition to be able to restore it during onResume().
         */
        if (savedIndexPosition >= 0)
            mLibVLC.stop();
        else {
            /* FIXME when the playback is started externally from AudioService
             * we don't have a savedIndexPosition. Use pause as a fallback until
             * we find a solution.
             */
            mLibVLC.pause();
        }

        mSurface.setKeepScreenOn(false);

        // Save position
        if (time >= 0) {
            SharedPreferences preferences = getSharedPreferences(PreferencesActivity.NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(PreferencesActivity.LAST_MEDIA, mLocation);
            editor.commit();
            DatabaseManager.getInstance(this).updateMedia(
                    mLocation,
                    DatabaseManager.mediaColumn.MEDIA_TIME,
                    time);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBatteryReceiver);
        if (mLibVLC != null && !mSwitchingView) {
            mLibVLC.stop();
        }

        EventManager em = EventManager.getInstance();
        em.removeHandler(eventHandler);

        mAudioManager = null;

        if(mSwitchingView) {
            Log.d(TAG, "mLocation = \"" + mLocation + "\"");
            AudioServiceController.getInstance().showWithoutParse(mLocation);
            AudioPlayerActivity.start(this, true);
        }
        //AudioServiceController.getInstance().unbindAudioService(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AudioServiceController.getInstance().bindAudioService(this);

        load();

        /*
         * if the activity has been paused by pressing the power button,
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in the background.
         * To workaround that, pause playback if the lockscreen is displayed
         */
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mLibVLC != null && mLibVLC.isPlaying()) {
                    KeyguardManager km = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
                    if (km.inKeyguardRestrictedInputMode())
            mLibVLC.pause();
                }
            }}, 500);

        showOverlay();
    }

    public static void start(Context context, String location) {
        start(context, location, null, false);
    }

    public static void start(Context context, String location, String title, Boolean dontParse) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra("itemLocation", location);
        intent.putExtra("itemTitle", title);
        intent.putExtra("dontParse", dontParse);

        if (dontParse)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        else {
            // Stop the currently running audio
            AudioServiceController asc = AudioServiceController.getInstance();
            asc.stop();
        }

        context.startActivity(intent);
    }

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int batteryLevel = intent.getIntExtra("level", 0);
            if (batteryLevel >= 50)
                mBattery.setTextColor(Color.GREEN);
            else if  (batteryLevel >= 30)
                mBattery.setTextColor(Color.YELLOW);
            else
                mBattery.setTextColor(Color.RED);
            mBattery.setText(String.format("%d%%", batteryLevel));
        }
    };

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        showOverlay();
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setSurfaceSize(mVideoWidth, mVideoHeight, mSarNum, mSarDen);
        super.onConfigurationChanged(newConfig);
    }

    public void setSurfaceSize(int width, int height, int sar_num, int sar_den) {
        // store video size
        mVideoHeight = height;
        mVideoWidth = width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        Message msg = mHandler.obtainMessage(SURFACE_SIZE);
        mHandler.sendMessage(msg);
    }

    /**
     * Lock screen rotation
     */
    @SuppressWarnings("deprecation")
    private void lockScreen() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int rotation;
        if (Build.VERSION.SDK_INT >= 8 /* Android 2.2 has getRotation */) {
            try {
                Method m = display.getClass().getDeclaredMethod("getRotation");
                rotation = (Integer) m.invoke(display);
            } catch (Exception e) {
                rotation = Surface.ROTATION_0;
            }
        } else {
            rotation = display.getOrientation();
        }
        switch (rotation) {
            case Surface.ROTATION_0:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case Surface.ROTATION_90:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case Surface.ROTATION_270:
                // FIXME: API Level 9+ (not tested on a device with API Level < 9)
                setRequestedOrientation(8); // SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                break;
        }

        showInfo(R.string.locked, 1000);
        mLock.setBackgroundResource(R.drawable.ic_lock_glow);
    }

    /**
     * Remove screen lock
     */
    private void unlockScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        showInfo(R.string.unlocked, 1000);
        mLock.setBackgroundResource(R.drawable.ic_lock);
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    private void showInfo(String text, int duration) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    private void showInfo(int textid, int duration) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    /**
     * Show text in the info view
     * @param text
     */
    private void showInfo(String text) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
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
        if (mInfo.getVisibility() == View.VISIBLE)
            mInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerActivity.this, android.R.anim.fade_out));
        mInfo.setVisibility(View.INVISIBLE);
    }

    /**
     *  Handle libvlc asynchronous events
     */
    private final Handler eventHandler = new VideoPlayerEventHandler(this);

    private static class VideoPlayerEventHandler extends WeakHandler<VideoPlayerActivity> {
        public VideoPlayerEventHandler(VideoPlayerActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerActivity activity = getOwner();
            if(activity == null) return;

            switch (msg.getData().getInt("event")) {
                case EventManager.MediaPlayerPlaying:
                    Log.i(TAG, "MediaPlayerPlaying");
                    break;
                case EventManager.MediaPlayerPaused:
                    Log.i(TAG, "MediaPlayerPaused");
                    break;
                case EventManager.MediaPlayerStopped:
                    Log.i(TAG, "MediaPlayerStopped");
                    break;
                case EventManager.MediaPlayerEndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    activity.endReached();
                    break;
                case EventManager.MediaPlayerVout:
                    activity.handleVout(msg);
                    break;
                default:
                    Log.e(TAG, String.format("Event not handled (0x%x)", msg.getData().getInt("event")));
                    break;
            }
            activity.updateOverlayPausePlay();
        }
    };

    /**
     * Handle resize of the surface and the overlay
     */
    private final Handler mHandler = new VideoPlayerHandler(this);

    private static class VideoPlayerHandler extends WeakHandler<VideoPlayerActivity> {
        public VideoPlayerHandler(VideoPlayerActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerActivity activity = getOwner();
            if(activity == null) // WeakReference could be GC'ed early
                return;

            switch (msg.what) {
                case FADE_OUT:
                    activity.hideOverlay(false);
                    break;
                case SHOW_PROGRESS:
                    int pos = activity.setOverlayProgress();
                    if (activity.canShowProgress()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case SURFACE_SIZE:
                    activity.changeSurfaceSize();
                    break;
                case FADE_OUT_INFO:
                    activity.fadeOutInfo();
                    break;
                case HIDE_NAV:
                    activity.dimStatusBar(true);
                    break;
            }
        }
    };

    private boolean canShowProgress() {
        return !mDragging && mShowing && mLibVLC.isPlaying();
    }

    private void endReached() {
        /* Exit player when reach the end */
        mEndReached = true;
        finish();
    }

    private void handleVout(Message msg) {
        if (msg.getData().getInt("data") == 0 && !mEndReached) {
            /* Video track lost, open in audio mode */
            Log.i(TAG, "Video track lost, switching to audio");
            mSwitchingView = true;
            finish();
        }
    }

    private void changeSurfaceSize() {
        // get screen size
        int dw = getWindow().getDecorView().getWidth();
        int dh = getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (dw > dh && isPortrait || dw < dh && !isPortrait) {
            int d = dw;
            dw = dh;
            dh = d;
        }
        if (dw * dh == 0)
            return;

        // compute the aspect ratio
        double ar, vw;
        double density = (double)mSarNum / (double)mSarDen;
        if (density == 1.0) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoWidth;
            ar = (double)mVideoWidth / (double)mVideoHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoWidth * density;
            ar = vw / mVideoHeight;
        }

        // compute the display aspect ratio
        double dar = (double) dw / (double) dh;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = (int) (dw / ar);
                else
                    dw = (int) (dh * ar);
                break;
            case SURFACE_FIT_HORIZONTAL:
                dh = (int) (dw / ar);
                break;
            case SURFACE_FIT_VERTICAL:
                dw = (int) (dh * ar);
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = (int) (dw / ar);
                else
                    dw = (int) (dh * ar);
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = (int) (dw / ar);
                else
                    dw = (int) (dh * ar);
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoHeight;
                dw = (int) vw;
                break;
        }

        mSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);
        LayoutParams lp = mSurface.getLayoutParams();
        lp.width = dw;
        lp.height = dh;
        mSurface.setLayoutParams(lp);
        mSurface.invalidate();
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        DisplayMetrics screen = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(screen);

        if (mAudioDisplayRange == 0)
            mAudioDisplayRange = Math.min(screen.widthPixels, screen.heightPixels);

        float y_changed = event.getRawY() - mTouchY;
        float x_changed = event.getRawX() - mTouchX;

        // coef is the gradient's move to determine a neutral zone
        float coef = Math.abs (y_changed / x_changed);
        float xgesturesize = ((x_changed / screen.xdpi) * 2.54f);
        float ygesturesize = ((y_changed / screen.ydpi) * 2.54f);

        switch (event.getAction()) {

        case MotionEvent.ACTION_DOWN:
            // Audio
            mTouchY = event.getRawY();
            mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mIsAudioOrBrightnessChanged = false;
            // Seek
            mTouchX = event.getRawX();
            break;

        case MotionEvent.ACTION_MOVE:
            // No audio/brightness action if coef < 2
            if (coef > 2) {
                // Audio (Up or Down - Right side)
                if (!mEnableBrightnessGesture || mTouchX > (screen.widthPixels / 2)){
                    int delta = -(int) ((y_changed / mAudioDisplayRange) * mAudioMax);
                    int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
                    if (delta != 0) {
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                                vol, AudioManager.FLAG_SHOW_UI);
                        mIsAudioOrBrightnessChanged = true;
                    }
                }
                // Brightness (Up or Down - Left side)
                if (mEnableBrightnessGesture && mTouchX < (screen.widthPixels / 2)){
                    if (mIsFirstBrightnessGesture) initBrightnessTouch();
                    doBrightnessTouch( - ygesturesize);
                }
            }
            // Seek (Right or Left move)
            evalTouchSeek(coef, xgesturesize, false);
            break;

        case MotionEvent.ACTION_UP:
            // Audio or Brightness
            if (!mIsAudioOrBrightnessChanged) {
                if (!mShowing) {
                    showOverlay();
                } else {
                    hideOverlay(true);
                    if (Util.isICSOrLater())
                        mHandler.sendMessageDelayed(
                                mHandler.obtainMessage(HIDE_NAV),
                                OVERLAY_TIMEOUT);
                }
            }
            // Seek
            evalTouchSeek(coef, xgesturesize, true);
            break;
        }
        return mIsAudioOrBrightnessChanged;
    }

    private void evalTouchSeek(float coef, float gesturesize, boolean seek) {
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (mEnableWheelbar || coef > 0.5 || Math.abs(gesturesize) < 1)
            return;

        // Always show seekbar when searching
        if (!mShowing) showOverlay();

        long length = mLibVLC.getLength();
        long time = mLibVLC.getTime();

        // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
        int jump = (int) (Math.signum(gesturesize) * ((600000 * Math.pow((gesturesize / 8), 4)) + 3000));

        // Adjust the jump
        if ((jump > 0) && ((time + jump) > length))
            jump = (int) (length - time);
        if ((jump < 0) && ((time + jump) < 0))
            jump = (int) -time;

        //Jump !
        if (seek)
            mPlayerControlListener.onSeekTo(time + jump);

        //Show the jump's size
        showInfo(String.format("%s%s (%s)",
                jump >= 0 ? "+" : "",
                Util.millisToString(jump),
                Util.millisToString(time + jump)), 1000);
    }

    private void initBrightnessTouch() {
        float brightnesstemp = 0.01f;
        // Initialize the layoutParams screen brightness
        try {
            brightnesstemp = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
        } catch (SettingNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightnesstemp;
        getWindow().setAttributes(lp);
        mIsFirstBrightnessGesture = false;
    }

    private void doBrightnessTouch(float gesturesize) {
        // No Brightness action if gesturesize < 0.4 cm
        if (Math.abs(gesturesize) < 0.4)
            return;

        mIsAudioOrBrightnessChanged = true;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness += Math.signum(gesturesize) * 0.05f;
        // Adjust Brightness
        if (lp.screenBrightness > 1) lp.screenBrightness = 1;
        else if (lp.screenBrightness <= 0) lp.screenBrightness = 0.01f;
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
            showOverlay(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay();
            hideInfo();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                mLibVLC.setTime(progress);
                setOverlayProgress();
                mTime.setText(Util.millisToString(progress));
                showInfo(Util.millisToString(progress));
            }

        }
    };

    /**
    *
    */
    private final OnClickListener mAudioTrackListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog dialog = new AlertDialog.Builder(VideoPlayerActivity.this)
            .setTitle(R.string.track_audio)
            .setAdapter(mAudioTracksAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int position) {
                    mLibVLC.setAudioTrack(position + 1);
                    dialog.dismiss();
                }
            })
            .create();
            final ListView lv = dialog.getListView();
            lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            //FIXME We should not force background color
            lv.setBackgroundColor(0xffffffff);
            dialog.show();
            lv.post(new Runnable() {

                @Override
                public void run() {
                    lv.setItemChecked((mLibVLC.getAudioTrack() - 1), true);
                }
            });
        }
    };

    /**
    *
    */
    private final OnClickListener mSubtitlesListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog dialog = new AlertDialog.Builder(VideoPlayerActivity.this)
            .setTitle(R.string.track_text)
            .setAdapter(mSubtitleTracksAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int position) {
                    mLibVLC.setSpuTrack(position);
                    dialog.dismiss();
                }
            })
            .create();

            final ListView lv = dialog.getListView();
            lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            //FIXME We should not force background color
            lv.setBackgroundColor(0xffffffff);
            dialog.show();
            lv.post(new Runnable() {

                @Override
                public void run() {
                    lv.setItemChecked((mLibVLC.getSpuTrack()), true);
                }
            });
        }
    };

    /**
    *
    */
    private final OnPlayerControlListener mPlayerControlListener = new OnPlayerControlListener() {
        @Override
        public void onPlayPause() {
            if (mLibVLC.isPlaying())
                pause();
            else
                play();
            showOverlay();
        }

        @Override
        public void onSeek(int delta) {
            long position = mLibVLC.getTime() + delta;
            if (position < 0) position = 0;
            mLibVLC.setTime(position);
            showOverlay();
        }

        @Override
        public void onSeekTo(long position) {
            mLibVLC.setTime(position);
            mTime.setText(Util.millisToString(position));
        }

        @Override
        public long onWheelStart() {
            showOverlay(OVERLAY_INFINITE);
            return mLibVLC.getTime();
        }

        @Override
        public void onShowInfo(String info) {
            if (info != null)
                showInfo(info);
            else {
                hideInfo();
                showOverlay();
            }
        }
    };

    /**
     *
     */
    private final OnClickListener mLockListener = new OnClickListener() {
        boolean isLocked = false;

        @Override
        public void onClick(View v) {
            if (isLocked) {
                unlockScreen();
                isLocked = false;
            } else {
                lockScreen();
                isLocked = true;
            }
            showOverlay();
        }
    };

    /**
     *
     */
    private final OnClickListener mSizeListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mCurrentSize < SURFACE_ORIGINAL) {
                mCurrentSize++;
            } else {
                mCurrentSize = 0;
            }
            changeSurfaceSize();
            switch (mCurrentSize) {
                case SURFACE_BEST_FIT:
                    showInfo(R.string.surface_best_fit, 1000);
                    break;
                case SURFACE_FIT_HORIZONTAL:
                    showInfo(R.string.surface_fit_horizontal, 1000);
                    break;
                case SURFACE_FIT_VERTICAL:
                    showInfo(R.string.surface_fit_vertical, 1000);
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
            showOverlay();
        }
    };

    private final OnClickListener mSpeedLabelListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            SpeedSelectorDialog d = new SpeedSelectorDialog(VideoPlayerActivity.this);
            d.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                    mSpeedLabel.setText(String.format(java.util.Locale.US, "%.2fx", mLibVLC.getRate()));
                }
            });
            d.show();
        }
    };

    /**
     * attach and disattach surface to the lib
     */
    private final SurfaceHolder.Callback mSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mLibVLC.attachSurface(holder.getSurface(), VideoPlayerActivity.this, width, height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mLibVLC.detachSurface();
        }
    };

    /**
     * show overlay the the default timeout
     */
    private void showOverlay() {
        showOverlay(OVERLAY_TIMEOUT);
    }

    /**
     * show overlay
     */
    private void showOverlay(int timeout) {
        mHandler.removeMessages(HIDE_NAV);
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        if (!mShowing) {
            mShowing = true;
            mOverlayHeader.setVisibility(View.VISIBLE);
            mOverlayOption.setVisibility(View.VISIBLE);
            mOverlay.setVisibility(View.VISIBLE);
            dimStatusBar(false);
        }
        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
        updateOverlayPausePlay();
    }


    /**
     * hider overlay
     */
    private void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);
            Log.i(TAG, "remove View!");
            if (!fromUser) {
                mOverlayHeader.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayOption.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlay.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            }
            mOverlayHeader.setVisibility(View.INVISIBLE);
            mOverlayOption.setVisibility(View.INVISIBLE);
            mOverlay.setVisibility(View.INVISIBLE);
            mShowing = false;
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(11)
    private void dimStatusBar(boolean dim) {
        if (Util.isHoneycombOrLater()) {
            if (dim) {
                mSurface.setSystemUiVisibility(Util.hasNavBar()
                        ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        : View.SYSTEM_UI_FLAG_LOW_PROFILE);
            } else {
                mSurface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    private void updateOverlayPausePlay() {
        if (mLibVLC == null) {
            return;
        }

        mControls.setState(mLibVLC.isPlaying());
    }

    /**
     * update the overlay
     */
    private int setOverlayProgress() {
        if (mLibVLC == null) {
            return 0;
        }
        int time = (int) mLibVLC.getTime();
        int length = (int) mLibVLC.getLength();
        // Update all view elements

        mSeekbar.setMax(length);
        mSeekbar.setProgress(time);
        mSysTime.setText(DateFormat.format("kk:mm", System.currentTimeMillis()));
        mTime.setText(Util.millisToString(time));
        mLength.setText(Util.millisToString(length));
        return time;
    }

    private void setTracksAndSubtitles () {
        if (mAudioTracksLibVLC == null) {
            mAudioTracksLibVLC = mLibVLC.getAudioTrackDescription();
            if (mAudioTracksLibVLC != null && mAudioTracksLibVLC.length > 1) {
                mAudioTracksAdapter = new ArrayAdapter<String>(this, R.layout.list_item_checkable, mAudioTracksLibVLC);
                mAudioTrack.setOnClickListener(mAudioTrackListener);
                mAudioTrack.setEnabled(true);
            }
            else {
                mAudioTrack.setEnabled(false);
                mAudioTrack.setOnClickListener(null);
            }
        }
        if (mSubtitleTracksLibVLC == null) {
            mSubtitleTracksLibVLC = mLibVLC.getSpuTrackDescription();
            if (mSubtitleTracksLibVLC != null && mSubtitleTracksLibVLC.length > 0) {
                mSubtitleTracksAdapter = new ArrayAdapter<String>(this, R.layout.list_item_checkable, mSubtitleTracksLibVLC);
                mSubtitle.setOnClickListener(mSubtitlesListener);
                mSubtitle.setEnabled(true);

            }
            else {
                mSubtitle.setEnabled(false);
                mSubtitle.setOnClickListener(null);
            }
        }
    }


    /**
     *
     */
    private void play() {
        mLibVLC.play();
        mSurface.setKeepScreenOn(true);
    }

    /**
     *
     */
    private void pause() {
        mLibVLC.pause();
        mSurface.setKeepScreenOn(false);
    }

    /**
     *
     */
    private void load() {
        mLocation = null;
        String title = getResources().getString(R.string.title);
        boolean dontParse = false;
        String itemTitle = null;

        if (getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            /* Started from external application */
            mLocation = getIntent().getDataString();
        } else if(getIntent().getExtras() != null) {
            /* Started from VideoListActivity */
            mLocation = getIntent().getExtras().getString("itemLocation");
            itemTitle = getIntent().getExtras().getString("itemTitle");
            dontParse = getIntent().getExtras().getBoolean("dontParse");
        }

        mSurface.setKeepScreenOn(true);

        /* Start / resume playback */
        if (savedIndexPosition > -1) {
            mLibVLC.playIndex(savedIndexPosition);
        } else if (mLocation != null && mLocation.length() > 0 && !dontParse) {
            savedIndexPosition = mLibVLC.readMedia(mLocation, false);
        }

        if (mLocation != null && mLocation.length() > 0 && !dontParse) {
            // restore last position
            Media media = DatabaseManager.getInstance(this).getMedia(this, mLocation);
            if (media != null && media.getTime() > 0)
                mLibVLC.setTime(media.getTime());

            try {
                title = URLDecoder.decode(mLocation, "UTF-8");
            } catch (UnsupportedEncodingException e) {
            }
            if (title.startsWith("file:")) {
                title = new File(title).getName();
                int dotIndex = title.lastIndexOf('.');
                if (dotIndex != -1)
                    title = title.substring(0, dotIndex);
            }
        } else if(itemTitle != null) {
            title = itemTitle;
        }
        mTitle.setText(title);
    }
}
