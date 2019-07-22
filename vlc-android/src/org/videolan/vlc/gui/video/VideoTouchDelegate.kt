package org.videolan.vlc.gui.video

import android.content.res.Configuration
import android.media.AudioManager
import android.os.Handler
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import androidx.core.view.ScaleGestureDetectorCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.Tools
import org.videolan.vlc.util.AndroidDevices
import kotlin.math.abs
import kotlin.math.roundToInt

const val TOUCH_FLAG_AUDIO_VOLUME = 1
const val TOUCH_FLAG_BRIGHTNESS = 1 shl 1
const val TOUCH_FLAG_SEEK = 1 shl 2
//Touch Events
private const val TOUCH_NONE = 0
private const val TOUCH_VOLUME = 1
private const val TOUCH_BRIGHTNESS = 2
private const val TOUCH_MOVE = 3
private const val TOUCH_SEEK = 4
private const val TOUCH_IGNORE = 5
private const val MIN_FOV = 20f
private const val MAX_FOV = 150f
//stick event
private const val JOYSTICK_INPUT_DELAY = 300

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class VideoTouchDelegate(private val player: VideoPlayerActivity,
                         private val touchControls: Int,
                         var screenConfig: ScreenConfig,
                         private val tv: Boolean) {

    var handler = Handler()

    var numberOfTaps = 0
    var lastTapTimeMs: Long = 0
    var touchDownMs: Long = 0
    private var touchAction = TOUCH_NONE
    private var initTouchY = 0f
    private var initTouchX = 0f
    private var touchY = -1f
    private var touchX = -1f
    private var verticalTouchActive = false

    private var lastMove: Long = 0

    private val scaleGestureDetector by lazy(LazyThreadSafetyMode.NONE) {
        ScaleGestureDetector(player, mScaleListener).apply { ScaleGestureDetectorCompat.setQuickScaleEnabled(this, false) }
    }

    // Brightness
    private var isFirstBrightnessGesture = true

    fun onTouchEvent(event: MotionEvent): Boolean {// Mouse events for the core
        // Seek
// Seek (Right or Left move)
        // No volume/brightness action if coef < 2 or a secondary display is connected
        //TODO : Volume action when a secondary display is connected
// Mouse events for the core
        // Audio
        // Seek
        // Mouse events for the core
        // locked or swipe disabled, only handle show/hide & ignore all actions

        // coef is the gradient's move to determine a neutral zone
        when {
            player.isPlaybackSettingActive -> {
                if (event.action == MotionEvent.ACTION_UP) {
                    player.endPlaybackSetting()
                    touchAction = TOUCH_NONE
                }
                return true
            }
            player.isPlaylistVisible -> {
                touchAction = TOUCH_IGNORE
                player.togglePlaylist()
                return true
            }
            else -> {
                if (!player.isLocked) {
                    scaleGestureDetector.onTouchEvent(event)
                    if (scaleGestureDetector.isInProgress) {
                        touchAction = TOUCH_IGNORE
                        return true
                    }
                }
                if (touchControls == 0 || player.isLocked) {
                    // locked or swipe disabled, only handle show/hide & ignore all actions
                    if (event.action == MotionEvent.ACTION_UP && touchAction != TOUCH_IGNORE) player.toggleOverlay()
                    return false
                }

                val xChanged = if (touchX != -1f && touchY != -1f) event.rawX - touchX else 0f
                val yChanged = if (touchX != -1f && touchY != -1f) event.rawY - touchY else 0f

                // coef is the gradient's move to determine a neutral zone
                val coef = Math.abs(yChanged / xChanged)
                val xgesturesize = xChanged / screenConfig.metrics.xdpi * 2.54f
                val deltaY = Math.max(1f, (Math.abs(initTouchY - event.rawY) / screenConfig.metrics.xdpi + 0.5f) * 2f)

                val xTouch = Math.round(event.rawX)
                val yTouch = Math.round(event.rawY)

                val now = System.currentTimeMillis()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownMs = now
                        verticalTouchActive = false
                        // Audio
                        initTouchY = event.rawY
                        initTouchX = event.rawX
                        touchY = initTouchY
                        player.initAudioVolume()
                        touchAction = TOUCH_NONE
                        // Seek
                        touchX = event.rawX
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_DOWN, xTouch, yTouch)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (touchAction == TOUCH_IGNORE) return false
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_MOVE, xTouch, yTouch)

                        if (player.fov == 0f) {
                            // No volume/brightness action if coef < 2 or a secondary display is connected
                            //TODO : Volume action when a secondary display is connected
                            if (touchAction != TOUCH_SEEK && coef > 2 && player.isOnPrimaryDisplay) {
                                if (!verticalTouchActive) {
                                    if (Math.abs(yChanged / screenConfig.yRange) >= 0.05) {
                                        verticalTouchActive = true
                                        touchY = event.rawY
                                        touchX = event.rawX
                                    }
                                    return false
                                }
                                touchY = event.rawY
                                touchX = event.rawX
                                doVerticalTouchAction(yChanged)
                            } else if (initTouchX < screenConfig.metrics.widthPixels * 0.95) {
                                // Seek (Right or Left move)
                                doSeekTouch(Math.round(deltaY), xgesturesize, false)
                            }
                        } else {
                            touchY = event.rawY
                            touchX = event.rawX
                            touchAction = TOUCH_MOVE
                            val yaw = player.fov * -xChanged / screenConfig.xRange.toFloat()
                            val pitch = player.fov * -yChanged / screenConfig.xRange.toFloat()
                            player.updateViewpoint(yaw, pitch, 0f)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val touchSlop = ViewConfiguration.get(player).scaledTouchSlop
                        if (touchAction == TOUCH_IGNORE) touchAction = TOUCH_NONE
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_UP, xTouch, yTouch)
                        // Seek
                        if (touchAction == TOUCH_SEEK) doSeekTouch(deltaY.roundToInt(), xgesturesize, true)
                        touchX = -1f
                        touchY = -1f

                        handler.removeCallbacksAndMessages(null)

                        if (now - touchDownMs > ViewConfiguration.getTapTimeout()) {
                            //it was not a tap
                            numberOfTaps = 0
                            lastTapTimeMs = 0
                        }

                        //verify that the touch coordinate distance did not exceed the touchslop to increment the count tap
                        if (abs(event.rawX - initTouchX) < touchSlop && abs(event.rawY - initTouchY) < touchSlop) {
                            if (numberOfTaps > 0 && now - lastTapTimeMs < ViewConfiguration.getDoubleTapTimeout()) {
                                numberOfTaps += 1
                            } else {
                                numberOfTaps = 1
                            }
                        }

                        lastTapTimeMs = now

                        //handle multi taps
                        if (numberOfTaps > 1 && !player.isLocked) {
                            if (touchControls and TOUCH_FLAG_SEEK == 0) {
                                player.doPlayPause()
                            } else {
                                val range = (if (screenConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) screenConfig.xRange else screenConfig.yRange).toFloat()

                                when {
                                    event.rawX < range / 4f -> player.seekDelta(-10000)
                                    event.rawX > range * 0.75 -> player.seekDelta(10000)
                                    else -> player.doPlayPause()
                                }
                            }
                        }

                        handler.postDelayed({
                            when (numberOfTaps) {
                                1 -> player.handler.sendEmptyMessage(if (player.isShowing) VideoPlayerActivity.HIDE_INFO else VideoPlayerActivity.SHOW_INFO)
                            }
                        }, ViewConfiguration.getDoubleTapTimeout().toLong())
                    }
                }
                return touchAction != TOUCH_NONE
            }
        }
    }

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (player.isLoading) return false
        //Check for a joystick event
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE)
            return false

        val mInputDevice = event.device

        val dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (mInputDevice == null || Math.abs(dpadx) == 1.0f || Math.abs(dpady) == 1.0f) return false

        val x = AndroidDevices.getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_X)
        val y = AndroidDevices.getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_Y)
        val rz = AndroidDevices.getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_RZ)

        if (System.currentTimeMillis() - lastMove > JOYSTICK_INPUT_DELAY) {
            if (Math.abs(x) > 0.3) {
                if (tv) {
                    player.navigateDvdMenu(if (x > 0.0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                } else
                    player.seekDelta(if (x > 0.0f) 10000 else -10000)
            } else if (Math.abs(y) > 0.3) {
                if (tv)
                    player.navigateDvdMenu(if (x > 0.0f) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
                else {
                    if (isFirstBrightnessGesture)
                        initBrightnessTouch()
                    player.changeBrightness(-y / 10f)
                }
            } else if (Math.abs(rz) > 0.3) {
                player.volume = player.audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                val delta = -(rz / 7 * player.audioMax).toInt()
                val vol = Math.min(Math.max(player.volume + delta, 0f), player.audioMax.toFloat()).toInt()
                player.setAudioVolume(vol)
            }
            lastMove = System.currentTimeMillis()
        }
        return true
    }

    fun isSeeking() = touchAction == TOUCH_SEEK

    fun clearTouchAction() {
        touchAction = TOUCH_NONE
    }

    private fun doVerticalTouchAction(y_changed: Float) {
        val rightAction = touchX.toInt() > 4 * screenConfig.metrics.widthPixels / 7f
        val leftAction = !rightAction && touchX.toInt() < 3 * screenConfig.metrics.widthPixels / 7f
        if (!leftAction && !rightAction) return
        val audio = touchControls and TOUCH_FLAG_AUDIO_VOLUME != 0
        val brightness = touchControls and TOUCH_FLAG_BRIGHTNESS != 0
        if (!audio && !brightness)
            return
        if (rightAction) {
            if (audio) doVolumeTouch(y_changed)
            else doBrightnessTouch(y_changed)
        } else {
            if (brightness) doBrightnessTouch(y_changed)
            else doVolumeTouch(y_changed)
        }
        player.hideOverlay(true)
    }

    private fun doSeekTouch(coef: Int, gesturesize: Float, seek: Boolean) {
        var coef = coef
        if (coef == 0) coef = 1
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (Math.abs(gesturesize) < 1 || !player.service!!.isSeekable) return

        if (touchAction != TOUCH_NONE && touchAction != TOUCH_SEEK) return
        touchAction = TOUCH_SEEK

        val length = player.service!!.length
        val time = player.service!!.time

        // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
        var jump = (Math.signum(gesturesize) * (600000 * Math.pow((gesturesize / 8).toDouble(), 4.0) + 3000) / coef).toInt()

        // Adjust the jump
        if (jump > 0 && time + jump > length) jump = (length - time).toInt()
        if (jump < 0 && time + jump < 0) jump = (-time).toInt()

        //Jump !
        if (seek && length > 0) player.seek(time + jump, length)

        //Show the jump's size
        if (length > 0) player.showInfo(String.format("%s%s (%s)%s",
                if (jump >= 0) "+" else "",
                Tools.millisToString(jump.toLong()),
                Tools.millisToString(time + jump),
                if (coef > 1) String.format(" x%.1g", 1.0 / coef) else ""), 50)
        else player.showInfo(org.videolan.vlc.R.string.unseekable_stream, 1000)
    }

    private fun doVolumeTouch(y_changed: Float) {
        if (touchAction != TOUCH_NONE && touchAction != TOUCH_VOLUME) return
        val audioMax = player.audioMax
        val delta = -(y_changed / screenConfig.yRange * audioMax * 1.25f)
        player.volume += delta
        val vol = Math.min(Math.max(player.volume, 0f), (audioMax * if (player.isAudioBoostEnabled) 2 else 1).toFloat()).toInt()
        if (delta < 0) player.originalVol = vol.toFloat()
        if (delta != 0f) {
            if (vol > audioMax) {
                if (player.isAudioBoostEnabled) {
                    if (player.originalVol < audioMax) {
                        player.displayWarningToast()
                        player.setAudioVolume(audioMax)
                    } else {
                        player.setAudioVolume(vol)
                    }
                    touchAction = TOUCH_VOLUME
                }
            } else {
                player.setAudioVolume(vol)
                touchAction = TOUCH_VOLUME
            }
        }
    }

    private fun initBrightnessTouch() {
        val lp = player.window.attributes

        //Check if we already have a brightness
        val brightnesstemp = if (lp.screenBrightness != -1f)
            lp.screenBrightness
        else {
            //Check if the device is in auto mode
            val contentResolver = player.applicationContext.contentResolver
            if (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                //cannot retrieve a value -> 0.5
                0.5f
            } else Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).toFloat() / 255
        }

        lp.screenBrightness = brightnesstemp
        player.window.attributes = lp
        isFirstBrightnessGesture = false
    }

    private fun doBrightnessTouch(ychanged: Float) {
        if (touchAction != TOUCH_NONE && touchAction != TOUCH_BRIGHTNESS) return
        if (isFirstBrightnessGesture) initBrightnessTouch()
        touchAction = TOUCH_BRIGHTNESS

        // Set delta : 2f is arbitrary for now, it possibly will change in the future
        val delta = -ychanged / screenConfig.yRange * 1.25f

        player.changeBrightness(delta)
    }

    private val mScaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        private var savedScale: MediaPlayer.ScaleType? = null
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return screenConfig.xRange != 0 || player.fov == 0f
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (player.fov != 0f && !player.isLocked) {
                val diff = VideoPlayerActivity.DEFAULT_FOV * (1 - detector.scaleFactor)
                if (player.updateViewpoint(0f, 0f, diff)) {
                    player.fov = Math.min(Math.max(MIN_FOV, player.fov + diff), MAX_FOV)
                    return true
                }
            }
            return false
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (player.fov == 0f && !player.isLocked) {
                val grow = detector.scaleFactor > 1.0f
                if (grow && player.currentScaleType != MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) {
                    savedScale = player.currentScaleType
                    player.setVideoScale(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN)
                } else if (!grow && savedScale != null) {
                    player.setVideoScale(savedScale!!)
                    savedScale = null
                } else if (!grow && player.currentScaleType == MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) {
                    player.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                }
            }
        }
    }
}

data class ScreenConfig(val metrics: DisplayMetrics, val xRange: Int, val yRange: Int, val orientation: Int)