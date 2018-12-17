package org.videolan.vlc.gui.video

import android.content.res.Configuration
import android.media.AudioManager
import android.util.DisplayMetrics
import android.view.*
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ScaleGestureDetectorCompat
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.Tools
import org.videolan.vlc.R
import org.videolan.vlc.util.AndroidDevices


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

class VideoTouchDelegate(private val player: VideoPlayerActivity,
                         private val mTouchControls : Int,
                         var screenConfig : ScreenConfig,
                         private val tv : Boolean) {

    private var mTouchAction = TOUCH_NONE
    private var mInitTouchY = 0f
    private var mInitTouchX = 0f
    private var mTouchY = -1f
    private var mTouchX = -1f

    private var mLastMove: Long = 0

    private val mScaleGestureDetector by lazy(LazyThreadSafetyMode.NONE) {
        ScaleGestureDetector(player, mScaleListener).apply { ScaleGestureDetectorCompat.setQuickScaleEnabled(this, false) }
    }
    private val mDetector by lazy(LazyThreadSafetyMode.NONE) {
        GestureDetectorCompat(player, mGestureListener).apply { setOnDoubleTapListener(mGestureListener) }
    }

    // Brightness
    private var mIsFirstBrightnessGesture = true

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
                    mTouchAction = TOUCH_NONE
                }
                return true
            }
            player.isPlaylistVisible -> {
                mTouchAction = TOUCH_IGNORE
                player.togglePlaylist()
                return true
            }
            else -> {
                mScaleGestureDetector.onTouchEvent(event)
                if (mScaleGestureDetector.isInProgress || mDetector.onTouchEvent(event)) {
                    mTouchAction = TOUCH_IGNORE
                    return true
                }
                if (player.isOptionsListShowing) {
                    mTouchAction = TOUCH_IGNORE
                    player.hideOptions()
                    return true
                }
                if (mTouchControls == 0 || player.isLocked) {
                    // locked or swipe disabled, only handle show/hide & ignore all actions
                    if (event.action == MotionEvent.ACTION_UP && mTouchAction != TOUCH_IGNORE) player.toggleOverlay()
                    return false
                }

                val xChanged = if (mTouchX != -1f && mTouchY != -1f) event.rawX - mTouchX else 0f
                val yChanged = if (xChanged != 0f) event.rawY - mTouchY else 0f

                // coef is the gradient's move to determine a neutral zone
                val coef = Math.abs(yChanged / xChanged)
                val xgesturesize = xChanged / screenConfig.metrics.xdpi * 2.54f
                val deltaY = Math.max(1f, (Math.abs(mInitTouchY - event.rawY) / screenConfig.metrics.xdpi + 0.5f) * 2f)

                val xTouch = Math.round(event.rawX)
                val yTouch = Math.round(event.rawY)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Audio
                        mInitTouchY = event.rawY
                        mInitTouchX = event.rawX
                        mTouchY = mInitTouchY
                        player.initAudioVolume()
                        mTouchAction = TOUCH_NONE
                        // Seek
                        mTouchX = event.rawX
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_DOWN, xTouch, yTouch)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (mTouchAction == TOUCH_IGNORE) return false
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_MOVE, xTouch, yTouch)

                        if (player.fov == 0f) {
                            // No volume/brightness action if coef < 2 or a secondary display is connected
                            //TODO : Volume action when a secondary display is connected
                            if (mTouchAction != TOUCH_SEEK && coef > 2 && player.isOnPrimaryDisplay) {
                                if (Math.abs(yChanged / screenConfig.yRange) < 0.05) return false
                                mTouchY = event.rawY
                                mTouchX = event.rawX
                                doVerticalTouchAction(yChanged)
                            } else if (mInitTouchX < screenConfig.metrics.widthPixels * 0.95) {
                                // Seek (Right or Left move)
                                doSeekTouch(Math.round(deltaY), xgesturesize, false)
                            }
                        } else {
                            mTouchY = event.rawY
                            mTouchX = event.rawX
                            mTouchAction = TOUCH_MOVE
                            val yaw = player.fov * -xChanged / screenConfig.xRange.toFloat()
                            val pitch = player.fov * -yChanged / screenConfig.xRange.toFloat()
                            player.updateViewpoint(yaw, pitch, 0f)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (mTouchAction == TOUCH_IGNORE) mTouchAction = TOUCH_NONE
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_UP, xTouch, yTouch)
                        // Seek
                        if (mTouchAction == TOUCH_SEEK) doSeekTouch(Math.round(deltaY), xgesturesize, true)
                        mTouchX = -1f
                        mTouchY = -1f
                    }
                }
                return mTouchAction != TOUCH_NONE
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

        if (System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY) {
            if (Math.abs(x) > 0.3) {
                if (tv) {
                    player.navigateDvdMenu(if (x > 0.0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                } else
                    player.seekDelta(if (x > 0.0f) 10000 else -10000)
            } else if (Math.abs(y) > 0.3) {
                if (tv)
                    player.navigateDvdMenu(if (x > 0.0f) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
                else {
                    if (mIsFirstBrightnessGesture)
                        initBrightnessTouch()
                    player.changeBrightness(-y / 10f)
                }
            } else if (Math.abs(rz) > 0.3) {
                player.volume = player.audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                val delta = -(rz / 7 * player.audioMax).toInt()
                val vol = Math.min(Math.max(player.volume + delta, 0f), player.audioMax.toFloat()).toInt()
                player.setAudioVolume(vol)
            }
            mLastMove = System.currentTimeMillis()
        }
        return true
    }

    fun isSeeking() = mTouchAction == TOUCH_SEEK

    fun clearTouchAction() {
        mTouchAction = TOUCH_NONE
    }

    private fun doVerticalTouchAction(y_changed: Float) {
        val rightAction = mTouchX.toInt() > 4 * screenConfig.metrics.widthPixels / 7f
        val leftAction = !rightAction && mTouchX.toInt() < 3 * screenConfig.metrics.widthPixels / 7f
        if (!leftAction && !rightAction) return
        val audio = mTouchControls and TOUCH_FLAG_AUDIO_VOLUME != 0
        val brightness = mTouchControls and TOUCH_FLAG_BRIGHTNESS != 0
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
        if (Math.abs(gesturesize) < 1 || !player.mService.isSeekable) return

        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK) return
        mTouchAction = TOUCH_SEEK

        val length = player.mService.length
        val time = player.mService.time

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
        else player.showInfo(R.string.unseekable_stream, 1000)
    }

    private fun doVolumeTouch(y_changed: Float) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME) return
        val audioMax = player.audioMax
        val delta = -(y_changed / screenConfig.metrics.heightPixels.toFloat() * audioMax)
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
                    mTouchAction = TOUCH_VOLUME
                }
            } else {
                player.setAudioVolume(vol)
                mTouchAction = TOUCH_VOLUME
            }
        }
    }

    private fun initBrightnessTouch() {
        val lp = player.window.attributes
        val brightnesstemp = if (lp.screenBrightness != -1f) lp.screenBrightness else 0.6f
        lp.screenBrightness = brightnesstemp
        player.window.attributes = lp
        mIsFirstBrightnessGesture = false
    }

    private fun doBrightnessTouch(ychanged: Float) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS) return
        if (mIsFirstBrightnessGesture) initBrightnessTouch()
        mTouchAction = TOUCH_BRIGHTNESS

        // Set delta : 2f is arbitrary for now, it possibly will change in the future
        val delta = -ychanged / screenConfig.yRange

        player.changeBrightness(delta)
    }

    private val mScaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        private var savedScale : MediaPlayer.ScaleType? = null
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
                    player.setVideoScale(savedScale)
                    savedScale = null
                } else if (!grow && player.currentScaleType == MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) {
                    player.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                }
            }
        }
    }

    private val mGestureListener = object : GestureDetector.SimpleOnGestureListener() {

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            player.handler.sendEmptyMessageDelayed(if (player.isShowing) VideoPlayerActivity.HIDE_INFO else VideoPlayerActivity.SHOW_INFO, 200)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            player.handler.removeMessages(VideoPlayerActivity.HIDE_INFO)
            player.handler.removeMessages(VideoPlayerActivity.SHOW_INFO)
            val range = (if (screenConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) screenConfig.xRange else screenConfig.yRange).toFloat()
            if (!player.isLocked) {
                if (mTouchControls and TOUCH_FLAG_SEEK == 0) {
                    player.doPlayPause()
                    return true
                }
                val x = e.x
                when {
                    x < range / 4f -> player.seekDelta(-10000)
                    x > range * 0.75 -> player.seekDelta(10000)
                    else -> player.doPlayPause()
                }
                return true
            }
            return false
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float ) = if (e1.x < screenConfig.metrics.widthPixels * 0.95) false
        else try {
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) onSwipeRight()
                    else onSwipeLeft()
                    true
                } else false
            }
            else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffY > 0) onSwipeBottom()
                else onSwipeTop()
                true
            } else false
        } catch (exception: Exception) {
            exception.printStackTrace()
            false
        }

        fun onSwipeRight() {
            player.hideOptions()
        }

        fun onSwipeLeft() {
            player.showAdvancedOptions()
        }

        fun onSwipeTop() {}

        fun onSwipeBottom() {}
    }
}

data class ScreenConfig(val metrics: DisplayMetrics, val xRange: Int, val yRange: Int, val orientation: Int)