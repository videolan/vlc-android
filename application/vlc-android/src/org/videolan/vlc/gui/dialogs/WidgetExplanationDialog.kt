/*
 * ************************************************************************
 *  WidgetExplanationDialog.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.dialogs

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.tools.coerceInOrDefault
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogWidgetExplanationBinding
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.SchedulerCallback


private const val ACTION_REFRESH = "refresh"

class WidgetExplanationDialog : VLCBottomSheetDialogFragment(), SchedulerCallback  {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = true

    private lateinit var resizeAnimation: AnimatorSet
    internal lateinit var binding: DialogWidgetExplanationBinding
    lateinit var scheduler: LifecycleAwareScheduler

    private val sizeDrawables = listOf(R.drawable.vlc_widget_mini, R.drawable.vlc_widget_micro, R.drawable.vlc_widget_pill, R.drawable.vlc_widget_macro)
    var currentDrawable = R.drawable.vlc_widget_macro

    private var currentStep = 1

    override fun initialFocusedView(): View = binding.title

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduler =  LifecycleAwareScheduler(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogWidgetExplanationBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scheduler.scheduleAtFixedRate(ACTION_REFRESH, 2000)

        val sizeDrawbles = listOf(R.drawable.vlc_widget_mini, R.drawable.vlc_widget_micro, R.drawable.vlc_widget_pill, R.drawable.vlc_widget_macro)
        displaySizeImage(sizeDrawbles[0])
        binding.widgetNextButton.setOnClickListener {
            when (currentStep) {
                1 -> {
                    binding.step1.setGone()
                    binding.step2.setVisible()
                    binding.step3.setGone()
                    animateLongTap()
                    scheduler.cancelAction(ACTION_REFRESH)
                }
                2 -> {
                    binding.step1.setGone()
                    binding.step2.setGone()
                    binding.step3.setVisible()
                    binding.widgetNextButton.text = getString(R.string.close)
                    resizeAnimation.cancel()
                }
                else -> dismiss()
            }
            currentStep++

        }
    }

    override fun onTaskTriggered(id: String, data: Bundle) {
        when (id) {
            ACTION_REFRESH -> {
                val nextIndex = (sizeDrawables.indexOf(currentDrawable) + 1).coerceInOrDefault(0, sizeDrawables.size - 1, 0)
                currentDrawable = sizeDrawables[nextIndex]
                displaySizeImage(currentDrawable)
            }
        }
    }

    /**
     * Display a new image in the UI showing the different widget sizes
     *
     * @param drawable
     */
    private fun displaySizeImage(@DrawableRes drawable: Int) {
        binding.widgetSizes.setImageDrawable(ContextCompat.getDrawable(requireActivity(), drawable))
    }

    /**
     * Animate the view showing how to resize a widget
     *
     */
    private fun animateLongTap() {
        val show = ObjectAnimator.ofFloat(binding.resizeLongTapIcon, View.ALPHA, 1F)
        show.duration = 200
        show.startDelay = 2500

        val tapScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9F)
        val tapScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9F)
        val tap: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.resizeLongTapIcon, tapScaleX, tapScaleY)
        tap.duration = 800

        val showHandle: ObjectAnimator = ObjectAnimator.ofFloat(binding.widgetResizeHandle, View.ALPHA, 1F)
        showHandle.duration = 200


        val widthAnimator = ValueAnimator.ofInt(binding.widgetResize.width, 128.dp)
        widthAnimator.duration = 1500
        widthAnimator.interpolator = DecelerateInterpolator()


        val untapScaleX: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1F)
        val untapScaleY: PropertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1F)
        val untap: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.resizeLongTapIcon, untapScaleX, untapScaleY)
        untap.duration = 300

        val hide = ObjectAnimator.ofFloat(binding.resizeLongTapIcon, View.ALPHA, 0F)
        hide.duration = 200

        resizeAnimation = AnimatorSet()
        resizeAnimation.playSequentially(show, tap, showHandle, untap, hide, widthAnimator)

        resizeAnimation.doOnEnd { resizeAnimation.start() }
        resizeAnimation.start()
    }
}