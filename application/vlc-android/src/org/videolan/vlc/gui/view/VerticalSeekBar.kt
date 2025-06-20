/*
 * ************************************************************************
 *  VerticalSeekBar.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.android.material.slider.Slider

class VerticalSeekBar : Slider {
    private var listener: Slider.OnSliderTouchListener? = null
    var fromUser = false

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {

    }

    override fun addOnSliderTouchListener(listener: OnSliderTouchListener) {
        this.listener = listener
        super.addOnSliderTouchListener(listener)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val handled = super.onTouchEvent(event)

        if (handled) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    fromUser = true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    fromUser = false
                }
            }
        }

        return handled
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return false


            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                fromUser = true
//                //to allow snaping to save current state when modifying a band from DPAD
                listener?.onStartTrackingTouch(this)

                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) -1 else 1
                var currentProgress = value + (direction * 10F)

                if (currentProgress > valueTo) {
                    currentProgress = valueTo
                } else if (currentProgress < 0) {
                    currentProgress = 0F
                }
                setValue(currentProgress)

                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        fromUser = false
        return super.onKeyUp(keyCode, event)
    }


}