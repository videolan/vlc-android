/*****************************************************************************
 * FlingViewGroup.java
 *
 * Copyright © 2011-2012 VLC authors and VideoLAN
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
import android.util.AttributeSet
import android.view.*
import android.widget.Scroller

open class FlingViewGroup(context: Context, attrs: AttributeSet) : ViewGroup(context, attrs) {

    var position = 0
    private val scroller: Scroller
    private var velocityTracker: VelocityTracker? = null

    private var touchState = TOUCH_STATE_REST
    private var interceptTouchState = TOUCH_STATE_REST
    private val touchSlop: Int
    private val maximumVelocity: Int

    private var lastX: Float = 0.toFloat()
    private var lastInterceptDownY: Float = 0.toFloat()
    private var initialMotionEventX: Float = 0.toFloat()
    private var initialMotionX: Float = 0.toFloat()
    private var initialMotionY: Float = 0.toFloat()

    private var viewSwitchListener: ViewSwitchListener? = null

    init {
        this.layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT)

        scroller = Scroller(context)
        val config = ViewConfiguration.get(getContext())
        touchSlop = config.scaledTouchSlop
        maximumVelocity = config.scaledMaximumFlingVelocity
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var childLeft = 0

        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                val childWidth = child.measuredWidth
                child.layout(childLeft, 0, childLeft + childWidth,
                        child.measuredHeight)
                childLeft += childWidth
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY)
            return

        val count = childCount
        var maxHeight = 0
        for (i in 0 until count) {
            val child = getChildAt(i)
            child.measure(widthMeasureSpec, heightMeasureSpec)
            maxHeight = Math.max(maxHeight, child.measuredHeight)
        }

        setMeasuredDimension(measuredWidth, maxHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!scroller.isFinished)
            scroller.abortAnimation()
        super.onSizeChanged(w, h, oldw, oldh)
        scrollTo(position * w, 0)
        requestLayout()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidate()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (childCount == 0)
            return false

        val x = ev.x
        val y = ev.y

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastInterceptDownY = ev.y
                initialMotionX = x
                initialMotionY = y
                touchState = if (scroller.isFinished)
                    TOUCH_STATE_REST
                else
                    TOUCH_STATE_MOVE
                interceptTouchState = TOUCH_STATE_REST
            }
            MotionEvent.ACTION_MOVE -> {
                if (interceptTouchState == TOUCH_STATE_MOVE)
                    return false
                if (Math.abs(lastInterceptDownY - y) > touchSlop)
                    interceptTouchState = TOUCH_STATE_MOVE
                if (Math.abs(lastX - x) > touchSlop)
                    touchState = TOUCH_STATE_MOVE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> interceptTouchState = TOUCH_STATE_REST
        }

        return touchState == TOUCH_STATE_MOVE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (childCount == 0)
            return false

        if (velocityTracker == null)
            velocityTracker = VelocityTracker.obtain()
        velocityTracker!!.addMovement(event)

        val action = event.action
        val x = event.x
        val y = event.y

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished)
                    scroller.abortAnimation()
                lastX = x
                if (viewSwitchListener != null)
                    viewSwitchListener!!.onTouchDown()
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = (lastX - x).toInt()
                lastX = x
                val scrollX = scrollX
                if (delta < 0) {
                    if (scrollX > 0) {
                        scrollBy(Math.max(-scrollX, delta), 0)
                    }
                } else if (delta > 0) {
                    val availableToScroll = getChildAt(childCount - 1).right - scrollX - width
                    if (availableToScroll > 0) {
                        scrollBy(Math.min(availableToScroll, delta), 0)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val velocityTracker = velocityTracker
                velocityTracker!!.computeCurrentVelocity(1000, maximumVelocity.toFloat())
                val velocityX = velocityTracker.xVelocity.toInt()
                val dx = x - initialMotionX
                val dy = y - initialMotionY

                if (dx > 0 && position == 0 && dx > touchSlop) {
                    if (viewSwitchListener != null)
                        viewSwitchListener!!.onBackSwitched()
                } else if (velocityX > 1000 && position > 0) {
                    snapToScreen(position - 1)
                } else if (velocityX < -1000 && position < childCount - 1) {
                    snapToScreen(position + 1)
                } else {
                    snapToDestination()
                }

                if (this.velocityTracker != null) {
                    this.velocityTracker!!.recycle()
                    this.velocityTracker = null
                }

                if (viewSwitchListener != null) {
                    viewSwitchListener!!.onTouchUp()
                    if (dx * dx + dy * dy < touchSlop * touchSlop)
                        viewSwitchListener!!.onTouchClick()
                }
            }
        }
        return true
    }

    override fun onScrollChanged(h: Int, v: Int, oldh: Int, oldv: Int) {
        super.onScrollChanged(h, v, oldh, oldv)
        if (viewSwitchListener != null && Math.abs(oldh - h) > Math.abs(oldv - v)) {
            val progress = h.toFloat() / (width * (childCount - 1)).toFloat()
            if (h != position * width)
                viewSwitchListener!!.onSwitching(progress)
            else
                viewSwitchListener!!.onSwitched(position)
        }
    }

    private fun snapToDestination() {
        val screenWidth = width
        val whichScreen = (scrollX + screenWidth / 2) / screenWidth
        snapToScreen(whichScreen)
    }

    private fun snapToScreen(position: Int) {
        this.position = position
        val delta = position * width - scrollX
        scroller.startScroll(scrollX, 0, delta, 0, Math.abs(delta))
        invalidate()
    }

    fun scrollTo(position: Int) {
        this.position = position
        val delta = position * width - scrollX
        scroller.startScroll(scrollX, 0, delta, 0, 1)
        invalidate()
    }

    fun smoothScrollTo(position: Int) {
        this.position = position
        val delta = position * width - scrollX
        scroller.startScroll(scrollX, 0, delta, 0, 300)
        invalidate()
    }

    fun setOnViewSwitchedListener(l: ViewSwitchListener) {
        viewSwitchListener = l
    }

    interface ViewSwitchListener {
        fun onSwitching(progress: Float)

        fun onSwitched(position: Int)

        fun onTouchDown()

        fun onTouchUp()

        fun onTouchClick()

        fun onBackSwitched()
    }

    companion object {
        const val TAG = "VLC/FlingViewGroup"
        private const val TOUCH_STATE_MOVE = 0
        private const val TOUCH_STATE_REST = 1
    }

}
