/*
 * ************************************************************************
 *  CollapsibleLinearLayout.kt
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

package org.videolan.vlc.gui.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.AccelerateInterpolator
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout

class CollapsibleLinearLayout : LinearLayout {

    private var locked: Boolean = false
    private var onReadyListener: (() -> Unit)? = null
    var isCollapsed = true
    private var animationUpdateListener: ((Float) -> Unit)? = null
    private var maxHeight: Int = -1
    private val animator by lazy {
        ValueAnimator().apply {
            interpolator = AccelerateInterpolator()
            duration = 300
            addUpdateListener { animator ->
                layoutParams.height = animator.animatedValue as Int
                requestLayout()
                animationUpdateListener?.invoke(animator.animatedFraction)
            }
        }
    }

    fun onReady(listener:() -> Unit) {
        this.onReadyListener = listener
    }

    fun setAnimationUpdateListener(listener: (Float) -> Unit) {
        this.animationUpdateListener = listener
    }

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize()
    }

    private fun initialize() {
        if (layoutParams is ConstraintLayout.LayoutParams) throw IllegalStateException("The parent should not be a ConstraintLayout to prevent height issues (when set to 0)")
        viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        maxHeight = height
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                        layoutParams.height = 0
                        requestLayout()
                        onReadyListener?.invoke()
                    }
                })
    }

    fun toggle() {
        if (locked) return
        val fromHeight = if (!isCollapsed) maxHeight else 0
        val toHeight = if (!isCollapsed) 0 else maxHeight
        isCollapsed = !isCollapsed
        animator.setIntValues(fromHeight, toHeight)
        animator.start()
    }

    fun collapse() {
        if (locked) return
        if (!isCollapsed) toggle()
    }

    fun lock() {
        if (isCollapsed) toggle()
        locked = true
    }


}