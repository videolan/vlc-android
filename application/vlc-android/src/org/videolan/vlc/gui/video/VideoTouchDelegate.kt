package org.videolan.vlc.gui.video

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ScaleGestureDetectorCompat
import com.google.android.material.circularreveal.CircularRevealCompat
import com.google.android.material.circularreveal.CircularRevealFrameLayout
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.Tools
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AndroidDevices.isTv
import org.videolan.tools.setVisible
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.view.HalfCircleView
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

const val TOUCH_FLAG_AUDIO_VOLUME = 1
const val TOUCH_FLAG_BRIGHTNESS = 1 shl 1
const val TOUCH_FLAG_DOUBLE_TAP_SEEK = 1 shl 2
const val TOUCH_FLAG_PLAY = 1 shl 3
const val TOUCH_FLAG_SWIPE_SEEK = 1 shl 4
const val TOUCH_FLAG_SCREENSHOT = 1 shl 5
const val TOUCH_FLAG_SCALE = 1 shl 6
//Touch Events
private const val TOUCH_NONE = 0
private const val TOUCH_VOLUME = 1
private const val TOUCH_BRIGHTNESS = 2
private const val TOUCH_MOVE = 3
private const val TOUCH_TAP_SEEK = 4
private const val TOUCH_IGNORE = 5
private const val TOUCH_SCREENSHOT = 6

private const val MIN_FOV = 20f
private const val MAX_FOV = 150f
//stick event
private const val JOYSTICK_INPUT_DELAY = 300

class VideoTouchDelegate(private val player: VideoPlayerActivity,
                         var touchControls: Int,
                         var screenConfig: ScreenConfig,
                         private val tv: Boolean) {

    private val resizeDelegate: VideoPlayerResizeDelegate
        get() = player.resizeDelegate
    var handler = Handler(Looper.getMainLooper())

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

    //Seek
    private var nbTimesTaped = 0
    private var lastSeekWasForward = true
    private var seekAnimRunning = false
    private var animatorSet: AnimatorSet = AnimatorSet()
    private val rightContainer: CircularRevealFrameLayout by lazy { player.findViewById(R.id.rightContainer) }
    private val leftContainer: CircularRevealFrameLayout by lazy { player.findViewById(R.id.leftContainer) }
    private val rightContainerBackground: HalfCircleView by lazy { player.findViewById(R.id.rightContainerBackground) }
    private val leftContainerBackground: HalfCircleView by lazy { player.findViewById(R.id.leftContainerBackground) }
    private val seekRightText: TextView by lazy { player.findViewById(R.id.seekRightText) }
    private val seekLeftText: TextView by lazy { player.findViewById(R.id.seekLeftText) }
    private val seekRewindFirst: ImageView by lazy { player.findViewById(R.id.seekRewindFirst) }
    private val seekForwardFirst: ImageView by lazy { player.findViewById(R.id.seekForwardFirst) }
    private val seekForwardSecond: ImageView by lazy { player.findViewById(R.id.seekForwardSecond) }
    private val seekRewindSecond: ImageView by lazy { player.findViewById(R.id.seekRewindSecond) }
    private val seekContainer: ConstraintLayout by lazy { player.findViewById(R.id.seekContainer) }
    private val seekBackground: FrameLayout by lazy { player.findViewById(R.id.seek_background) }

    companion object {
        private const val TAG = "VLC/VideoTouchDelegate"
        private const val SEEK_TIMEOUT = 750L
    }

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
            player.isPlaylistVisible -> {
                touchAction = TOUCH_IGNORE
                player.overlayDelegate.togglePlaylist()
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
                // fov == 0f -> not a 360 video
                if ((touchControls == 0  && player.fov == 0f) || player.isLocked) {
                    // locked or swipe disabled, only handle show/hide & ignore all actions
                    if (event.action == MotionEvent.ACTION_UP && touchAction != TOUCH_IGNORE) player.overlayDelegate.toggleOverlay()
                    return false
                }

                val xChanged = if (touchX != -1f && touchY != -1f) event.x - touchX else 0f
                val yChanged = if (touchX != -1f && touchY != -1f) event.y - touchY else 0f

                // coef is the gradient's move to determine a neutral zone
                val coef = abs(yChanged / xChanged)
                val xgesturesize = xChanged / screenConfig.metrics.xdpi * 2.54f
                val deltaY = ((abs(initTouchY - event.y) / screenConfig.metrics.xdpi + 0.5f) * 2f).coerceAtLeast(1f)

                val (xTouch, yTouch) = try {
                    Pair(event.x.roundToInt(), event.y.roundToInt())
                } catch (e: IllegalArgumentException) {
                    return false
                }

                val now = System.currentTimeMillis()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownMs = now
                        verticalTouchActive = false
                        // Audio
                        initTouchY = event.y
                        initTouchX = event.x
                        touchY = initTouchY
                        player.initAudioVolume()
                        touchAction = TOUCH_NONE
                        // Seek
                        touchX = event.x
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_DOWN, xTouch, yTouch)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if ((touchControls and TOUCH_FLAG_SCREENSHOT == TOUCH_FLAG_SCREENSHOT) && event.pointerCount == 3) touchAction = TOUCH_SCREENSHOT
                        if (touchAction == TOUCH_IGNORE) return false
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_MOVE, xTouch, yTouch)

                        if (player.fov == 0f) {
                            // No volume/brightness action if coef < 2 or a secondary display is connected
                            //TODO : Volume action when a secondary display is connected
                            if (touchAction != TOUCH_TAP_SEEK && coef > 2 && player.isOnPrimaryDisplay) {
                                if (!verticalTouchActive) {
                                    if (abs(yChanged / screenConfig.yRange) >= 0.05) {
                                        verticalTouchActive = true
                                        touchY = event.y
                                        touchX = event.x
                                    }
                                    return false
                                }
                                touchY = event.y
                                touchX = event.x
                                doVerticalTouchAction(yChanged)
                            } else if (initTouchX < screenConfig.metrics.widthPixels * 0.95) {
                                // Seek (Right or Left move)
                                doSeekTouch(deltaY.roundToInt(), xgesturesize, false)
                            }
                        } else {
                            touchY = event.y
                            touchX = event.x
                            touchAction = TOUCH_MOVE
                            val yaw = player.fov * -xChanged / screenConfig.xRange.toFloat()
                            val pitch = player.fov * -yChanged / screenConfig.xRange.toFloat()
                            player.updateViewpoint(yaw, pitch, 0f)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if ((touchControls and TOUCH_FLAG_SCREENSHOT == TOUCH_FLAG_SCREENSHOT) && touchAction == TOUCH_SCREENSHOT) {
                            player.takeScreenshot()
                            return true
                        }
                        val touchSlop = ViewConfiguration.get(player).scaledTouchSlop
                        if (touchAction == TOUCH_IGNORE) touchAction = TOUCH_NONE
                        // Mouse events for the core
                        player.sendMouseEvent(MotionEvent.ACTION_UP, xTouch, yTouch)
                        touchX = -1f
                        touchY = -1f
                        // Seek
                        if (touchAction == TOUCH_TAP_SEEK) {
                            doSeekTouch(deltaY.roundToInt(), xgesturesize, true)
                            return true
                        }
                        // Vertical actions
                        if (touchAction == TOUCH_VOLUME || touchAction == TOUCH_BRIGHTNESS) {
                            doVerticalTouchAction(yChanged)
                            return true
                        }

                        handler.removeCallbacksAndMessages(null)

                        if (now - touchDownMs > ViewConfiguration.getDoubleTapTimeout()) {
                            //it was not a tap
                            numberOfTaps = 0
                            lastTapTimeMs = 0
                        }

                        //verify that the touch coordinate distance did not exceed the touchslop to increment the count tap
                        if (abs(event.x - initTouchX) < touchSlop && abs(event.y - initTouchY) < touchSlop) {
                            if (numberOfTaps > 0 && now - lastTapTimeMs < ViewConfiguration.getDoubleTapTimeout()) {
                                numberOfTaps += 1
                            } else {
                                numberOfTaps = 1
                            }
                        }

                        lastTapTimeMs = now

                        //handle multi taps
                        if (numberOfTaps > 1 && !player.isLocked) {
                            val range = (if (screenConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) screenConfig.xRange else screenConfig.yRange).toFloat()
                            when {
                                (touchControls and TOUCH_FLAG_DOUBLE_TAP_SEEK != 0) && event.x < range / 4f -> seekDelta(-org.videolan.tools.Settings.videoDoubleTapJumpDelay * 1000)
                                (touchControls and TOUCH_FLAG_DOUBLE_TAP_SEEK != 0) && event.x > range * 0.75 -> seekDelta(org.videolan.tools.Settings.videoDoubleTapJumpDelay * 1000)
                                else -> if (touchControls and TOUCH_FLAG_PLAY != 0) player.doPlayPause()
                            }
                        }

                        val showHideUIRunnable = Runnable {
                            when (numberOfTaps) {
                                1 -> player.handler.sendEmptyMessage(if (player.isShowing) VideoPlayerActivity.HIDE_INFO else VideoPlayerActivity.SHOW_INFO)
                            }
                        }

                        if (touchControls and (TOUCH_FLAG_PLAY or TOUCH_FLAG_DOUBLE_TAP_SEEK) != 0) {
                            handler.postDelayed(showHideUIRunnable, ViewConfiguration.getDoubleTapTimeout().toLong())
                        } else {
                            showHideUIRunnable.run()
                        }
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

        val inputDevice = event.device

        val dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (inputDevice == null || abs(dpadx) == 1.0f || abs(dpady) == 1.0f) return false

        val x = AndroidDevices.getCenteredAxis(event, inputDevice, MotionEvent.AXIS_X)
        val y = AndroidDevices.getCenteredAxis(event, inputDevice, MotionEvent.AXIS_Y)
        val rz = AndroidDevices.getCenteredAxis(event, inputDevice, MotionEvent.AXIS_RZ)

        if (System.currentTimeMillis() - lastMove > JOYSTICK_INPUT_DELAY) {
            if (abs(x) > 0.3) {
                if (tv) {
                    player.navigateDvdMenu(if (x > 0.0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                } else
                    seekDelta(if (x > 0.0f) 10000 else -10000)
            } else if (abs(y) > 0.3) {
                if (tv)
                    player.navigateDvdMenu(if (x > 0.0f) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
                else {
                    if (isFirstBrightnessGesture)
                        initBrightnessTouch()
                    player.changeBrightness(-y / 10f)
                }
            } else if (abs(rz) > 0.3) {
                player.volume = player.audiomanager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                val delta = -(rz / 7 * player.audioMax).toInt()
                val vol = (player.volume.toInt() + delta).coerceIn(0, player.audioMax)
                player.setAudioVolume(vol)
            }
            lastMove = System.currentTimeMillis()
        }
        return true
    }

    fun isSeeking() = touchAction == TOUCH_TAP_SEEK

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
        player.overlayDelegate.hideOverlay(true)
    }

    private fun doSeekTouch(coef: Int, gesturesize: Float, seek: Boolean) {
        if (touchControls and TOUCH_FLAG_SWIPE_SEEK != 0) {
            var realCoef = coef
            if (realCoef == 0) realCoef = 1
            // No seek action if coef > 0.5 and gesturesize < 1cm
            if (abs(gesturesize) < 1 || !player.service!!.isSeekable) return

            if (touchAction != TOUCH_NONE && touchAction != TOUCH_TAP_SEEK) return
            touchAction = TOUCH_TAP_SEEK

            val length = player.service!!.length
            val time = player.service!!.getTime()

            // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
            var jump = (sign(gesturesize) * (600000 * (gesturesize / 8).toDouble()
                .pow(4.0) + 3000) / realCoef).toInt()

            // Adjust the jump
            if (jump > 0 && time + jump > length) jump = (length - time).toInt()
            if (jump < 0 && time + jump < 0) jump = (-time).toInt()

            //Jump !
            if (seek && length > 0) player.seek(time + jump, length)

            //Show the jump's size
            if (length > 0) player.overlayDelegate.showInfo(
                String.format(
                    "%s%s (%s)%s",
                    if (jump >= 0) "+" else "",
                    Tools.millisToString(jump.toLong()),
                    Tools.millisToString(time + jump),
                    if (realCoef > 1) String.format(" x%.1g", 1.0 / realCoef) else ""
                ), 50
            )
            else player.overlayDelegate.showInfo(R.string.unseekable_stream, 1000)
        }
    }

    private fun doVolumeTouch(y_changed: Float) {
        if (touchAction != TOUCH_NONE && touchAction != TOUCH_VOLUME) return
        val audioMax = player.audioMax
        val delta = -(y_changed / screenConfig.yRange * audioMax * 1.25f)
        player.volume += delta
        val vol = player.volume.toInt().coerceIn(0, audioMax * if (player.isAudioBoostEnabled) 2 else 1)
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
            if (touchControls and TOUCH_FLAG_SCALE != TOUCH_FLAG_SCALE) return false
            return screenConfig.xRange != 0 || player.fov == 0f
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (player.fov != 0f && !player.isLocked && (touchControls and TOUCH_FLAG_SCALE == TOUCH_FLAG_SCALE)) {
                val diff = VideoPlayerActivity.DEFAULT_FOV * (1 - detector.scaleFactor)
                if (player.updateViewpoint(0f, 0f, diff)) {
                    player.fov = (player.fov + diff).coerceIn(MIN_FOV, MAX_FOV)
                    return true
                }
            }
            return false
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (player.fov == 0f && !player.isLocked && (touchControls and TOUCH_FLAG_SCALE == TOUCH_FLAG_SCALE)) {
                val grow = detector.scaleFactor > 1.0f
                if (grow && player.currentScaleType != MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) {
                    savedScale = player.currentScaleType
                    resizeDelegate.setVideoScale(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN)
                } else if (!grow && savedScale != null) {
                    resizeDelegate.setVideoScale(savedScale!!)
                    savedScale = null
                } else if (!grow && player.currentScaleType == MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) {
                    resizeDelegate.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                }
                touchAction = TOUCH_NONE
            }
        }
    }

    //Seek

    internal fun seekDelta(delta: Int) {
        player.service?.let { service ->
            // unseekable stream
            if (service.length <= 0 || !service.isSeekable) return

            var position = player.time + delta
            if (position < 0) position = 0
            if (position > service.length) position = service.length
            player.seek(position)
            val sb = StringBuilder()
            val seekForward = delta >= 0

            initSeekOverlay()
            if (lastSeekWasForward != seekForward) {
                animatorSet.cancel()
                hideSeekOverlay(true)
            }

            if (nbTimesTaped != 0 && lastSeekWasForward != seekForward) {
                nbTimesTaped = 0
            }

            nbTimesTaped++

            lastSeekWasForward = seekForward
            if (service.getTime() > 0 && service.getTime() < service.length) sb.append(if (nbTimesTaped == -1) (delta / 1000f).toInt() else (nbTimesTaped * (delta / 1000f).toInt()))
                .append("s ")
            sb.append("(").append(Tools.millisToString(service.getTime()))
                .append(')')

            val container = if (seekForward) rightContainer else leftContainer
            val containerBackground = if (seekForward) rightContainerBackground else leftContainerBackground
            val textView = if (seekForward) seekRightText else seekLeftText
            val imageFirst = if (seekForward) seekForwardFirst else seekRewindFirst
            val imageSecond = if (seekForward) seekForwardSecond else seekRewindSecond

            container.post {

                //On TV, seek text and animation should be centered in parent
                if (isTv) {
                    val seekTVConstraintSet = ConstraintSet()
                    seekTVConstraintSet.clone(seekContainer)

                    seekTVConstraintSet.connect(R.id.rightContainerBackground, ConstraintSet.START, R.id.seekRightContainer, ConstraintSet.START)
                    seekTVConstraintSet.connect(R.id.rightContainerBackground, ConstraintSet.TOP, R.id.seekRightContainer, ConstraintSet.TOP)
                    seekTVConstraintSet.connect(R.id.rightContainerBackground, ConstraintSet.BOTTOM, R.id.seekRightContainer, ConstraintSet.BOTTOM)
                    seekTVConstraintSet.setMargin(R.id.seekRightText, ConstraintSet.END, player.resources.getDimensionPixelSize(R.dimen.tv_overscan_horizontal))

                    seekTVConstraintSet.connect(R.id.leftContainerBackground, ConstraintSet.END, R.id.seekLeftContainer, ConstraintSet.END)
                    seekTVConstraintSet.connect(R.id.leftContainerBackground, ConstraintSet.TOP, R.id.seekLeftContainer, ConstraintSet.TOP)
                    seekTVConstraintSet.connect(R.id.leftContainerBackground, ConstraintSet.BOTTOM, R.id.seekLeftContainer, ConstraintSet.BOTTOM)
                    seekTVConstraintSet.setMargin(R.id.seekLeftText, ConstraintSet.START, player.resources.getDimensionPixelSize(R.dimen.tv_overscan_horizontal))
                    seekForwardFirst.setImageResource(R.drawable.ic_half_seek_forward_tv)
                    seekForwardSecond.setImageResource(R.drawable.ic_half_seek_forward_tv)
                    seekRewindFirst.setImageResource(R.drawable.ic_half_seek_rewind_tv)
                    seekRewindSecond.setImageResource(R.drawable.ic_half_seek_rewind_tv)

                    seekRightText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    seekLeftText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    seekTVConstraintSet.applyTo(seekContainer)
                }

                val backgroundAnim = ObjectAnimator.ofFloat(seekBackground, "alpha", 1f)
                backgroundAnim.duration = 200

                val firstImageAnim = ObjectAnimator.ofFloat(imageFirst, "alpha", 1f, 0f)
                firstImageAnim.duration = 500

                val secondImageAnim = ObjectAnimator.ofFloat(imageSecond, "alpha", 0F, 1f, 0f)
                secondImageAnim.duration = 750

                //the center is offset + the radius is 2 * the width to reveal an arc instead of half a circle
                val cx = if (seekForward) container.width * 2 else -container.width
                val cy = container.height / 2
                animatorSet = AnimatorSet()
                val backgroundColorAnimator = CircularRevealCompat.createCircularReveal(container, cx.toFloat(), cy.toFloat(), 0F, container.width.toFloat() * 2)
                backgroundColorAnimator.duration = 750

                val containerBackgroundAnim = ObjectAnimator.ofFloat(containerBackground, "alpha", 0f, 1f)
                containerBackgroundAnim.duration = 300

                val textAnim = ObjectAnimator.ofFloat(textView, "alpha", 0f, 1f)
                textAnim.duration = 300

                val anims: ArrayList<Animator> = arrayListOf(firstImageAnim, secondImageAnim)
                if (!isTv) {
                    anims.add(backgroundColorAnimator)
                }
                if (!seekAnimRunning) {
                    anims.add(containerBackgroundAnim)
                }
                if (!seekAnimRunning) {
                    anims.add(textAnim)
                }

                seekAnimRunning = true

                seekRightText.animate().cancel()
                seekLeftText.animate().cancel()
                rightContainerBackground.animate().cancel()
                leftContainerBackground.animate().cancel()

                animatorSet.playTogether(anims)

                val mainAnimOut = ObjectAnimator.ofFloat(seekBackground, "alpha", 0f)
                backgroundAnim.duration = 200

                val seekAnimatorSet = AnimatorSet()
                seekAnimatorSet.playSequentially(animatorSet, mainAnimOut)


                player.handler.removeMessages(VideoPlayerActivity.HIDE_SEEK)
                player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.HIDE_SEEK, SEEK_TIMEOUT)

                if (!isTv) {
                    container.visibility = View.VISIBLE
                }
                seekAnimatorSet.start()
            }
            textView.text = sb.toString()
        }
    }

    fun hideSeekOverlay(immediate: Boolean = false) {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "hideSeekOverlay $immediate")
        seekAnimRunning = false
        rightContainer.visibility = View.INVISIBLE
        leftContainer.visibility = View.INVISIBLE
        if (immediate) {
            seekRightText.animate().cancel()
            seekLeftText.animate().cancel()
            rightContainerBackground.animate().cancel()
            leftContainerBackground.animate().cancel()
            seekRightText.alpha = 0f
            seekLeftText.alpha = 0f
            rightContainerBackground.alpha = 0f
            leftContainerBackground.alpha = 0f
        } else {
            seekRightText.animate().alpha(0f).withEndAction { seekRightText.text = "" }
            seekLeftText.animate().alpha(0f).withEndAction { seekLeftText.text = "" }
            rightContainerBackground.animate().alpha(0f)
            leftContainerBackground.animate().alpha(0f)
        }
        nbTimesTaped = 0
        seekForwardFirst.alpha = 0f
        seekForwardSecond.alpha = 0f
        seekRewindFirst.alpha = 0f
        seekRewindSecond.alpha = 0f
    }

    private fun initSeekOverlay() = player.findViewById<ViewStubCompat>(R.id.player_seek_stub)?.setVisible()

}

data class ScreenConfig(val metrics: DisplayMetrics, val xRange: Int, val yRange: Int, val orientation: Int)