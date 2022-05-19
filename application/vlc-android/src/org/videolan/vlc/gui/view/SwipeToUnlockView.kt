/*
 * ************************************************************************
 *  SwipeToUnlockView.kt
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

package org.videolan.vlc.gui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.MaskFilterSpan
import android.util.AttributeSet
import android.util.LayoutDirection
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import org.videolan.resources.AndroidDevices
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.vlc.R

class SwipeToUnlockView : ConstraintLayout {

    private lateinit var currentText: String
    private var extremum: Int = 0
    private lateinit var swipeIcon: ImageView
    private lateinit var guideline: Guideline
    private lateinit var swipeText: TextView

    private var unlocking: Boolean = false
    private lateinit var onStartTouching: () -> Unit
    private lateinit var onStopTouching: () -> Unit
    private lateinit var onUnlock: () -> Unit
    private lateinit var keyAnimation: ValueAnimator
    var isDPADAllowed = true
    set(value) {
        field = value
        updateText()
    }

    private val tvAcceptedKeys = arrayOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    fun setOnStartTouchingListener(listener: () -> Unit) {
        onStartTouching = listener
    }

    fun setOnStopTouchingListener(listener: () -> Unit) {
        onStopTouching = listener
    }

    fun setOnUnlockListener(listener: () -> Unit) {
        onUnlock = listener
    }

    override fun setVisibility(visibility: Int) {
        if (visibility == View.VISIBLE) {
            unlocking = false
            if (extremum != 0) playStep(extremum)
            requestFocus()
        }
        super.setVisibility(visibility)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (unlocking) return super.onTouchEvent(event)
        event?.let {
            val currentX = event.x.toInt().coerceAtLeast(extremum).coerceAtMost(width - extremum).run {
                if (layoutDirection == LayoutDirection.RTL) width - this
                else this
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onStartTouching.invoke()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {

                    if (currentX >= width - extremum) unlock()

                    playStep(currentX)

                    return true
                }
                MotionEvent.ACTION_UP -> {
                    animateBack(currentX)
                    onStopTouching.invoke()
                    return true
                }
                else -> return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateBack(currentX: Int) {
        val animation = ValueAnimator.ofInt(currentX, extremum)
        animation.duration = 250 // milliseconds

        animation.addUpdateListener { animator -> playStep(animator.animatedValue as Int) }
        animation.start()
        swipeText.alpha = 1f
    }

    private fun playStep(currentX: Int) {
        guideline.setGuidelineBegin(currentX)
        val progress = (currentX.toFloat() - extremum) / (width - extremum)
        swipeText.alpha = 1F - progress

        val string = SpannableString(currentText)
        if (progress > 0) {
            val blurMask = BlurMaskFilter(progress * 10, BlurMaskFilter.Blur.NORMAL)
            string.setSpan(MaskFilterSpan(blurMask), 0, string.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        swipeText.text = string
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isDPADAllowed && event?.keyCode in tvAcceptedKeys && !unlocking) {
            onStartTouching.invoke()
            if (!::keyAnimation.isInitialized || !keyAnimation.isRunning) {
                keyAnimation = ValueAnimator.ofInt(extremum, width - extremum)
                keyAnimation.interpolator = AccelerateInterpolator()
                keyAnimation.duration = 2000
                keyAnimation.addUpdateListener { animator ->
                    run {
                        playStep(animator.animatedValue as Int)
                        if (animator.animatedValue == width - extremum) unlock()
                    }
                }
                keyAnimation.start()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.keyCode in tvAcceptedKeys) {
            onStopTouching.invoke()

            if (::keyAnimation.isInitialized && keyAnimation.isRunning) {
                animateBack(keyAnimation.animatedValue as Int)
                keyAnimation.removeAllUpdateListeners()
                keyAnimation.cancel()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun unlock() {
        unlocking = true
        onUnlock.invoke()
        setGone()
        guideline.setGuidelineBegin(extremum)
    }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.swipe_to_unlock, this, true)
        guideline = findViewById(R.id.swipe_guideline)
        swipeIcon = findViewById(R.id.swipe_icon)
        swipeText = findViewById(R.id.swipe_text)

        swipeIcon.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> extremum = (v.width / 2) + 4.dp }
        isFocusable = true

        updateText()
    }

    private fun updateText() {
        currentText = if (!isDPADAllowed || !AndroidDevices.isTv) context.getString(R.string.swipe_unlock) else context.getString(R.string.swipe_unlock_no_touch)
        swipeText.text = currentText
    }
}
