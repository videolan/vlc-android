/*****************************************************************************
 * VerticalSeekBar.java
 *
 * Copyright © 2013 VLC authors and VideoLAN
 *
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
 */

package org.videolan.vlc.gui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

class VerticalSeekBar : AppCompatSeekBar {

    private var mIsMovingThumb = false
    var fromUser = false

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(c: Canvas) {
        c.rotate(-90f)
        c.translate((-height).toFloat(), 0f)

        super.onDraw(c)
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        onSizeChanged(width, height, 0, 0)
    }

    private fun isWithinThumb(event: MotionEvent): Boolean {
        val progress = progress.toFloat()
        val density = this.resources.displayMetrics.density
        val height = height.toFloat()
        val y = event.y
        val max = max.toFloat()
        return progress >= max - (max * (y + THUMB_SLOP * density) / height).toInt() && progress <= max - (max * (y - THUMB_SLOP * density) / height).toInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }

        var handled = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> if (isWithinThumb(event)) {
                fromUser = true
                parent.requestDisallowInterceptTouchEvent(true)
                mIsMovingThumb = true
                handled = true
            }
            MotionEvent.ACTION_MOVE -> if (mIsMovingThumb) {
                val max = max
                progress = max - (max * event.y / height).toInt()
                handled = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                mIsMovingThumb = false
                handled = true
                fromUser = false
            }
        }
        return handled
    }

    companion object {
        private const val THUMB_SLOP = 25f
    }
}
