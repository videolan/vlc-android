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
}
