/*****************************************************************************
 * AudioBrowserActivity.java
 *****************************************************************************
 * Copyright © 2011-2013 VLC authors and VideoLAN
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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HeaderScrollView extends HorizontalScrollView {

    private int mTabWidth;
    private float mProgress = 0;
    public final static int MODE_TOTAL = 4;

    public HeaderScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mTabWidth = w / 2;

        post(new Runnable() {
            @Override
            public void run() {
                LinearLayout hl = (LinearLayout) findViewById(R.id.header_layout);
                for (int i = 0; i < hl.getChildCount(); ++i) {
                    View t = hl.getChildAt(i);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mTabWidth, LayoutParams.WRAP_CONTENT, 1);
                    if (i == 0)
                        lp.setMargins(mTabWidth / 2, 0, 0, 0);
                    else if (i == hl.getChildCount() - 1)
                        lp.setMargins(0, 0, mTabWidth / 2, 0);
                    t.setLayoutParams(lp);
                }
            }
        });
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        restoreScroll();
    }

    public void scroll(float progress) {
        mProgress = progress;
        restoreScroll();
    }

    private void restoreScroll() {
        /*
         * How progress works:
         * |------|------|------|
         * 0     1/3    2/3     1
         *
         * To calculate the "progress" of a particular tab, one can use this
         * formula:
         *
         * <tab beginning with 0> * (1 / (total tabs - 1))
         */
        int x = (int) (mProgress * (MODE_TOTAL - 1) * mTabWidth);
        smoothScrollTo(x, 0);
    }

    public void highlightTab(int existingPosition, int newPosition) {
        LinearLayout hl = (LinearLayout) findViewById(R.id.header_layout);
        TypedArray attrs = getContext().obtainStyledAttributes(new int[] { R.attr.font_light, R.attr.font_default});
        if (hl == null)
            return;
        TextView oldView = (TextView) hl.getChildAt(existingPosition);
        if (oldView != null)
            oldView.setTextColor(attrs.getColor(0, 0));
        TextView newView = (TextView) hl.getChildAt(newPosition);
        if (newView != null)
            newView.setTextColor(attrs.getColor(1, 0));
        attrs.recycle();
    }
}
