/*
 * ************************************************************************
 *  VideoTipsDelegate.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.video

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.View.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.transition.Fade
import androidx.transition.TransitionManager
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.TipsUtils
import org.videolan.vlc.gui.view.PlayerProgress

/**
 * Delegate to manage the video tips workflow.
 */
class VideoTipsDelegate(private val player: VideoPlayerActivity) : OnClickListener {

    var currentTip: VideoPlayerTipsStep? = null
    var currentControl: Int? = null
    private lateinit var initialConstraintSet: ConstraintSet
    private val transition = Fade().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }
    private val currentAnimations = ArrayList<Animator>()
    private lateinit var tipsBrightnessProgress: PlayerProgress
    private lateinit var tipsVolumeProgress: PlayerProgress
    private lateinit var overlayTipsLayout: ConstraintLayout
    private lateinit var tapIndicatorTracks: View
    private lateinit var tapIndicatorOrientation: View
    private lateinit var tapIndicatorPlay: View
    private lateinit var tapIndicatorRatio: View
    private lateinit var tapIndicatorAdvanced: View
    private lateinit var tapGesture: View
    private lateinit var nextButton: Button
    private lateinit var tipsBrightnessText: TextView
    private lateinit var tipsVolumeText: TextView
    private lateinit var doubleTapCenter: View
    private lateinit var doubleTapLeft: View
    private lateinit var doubleTapRight: View
    private lateinit var seekRewindFirst: ImageView
    private lateinit var seekRewindSecond: ImageView
    private lateinit var seekForwardFirst: ImageView
    private lateinit var seekForwardSecond: ImageView
    private lateinit var tapGestureHorizontal: View
    private lateinit var helpTitle: TextView
    private lateinit var helpDescription: TextView

    /**
     * Init the tips:
     * - Inflate the views
     * - Initialize some views
     * - Start the tips
     */
    fun init() {
        (player.findViewById<View>(R.id.player_overlay_tips) as? ViewStubCompat)?.inflate()

        tipsBrightnessProgress = player.findViewById(R.id.tipsBrightnessProgress)
        tipsVolumeProgress = player.findViewById(R.id.tipsVolumeProgress)
        overlayTipsLayout = player.findViewById(R.id.overlayTipsLayout)
        tapIndicatorTracks = player.findViewById(R.id.tapIndicatorTracks)
        tapIndicatorOrientation = player.findViewById(R.id.tapIndicatorOrientation)
        tapIndicatorPlay = player.findViewById(R.id.tapIndicatorPlay)
        tapIndicatorRatio = player.findViewById(R.id.tapIndicatorRatio)
        tapIndicatorAdvanced = player.findViewById(R.id.tapIndicatorAdvanced)
        tapGesture = player.findViewById(R.id.tapGesture)
        nextButton = player.findViewById(R.id.nextButton)
        tipsBrightnessText = player.findViewById(R.id.tipsBrightnessText)
        tipsVolumeText = player.findViewById(R.id.tipsVolumeText)
        doubleTapCenter = player.findViewById(R.id.doubleTapCenter)
        doubleTapLeft = player.findViewById(R.id.doubleTapLeft)
        doubleTapRight = player.findViewById(R.id.doubleTapRight)
        seekRewindFirst = player.findViewById(R.id.seekRewindFirst)
        seekRewindSecond = player.findViewById(R.id.seekRewindSecond)
        seekForwardFirst = player.findViewById(R.id.seekForwardFirst)
        seekForwardSecond = player.findViewById(R.id.seekForwardSecond)
        tapGestureHorizontal = player.findViewById(R.id.tapGestureHorizontal)
        helpTitle = player.findViewById(R.id.helpTitle)
        helpDescription = player.findViewById(R.id.helpDescription)

        tipsBrightnessProgress.setValue(50)
        tipsVolumeProgress.setValue(50)
        if (!::initialConstraintSet.isInitialized) {
            initialConstraintSet = ConstraintSet()
            initialConstraintSet.clone(overlayTipsLayout)
        }
        overlayTipsLayout.setVisible()
        next()
        getTapIndicators().forEach { it.setOnClickListener(this) }
    }

    /**
     * Get all the tap indicators
     * @return the list of all the tap indicators
     */
    private fun getTapIndicators() = arrayOf(
        tapIndicatorTracks,
        tapIndicatorOrientation,
        tapIndicatorPlay,
        tapIndicatorRatio,
        tapIndicatorAdvanced
    )

    /**
     * Get a double tap anim, with a seek animation if needed
     * @param view: the view to animate
     * @param repeat: should the animation take care of the loop. If set to false, the animation won't loop
     * @param firstSeek: if a seek animation has to be added, the first view to animate
     * @param secondSeek: if a seek animation has to be added, the second view to animate
     * @return the [AnimatorSet]
     */
    private fun doubleTap(view: View, repeat: Boolean = true, firstSeek: View? = null, secondSeek: View? = null): AnimatorSet {

        view.clearAnimation()
        view.scaleX = 1F
        view.scaleY = 1F
        view.alpha = 0F
        val objectAnimator1 = ObjectAnimator.ofFloat(view, SCALE_X, 1F, 0.9F, 1F, 0.9F, 1F, 1F)
        val objectAnimator2 = ObjectAnimator.ofFloat(view, SCALE_Y, 1F, 0.9F, 1F, 0.9F, 1F, 1F)
        val objectAnimator3 = ObjectAnimator.ofFloat(view, ALPHA, 0F, 1F, 1F, 1F, 0F, 0F)
        objectAnimator1.startDelay = 500
        objectAnimator2.startDelay = 500
        objectAnimator3.startDelay = 500
        objectAnimator1.duration = 1600
        objectAnimator2.duration = 1600
        objectAnimator3.duration = 1600

        val anims: MutableList<Animator> = arrayListOf(objectAnimator1, objectAnimator2, objectAnimator3)

        firstSeek?.let {
            val firstImageAnim = ObjectAnimator.ofFloat(it, ALPHA, 0F, 1f, 0f)
            firstImageAnim.duration = 500
            firstImageAnim.startDelay = 1000
            anims.add(firstImageAnim)
        }

        secondSeek?.let {
            val secondImageAnim = ObjectAnimator.ofFloat(it, ALPHA, 0F, 1f, 0f)
            secondImageAnim.duration = 750
            secondImageAnim.startDelay = 1000
            anims.add(secondImageAnim)
        }

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(anims)


        if (repeat) {
            animatorSet.doOnEnd { animatorSet.start() }
            animatorSet.start()
        }

        return animatorSet
    }

    /**
     * Start and get the swipe animation. Animates the [PlayerProgress] and updates the [TextView] as well
     * @param progress: the progress view to animate during the swipe
     * @param textView: the textview the update during the swipe
     * @return the generated [ObjectAnimator]
     */
    private fun swipe(progress: PlayerProgress, textView: TextView): ObjectAnimator {

        tapGesture.translationY = 0F
        val slideAnimator = ObjectAnimator.ofFloat(tapGesture, TRANSLATION_Y, 0F)
        slideAnimator.duration = 1600L
        slideAnimator.setFloatValues(0F, 30F, -30F, 0F)
        slideAnimator.interpolator = LinearInterpolator()

        slideAnimator.startDelay = 1000L
        slideAnimator.addUpdateListener {
            val value = 50 - ((it.animatedValue as Float) * 2 / 3).toInt()
            progress.setValue(value)
            textView.text = "$value%"
        }
        slideAnimator.doOnEnd { slideAnimator.start()  }
        slideAnimator.start()
        return slideAnimator
    }

    /**
     * Load the next tip screen depending on the currentTip
     */
    fun next() {
        if (currentTip == VideoPlayerTipsStep.SEEK) {
            close()
            return
        }
        currentTip = currentTip?.next() ?: VideoPlayerTipsStep.CONTROLS

        val constraintSet = ConstraintSet().apply { clone(initialConstraintSet) }
        TransitionManager.beginDelayedTransition(overlayTipsLayout, transition)

        getTapIndicators().forEach { constraintSet.setVisibility(it.id, GONE) }

        clearAllAnimations()
        nextButton.setText(R.string.next_step)

        when (currentTip) {
            VideoPlayerTipsStep.CONTROLS -> {
                getTapIndicators().forEach { constraintSet.setVisibility(it.id, VISIBLE) }
                TipsUtils.startTapAnimation(getTapIndicators().toList())
            }
            VideoPlayerTipsStep.BRIGHTNESS -> {
                constraintSet.connect(R.id.helpTitle, ConstraintSet.START, R.id.tap_gesture_background, ConstraintSet.END, 16.dp)
                constraintSet.connect(R.id.helpTitle, ConstraintSet.END, R.id.tipsBrightnessProgress, ConstraintSet.START, 16.dp)
                constraintSet.setHorizontalBias(R.id.helpTitle, 1F)
                constraintSet.connect(R.id.helpDescription, ConstraintSet.START, R.id.tap_gesture_background, ConstraintSet.END, 16.dp)
                constraintSet.connect(R.id.helpDescription, ConstraintSet.END, R.id.tipsBrightnessProgress, ConstraintSet.START, 16.dp)
                constraintSet.setHorizontalBias(R.id.helpDescription, 1F)

                constraintSet.setVisibility(R.id.tapGesture, VISIBLE)
                constraintSet.setVisibility(R.id.tap_gesture_background, VISIBLE)
                constraintSet.setVisibility(R.id.tipsBrightnessText, VISIBLE)
                constraintSet.setVisibility(R.id.tips_brightness_icon, VISIBLE)
                constraintSet.setVisibility(R.id.tipsBrightnessProgress, VISIBLE)
                currentAnimations.clear()
                currentAnimations.add(swipe(tipsBrightnessProgress, tipsBrightnessText))
            }
            VideoPlayerTipsStep.VOLUME -> {
                constraintSet.clear(R.id.tap_gesture_background, ConstraintSet.START)
                constraintSet.connect(R.id.tap_gesture_background, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 32.dp)

                constraintSet.connect(R.id.helpTitle, ConstraintSet.START, R.id.tipsVolumeProgress, ConstraintSet.END, 16.dp)
                constraintSet.connect(R.id.helpTitle, ConstraintSet.END, R.id.tap_gesture_background, ConstraintSet.START, 16.dp)
                constraintSet.setHorizontalBias(R.id.helpTitle, 0F)
                constraintSet.connect(R.id.helpDescription, ConstraintSet.START, R.id.tipsVolumeProgress, ConstraintSet.END, 16.dp)
                constraintSet.connect(R.id.helpDescription, ConstraintSet.END, R.id.tap_gesture_background, ConstraintSet.START, 16.dp)
                constraintSet.setHorizontalBias(R.id.helpDescription, 0F)

                constraintSet.setVisibility(R.id.tapGesture, VISIBLE)
                constraintSet.setVisibility(R.id.tap_gesture_background, VISIBLE)
                constraintSet.setVisibility(R.id.tipsVolumeText, VISIBLE)
                constraintSet.setVisibility(R.id.tips_volume_icon, VISIBLE)
                constraintSet.setVisibility(R.id.tipsVolumeProgress, VISIBLE)
                currentAnimations.clear()
                currentAnimations.add(swipe(tipsVolumeProgress, tipsVolumeText))
            }
            VideoPlayerTipsStep.PAUSE -> {

                constraintSet.setVisibility(R.id.doubleTapCenter, VISIBLE)
                currentAnimations.clear()
                currentAnimations.add(doubleTap(doubleTapCenter))
            }
            VideoPlayerTipsStep.SEEK_TAP -> {

                constraintSet.setVisibility(R.id.doubleTapLeft, VISIBLE)
                constraintSet.setVisibility(R.id.doubleTapRight, VISIBLE)
                constraintSet.setVisibility(R.id.seekRewindFirst, VISIBLE)
                constraintSet.setVisibility(R.id.seekRewindSecond, VISIBLE)
                constraintSet.setVisibility(R.id.seekForwardFirst, VISIBLE)
                constraintSet.setVisibility(R.id.seekForwardSecond, VISIBLE)
                currentAnimations.clear()
                val tapLeft = doubleTap(doubleTapLeft, false, seekRewindFirst, seekRewindSecond)
                val tapRight = doubleTap(doubleTapRight, false, seekForwardFirst, seekForwardSecond)

                tapLeft.doOnEnd { tapRight.start() }
                tapRight.doOnEnd { tapLeft.start() }
                currentAnimations.add(tapLeft)
                currentAnimations.add(tapRight)
                tapLeft.start()
            }
            VideoPlayerTipsStep.SEEK -> {
                constraintSet.connect(R.id.tapGesture, ConstraintSet.END, R.id.tap_gesture_horizontal_background, ConstraintSet.END)
                constraintSet.connect(R.id.tapGesture, ConstraintSet.START, R.id.tap_gesture_horizontal_background, ConstraintSet.START)
                constraintSet.connect(R.id.tapGesture, ConstraintSet.TOP, R.id.tap_gesture_horizontal_background, ConstraintSet.TOP)
                constraintSet.connect(R.id.tapGesture, ConstraintSet.BOTTOM, R.id.tap_gesture_horizontal_background, ConstraintSet.BOTTOM)

                constraintSet.setVisibility(R.id.tap_gesture_horizontal_background, VISIBLE)
                constraintSet.setVisibility(R.id.tapGestureHorizontal, VISIBLE)
                currentAnimations.clear()
                currentAnimations.add(TipsUtils.horizontalSwipe(tapGestureHorizontal))
                nextButton.setText(R.string.close)
            }
            else -> {}
        }

        constraintSet.applyTo(overlayTipsLayout)
        helpTitle.setText(currentTip!!.titleText)
        helpDescription.setText(currentTip!!.descriptionText)
    }

    /**
     * Clear all the launched animations
     */
    private fun clearAllAnimations() {
        getTapIndicators().forEach { it.animate().cancel() }
        tapGesture.clearAnimation()
        tapGestureHorizontal.clearAnimation()
        currentAnimations.forEach {
            it.cancel()
            it.removeAllListeners()
        }
    }

    /**
     * Close the tips, cancel all the animations, relaunch the playback
     */
    fun close() {
        clearAllAnimations()
        overlayTipsLayout.setGone()
        Settings.getInstance(player).putSingle(PREF_TIPS_SHOWN, true)
        currentTip = null
        player.play()
    }

    /**
     * Click listener for the tap indicators
     */
    override fun onClick(v: View?) {
        getTapIndicators().forEach { it.setBackgroundResource(0) }
        if (currentControl == v?.id) {
            helpTitle.setText(R.string.tips_player_controls)
            helpDescription.setText(R.string.tips_player_controls_description)
            currentControl = null
            return
        }
        when (v?.id) {
            R.id.tapIndicatorTracks -> {
                helpTitle.setText(R.string.tips_audio_sub)
                helpDescription.setText(R.string.tap)
                v.background = ContextCompat.getDrawable(player, R.drawable.tips_tap)
                currentControl = v.id
            }
            R.id.tapIndicatorOrientation -> {
                helpTitle.setText(R.string.lock_orientation)
                helpDescription.setText(R.string.lock_orientation_description)
                v.background = ContextCompat.getDrawable(player, R.drawable.tips_tap)
                currentControl = v.id
            }
            R.id.tapIndicatorPlay -> {
                helpTitle.setText(R.string.play)
                helpDescription.setText(R.string.tips_play_description)
                v.background = ContextCompat.getDrawable(player, R.drawable.tips_tap)
                currentControl = v.id
            }
            R.id.tapIndicatorRatio -> {
                helpTitle.setText(R.string.aspect_ratio)
                helpDescription.setText(R.string.aspect_ratio_description)
                v.background = ContextCompat.getDrawable(player, R.drawable.tips_tap)
                currentControl = v.id
            }
            R.id.tapIndicatorAdvanced -> {
                helpTitle.setText(R.string.advanced_options)
                helpDescription.setText(R.string.advanced_options_description)
                v.background = ContextCompat.getDrawable(player, R.drawable.tips_tap)
                currentControl = v.id
            }
        }
    }
}

/**
 * Steps for the tips
 * @param titleText: the string resource to display in the title [TextView]
 * @param descriptionText: the string resource to display in the description [TextView]
 */
enum class VideoPlayerTipsStep(@StringRes var titleText: Int, @StringRes var descriptionText: Int) {
    CONTROLS(R.string.tips_player_controls, R.string.tips_player_controls_description),
    BRIGHTNESS(R.string.brightness, R.string.tips_swipe),
    VOLUME(R.string.volume, R.string.tips_swipe),
    PAUSE(R.string.pause, R.string.pause_description),
    SEEK_TAP(R.string.seek_tap, R.string.seek_tap_description),
    SEEK(R.string.seek, R.string.tips_swipe_horizontal);

    /**
     * @return the next step
     */
    fun next(): VideoPlayerTipsStep {
        return values()[ordinal + 1]
    }
}