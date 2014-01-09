package org.videolan.vlc.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;


/**
 * This class extends the linear layout class and override its onInterceptTouchEvent
 * method to intercept the touch events that should not be handled by its children.
 * This is necessary since else the layout children receive events even if the
 * audio player is displayed just under the touch event position.
 */
public class ContentLinearLayout extends LinearLayout {

    public ContentLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        SlidingPaneLayout slidingPaneLayout = (SlidingPaneLayout)getParent();
        if (slidingPaneLayout.isSecondChildUnder((int)ev.getX(), (int)ev.getY()))
            return true;
        else
            return super.onInterceptHoverEvent(ev);
    }
}
