/*
 * ************************************************************************
 *  AudioPlayerAnimatorDelegate.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

import android.annotation.TargetApi
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.LifecycleObserver
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AudioPlayerBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.getScreenWidth
import kotlin.math.max
import kotlin.math.min

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal class AudioPlayerAnimator : IAudioPlayerAnimator, LifecycleObserver {

    override fun AudioPlayer.setupAnimator(binding: AudioPlayerBinding) {
        audioPlayer = this
        cl = binding.root as ConstraintLayout
        this@AudioPlayerAnimator.binding = binding
        defaultBackgroundId = UiTools.getResourceFromAttribute(requireActivity(), R.attr.background_default)
        lifecycle.addObserver(this@AudioPlayerAnimator)
        initConstraintSets()
    }

    @DrawableRes
    private var defaultBackgroundId: Int = -1
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var cl: ConstraintLayout
    private val showPlaylistConstraint = ConstraintSet()
    private val hidePlaylistConstraint = ConstraintSet()
    private val hidePlaylistLandscapeConstraint = ConstraintSet()
    private var currentCoverArt: String? = null
    private lateinit var binding: AudioPlayerBinding
    private val transition = AutoTransition().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }
    private var showCover = false
        set(value) {
            //avoid playing the transition again
            if (value == field) {
                return
            }
            TransitionManager.beginDelayedTransition(cl, transition)
            when {
                !value -> showPlaylistConstraint
                audioPlayer.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> hidePlaylistLandscapeConstraint
                else -> hidePlaylistConstraint
            }.applyTo(cl)
            onSlide(1F)
            field = value
        }

    override fun switchShowCover() {
        showCover = !showCover
    }

    override fun isShowingCover(): Boolean = showCover

    override fun showCover(value: Boolean) {
        showCover = value
    }

    private fun initConstraintSets() {
        showPlaylistConstraint.clone(cl)
        hidePlaylistConstraint.clone(cl)
        hidePlaylistLandscapeConstraint.clone(cl)

        hidePlaylistConstraint.setVisibility(R.id.songs_list, View.GONE)
        hidePlaylistConstraint.setVisibility(R.id.cover_media_switcher, View.VISIBLE)

        hidePlaylistLandscapeConstraint.setVisibility(R.id.songs_list, View.GONE)
        hidePlaylistLandscapeConstraint.setVisibility(R.id.cover_media_switcher, View.VISIBLE)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override suspend fun updateBackground() {
        if (Settings.getInstance(audioPlayer.requireActivity()).getBoolean("blurred_cover_background", true)) {
            val mw = audioPlayer.playlistModel.currentMediaWrapper ?: return
            if (currentCoverArt == mw.artworkMrl) return
            currentCoverArt = mw.artworkMrl
            if (mw.artworkMrl.isNullOrEmpty()) setDefaultBackground()
            else {
                val width = if (binding.contentLayout.width > 0) binding.contentLayout.width else audioPlayer.activity?.getScreenWidth() ?: return
                val blurredCover = withContext(Dispatchers.IO) { UiTools.blurBitmap(AudioUtil.readCoverBitmap(Uri.decode(mw.artworkMrl), width)) }
                if (blurredCover !== null) {
                    val activity = audioPlayer.activity as? AudioPlayerContainerActivity ?: return
                    binding.backgroundView.setColorFilter(UiTools.getColorFromAttribute(activity, R.attr.audio_player_background_tint))
                    binding.backgroundView.setImageBitmap(blurredCover)
                    binding.backgroundView.visibility = View.VISIBLE
                } else setDefaultBackground()
            }
        }
    }

    @MainThread
    private fun setDefaultBackground() {
        binding.backgroundView.visibility = View.INVISIBLE
    }

    override fun manageSearchVisibilities(filter: Boolean) {
        binding.playlistSearch.alpha = if (filter) 0f else 1f
        binding.playlistSwitch.alpha = if (filter) 0f else 1f
        binding.advFunction.alpha = if (filter) 0f else 1f
        binding.audioMediaSwitcher.alpha = if (filter) 0f else 1f
        binding.playlistSearchText.visibility = if (filter) View.VISIBLE else View.GONE
    }

    override fun onSlide(slideOffset: Float) {
        binding.progressBar.alpha = 1 - slideOffset
        binding.progressBar.layoutParams.height = ((1 - slideOffset) * 4.dp).toInt()
        binding.progressBar.requestLayout()
        // 0% to 40%
        binding.headerBackground.alpha = 0.4F + ((1 - slideOffset) * 0.6F)
        binding.headerDivider.alpha = slideOffset
        if (slideOffset != 1f) audioPlayer.clearSearch()
        binding.playlistSearch.alpha = slideOffset
        binding.playlistSwitch.alpha = slideOffset
        binding.advFunction.alpha = slideOffset
        binding.headerPlayPause.alpha = 1 - slideOffset
        binding.headerTime.alpha = 1 - slideOffset

        val translationOffset = min(1f, max(0f, (slideOffset * 1.4f) - 0.2f))
        binding.playlistSearch.translationY = -(1 - translationOffset) * 48.dp
        binding.playlistSwitch.translationY = -(1 - translationOffset) * 48.dp
        binding.advFunction.translationY = -(1 - translationOffset) * 48.dp
        binding.headerPlayPause.translationY = translationOffset * 48.dp
        binding.headerTime.translationY = translationOffset * 48.dp
    }
}

interface IAudioPlayerAnimator {
    fun switchShowCover()
    fun isShowingCover(): Boolean
    fun showCover(value: Boolean)
    fun AudioPlayer.setupAnimator(binding: AudioPlayerBinding)
    suspend fun updateBackground()
    fun manageSearchVisibilities(filter: Boolean = false)
    fun onSlide(slideOffset: Float)
}