/*****************************************************************************
 * SlidingPanel.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
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

import org.videolan.vlc.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SlidingDrawer;

public class SlidingPanel extends SlidingDrawer {

    public SlidingPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        View handle = getHandle();
        handle.layout(0, handle.getTop(), handle.getWidth(), handle.getBottom());
    }

    public void CollapseHandle() {
        View handle = getHandle();
        View filler = handle.findViewById(R.id.slider_handle_filler);
        LayoutParams lp = handle.getLayoutParams();
        if (lp.width == LayoutParams.WRAP_CONTENT)
            return;
        lp.width = LayoutParams.WRAP_CONTENT;
        handle.setLayoutParams(lp);
        handle.setBackgroundResource(android.R.color.transparent);
        filler.setVisibility(View.GONE);
    }

    public void ExpandHandle() {
        View handle = getHandle();
        View filler = handle.findViewById(R.id.slider_handle_filler);
        LayoutParams lp = handle.getLayoutParams();
        if (lp.width == LayoutParams.MATCH_PARENT)
            return;
        lp.width = LayoutParams.MATCH_PARENT;
        handle.setLayoutParams(lp);
        handle.setBackgroundResource(R.color.transparent_gray);
        filler.setVisibility(View.VISIBLE);
    }
}
