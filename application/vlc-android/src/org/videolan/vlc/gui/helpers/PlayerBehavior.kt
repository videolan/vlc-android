package org.videolan.vlc.gui.helpers

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PlayerBehavior<V : View> : com.google.android.material.bottomsheet.BottomSheetBehavior<V> {
    private var lock = false

    constructor() {
        isHideable = true
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    fun lock(lock: Boolean) {
        this.lock = lock
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (lock) return false
        try {
            return super.onInterceptTouchEvent(parent, child, event)
        } catch (ignored: NullPointerException) {
            // BottomSheetBehavior receives input events too soon and mNestedScrollingChildRef is not set yet.
            return false
        }

    }


    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, type: Int) {
        if (lock) return
        try {
            super.onStopNestedScroll(coordinatorLayout, child, target, type)
        } catch (ignored: NullPointerException) {
        }

    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View) {
        if (lock) return
        try {
            super.onStopNestedScroll(coordinatorLayout, child, target)
        } catch (ignored: NullPointerException) {
            //Same crash, weakref not already set.
        }

    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (lock) return
        try {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
        } catch (ignored: NullPointerException) {
            //Same crash, weakref not already set.
        }

    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, dx: Int, dy: Int, consumed: IntArray) {
        if (lock) return
        try {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed)
        } catch (ignored: NullPointerException) {
            //Same crash, weakref not already set.
        }

    }

    override fun onNestedPreFling(coordinatorLayout: CoordinatorLayout, child: V, target: View, velocityX: Float, velocityY: Float): Boolean {
        if (lock) return false
        try {
            return super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY)
        } catch (ignored: NullPointerException) {
            //Same crash, weakref not already set.
        }

        return false
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int)=  try {
        super.onLayoutChild(parent, child, layoutDirection)
    } catch (ignored: IndexOutOfBoundsException) {
        false
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (lock) return false
        return try {
            super.onTouchEvent(parent, child, event)
        } catch (ignored: NullPointerException) {
            false
        }

    }

    companion object {
        const val TAG = "VLC/BottomSheetBehavior"
    }
}
