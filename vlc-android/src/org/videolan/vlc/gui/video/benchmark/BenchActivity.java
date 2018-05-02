/*****************************************************************************
 * BenchActivity.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video.benchmark;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * BenchActivity is a class that overrides VideoPlayerActivity through ShallowVideoPlayer.
 * BenchActivity can perform several tests:
 * - PLAYBACK:
 *  The class just plays the video, sending back statistics to VLCBenchmark.
 * - SCREENSHOTS:
 *  The class waits for the video to buffer the first time to do the initial setup.
 *  Then it starts an activity that asks for the permission to take screenshots.
 *  If that permission is granted, a seek is performed to the first screenshot timestamp.
 *  Once the buffering is finished, a callback is set on the next image available.
 *  That callback writes the image bitmap to a file, and calls the seek to the next timestamp.
 */

public class BenchActivity extends ShallowVideoPlayer {

    private static final String SCREENSHOTS = "org.videolan.vlc.gui.video.benchmark.ACTION_SCREENSHOTS";
    private static final String PLAYBACK = "org.videolan.vlc.gui.video.benchmark.ACTION_PLAYBACK";

    private static final String TIMESTAMPS = "org.videolan.vlc.gui.video.benchmark.TIMESTAMPS";
    private static final String INTENT_SCREENSHOT_DIR = "SCREENSHOT_DIR";
    private static final String TAG = "VLCBenchmark";
    private static final int REQUEST_SCREENSHOT = 666;

    private static final int RESULT_FAILED = 0;
    private static final int RESULT_NO_HW = 1;

    private static final int VIRTUAL_DISPLAY_FLAGS = 0;

    private Runnable mTimeOut = null;

    private int mWidth;
    private int mHeight;
    private int mDensity;
    private VirtualDisplay mVirtualDisplay = null;
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection = null;
    private ImageReader mImageReader = null;
    private Handler mHandler = null;
    private List<Long> mTimestamp;
    private boolean mIsScreenshot = false;
    private int mScreenshotCount = 0;
    private int mScreenshotNumber = 0;
    private int mLateFrameCounter = 0;
    private boolean mSetup = false;
    /* Differentiates between buffering due or not to seeking */
    private boolean mSeeking = false;
    /* set to true when VLC crashes */
    private boolean mVLCFailed = false;
    /* set to true when video is in hardware decoding */
    private boolean mIsHardware = false;
    /* set to true when Vout event is received
     * used to check if hardware decoder works */
    private boolean mHasVout = false;
    /* screenshot directory location */
    private String screenshotDir;

    @Override
    protected void loadMedia() {
        if (mService != null) {
            mService.setBenchmark();
            if (mIsHardware) {
                mService.setHardware();
            }
        }
        super.loadMedia();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* Crash handler setup */
        StartActivityOnCrash.setUp(this);

        Intent intent = getIntent();
        /* Enabling hardware mode if necessary*/
        /* Stops the hardware decoder falling back to software */
        if (!intent.hasExtra("disable_hardware")) {
            mIsHardware = true;
        }
        mIsBenchmark = true;

        if (!intent.hasExtra(INTENT_SCREENSHOT_DIR)) {
            errorFinish("Failed to get screenshot directory location");
        }
        screenshotDir = intent.getStringExtra(INTENT_SCREENSHOT_DIR);

        super.onCreate(savedInstanceState);

        /* Determining the benchmark mode */
        switch (intent.getAction()) {
            case PLAYBACK:
                break;
            case SCREENSHOTS:
                mIsScreenshot = intent.hasExtra(TIMESTAMPS);
                if (!mIsScreenshot) {
                    errorFinish("Missing screenshots timestamps");
                }
                if (intent.getSerializableExtra(TIMESTAMPS) instanceof List) {
                    mTimestamp = (List<Long>) intent.getSerializableExtra(TIMESTAMPS);
                } else {
                    errorFinish("Failed to get timestamps");
                }

                /* Deactivates secondary displays */
                mEnableCloneMode = true;
                mDisplayManager.release();
        }

        // blocking display in landscape orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    /**
     * Reacts on the event buffering before calling super:
     * <p/>
     * if end of buffering, initialises screen info,
     * the projectionManager, and handler used, and starts
     * the activity that asks for the screenshot permission.
     *
     * if end of buffering, and boolean seeking is true
     * sets screenshot callback.
     *
     * if not end of buffering, and seeking, checks for seek timeouts.
     *
     * @param event mediaPlayer events
     */
    @Override
    @TargetApi(21)
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Vout:
                mHasVout = true;
            case MediaPlayer.Event.Buffering:
                if (event.getBuffering() == 100f) {
                    /* initial setup that has to be done when the video
                     * has finished the first buffering */
                    if (!mSetup) {
                        mSetup = true;
                        if (mIsScreenshot) {
                            mService.pause();
                            Point size = new Point();
                            getWindowManager().getDefaultDisplay().getSize(size);
                            mWidth = size.x;
                            mHeight = size.y;
                            DisplayMetrics metrics = new DisplayMetrics();
                            getWindowManager().getDefaultDisplay().getMetrics(metrics);
                            mDensity = metrics.densityDpi;
                            mProjectionManager =
                                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                            if (mProjectionManager == null) {
                                errorFinish("Failed to create MediaProjectionManager");
                            }
                            mHandler = new Handler();
                            Intent screenshotIntent;
                            screenshotIntent = mProjectionManager.createScreenCaptureIntent();
                            screenshotIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            screenshotIntent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                            startActivityForResult(screenshotIntent, REQUEST_SCREENSHOT);
                        }
                    }
                    /* Screenshot callback setup */
                    if (mIsScreenshot && mSetup && mScreenshotNumber < mTimestamp.size() && mSeeking) {
                        mSeeking = false;
                        /* deactivating timeout*/
                        if (mTimeOut != null) {
                            mHandler.removeCallbacks(mTimeOut);
                            mTimeOut = null;
                        }
                        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);

                        mVirtualDisplay =
                                mMediaProjection.createVirtualDisplay("testScreenshot", mWidth,
                                        mHeight, mDensity, VIRTUAL_DISPLAY_FLAGS,
                                        mImageReader.getSurface(), null, mHandler);

                        if (mVirtualDisplay == null) {
                            errorFinish("Failed to create Virtual Display");
                        }
                        try {
                            mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
                        } catch (IllegalArgumentException e) {
                            errorFinish("Failed to create screenshot callback");
                        }
                    }
                }
                break;
        }
        super.onMediaPlayerEvent(event);
    }

    /**
     * Seeks to the position of the next screenshot,
     * triggering the buffering of the video.
     * At the end of the video buffering, the screenshot callback is set.
     */
    private void seekScreenshot() {
        if (mProjectionManager != null && mScreenshotCount < mTimestamp.size()) {
            seek(mTimestamp.get(mScreenshotCount));
            /* Setting a timeout system */
            mTimeOut = new Runnable() {
                @Override
                public void run() {
                    errorFinish("Seek froze");
                }
            };
            mHandler.postDelayed(mTimeOut, 5000);
            ++mScreenshotCount;
            mSeeking = true;
        } else {
            finish();
        }
    }

    /**
     * Called on return of launched activities,
     * and particularly the screenshot authorisation activity.
     * If successful, sets up the mediaProjection for later virtual displays
     *
     * @param requestCode activity request code
     * @param resultCode  activity result code
     * @param resultData  activity result data
     */
    @TargetApi(21)
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_SCREENSHOT && resultData != null && resultCode == RESULT_OK) {
            /* Hiding navigation bar */
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);

            /* Creating the MediaProjection used later for each VirtualDisplay creation */
            mMediaProjection = mProjectionManager.getMediaProjection(resultCode, resultData);
            if (mMediaProjection == null) {
                errorFinish("Failed to create MediaProjection");
            }

            /* Temporizing for the authorisation popup to disappear */
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    seekScreenshot();
                }
            }, 1000);
        } else {
            errorFinish("Failed to get screenshot permission");
        }
    }

    /**
     * Override of VideoPlayerActivity's exit aiming to catch the resultCode
     * if the resultCode is different from RESULT_OK, boolean mVLCFailed is set to true,
     * then we will not override the finish(), and change the resultCode.
     * @param resultCode VideoPlayerActivity's resultCode
     */
    @Override
    public void exit (int resultCode) {
        if (resultCode != RESULT_OK) {
            mVLCFailed = true;
        }
        super.exit(resultCode);
    }

    /**
     * To be called when the error is big enough to return to VLCBenchmark
     * @param resultString error description for display in VLCBenchmark
     */
    private void errorFinish(String resultString) {
        Intent sendIntent = new Intent();
        sendIntent.putExtra("Error", resultString);
        setResult(RESULT_FAILED, sendIntent);
        super.finish();
    }
    /**
     * Method analysing VLC logs to find warnings,
     * and report them to VLCBenchmark
     */
    private void checkLogs() {
        int counter = 0;

        try {
            Process process = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("W VLC") || line.contains("E VLC")) {
                    if (line.contains(" late ")) {
                        counter += 1;
                    }
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, ex.toString());
        }
        mLateFrameCounter = counter;
    }

    /**
     * Sets up the benchmark statistics to be returned
     * before calling super
     */
    @Override
    @TargetApi(21)
    public void finish() {
        /* Case of error in VideoPlayerActivity, then finish is not overridden */
        if (mVLCFailed) {
            super.finish();
            return;
        }
        if (!mHasVout) {
            setResult(RESULT_NO_HW, null);
            super.finish();
        }
        Intent sendIntent = new Intent();
        checkLogs();
        if (mService != null) {
            Media.Stats stats = mService.getLastStats();
            sendIntent.putExtra("percent_of_bad_seek", 0.0);
            sendIntent.putExtra("number_of_dropped_frames", (stats == null ? 100 : stats.lostPictures));
            sendIntent.putExtra("screenshot_folder", Environment.getExternalStorageDirectory() + File.separator + "screenshotFolder");
            sendIntent.putExtra("late_frames", mLateFrameCounter);
            setResult(RESULT_OK, sendIntent);
            super.finish();
        } else {
            errorFinish("PlaybackService is null");
        }
    }

    @Override
    @TargetApi(21)
    protected void onDestroy() {
        if (mImageReader != null) {
            try {
                mImageReader.setOnImageAvailableListener(null, null);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to destroy screenshot callback");
            }
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
        super.onDestroy();
    }

    /**
     * Callback that is called when the first image is available after setting up
     * ImageReader in onEventReceive(...) at the end of the video buffering.
     * <p/>
     * It takes the screenshot.
     */
    @TargetApi(19)
    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        @TargetApi(21)
        public void onImageAvailable(ImageReader reader) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    FileOutputStream outputStream = null;
                    Image image = null;
                    Bitmap bitmap;
                    try {
                        image = mImageReader.acquireLatestImage();
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Failed to acquire latest image for screenshot.");
                    }
                    if (image != null) {
                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * mWidth;

                        bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight,
                                Bitmap.Config.ARGB_8888);
                        if (bitmap != null) {
                            bitmap.copyPixelsFromBuffer(buffer);

                            File folder = new File(screenshotDir);

                            if (!folder.exists()) {
                                if (!folder.mkdir()) {
                                    errorFinish("Failed to create screenshot directory");
                                }
                            }

                            //File imageFile = new File(getExternalFilesDir(null), "Screenshot_" + sScreenshotNumber + ".jpg");
                            File imageFile = new File(folder.getAbsolutePath() + File.separator + "Screenshot_" + mScreenshotNumber + ".jpg");
                            mScreenshotNumber += 1;
                            try {
                                outputStream = new FileOutputStream(imageFile);
                            } catch (IOException e) {
                                Log.e(TAG, "Failed to create outputStream");
                            }
                            if (outputStream != null) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                            }

                            bitmap.recycle();
                            image.close();
                            if (outputStream != null) {
                                try {
                                    outputStream.flush();
                                    outputStream.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Failed to release outputStream");
                                }
                            }
                        }
                    }
                    try {
                        mImageReader.setOnImageAvailableListener(null, null);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Failed to delete ImageReader callback");
                    }
                    mVirtualDisplay.release();
                    mVirtualDisplay = null;
                    mImageReader.close();
                    if (mScreenshotNumber < mTimestamp.size()) {
                        seekScreenshot();
                    } else {
                        finish();
                    }
                }
            }, 1000);
        }
    }
}

