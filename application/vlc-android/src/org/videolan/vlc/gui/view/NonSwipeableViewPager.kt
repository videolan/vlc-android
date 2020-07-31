package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

import androidx.viewpager.widget.ViewPager

class NonSwipeableViewPager : ViewPager {

    var scrollEnabled = true

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!scrollEnabled) {
            return false
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!scrollEnabled) {
            return false
        }
        return super.onTouchEvent(event)
    }

}