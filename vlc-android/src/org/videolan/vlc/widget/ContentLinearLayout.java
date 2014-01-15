/*****************************************************************************
 * ContentLinearLayout.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 *****************************************************************************/
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
            return super.onInterceptTouchEvent(ev);
    }
}
