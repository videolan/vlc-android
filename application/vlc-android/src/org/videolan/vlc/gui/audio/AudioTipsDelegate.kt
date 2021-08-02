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
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.transition.Fade
import androidx.transition.TransitionManager
import kotlinx.android.synthetic.main.audio_player_tips.*
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.helpers.TipsUtils

class AudioTipsDelegate(private val activity: AudioPlayerContainerActivity) {
    var currentTip: AudioPlayerTipsStep? = null
    private lateinit var initialConstraintSet: ConstraintSet
    private val transition = Fade().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }
    private val currentAnimations = ArrayList<Animator>()



    fun init(vsc: ViewStubCompat) {
        vsc.inflate()
        activity.playerBehavior.lock(true)
        activity.playerBehavior.setPeekHeightListener {
            updateBackgroundPosition(it)
        }
        initialConstraintSet = ConstraintSet()
        initialConstraintSet.clone(activity.audioPlayerTips)
        activity.audioPlayerTips.setOnTouchListener { _, _ -> true }
        next()
    }

    private fun updateBackgroundPosition(peek: Int) {
        val lp = (activity.audio_tips_background.layoutParams as ConstraintLayout.LayoutParams)
        lp.bottomMargin = peek
        activity.audio_tips_background.layoutParams = lp
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
        TransitionManager.beginDelayedTransition(activity.audioPlayerTips, transition)


        clearAllAnimations()

        when (currentTip) {
            AudioPlayerTipsStep.SWIPE_NEXT -> {
                constraintSet.setVisibility(R.id.tap_gesture_horizontal_background, View.VISIBLE)
                constraintSet.setVisibility(R.id.tapGestureHorizontal, View.VISIBLE)
                currentAnimations.clear()
                currentAnimations.add(TipsUtils.horizontalSwipe(activity.tapGestureHorizontal))
            }
            AudioPlayerTipsStep.TAP_PLAYLIST -> {
                constraintSet.setVisibility(R.id.tapIndicatorPlaylist, View.VISIBLE)
                TipsUtils.startTapAnimation(listOf(activity.tapIndicatorPlaylist))
            }
            AudioPlayerTipsStep.HOLD_STOP -> {
                constraintSet.setVisibility(R.id.tapIndicatorStop, View.VISIBLE)
                TipsUtils.startTapAnimation(listOf(activity.tapIndicatorStop), true)
                activity.nextButton.setText(R.string.close)
            }
        }

        constraintSet.applyTo(activity.audioPlayerTips)
        updateBackgroundPosition(activity.playerBehavior.peekHeight)

        activity.helpTitle.setText(currentTip!!.titleText)
        activity.helpDescription.setText(currentTip!!.descriptionText)
    }

    /**
     * Close the tips, cancel all the animations, relaunch the playback
     */
    fun close() {
        clearAllAnimations()
        (activity.audioPlayerTips.parent as? ViewGroup)?.removeView(activity.audioPlayerTips)
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
        activity.tapIndicatorPlaylist.animate().cancel()
        activity.tapIndicatorStop.animate().cancel()
    }
}

/**
 * Steps for the tips
 * @param titleText: the string resource to display in the title [TextView]
 * @param descriptionText: the string resource to display in the description [TextView]
 */
enum class AudioPlayerTipsStep(@StringRes var titleText: Int, @StringRes var descriptionText: Int) {
    SWIPE_NEXT(R.string.previous_next_song, R.string.tips_swipe_horizontal),
    TAP_PLAYLIST(R.string.tips_playlist, R.string.tap),
    HOLD_STOP(R.string.stop, R.string.hold_to_stop);

    /**
     * @return the next step
     */
    fun next(): AudioPlayerTipsStep {
        return values()[ordinal + 1]
    }
}