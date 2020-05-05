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

import android.annotation.TargetApi
import android.app.Activity
import android.content.*
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.vlc.PlaybackService
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.resources.VLCInstance
import org.videolan.vlc.R
import java.io.*

/**
 * BenchActivity is a class that overrides VideoPlayerActivity through ShallowVideoPlayer.
 * BenchActivity can perform several tests:
 * - PLAYBACK:
 * The class just plays the video, sending back statistics to VLCBenchmark.
 * - SCREENSHOTS:
 * The class waits for the video to buffer the first time to do the initial setup.
 * Then a seek is performed to the first screenshot timestamp.
 * Once the buffering is finished, a broadcast is sent to org.videolan.vlcbenchmark's service
 * to take a screenshot. Screenshots are taken in VLCBenchmark because, as of Android 10,
 * the MediaProjection API previously used to take screenshots now requires user input to
 * accept permission to take a screenshot at every restart of the app, which happens for every test
 * on vlc-android. By using this API in a service in VLCBenchmark, the user is only asked permission
 * once, when the user starts the benchmark.
 * VLCBenchmark then uses a broadcast to ask vlc-android to resume the sample to the next
 * screenshot timestamp or stop if there aren't any left.
 */

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(21)
class BenchActivity : ShallowVideoPlayer() {

    private lateinit var timeOut: Runnable
    private val timeoutHandler: Handler = Handler()
    private var screenshotsTimestamp: List<Long>? = null
    private var isScreenshot = false
    private var screenshotCount = 0
    private var lateFrameCounter = 0
    private var isSetup = false
    /* Differentiates between buffering due or not to seeking */
    private var isSeeking = false
    /* set to true when VLC crashes */
    private var hasVLCFailed = false
    /* set to true when video is in hardware decoding */
    private var isHardware = false
    /* set to true when Vout event is received
     * used to check if hardware decoder works */
    private var hasVout = false

    /* android_display vout is forced on hardware tests */
    /* this option is set using the opengl sharedPref */
    /* Saves the original value to reset it after the benchmark */
    private var oldOpenglValue: String? = "-2"

    /* To avoid storing benchmark samples in the user's vlc history, the user's preference is
    *  saved, to be restored at the end of the test */
    private var oldHistoryBoolean = true

    /* Used to determine when a playback is stuck */
    private var position = 0f
    private var positionCounter = 0

    /* File in which vlc will store logs in case of a crash or freeze */
    private var stacktraceFile: String? = null

    /* Receive continue benchmark action from VLCBenchmark after having taken screenshots */
    private var broadcastReceiver: BroadcastReceiver = ScreenshotBroadcastReceiver()

    override fun onServiceChanged(service: PlaybackService?) {
        super.onServiceChanged(service)
        if (isHardware && this.service != null) {
            val sharedPref = Settings.getInstance(this)
            oldOpenglValue = sharedPref.getString(PREFERENCE_OPENGL, "-1")
            oldHistoryBoolean = sharedPref.getBoolean(PREFERENCE_PLAYBACK_HISTORY, true)
            AppScope.launch(Dispatchers.IO) {
                sharedPref.edit().run {
                    putString(PREFERENCE_OPENGL, "0")
                    putBoolean(PREFERENCE_PLAYBACK_HISTORY, false)
                    apply()
                }
            }
            VLCInstance.restart()
            this.service?.restartMediaPlayer()
        }
    }

    override fun loadMedia() {
        service?.setBenchmark()
        if (isHardware) service?.setHardware()
        super.loadMedia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        /* Crash handler setup */
        StartActivityOnCrash.setUp(this)

        val intent = intent

        /* Enabling hardware mode if necessary*/
        /* Stops the hardware decoder falling back to software */
        isHardware = !intent.getBooleanExtra(EXTRA_HARDWARE, true)
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
                isScreenshot = intent.hasExtra(EXTRA_TIMESTAMPS)
                if (!isScreenshot) {
                    errorFinish("Missing screenshots timestamps")
                    return
                }
                if (intent.getSerializableExtra(EXTRA_TIMESTAMPS) is List<*>) {
                    screenshotsTimestamp = intent.getSerializableExtra(EXTRA_TIMESTAMPS) as List<Long>
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

        registerReceiver(broadcastReceiver, IntentFilter(ACTION_CONTINUE_BENCHMARK))
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
        if (isSetup) {
            if (::timeOut.isInitialized)
                timeoutHandler.removeCallbacks(timeOut)
            timeOut = Runnable {
                Log.e(TAG, "VLC Seek Froze")
                errorFinish("VLC Seek Froze")
            }
            timeoutHandler.postDelayed(timeOut, 10000)
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
     * trigger screenshot in org.videolan.vlcbenchmark's service
     *
     * if not end of buffering, and seeking, checks for seek timeouts.
     *
     * @param event mediaPlayer events
     */
    @TargetApi(21)
    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Vout -> hasVout = true
            MediaPlayer.Event.TimeChanged -> setTimeout()
            MediaPlayer.Event.PositionChanged -> {
                val pos = event.positionChanged
                if (!isScreenshot) {
                    when {
                        pos != position -> {
                            position = pos
                            positionCounter = 0
                        }
                        positionCounter > 50 -> errorFinish("VLC Playback Froze")
                        else -> positionCounter += 1
                    }
                }
            }
            MediaPlayer.Event.Buffering -> if (event.buffering == 100f) {
                /* initial setup that has to be done when the video
                 * has finished the first buffering */
                if (!isSetup) {
                    isSetup = true
                    if (isScreenshot) {
                        service?.pause()
                        timeoutHandler.postDelayed({ seekScreenshot() }, 1000)
                    }
                }
                /* Screenshot callback setup */
                if (isScreenshot && isSetup && screenshotCount < screenshotsTimestamp!!.size && isSeeking) {
                    isSeeking = false

                    /* Hiding navigation bar */
                    val decorView = window.decorView
                    val uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                    decorView.systemUiVisibility = uiOptions

                    /* Broadcast to trigger screenshot in VLCBenchamrk */
                    val packageName = getString(R.string.benchmark_package_name)
                    val broadcastIntent = Intent(ACTION_TRIGGER_SCREENSHOT)
                    broadcastIntent.setPackage(packageName)
                    broadcastIntent.putExtra("screenshot", screenshotCount)
                    broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    sendBroadcast(broadcastIntent)
                }
            }
        }
        super.onMediaPlayerEvent(event)
    }

    private fun continueScreenshots() {
        screenshotCount++
        if (screenshotCount < screenshotsTimestamp!!.size) {
            seekScreenshot()
        } else {
            finish()
        }
    }

    inner class ScreenshotBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                if (intent.action == ACTION_CONTINUE_BENCHMARK) {
                    continueScreenshots()
                }
            }
        }
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
        if (screenshotCount < screenshotsTimestamp!!.size) {
            setTimeout()
            seek(screenshotsTimestamp!![screenshotCount])
            isSeeking = true
        } else {
            finish()
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
            hasVLCFailed = true
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
                val outputFile = File(stacktraceFile!!)
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

        lateFrameCounter = counter
    }

    /**
     * Sets up the benchmark statistics to be returned
     * before calling super
     */
    override fun finish() {
        /* Resetting vout preference to it value before the benchmark */
        if (isHardware && oldOpenglValue != "-2") {
            val sharedPref = Settings.getInstance(this)
            AppScope.launch(Dispatchers.IO) {
                sharedPref.edit().run {
                    putString(PREFERENCE_OPENGL, oldOpenglValue)
                    putBoolean(PREFERENCE_PLAYBACK_HISTORY, oldHistoryBoolean)
                    apply()
                }
            }
            VLCInstance.restart()
        }
        /* Case of error in VideoPlayerActivity, then finish is not overridden */
        if (hasVLCFailed) {
            super.finish()
            return
        }
        if (!hasVout) {
            setResult(RESULT_NO_HW, null)
            super.finish()
        }
        val sendIntent = Intent()
        checkLogs()
        if (service != null) {
            val stats = service!!.lastStats
            sendIntent.putExtra("percent_of_bad_seek", 0.0)
            sendIntent.putExtra("number_of_dropped_frames", stats?.lostPictures ?: 100)
            sendIntent.putExtra("late_frames", lateFrameCounter)
            setResult(Activity.RESULT_OK, sendIntent)
            super.finish()
        } else {
            errorFinish("PlaybackService is null")
        }
    }

    override fun onDestroy() {
        if (::timeOut.isInitialized) {
            timeoutHandler.removeCallbacks(timeOut)
        }
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    companion object {

        private const val EXTRA_TIMESTAMPS = "extra_benchmark_timestamps"
        private const val EXTRA_ACTION_QUALITY = "extra_benchmark_action_quality"
        private const val EXTRA_ACTION_PLAYBACK = "extra_benchmark_action_playback"
        private const val EXTRA_ACTION = "extra_benchmark_action"
        private const val EXTRA_HARDWARE = "extra_benchmark_disable_hardware"
        private const val EXTRA_STACKTRACE_FILE = "stacktrace_file"

        private const val ACTION_TRIGGER_SCREENSHOT = "org.videolan.vlcbenchmark.TRIGGER_SCREENSHOT"
        private const val ACTION_CONTINUE_BENCHMARK = "org.videolan.vlc.gui.video.benchmark.CONTINUE_BENCHMARK"

        private const val TAG = "VLCBenchmark"

        private const val RESULT_FAILED = 6
        private const val RESULT_NO_HW = 1

        private const val PREFERENCE_PLAYBACK_HISTORY = "playback_history"
        private const val PREFERENCE_OPENGL = "opengl"
    }
}

