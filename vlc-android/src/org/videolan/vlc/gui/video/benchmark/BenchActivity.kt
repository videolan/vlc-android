/*****************************************************************************
 * BenchActivity.java
 *
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
 */

package org.videolan.vlc.gui.video.benchmark

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.VLCInstance
import java.io.*

/**
 * BenchActivity is a class that overrides VideoPlayerActivity through ShallowVideoPlayer.
 * BenchActivity can perform several tests:
 * - PLAYBACK:
 * The class just plays the video, sending back statistics to VLCBenchmark.
 * - SCREENSHOTS:
 * The class waits for the video to buffer the first time to do the initial setup.
 * Then it starts an activity that asks for the permission to take screenshots.
 * If that permission is granted, a seek is performed to the first screenshot timestamp.
 * Once the buffering is finished, a callback is set on the next image available.
 * That callback writes the image bitmap to a file, and calls the seek to the next timestamp.
 */

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class BenchActivity : ShallowVideoPlayer() {

    private var mTimeOut: Runnable? = null

    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mDensity: Int = 0
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mProjectionManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mImageReader: ImageReader? = null
    private var mHandler: Handler? = null
    private var mTimestamp: List<Long>? = null
    private var mIsScreenshot = false
    private var mScreenshotCount = 0
    private var mScreenshotNumber = 0
    private var mLateFrameCounter = 0
    private var mSetup = false
    /* Differentiates between buffering due or not to seeking */
    private var mSeeking = false
    /* set to true when VLC crashes */
    private var mVLCFailed = false
    /* set to true when video is in hardware decoding */
    private var mIsHardware = false
    /* set to true when Vout event is received
     * used to check if hardware decoder works */
    private var mHasVout = false
    /* screenshot directory location */
    private var screenshotDir: String? = null
    /* bool to wait in pause for user permission */
    private var mWritePermission = false

    /* android_display vout is forced on hardware tests */
    /* this option is set using the opengl sharedPref */
    /* Saves the original value to reset it after the benchmark */
    private var mOldOpenglValue: String? = "-2"

    /* To avoid storing benchmark samples in the user's vlc history, the user's preference is
    *  saved, to be restored at the end of the test */
    private var mOldHistoryBoolean = true

    /* Used to determine when a playback is stuck */
    private var mPosition = 0f
    private var mPositionCounter = 0

    /* File in which vlc will store logs in case of a crash or freeze */
    private var stacktraceFile: String? = null

    override fun onChanged(service: PlaybackService?) {
        super.onChanged(service)
        if (mIsHardware && this.service != null) {
            val sharedPref = Settings.getInstance(this)
            mOldOpenglValue = sharedPref.getString(PREFERENCE_OPENGL, "-1")
            mOldHistoryBoolean = sharedPref.getBoolean(PREFERENCE_PLAYBACK_HISTORY, true)
            val editor = sharedPref.edit()
            editor.putString(PREFERENCE_OPENGL, "0")
            editor.putBoolean(PREFERENCE_PLAYBACK_HISTORY, false)
            editor.commit()
            VLCInstance.restart()
            this.service?.restartMediaPlayer()
        }
    }

    override fun loadMedia() {
        if (service != null) {
            service!!.setBenchmark()
            if (mIsHardware) {
                service!!.setHardware()
            }
        }
        super.loadMedia()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // checking for permission other than granted
        if (requestCode == PERMISSION_REQUEST_WRITE &&
                grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            errorFinish("Failed to get write permission for screenshots")
        } else if (requestCode == PERMISSION_REQUEST_WRITE && grantResults.isNotEmpty()) {
            mWritePermission = true
            if (mIsScreenshot) {
                /* Temporizing for the authorisation popup to disappear */
                mHandler!!.postDelayed({ seekScreenshot() }, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        /* Crash handler setup */
        StartActivityOnCrash.setUp(this)

        val intent = intent

        /* Enabling hardware mode if necessary*/
        /* Stops the hardware decoder falling back to software */
        mIsHardware = !intent.getBooleanExtra(EXTRA_HARDWARE, true)
        isBenchmark = true

        super.onCreate(savedInstanceState)

        /* Determining the benchmark mode */
        if (!intent.hasExtra(EXTRA_ACTION)) {
            errorFinish("Missing action intent extra")
            return
        }

        if (intent.hasExtra(EXTRA_STACKTRACE_FILE)) {
            stacktraceFile = intent.getStringExtra(EXTRA_STACKTRACE_FILE)
        }

        when (intent.getStringExtra(EXTRA_ACTION)) {
            EXTRA_ACTION_PLAYBACK -> {
            }
            EXTRA_ACTION_QUALITY -> {
                if (!intent.hasExtra(EXTRA_SCREENSHOT_DIR)) {
                    errorFinish("Failed to get screenshot directory location")
                    return
                }
                screenshotDir = intent.getStringExtra(EXTRA_SCREENSHOT_DIR)
                mIsScreenshot = intent.hasExtra(EXTRA_TIMESTAMPS)
                if (!mIsScreenshot) {
                    errorFinish("Missing screenshots timestamps")
                    return
                }
                if (intent.getSerializableExtra(EXTRA_TIMESTAMPS) is List<*>) {
                    mTimestamp = intent.getSerializableExtra(EXTRA_TIMESTAMPS) as List<Long>
                } else {
                    errorFinish("Failed to get timestamps")
                    return
                }

                /* Deactivates secondary displays */
                enableCloneMode = true
                displayManager.release()
            }
        }

        // blocking display in landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Minimum apk for benchmark is 21, so this warning is a non-issue
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        // Check for write permission, if false will ask
        // for them after asking for screenshot permission
        mWritePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        setTimeout()
    }

    /**
     * On some weak devices, the hardware decoder will end up hung.
     * To avoid stopping the benchmark, a timeout is set to stop vlc
     * and return to the benchmark for the next test.
     */
    private fun setTimeout() {
        if (mSetup && mHandler != null) {
            if (mTimeOut != null) {
                mHandler!!.removeCallbacks(mTimeOut)
                mTimeOut = null
            }
            mTimeOut = Runnable {
                Log.e(TAG, "VLC Seek Froze")
                errorFinish("VLC Seek Froze")
            }
            mHandler!!.postDelayed(mTimeOut, 10000)
        }
    }

    /**
     * Reacts on the event buffering before calling super:
     *
     *
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
    @TargetApi(21)
    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Vout -> mHasVout = true
            MediaPlayer.Event.TimeChanged -> setTimeout()
            MediaPlayer.Event.PositionChanged -> {
                val pos = event.positionChanged
                if (!mIsScreenshot) {
                    when {
                        pos != mPosition -> {
                            mPosition = pos
                            mPositionCounter = 0
                        }
                        mPositionCounter > 50 -> errorFinish("VLC Playback Froze")
                        else -> mPositionCounter += 1
                    }
                }
            }
            MediaPlayer.Event.Buffering -> if (event.buffering == 100f) {
                /* initial setup that has to be done when the video
                     * has finished the first buffering */
                if (!mSetup) {
                    mSetup = true
                    if (mIsScreenshot) {
                        service?.pause()
                        val metrics = DisplayMetrics()
                        windowManager.defaultDisplay.getRealMetrics(metrics)
                        mWidth = metrics.widthPixels
                        mHeight = metrics.heightPixels
                        mDensity = metrics.densityDpi
                        mProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        if (mProjectionManager == null) {
                            errorFinish("Failed to create MediaProjectionManager")
                        }
                        mHandler = Handler()
                        val screenshotIntent: Intent = mProjectionManager!!.createScreenCaptureIntent()
                        screenshotIntent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                        screenshotIntent.flags = Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
                        startActivityForResult(screenshotIntent, REQUEST_SCREENSHOT)
                    }
                }
                /* Screenshot callback setup */
                if (mIsScreenshot && mSetup && mScreenshotNumber < mTimestamp!!.size && mSeeking) {
                    mSeeking = false
                    mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2)
                    mVirtualDisplay = mMediaProjection!!.createVirtualDisplay("testScreenshot", mWidth,
                            mHeight, mDensity, VIRTUAL_DISPLAY_FLAGS,
                            mImageReader!!.surface, null, mHandler)

                    if (mVirtualDisplay == null) {
                        errorFinish("Failed to create Virtual Display")
                    }
                    try {
                        mImageReader!!.setOnImageAvailableListener(ImageAvailableListener(), mHandler)
                    } catch (e: IllegalArgumentException) {
                        errorFinish("Failed to create screenshot callback")
                    }

                }
            }
        }
        super.onMediaPlayerEvent(event)
    }

    /**
     * Seeks to the position of the next screenshot,
     * triggering the buffering of the video.
     * At the end of the video buffering, the screenshot callback is set.
     */
    private fun seekScreenshot() {
        // tmp fix
        // mService should never be null in this context but it happens
        if (service == null) {
            Log.w(TAG, "seekScreenshot: service is null")
            errorFinish("PlayerService is null")
            return
        }
        if (mProjectionManager != null && mScreenshotCount < mTimestamp!!.size) {
            setTimeout()
            seek(mTimestamp!![mScreenshotCount])
            ++mScreenshotCount
            mSeeking = true
        } else {
            finish()
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
    public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_SCREENSHOT && resultData != null && resultCode == Activity.RESULT_OK) {
            /* Hiding navigation bar */
            val decorView = window.decorView
            val uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
            decorView.systemUiVisibility = uiOptions

            /* Creating the MediaProjection used later for each VirtualDisplay creation */
            mMediaProjection = mProjectionManager!!.getMediaProjection(resultCode, resultData)
            if (mMediaProjection == null) {
                errorFinish("Failed to create MediaProjection")
            }
            if (mWritePermission) {
                /* Temporizing for the authorisation popup to disappear */
                mHandler!!.postDelayed({ seekScreenshot() }, 1000)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE)
            }
        } else {
            errorFinish("Failed to get screenshot permission")
        }
    }

    /**
     * Override of VideoPlayerActivity's exit aiming to catch the resultCode
     * if the resultCode is different from RESULT_OK, boolean mVLCFailed is set to true,
     * then we will not override the finish(), and change the resultCode.
     * @param resultCode VideoPlayerActivity's resultCode
     */
    override fun exit(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            mVLCFailed = true
        }
        super.exit(resultCode)
    }

    /**
     * To be called when the error is big enough to return to VLCBenchmark
     * @param resultString error description for display in VLCBenchmark
     */
    private fun errorFinish(resultString: String) {
        Log.e(TAG, "errorFinish: $resultString")
        val sendIntent = Intent()
        sendIntent.putExtra("Error", resultString)
        getStackTrace()
        setResult(RESULT_FAILED, sendIntent)
        super.finish()
    }

    /**
     * Method reading vlc-android logs so that the benchmark can get the cause
     * of the crash / freeze
     */
    private fun getStackTrace() = AppScope.launch(Dispatchers.IO) {
        if (stacktraceFile != null) {
            try {
                val pid = android.os.Process.myPid()
                /*Displays priority, tag, and PID of the process issuing the message from this pid*/
                val process = Runtime.getRuntime().exec("logcat -d -v brief --pid=$pid")
                val bufferedReader = BufferedReader(
                        InputStreamReader(process.inputStream))
                var line = bufferedReader.readLine()
                val stacktraceContent = StringBuilder()
                while (line != null) {
                    stacktraceContent.append(line)
                    line = bufferedReader.readLine()
                }
                val outputFile = File(stacktraceFile)
                val fileOutputStream = FileOutputStream(outputFile)
                fileOutputStream.write(stacktraceContent.toString().toByteArray(Charsets.UTF_8))
                fileOutputStream.close()
                bufferedReader.close()
                /* Clear logs, so that next test is not polluted by current one */
                ProcessBuilder()
                        .command("logcat", "-c")
                        .redirectErrorStream(true)
                        .start()
            } catch (ex: IOException) {
                Log.e(TAG, ex.toString())
            }
        } else {
            Log.e(TAG, "getStackTrace: There was no stacktrace file provided")
        }
    }

    /**
     * Method analysing VLC logs to find warnings,
     * and report them to VLCBenchmark
     */
    private fun checkLogs() = AppScope.launch (Dispatchers.IO){
        var counter = 0
        try {
            val pid = android.os.Process.myPid()
            /*Displays priority, tag, and PID of the process issuing the message from this pid*/
            val process = Runtime.getRuntime().exec("logcat -d -v brief --pid=$pid")
            val inputStreamReader = InputStreamReader(process.inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            var line = bufferedReader.readLine()
            while (line != null) {
                if (line.contains("W/") || line.contains("E/")) {
                    if (line.contains(" late ")) {
                        counter += 1
                    }
                }
                line = bufferedReader.readLine()
            }
            inputStreamReader.close()
            bufferedReader.close()
            /* Clear logs, so that next test is not polluted by current one */
            ProcessBuilder()
                    .command("logcat", "-c")
                    .redirectErrorStream(true)
                    .start()
        } catch (ex: IOException) {
            Log.e(TAG, ex.toString())
        }

        mLateFrameCounter = counter
    }

    /**
     * Sets up the benchmark statistics to be returned
     * before calling super
     */
    @TargetApi(21)
    override fun finish() {
        /* Resetting vout preference to it value before the benchmark */
        if (mIsHardware && mOldOpenglValue != "-2") {
            val sharedPref = Settings.getInstance(this)
            val editor = sharedPref.edit()
            editor.putString(PREFERENCE_OPENGL, mOldOpenglValue)
            editor.putBoolean(PREFERENCE_PLAYBACK_HISTORY, mOldHistoryBoolean)
            editor.commit()
            VLCInstance.restart()
        }
        /* Case of error in VideoPlayerActivity, then finish is not overridden */
        if (mVLCFailed) {
            super.finish()
            return
        }
        if (!mHasVout) {
            setResult(RESULT_NO_HW, null)
            super.finish()
        }
        val sendIntent = Intent()
        checkLogs()
        if (service != null) {
            val stats = service!!.lastStats
            sendIntent.putExtra("percent_of_bad_seek", 0.0)
            sendIntent.putExtra("number_of_dropped_frames", stats?.lostPictures ?: 100)
            sendIntent.putExtra("screenshot_folder", Environment.getExternalStorageDirectory().toString() + File.separator + "screenshotFolder")
            sendIntent.putExtra("late_frames", mLateFrameCounter)
            setResult(Activity.RESULT_OK, sendIntent)
            super.finish()
        } else {
            errorFinish("PlaybackService is null")
        }
    }

    @TargetApi(21)
    override fun onDestroy() {
        if (mImageReader != null) {
            try {
                mImageReader!!.setOnImageAvailableListener(null, null)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to destroy screenshot callback")
            }

        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay!!.release()
        }
        if (mMediaProjection != null) {
            mMediaProjection!!.stop()
        }
        if (mTimeOut != null && mHandler != null) {
            mHandler!!.removeCallbacks(mTimeOut)
        }
        super.onDestroy()
    }

    /**
     * Callback that is called when the first image is available after setting up
     * ImageReader in onEventReceive(...) at the end of the video buffering.
     *
     *
     * It takes the screenshot.
     */
    @TargetApi(19)
    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        @TargetApi(21)
        override fun onImageAvailable(reader: ImageReader) {
            mHandler!!.postDelayed({
                var outputStream: FileOutputStream? = null
                var image: Image? = null
                val bitmap: Bitmap?
                try {
                    image = mImageReader!!.acquireLatestImage()
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Failed to acquire latest image for screenshot.")
                }

                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer.rewind()
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * mWidth

                    bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight,
                            Bitmap.Config.ARGB_8888)
                    if (bitmap != null) {
                        bitmap.copyPixelsFromBuffer(buffer)
                        val folder = File(screenshotDir)

                        if (!folder.exists()) {
                            if (!folder.mkdir()) {
                                errorFinish("Failed to create screenshot directory")
                            }
                        }
                        val imageFile = File(folder.absolutePath + File.separator + "Screenshot_" + mScreenshotNumber + ".png")
                        mScreenshotNumber += 1
                        try {
                            outputStream = FileOutputStream(imageFile)
                        } catch (e: IOException) {
                            Log.e(TAG, "Failed to create outputStream")
                        }

                        if (outputStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }

                        bitmap.recycle()
                        image.close()
                        if (outputStream != null) {
                            try {
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                Log.e(TAG, "Failed to release outputStream")
                            }

                        }
                    }
                }
                try {
                    mImageReader!!.setOnImageAvailableListener(null, null)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Failed to delete ImageReader callback")
                }

                mVirtualDisplay!!.release()
                mVirtualDisplay = null
                mImageReader!!.close()
                if (mScreenshotNumber < mTimestamp!!.size) {
                    seekScreenshot()
                } else {
                    finish()
                }
            }, 3000)
        }
    }

    companion object {

        private const val EXTRA_TIMESTAMPS = "extra_benchmark_timestamps"
        private const val EXTRA_ACTION_QUALITY = "extra_benchmark_action_quality"
        private const val EXTRA_ACTION_PLAYBACK = "extra_benchmark_action_playback"
        private const val EXTRA_SCREENSHOT_DIR = "extra_benchmark_screenshot_dir"
        private const val EXTRA_ACTION = "extra_benchmark_action"
        private const val EXTRA_HARDWARE = "extra_benchmark_disable_hardware"
        private const val EXTRA_STACKTRACE_FILE = "stacktrace_file"

        private const val TAG = "VLCBenchmark"
        private const val REQUEST_SCREENSHOT = 666

        private const val RESULT_FAILED = 6
        private const val RESULT_NO_HW = 1

        private const val VIRTUAL_DISPLAY_FLAGS = 0

        private const val PERMISSION_REQUEST_WRITE = 1

        private const val PREFERENCE_PLAYBACK_HISTORY = "playback_history"
        private const val PREFERENCE_OPENGL = "opengl"
    }
}

