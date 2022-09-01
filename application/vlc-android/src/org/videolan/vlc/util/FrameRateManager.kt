package org.videolan.vlc.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import java.math.BigDecimal
import java.math.RoundingMode

private const val TAG = "VLC/FrameRateMatch"
private const val SHORT_VIDEO_LENGTH = 300000
class FrameRateManager(var context: Context, var service: PlaybackService) {

    init {
        // listen for display change and resume play
        // TODO: check if display change is because of frame rate mode switch
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                //switching mode may cause playback to pause i.e HDMI
                //wait 2 seconds and resume play, mode switch will have happened by then
                Handler(Looper.getMainLooper()).postDelayed({
                    val videoTrack = try {
                        this@FrameRateManager.service.mediaplayer.currentVideoTrack
                    } catch (e: IllegalStateException) {
                        null
                    }
                    if (videoTrack != null) {
                        service.play()
                    }
                }, 2000)
            }
        }

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    fun matchFrameRate(surfaceView: SurfaceView, window: Window) {
        /* automatic frame rate switching for displays/HDMI
        most media will be either 23.976, 24, 25, 29.97, 30, 48, 50, 59.94, and 60 fps */
        service.mediaplayer.currentVideoTrack?.let { videoTrack ->
            val videoFrameRate = videoTrack.frameRateNum / videoTrack.frameRateDen.toFloat()
            val surface = surfaceView.holder.surface

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> setFrameRateS(videoFrameRate, surface)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.R -> setFrameRateR(videoFrameRate, surface)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> setFrameRateM(videoFrameRate, window)
                else -> {}
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun setFrameRateR(videoFrameRate: Float, surface: Surface) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Optimal frame rate will be set by Android system")

        //Android 11 does not support Frame Rate Strategy
        surface.setFrameRate(videoFrameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setFrameRateS(videoFrameRate: Float, surface: Surface) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Optimal frame rate will be set by Android system")

        //on Android 12 and up supports Frame Rate Strategy
        //for short video less than 5 minutes, only change frame rate if seamless
        val display = context.display

        if (service.mediaplayer.length < SHORT_VIDEO_LENGTH) {
            surface.setFrameRate(videoFrameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
        } else {
            //detect if a non-seamless refresh rate switch is about to happen
            var seamless = false
            val androidDisplayManager: DisplayManager =
                    context.getSystemService(AppCompatActivity.DISPLAY_SERVICE) as DisplayManager
            display?.mode?.alternativeRefreshRates?.let { refreshRates ->
                for (rate in refreshRates) {
                    if ((videoFrameRate.toString().startsWith(rate.toString())) || (rate.toString().startsWith(videoFrameRate.toString())) || rate % videoFrameRate == 0F) {
                        seamless = true
                        break
                    }
                }
            }

            if (seamless) {
                //switch will be seamless
                surface.setFrameRate(videoFrameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ALWAYS)
            } else if (!seamless && (androidDisplayManager.matchContentFrameRateUserPreference == DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS)) {
                //switch will be non seamless, check if user has opted in for this at the OS level
                //TODO: only included this here because Android guide makes it sound like seamless-behavior includes stuff like HDMI switching
                //may have to remove this block since we intend to switch only if it will be seamless
                surface.setFrameRate(videoFrameRate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ALWAYS)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setFrameRateM(videoFrameRate: Float, window: Window) {
        val wm = context.getSystemService<WindowManager>()!!
        val display = wm.defaultDisplay
        //only change frame rate if video is longer than 5 minutes
        if (service.mediaplayer.length > SHORT_VIDEO_LENGTH) {
            //on older versions of Android use this manual frame rate switching method
            //ignore modes which don't match the current resolution, cause we want resolution to remain the same
            display.supportedModes?.let { supportedModes ->
                val currentMode = display.mode
                var modeToUse = currentMode
                for (mode in supportedModes) {
                    if ((mode.physicalHeight != currentMode.physicalHeight) || (mode.physicalWidth != currentMode.physicalWidth)) {
                        continue
                    }

                    if (BuildConfig.DEBUG) Log.d(TAG, "Supported display mode - $mode")
                    if (BigDecimal(videoFrameRate.toString()).setScale(1, RoundingMode.FLOOR) == BigDecimal(mode.refreshRate.toString()).setScale(1, RoundingMode.FLOOR)) {
                        //this is the best frame rate because it's exactly the same as source media
                        modeToUse = mode
                        break
                    } else if (mode.refreshRate % videoFrameRate == 0F) {
                        //this mode's frame rate is evenly divisible by source media frame rate, use this as second best option
                        modeToUse = mode
                        break
                    }
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "We will use ${modeToUse.refreshRate} frame rate")

                // set frame rate
                if (modeToUse != currentMode) {
                    window.attributes.preferredDisplayModeId = modeToUse.modeId
                }
            }
        }
    }
}