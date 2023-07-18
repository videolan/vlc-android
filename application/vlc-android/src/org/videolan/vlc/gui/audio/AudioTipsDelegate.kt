/*
 * ************************************************************************
 *  AudioTipsDelegate.kt
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

package org.videolan.vlc.gui.audio

import android.animation.Animator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.window.layout.FoldingFeature
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.tools.PREF_AUDIOPLAYER_TIPS_SHOWN
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.tools.putSingle
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.helpers.TipsUtils
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.util.getScreenHeight
import org.videolan.vlc.util.getScreenWidth

class AudioTipsDelegate(private val activity: AudioPlayerContainerActivity) {
    var currentTip: AudioPlayerTipsStep? = null
    private lateinit var initialConstraintSet: ConstraintSet
    private lateinit var audioPlayerTips: ConstraintLayout
    private lateinit var audioTipsBackground: View
    private lateinit var headerPrevious: ImageView
    private lateinit var tapIndicatorPlaylist: View
    private lateinit var headerLargePlayPause: ImageView
    private lateinit var tapIndicatorStop: View
    private lateinit var nextButton: Button
    private lateinit var tapGestureHorizontal: View
    private lateinit var helpTitle: TextView
    private lateinit var helpDescription: TextView
    private var rightGuidelineEndBound = 1F
    private var topGuidelineEndBound = 0F

    private val transition = Fade().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }
    private val currentAnimations = ArrayList<Animator>()

    fun init(vsc: ViewStubCompat?) {
        vsc?.inflate()
        audioTipsBackground = activity.findViewById(R.id.audio_tips_background)
        headerPrevious = activity.findViewById(R.id.header_previous)
        audioPlayerTips = activity.findViewById(R.id.audioPlayerTips)
        tapIndicatorPlaylist = activity.findViewById(R.id.tapIndicatorPlaylist)
        headerLargePlayPause = activity.findViewById(R.id.header_large_play_pause)
        tapIndicatorStop = activity.findViewById(R.id.tapIndicatorStop)
        nextButton = activity.findViewById(R.id.nextButton)
        tapGestureHorizontal = activity.findViewById(R.id.tapGestureHorizontal)
        helpTitle = activity.findViewById(R.id.helpTitle)
        helpDescription = activity.findViewById(R.id.helpDescription)
        activity.playerBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        activity.playerBehavior.lock(true)
        activity.playerBehavior.setPeekHeightListener {
            updateBackgroundPosition(it)
        }
        if (!::initialConstraintSet.isInitialized) {
            initialConstraintSet = ConstraintSet()
            initialConstraintSet.clone(audioPlayerTips)
        }
        audioPlayerTips.setVisible()
        audioPlayerTips.setOnTouchListener { _, _ -> true }
        activity.lifecycleScope.launch(Dispatchers.Main) { next() }
        (activity.windowLayoutInfo?.displayFeatures?.firstOrNull() as? FoldingFeature)?.let {foldingFeature ->
            if (foldingFeature.occlusionType == FoldingFeature.OcclusionType.FULL) {
                if (foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL)
                    rightGuidelineEndBound = foldingFeature.bounds.left.toFloat() / activity.getScreenWidth()
                else
                    topGuidelineEndBound = foldingFeature.bounds.bottom.toFloat() / activity.getScreenHeight()
            }
        }
    }

    private fun updateBackgroundPosition(peek: Int) {
        val lp = (audioTipsBackground.layoutParams as ConstraintLayout.LayoutParams)
        lp.bottomMargin = peek
        audioTipsBackground.layoutParams = lp
    }

    /**
     * Load the next tip screen depending on the currentTip
     */
    fun next() {
        if (currentTip == AudioPlayerTipsStep.HOLD_STOP) {
            close()
            return
        }
        currentTip = currentTip?.next() ?: AudioPlayerTipsStep.SWIPE_NEXT

        val constraintSet = ConstraintSet().apply { clone(initialConstraintSet) }
        TransitionManager.beginDelayedTransition(audioPlayerTips, transition)

        clearAllAnimations()
        nextButton.setText(R.string.next_step)

        constraintSet.setGuidelinePercent(R.id.endGuideline, rightGuidelineEndBound)
        constraintSet.setGuidelinePercent(R.id.topGuideline, topGuidelineEndBound)
        when (currentTip) {
            AudioPlayerTipsStep.SWIPE_NEXT -> {
                if (activity.isTablet()) {
                    currentAnimations.clear()
                    constraintSet.setVisibility(R.id.tapIndicatorPlaylist, View.VISIBLE)
                    val indicatorX = headerPrevious.left + (headerPrevious.width / 2) - 24.dp
                    constraintSet.setMargin(R.id.tapIndicatorPlaylist, ConstraintSet.START, indicatorX)
                    TipsUtils.startTapAnimation(listOf(tapIndicatorPlaylist))
                } else {
                    constraintSet.setVisibility(R.id.tap_gesture_horizontal_background, View.VISIBLE)
                    constraintSet.setVisibility(R.id.tapGestureHorizontal, View.VISIBLE)
                    currentAnimations.clear()
                    currentAnimations.add(TipsUtils.horizontalSwipe(tapGestureHorizontal))
                }
            }
            AudioPlayerTipsStep.TAP_PLAYLIST -> {
                constraintSet.setVisibility(R.id.tapIndicatorPlaylist, View.VISIBLE)
                TipsUtils.startTapAnimation(listOf(tapIndicatorPlaylist))
            }
            AudioPlayerTipsStep.HOLD_STOP -> {
                if (activity.isTablet()) {
                    val indicatorX = headerLargePlayPause.left + (headerLargePlayPause.width / 2) - 24.dp
                    constraintSet.connect(R.id.tapIndicatorStop, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    constraintSet.setMargin(R.id.tapIndicatorStop, ConstraintSet.START, indicatorX)
                    constraintSet.clear(R.id.tapIndicatorStop, ConstraintSet.END)
                    TipsUtils.startTapAnimation(listOf(tapIndicatorPlaylist))
                }
                constraintSet.setVisibility(R.id.tapIndicatorStop, View.VISIBLE)

                TipsUtils.startTapAnimation(listOf(tapIndicatorStop), true)
                nextButton.setText(R.string.close)
            }
            else -> {}
        }

        constraintSet.applyTo(audioPlayerTips)
        updateBackgroundPosition(activity.playerBehavior.peekHeight)

        helpTitle.setText(currentTip!!.titleText)
        helpDescription.setText(if ( activity.isTablet()) currentTip!!.descriptionTextTablet else currentTip!!.descriptionText)
    }

    /**
     * Close the tips, cancel all the animations, relaunch the playback
     */
    fun close() {
        clearAllAnimations()
        audioPlayerTips.setGone()
        activity.playerBehavior.removePeekHeightListener()
        Settings.getInstance(activity).putSingle(PREF_AUDIOPLAYER_TIPS_SHOWN, true)
        currentTip = null

        activity.audioPlayer.playlistModel.service?.play()
        activity.shownTips.add(R.id.audio_player_tips)
        activity.playerBehavior.lock(false)
    }

    /**
     * Clear all the launched animations
     */
    private fun clearAllAnimations() {
        currentAnimations.forEach {
            it.cancel()
            it.removeAllListeners()
        }
        tapIndicatorPlaylist.animate().cancel()
        tapIndicatorStop.animate().cancel()
    }
}

/**
 * Steps for the tips
 * @param titleText: the string resource to display in the title [TextView]
 * @param descriptionText: the string resource to display in the description [TextView]
 */
enum class AudioPlayerTipsStep(@StringRes var titleText: Int, @StringRes var descriptionText: Int, @StringRes var descriptionTextTablet: Int) {
    SWIPE_NEXT(R.string.previous_next_song, R.string.tips_swipe_horizontal, R.string.tap_to_previous_next),
    TAP_PLAYLIST(R.string.tips_playlist, R.string.tap, R.string.tap),
    HOLD_STOP(R.string.stop, R.string.hold_to_stop, R.string.hold_to_stop);

    /**
     * @return the next step
     */
    fun next(): AudioPlayerTipsStep {
        return values()[ordinal + 1]
    }
}