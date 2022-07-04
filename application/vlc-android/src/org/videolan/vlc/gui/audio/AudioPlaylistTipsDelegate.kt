/*
 * ************************************************************************
 *  AudioPlaylistTipsDelegate.kt
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

import android.animation.*
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlaylistItemBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.helpers.TipsUtils
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.getScreenHeight
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.PlaylistModel

class AudioPlaylistTipsDelegate(private val activity: AudioPlayerContainerActivity) {
    private lateinit var thirdItemBinding: PlaylistItemBinding
    private lateinit var secondItemBinding: PlaylistItemBinding
    var currentTip: AudioPlaylistTipsStep? = null
    private lateinit var initialConstraintSet: ConstraintSet
    private val transition = Fade().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }
    private val currentAnimations = ArrayList<Animator>()

    private lateinit var audioPlaylistTips: ConstraintLayout
    private lateinit var tracksContainer: LinearLayout
    private lateinit var nextButton: Button
    private lateinit var tapIndicatorRearrange: View
    private lateinit var tapGestureHorizontal: View
    private lateinit var tapIndicatorRewind: View
    private lateinit var tapIndicatorForward: View
    private lateinit var plTipsTimeline: SeekBar
    private lateinit var helpTitle: TextView
    private lateinit var helpDescription: TextView
    private var rightGuidelineEndBound = 1F
    private var middleGuidelineEndBound = 0.5F

    fun init(vsc: ViewStubCompat?) {
        vsc?.inflate()
        audioPlaylistTips = activity.findViewById(R.id.audioPlaylistTips)
        tracksContainer = activity.findViewById(R.id.tracksContainer)
        nextButton = activity.findViewById(R.id.nextButton)
        tapIndicatorRearrange = activity.findViewById(R.id.tapIndicatorRearrange)
        tapGestureHorizontal = activity.findViewById(R.id.tapGestureHorizontal)
        tapIndicatorRewind = activity.findViewById(R.id.tapIndicatorRewind)
        tapIndicatorForward = activity.findViewById(R.id.tapIndicatorForward)
        plTipsTimeline = activity.findViewById(R.id.plTipsTimeline)
        helpTitle = activity.findViewById(R.id.helpTitle)
        helpDescription = activity.findViewById(R.id.helpDescription)
        activity.lockPlayer(true)
        if (!::initialConstraintSet.isInitialized) {
            initialConstraintSet = ConstraintSet()
            initialConstraintSet.clone(audioPlaylistTips)
        }

        if (tracksContainer.childCount == 0) {
            //populate fake tracks
            val playlistModel = ViewModelProvider(activity).get(PlaylistModel::class.java)
            playlistModel.currentMediaWrapper?.let {
                for (i in 0..10) {
                    val v = LayoutInflater.from(activity)
                        .inflate(R.layout.playlist_item, tracksContainer, false)
                    var binding: PlaylistItemBinding = DataBindingUtil.bind(v)!!
                    binding.media = it
                    binding.scaleType = ImageView.ScaleType.CENTER_CROP
                    binding.subTitle = MediaUtils.getMediaSubtitle(it)
                    tracksContainer.addView(v)
                    if (i == 2) {
                        binding.playing.stop()
                        binding.playing.visibility = View.VISIBLE
                        binding.coverImage.visibility = View.INVISIBLE
                        binding.audioItemTitle.setTypeface(null, Typeface.BOLD)
                        binding.audioItemSubtitle.setTypeface(null, Typeface.BOLD)
                    } else {
                        binding.playing.stop()
                        binding.playing.visibility = View.INVISIBLE
                        binding.audioItemTitle.typeface = null
                        binding.coverImage.visibility = View.VISIBLE
                    }
                    binding.masked = true
                    if (i == 1) {
                        binding.masked = false
                        binding.itemContainer.setBackgroundColor(getItemColor())
                        secondItemBinding = binding
                    } else if (i == 2) thirdItemBinding = binding
                }
            }
        }

        audioPlaylistTips.setVisible()
        activity.lifecycleScope.launch(Dispatchers.Main) { next() }
        (activity.windowLayoutInfo?.displayFeatures?.firstOrNull() as? FoldingFeature)?.let {foldingFeature ->
            if (foldingFeature.occlusionType == FoldingFeature.OcclusionType.FULL) {
                if (foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL)
                    rightGuidelineEndBound = foldingFeature.bounds.left.toFloat() / activity.getScreenWidth()
                else
                    middleGuidelineEndBound = (foldingFeature.bounds.bottom.toFloat() + ((activity.getScreenHeight() - foldingFeature.bounds.bottom.toFloat()) / 2)) / activity.getScreenHeight()
            }
        }
    }

    private fun getItemColor(): Int {
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(R.attr.tips_item_background, typedValue, true)
        return typedValue.data
    }

    /**
     * Start and get the drag and drop animation.
     * @param indicatorView: the tap indicator view
     * @param draggedView: the view that has to be dragged
     * @return the generated [ObjectAnimator]
     */
    private fun dragAndDrop(indicatorView:View, draggedView:View): AnimatorSet {

        val show = ObjectAnimator.ofFloat(indicatorView, View.ALPHA, 1F)
        show.duration = 200
        show.startDelay = 500

        val tapScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9F)
        val tapScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9F)
        val tap: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(indicatorView, tapScaleX, tapScaleY)
        tap.duration = 300

        val drag = ObjectAnimator.ofFloat(indicatorView, View.TRANSLATION_Y, (-48).dp.toFloat())
        drag.startDelay = 300
        drag.duration = 800
        drag.addUpdateListener { draggedView.translationY = it.animatedValue as Float }


        val untapScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1F)
        val untapScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1F)
        val untap: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(indicatorView, untapScaleX, untapScaleY)
        untap.duration = 300
        untap.doOnEnd { draggedView.animate().setDuration(300).translationY(0F) }

        val hide = ObjectAnimator.ofFloat(indicatorView, View.ALPHA, 0F)
        hide.duration = 200

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(show, tap, drag, untap, hide)

        animatorSet.doOnEnd { animatorSet.start() }
        animatorSet.start()

        return animatorSet
    }


    /**
     * Start and get the long tap to seek animation.
     * @param rewindIndicator: the tap indicator view for the rewind action
     * @param forwardIndicator: the tap indicator view for the forward action
     * @param seekView: the seekbar that has to be animated
     * @return the generated [ObjectAnimator]
     */
    private fun longTapSeek(rewindIndicator:View, forwardIndicator:View, seekView:SeekBar): AnimatorSet {
        val showRewind = ObjectAnimator.ofFloat(rewindIndicator, View.ALPHA, 1F)
        showRewind.duration = 200
        showRewind.startDelay = 500

        val tapRewindScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9F)
        val tapRewindScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9F)
        val tapRewind: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(rewindIndicator, tapRewindScaleX, tapRewindScaleY)
        tapRewind.duration = 300

        tapRewind.doOnStart {
           val seek = ValueAnimator.ofInt(660,410)
            seek.startDelay = 200
            seek.duration = 1400
            seek.interpolator = AccelerateInterpolator()
            seek.addUpdateListener { animator -> seekView.progress = animator.animatedValue as Int }
            seek.start()
        }

        val untapRewindScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1F)
        val untapRewindScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1F)
        val untapRewind: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(rewindIndicator, untapRewindScaleX, untapRewindScaleY)
        untapRewind.duration = 300
        untapRewind.startDelay = 1200

        val hideRewind = ObjectAnimator.ofFloat(rewindIndicator, View.ALPHA, 0F)
        hideRewind.duration = 200

        val showForward = ObjectAnimator.ofFloat(forwardIndicator, View.ALPHA, 1F)
        showForward.duration = 200
        showForward.startDelay = 500

        val tapForwardScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9F)
        val tapForwardScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9F)
        val tapForward: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(forwardIndicator, tapForwardScaleX, tapForwardScaleY)
        tapForward.duration = 300

        tapForward.doOnStart {
            val seek = ValueAnimator.ofInt(410,660)
            seek.startDelay = 200
            seek.duration = 1400
            seek.interpolator = AccelerateInterpolator()
            seek.addUpdateListener { animator -> seekView.progress = animator.animatedValue as Int }
            seek.start()
        }

        val untapForwardScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1F)
        val untapForwardScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1F)
        val untapForward: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(forwardIndicator, untapForwardScaleX, untapForwardScaleY)
        untapForward.duration = 300
        untapForward.startDelay = 1200

        val hideForward = ObjectAnimator.ofFloat(forwardIndicator, View.ALPHA, 0F)
        hideForward.duration = 200

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(showRewind, tapRewind, untapRewind, hideRewind, showForward, tapForward, untapForward, hideForward)

        animatorSet.doOnEnd { animatorSet.start() }
        animatorSet.start()

        return animatorSet

    }



    /**
     * Load the next tip screen depending on the currentTip
     */
    fun next() {
        if (currentTip == AudioPlaylistTipsStep.SEEK) {
            close()
            return
        }
        currentTip = currentTip?.next() ?: AudioPlaylistTipsStep.REMOVE

        val constraintSet = ConstraintSet().apply { clone(initialConstraintSet) }
        TransitionManager.beginDelayedTransition(audioPlaylistTips, transition)


        clearAllAnimations()
        nextButton.setText(R.string.next_step)

        constraintSet.setGuidelinePercent(R.id.endGuideline, rightGuidelineEndBound)
        constraintSet.setGuidelinePercent(R.id.middleGuideline, middleGuidelineEndBound)
        when (currentTip) {
            AudioPlaylistTipsStep.REMOVE -> {
                if (activity.isTablet()){
                    val indicatorY = secondItemBinding.itemContainer.top + (secondItemBinding.itemContainer.height / 2) - 24.dp
                    constraintSet.setMargin(R.id.tapIndicatorRearrange, ConstraintSet.TOP, indicatorY)
                    currentAnimations.clear()
                    constraintSet.setVisibility(R.id.tapIndicatorRearrange, View.VISIBLE)
                    val indicatorX = secondItemBinding.itemDelete.left + (secondItemBinding.itemDelete.width / 2) - 24.dp
                    constraintSet.setMargin(R.id.tapIndicatorRearrange, ConstraintSet.START, indicatorX)
                    constraintSet.clear(R.id.tapIndicatorRearrange, ConstraintSet.END)
                    TipsUtils.startTapAnimation(listOf(tapIndicatorRearrange))
                } else {
                    constraintSet.setVisibility(R.id.tap_gesture_horizontal_background, View.VISIBLE)
                    constraintSet.setVisibility(R.id.tapGestureHorizontal, View.VISIBLE)
                    currentAnimations.clear()
                    currentAnimations.add(TipsUtils.horizontalSwipe(tapGestureHorizontal) {
                        secondItemBinding.itemContainer.translationX = (it.animatedValue as Float) * 4
                    })
                }
            }
            AudioPlaylistTipsStep.REARRANGE -> {
                secondItemBinding.itemContainer.translationX = 0F
                thirdItemBinding.masked = false
                thirdItemBinding.playing.start()
                thirdItemBinding.itemContainer.setBackgroundColor(getItemColor())

                constraintSet.setVisibility(R.id.tapIndicatorRearrange, View.VISIBLE)
                val indicatorY = thirdItemBinding.itemContainer.top + (thirdItemBinding.itemContainer.height / 2) - 24.dp
                constraintSet.setMargin(R.id.tapIndicatorRearrange, ConstraintSet.TOP, indicatorY)
                currentAnimations.clear()
                if (activity.isTablet()){
                    val indicatorX = thirdItemBinding.itemMoveUp.left + (thirdItemBinding.itemMoveUp.width / 2) - 24.dp
                    constraintSet.setMargin(R.id.tapIndicatorRearrange, ConstraintSet.START, indicatorX)
                    constraintSet.clear(R.id.tapIndicatorRearrange, ConstraintSet.END)
                    TipsUtils.startTapAnimation(listOf(tapIndicatorRearrange))
                } else currentAnimations.add(dragAndDrop(tapIndicatorRearrange, thirdItemBinding.itemContainer))

            }
            AudioPlaylistTipsStep.SEEK -> {
                tracksContainer.removeAllViews()
                constraintSet.setVisibility(R.id.pl_tips_next, View.VISIBLE)
                constraintSet.setVisibility(R.id.pl_tips_play_pause, View.VISIBLE)
                constraintSet.setVisibility(R.id.pl_tips_previous, View.VISIBLE)
                constraintSet.setVisibility(R.id.pl_tips_repeat, View.VISIBLE)
                constraintSet.setVisibility(R.id.pl_tips_shuffle, View.VISIBLE)
                constraintSet.setVisibility(R.id.plTipsTimeline, View.VISIBLE)
                constraintSet.setVisibility(R.id.tapIndicatorRewind, View.VISIBLE)
                constraintSet.setVisibility(R.id.tapIndicatorForward, View.VISIBLE)
                constraintSet.connect(R.id.helpTitle, ConstraintSet.BOTTOM, R.id.guideline8, ConstraintSet.TOP, 72.dp)
                currentAnimations.clear()
                currentAnimations.add(longTapSeek(tapIndicatorRewind, tapIndicatorForward, plTipsTimeline))
                nextButton.setText(R.string.close)
            }
            else -> {}
        }

        constraintSet.applyTo(audioPlaylistTips)

        helpTitle.setText(currentTip!!.titleText)
        helpDescription.setText(if (activity.isTablet()) currentTip!!.descriptionTextTablet else currentTip!!.descriptionText)
    }

    /**
     * Close the tips, cancel all the animations, relaunch the playback
     */
    fun close() {
        clearAllAnimations()
        audioPlaylistTips.setGone()
        Settings.getInstance(activity).putSingle(PREF_PLAYLIST_TIPS_SHOWN, true)
        currentTip = null
        activity.audioPlayer.playlistModel.service?.play()
        activity.shownTips.add(R.id.audio_playlist_tips)
        activity.playerBehavior.lock(false)
    }

    /**
     * Clear all the launched animations
     */
    private fun clearAllAnimations() {
        currentAnimations.forEach {
            it.removeAllListeners()
            it.cancel()
        }
    }
}

/**
 * Steps for the tips
 * @param titleText: the string resource to display in the title [TextView]
 * @param descriptionText: the string resource to display in the description [TextView]
 */
enum class AudioPlaylistTipsStep(@StringRes var titleText: Int, @StringRes var descriptionText: Int, @StringRes var descriptionTextTablet: Int) {
    REMOVE(R.string.remove_song, R.string.tips_swipe_horizontal, R.string.tap_to_remove),
    REARRANGE(R.string.rearrange_order, R.string.tips_long_drop, R.string.tap_to_rearrange),
    SEEK(R.string.seek, R.string.tips_hold_seek, R.string.tips_hold_seek);

    /**
     * @return the next step
     */
    fun next(): AudioPlaylistTipsStep {
        return values()[ordinal + 1]
    }
}