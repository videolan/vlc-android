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
import org.videolan.vlc.EventManager;
import org.videolan.vlc.LibVLC;
import org.videolan.vlc.LibVlcException;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.WeakHandler;
import org.videolan.vlc.gui.PreferencesActivity;
import org.videolan.vlc.gui.audio.AudioPlayerActivity;
import org.videolan.vlc.interfaces.IPlayerControl;
import org.videolan.vlc.interfaces.OnPlayerControlListener;
import org.videolan.vlc.widget.PlayerControlClassic;
import org.videolan.vlc.widget.PlayerControlWheel;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
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
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
    private View mOverlay;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SURFACE_SIZE = 3;
    private static final int FADE_OUT_INFO = 4;
    private static final int HIDE_NAV = 5;
    private boolean mDragging;
    private boolean mShowing;
    private SeekBar mSeekbar;
    private TextView mTitle;
    private TextView mSysTime;
    private TextView mBattery;
    private TextView mTime;
    private TextView mLength;
    private TextView mInfo;
    private IPlayerControl mControls;
    private ImageButton mAudio;
    private ImageButton mSubtitles;
    private ImageButton mLock;
    private ImageButton mSize;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mEndReached;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;

    //Audio
    private AudioManager mAudioManager;
    private int mAudioMax;
    private int mAudioDisplayRange;
    private float mTouchY, mVol;
    private boolean mIsAudioChanged;
    private String[] mAudioTracks;
    private String[] mSubtitleTracks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if(Util.isICSOrLater())
            getWindow().getDecorView().findViewById(android.R.id.content).setOnSystemUiVisibilityChangeListener(
                    new OnSystemUiVisibilityChangeListener() {
                        @Override
                        @TargetApi(14)
                        public void onSystemUiVisibilityChange(int visibility) {
                            if(visibility == View.SYSTEM_UI_FLAG_VISIBLE && Util.isICSOrLater())
                                mHandler.sendMessageDelayed(mHandler.obtainMessage(HIDE_NAV), OVERLAY_TIMEOUT);
                        }
                    }
            );

        /** initialize Views an their Events */
        mOverlayHeader = findViewById(R.id.player_overlay_header);
        mOverlay = findViewById(R.id.player_overlay);

        /* header */
        mTitle = (TextView) findViewById(R.id.player_overlay_title);
        mSysTime = (TextView) findViewById(R.id.player_overlay_systime);
        mBattery = (TextView) findViewById(R.id.player_overlay_battery);

        mTime = (TextView) findViewById(R.id.player_overlay_time);
        mLength = (TextView) findViewById(R.id.player_overlay_length);
        // the info textView is not on the overlay
        mInfo = (TextView) findViewById(R.id.player_overlay_info);

        mControls = pref.getBoolean("enable_wheel_bar", false)
                ? new PlayerControlWheel(this)
                : new PlayerControlClassic(this);
        mControls.setOnPlayerControlListener(mPlayerControlListener);
        FrameLayout mControlContainer = (FrameLayout) findViewById(R.id.player_control);
        mControlContainer.addView((View) mControls);

        mAudio = (ImageButton) findViewById(R.id.player_overlay_audio);
        mAudio.setOnClickListener(mAudioListener);

        mSubtitles = (ImageButton) findViewById(R.id.player_overlay_subtitle);
        mSubtitles.setOnClickListener(mSubtitlesListener);

        mLock = (ImageButton) findViewById(R.id.player_overlay_lock);
        mLock.setOnClickListener(mLockListener);

        mSize = (ImageButton) findViewById(R.id.player_overlay_size);
        mSize.setOnClickListener(mSizeListener);

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

        EventManager em = EventManager.getIntance();
        em.addHandler(eventHandler);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        load();
    }

    @Override
    protected void onStart() {
        super.onStart();
        dimStatusBar(true);
        mSwitchingView = false;
    }

    @Override
    protected void onPause() {
        if(mSwitchingView) {
            super.onPause();
            return;
        }
        long time = 0;
        if (mLibVLC.isPlaying()) {
            time = mLibVLC.getTime() - 5000;
            mLibVLC.pause();
        }
        mSurface.setKeepScreenOn(false);

        // Save position
        if (time >= 0) {
            SharedPreferences preferences = getSharedPreferences(PreferencesActivity.NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(PreferencesActivity.LAST_MEDIA, mLocation);
            editor.putLong(PreferencesActivity.LAST_TIME, time);
            editor.commit();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mBatteryReceiver);
        if (mLibVLC != null && !mSwitchingView) {
            mLibVLC.stop();
        }

        EventManager em = EventManager.getIntance();
        em.removeHandler(eventHandler);

        mAudioManager = null;

        if(mSwitchingView) {
            Log.d(TAG, "mLocation = \"" + mLocation + "\"");
            AudioServiceController.getInstance().showWithoutParse(mLocation);
            Intent i = new Intent(this, AudioPlayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(i);
        }
        //AudioServiceController.getInstance().unbindAudioService(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        AudioServiceController.getInstance().bindAudioService(this);
        super.onResume();
    }

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int batteryLevel = intent.getIntExtra("level", 0);
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
        setSurfaceSize(mVideoWidth, mVideoHeight);
        super.onConfigurationChanged(newConfig);
    }

    public void setSurfaceSize(int width, int height) {
        // store video size
        mVideoHeight = height;
        mVideoWidth = width;
        Message msg = mHandler.obtainMessage(SURFACE_SIZE);
        mHandler.sendMessage(msg);
    }

    /**
     * Lock screen rotation
     */
    private void lockScreen() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int rotation = display.getOrientation();
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
                    Log.e(TAG, "Event not handled");
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
                    activity.dimStatusBarICS();
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
        int dw = getWindowManager().getDefaultDisplay().getWidth();
        int dh = getWindowManager().getDefaultDisplay().getHeight();

        // calculate aspect ratio
        double ar = (double) mVideoWidth / (double) mVideoHeight;
        // calculate display aspect ratio
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
                dw = mVideoWidth;
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

        if (mAudioDisplayRange == 0)
            mAudioDisplayRange = Math.min(
                    getWindowManager().getDefaultDisplay().getWidth(),
                    getWindowManager().getDefaultDisplay().getHeight());

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                mTouchY = event.getRawY();
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                mIsAudioChanged = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float y = event.getRawY();

                int delta = (int) (((mTouchY - y) / mAudioDisplayRange) * mAudioMax);
                int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
                if (delta != 0) {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, AudioManager.FLAG_SHOW_UI);
                    mIsAudioChanged = true;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (!mIsAudioChanged) {
                    if (!mShowing) {
                        showOverlay();
                    } else {
                        hideOverlay(true);
                        if(Util.isICSOrLater())
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(HIDE_NAV), OVERLAY_TIMEOUT);
                    }
                }
                break;
        }
        return mIsAudioChanged;
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlay(3600000);
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
    private final OnClickListener mAudioListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mAudioTracks == null || mAudioTracks.length <= 1)
                return;

            int current = mLibVLC.getAudioTrack() - 1;

            Builder builder = new AlertDialog.Builder(VideoPlayerActivity.this);
            builder.setSingleChoiceItems(mAudioTracks, current, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    mLibVLC.setAudioTrack(which + 1);
                }
            });

            builder.show();
        }
    };

    /**
    *
    */
   private final OnClickListener mSubtitlesListener = new OnClickListener() {
       @Override
    public void onClick(View v) {
           if (mSubtitleTracks == null || mSubtitleTracks.length == 0)
               return;

           int current = mLibVLC.getSpuTrack();

           Builder builder = new AlertDialog.Builder(VideoPlayerActivity.this);
           builder.setSingleChoiceItems(mSubtitleTracks, current, new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                   dialog.dismiss();
                   mLibVLC.setSpuTrack(which);
               }
           });

           builder.show();
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
            long position = mLibVLC.getTime();
            mLibVLC.setTime(position + delta);
            showOverlay();
        }

        @Override
        public void onSeekTo(long position) {
            mLibVLC.setTime(position);
            mTime.setText(Util.millisToString(position));
        }

        @Override
        public long onWheelStart() {
            showOverlay(3600000);
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
            mOverlay.setVisibility(View.VISIBLE);
            dimStatusBar(false);
        }
        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
        if (mAudioTracks == null) {
            mAudioTracks = mLibVLC.getAudioTrackDescription();
            if (mAudioTracks != null && mAudioTracks.length > 1)
                mAudio.setVisibility(View.VISIBLE);
            else
                mAudio.setVisibility(View.GONE);
        }
        if (mSubtitleTracks == null) {
            mSubtitleTracks = mLibVLC.getSpuTrackDescription();
            if (mSubtitleTracks != null && mSubtitleTracks.length > 0)
                mSubtitles.setVisibility(View.VISIBLE);
            else
                mSubtitles.setVisibility(View.GONE);
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
                mOverlay.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            }
            mOverlayHeader.setVisibility(View.INVISIBLE);
            mOverlay.setVisibility(View.INVISIBLE);
            mShowing = false;
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed.
     * Android 3.0 and later
     */
    @TargetApi(11)
	private void dimStatusBar(boolean dim) {
        if(Util.isHoneycombOrLater()) {
            if (dim) {
                mSurface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            } else {
                mSurface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    /**
     * ICS full-screen profile
     * Android 4.0 and later only
     */
    @TargetApi(14)
    private void dimStatusBarICS() {
        if(Util.isICSOrLater())
            mSurface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
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
        String title = null;
        String lastLocation = null;
        long lastTime = 0;
        boolean dontParse = false;
        SharedPreferences preferences = getSharedPreferences(PreferencesActivity.NAME, MODE_PRIVATE);

        if (getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            /* Started from external application */
            mLocation = getIntent().getDataString();
        } else if(getIntent().getExtras() != null) {
            /* Started from VideoListActivity */
            mLocation = getIntent().getExtras().getString("itemLocation");
            dontParse = getIntent().getExtras().getBoolean("dontParse");
        }

        if (mLocation != null && mLocation.length() > 0 && !dontParse) {
            mLibVLC.readMedia(mLocation, false);
            mSurface.setKeepScreenOn(true);

            // Save media for next time, and restore position if it's the same one as before
            lastLocation = preferences.getString(PreferencesActivity.LAST_MEDIA, null);
            lastTime = preferences.getLong(PreferencesActivity.LAST_TIME, 0);
            if (lastTime > 0 && mLocation.equals(lastLocation))
                mLibVLC.setTime(lastTime);

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
            mTitle.setText(title);
        }
    }
}
