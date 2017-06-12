package org.videolan.vlc.gui.helpers;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


public class BottomSheetBehavior<V extends View> extends android.support.design.widget.BottomSheetBehavior<V> {
    public static final String TAG = "VLC/BottomSheetBehavior";

    public BottomSheetBehavior() {}

    public BottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        try {
            return super.onInterceptTouchEvent(parent, child, event);
        } catch (NullPointerException ignored) {
            // BottomSheetBehavior receives input events too soon and mNestedScrollingChildRef is not set yet.
            return false;
        }
    }

    @Override
    public void onStopNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target) {
        try {
            super.onStopNestedScroll(coordinatorLayout, child, target);
        } catch (NullPointerException ignored) {
            //Same crash, weakref not already set.
        }
    }

    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dx, int dy, int[] consumed) {
        try {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed);
        } catch (NullPointerException ignored) {
            //Same crash, weakref not already set.
        }
    }

    @Override
    public boolean onNestedPreFling(CoordinatorLayout coordinatorLayout, V child, View target, float velocityX, float velocityY) {
        try {
            return super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY);
        } catch (NullPointerException ignored) {
            //Same crash, weakref not already set.
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        try {
            return super.onTouchEvent(parent, child, event);
        } catch (NullPointerException ignored) {
            return false;
        }
    }
}
