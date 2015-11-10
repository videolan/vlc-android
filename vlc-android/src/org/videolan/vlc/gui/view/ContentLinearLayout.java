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
package org.videolan.vlc.gui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.android.widget.SlidingPaneLayout;


/**
 * This class extends the linear layout class and override its onInterceptTouchEvent
 * method to intercept the touch events that should not be handled by its children.
 * This is necessary for the audioplayer to get the swipe events for next/previous skip.
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
